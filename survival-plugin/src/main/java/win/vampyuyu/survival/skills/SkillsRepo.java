package win.vampyuyu.survival.skills;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import win.vampyuyu.survival.data.Db;
import win.vampyuyu.survival.data.Settings;

/**
 * Reads the skills tuning from the content database (the {@code skill*}, {@code collection*}, {@code sea_creature} and
 * {@code enchanted_item} tables and the {@code skills.*} settings). It only reads rows and keeps names as text:
 * {@link SkillsConfig} turns them into materials, entities and stats and skips what does not parse.
 */
public final class SkillsRepo {
    /** A skill row. */
    record SkillRow(String id, int maxLevel, double multiplier) {}

    /** One run of levels that each give {@code amount} of the stat {@code stat}. */
    record StatRangeRow(String skillId, String stat, int from, int to, double amount) {}

    record UnlockRow(String skillId, int level, String text) {}

    /** A block that gives experience, with its two crop flags. */
    record BlockRow(String material, String skill, double xp, boolean matureOnly, boolean columnPlant) {}

    record SeaCreatureRow(String id, String entity, String name, int minLevel, double weight, double health) {}

    /** A collection ladder: item amounts per tier and the coins of each tier (same order). */
    record PresetRow(List<Long> tiers, List<Double> coins) {}

    record CollectionRow(String id, String name, String category, String preset, String icon, List<String> materials) {}

    record EnchantedRow(String id, String name, String material, String rarity, String model, int tier, List<String> lore) {}

    record IngredientRow(String item, String custom, int slots, int perSlot) {}

    record RecipeRow(String id, String resultCustom, String resultItem, int amount, String xpSkill, double xpAmount,
            List<IngredientRow> ingredients, Map<String, Integer> requires) {}

    /** Every table, read once. Experience maps are in row order, keyed by the material or entity name. */
    record Tables(Map<String, SkillRow> skills, List<StatRangeRow> statRanges, List<UnlockRow> unlocks,
            List<BlockRow> blocks, Map<String, Double> mobXp, Map<String, Double> catchXp, Map<String, Double> seaXp,
            Map<String, Double> brewXp, Map<String, Double> craftXp, List<SeaCreatureRow> seaCreatures,
            Map<String, PresetRow> presets, List<CollectionRow> collections, List<EnchantedRow> enchanted,
            List<RecipeRow> recipes) {
        /** Nothing at all (every lookup falls back to its default). */
        static Tables empty() {
            return new Tables(Map.of(), List.of(), List.of(), List.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(),
                List.of(), Map.of(), List.of(), List.of(), List.of());
        }
    }

    private SkillsRepo() {}

    /** The whole configuration; entries that do not parse are skipped and listed in {@link SkillsConfig#warnings}. */
    public static SkillsConfig load(Db db) {
        Settings settings = Settings.load(db, "skills");
        return new SkillsConfig(settings, db.read(SkillsRepo::tables));
    }

    private static Tables tables(Connection c) throws SQLException {
        Map<String, SkillRow> skills = new LinkedHashMap<>();
        for (SkillRow row : Db.query(c, "SELECT id, max_level, multiplier FROM skill ORDER BY rowid",
                rs -> new SkillRow(rs.getString(1), rs.getInt(2), rs.getDouble(3)))) {
            skills.put(row.id(), row);
        }
        List<StatRangeRow> ranges = Db.query(c, "SELECT skill_id, stat, from_level, to_level, amount FROM skill_stat_range "
            + "ORDER BY skill_id, position", rs -> new StatRangeRow(rs.getString(1), rs.getString(2), rs.getInt(3),
                rs.getInt(4), rs.getDouble(5)));
        List<UnlockRow> unlocks = Db.query(c, "SELECT skill_id, level, text FROM skill_unlock ORDER BY skill_id, level",
            rs -> new UnlockRow(rs.getString(1), rs.getInt(2), rs.getString(3)));
        List<BlockRow> blocks = Db.query(c, "SELECT material, skill, xp, mature_only, column_plant FROM skill_block_xp "
            + "ORDER BY position", rs -> new BlockRow(rs.getString(1), rs.getString(2), rs.getDouble(3),
                rs.getInt(4) == 1, rs.getInt(5) == 1));
        List<SeaCreatureRow> sea = Db.query(c, "SELECT id, entity, name, min_level, weight, health FROM sea_creature "
            + "ORDER BY position", rs -> new SeaCreatureRow(rs.getString(1), rs.getString(2), rs.getString(3), rs.getInt(4),
                rs.getDouble(5), rs.getDouble(6)));

        Map<String, List<Long>> presetTiers = new HashMap<>();
        Map<String, List<Double>> presetCoins = new HashMap<>();
        for (Object[] r : Db.query(c, "SELECT preset_id, amount, coins FROM collection_preset_tier ORDER BY preset_id, tier",
                rs -> new Object[] {rs.getString(1), rs.getLong(2), rs.getDouble(3)})) {
            presetTiers.computeIfAbsent((String) r[0], k -> new ArrayList<>()).add((Long) r[1]);
            presetCoins.computeIfAbsent((String) r[0], k -> new ArrayList<>()).add((Double) r[2]);
        }
        Map<String, PresetRow> presets = new LinkedHashMap<>();
        for (String id : Db.query(c, "SELECT id FROM collection_preset ORDER BY position", rs -> rs.getString(1))) {
            presets.put(id, new PresetRow(presetTiers.getOrDefault(id, List.of()), presetCoins.getOrDefault(id, List.of())));
        }
        Map<String, List<String>> materials = children(c, "SELECT collection_id, material FROM collection_material "
            + "ORDER BY collection_id, position");
        List<CollectionRow> collections = Db.query(c, "SELECT id, name, category, preset, icon FROM collection ORDER BY position",
            rs -> new CollectionRow(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5),
                materials.getOrDefault(rs.getString(1), List.of())));

