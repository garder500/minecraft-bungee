package win.vampyuyu.survival.items.catalog;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import win.vampyuyu.survival.api.Stat;
import win.vampyuyu.survival.data.ContentDomains;
import win.vampyuyu.survival.data.Db;
import win.vampyuyu.survival.data.Seeds;

/**
 * The armor sets, read from the {@code armor_set} tables. {@link #install} is called when the items feature loads; before
 * that (tests, tools) the jar's default sets are used.
 */
public final class ArmorSets {
    private ArmorSets() {}

    public static final List<String> SLOTS = List.of("helmet", "chestplate", "leggings", "boots");

    private static volatile List<ArmorSet> installed;
    private static volatile List<ArmorSet> defaults;

    /** Reads the sets of the database. */
    public static List<ArmorSet> load(Db db) {
        return db.read(c -> {
            Map<String, Map<Stat, Double>> piece = new LinkedHashMap<>();
            Map<String, Map<Stat, Double>> bonus = new LinkedHashMap<>();
            Db.query(c, "SELECT set_id, role, stat, value FROM armor_set_stat ORDER BY set_id, role, stat", rs -> {
                Map<String, Map<Stat, Double>> target = rs.getString(2).equals("piece") ? piece : bonus;
                for (Stat.Converted conv : Stat.resolve(rs.getString(3))) {
                    target.computeIfAbsent(rs.getString(1), k -> new EnumMap<>(Stat.class))
                        .merge(conv.stat(), rs.getDouble(4) * conv.factor(), Double::sum);
                }
                return null;
            });
            Map<String, List<String>> lines = new LinkedHashMap<>();
            Db.query(c, "SELECT set_id, line FROM armor_set_line ORDER BY set_id, position", rs -> {
                lines.computeIfAbsent(rs.getString(1), k -> new ArrayList<>()).add(rs.getString(2));
                return null;
            });
            return new ArrayList<>(Db.query(c, "SELECT id, name, material, bonus_name FROM armor_set ORDER BY position", rs ->
                new ArmorSet(rs.getString(1), rs.getString(2), rs.getString(3), piece.getOrDefault(rs.getString(1), new EnumMap<>(Stat.class)),
                    rs.getString(4), lines.getOrDefault(rs.getString(1), List.of()),
                    bonus.getOrDefault(rs.getString(1), new EnumMap<>(Stat.class)))));
        });
    }

    /** Makes {@code sets} the sets of the running server. */
    public static void install(List<ArmorSet> sets) {
        installed = List.copyOf(sets);
    }

    private static List<ArmorSet> current() {
        List<ArmorSet> sets = installed;
        if (sets != null) {
            return sets;
        }
        List<ArmorSet> fallback = defaults;
        if (fallback == null) {
            try (Db db = Seeds.defaults(ContentDomains.ITEMS)) {
                fallback = List.copyOf(load(db));
            }
            defaults = fallback;
        }
        return fallback;
    }

    public static List<ArmorSet> all() { return current(); }

    public static Optional<ArmorSet> get(String id) {
        return current().stream().filter(set -> set.id().equals(id)).findFirst();
    }
}
