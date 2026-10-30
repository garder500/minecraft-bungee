package win.vampyuyu.survival.items.obtain;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.bukkit.configuration.ConfigurationSection;

/**
 * One row of the loot tables ({@code loot.entries} in items.yml).
 *
 * @param kind {@code item} (catalog id), {@code book} (enchanted book) or {@code pet} (pet egg)
 * @param id catalog id, enchant id (or {@code @ultimate} for a random ultimate) or pet species id;
 *     for {@code item}, {@code @armor:N} is a random armor piece of tier N
 * @param level book level (or the lowest level when {@code maxLevel} is set)
 * @param maxLevel highest book level, 0 = always {@code level}
 * @param rarity pet rarity name
 * @param amount stack size of an item
 * @param chance base chance in percent, per roll, before Magic Find / Pet Luck
 * @param sources where it can drop: {@code boss}, {@code mob}, {@code fishing}, or {@code mob:ZOMBIE} / {@code boss:<id>}
 */
public record LootEntry(String kind, String id, int level, int maxLevel, String rarity, int amount, double chance,
        List<String> sources) {

    public LootEntry {
        kind = kind.toLowerCase(Locale.ROOT);
        sources = List.copyOf(sources);
        level = Math.max(1, level);
        maxLevel = Math.max(0, maxLevel);
        amount = Math.max(1, amount);
        chance = Math.max(0, chance);
    }

    /** True when the entry can drop from {@code source} ({@code mob}) with the given detail ({@code ZOMBIE}). */
    public boolean matches(String source, String detail) {
        for (String s : sources) {
            String lower = s.toLowerCase(Locale.ROOT);
            if (lower.equals(source) || (!detail.isEmpty() && lower.equals(source + ":" + detail.toLowerCase(Locale.ROOT)))) {
                return true;
            }
        }
        return false;
    }

    /** True for entries whose chance is boosted by Pet Luck (pet eggs) instead of Magic Find. */
    public boolean petLuck() { return kind.equals("pet"); }

    /** Parses {@code loot.entries}; the built in table when the key is missing. Broken rows are skipped. */
    public static List<LootEntry> parse(ConfigurationSection root) {
        if (!root.isList("loot.entries")) {
            return DEFAULTS;
        }
        List<LootEntry> out = new ArrayList<>();
        for (Map<?, ?> row : root.getMapList("loot.entries")) {
            try {
                Object kind = row.get("kind");
                Object id = row.get("id");
                if (kind == null || id == null) {
                    continue;
                }
                List<String> sources = new ArrayList<>();
                if (row.get("sources") instanceof List<?> list) {
                    list.forEach(x -> sources.add(String.valueOf(x)));
                }
                out.add(new LootEntry(String.valueOf(kind), String.valueOf(id), num(row.get("level"), 1),
                    num(row.get("max-level"), 0), row.get("rarity") == null ? "" : String.valueOf(row.get("rarity")),
                    num(row.get("amount"), 1), dbl(row.get("chance"), 0), sources));
            } catch (RuntimeException ignored) {
                // a malformed row is skipped, the others stay
            }
        }
        return List.copyOf(out);
    }

    private static int num(Object value, int def) {
        return value instanceof Number n ? n.intValue() : def;
    }

    private static double dbl(Object value, double def) {
        return value instanceof Number n ? n.doubleValue() : def;
    }

    /** The shipped table (must stay equal to the one in items.yml: a test compares them). */
    public static final List<LootEntry> DEFAULTS = List.of(
            new LootEntry("book", "sharpness", 6, 0, "", 1, 0.12, List.of("mob")),
            new LootEntry("book", "power", 6, 0, "", 1, 0.12, List.of("mob")),
            new LootEntry("book", "protection", 6, 0, "", 1, 0.12, List.of("mob")),
            new LootEntry("book", "growth", 6, 0, "", 1, 0.12, List.of("mob")),
            new LootEntry("book", "critical", 6, 0, "", 1, 0.12, List.of("mob")),
            new LootEntry("book", "efficiency", 6, 0, "", 1, 0.12, List.of("mob")),
            new LootEntry("item", "hot_potato_book", 1, 0, "", 1, 0.5, List.of("mob")),
            new LootEntry("item", "pet_candy", 1, 0, "", 1, 0.6, List.of("mob")),
            new LootEntry("item", "reforge_stone_fabled", 1, 0, "", 1, 0.06, List.of("mob")),
            new LootEntry("item", "recombobulator", 1, 0, "", 1, 0.015, List.of("mob")),
            new LootEntry("item", "warp_pearl", 1, 0, "", 1, 0.8, List.of("mob:ENDERMAN")),
            new LootEntry("pet", "wolf", 1, 0, "COMMON", 1, 0.12, List.of("mob:ZOMBIE", "mob:SKELETON", "mob:SPIDER")),
            new LootEntry("pet", "enderman", 1, 0, "RARE", 1, 0.1, List.of("mob:ENDERMAN")),
            new LootEntry("pet", "tiger", 1, 0, "EPIC", 1, 0.04, List.of("mob:BLAZE", "mob:WITHER_SKELETON")),
            new LootEntry("item", "pet_candy", 1, 0, "", 1, 1, List.of("fishing")),
            new LootEntry("item", "warp_pearl", 1, 0, "", 1, 1.5, List.of("fishing")),
            new LootEntry("item", "reforge_stone_fabled", 1, 0, "", 1, 0.2, List.of("fishing")),
            new LootEntry("item", "rod_of_legends", 1, 0, "", 1, 0.03, List.of("fishing")),
            new LootEntry("pet", "turtle", 1, 0, "UNCOMMON", 1, 0.4, List.of("fishing")),
            new LootEntry("pet", "rabbit", 1, 0, "COMMON", 1, 0.25, List.of("fishing")),
            new LootEntry("book", "@ultimate", 1, 2, "", 1, 18, List.of("boss")),
            new LootEntry("book", "sharpness", 7, 0, "", 1, 12, List.of("boss")),
            new LootEntry("book", "protection", 7, 0, "", 1, 12, List.of("boss")),
            new LootEntry("book", "critical", 7, 0, "", 1, 10, List.of("boss")),
            new LootEntry("item", "@armor:2", 1, 0, "", 1, 20, List.of("boss")),
            new LootEntry("item", "@armor:3", 1, 0, "", 1, 3, List.of("boss")),
            new LootEntry("item", "reforge_stone_withered", 1, 0, "", 1, 14, List.of("boss")),
            new LootEntry("item", "reforge_stone_ancient", 1, 0, "", 1, 5, List.of("boss")),
            new LootEntry("item", "reforge_stone_refined", 1, 0, "", 1, 1.5, List.of("boss")),
            new LootEntry("item", "recombobulator", 1, 0, "", 1, 6, List.of("boss")),
            new LootEntry("item", "drill_fuel_volta", 1, 0, "", 1, 10, List.of("boss")),
            new LootEntry("item", "wither_blade", 1, 0, "", 1, 0.7, List.of("boss")),
            new LootEntry("item", "titan_drill", 1, 0, "", 1, 0.5, List.of("boss")),
            new LootEntry("pet", "tiger", 1, 0, "EPIC", 1, 4, List.of("boss")),
            new LootEntry("pet", "phoenix", 1, 0, "EPIC", 1, 3, List.of("boss")),
            new LootEntry("pet", "ender_dragon", 1, 0, "LEGENDARY", 1, 1.5, List.of("boss"))
    );
}
