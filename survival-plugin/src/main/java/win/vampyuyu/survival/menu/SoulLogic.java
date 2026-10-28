package win.vampyuyu.survival.menu;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import win.vampyuyu.survival.api.Stat;

/** Pure Fairy Soul rules: where souls are, who sees them, which milestone pays what. */
public final class SoulLogic {
    private SoulLogic() {}

    /**
     * A soul definition. {@code world} is {@code hub|world|nether|end} (resolved to a real world name by the caller)
     * for fixed souls; island souls have {@code relative = true} and x/y/z are offsets from the island centre.
     */
    public record SoulDef(String id, String world, double x, double y, double z, boolean relative) {
        public SoulDef {
            if (!id.matches("[a-z0-9_]+")) {
                throw new IllegalArgumentException("Bad soul id '" + id + "'");
            }
            world = world == null ? "" : world.toLowerCase(Locale.ROOT);
        }
    }

    /** A soul at a concrete place of a concrete world. */
    public record SoulSpot(String id, String worldName, double x, double y, double z) {
        public double distanceSquared(double px, double py, double pz) {
            double dx = x - px;
            double dy = y - py;
            double dz = z - pz;
            return dx * dx + dy * dy + dz * dz;
        }
    }

    /** One step of the milestone stat cycle. */
    public record StatStep(Stat stat, double amount) {}

    /** True when the player has NOT found the soul (so it may be shown to them and collected). */
    public static boolean visibleTo(Set<String> found, String soulId) {
        return !found.contains(soulId);
    }

    /**
     * The souls present around a player: fixed souls whose world matches {@code worldName}, plus the island
     * offsets applied to {@code islandCenter} (null when the player is not on an island).
     *
     * @param resolveWorld maps a config world token (hub/world/nether/end) to a world name
     */
    public static List<SoulSpot> spotsFor(String worldName, double[] islandCenter, List<SoulDef> fixed,
            List<SoulDef> islandOffsets, java.util.function.Function<String, String> resolveWorld) {
        List<SoulSpot> spots = new ArrayList<>();
        for (SoulDef def : fixed) {
            if (worldName.equals(resolveWorld.apply(def.world()))) {
                spots.add(new SoulSpot(def.id(), worldName, def.x(), def.y(), def.z()));
            }
        }
        if (islandCenter != null) {
            for (SoulDef def : islandOffsets) {
                spots.add(new SoulSpot(def.id(), worldName, islandCenter[0] + def.x(), islandCenter[1] + def.y(),
                    islandCenter[2] + def.z()));
            }
        }
        return spots;
    }

    /** The closest spot the player has not found. */
    public static Optional<SoulSpot> nearest(List<SoulSpot> spots, Set<String> found, double x, double y, double z) {
        SoulSpot best = null;
        double bestDistance = Double.MAX_VALUE;
        for (SoulSpot spot : spots) {
            if (!visibleTo(found, spot.id())) {
                continue;
            }
            double d = spot.distanceSquared(x, y, z);
            if (d < bestDistance) {
                best = spot;
                bestDistance = d;
            }
        }
        return Optional.ofNullable(best);
    }

    /** Completed milestones for a number of souls found. {@code every <= 0} disables rewards. */
    public static int milestones(int count, int every) {
        return every <= 0 ? 0 : Math.max(0, count) / every;
    }

    /** Souls missing until the next milestone (0 when rewards are disabled). */
    public static int untilNext(int count, int every) {
        return every <= 0 ? 0 : every - Math.max(0, count) % every;
    }

    /** Total stats granted by {@code milestones} milestones, cycling through {@code cycle}. */
    public static Map<Stat, Double> milestoneStats(int milestones, List<StatStep> cycle) {
        Map<Stat, Double> totals = new EnumMap<>(Stat.class);
        if (cycle.isEmpty()) {
            return totals;
        }
        for (int i = 0; i < milestones; i++) {
            StatStep step = cycle.get(i % cycle.size());
            totals.merge(step.stat(), step.amount(), Double::sum);
        }
        return totals;
    }

    /** Compass word for the direction from (0,0) to (dx,dz) in Minecraft axes (north is -z). */
    public static String direction(double dx, double dz) {
        double angle = Math.toDegrees(Math.atan2(dx, -dz)); // 0 = north, 90 = east
        String[] names = {"north", "north-east", "east", "south-east", "south", "south-west", "west", "north-west"};
        int index = (int) Math.round(((angle % 360) + 360) % 360 / 45.0) % 8;
        return names[index];
    }

    /** Parses a list of soul entries from a config list of maps ({@code id, world, x, y, z}). */
    public static List<SoulDef> parse(List<Map<?, ?>> raw, boolean relative, List<String> problems) {
        List<SoulDef> out = new ArrayList<>();
        for (Map<?, ?> map : raw) {
            try {
                out.add(new SoulDef(String.valueOf(map.get("id")), relative ? "" : String.valueOf(map.get("world")),
                    num(map, "x"), num(map, "y"), num(map, "z"), relative));
            } catch (RuntimeException e) {
                problems.add("fairy soul " + map + ": " + e.getMessage());
            }
        }
        return out;
    }

    private static double num(Map<?, ?> map, String key) {
        Object value = map.get(key);
        if (value instanceof Number n) {
            return n.doubleValue();
        }
        throw new IllegalArgumentException("missing number '" + key + "'");
    }

    /** Parses {@code stat-cycle: [{stat: HEALTH, amount: 2}]}; unknown stats are reported and skipped. */
    public static List<StatStep> parseCycle(List<Map<?, ?>> raw, List<String> problems) {
        List<StatStep> steps = new ArrayList<>();
        for (Map<?, ?> map : raw) {
            try {
                Stat stat = Stat.valueOf(String.valueOf(map.get("stat")).toUpperCase(Locale.ROOT));
                steps.add(new StatStep(stat, num(map, "amount")));
            } catch (RuntimeException e) {
                problems.add("fairy stat step " + map + ": " + e.getMessage());
            }
        }
        return steps;
    }

}
