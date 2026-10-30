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
     * A soul definition. {@code world} is {@code hub|world|nether|end} (resolved to a real world name by the caller).
     * Fixed souls have absolute coordinates; island souls have {@code relative = true}, x/y/z are offsets from the
     * centre of the island platform in that world (the Nether and End platforms have their own centre).
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
                if (!def.world().isEmpty() && !worldName.equals(resolveWorld.apply(def.world()))) {
                    continue;
                }
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

    /** Coins of milestone {@code number} (1-based): base plus {@code increase} for every earlier milestone. */
    public static double milestoneCoins(int number, double base, double increase) {
        return Math.max(0, base + Math.max(0, increase) * Math.max(0, number - 1));
    }

    /** Souls found and available in one zone (a world token such as {@code hub}). */
    public record ZoneProgress(String zone, int found, int total) {}

    private static final List<String> ZONE_ORDER = List.of("hub", "world", "nether", "end");

    /** Per zone counts over every soul definition (a soul id counts once), known zones first, then the others by name. */
    public static List<ZoneProgress> progress(Set<String> found, List<SoulDef> defs) {
        Map<String, int[]> zones = new java.util.TreeMap<>();
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (SoulDef def : defs) {
            if (!seen.add(def.id())) {
                continue;
            }
            int[] counts = zones.computeIfAbsent(def.world().isEmpty() ? "world" : def.world(), z -> new int[2]);
            counts[1]++;
            if (found.contains(def.id())) {
                counts[0]++;
            }
        }
        List<ZoneProgress> out = new ArrayList<>();
        for (String zone : ZONE_ORDER) {
            int[] counts = zones.remove(zone);
            if (counts != null) {
                out.add(new ZoneProgress(zone, counts[0], counts[1]));
            }
        }
        zones.forEach((zone, counts) -> out.add(new ZoneProgress(zone, counts[0], counts[1])));
        return out;
    }

    /** Text progress bar: {@code ████░░░░}. */
    public static String bar(int found, int total, int width) {
        int filled = total <= 0 ? 0 : (int) Math.round((double) Math.min(found, total) / total * width);
        return "█".repeat(filled) + "░".repeat(Math.max(0, width - filled));
    }

    /** Parses a list of soul entries from a config list of maps ({@code id, world, x, y, z}). */
    public static List<SoulDef> parse(List<Map<?, ?>> raw, boolean relative, List<String> problems) {
        List<SoulDef> out = new ArrayList<>();
        for (Map<?, ?> map : raw) {
            try {
                Object world = map.get("world");
                if (world == null && !relative) {
                    throw new IllegalArgumentException("missing 'world'");
                }
                out.add(new SoulDef(String.valueOf(map.get("id")), world != null ? String.valueOf(world) : "world",
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
