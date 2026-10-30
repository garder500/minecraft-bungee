package win.vampyuyu.survival.items.obtain;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

/**
 * A crafting recipe of a catalog item ({@code recipes.list.<name>} in items.yml).
 *
 * @param name recipe name (part of the recipe key)
 * @param result catalog item id
 * @param shape one to three rows of one to three characters
 * @param ingredients character to vanilla material name
 */
public record RecipeDef(String name, String result, int amount, List<String> shape, Map<Character, String> ingredients) {

    public RecipeDef {
        shape = List.copyOf(shape);
        ingredients = Map.copyOf(ingredients);
        amount = Math.max(1, amount);
    }

    /** Null when the definition is usable, else why it is not. */
    public String problem() {
        if (shape.isEmpty() || shape.size() > 3) {
            return "shape needs 1 to 3 rows";
        }
        for (String row : shape) {
            if (row.isEmpty() || row.length() > 3) {
                return "rows need 1 to 3 characters";
            }
        }
        for (String row : shape) {
            for (char c : row.toCharArray()) {
                if (c != ' ' && !ingredients.containsKey(c)) {
                    return "no ingredient for '" + c + "'";
                }
            }
        }
        for (Map.Entry<Character, String> e : ingredients.entrySet()) {
            Material m = Material.matchMaterial(e.getValue());
            if (m == null || !m.isItem()) {
                return "unknown material " + e.getValue();
            }
        }
        return null;
    }

    /** Parses {@code recipes.list}; the built in recipes when the section is missing. */
    public static List<RecipeDef> parse(ConfigurationSection root) {
        ConfigurationSection list = root.getConfigurationSection("recipes.list");
        if (list == null) {
            return DEFAULTS;
        }
        List<RecipeDef> out = new java.util.ArrayList<>();
        for (String name : list.getKeys(false)) {
            ConfigurationSection row = list.getConfigurationSection(name);
            if (row == null || row.getString("result") == null) {
                continue;
            }
            Map<Character, String> ingredients = new LinkedHashMap<>();
            ConfigurationSection ing = row.getConfigurationSection("ingredients");
            if (ing != null) {
                for (String key : ing.getKeys(false)) {
                    if (key.length() == 1) {
                        ingredients.put(key.charAt(0), String.valueOf(ing.get(key)).toUpperCase(Locale.ROOT));
                    }
                }
            }
            out.add(new RecipeDef(name.toLowerCase(Locale.ROOT), row.getString("result", ""), row.getInt("amount", 1),
                row.getStringList("shape"), ingredients));
        }
        return List.copyOf(out);
    }

