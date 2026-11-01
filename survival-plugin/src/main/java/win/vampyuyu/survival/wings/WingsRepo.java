package win.vampyuyu.survival.wings;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import win.vampyuyu.survival.data.Db;
import win.vampyuyu.survival.data.Settings;
import win.vampyuyu.survival.wings.WingsSettings.Region;
import win.vampyuyu.survival.wings.WingsSettings.Upgrade;

/** Reads the Wings tuning from the content database: {@code wings.*} settings and the {@code wings_*} tables. */
public final class WingsRepo {
    private WingsRepo() {}

    /** The columns of {@code wings_level} that are plain numbers. */
    enum Column { CAPACITY, REGEN, DRAIN, FLY_SPEED }

    /** The trail of one level. */
    record TrailRow(String particle, int count, Double spread) {}

    /** One row of {@code wings_level}. */
    record LevelRow(int level, double capacity, double regen, double drain, double flySpeed, TrailRow trail) {}

    /** The table part of the configuration. */
    public record Rows(List<LevelRow> levels, Map<String, List<Double>> stats, Map<Integer, Upgrade> upgrades,
            List<String> coreNames, List<Region> regions) {
        public Rows {
            levels = List.copyOf(levels);
            stats = Map.copyOf(stats);
            upgrades = Map.copyOf(upgrades);
            coreNames = List.copyOf(coreNames);
            regions = List.copyOf(regions);
        }

        List<Double> column(Column column) {
            return levels.stream().map(l -> switch (column) {
                case CAPACITY -> l.capacity();
                case REGEN -> l.regen();
                case DRAIN -> l.drain();
                case FLY_SPEED -> l.flySpeed();
            }).toList();
        }

        List<TrailRow> trails() {
            return levels.stream().map(LevelRow::trail).toList();
        }

        /** Empty tables, except for the three stat bonuses every Wings had by default. */
        static Rows defaults() {
            Map<String, List<Double>> stats = new LinkedHashMap<>();
            WingsSettings.DEFAULT_STAT_BONUS.forEach((name, values) ->
                stats.put(name, java.util.Arrays.stream(values).boxed().toList()));
            return new Rows(List.of(), stats, Map.of(), List.of(), List.of());
        }
    }

    /** Settings and tables, with the clamps and fallbacks of {@link WingsSettings}. */
    public static WingsSettings load(Db db) {
        return WingsSettings.from(Settings.load(db, "wings"), rows(db));
    }

    static Rows rows(Db db) {
        return db.read(c -> {
            List<LevelRow> levels = Db.query(c, "SELECT level, capacity_bonus, regen_multiplier, drain_multiplier, fly_speed, "
                + "trail_particle, trail_count, trail_spread FROM wings_level ORDER BY level",
                rs -> new LevelRow(rs.getInt(1), rs.getDouble(2), rs.getDouble(3), rs.getDouble(4), rs.getDouble(5),
                    new TrailRow(rs.getString(6), rs.getInt(7), rs.getObject(8) == null ? null : rs.getDouble(8))));
            Map<String, List<Double>> stats = new LinkedHashMap<>();
            Db.query(c, "SELECT stat, value FROM wings_level_stat ORDER BY level", rs -> {
                stats.computeIfAbsent(rs.getString(1), k -> new ArrayList<>()).add(rs.getDouble(2));
                return null;
            });
            Map<Integer, Upgrade> upgrades = new LinkedHashMap<>();
            Db.query(c, "SELECT level, core, amount, coins FROM wings_upgrade ORDER BY level", rs -> {
                upgrades.put(rs.getInt(1), new Upgrade(rs.getInt(2), rs.getInt(3), rs.getDouble(4)));
                return null;
            });
            List<String> names = Db.query(c, "SELECT name FROM wings_core ORDER BY tier", rs -> rs.getString(1));
            List<Region> regions = Db.query(c, "SELECT world, min_x, min_y, min_z, max_x, max_y, max_z FROM wings_region ORDER BY id",
                rs -> new Region(rs.getString(1), Math.min(rs.getInt(2), rs.getInt(5)), Math.min(rs.getInt(3), rs.getInt(6)),
                    Math.min(rs.getInt(4), rs.getInt(7)), Math.max(rs.getInt(2), rs.getInt(5)),
                    Math.max(rs.getInt(3), rs.getInt(6)), Math.max(rs.getInt(4), rs.getInt(7))));
            return new Rows(levels, stats, upgrades, names, regions);
        });
    }
}
