package win.vampyuyu.survival.core;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.regex.Pattern;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.permissions.PermissionDefault;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Context handed to every {@link Feature}: shared services, the service registry and configuration.
 * One instance per plugin run, created by {@code SurvivalPlugin}.
 */
public final class Core {
    private static final Pattern FEATURE_ID = Pattern.compile("[a-z0-9_]+");

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
        Bukkit.getPluginManager().registerEvents(profiles, plugin);
        Bukkit.getPluginManager().registerEvents(guis, plugin);
        profiles.start(scheduler);
        GiveCommand give = new GiveCommand(items, msg);
        permissions.register("vampyuyu.sb.admin", "Skyblock administration (give items, bosses...)", PermissionDefault.OP);
        commands.command("sbgive").description("Give a custom Skyblock item")
            .usage("/<command> <id> [amount] [player]")
            .permission("vampyuyu.sb.admin", PermissionDefault.OP)
            .executor(give).tab(give).register();
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
        HandlerList.unregisterAll(plugin);
    }

    // ---------------------------------------------------------------- plugin

    public JavaPlugin getPlugin() { return plugin; }

    public File getDataFolder() { return plugin.getDataFolder(); }

    /** Main {@code config.yml}. */
    public FileConfiguration config() { return plugin.getConfig(); }

    /** Logs a line at INFO when {@code debug: true} in config.yml. */
    public void debug(String line) {
        if (plugin.getConfig().getBoolean("debug", false)) {
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

    /**
     * Per-feature configuration {@code <dataFolder>/<id>.yml}. The first time, the default
     * {@code <id>.yml} shipped in the jar (if any) is copied out; keys missing from the user's file fall back to the
     * jar's copy. Never throws for a missing resource: returns an empty configuration.
     */
    public YamlConfiguration featureConfig(String id) {
        if (!FEATURE_ID.matcher(id).matches()) {
            throw new IllegalArgumentException("Bad feature id: " + id);
        }
        String resource = id + ".yml";
        File file = new File(plugin.getDataFolder(), resource);
        boolean inJar = plugin.getResource(resource) != null;
        if (!file.exists() && inJar) {
            plugin.saveResource(resource, false);
        }
        YamlConfiguration config = file.exists() ? YamlConfiguration.loadConfiguration(file) : new YamlConfiguration();
        if (inJar) {
            try (InputStream in = plugin.getResource(resource);
                    InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                config.setDefaults(YamlConfiguration.loadConfiguration(reader));
            } catch (IOException e) {
                plugin.getLogger().warning("Could not read default " + resource + ": " + e.getMessage());
            }
        }
        return config;
    }

    // ---------------------------------------------------------------- shared services

    public PlayerProfiles profiles() { return profiles; }

    public Items items() { return items; }

    public Commands commands() { return commands; }

    public Permissions permissions() { return permissions; }

    public Guis guis() { return guis; }

    public Scheduler scheduler() { return scheduler; }

    public Msg msg() { return msg; }

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

    /** The service, or empty when its feature is disabled, failed, or has not enabled yet. */
    public <T> Optional<T> service(Class<T> type) {
        return Optional.ofNullable(type.cast(services.get(type)));
    }

    /** The service. @throws IllegalStateException when absent; prefer {@link #service} unless the feature cannot work without it. */
    public <T> T require(Class<T> type) {
        return service(type).orElseThrow(() -> new IllegalStateException("Required service missing: " + type.getName()));
    }
}
