package win.vampyuyu.survival.island;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Logger;
import win.vampyuyu.survival.data.Db;
import win.vampyuyu.survival.data.Settings;

/** Reads the island tuning from the content database: {@code island.*} settings and the {@code island_*} tables. */
public final class IslandRepo {
    private IslandRepo() {}

    /** A world's time, border, weather and game rules (true/false become booleans, whole numbers become integers). */
    public record WorldRow(String time, double border, boolean weatherClear, Map<String, Object> gamerules) {}

    /** One upgrade step of a ladder ({@code radius}, {@code members} or {@code minions}). */
    public record TierRow(String kind, int value, double cost) {}

    /** A material and an amount, as in a chest list. */
    public record ItemRow(String material, int amount) {}

    public record TemplateRow(String id, boolean enabled, String name, String icon, String biome, List<String> description,
            List<ItemRow> items) {}

    /** The table part of the configuration. */
    public record Rows(Map<String, WorldRow> worlds, List<TierRow> tiers, List<TemplateRow> templates,
            Map<String, List<ItemRow>> chests, Map<String, Double> weights, Map<String, Boolean> flags) {
        public Rows {
            worlds = Map.copyOf(worlds);
            tiers = List.copyOf(tiers);
            templates = List.copyOf(templates);
            chests = Map.copyOf(chests);
            weights = Map.copyOf(weights);
            flags = Map.copyOf(flags);
        }

        /** No tables at all: every value falls back to the one in {@link IslandConfig}. */
        public static Rows empty() {
            return new Rows(Map.of(), List.of(), List.of(), Map.of(), Map.of(), Map.of());
        }

        List<TierRow> tiers(String kind) {
            return tiers.stream().filter(t -> t.kind().equals(kind)).toList();
        }

        List<ItemRow> chest(String id) {
            return chests.getOrDefault(id, List.of());
        }
    }

    /** Settings and tables, with the clamps and fallbacks of {@link IslandConfig}. */
    public static IslandConfig load(Db db, Logger log) {
        return new IslandConfig(Settings.load(db, "island"), rows(db), log);
    }

    static Rows rows(Db db) {
        return db.read(c -> {
            Map<String, Map<String, Object>> rules = new LinkedHashMap<>();
            Db.query(c, "SELECT world, rule, value FROM island_world_gamerule ORDER BY world, position", rs -> {
                rules.computeIfAbsent(rs.getString(1), k -> new LinkedHashMap<>()).put(rs.getString(2), typed(rs.getString(3)));
                return null;
            });
            Map<String, WorldRow> worlds = new LinkedHashMap<>();
            Db.query(c, "SELECT key, time, border, weather_clear FROM island_world", rs -> {
                String key = rs.getString(1);
                worlds.put(key, new WorldRow(rs.getString(2), rs.getDouble(3), rs.getInt(4) == 1, rules.getOrDefault(key, Map.of())));
                return null;
            });
            List<TierRow> tiers = Db.query(c, "SELECT kind, value, cost FROM island_upgrade ORDER BY kind, position",
                rs -> new TierRow(rs.getString(1), rs.getInt(2), rs.getDouble(3)));
            Map<String, List<String>> lines = new LinkedHashMap<>();
            Db.query(c, "SELECT template_id, text FROM island_template_line ORDER BY template_id, position", rs -> {
                lines.computeIfAbsent(rs.getString(1), k -> new ArrayList<>()).add(rs.getString(2));
                return null;
            });
            Map<String, List<ItemRow>> templateItems = new LinkedHashMap<>();
            Db.query(c, "SELECT template_id, material, amount FROM island_template_item ORDER BY template_id, position", rs -> {
                templateItems.computeIfAbsent(rs.getString(1), k -> new ArrayList<>()).add(new ItemRow(rs.getString(2), rs.getInt(3)));
                return null;
            });
            List<TemplateRow> templates = Db.query(c, "SELECT id, enabled, name, icon, biome FROM island_template ORDER BY position",
                rs -> new TemplateRow(rs.getString(1), rs.getInt(2) == 1, rs.getString(3), rs.getString(4), rs.getString(5),
                    lines.getOrDefault(rs.getString(1), List.of()), templateItems.getOrDefault(rs.getString(1), List.of())));
            Map<String, List<ItemRow>> chests = new LinkedHashMap<>();
            Db.query(c, "SELECT chest_id, material, amount FROM island_chest_item ORDER BY chest_id, position", rs -> {
                chests.computeIfAbsent(rs.getString(1), k -> new ArrayList<>()).add(new ItemRow(rs.getString(2), rs.getInt(3)));
                return null;
            });
            Map<String, Double> weights = new LinkedHashMap<>();
            Db.query(c, "SELECT material, weight FROM island_level_weight ORDER BY rowid", rs -> {
                weights.put(rs.getString(1), rs.getDouble(2));
                return null;
            });
            Map<String, Boolean> flags = new LinkedHashMap<>();
            Db.query(c, "SELECT flag, value FROM island_flag_default", rs -> {
                flags.put(rs.getString(1), rs.getInt(2) == 1);
                return null;
            });
            return new Rows(worlds, tiers, templates, chests, weights, flags);
        });
    }

    private static Object typed(String value) {
        String text = value.trim();
        if (text.equalsIgnoreCase("true") || text.equalsIgnoreCase("false")) {
            return Boolean.parseBoolean(text.toLowerCase(Locale.ROOT));
        }
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException e) {
            return text;
        }
    }
}
