package win.vampyuyu.survival.minions;

import com.google.gson.JsonElement;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import win.vampyuyu.survival.core.JsonStore;

/**
 * Every placed minion, in memory, and its persistence: one file per owner {@code minions/<owner uuid>.json} plus
 * {@code minions/ledger.json} (spent item uids).
 *
 * <p>All mutation happens on the main thread. A save takes a deep snapshot of the owner's minions on the calling
 * thread and hands the JSON to ONE writer thread, so two saves of the same file can never land out of order. Saves
 * that move items (place, pick up, collect, install, tier up) wait for the write to finish before the items change
 * hands; production-only changes are flushed by the periodic {@link #flushDirty()}.
 */
public final class MinionStore {
    private static final String DIR = "minions/";
    private static final String LEDGER = DIR + "ledger.json";

    /** File layout of one owner. */
    public static final class OwnerFile {
        public int version = 1;
        public List<MinionData> minions = new ArrayList<>();
    }

    /** File layout of the ledger. */
    public static final class LedgerFile {
        public int version = 1;
        public List<String> spent = new ArrayList<>();
    }

    private final JsonStore json;
    private final File dir;
    private final Logger log;
    private final Map<String, MinionData> byId = new LinkedHashMap<>();
    private final Map<String, Set<String>> byOwner = new HashMap<>();
    private final Map<String, Set<String>> byChunk = new HashMap<>();
    private final Set<String> dirtyOwners = new HashSet<>();
    private final UidLedger ledger = new UidLedger(50_000);
    private final ExecutorService writer = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "minions-writer");
        t.setDaemon(true);
        return t;
    });
    private boolean ledgerDirty;

    public MinionStore(JsonStore json, File dataFolder, Logger log) {
        this.json = json;
        this.dir = new File(dataFolder, "minions");
        this.log = log;
    }

    public UidLedger ledger() {
        return ledger;
    }

    // ---------------------------------------------------------------- loading

    /** Reads every file. Duplicated ids or positions (a hand edited or restored file) are dropped with a warning. */
    public void loadAll() {
        byId.clear();
        byOwner.clear();
        byChunk.clear();
        LedgerFile lf = json.read(LEDGER, LedgerFile.class, LedgerFile::new);
        ledger.load(lf.spent);
        File[] files = dir.listFiles((d, name) -> name.endsWith(".json") && !name.equals("ledger.json"));
        if (files == null) {
            return;
        }
        java.util.Arrays.sort(files);
        Set<String> positions = new HashSet<>();
        for (File file : files) {
            OwnerFile of = json.read(DIR + file.getName(), OwnerFile.class, OwnerFile::new);
            for (MinionData d : of.minions) {
                if (d == null || d.id.isBlank() || d.type.isBlank() || d.world.isBlank() || d.owner.isBlank()) {
                    log.warning("minions: ignored an incomplete entry in " + file.getName());
                    continue;
                }
                String pos = d.world + ":" + d.x + ":" + d.y + ":" + d.z;
                if (byId.containsKey(d.id) || !positions.add(pos)) {
                    log.warning("minions: dropped duplicated minion " + d.id + " in " + file.getName());
                    continue;
                }
                new Storage(d.storage, Integer.MAX_VALUE).sanitize();
                d.tier = MinionConfig.clampTier(d.tier);
                index(d);
            }
        }
    }

    // ---------------------------------------------------------------- queries

    public Optional<MinionData> get(String id) {
        return Optional.ofNullable(byId.get(id));
    }

    /** Snapshot of every minion. */
    public List<MinionData> all() {
        return new ArrayList<>(byId.values());
    }

    public int size() {
        return byId.size();
    }

    public List<MinionData> ownedBy(String owner) {
        List<MinionData> out = new ArrayList<>();
        for (String id : byOwner.getOrDefault(owner, Set.of())) {
            out.add(byId.get(id));
        }
        return out;
    }

    public int countOwned(String owner) {
        return byOwner.getOrDefault(owner, Set.of()).size();
    }

    public int countOnIsland(String island) {
        int n = 0;
        for (MinionData d : byId.values()) {
            if (d.island.equals(island)) {
                n++;
            }
        }
        return n;
    }

    public List<MinionData> inChunk(String world, int chunkX, int chunkZ) {
        List<MinionData> out = new ArrayList<>();
        for (String id : byChunk.getOrDefault(chunkIndexKey(world, chunkX, chunkZ), Set.of())) {
            out.add(byId.get(id));
        }
        return out;
    }

    public Optional<MinionData> atBlock(String world, int x, int y, int z) {
        for (MinionData d : inChunk(world, x >> 4, z >> 4)) {
            if (d.x == x && d.y == y && d.z == z) {
                return Optional.of(d);
            }
        }
        return Optional.empty();
    }

    // ---------------------------------------------------------------- mutation (main thread)

    /** Registers a new minion (not saved yet). @throws IllegalStateException if the id or the block is taken */
    public void add(MinionData d) {
        if (byId.containsKey(d.id) || atBlock(d.world, d.x, d.y, d.z).isPresent()) {
            throw new IllegalStateException("Minion id or position already used");
        }
        index(d);
    }

    /** Forgets a minion (not saved yet). */
    public boolean remove(String id) {
        MinionData d = byId.remove(id);
        if (d == null) {
            return false;
        }
        Set<String> owned = byOwner.get(d.owner);
        if (owned != null) {
            owned.remove(id);
        }
        Set<String> chunk = byChunk.get(chunkIndexKey(d.world, d.x >> 4, d.z >> 4));
        if (chunk != null) {
            chunk.remove(id);
        }
        return true;
    }

    private void index(MinionData d) {
        byId.put(d.id, d);
        byOwner.computeIfAbsent(d.owner, k -> new HashSet<>()).add(d.id);
        byChunk.computeIfAbsent(chunkIndexKey(d.world, d.x >> 4, d.z >> 4), k -> new HashSet<>()).add(d.id);
    }

    private static String chunkIndexKey(String world, int chunkX, int chunkZ) {
        return world + "|" + MinionData.chunkKey(chunkX, chunkZ);
    }

    // ---------------------------------------------------------------- saving

    /** Production changed: the periodic flush writes the owner's file. */
    public void markDirty(MinionData d) {
        dirtyOwners.add(d.owner);
    }

    /** Spends a uid and queues the ledger for writing. */
    public boolean spendUid(String uid) {
        boolean spent = ledger.spend(uid);
        ledgerDirty |= spent;
        return spent;
    }

    /**
     * Writes the owner's file now and waits for it to reach the disk. Call this BEFORE items change hands, so a crash
     * can lose an item but never duplicate one.
     */
    public void saveNow(String owner) {
        // The ledger first: if the process dies between the two writes, a spent uid with no minion only costs the
        // player an item, while the other order could let the same item be placed twice.
        if (ledgerDirty) {
            ledgerDirty = false;
            wait(submitLedger());
        }
        dirtyOwners.remove(owner);
        wait(submit(owner));
    }

    /** Queues every dirty owner (and the ledger) without waiting. */
    public void flushDirty() {
        for (String owner : new ArrayList<>(dirtyOwners)) {
            submit(owner);
        }
        dirtyOwners.clear();
        if (ledgerDirty) {
            ledgerDirty = false;
            submitLedger();
        }
    }

    private Future<?> submit(String owner) {
        List<MinionData> copy = new ArrayList<>();
        for (MinionData d : ownedBy(owner)) {
            copy.add(d.copy());
        }
        String path = DIR + owner + ".json";
        if (copy.isEmpty()) {
            return run(() -> {
                try {
                    json.delete(path);
                } catch (IOException e) {
                    log.log(Level.WARNING, "minions: could not delete " + path, e);
                }
            });
        }
        OwnerFile file = new OwnerFile();
        file.minions = copy;
        JsonElement tree = JsonStore.GSON.toJsonTree(file);
        return run(() -> write(path, tree));
    }

    /** Runs on the writer thread, or inline once the writer is stopped (a save after disable must not be lost). */
    private Future<?> run(Runnable task) {
        if (writer.isShutdown()) {
            task.run();
            return CompletableFuture.completedFuture(null);
        }
        return writer.submit(task);
    }

    private Future<?> submitLedger() {
        LedgerFile file = new LedgerFile();
        file.spent = ledger.snapshot();
        JsonElement tree = JsonStore.GSON.toJsonTree(file);
        return run(() -> write(LEDGER, tree));
    }

    private void write(String path, JsonElement tree) {
        try {
            json.write(path, tree);
        } catch (IOException | RuntimeException e) {
            log.log(Level.SEVERE, "minions: could not save " + path, e);
        }
    }

    private void wait(Future<?> future) {
        try {
            future.get(15, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.log(Level.SEVERE, "minions: a save did not finish", e);
        }
    }

    /** Saves everything and stops the writer. Call on disable. */
    public void close() {
        if (ledgerDirty) {
            submitLedger();
            ledgerDirty = false;
        }
        for (String owner : new HashSet<>(byOwner.keySet())) {
            submit(owner);
        }
        for (String owner : dirtyOwners) {
            if (!byOwner.containsKey(owner)) {
                submit(owner);
            }
        }
        dirtyOwners.clear();
        writer.shutdown();
        try {
            if (!writer.awaitTermination(20, TimeUnit.SECONDS)) {
                log.severe("minions: the writer did not finish in time");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