    /** The shipped recipes (a test compares them with items.yml). */
    public static final List<RecipeDef> DEFAULTS = List.of(
            new RecipeDef("rookie_sword", "rookie_sword", 1, List.of("C", "I", "S"), Map.of('C', "COPPER_INGOT", 'I', "IRON_INGOT", 'S', "STICK")),
            new RecipeDef("warriors_blade", "warriors_blade", 1, List.of("I", "I", "B"), Map.of('I', "IRON_INGOT", 'B', "BLAZE_ROD")),
            new RecipeDef("miners_pickaxe", "miners_pickaxe", 1, List.of("IDI", " C ", " C "), Map.of('I', "IRON_INGOT", 'D', "DIAMOND", 'C', "COPPER_INGOT")),
            new RecipeDef("farmers_hoe", "farmers_hoe", 1, List.of("II", " C", " C"), Map.of('I', "IRON_INGOT", 'C', "COPPER_INGOT")),
            new RecipeDef("diggers_shovel", "diggers_shovel", 1, List.of("I", "C", "C"), Map.of('I', "IRON_INGOT", 'C', "COPPER_INGOT")),
            new RecipeDef("grappling_hook", "grappling_hook", 1, List.of("  I", " IS", "I S"), Map.of('I', "IRON_INGOT", 'S', "STRING")),
            new RecipeDef("warp_pearl", "warp_pearl", 1, List.of("EC", "CE"), Map.of('E', "ENDER_PEARL", 'C', "CHORUS_FRUIT")),
            new RecipeDef("hot_potato_book", "hot_potato_book", 1, List.of("PGP", "GBG", "PGP"), Map.of('P', "BAKED_POTATO", 'G', "GOLDEN_CARROT", 'B', "BOOK")),
            new RecipeDef("miner_helmet_t1", "miner_helmet_t1", 1, List.of("MMM", "M M"), Map.of('M', "COBBLESTONE")),
            new RecipeDef("miner_chestplate_t1", "miner_chestplate_t1", 1, List.of("M M", "MMM", "MMM"), Map.of('M', "COBBLESTONE")),
            new RecipeDef("miner_leggings_t1", "miner_leggings_t1", 1, List.of("MMM", "M M", "M M"), Map.of('M', "COBBLESTONE")),
            new RecipeDef("miner_boots_t1", "miner_boots_t1", 1, List.of("M M", "M M"), Map.of('M', "COBBLESTONE")),
            new RecipeDef("lumberjack_helmet_t1", "lumberjack_helmet_t1", 1, List.of("MMM", "M M"), Map.of('M', "OAK_LOG")),
            new RecipeDef("lumberjack_chestplate_t1", "lumberjack_chestplate_t1", 1, List.of("M M", "MMM", "MMM"), Map.of('M', "OAK_LOG")),
            new RecipeDef("lumberjack_leggings_t1", "lumberjack_leggings_t1", 1, List.of("MMM", "M M", "M M"), Map.of('M', "OAK_LOG")),
            new RecipeDef("lumberjack_boots_t1", "lumberjack_boots_t1", 1, List.of("M M", "M M"), Map.of('M', "OAK_LOG")),
            new RecipeDef("farmer_helmet_t1", "farmer_helmet_t1", 1, List.of("MMM", "M M"), Map.of('M', "WHEAT")),
            new RecipeDef("farmer_chestplate_t1", "farmer_chestplate_t1", 1, List.of("M M", "MMM", "MMM"), Map.of('M', "WHEAT")),
            new RecipeDef("farmer_leggings_t1", "farmer_leggings_t1", 1, List.of("MMM", "M M", "M M"), Map.of('M', "WHEAT")),
            new RecipeDef("farmer_boots_t1", "farmer_boots_t1", 1, List.of("M M", "M M"), Map.of('M', "WHEAT")),
            new RecipeDef("hunter_helmet_t1", "hunter_helmet_t1", 1, List.of("MMM", "M M"), Map.of('M', "BONE")),
            new RecipeDef("hunter_chestplate_t1", "hunter_chestplate_t1", 1, List.of("M M", "MMM", "MMM"), Map.of('M', "BONE")),
            new RecipeDef("hunter_leggings_t1", "hunter_leggings_t1", 1, List.of("MMM", "M M", "M M"), Map.of('M', "BONE")),
            new RecipeDef("hunter_boots_t1", "hunter_boots_t1", 1, List.of("M M", "M M"), Map.of('M', "BONE")),
            new RecipeDef("void_walker_helmet_t1", "void_walker_helmet_t1", 1, List.of("MMM", "M M"), Map.of('M', "END_STONE")),
            new RecipeDef("void_walker_chestplate_t1", "void_walker_chestplate_t1", 1, List.of("M M", "MMM", "MMM"), Map.of('M', "END_STONE")),
            new RecipeDef("void_walker_leggings_t1", "void_walker_leggings_t1", 1, List.of("MMM", "M M", "M M"), Map.of('M', "END_STONE")),
            new RecipeDef("void_walker_boots_t1", "void_walker_boots_t1", 1, List.of("M M", "M M"), Map.of('M', "END_STONE")),
            new RecipeDef("frostborne_helmet_t1", "frostborne_helmet_t1", 1, List.of("MMM", "M M"), Map.of('M', "SNOW_BLOCK")),
            new RecipeDef("frostborne_chestplate_t1", "frostborne_chestplate_t1", 1, List.of("M M", "MMM", "MMM"), Map.of('M', "SNOW_BLOCK")),
            new RecipeDef("frostborne_leggings_t1", "frostborne_leggings_t1", 1, List.of("MMM", "M M", "M M"), Map.of('M', "SNOW_BLOCK")),
            new RecipeDef("frostborne_boots_t1", "frostborne_boots_t1", 1, List.of("M M", "M M"), Map.of('M', "SNOW_BLOCK"))
    );
}
