package win.vampyuyu.survival.boss.slayer;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.EntityType;
import org.jspecify.annotations.Nullable;
import win.vampyuyu.survival.api.Stat;
import win.vampyuyu.survival.boss.AbilitySpec;
import win.vampyuyu.survival.boss.BossDefinition;
import win.vampyuyu.survival.boss.DefinitionParser;

/** Reads the {@code slayers:} section of boss.yml into {@link SlayerDefinition}s and their generated tier bosses. */
public final class SlayerParser {
    public static final int TIERS = 5;
    public static final int LEVELS = 9;

    private SlayerParser() {}

    public static Map<String, SlayerDefinition> parseAll(@Nullable ConfigurationSection root, List<String> errors) {
        Map<String, SlayerDefinition> result = new LinkedHashMap<>();
        if (root == null) {
            return result;
        }
        for (String id : root.getKeys(false)) {
            ConfigurationSection s = root.getConfigurationSection(id);
            if (s == null) {
                errors.add("slayers." + id + ": not a section");
                continue;
            }
            SlayerDefinition def = parse(id, s, errors);
            if (def != null) {
                result.put(id, def);
            }
        }
        return result;
    }

    public static @Nullable SlayerDefinition parse(String id, ConfigurationSection s, List<String> errors) {
        int before = errors.size();
        String path = "slayers." + id;
        if (!DefinitionParser.ID.matcher(id).matches()) {
            errors.add(path + ": id must match [a-z0-9_]+");
            return null;
        }
        String name = s.getString("name", id);
        Material icon = Material.matchMaterial(s.getString("icon", "ROTTEN_FLESH"));
        if (icon == null || icon.isAir() || !icon.isItem()) {
            errors.add(path + ".icon: unknown item");
            icon = Material.BARRIER;
        }
        EntityType bossEntity = DefinitionParser.parseEntity(s.getString("entity", ""), path + ".entity", errors);
        List<EntityType> targets = new ArrayList<>();
        for (String text : s.getStringList("targets")) {
            EntityType type = DefinitionParser.parseEntity(text, path + ".targets", errors);
            if (type != null) {
                targets.add(type);
            }
        }
        if (targets.isEmpty()) {
            errors.add(path + ".targets: at least one mob type is required");
        }
        Map<EntityType, Double> killXp = new LinkedHashMap<>();
        double defaultKill = 5;
        ConfigurationSection kx = s.getConfigurationSection("kill-xp");
        if (kx != null) {
            for (String key : kx.getKeys(false)) {
                if (key.equals("default")) {
                    defaultKill = kx.getDouble(key);
                    continue;
                }
                EntityType type = DefinitionParser.parseEntity(key, path + ".kill-xp", errors);
                if (type != null) {
                    killXp.put(type, kx.getDouble(key));
                }
            }
        }
        if (defaultKill <= 0) {
            errors.add(path + ".kill-xp.default: must be positive");
        }
        List<String> worlds = s.getStringList("worlds");
        List<Long> levelXp = new ArrayList<>();
        for (Number n : s.getList("level-xp", List.of()).stream().filter(Number.class::isInstance)
            .map(Number.class::cast).toList()) {
            levelXp.add(n.longValue());
        }
        if (levelXp.size() != LEVELS) {
            errors.add(path + ".level-xp: exactly " + LEVELS + " values required");
        } else {
            for (int i = 1; i < LEVELS; i++) {
                if (levelXp.get(i) <= levelXp.get(i - 1)) {
                    errors.add(path + ".level-xp: values must be strictly increasing");
                    break;
                }
            }
        }
        List<SlayerDefinition.Perk> perks = new ArrayList<>();
        for (Map<?, ?> raw : s.getMapList("perks")) {
            Map<String, Object> m = DefinitionParser.stringKeys(raw);
            int level = (int) DefinitionParser.number(m.get("level"), 0);
            Stat stat;
            try {
                stat = Stat.valueOf(String.valueOf(m.get("stat")).toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                errors.add(path + ".perks: unknown stat " + m.get("stat"));
                continue;
            }
            if (level < 1 || level > LEVELS) {
                errors.add(path + ".perks: level must be 1-" + LEVELS);
                continue;
            }
            perks.add(new SlayerDefinition.Perk(level, stat, DefinitionParser.number(m.get("amount"), 0)));
        }
        double[] cost = tierValues(s, "costs", path, errors, true);
        double[] required = tierValues(s, "required-xp", path, errors, true);
        double[] health = tierValues(s, "health", path, errors, true);
        double[] damage = tierValues(s, "damage", path, errors, true);
        double[] slayerXp = tierValues(s, "slayer-xp", path, errors, true);
        double[] limit = tierValues(s, "time-limit", path, errors, true);
        double[] coins = tierValues(s, "coins", path, errors, false);
        double[] combatXp = tierValues(s, "combat-xp", path, errors, false);
        double[] speed = tierValues(s, "speed", path, errors, false);
        double[] scale = tierValues(s, "scale", path, errors, false);
        double[] armor = tierValues(s, "armor", path, errors, false);
        double[] unlock = tierValues(s, "unlock-level", path, errors, false);
        ConfigurationSection bar = s.getConfigurationSection("bar");
        String color = bar == null ? "RED" : bar.getString("color", "RED").toUpperCase(Locale.ROOT);
        String overlay = bar == null ? "PROGRESS" : bar.getString("overlay", "PROGRESS").toUpperCase(Locale.ROOT);
        Map<String, BossDefinition.EquipSpec> equipment = DefinitionParser.parseEquipment(
            s.getConfigurationSection("equipment"), path + ".equipment", errors);
        Map<String, Object> options = new LinkedHashMap<>();
        if (s.getConfigurationSection("options") != null) {
            options.putAll(s.getConfigurationSection("options").getValues(false));
        }
        List<AbilitySpec> abilities = DefinitionParser.parseAbilities(s.getMapList("abilities"),
            path + ".abilities", errors);
        BossDefinition.Loot baseLoot = DefinitionParser.parseLoot(s.getConfigurationSection("loot"), path + ".loot", errors);
        ConfigurationSection frenzy = s.getConfigurationSection("frenzy");
        if (errors.size() > before || bossEntity == null) {
            return null;
        }
        List<SlayerDefinition.Tier> tiers = new ArrayList<>();
        for (int t = 1; t <= TIERS; t++) {
            final int tier = t;
            int i = t - 1;
            List<AbilitySpec> active = abilities.stream().filter(a -> a.fromTier() <= tier).toList();
            List<BossDefinition.Phase> phases = new ArrayList<>();
            phases.add(new BossDefinition.Phase("Hunt", 1.0, "", "", 1.0, 1.0, List.of(), active));
            if (frenzy != null && t >= frenzy.getInt("from-tier", 3)) {
                double at = frenzy.getDouble("at", 0.4);
                if (at > 0 && at < 1) {
                    phases.add(new BossDefinition.Phase("Frenzy", at, frenzy.getString("title", "<red><bold>FRENZY"),
                        frenzy.getString("subtitle", "<gray>It is enraged!"), frenzy.getDouble("damage", 1.25),
                        frenzy.getDouble("speed", 1.2), List.of(), active));
                }
            }
            String bossName = name + " " + SlayerDefinition.roman(t);
            List<BossDefinition.LootEntry> entries = baseLoot.entries().stream().filter(e -> e.fromTier() <= tier).toList();
            BossDefinition.Loot loot = new BossDefinition.Loot(coins[i], combatXp[i], baseLoot.minShare(),
                baseLoot.shareFloor(), baseLoot.rankBonus(), entries, -1, baseLoot.announceBelow());
            BossDefinition.Arena arena = new BossDefinition.Arena(36, 48, 48, false, false, false, 1, 0, 1, 0, 1.5, 0,
                25, (int) limit[i] + 30, 8);
            BossDefinition boss = new BossDefinition(SlayerDefinition.bossId(id, t), "<red>" + name + " <yellow>"
                + SlayerDefinition.roman(t), "Slayer boss tier " + t, bossEntity, health[i], damage[i],
                armor[i], scale[i], speed[i], 0.8, color, overlay, arena, phases, equipment, options, loot, null,
                BossDefinition.Kind.SLAYER);
            tiers.add(new SlayerDefinition.Tier(t, cost[i], required[i], (long) slayerXp[i], (int) limit[i],
                (int) unlock[i], boss));
        }
        return new SlayerDefinition(id, name, icon, s.getString("description", ""), targets, killXp, defaultKill,
            worlds, levelXp, perks, tiers);
    }

    /** A list of five numbers, or one number repeated; optional lists default to the fallback. */
    static double[] tierValues(ConfigurationSection s, String key, String path, List<String> errors, boolean required) {
        double[] out = new double[TIERS];
        double fallback = switch (key) {
            case "speed", "scale" -> 1.0;
            default -> 0;
        };
        if (!s.contains(key)) {
            if (required) {
                errors.add(path + "." + key + ": missing (five values, tier I to V)");
            }
            java.util.Arrays.fill(out, fallback);
            return out;
        }
        if (s.isList(key)) {
            List<?> list = s.getList(key);
            if (list == null || list.size() != TIERS || !list.stream().allMatch(Number.class::isInstance)) {
                errors.add(path + "." + key + ": expected five numbers");
                java.util.Arrays.fill(out, fallback);
                return out;
            }
            for (int i = 0; i < TIERS; i++) {
                out[i] = ((Number) list.get(i)).doubleValue();
            }
        } else {
            java.util.Arrays.fill(out, s.getDouble(key));
        }
        for (double v : out) {
            if (v < 0 || (required && v == 0 && !key.equals("coins"))) {
                errors.add(path + "." + key + ": values must be positive");
                break;
            }
        }
        return out;
    }
}
