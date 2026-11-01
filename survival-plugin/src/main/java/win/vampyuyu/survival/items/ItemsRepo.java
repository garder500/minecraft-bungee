package win.vampyuyu.survival.items;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.bukkit.configuration.MemoryConfiguration;
import win.vampyuyu.survival.data.Db;

/**
 * Reads the items domain of the content database ({@code items.*} settings, {@code item_mob}, {@code item_recipe*},
 * {@code item_loot*}) into the configuration tree {@link ItemsSettings} understands. A value is read with the type its
 * {@code setting} row declares; collections come from their own tables.
 */
public final class ItemsRepo {
    private ItemsRepo() {}

    /** The whole items configuration tree. */
    public static MemoryConfiguration config(Db db) {
        MemoryConfiguration cfg = new MemoryConfiguration();
        db.read(c -> {
            String prefix = "items.";
            Map<String, List<String>> lists = new LinkedHashMap<>();
            Db.query(c, "SELECT setting_id, value FROM setting_item WHERE setting_id GLOB 'items.*' ORDER BY setting_id, position", rs -> {
                lists.computeIfAbsent(rs.getString(1), k -> new ArrayList<>()).add(rs.getString(2));
                return null;
            });
            Db.query(c, "SELECT id, type, value FROM setting WHERE id GLOB 'items.*'", rs -> {
                String path = rs.getString(1).substring(prefix.length());
                String value = rs.getString(3);
                try {
                    switch (rs.getString(2)) {
                        case "int" -> cfg.set(path, Integer.parseInt(value.trim()));
                        case "real" -> cfg.set(path, Double.parseDouble(value.trim()));
                        case "bool" -> cfg.set(path, Boolean.parseBoolean(value.trim()));
                        case "list" -> cfg.set(path, new ArrayList<>(lists.getOrDefault(rs.getString(1), List.of())));
                        default -> cfg.set(path, value);
                    }
                } catch (NumberFormatException e) {
                    // a value that does not parse is left out: the getter falls back to its default
                }
                return null;
            });
            Db.query(c, "SELECT entity, level, health, damage, weakness, resistance FROM item_mob", rs -> {
                String base = "mobs.table." + rs.getString(1).toUpperCase(Locale.ROOT);
                cfg.set(base + ".level", rs.getInt(2));
                cfg.set(base + ".health", rs.getDouble(3));
                cfg.set(base + ".damage", rs.getDouble(4));
                if (rs.getString(5) != null) {
                    cfg.set(base + ".weakness", rs.getString(5));
                }
                if (rs.getString(6) != null) {
                    cfg.set(base + ".resistance", rs.getString(6));
                }
                return null;
            });
            Map<String, List<String>> shapes = new LinkedHashMap<>();
            Db.query(c, "SELECT recipe_id, shape FROM item_recipe_row ORDER BY recipe_id, position", rs -> {
                shapes.computeIfAbsent(rs.getString(1), k -> new ArrayList<>()).add(rs.getString(2));
                return null;
            });
            Map<String, Map<String, String>> ingredients = new LinkedHashMap<>();
            Db.query(c, "SELECT recipe_id, symbol, material FROM item_recipe_ingredient ORDER BY recipe_id, symbol", rs -> {
                ingredients.computeIfAbsent(rs.getString(1), k -> new LinkedHashMap<>()).put(rs.getString(2), rs.getString(3));
                return null;
            });
            cfg.createSection("recipes.list");
            Db.query(c, "SELECT id, result, amount FROM item_recipe ORDER BY rowid", rs -> {
                String base = "recipes.list." + rs.getString(1);
                cfg.set(base + ".result", rs.getString(2));
                cfg.set(base + ".amount", rs.getInt(3));
                cfg.set(base + ".shape", shapes.getOrDefault(rs.getString(1), List.of()));
                ingredients.getOrDefault(rs.getString(1), Map.of()).forEach((symbol, material) ->
                    cfg.set(base + ".ingredients." + symbol, material));
                return null;
            });
            Map<String, List<String>> sources = new LinkedHashMap<>();
            Db.query(c, "SELECT loot_id, source FROM item_loot_source ORDER BY loot_id, position", rs -> {
                sources.computeIfAbsent(rs.getString(1), k -> new ArrayList<>()).add(rs.getString(2));
                return null;
            });
            List<Map<String, Object>> loot = new ArrayList<>();
            Db.query(c, "SELECT id, kind, item, level, max_level, rarity, amount, chance FROM item_loot ORDER BY position", rs -> {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("kind", rs.getString(2));
                row.put("id", rs.getString(3));
                row.put("level", rs.getInt(4));
                row.put("max-level", rs.getInt(5));
                row.put("rarity", rs.getString(6));
                row.put("amount", rs.getInt(7));
                row.put("chance", rs.getDouble(8));
                row.put("sources", new ArrayList<>(sources.getOrDefault(rs.getString(1), List.of())));
                loot.add(row);
                return null;
            });
            cfg.set("loot.entries", loot);
            return null;
        });
        return cfg;
    }
}
