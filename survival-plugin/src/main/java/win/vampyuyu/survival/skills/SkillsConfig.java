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
import org.bukkit.entity.EntityType;
import org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason;
import win.vampyuyu.survival.api.Skill;
import win.vampyuyu.survival.api.Stat;
import win.vampyuyu.survival.core.Rarity;
import win.vampyuyu.survival.data.Settings;

/**
 * Immutable view of the skills tuning read from the content database by {@link SkillsRepo}. Parsing never throws for a bad entry: it skips it and records a
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

    /** One sea creature that can bite: its entity, the minimum Fishing level, spawn weight, health and display name. */
    public record SeaCreatureDef(String id, EntityType type, String name, int minLevel, double weight, double health) {}

    /** Sea creature fishing: chance per catch, caps and the creature table. */
    public record SeaCreatureCfg(boolean enabled, double baseChance, double perLevel, double perStat, double maxChance,
            int maxAlivePerPlayer, long despawnSeconds, List<SeaCreatureDef> creatures) {}

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
    /** Plants that grow upwards from a base block (sugar cane, cactus, bamboo): the whole column pays on a break. */
    public final Set<Material> columnPlants;
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
    public final double enchantAnvilPerLevel;
    public final double brewOwnerRadius;
    public final boolean guardEnchantedItems;
    public final SeaCreatureCfg seaCreatures;
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

    SkillsConfig(Settings s, SkillsRepo.Tables t) {
        List<String> warn = new ArrayList<>();
        onlySkyblockWorlds = s.getBoolean("only_skyblock_worlds", true);
        blockedWorlds = Set.copyOf(s.getList("blocked_worlds"));
        globalMultiplier = Math.max(0, s.getDouble("xp_multiplier", 1.0));
        boostCap = Math.max(1, s.getDouble("boosts.max_total", 10.0));
        defaultMaxLevel = Math.max(1, s.getInt("levels.default_max", 60));

        int tableSize = Math.max(defaultMaxLevel, 1);
        List<Double> explicit = new ArrayList<>();
        for (String v : s.getList("levels.xp_table")) {
            try {
                explicit.add(Double.parseDouble(v.trim()));
            } catch (NumberFormatException e) {
                // an entry that is not a number is dropped
            }
        }
        for (Skill skill : Skill.values()) {
            SkillsRepo.SkillRow row = t.skills().get(skill.key());
            tableSize = Math.max(tableSize, row == null ? defaultMaxLevel : row.maxLevel());
        }
        LevelTable table;
        try {
            table = LevelTable.build(explicit, tableSize, s.getDouble("levels.base", 50),
                s.getDouble("levels.growth", 1.2), s.getInt("levels.significant_digits", 2));
        } catch (IllegalArgumentException e) {
            warn.add("levels: " + e.getMessage() + "; using the default curve");
            table = LevelTable.build(null, tableSize, 50, 1.2, 2);
        }
        levels = table;

        Map<Skill, SkillDef> defs = new EnumMap<>(Skill.class);
        for (Skill skill : Skill.values()) {
            defs.put(skill, parseSkill(t, skill, warn));
        }
        skills = Collections.unmodifiableMap(defs);

        levelCoins = new LevelCoins(s.getDouble("rewards.coins.base", 200),
            s.getDouble("rewards.coins.step", 40), s.getInt("rewards.coins.milestone_every", 10),
            s.getDouble("rewards.coins.milestone_multiplier", 5));
        Set<Skill> avg = EnumSet.noneOf(Skill.class);
        List<String> avgNames = s.getList("average.skills");
        if (avgNames.isEmpty()) {
            avg.addAll(EnumSet.allOf(Skill.class));
            avg.remove(Skill.CARPENTRY);
        } else {
            for (String name : avgNames) {
                Skill sk = skillByName(name);
                if (sk == null) {
                    warn.add("average.skills: unknown skill " + name);
                } else {
                    avg.add(sk);
                }
            }
        }
        averageSkills = Collections.unmodifiableSet(avg);

        Map<String, Double> mining = new LinkedHashMap<>();
        Map<String, Double> foraging = new LinkedHashMap<>();
        Map<String, Double> farming = new LinkedHashMap<>();
        List<String> mature = new ArrayList<>();
        List<String> columns = new ArrayList<>();
        for (SkillsRepo.BlockRow row : t.blocks()) {
            switch (row.skill()) {
                case "mining" -> mining.put(row.material(), row.xp());
                case "foraging" -> foraging.put(row.material(), row.xp());
                case "farming" -> farming.put(row.material(), row.xp());
                default -> warn.add("xp blocks: " + row.material() + " has an unknown skill " + row.skill());
            }
            if (row.matureOnly()) {
                mature.add(row.material());
            }
            if (row.columnPlant()) {
                columns.add(row.material());
            }
        }
        miningBlocks = materials(mining, "xp.mining.blocks", warn);
        foragingBlocks = materials(foraging, "xp.foraging.blocks", warn);
        farmingBlocks = materials(farming, "xp.farming.blocks", warn);
        matureOnly = materialSet(mature, "xp.farming.mature_only", warn);
        columnPlants = Collections.unmodifiableSet(materialSet(columns, "xp.farming.column_plants", warn));
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

        mobXp = entities(t.mobXp(), "xp.combat.mobs", warn);
        healthFactor = Math.max(0, s.getDouble("xp.combat.health_factor", 0.4));
        maxPerKill = Math.max(0, s.getDouble("xp.combat.max_per_kill", 250));
        blockedEntities = Set.copyOf(entitySet(s.getList("xp.combat.blocked_entities"),
            "xp.combat.blocked_entities", warn));
        Set<SpawnReason> reasons = EnumSet.noneOf(SpawnReason.class);
        for (String name : s.getList("xp.combat.blocked_spawn_reasons")) {
            try {
                reasons.add(SpawnReason.valueOf(name.toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException e) {
                warn.add("xp.combat.blocked_spawn_reasons: unknown reason " + name);
            }
        }
        blockedSpawnReasons = Collections.unmodifiableSet(reasons);

        catchXp = materials(t.catchXp(), "xp.fishing.catches", warn);
        defaultCatchXp = Math.max(0, s.getDouble("xp.fishing.default_catch", 10));
        seaCreatureXp = entities(t.seaXp(), "xp.fishing.sea_creatures", warn);
        defaultSeaCreatureXp = Math.max(0, s.getDouble("xp.fishing.default_sea_creature", 60));
        enchantPerCostLevel = Math.max(0, s.getDouble("xp.enchanting.per_cost_level", 3));
        enchantPerLevel = Math.max(0, s.getDouble("xp.enchanting.per_enchant_level", 4));
        brewIngredientXp = materials(t.brewXp(), "xp.alchemy.ingredients", warn);
        defaultBrewXp = Math.max(0, s.getDouble("xp.alchemy.default", 6));
        craftXp = materials(t.craftXp(), "xp.carpentry.crafts", warn);
        defaultCraftXp = Math.max(0, s.getDouble("xp.carpentry.default", 0));
        craftXpMaxBatch = Math.max(1, s.getInt("xp.carpentry.max_batch", 64));

        mobAntiFarm = antiFarm(s, "anti_farm.mobs", new AntiFarmCfg(4, 120_000, 25, 0.04, 0.1));
        blockAntiFarm = antiFarm(s, "anti_farm.blocks", new AntiFarmCfg(1, 30_000, 4, 0.2, 0.1));
        // Off by default: the items feature owns the Mining/Farming/Foraging Fortune drops (see the notes).
        fortuneEnabled = s.getBoolean("fortune.enabled", false);
        enchantAnvilPerLevel = Math.max(0, s.getDouble("xp.enchanting.anvil_per_level", 2));
        brewOwnerRadius = Math.max(0, s.getDouble("xp.alchemy.owner_radius", 24));
        guardEnchantedItems = s.getBoolean("enchanted_guard.enabled", true);
        seaCreatures = parseSeaCreatures(s, t.seaCreatures(), warn);
        fortuneMaxMultiplier = Math.max(1, s.getDouble("fortune.max_multiplier", 10));

        // collections
        Map<String, CollectionDef> cols = new LinkedHashMap<>();
        Map<Material, CollectionDef> byMat = new LinkedHashMap<>();
        for (SkillsRepo.CollectionRow row : t.collections()) {
            CollectionDef def = parseCollection(t, row, warn);
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
        // A crop is planted as a seedling and only pays when it is fully grown, so planting it must not mark it:
        // the maturity check already stops plant-and-break, and a mark would block the harvest forever.
        tracked.removeAll(matureOnly);
        tracked.addAll(materialSet(s.getList("placed_tracking.extra"), "placed_tracking.extra", warn));
        trackedPlaced = Collections.unmodifiableSet(tracked);

        Map<String, EnchantedDef> ench = new LinkedHashMap<>();
        for (SkillsRepo.EnchantedRow row : t.enchanted()) {
            Material mat = Material.matchMaterial(row.material());
            if (!row.id().matches("[a-z0-9_]+") || mat == null) {
                warn.add("enchanted_items." + row.id() + ": bad id or material");
                continue;
            }
            Rarity rarity = Rarity.UNCOMMON;
            try {
                rarity = Rarity.valueOf(row.rarity().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                warn.add("enchanted_items." + row.id() + ": unknown rarity");
            }
            ench.put(row.id(), new EnchantedDef(row.id(), row.name(), mat, rarity, row.model(), row.tier(), row.lore()));
        }
        enchantedItems = Collections.unmodifiableMap(ench);

        Map<String, RecipeDef> recs = new LinkedHashMap<>();
        for (SkillsRepo.RecipeRow row : t.recipes()) {
            RecipeDef def = parseRecipe(row, warn);
            if (def != null) {
                recs.put(row.id(), def);
            }
        }
        recipes = Collections.unmodifiableMap(recs);

        leaderboardRefreshSeconds = Math.max(10, s.getLong("leaderboard.refresh_seconds", 300));
        leaderboardSize = Math.max(3, s.getInt("leaderboard.size", 100));
        leaderboardMaxFiles = Math.max(100, s.getInt("leaderboard.max_files", 20000));
        s.problems().forEach(p -> warn.add("setting " + p));
        warnings = List.copyOf(warn);
    }

    /** A configuration with no row at all: every value is its default (tests). */
    static SkillsConfig defaults() {
        return new SkillsConfig(Settings.empty("skills"), SkillsRepo.Tables.empty());
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

    private SkillDef parseSkill(SkillsRepo.Tables t, Skill skill, List<String> warn) {
        String path = "skills." + skill.key();
        SkillsRepo.SkillRow row = t.skills().get(skill.key());
        int max = Math.max(1, Math.min(row == null ? defaultMaxLevel : row.maxLevel(), levels.maxLevel()));
        double mult = Math.max(0, row == null ? 1.0 : row.multiplier());
        // ranges grouped by stat, stats in the order they first appear
        Map<String, List<StatRange>> byStat = new LinkedHashMap<>();
        for (SkillsRepo.StatRangeRow r : t.statRanges()) {
            if (r.skillId().equals(skill.key())) {
                List<StatRange> list = byStat.computeIfAbsent(r.stat(), k -> new ArrayList<>());
                if (r.to() >= r.from() && r.amount() != 0) {
                    list.add(new StatRange(r.from(), r.to(), r.amount()));
                }
            }
        }
        // a current or an old stat name; old ones convert (Strength becomes Might), dropped ones give nothing
        Map<Stat, List<StatRange>> merged = new java.util.EnumMap<>(Stat.class);
        for (var e : byStat.entrySet()) {
            if (!Stat.known(e.getKey())) {
                warn.add(path + ".stats: unknown stat " + e.getKey());
                continue;
            }
            for (Stat.Converted conv : Stat.resolve(e.getKey())) {
                for (StatRange range : e.getValue()) {
                    merged.computeIfAbsent(conv.stat(), k -> new ArrayList<>())
                        .add(new StatRange(range.from(), range.to(), range.amount() * conv.factor()));
                }
            }
        }
        List<StatReward> stats = new ArrayList<>();
        for (var e : merged.entrySet()) {
            if (!e.getValue().isEmpty()) {
                stats.add(new StatReward(e.getKey(), List.copyOf(e.getValue())));
            }
        }
        Map<Integer, String> unlocks = new LinkedHashMap<>();
        for (SkillsRepo.UnlockRow u : t.unlocks()) {
            if (u.skillId().equals(skill.key())) {
                unlocks.put(u.level(), u.text());
            }
        }
        return new SkillDef(skill, max, mult, List.copyOf(stats), Collections.unmodifiableMap(unlocks));
    }

    private static SeaCreatureCfg parseSeaCreatures(Settings s, List<SkillsRepo.SeaCreatureRow> rows, List<String> warn) {
        List<SeaCreatureDef> list = new ArrayList<>();
        for (SkillsRepo.SeaCreatureRow c : rows) {
            EntityType type = entityType(c.entity());
            if (type == null || !c.id().matches("[a-z0-9_]+") || type.getEntityClass() == null
                    || !org.bukkit.entity.LivingEntity.class.isAssignableFrom(type.getEntityClass())) {
                warn.add("sea_creatures.creatures." + c.id() + ": bad id or entity");
                continue;
            }
            list.add(new SeaCreatureDef(c.id(), type, c.name(), Math.max(0, c.minLevel()), Math.max(0, c.weight()),
                Math.max(1, c.health())));
        }
        return new SeaCreatureCfg(s.getBoolean("sea_creatures.enabled", true), clamp01(s.getDouble("sea_creatures.base_chance", 0.04)),
            Math.max(0, s.getDouble("sea_creatures.per_fishing_level", 0.0008)),
            Math.max(0, s.getDouble("sea_creatures.per_chance_stat", 0.01)),
            clamp01(s.getDouble("sea_creatures.max_chance", 0.35)), Math.max(1, s.getInt("sea_creatures.max_alive_per_player", 3)),
            Math.max(10, s.getLong("sea_creatures.despawn_seconds", 120)), List.copyOf(list));
    }

    private static double clamp01(double v) {
        return Math.max(0, Math.min(1, v));
    }

    private CollectionDef parseCollection(SkillsRepo.Tables t, SkillsRepo.CollectionRow row, List<String> warn) {
        String id = row.id();
        Skill category = skillByName(row.category());
        if (category == null) {
            warn.add("collections." + id + ": unknown category");
            return null;
        }
        Set<Material> mats = new java.util.LinkedHashSet<>();
        List<String> names = row.materials().isEmpty() ? List.of(id) : row.materials();
        mats.addAll(materialSet(names, "collections." + id + ".materials", warn));
        if (mats.isEmpty()) {
            warn.add("collections." + id + ": no valid material");
            return null;
        }
        Material icon = row.icon() == null ? null : Material.matchMaterial(row.icon());
        if (icon == null) {
            icon = mats.iterator().next();
        }
        SkillsRepo.PresetRow preset = t.presets().get(row.preset());
        List<Long> tiers = new ArrayList<>();
        if (preset != null) {
            for (long n : preset.tiers()) {
                if (n > 0) {
                    tiers.add(n);
                }
            }
        }
        if (tiers.isEmpty()) {
            warn.add("collections." + id + ": preset " + row.preset() + " has no tiers");
            return null;
        }
        List<Double> coins = new ArrayList<>();
        for (double coin : preset.coins()) {
            coins.add(Math.max(0, coin));
        }
        return new CollectionDef(id.toUpperCase(Locale.ROOT), row.name() == null ? prettify(id) : row.name(), category, icon,
            Collections.unmodifiableSet(mats), List.copyOf(tiers), List.copyOf(coins));
    }

    private RecipeDef parseRecipe(SkillsRepo.RecipeRow row, List<String> warn) {
        String id = row.id();
        String path = "recipes." + id;
        if (!id.matches("[a-z0-9_]+")) {
            warn.add(path + ": bad id");
            return null;
        }
        String resultCustom = row.resultCustom();
        Material resultMat = row.resultItem() == null ? null : Material.matchMaterial(row.resultItem());
        if (resultCustom == null && resultMat == null) {
            warn.add(path + ": needs result_custom or a valid result_item");
            return null;
        }
        List<Ingredient> ingredients = new ArrayList<>();
        int total = 0;
        for (SkillsRepo.IngredientRow m : row.ingredients()) {
            int slots = Math.max(1, m.slots());
            int perSlot = Math.max(1, m.perSlot());
            Ingredient ingredient = null;
            if (m.custom() != null) {
                ingredient = new Ingredient(null, m.custom(), slots, perSlot);
            } else if (m.item() != null) {
                Material mat = Material.matchMaterial(m.item());
                if (mat != null && !mat.isAir() && mat.isItem()) {
                    ingredient = new Ingredient(mat, null, slots, perSlot);
                }
            }
            if (ingredient == null) {
                warn.add(path + ": bad ingredient " + (m.custom() != null ? m.custom() : m.item()));
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
        for (var e : row.requires().entrySet()) {
            requires.put(e.getKey().toUpperCase(Locale.ROOT), Math.max(1, e.getValue()));
        }
        Skill xpSkill = skillByName(row.xpSkill());
        return new RecipeDef(id, resultCustom, resultMat, Math.max(1, Math.min(64, row.amount())),
            List.copyOf(ingredients), Collections.unmodifiableMap(requires), xpSkill, Math.max(0, row.xpAmount()));
    }

    private static AntiFarmCfg antiFarm(Settings s, String path, AntiFarmCfg defaults) {
        return new AntiFarmCfg(Math.max(1, s.getInt(path + ".cell", defaults.cell())),
            Math.max(1, s.getLong(path + ".window_seconds", defaults.windowMillis() / 1000)) * 1000,
            Math.max(0, s.getInt(path + ".free", defaults.free())), Math.max(0, s.getDouble(path + ".step", defaults.step())),
            Math.max(0, Math.min(1, s.getDouble(path + ".min", defaults.min()))));
    }

    private static Map<Material, Double> materials(Map<String, Double> raw, String path, List<String> warn) {
        Map<Material, Double> out = new LinkedHashMap<>();
        for (var e : raw.entrySet()) {
            Material m = Material.matchMaterial(e.getKey());
            if (m == null) {
                warn.add(path + ": unknown material " + e.getKey());
            } else {
                out.put(m, Math.max(0, e.getValue()));
            }
        }
        return Collections.unmodifiableMap(out);
    }

    private static Set<Material> materialSet(List<String> names, String path, List<String> warn) {
        Set<Material> out = new java.util.LinkedHashSet<>(); // keeps the data order (the first one is the icon)
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

    private static Map<EntityType, Double> entities(Map<String, Double> raw, String path, List<String> warn) {
        Map<EntityType, Double> out = new LinkedHashMap<>();
        for (var e : raw.entrySet()) {
            EntityType type = entityType(e.getKey());
            if (type == null) {
                warn.add(path + ": unknown entity " + e.getKey());
            } else {
                out.put(type, Math.max(0, e.getValue()));
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
