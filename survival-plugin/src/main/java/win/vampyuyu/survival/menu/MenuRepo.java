package win.vampyuyu.survival.menu;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import win.vampyuyu.survival.api.Stat;
import win.vampyuyu.survival.data.Db;
import win.vampyuyu.survival.data.Settings;
import win.vampyuyu.survival.menu.SoulLogic.SoulDef;
import win.vampyuyu.survival.menu.SoulLogic.StatStep;

/**
 * Reads the menu tuning from the content database: {@code menu.*} settings, the {@code menu_*} tables and, through
 * {@link QuestRepo}, the quests. A bad row is skipped and listed in {@link MenuConfig#problems()}.
 */
public final class MenuRepo {
    private MenuRepo() {}

    /** The table part of the configuration. */
    public record Rows(Map<String, List<String>> commands, Map<String, MenuConfig.WarpPoint> warps,
            Map<String, String> defaultZones, List<Zones.Zone> zones, Map<String, String> sounds, List<StatStep> fairyCycle,
            List<SoulDef> fairyFixed, List<SoulDef> fairyIsland, List<String> problems) {
        public Rows {
            commands = Map.copyOf(commands);
            warps = Map.copyOf(warps);
            defaultZones = Map.copyOf(defaultZones);
            zones = List.copyOf(zones);
            sounds = Map.copyOf(sounds);
            fairyCycle = List.copyOf(fairyCycle);
            fairyFixed = List.copyOf(fairyFixed);
            fairyIsland = List.copyOf(fairyIsland);
            problems = List.copyOf(problems);
        }
    }

    /** Settings, tables and quests, with the clamps and fallbacks of {@link MenuConfig}. */
    public static MenuConfig load(Db db) {
        return new MenuConfig(Settings.load(db, "menu"), rows(db), QuestRepo.load(db));
    }

    static Rows rows(Db db) {
        List<String> problems = new ArrayList<>();
        return db.read(c -> {
            Map<String, List<String>> aliases = new LinkedHashMap<>();
            Db.query(c, "SELECT command, alias FROM menu_command_alias ORDER BY command, position", rs -> {
                aliases.computeIfAbsent(rs.getString(1), k -> new ArrayList<>()).add(rs.getString(2));
                return null;
            });
            Map<String, List<String>> commands = new LinkedHashMap<>();
            Db.query(c, "SELECT name FROM menu_command ORDER BY position", rs -> {
                commands.put(rs.getString(1), aliases.getOrDefault(rs.getString(1), List.of()));
                return null;
            });
            Map<String, MenuConfig.WarpPoint> warps = new LinkedHashMap<>();
            Db.query(c, "SELECT world, x, y, z, yaw, pitch FROM menu_warp", rs -> {
                warps.put(rs.getString(1), new MenuConfig.WarpPoint(rs.getDouble(2), rs.getDouble(3), rs.getDouble(4),
                    (float) rs.getDouble(5), (float) rs.getDouble(6)));
                return null;
            });
            Map<String, String> defaultZones = new LinkedHashMap<>();
            Db.query(c, "SELECT world, name FROM menu_default_zone ORDER BY position", rs -> {
                defaultZones.put(rs.getString(1), rs.getString(2));
                return null;
            });
            List<Zones.Zone> zones = Db.query(c, "SELECT name, world, min_x, min_y, min_z, max_x, max_y, max_z FROM menu_zone "
                + "ORDER BY position", rs -> new Zones.Zone(rs.getString(1), MenuConfig.worldName(rs.getString(2)),
                    Math.min(rs.getDouble(3), rs.getDouble(6)), Math.min(rs.getDouble(4), rs.getDouble(7)),
                    Math.min(rs.getDouble(5), rs.getDouble(8)), Math.max(rs.getDouble(3), rs.getDouble(6)),
                    Math.max(rs.getDouble(4), rs.getDouble(7)), Math.max(rs.getDouble(5), rs.getDouble(8))));
            Map<String, String> sounds = new LinkedHashMap<>();
            Db.query(c, "SELECT event, sound FROM menu_sound ORDER BY position", rs -> {
                sounds.put(rs.getString(1), rs.getString(2));
                return null;
            });
            List<StatStep> cycle = new ArrayList<>();
            Db.query(c, "SELECT id, stat, amount FROM menu_fairy_stat ORDER BY position", rs -> {
                if (!Stat.known(rs.getString(2))) {
                    problems.add("fairy stat step " + rs.getString(1) + ": unknown stat '" + rs.getString(2) + "'");
                    return null;
                }
                // an old stat name converts (Magic Find becomes Fortune); a dropped stat gives no step
                for (Stat.Converted conv : Stat.resolve(rs.getString(2))) {
                    cycle.add(new StatStep(conv.stat(), rs.getDouble(3) * conv.factor()));
                }
                return null;
            });
            List<SoulDef> fixed = new ArrayList<>();
            List<SoulDef> island = new ArrayList<>();
            Db.query(c, "SELECT id, kind, world, x, y, z FROM menu_fairy_soul ORDER BY kind, position", rs -> {
                boolean relative = rs.getString(2).equals("island");
                try {
                    (relative ? island : fixed).add(new SoulDef(rs.getString(1), rs.getString(3), rs.getDouble(4),
                        rs.getDouble(5), rs.getDouble(6), relative));
                } catch (IllegalArgumentException e) {
                    problems.add("wisp " + rs.getString(1) + ": " + e.getMessage());
                }
                return null;
            });
            return new Rows(commands, warps, defaultZones, zones, sounds, cycle, fixed, island, problems);
        });
    }
}
