package win.vampyuyu.survival.island;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.kyori.adventure.util.TriState;
import org.bukkit.Bukkit;
import org.bukkit.GameRule;
import org.bukkit.GameRules;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;
import org.bukkit.block.Biome;

/** Creates the four void worlds and applies their game rules, border and fixed time. Every tweak is best effort. */
public final class WorldSetup {
    private static final double MAX_BORDER = 59_999_968;

    private final Logger log;

    public WorldSetup(Logger log) {
        this.log = log;
    }

    /** The world with that name, created (or loaded) as a void world when missing. Null when the server refuses. */
    public World ensure(String name, World.Environment environment, Biome biome, int spawnY) {
        World existing = Bukkit.getWorld(name);
        if (existing != null) {
            return existing;
        }
        try {
            WorldCreator creator = new WorldCreator(name)
                .environment(environment)
                .type(WorldType.FLAT)
                .generateStructures(false)
                .generator(new VoidGenerator(spawnY))
                .biomeProvider(new VoidGenerator.Fixed(biome))
                .keepSpawnLoaded(TriState.FALSE);
            return Bukkit.createWorld(creator);
        } catch (RuntimeException e) {
            log.log(Level.SEVERE, "Could not create world " + name, e);
            return null;
        }
    }

    /** Applies the configured rules, time, weather and border. */
    public void configure(World world, IslandConfig.WorldCfg cfg, int spawnX, int spawnY, int spawnZ) {
        for (Map.Entry<String, Object> rule : cfg.gamerules().entrySet()) {
            applyRule(world, rule.getKey(), rule.getValue());
        }
        String time = cfg.time().toUpperCase(Locale.ROOT);
        if (!time.equals("CYCLE")) {
            TimeLock lock = TimeLock.parse(time);
            if (lock != TimeLock.NONE) {
                guard(world, "advance_time", () -> world.setGameRule(GameRules.ADVANCE_TIME, false));
                guard(world, "time", () -> world.setTime(lock.ticks()));
            } else {
                log.warning("island.yml: unknown time '" + cfg.time() + "' for " + world.getName());
            }
        }
        if (cfg.weatherClear()) {
            guard(world, "weather", () -> {
                world.setGameRule(GameRules.ADVANCE_WEATHER, false);
                world.setStorm(false);
                world.setThundering(false);
            });
        }
        guard(world, "border", () -> {
            var border = world.getWorldBorder();
            border.setCenter(0, 0);
            border.setSize(Math.min(cfg.border(), MAX_BORDER));
        });
        guard(world, "spawn", () -> world.setSpawnLocation(spawnX, spawnY, spawnZ));
    }

    private void guard(World world, String what, Runnable action) {
        try {
            action.run();
        } catch (RuntimeException | LinkageError e) {
            log.warning("World " + world.getName() + ": could not apply " + what + ": " + e);
        }
    }

    /** All rules by lower-case name, from the typed {@link GameRules} constants. */
    public static Map<String, GameRule<?>> knownRules() {
        Map<String, GameRule<?>> rules = new java.util.TreeMap<>();
        for (Field field : GameRules.class.getFields()) {
            if (Modifier.isStatic(field.getModifiers()) && GameRule.class.isAssignableFrom(field.getType())) {
                try {
                    rules.put(field.getName().toLowerCase(Locale.ROOT), (GameRule<?>) field.get(null));
                } catch (IllegalAccessException | RuntimeException e) {
                    // skip rules this server cannot resolve
                }
            }
        }
        return rules;
    }

    private void applyRule(World world, String name, Object value) {
        GameRule<?> rule = knownRules().get(name.toLowerCase(Locale.ROOT));
        if (rule == null) {
            log.warning("island.yml: unknown game rule '" + name + "' for " + world.getName());
            return;
        }
        guard(world, "game rule " + name, () -> set(world, rule, value));
    }

    @SuppressWarnings("unchecked")
    private static <T> void set(World world, GameRule<T> rule, Object value) {
        Class<T> type = rule.getType();
        Object coerced;
        if (type == Boolean.class) {
            coerced = value instanceof Boolean b ? b : Boolean.parseBoolean(String.valueOf(value));
        } else if (type == Integer.class) {
            coerced = value instanceof Number n ? n.intValue() : Integer.parseInt(String.valueOf(value));
        } else {
            throw new IllegalArgumentException("unsupported game rule type " + type);
        }
        world.setGameRule(rule, (T) coerced);
    }
}
