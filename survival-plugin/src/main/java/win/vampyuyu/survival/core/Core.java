package win.vampyuyu.survival.core;

import java.io.File;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.bukkit.Bukkit;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.permissions.PermissionDefault;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Context handed to every {@link Feature}: shared services, the service registry and configuration.
 * One instance per plugin run, created by {@code SurvivalPlugin}.
 */
public final class Core {

    private final JavaPlugin plugin;
    private final Msg msg = new Msg();
    private final Scheduler scheduler;
    private final JsonStore store;
    private final PlayerProfiles profiles;
    private final Items items = new Items();
    private final Permissions permissions = new Permissions();
    private final Commands commands;
    private final Guis guis;
    private final ConcurrentMap<Class<?>, Object> services = new ConcurrentHashMap<>();
    private final java.util.List<Class<?>> serviceOrder = new java.util.concurrent.CopyOnWriteArrayList<>();
    private final java.util.List<Listener> listeners = new java.util.concurrent.CopyOnWriteArrayList<>();
    private boolean started;
    private final ModeTravel modes = new ModeTravel(this);
    private win.vampyuyu.survival.data.Db data;
    private final java.util.List<java.util.function.Consumer<String>> dataListeners = new java.util.concurrent.CopyOnWriteArrayList<>();
    private volatile boolean debug;

    public Core(JavaPlugin plugin) {
        this.plugin = plugin;
        this.scheduler = new Scheduler(plugin);
        this.store = new JsonStore(plugin.getDataFolder().toPath(), plugin.getLogger());
        this.profiles = new PlayerProfiles(store, plugin.getLogger());
        this.commands = new Commands(plugin, permissions);
        this.guis = new Guis(scheduler, plugin.getLogger());
    }

    /** Registers the global listeners, the autosave and the {@code /sbgive} command. Called once before any feature enables. */
    public void start() {
        if (started) {
            return;
        }
        started = true;
        data();
        debug = coreSettings().getBoolean("debug", false);
        Bukkit.getPluginManager().registerEvents(profiles, plugin);
        Bukkit.getPluginManager().registerEvents(guis, plugin);
        try {
            Bukkit.getPluginManager().registerEvents(new SwingCharge(), plugin);
        } catch (RuntimeException e) {
            // a server without the Paper cooldown event (some test servers): swings fall back to the live cooldown
            plugin.getLogger().warning("Swing charge tracking off: " + e);
        }
        profiles.start(scheduler);
        listen(modes);
        GiveCommand give = new GiveCommand(items, msg);
        permissions.register("vampyuyu.sb.admin", "Skyblock administration (give items, bosses...)", PermissionDefault.OP);
        commands.command("sbgive").description("Give a custom Skyblock item")
            .usage("/<command> <id> [amount] [player]")
            .permission("vampyuyu.sb.admin", PermissionDefault.OP)
            .executor(give).tab(give).register();
        commands.command("sbdata").description("Skyblock content data (panel edits)")
            .usage("/<command> reload")
            .permission("vampyuyu.sb.admin", PermissionDefault.OP)
            .executor((sender, command, label, args) -> {
                if (args.length != 1 || !args[0].equalsIgnoreCase("reload")) {
                    return false;
                }
                for (win.vampyuyu.survival.data.Seeds.Domain domain : win.vampyuyu.survival.data.ContentDomains.ALL) {
                    dataChanged(domain.name());
                }
                sender.sendMessage("Skyblock data: every feature that follows the database re-reads it.");
                return true;
            })
            .tab((sender, command, alias, args) -> args.length == 1 ? java.util.List.of("reload") : java.util.List.of())
            .register();
    }

    /** Releases everything the core owns. Called from onDisable after the features were disabled. */
    public void stop() {
        guis.closeAll();
        commands.unregisterAll();
        permissions.unregisterAll();
        profiles.shutdown();
        scheduler.cancelAll();
        services.clear();
        serviceOrder.clear();
        dataListeners.clear();
        HandlerList.unregisterAll(plugin);
        admin = null;
        if (data != null) {
            data.close();
            data = null;
        }
    }

    // ---------------------------------------------------------------- plugin

    public JavaPlugin getPlugin() { return plugin; }

    public File getDataFolder() { return plugin.getDataFolder(); }

    /** The plugin level settings ({@code core.*} rows of the content database): debug flag and feature toggles. */
    public win.vampyuyu.survival.data.Settings coreSettings() {
        return win.vampyuyu.survival.data.Settings.load(data(), "core");
    }

    /** Logs a line at INFO when the {@code core.debug} setting is on (read once when the core starts). */
    public void debug(String line) {
        if (debug) {
            plugin.getLogger().info("[debug] " + line);
        }
    }

    /** Registers a Bukkit listener for the plugin. */
    public void listen(Listener listener) {
        listeners.add(listener);
        Bukkit.getPluginManager().registerEvents(listener, plugin);
    }

    /** Snapshot of what has been registered through the core so far (see {@link #rollback}). */
    public record Mark(int services, int listeners, int commands, int permissions) {}

