package win.vampyuyu.survival.breath;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import win.vampyuyu.survival.api.Realm;
import win.vampyuyu.survival.core.SkyblockWorlds;
import win.vampyuyu.survival.data.Db;
import win.vampyuyu.survival.data.Settings;

/** The {@code breath.*} setting rows. */
public record BreathConfig(boolean enabled, int intervalMinutes, int firstDelayMinutes, Map<String, BreathEvent> events,
        int fragmentsPerKey, double fractureFragmentChance, long fractureShardsPerFloor, String invaderType,
        String invaderName, int invadersPerPlayer, int invadersMax, int invaderRespawnSeconds, double invaderHealth,
        long invaderShards, long invaderShardsCap, double invaderFragmentChance, List<String> problems) {

    public static BreathConfig load(Db db) {
        return parse(Settings.load(db, "breath"));
    }

    public static BreathConfig parse(Settings s) {
        List<String> problems = new ArrayList<>(s.problems());
        Map<String, BreathEvent> events = new LinkedHashMap<>();
        for (String row : s.getList("events")) {
            try {
                BreathEvent e = BreathRules.parse(row);
                if (events.put(e.id(), e) != null) {
                    problems.add("breath.events: duplicate id '" + e.id() + "'");
                }
            } catch (IllegalArgumentException ex) {
                problems.add("breath.events: " + ex.getMessage() + " in '" + row + "'");
            }
        }
        return new BreathConfig(s.getBoolean("enabled", true), Math.max(5, s.getInt("interval-minutes", 180)),
            Math.max(1, s.getInt("first-delay-minutes", 10)), Map.copyOf(events), Math.max(1, s.getInt("fragments.per-key", 5)),
            clamp(s.getDouble("fracture.fragment-chance", 0.2)), Math.max(0, s.getLong("fracture.shards-per-floor", 15)),
            s.getString("invasion.mob", "ZOMBIE").toUpperCase(java.util.Locale.ROOT), s.getString("invasion.name", "Immune Cell"),
            Math.max(1, s.getInt("invasion.per-player", 4)), Math.max(1, s.getInt("invasion.max", 30)),
            Math.max(5, s.getInt("invasion.respawn-seconds", 20)), Math.max(1, s.getDouble("invasion.health", 40)),
            Math.max(0, s.getLong("invasion.shards-per-kill", 2)), Math.max(0, s.getLong("invasion.shards-cap", 40)),
            clamp(s.getDouble("invasion.fragment-chance", 0.03)), List.copyOf(problems));
    }

    private static double clamp(double v) {
        return Math.max(0, Math.min(1, v));
    }

    /** Events in the order they were declared (Map.copyOf loses it, so callers sort by id for a stable order). */
    public List<BreathEvent> ordered() {
        List<BreathEvent> list = new ArrayList<>(events.values());
        list.sort(java.util.Comparator.comparing(BreathEvent::id));
        return list;
    }

    /** The world a Realm lives in. */
    public static String worldOf(Realm realm) {
        return switch (realm) {
            case ASHES -> SkyblockWorlds.ASHES;
            case GROVE -> SkyblockWorlds.GROVE;
            case HEIGHTS -> SkyblockWorlds.HEIGHTS;
            case CITADEL -> SkyblockWorlds.CITADEL;
            case MARSHES -> SkyblockWorlds.MARSH;
            case ARCHIPELAGO -> SkyblockWorlds.STORM;
            case DEPTHS -> SkyblockWorlds.DEPTHS;
            case NECROPOLIS -> SkyblockWorlds.NECROPOLIS;
        };
    }

    /** The world an INVADE or FRACTURE target names ({@code hub} or a Realm id). */
    public static String worldOfTarget(String target) {
        if (target.equals("hub")) {
            return SkyblockWorlds.HUB;
        }
        Realm realm = Realm.byId(target);
        return realm == null ? null : worldOf(realm);
    }
}
