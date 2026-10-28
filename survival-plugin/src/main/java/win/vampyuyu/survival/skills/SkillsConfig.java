package win.vampyuyu.survival.skills;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.EntityType;
import org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason;
import win.vampyuyu.survival.api.Skill;
import win.vampyuyu.survival.api.Stat;
import win.vampyuyu.survival.core.Rarity;

/**
 * Parsed, immutable view of {@code skills.yml}. Parsing never throws for a bad entry: it skips it and records a
 * warning (see {@link #warnings}) so a typo in one material does not disable the whole feature.
 */
public final class SkillsConfig {
    /** One run of levels that each give {@code amount} of a stat (levels {@code from..to} inclusive). */
    public record StatRange(int from, int to, double amount) {}

    /** Stat bonus of a skill: the sum of its ranges up to the player's level. */
    public record StatReward(Stat stat, List<StatRange> ranges) {
        public double total(int level) {
            double sum = 0;
            for (StatRange range : ranges) {
                int upTo = Math.min(level, range.to());
                if (upTo >= range.from()) {
                    sum += (upTo - range.from() + 1) * range.amount();
                }
            }
            return sum;
        }

        /** Bonus granted by reaching exactly {@code level}. */
        public double at(int level) {
            for (StatRange range : ranges) {
                if (level >= range.from() && level <= range.to()) {
                    return range.amount();
                }
            }
            return 0;
        }
    }

    public record SkillDef(Skill skill, int maxLevel, double multiplier, List<StatReward> stats,
            Map<Integer, String> unlocks) {}

    /** Coins at each level: {@code base + step * level}, times {@code milestoneMultiplier} on every {@code milestoneEvery}. */
    public record LevelCoins(double base, double step, int milestoneEvery, double milestoneMultiplier) {
        public double at(int level) {
            double value = base + step * level;
            if (milestoneEvery > 0 && level % milestoneEvery == 0) {
                value *= milestoneMultiplier;
            }
            return Math.floor(value);
        }
    }

    public record CollectionDef(String id, String name, Skill category, Material icon, Set<Material> materials,
            List<Long> tiers, List<Double> coins) {
        public int tierOf(long amount) {
            int tier = 0;
            for (int i = 0; i < tiers.size(); i++) {
                if (amount >= tiers.get(i)) {
                    tier = i + 1;
                }
            }
            return tier;
        }

        public int maxTier() {
            return tiers.size();
        }

        public double coinsFor(int tier) {
            return tier >= 1 && tier <= coins.size() ? coins.get(tier - 1) : 0;
        }
    }

    /** One resource of a custom recipe: a vanilla material or a custom item id, {@code slots} stacks of {@code perSlot}. */
    public record Ingredient(Material material, String customId, int slots, int perSlot) {
        public boolean custom() {
            return customId != null;
        }
    }

    public record RecipeDef(String id, String resultCustom, Material resultMaterial, int amount,
            List<Ingredient> ingredients, Map<String, Integer> requires, Skill xpSkill, double xpAmount) {
        public int totalSlots() {
            return ingredients.stream().mapToInt(Ingredient::slots).sum();
        }
    }

    public record EnchantedDef(String id, String name, Material material, Rarity rarity, String model, int tier,
            List<String> lore) {}

    /** Diminishing returns for spamming one spot. */
    public record AntiFarmCfg(int cell, long windowMillis, int free, double step, double min) {}

