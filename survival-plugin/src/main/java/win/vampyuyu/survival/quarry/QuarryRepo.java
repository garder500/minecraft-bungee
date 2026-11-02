package win.vampyuyu.survival.quarry;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.logging.Logger;
import win.vampyuyu.survival.data.Db;
import win.vampyuyu.survival.data.Settings;

/** Reads the Quarry from the content database ({@code ore_roll*}, {@code forge_*} tables and the {@code quarry.*} settings). A broken row is dropped and logged; the rest still loads. */
public final class QuarryRepo {
    private QuarryRepo() {}

    public static QuarryConfig load(Db db, Logger log) {
        Settings settings = Settings.load(db, "quarry");
        win.vampyuyu.survival.api.TimeTide tide = win.vampyuyu.survival.island.CitadelRealm.tide(Settings.load(db, "island"));
        QuarryConfig config = db.read(c -> load(c, settings, tide, log));
        settings.problems().forEach(p -> log.warning("quarry: " + p));
        return config;
    }

    private static QuarryConfig load(Connection c, Settings s, win.vampyuyu.survival.api.TimeTide tide, Logger log) throws SQLException {
        Map<String, double[]> chances = new HashMap<>();
        for (Object[] r : Db.query(c, "SELECT ore_roll_id, tier, chance FROM ore_roll_chance ORDER BY ore_roll_id, tier",
                rs -> new Object[] {rs.getString(1), rs.getInt(2), rs.getDouble(3)})) {
            double[] list = chances.compute((String) r[0], (k, old) -> old == null ? new double[0] : old);
            int tier = (Integer) r[1];
            if (list.length < tier) {
                list = java.util.Arrays.copyOf(list, tier);
            }
            list[tier - 1] = Math.max(0, (Double) r[2]);
            chances.put((String) r[0], list);
        }
        Map<String, Set<String>> blocks = new HashMap<>();
        for (String[] r : Db.query(c, "SELECT ore_roll_id, block FROM ore_roll_block", rs -> new String[] {rs.getString(1), rs.getString(2)})) {
            blocks.computeIfAbsent(r[0], k -> new TreeSet<>()).add(r[1].toUpperCase(java.util.Locale.ROOT));
        }
        List<OreRoller.Ore> cobble = new ArrayList<>();
        List<OreRoller.Ore> rings = new ArrayList<>();
        for (Object[] r : Db.query(c, "SELECT id, item, source, pity, geode FROM ore_roll ORDER BY position",
                rs -> new Object[] {rs.getString(1), rs.getString(2), rs.getString(3), rs.getInt(4), rs.getInt(5) == 1})) {
            String id = (String) r[0];
            if (!QuarryConfig.validItem((String) r[1])) {
                log.warning("quarry: ore " + id + ": bad or missing item; ore skipped");
                continue;
            }
            OreRoller.Ore ore = new OreRoller.Ore(id, (String) r[1], chances.getOrDefault(id, new double[0]),
                Math.max(0, (Integer) r[3]), (Boolean) r[4], (String) r[2], blocks.getOrDefault(id, Set.of()));
            (ore.source().equals(OreRoller.COBBLE) ? cobble : rings).add(ore);
        }

        Map<String, Map<String, Integer>> costs = new HashMap<>();
        for (Object[] r : Db.query(c, "SELECT forge_recipe_id, item, amount FROM forge_recipe_ingredient ORDER BY forge_recipe_id, position",
                rs -> new Object[] {rs.getString(1), rs.getString(2), rs.getInt(3)})) {
            if (!QuarryConfig.validItem((String) r[1]) || (Integer) r[2] < 1) {
                log.warning("quarry: forge recipe " + r[0] + ": bad ingredient '" + r[1] + "'; skipped");
                continue;
            }
            costs.computeIfAbsent((String) r[0], k -> new LinkedHashMap<>()).put((String) r[1], (Integer) r[2]);
        }
        Map<String, ForgeRecipe> recipes = new LinkedHashMap<>();
        for (Object[] r : Db.query(c, "SELECT id, result, seconds, amount FROM forge_recipe ORDER BY position",
                rs -> new Object[] {rs.getString(1), rs.getString(2), rs.getInt(3), rs.getInt(4)})) {
            String id = (String) r[0];
            if (((String) r[1]).isBlank()) {
                log.warning("quarry: forge recipe " + id + ": no result; recipe skipped");
                continue;
            }
            Map<String, Integer> cost = costs.getOrDefault(id, Map.of());
            if (cost.isEmpty()) {
                log.warning("quarry: forge recipe " + id + ": empty cost; recipe skipped");
                continue;
            }
            recipes.put(id, new ForgeRecipe(id, (String) r[1], Math.max(1, (Integer) r[2]), cost, (Integer) r[3]));
        }
        Map<String, Integer> block = new LinkedHashMap<>();
        for (Object[] r : Db.query(c, "SELECT item, amount FROM forge_block_ingredient ORDER BY position",
                rs -> new Object[] {rs.getString(1), rs.getInt(2)})) {
            if (!QuarryConfig.validItem((String) r[0]) || (Integer) r[1] < 1) {
                log.warning("quarry: forge block recipe: bad ingredient '" + r[0] + "'; skipped");
                continue;
            }
            block.put((String) r[0], (Integer) r[1]);
        }

        double geode = s.getDouble("generator.geode-chance", 0.05);
        int min = s.getInt("generator.geode-min", 3);
        int max = s.getInt("generator.geode-max", 6);
        double cap = s.getDouble("generator.luck-cap", 300);
        int tiers = Math.max(1, s.getInt("generator.tiers", 1));
        OreRoller roller = s.has("generator.geode-bonus.ore")
            ? new OreRoller(cobble, geode, min, max, cap, s.getString("generator.geode-bonus.ore", null),
                s.getDouble("generator.geode-bonus.percent", 0), s.getInt("generator.geode-bonus.from-tier", 1))
            : new OreRoller(cobble, geode, min, max, cap);
        OreRoller outputs = new OreRoller(rings, 0, 1, 1, 0);
        return new QuarryConfig(roller, outputs, tiers, recipes, block, Math.max(1, s.getInt("forge.slots", 2)),
            new MachineSettings(s, tide));
    }
}
