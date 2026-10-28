package win.vampyuyu.survival.core;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * One JSON document per player at {@code plugins/VampyuyuSurvival/players/<uuid>.json}:
 * <pre>{ "version": 1, "name": "Steve", "lastSeen": 1730000000000, "sections": { "economy": {...}, "skills": {...} } }</pre>
 * Each feature owns one or more SECTION keys (see docs/skyblock-design.md, section 6) and reads/writes them as
 * plain Gson-serialisable classes of its own.
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * EconomyData data = profiles.section(uuid, "economy", EconomyData.class, EconomyData::new);
 * data.coins += 5;
 * profiles.markDirty(uuid);
 *
 * // from any thread, atomic with respect to saving and eviction:
 * profiles.edit(uuid, "economy", EconomyData.class, EconomyData::new, d -> d.coins += 5);
 * }</pre>
 *
 * <h2>Lifecycle and threading</h2>
 * Loaded in {@code AsyncPlayerPreLoginEvent} (off the main thread) and again defensively on join; unknown
 * (offline) players are loaded lazily by {@link #section}, which then does blocking disk I/O: do that off the main
 * thread. Dirty profiles are serialised on the thread that triggers the save (the main thread for the periodic save
 * and quit) and written by one single-thread executor, so writes stay ordered and a quit followed by a quick
 * rejoin never reads a stale file (a profile stays cached until its last write is done). Saved every 5
 * minutes, on quit and on {@link #shutdown()}. A section object is shared and mutable: mutate it on the main
 * thread, or through {@link #edit} (locks the profile); a bare {@code section()} plus {@code markDirty()} is only safe
 * from the main thread.
 */
public final class PlayerProfiles implements Listener {
    /** Idle time after which a clean offline profile is dropped from memory. */
    private static final long EVICT_AFTER_MS = 60_000;

    private static final class Profile {
        final UUID id;
        final JsonObject raw;
        final Map<String, Object> typed = new LinkedHashMap<>();
        String name = "";
        long lastSeen;
        boolean dirty;
        boolean online;
        boolean evicted;
        int pending;
        volatile long touched;

        Profile(UUID id, JsonObject raw) {
            this.id = id;
            this.raw = raw;
        }
    }

    private final JsonStore store;
    private final Logger log;
    private final ExecutorService io = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "survival-profile-io");
        thread.setDaemon(true);
        return thread;
    });
    private final Map<UUID, Profile> cache = new ConcurrentHashMap<>();
    private Scheduler.Handle task;

    public PlayerProfiles(JsonStore store, Logger log) {
        this.store = store;
        this.log = log;
    }

    private static String path(UUID id) {
        return "players/" + id + ".json";
    }

    /** Starts the 5 minute autosave on the main thread. */
    public void start(Scheduler scheduler) {
        task = scheduler.repeating(6000, 6000, this::saveDirty);
    }

    // ---------------------------------------------------------------- access

    private Profile acquire(UUID id) {
        Profile existing = cache.get(id);
        if (existing != null) {
            existing.touched = System.currentTimeMillis();
            return existing;
        }
        return cache.computeIfAbsent(id, this::loadFromDisk);
    }

    private Profile loadFromDisk(UUID id) {
        JsonObject raw = new JsonObject();
        String name = "";
        long lastSeen = 0;
        try {
            String text = store.readText(path(id));
            if (text != null) {
                JsonObject doc = JsonParser.parseString(text).getAsJsonObject();
                if (doc.has("sections") && doc.get("sections").isJsonObject()) {
                    raw = doc.getAsJsonObject("sections");
                }
                name = doc.has("name") ? doc.get("name").getAsString() : "";
                lastSeen = doc.has("lastSeen") ? doc.get("lastSeen").getAsLong() : 0;
            }
        } catch (IOException | RuntimeException e) {
            store.quarantine(store.resolve(path(id)), e);
            raw = new JsonObject();
        }
        Profile profile = new Profile(id, raw);
        profile.name = name;
        profile.lastSeen = lastSeen;
        profile.touched = System.currentTimeMillis();
        return profile;
    }

    private <T> T sectionOf(Profile profile, String key, Class<T> type, Supplier<T> defaults) {
        Object cached = profile.typed.get(key);
        if (cached != null) {
            if (!type.isInstance(cached)) {
                throw new IllegalStateException("Profile section '" + key + "' is already used as "
                    + cached.getClass().getName() + ", not " + type.getName());
            }
            return type.cast(cached);
        }
        T value = null;
        JsonElement stored = profile.raw.get(key);
        if (stored != null && !stored.isJsonNull()) {
            try {
                value = JsonStore.GSON.fromJson(stored, type);
            } catch (JsonParseException e) {
                log.log(Level.SEVERE, "Corrupt profile section '" + key + "' of " + profile.id
                    + " (kept as '" + key + ".corrupt')", e);
                profile.raw.add(key + ".corrupt", stored);
                profile.dirty = true;
            }
        }
        if (value == null) {
            value = defaults.get();
        }
        profile.typed.put(key, value);
        return value;
    }

    /**
     * The player's section as a live, mutable object. Created with {@code defaults} (and kept) when absent. The
     * same key must always be requested with the same class. Call {@link #markDirty} after changing it.
     */
    public <T> T section(UUID player, String key, Class<T> type, Supplier<T> defaults) {
        while (true) {
            Profile profile = acquire(player);
            synchronized (profile) {
                if (!profile.evicted) {
                    return sectionOf(profile, key, type, defaults);
                }
            }
        }
    }

    /**
     * Runs {@code change} on the section while holding the profile lock, then marks the profile dirty.
     * Safe from any thread, also for offline players.
     */
    public <T> void edit(UUID player, String key, Class<T> type, Supplier<T> defaults, Consumer<T> change) {
        while (true) {
            Profile profile = acquire(player);
            synchronized (profile) {
                if (profile.evicted) {
                    continue;
                }
                change.accept(sectionOf(profile, key, type, defaults));
                profile.dirty = true;
                return;
            }
        }
    }

    /** Flags the profile as changed so the next save writes it. Cheap; no-op for a profile that is not loaded. */
    public void markDirty(UUID player) {
        Profile profile = cache.get(player);
        if (profile != null) {
            synchronized (profile) {
                profile.dirty = true;
            }
        }
    }

    /** True when the profile is in memory (online players, or offline ones touched recently). */
    public boolean isLoaded(UUID player) {
        return cache.containsKey(player);
    }

    /** Last known player name stored in the document ("" if never seen online). */
    public String lastName(UUID player) {
        Profile profile = acquire(player);
        synchronized (profile) {
            return profile.name;
        }
    }

    /** Loads (blocking) a profile that is not in memory. Called off-thread at pre-login. */
    public void preload(UUID player) {
        acquire(player);
    }

    // ---------------------------------------------------------------- lifecycle

    /** Marks the player online; loads the profile if needed. */
    public void playerJoined(UUID player, String name) {
        while (true) {
            Profile profile = acquire(player);
            synchronized (profile) {
                if (profile.evicted) {
                    continue;
                }
                profile.online = true;
                profile.name = name;
                profile.lastSeen = System.currentTimeMillis();
                profile.dirty = true;
                return;
            }
        }
    }

    /** Saves the profile now (asynchronously) and lets it be unloaded once written. */
    public void playerQuit(UUID player) {
        Profile profile = cache.get(player);
        if (profile == null) {
            return;
        }
        synchronized (profile) {
            profile.online = false;
            profile.lastSeen = System.currentTimeMillis();
            profile.dirty = true;
        }
        save(profile);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        if (event.getLoginResult() == AsyncPlayerPreLoginEvent.Result.ALLOWED) {
            preload(event.getUniqueId());
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent event) {
        playerJoined(event.getPlayer().getUniqueId(), event.getPlayer().getName());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        playerQuit(event.getPlayer().getUniqueId());
    }

    // ---------------------------------------------------------------- saving

    /** Writes every dirty profile and drops idle clean offline ones. Called every 5 minutes. */
    public void saveDirty() {
        for (Profile profile : cache.values()) {
            save(profile);
            tryEvict(profile);
        }
    }

    private void save(Profile profile) {
        String json;
        synchronized (profile) {
            if (!profile.dirty) {
                return;
            }
            json = serialise(profile);
            profile.dirty = false;
            profile.pending++;
        }
        try {
            io.execute(() -> write(profile, json));
        } catch (java.util.concurrent.RejectedExecutionException e) {
            write(profile, json);
        }
    }

    private void write(Profile profile, String json) {
        try {
            store.writeText(path(profile.id), json);
        } catch (IOException e) {
            log.log(Level.SEVERE, "Could not save profile " + profile.id, e);
            synchronized (profile) {
                profile.dirty = true;
            }
        } finally {
            synchronized (profile) {
                profile.pending--;
            }
        }
    }

    private String serialise(Profile profile) {
        JsonObject sections = profile.raw.deepCopy();
        for (Map.Entry<String, Object> entry : profile.typed.entrySet()) {
            sections.add(entry.getKey(), JsonStore.GSON.toJsonTree(entry.getValue()));
        }
        JsonObject doc = new JsonObject();
        doc.addProperty("version", 1);
        doc.addProperty("name", profile.name);
        doc.addProperty("lastSeen", profile.lastSeen);
        doc.add("sections", sections);
        return JsonStore.GSON.toJson(doc);
    }

    private void tryEvict(Profile profile) {
        synchronized (profile) {
            boolean idle = System.currentTimeMillis() - profile.touched > EVICT_AFTER_MS;
            if (!profile.online && !profile.dirty && profile.pending == 0 && idle) {
                profile.evicted = true;
                cache.remove(profile.id, profile);
            }
        }
    }

    /** Blocks until every queued write finished (tests, shutdown). */
    public void awaitWrites() {
        try {
            io.submit(() -> {}).get(30, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.log(Level.WARNING, "Profile writes did not finish in time", e);
        }
    }

    /** Saves everything synchronously and stops the writer. Call from onDisable. */
    public void shutdown() {
        if (task != null) {
            task.cancel();
        }
        for (Profile profile : cache.values()) {
            save(profile);
        }
        io.shutdown();
        try {
            if (!io.awaitTermination(30, TimeUnit.SECONDS)) {
                log.severe("Profile writer did not stop in time; some data may be lost");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