    /** Remembers the current registrations. {@link FeatureManager} takes one before each {@code enable()}. */
    public Mark mark() {
        return new Mark(serviceOrder.size(), listeners.size(), commands.count(), permissions.size());
    }

    /**
     * Undoes everything registered through the core since {@code mark}: services, listeners ({@link #listen}),
     * commands and permissions. Used when a feature's {@code enable()} throws, so no half-built service stays visible.
     * Custom items, tasks and GUIs are not rolled back.
     */
    public void rollback(Mark mark) {
        while (serviceOrder.size() > mark.services()) {
            services.remove(serviceOrder.remove(serviceOrder.size() - 1));
        }
        while (listeners.size() > mark.listeners()) {
            HandlerList.unregisterAll(listeners.remove(listeners.size() - 1));
        }
        commands.unregisterFrom(mark.commands());
        permissions.unregisterFrom(mark.permissions());
    }


    // ---------------------------------------------------------------- shared services

    public ModeTravel modes() { return modes; }

    public PlayerProfiles profiles() { return profiles; }

    public Items items() { return items; }

    public Commands commands() { return commands; }

    public Permissions permissions() { return permissions; }

    public Guis guis() { return guis; }

    public Scheduler scheduler() { return scheduler; }

    public Msg msg() { return msg; }

    /**
     * The Skyblock content database ({@code skyblock.db}): quests, items, bosses, tuning. Opened on first use; the
     * jar's default content is synced in then (see {@link win.vampyuyu.survival.data.Seeds}).
     */
    public synchronized win.vampyuyu.survival.data.Db data() {
        if (data == null) {
            data = win.vampyuyu.survival.data.Db.open(plugin.getDataFolder().toPath().resolve("skyblock.db"));
            win.vampyuyu.survival.data.Seeds.sync(data, win.vampyuyu.survival.data.ContentDomains.ALL, plugin.getLogger());
        }
        return data;
    }

    /**
     * Subscribes to content changes made from the panel (or {@code /sbdata reload}): the listener gets the domain name
     * ({@link win.vampyuyu.survival.data.ContentDomains}) on the main thread, after the change is committed. A feature
     * that cannot re-read its data live simply does not subscribe (it then needs a restart to see edits). Listeners are
     * dropped when the core stops; the returned handle removes this one earlier (call it from {@code disable()}).
     */
    public Runnable onDataChange(java.util.function.Consumer<String> listener) {
        dataListeners.add(listener);
        return () -> dataListeners.remove(listener);
    }

    /**
     * Tells the subscribers that {@code domain} changed. Safe from any thread: delivery is on the main thread, one
     * listener failing does not stop the others.
     */
    public void dataChanged(String domain) {
        scheduler.sync(() -> {
            for (java.util.function.Consumer<String> listener : dataListeners) {
                try {
                    listener.accept(domain);
                } catch (RuntimeException e) {
                    plugin.getLogger().log(java.util.logging.Level.WARNING, "Skyblock data: a listener failed on '" + domain + "'", e);
                }
            }
        });
    }

    private win.vampyuyu.survival.data.DataAdmin admin;

    /** Staff editing of the content database; every committed write fires {@link #dataChanged}. */
    public synchronized win.vampyuyu.survival.data.DataAdmin dataAdmin() {
        if (admin == null) {
            admin = new win.vampyuyu.survival.data.DataAdmin(data(), this::dataChanged);
        }
        return admin;
    }

    /** Atomic JSON files under the data folder (for data that is not per player). */
    public JsonStore store() { return store; }

    // ---------------------------------------------------------------- service registry

    /**
     * Publishes a cross-feature service (normally one of the {@code api/} interfaces). One implementation per type.
     *
     * @throws IllegalStateException when the type is already registered
     */
    public <T> void register(Class<T> type, T implementation) {
        if (services.putIfAbsent(type, implementation) != null) {
            throw new IllegalStateException("Service already registered: " + type.getName());
        }
        serviceOrder.add(type);
    }

    /**
     * Publishes a service even when a feature already registered one. Test seam: a test boots the whole plugin and
     * swaps a real service for a stand-in. Production code uses {@link #register}.
     */
    public <T> void replace(Class<T> type, T implementation) {
        if (services.put(type, implementation) == null) serviceOrder.add(type);
    }

    /** Test seam: withdraws a published service, as if its feature were disabled. */
    public <T> void unpublish(Class<T> type) {
        if (services.remove(type) != null) serviceOrder.remove(type);
    }

    /** The service, or empty when its feature is disabled, failed, or has not enabled yet. */
    public <T> Optional<T> service(Class<T> type) {
        return Optional.ofNullable(type.cast(services.get(type)));
    }

    /** The service. @throws IllegalStateException when absent; prefer {@link #service} unless the feature cannot work without it. */
    public <T> T require(Class<T> type) {
        return service(type).orElseThrow(() -> new IllegalStateException("Required service missing: " + type.getName()));
    }
}