    public final boolean onlySkyblockWorlds;
    public final Set<String> blockedWorlds;
    public final double globalMultiplier;
    public final double boostCap;
    public final LevelTable levels;
    public final int defaultMaxLevel;
    public final Map<Skill, SkillDef> skills;
    public final LevelCoins levelCoins;
    public final Set<Skill> averageSkills;
    public final Map<Material, Double> miningBlocks;
    public final Map<Material, Double> foragingBlocks;
    public final Map<Material, Double> farmingBlocks;
    public final Set<Material> matureOnly;
    public final Map<Material, Double> blockXp;
    public final Map<Material, Skill> blockSkill;
    public final Map<EntityType, Double> mobXp;
    public final double healthFactor;
    public final double maxPerKill;
    public final Set<EntityType> blockedEntities;
    public final Set<SpawnReason> blockedSpawnReasons;
    public final Map<Material, Double> catchXp;
    public final double defaultCatchXp;
    public final Map<EntityType, Double> seaCreatureXp;
    public final double defaultSeaCreatureXp;
    public final double enchantPerCostLevel;
    public final double enchantPerLevel;
    public final Map<Material, Double> brewIngredientXp;
    public final double defaultBrewXp;
    public final Map<Material, Double> craftXp;
    public final double defaultCraftXp;
    public final AntiFarmCfg mobAntiFarm;
    public final AntiFarmCfg blockAntiFarm;
    public final boolean fortuneEnabled;
    public final double fortuneMaxMultiplier;
    public final Set<Material> trackedPlaced;
    public final Map<String, CollectionDef> collections;
    public final Map<Material, CollectionDef> collectionByMaterial;
    public final Map<String, EnchantedDef> enchantedItems;
    public final Map<String, RecipeDef> recipes;
    public final long leaderboardRefreshSeconds;
    public final int leaderboardSize;
    public final int leaderboardMaxFiles;
    public final int craftXpMaxBatch;
    public final List<String> warnings;

