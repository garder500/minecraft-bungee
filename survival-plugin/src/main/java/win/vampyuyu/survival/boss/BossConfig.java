package win.vampyuyu.survival.boss;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import win.vampyuyu.survival.boss.slayer.SlayerDefinition;
import win.vampyuyu.survival.boss.slayer.SlayerParser;
import win.vampyuyu.survival.boss.world.WorldBossConfig;

/**
 * Everything read from boss.yml, validated. Invalid entries are dropped and listed in {@link #errors()}; the rest
 * still loads. Immutable: a reload builds a new instance.
 */
public record BossConfig(BossSettings settings, Map<String, BossDefinition> bosses,
        Map<String, SlayerDefinition> slayers, WorldBossConfig worldBoss, SlayerSettings slayerSettings,
        List<MobDrop> mobDrops, Map<String, RecipeSpec> recipes, Map<String, ConfigurationSection> itemOverrides,
        List<String> errors) {

    /**
     * Slayer behaviour.
     *
     * @param enabled master switch
     * @param ignoreSpawnerMobs mobs from monster spawners give no quest progress
     * @param spawnMinDistance the boss appears at least this far from the player
     * @param spawnMaxDistance and at most this far
     * @param spawnCountdownSeconds warning before the boss appears
     */
    public record SlayerSettings(boolean enabled, boolean ignoreSpawnerMobs, double spawnMinDistance,
            double spawnMaxDistance, int spawnCountdownSeconds) {}

    /** Extra drops of vanilla mobs (sigils, eggs, scales). */
    public record MobDrop(String item, Set<EntityType> entities, Set<String> worlds, double chance,
            boolean magicFind, boolean allowSpawner) {}

    /** Crafting recipe of a custom item. */
    public record RecipeSpec(String item, List<String> shape, Map<Character, Material> ingredients) {}

    public Optional<BossDefinition> definition(String id) {
        BossDefinition def = bosses.get(id);
        if (def != null) {
            return Optional.of(def);
        }
        for (SlayerDefinition slayer : slayers.values()) {
            for (SlayerDefinition.Tier tier : slayer.tiers()) {
                if (tier.boss().id().equals(id)) {
                    return Optional.of(tier.boss());
                }
            }
        }
        return Optional.empty();
    }

    /** Parses a configuration. Never throws for bad content. */
    public static BossConfig load(YamlConfiguration yaml) {
        List<String> errors = new ArrayList<>();
        BossSettings settings = BossSettings.parse(yaml.getConfigurationSection("settings"));
        Map<String, BossDefinition> bosses = DefinitionParser.parseAll(yaml.getConfigurationSection("bosses"),
            BossDefinition.Kind.BOSS, errors);
        Map<String, SlayerDefinition> slayers = SlayerParser.parseAll(yaml.getConfigurationSection("slayers"), errors);
        for (SlayerDefinition slayer : slayers.values()) {
            for (SlayerDefinition.Tier tier : slayer.tiers()) {
                if (bosses.containsKey(tier.boss().id())) {
                    errors.add("slayer boss id clashes with a boss: " + tier.boss().id());
                }
            }
        }
        WorldBossConfig world = WorldBossConfig.parse(yaml.getConfigurationSection("world-bosses"), bosses, errors);
        ConfigurationSection ss = yaml.getConfigurationSection("slayer-settings");
        SlayerSettings slayerSettings = new SlayerSettings(ss == null || ss.getBoolean("enabled", true),
            ss == null || ss.getBoolean("ignore-spawner-mobs", true),
            ss == null ? 6 : Math.max(2, ss.getDouble("spawn-min-distance", 6)),
            ss == null ? 10 : Math.max(3, ss.getDouble("spawn-max-distance", 10)),
            ss == null ? 3 : Math.max(0, ss.getInt("spawn-countdown-seconds", 3)));
        List<MobDrop> drops = new ArrayList<>();
        for (Map<?, ?> raw : yaml.getMapList("mob-drops")) {
            Map<String, Object> m = DefinitionParser.stringKeys(raw);
            String item = String.valueOf(m.get("item"));
            double chance = DefinitionParser.number(m.get("chance"), 0);
            Set<EntityType> types = new java.util.HashSet<>();
            if (m.get("entities") instanceof List<?> list) {
                for (Object o : list) {
                    EntityType type = DefinitionParser.parseEntity(String.valueOf(o), "mob-drops." + item, errors);
                    if (type != null) {
                        types.add(type);
                    }
                }
            }
            Set<String> worlds = new java.util.HashSet<>();
            if (m.get("worlds") instanceof List<?> list) {
                list.forEach(o -> worlds.add(String.valueOf(o)));
            }
            if (!DefinitionParser.ID.matcher(item).matches() || chance <= 0 || chance > 1 || types.isEmpty()) {
                errors.add("mob-drops: entry '" + item + "' needs an item id, entities and a chance in (0, 1]");
                continue;
            }
            drops.add(new MobDrop(item, types, worlds, chance, !Boolean.FALSE.equals(m.get("magic-find")),
                Boolean.TRUE.equals(m.get("allow-spawner"))));
        }
        Map<String, RecipeSpec> recipes = new LinkedHashMap<>();
        ConfigurationSection rs = yaml.getConfigurationSection("recipes");
        if (rs != null) {
            for (String item : rs.getKeys(false)) {
                ConfigurationSection r = rs.getConfigurationSection(item);
                if (r == null || !r.getBoolean("enabled", true)) {
                    continue;
                }
                List<String> shape = r.getStringList("shape");
                Map<Character, Material> ingredients = new LinkedHashMap<>();
                ConfigurationSection ing = r.getConfigurationSection("ingredients");
                boolean ok = !shape.isEmpty() && shape.size() <= 3 && ing != null;
                if (ok) {
                    for (String key : ing.getKeys(false)) {
                        Material m = Material.matchMaterial(ing.getString(key, ""));
                        if (key.length() != 1 || m == null || !m.isItem()) {
                            ok = false;
                            errors.add("recipes." + item + ": bad ingredient " + key);
                        } else {
                            ingredients.put(key.charAt(0), m);
                        }
                    }
                    for (String row : shape) {
                        if (row.length() < 1 || row.length() > 3) {
                            ok = false;
                        }
                        for (char c : row.toCharArray()) {
                            if (c != ' ' && !ingredients.containsKey(c)) {
                                ok = false;
                                errors.add("recipes." + item + ": shape uses '" + c + "' with no ingredient");
                            }
                        }
                    }
                }
                if (ok) {
                    recipes.put(item.toLowerCase(Locale.ROOT), new RecipeSpec(item.toLowerCase(Locale.ROOT), shape, ingredients));
                } else {
                    errors.add("recipes." + item + ": invalid shape or ingredients, recipe skipped");
                }
            }
        }
        Map<String, ConfigurationSection> overrides = new LinkedHashMap<>();
        ConfigurationSection items = yaml.getConfigurationSection("items");
        if (items != null) {
            for (String id : items.getKeys(false)) {
                ConfigurationSection c = items.getConfigurationSection(id);
                if (c != null) {
                    overrides.put(id, c);
                }
            }
        }
        return new BossConfig(settings, bosses, slayers, world, slayerSettings, drops, recipes, overrides, errors);
    }
}
