package win.vampyuyu.survival.quarry;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

/** quarry.yml. A broken entry is dropped and logged; the rest still loads. */
public final class QuarryConfig {
    public final OreRoller roller;
    public final int tiers;
    public final Map<String, ForgeRecipe> recipes;
    public final Map<String, Integer> forgeBlockRecipe;
    public final int forgeSlots;
    final MachineSettings machines;

    private QuarryConfig(OreRoller roller, int tiers, Map<String, ForgeRecipe> recipes,
            Map<String, Integer> forgeBlockRecipe, int forgeSlots, MachineSettings machines) {
        this.roller = roller;
        this.tiers = tiers;
        this.recipes = recipes;
        this.forgeBlockRecipe = forgeBlockRecipe;
        this.forgeSlots = forgeSlots;
        this.machines = machines;
    }

    /** Ores that only the ring generators drop (see OutputListener). */
    static final java.util.Set<String> GENERATOR_ONLY = java.util.Set.of("frostium", "sunstone");

    public static QuarryConfig parse(ConfigurationSection root, Logger log) {
        ConfigurationSection gen = root.getConfigurationSection("generator");
        double geode = gen == null ? 0.05 : gen.getDouble("geode-chance", 0.05);
        int min = gen == null ? 3 : gen.getInt("geode-min", 3);
        int max = gen == null ? 6 : gen.getInt("geode-max", 6);
        double cap = gen == null ? 300 : gen.getDouble("luck-cap", 300);
        int tiers = Math.max(1, gen == null ? 1 : gen.getInt("tiers", 1));
        List<OreRoller.Ore> ores = new ArrayList<>();
        ConfigurationSection oreSection = root.getConfigurationSection("ores");
        if (oreSection != null) {
            for (String id : oreSection.getKeys(false)) {
                if (GENERATOR_ONLY.contains(id)) {
                    continue; // Frostium and Sunstone come from the Frost and Ember generators only, whatever an older quarry.yml says
                }
                ConfigurationSection s = oreSection.getConfigurationSection(id);
                if (s == null || !validItem(s.getString("item", ""))) {
                    log.warning("quarry.yml: ores." + id + ": bad or missing item; ore skipped");
                    continue;
                }
                List<Double> list = s.getDoubleList("chance");
                double[] chance = new double[list.size()];
                for (int i = 0; i < chance.length; i++) {
                    chance[i] = Math.max(0, list.get(i));
                }
                ores.add(new OreRoller.Ore(id, s.getString("item"), chance, Math.max(0, s.getInt("pity", 0)),
                    s.getBoolean("geode", false)));
            }
        }
        Map<String, ForgeRecipe> recipes = new LinkedHashMap<>();
        ConfigurationSection forge = root.getConfigurationSection("forge");
        Map<String, Integer> block = new LinkedHashMap<>();
        int slots = 2;
        if (forge != null) {
            slots = Math.max(1, forge.getInt("slots", 2));
            readCost(forge.getConfigurationSection("recipe"), block, log, "forge.recipe");
            ConfigurationSection rs = forge.getConfigurationSection("recipes");
            if (rs != null) {
                for (String id : rs.getKeys(false)) {
                    ConfigurationSection s = rs.getConfigurationSection(id);
                    if (s == null || s.getString("result", "").isBlank()) {
                        log.warning("quarry.yml: forge.recipes." + id + ": no result; recipe skipped");
                        continue;
                    }
                    Map<String, Integer> cost = new LinkedHashMap<>();
                    readCost(s.getConfigurationSection("cost"), cost, log, "forge.recipes." + id);
                    if (cost.isEmpty()) {
                        log.warning("quarry.yml: forge.recipes." + id + ": empty cost; recipe skipped");
                        continue;
                    }
                    recipes.put(id, new ForgeRecipe(id, s.getString("result"), Math.max(1, s.getInt("seconds", 60)), cost,
                        s.getInt("amount", 1)));
                }
            }
        }
        ConfigurationSection bonus = gen == null ? null : gen.getConfigurationSection("geode-bonus");
        OreRoller roller = bonus == null ? new OreRoller(ores, geode, min, max, cap)
            : new OreRoller(ores, geode, min, max, cap, bonus.getString("ore"), bonus.getDouble("percent", 0),
                bonus.getInt("from-tier", 1));
        return new QuarryConfig(roller, tiers, recipes, block, slots,
            new MachineSettings(root.getConfigurationSection("machines")));
    }

    private static void readCost(ConfigurationSection s, Map<String, Integer> out, Logger log, String where) {
        if (s == null) {
            return;
        }
        for (String key : s.getKeys(false)) {
            int amount = s.getInt(key, 0);
            if (amount < 1 || !validItem(key)) {
                log.warning("quarry.yml: " + where + ": bad entry '" + key + "'; skipped");
                continue;
            }
            out.put(key, amount);
        }
    }

    /** A vanilla material name or custom:<id>. Custom ids are checked when used (they register later). */
    static boolean validItem(String key) {
        if (key == null || key.isBlank()) {
            return false;
        }
        return key.startsWith("custom:") ? key.length() > 7 : Material.matchMaterial(key) != null;
    }
}