    public SkillsConfig(ConfigurationSection root) {
        List<String> warn = new ArrayList<>();
        onlySkyblockWorlds = root.getBoolean("only_skyblock_worlds", true);
        blockedWorlds = Set.copyOf(root.getStringList("blocked_worlds"));
        globalMultiplier = Math.max(0, root.getDouble("xp_multiplier", 1.0));
        boostCap = Math.max(1, root.getDouble("boosts.max_total", 10.0));
        defaultMaxLevel = Math.max(1, root.getInt("levels.default_max", 60));

        int tableSize = Math.max(defaultMaxLevel, 1);
        List<Double> explicit = new ArrayList<>();
        for (Object o : root.getList("levels.xp_table", List.of())) {
            if (o instanceof Number n) {
                explicit.add(n.doubleValue());
            }
        }
        for (Skill skill : Skill.values()) {
            tableSize = Math.max(tableSize, root.getInt("skills." + skill.key() + ".max_level", defaultMaxLevel));
        }
        LevelTable table;
        try {
            table = LevelTable.build(explicit, tableSize, root.getDouble("levels.base", 50),
                root.getDouble("levels.growth", 1.2), root.getInt("levels.significant_digits", 2));
        } catch (IllegalArgumentException e) {
            warn.add("levels: " + e.getMessage() + "; using the default curve");
            table = LevelTable.build(null, tableSize, 50, 1.2, 2);
        }
        levels = table;

        Map<Skill, SkillDef> defs = new EnumMap<>(Skill.class);
        for (Skill skill : Skill.values()) {
            defs.put(skill, parseSkill(root, skill, warn));
        }
        skills = Collections.unmodifiableMap(defs);

        levelCoins = new LevelCoins(root.getDouble("rewards.coins.base", 200),
            root.getDouble("rewards.coins.step", 40), root.getInt("rewards.coins.milestone_every", 10),
            root.getDouble("rewards.coins.milestone_multiplier", 5));
        Set<Skill> avg = EnumSet.noneOf(Skill.class);
        List<String> avgNames = root.getStringList("average.skills");
        if (avgNames.isEmpty()) {
            avg.addAll(EnumSet.allOf(Skill.class));
            avg.remove(Skill.CARPENTRY);
        } else {
            for (String name : avgNames) {
                Skill s = skillByName(name);
                if (s == null) {
                    warn.add("average.skills: unknown skill " + name);
                } else {
                    avg.add(s);
                }
            }
        }
        averageSkills = Collections.unmodifiableSet(avg);

        miningBlocks = materials(root.getConfigurationSection("xp.mining.blocks"), "xp.mining.blocks", warn);
        foragingBlocks = materials(root.getConfigurationSection("xp.foraging.blocks"), "xp.foraging.blocks", warn);
        farmingBlocks = materials(root.getConfigurationSection("xp.farming.blocks"), "xp.farming.blocks", warn);
        matureOnly = materialSet(root.getStringList("xp.farming.mature_only"), "xp.farming.mature_only", warn);
        Map<Material, Double> allBlocks = new LinkedHashMap<>();
        Map<Material, Skill> skillOf = new LinkedHashMap<>();
        for (var e : miningBlocks.entrySet()) {
            allBlocks.put(e.getKey(), e.getValue());
            skillOf.put(e.getKey(), Skill.MINING);
        }
        for (var e : foragingBlocks.entrySet()) {
            allBlocks.put(e.getKey(), e.getValue());
            skillOf.put(e.getKey(), Skill.FORAGING);
        }
        for (var e : farmingBlocks.entrySet()) {
            allBlocks.put(e.getKey(), e.getValue());
            skillOf.put(e.getKey(), Skill.FARMING);
        }
        blockXp = Collections.unmodifiableMap(allBlocks);
        blockSkill = Collections.unmodifiableMap(skillOf);

        mobXp = entities(root.getConfigurationSection("xp.combat.mobs"), "xp.combat.mobs", warn);
        healthFactor = Math.max(0, root.getDouble("xp.combat.health_factor", 0.4));
        maxPerKill = Math.max(0, root.getDouble("xp.combat.max_per_kill", 250));
        blockedEntities = Set.copyOf(entitySet(root.getStringList("xp.combat.blocked_entities"),
            "xp.combat.blocked_entities", warn));
        Set<SpawnReason> reasons = EnumSet.noneOf(SpawnReason.class);
        for (String name : root.getStringList("xp.combat.blocked_spawn_reasons")) {
            try {
                reasons.add(SpawnReason.valueOf(name.toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException e) {
                warn.add("xp.combat.blocked_spawn_reasons: unknown reason " + name);
            }
        }
        blockedSpawnReasons = Collections.unmodifiableSet(reasons);

        catchXp = materials(root.getConfigurationSection("xp.fishing.catches"), "xp.fishing.catches", warn);
        defaultCatchXp = Math.max(0, root.getDouble("xp.fishing.default_catch", 10));
        seaCreatureXp = entities(root.getConfigurationSection("xp.fishing.sea_creatures"), "xp.fishing.sea_creatures", warn);
        defaultSeaCreatureXp = Math.max(0, root.getDouble("xp.fishing.default_sea_creature", 60));
        enchantPerCostLevel = Math.max(0, root.getDouble("xp.enchanting.per_cost_level", 3));
        enchantPerLevel = Math.max(0, root.getDouble("xp.enchanting.per_enchant_level", 4));
        brewIngredientXp = materials(root.getConfigurationSection("xp.alchemy.ingredients"), "xp.alchemy.ingredients", warn);
        defaultBrewXp = Math.max(0, root.getDouble("xp.alchemy.default", 6));
        craftXp = materials(root.getConfigurationSection("xp.carpentry.crafts"), "xp.carpentry.crafts", warn);
        defaultCraftXp = Math.max(0, root.getDouble("xp.carpentry.default", 0));
        craftXpMaxBatch = Math.max(1, root.getInt("xp.carpentry.max_batch", 64));

        mobAntiFarm = antiFarm(root.getConfigurationSection("anti_farm.mobs"), new AntiFarmCfg(4, 120_000, 25, 0.04, 0.1));
        blockAntiFarm = antiFarm(root.getConfigurationSection("anti_farm.blocks"), new AntiFarmCfg(1, 30_000, 4, 0.2, 0.1));
        fortuneEnabled = root.getBoolean("fortune.enabled", true);
        fortuneMaxMultiplier = Math.max(1, root.getDouble("fortune.max_multiplier", 10));

        // collections
        Map<String, CollectionDef> cols = new LinkedHashMap<>();
        Map<Material, CollectionDef> byMat = new LinkedHashMap<>();
        ConfigurationSection items = root.getConfigurationSection("collections.items");
        if (items != null) {
            for (String id : items.getKeys(false)) {
                CollectionDef def = parseCollection(root, items.getConfigurationSection(id), id, warn);
                if (def == null) {
                    continue;
                }
                cols.put(def.id(), def);
                for (Material m : def.materials()) {
                    if (byMat.putIfAbsent(m, def) != null) {
                        warn.add("collections: " + m + " is in two collections; " + byMat.get(m).id() + " wins");
                    }
                }
            }
        }
        collections = Collections.unmodifiableMap(cols);
        collectionByMaterial = Collections.unmodifiableMap(byMat);

        Set<Material> tracked = EnumSet.noneOf(Material.class);
        tracked.addAll(blockXp.keySet());
        for (CollectionDef def : cols.values()) {
            for (Material m : def.materials()) {
                if (m.isBlock()) {
                    tracked.add(m);
                }
            }
        }
        tracked.addAll(materialSet(root.getStringList("placed_tracking.extra"), "placed_tracking.extra", warn));
        trackedPlaced = Collections.unmodifiableSet(tracked);

        Map<String, EnchantedDef> ench = new LinkedHashMap<>();
        ConfigurationSection enchSec = root.getConfigurationSection("enchanted_items");
        if (enchSec != null) {
            for (String id : enchSec.getKeys(false)) {
                ConfigurationSection s = enchSec.getConfigurationSection(id);
                Material mat = s == null ? null : Material.matchMaterial(s.getString("material", ""));
                if (!id.matches("[a-z0-9_]+") || mat == null) {
                    warn.add("enchanted_items." + id + ": bad id or material");
                    continue;
                }
                Rarity rarity = Rarity.UNCOMMON;
                try {
                    rarity = Rarity.valueOf(s.getString("rarity", "UNCOMMON").toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException e) {
                    warn.add("enchanted_items." + id + ": unknown rarity");
                }
                ench.put(id, new EnchantedDef(id, s.getString("name", id), mat, rarity,
                    s.getString("model", "vampyuyu:skills/" + id), s.getInt("tier", 1), s.getStringList("lore")));
            }
        }
        enchantedItems = Collections.unmodifiableMap(ench);

        Map<String, RecipeDef> recs = new LinkedHashMap<>();
        ConfigurationSection recSec = root.getConfigurationSection("recipes");
        if (recSec != null) {
            for (String id : recSec.getKeys(false)) {
                RecipeDef def = parseRecipe(recSec.getConfigurationSection(id), id, warn);
                if (def != null) {
                    recs.put(id, def);
                }
            }
        }
        recipes = Collections.unmodifiableMap(recs);

        leaderboardRefreshSeconds = Math.max(10, root.getLong("leaderboard.refresh_seconds", 300));
        leaderboardSize = Math.max(3, root.getInt("leaderboard.size", 100));
        leaderboardMaxFiles = Math.max(100, root.getInt("leaderboard.max_files", 20000));
        warnings = List.copyOf(warn);
    }

    // ---------------------------------------------------------------- lookups

    public SkillDef skill(Skill skill) {
        return skills.get(skill);
    }

    public int maxLevel(Skill skill) {
        return skills.get(skill).maxLevel();
    }

    /** True when xp may be earned in this world. */
    public boolean worldAllowed(String worldName) {
        if (worldName == null || blockedWorlds.contains(worldName)) {
            return false;
        }
        return !onlySkyblockWorlds || win.vampyuyu.survival.core.SkyblockWorlds.isSkyblock(worldName);
    }

    public static Skill skillByName(String name) {
        if (name == null) {
            return null;
        }
        String upper = name.trim().toUpperCase(Locale.ROOT);
        for (Skill s : Skill.values()) {
            if (s.name().equals(upper)) {
                return s;
            }
        }
        return null;
    }

    // ---------------------------------------------------------------- parsing helpers

    private SkillDef parseSkill(ConfigurationSection root, Skill skill, List<String> warn) {
        String path = "skills." + skill.key();
        int max = Math.max(1, Math.min(root.getInt(path + ".max_level", defaultMaxLevel), levels.maxLevel()));
        double mult = Math.max(0, root.getDouble(path + ".multiplier", 1.0));
        List<StatReward> stats = new ArrayList<>();
        ConfigurationSection statSec = root.getConfigurationSection(path + ".stats");
        if (statSec != null) {
            for (String name : statSec.getKeys(false)) {
                Stat stat;
                try {
                    stat = Stat.valueOf(name.toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException e) {
                    warn.add(path + ".stats: unknown stat " + name);
                    continue;
                }
                List<StatRange> ranges = new ArrayList<>();
                for (Map<?, ?> m : statSec.getMapList(name)) {
                    int from = intOf(m.get("from"), 1);
                    int to = intOf(m.get("to"), max);
                    double amount = m.get("amount") instanceof Number n ? n.doubleValue() : 0;
                    if (to >= from && amount != 0) {
                        ranges.add(new StatRange(from, to, amount));
                    }
                }
                if (!ranges.isEmpty()) {
                    stats.add(new StatReward(stat, List.copyOf(ranges)));
                }
            }
        }
        Map<Integer, String> unlocks = new LinkedHashMap<>();
        ConfigurationSection unlockSec = root.getConfigurationSection(path + ".unlocks");
        if (unlockSec != null) {
            for (String key : unlockSec.getKeys(false)) {
                try {
                    unlocks.put(Integer.parseInt(key), unlockSec.getString(key, ""));
                } catch (NumberFormatException e) {
                    warn.add(path + ".unlocks: level " + key + " is not a number");
                }
            }
        }
        return new SkillDef(skill, max, mult, List.copyOf(stats), Collections.unmodifiableMap(unlocks));
    }

    private static int intOf(Object o, int fallback) {
        return o instanceof Number n ? n.intValue() : fallback;
    }

    private CollectionDef parseCollection(ConfigurationSection root, ConfigurationSection s, String id, List<String> warn) {
        if (s == null) {
            return null;
        }
        Skill category = skillByName(s.getString("category", ""));
        if (category == null) {
            warn.add("collections." + id + ": unknown category");
            return null;
        }
        Set<Material> mats = new java.util.LinkedHashSet<>();
        List<String> names = s.getStringList("materials");
        if (names.isEmpty()) {
            names = List.of(id);
        }
        mats.addAll(materialSet(names, "collections." + id + ".materials", warn));
        if (mats.isEmpty()) {
            warn.add("collections." + id + ": no valid material");
            return null;
        }
        Material icon = Material.matchMaterial(s.getString("icon", ""));
        if (icon == null) {
            icon = mats.iterator().next();
        }
        String preset = s.getString("preset", "common");
        List<Long> tiers = new ArrayList<>();
        for (Object o : root.getList("collections.presets." + preset + ".tiers", List.of())) {
            if (o instanceof Number n && n.longValue() > 0) {
                tiers.add(n.longValue());
            }
        }
        if (tiers.isEmpty()) {
            warn.add("collections." + id + ": preset " + preset + " has no tiers");
            return null;
        }
        List<Double> coins = new ArrayList<>();
        for (Object o : root.getList("collections.presets." + preset + ".coins", List.of())) {
            coins.add(o instanceof Number n ? Math.max(0, n.doubleValue()) : 0.0);
        }
        return new CollectionDef(id.toUpperCase(Locale.ROOT), s.getString("name", prettify(id)), category, icon,
            Collections.unmodifiableSet(mats), List.copyOf(tiers), List.copyOf(coins));
    }

    private RecipeDef parseRecipe(ConfigurationSection s, String id, List<String> warn) {
        String path = "recipes." + id;
        if (s == null || !id.matches("[a-z0-9_]+")) {
            warn.add(path + ": bad id");
            return null;
        }
        String resultCustom = s.getString("result_custom");
        Material resultMat = s.contains("result_item") ? Material.matchMaterial(s.getString("result_item", "")) : null;
        if (resultCustom == null && resultMat == null) {
            warn.add(path + ": needs result_custom or a valid result_item");
            return null;
        }
        List<Ingredient> ingredients = new ArrayList<>();
        int total = 0;
        for (Map<?, ?> m : s.getMapList("ingredients")) {
            int slots = Math.max(1, intOf(m.get("slots"), 1));
            int perSlot = Math.max(1, intOf(m.get("per_slot"), 1));
            Object custom = m.get("custom");
            Object item = m.get("item");
            Ingredient ingredient = null;
            if (custom instanceof String c) {
                ingredient = new Ingredient(null, c, slots, perSlot);
            } else if (item instanceof String name) {
                Material mat = Material.matchMaterial(name);
                if (mat != null && !mat.isAir() && mat.isItem()) {
                    ingredient = new Ingredient(mat, null, slots, perSlot);
                }
            }
            if (ingredient == null) {
                warn.add(path + ": bad ingredient " + m);
                return null;
            }
            total += slots;
            ingredients.add(ingredient);
        }
        if (ingredients.isEmpty() || total > 9) {
            warn.add(path + ": needs 1 to 9 ingredient slots in total");
            return null;
        }
        Map<String, Integer> requires = new LinkedHashMap<>();
        ConfigurationSection req = s.getConfigurationSection("requires");
        if (req != null) {
            for (String key : req.getKeys(false)) {
                requires.put(key.toUpperCase(Locale.ROOT), Math.max(1, req.getInt(key)));
            }
        }
        Skill xpSkill = skillByName(s.getString("xp.skill"));
        return new RecipeDef(id, resultCustom, resultMat, Math.max(1, Math.min(64, s.getInt("amount", 1))),
            List.copyOf(ingredients), Collections.unmodifiableMap(requires), xpSkill,
            Math.max(0, s.getDouble("xp.amount", 0)));
    }

    private static AntiFarmCfg antiFarm(ConfigurationSection s, AntiFarmCfg defaults) {
        if (s == null) {
            return defaults;
        }
        return new AntiFarmCfg(Math.max(1, s.getInt("cell", defaults.cell())),
            Math.max(1, s.getLong("window_seconds", defaults.windowMillis() / 1000)) * 1000,
            Math.max(0, s.getInt("free", defaults.free())), Math.max(0, s.getDouble("step", defaults.step())),
            Math.max(0, Math.min(1, s.getDouble("min", defaults.min()))));
    }

    private static Map<Material, Double> materials(ConfigurationSection s, String path, List<String> warn) {
        Map<Material, Double> out = new LinkedHashMap<>();
        if (s != null) {
            for (String key : s.getKeys(false)) {
                Material m = Material.matchMaterial(key);
                if (m == null) {
                    warn.add(path + ": unknown material " + key);
                } else {
                    out.put(m, Math.max(0, s.getDouble(key)));
                }
            }
        }
        return Collections.unmodifiableMap(out);
    }

    private static Set<Material> materialSet(List<String> names, String path, List<String> warn) {
        Set<Material> out = new java.util.LinkedHashSet<>(); // keeps the config order (the first one is the icon)
        for (String name : names) {
            Material m = Material.matchMaterial(name);
            if (m == null) {
                warn.add(path + ": unknown material " + name);
            } else {
                out.add(m);
            }
        }
        return out;
    }

    private static Map<EntityType, Double> entities(ConfigurationSection s, String path, List<String> warn) {
        Map<EntityType, Double> out = new LinkedHashMap<>();
        if (s != null) {
            for (String key : s.getKeys(false)) {
                EntityType type = entityType(key);
                if (type == null) {
                    warn.add(path + ": unknown entity " + key);
                } else {
                    out.put(type, Math.max(0, s.getDouble(key)));
                }
            }
        }
        return Collections.unmodifiableMap(out);
    }

    private static Set<EntityType> entitySet(List<String> names, String path, List<String> warn) {
        Set<EntityType> out = EnumSet.noneOf(EntityType.class);
        for (String name : names) {
            EntityType type = entityType(name);
            if (type == null) {
                warn.add(path + ": unknown entity " + name);
            } else {
                out.add(type);
            }
        }
        return out;
    }

    private static EntityType entityType(String name) {
        try {
            return EntityType.valueOf(name.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** "NETHER_WART" to "Nether Wart". */
    public static String prettify(String id) {
        StringBuilder out = new StringBuilder();
        for (String part : id.toLowerCase(Locale.ROOT).split("[_ ]+")) {
            if (part.isEmpty()) {
                continue;
            }
            if (out.length() > 0) {
                out.append(' ');
            }
            out.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return out.toString();
    }
}