        Map<String, List<String>> lore = children(c, "SELECT item_id, line FROM enchanted_item_lore ORDER BY item_id, position");
        List<EnchantedRow> enchanted = Db.query(c, "SELECT id, name, material, rarity, model, tier FROM enchanted_item "
            + "ORDER BY position", rs -> new EnchantedRow(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4),
                rs.getString(5), rs.getInt(6), lore.getOrDefault(rs.getString(1), List.of())));

        Map<String, List<IngredientRow>> ingredients = new HashMap<>();
        for (Object[] r : Db.query(c, "SELECT recipe_id, item, custom, slots, per_slot FROM skill_recipe_ingredient "
                + "ORDER BY recipe_id, position", rs -> new Object[] {rs.getString(1), rs.getString(2), rs.getString(3),
                    rs.getInt(4), rs.getInt(5)})) {
            ingredients.computeIfAbsent((String) r[0], k -> new ArrayList<>())
                .add(new IngredientRow((String) r[1], (String) r[2], (Integer) r[3], (Integer) r[4]));
        }
        Map<String, Map<String, Integer>> requires = new HashMap<>();
        for (Object[] r : Db.query(c, "SELECT recipe_id, collection, tier FROM skill_recipe_requirement ORDER BY recipe_id, rowid",
                rs -> new Object[] {rs.getString(1), rs.getString(2), rs.getInt(3)})) {
            requires.computeIfAbsent((String) r[0], k -> new LinkedHashMap<>()).put((String) r[1], (Integer) r[2]);
        }
        List<RecipeRow> recipes = Db.query(c, "SELECT id, result_custom, result_item, amount, xp_skill, xp_amount FROM skill_recipe "
            + "ORDER BY position", rs -> new RecipeRow(rs.getString(1), rs.getString(2), rs.getString(3), rs.getInt(4),
                rs.getString(5), rs.getDouble(6), ingredients.getOrDefault(rs.getString(1), List.of()),
                requires.getOrDefault(rs.getString(1), Map.of())));

        return new Tables(skills, ranges, unlocks, blocks, xpMap(c, "skill_mob_xp", "entity"),
            xpMap(c, "skill_catch_xp", "material"), xpMap(c, "skill_sea_xp", "entity"), xpMap(c, "skill_brew_xp", "material"),
            xpMap(c, "skill_craft_xp", "material"), sea, presets, collections, enchanted, recipes);
    }

    private static Map<String, Double> xpMap(Connection c, String table, String key) throws SQLException {
        Map<String, Double> out = new LinkedHashMap<>();
        for (Object[] r : Db.query(c, "SELECT " + key + ", xp FROM " + table + " ORDER BY position",
                rs -> new Object[] {rs.getString(1), rs.getDouble(2)})) {
            out.put((String) r[0], (Double) r[1]);
        }
        return out;
    }

    /** {@code parent id -> values} of a two column query, in query order. */
    private static Map<String, List<String>> children(Connection c, String sql) throws SQLException {
        Map<String, List<String>> out = new HashMap<>();
        for (String[] r : Db.query(c, sql, rs -> new String[] {rs.getString(1), rs.getString(2)})) {
            out.computeIfAbsent(r[0], k -> new ArrayList<>()).add(r[1]);
        }
        return out;
    }
}
