package win.vampyuyu.survival.dungeon.run;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import win.vampyuyu.survival.dungeon.gen.Pos;

/** The numbers behind a fair ambush room, with no Bukkit in it (see {@code FightDirector}). */
final class AmbushPacing {
    private AmbushPacing() { }

    /** How many enemies of the room may be alive at once: base plus a step per extra player, never above the room cap. */
    static int cap(int base, int perExtraPlayer, int partySize, int roomCap) {
        return Math.max(1, Math.min(roomCap, base + perExtraPlayer * Math.max(0, partySize - 1)));
    }

    /** The same enemies cut into {@code waves} consecutive parts (the first ones take the remainder): no enemy is lost. */
    static <T> List<List<T>> split(List<T> all, int waves) {
        int n = Math.max(1, Math.min(waves, Math.max(1, all.size())));
        List<List<T>> out = new ArrayList<>();
        int at = 0;
        for (int i = 0; i < n; i++) {
            int size = all.size() / n + (i < all.size() % n ? 1 : 0);
            out.add(new ArrayList<>(all.subList(at, at + size)));
            at += size;
        }
        return out;
    }

    static boolean telegraphOver(long now, long until) {
        return now >= until;
    }

    /** The next wave comes when the current one is down to {@code left} enemies (alive or still waiting), or after its time. */
    static boolean nextWaveDue(int alive, int queued, long now, long waveStart, int left, long afterTicks) {
        return alive + queued <= left || now - waveStart >= afterTicks;
    }

    /**
     * Where {@code count} enemies come in: spots at least {@code minDistance} from every player (when none is, the
     * farthest half), the ones nearest to the room's walls first (distance from the centre), then shuffled.
     */
    static List<Pos> edgeSpots(List<Pos> candidates, Pos center, List<double[]> players, double minDistance, int count, Random rng) {
        if (candidates.isEmpty() || count <= 0) {
            return List.of();
        }
        List<Pos> far = new ArrayList<>();
        for (Pos p : candidates) {
            if (nearest(p, players) >= minDistance * minDistance) {
                far.add(p);
            }
        }
        if (far.isEmpty()) {
            far.addAll(candidates);
            far.sort(Comparator.comparingDouble((Pos p) -> -nearest(p, players)));
            far = new ArrayList<>(far.subList(0, Math.max(1, far.size() / 2)));
        }
        far.sort(Comparator.comparingInt((Pos p) -> -Math.max(Math.abs(p.x() - center.x()), Math.abs(p.z() - center.z()))));
        List<Pos> edge = new ArrayList<>(far.subList(0, Math.max(1, Math.min(far.size(), Math.max(count, (far.size() + 1) / 2)))));
        java.util.Collections.shuffle(edge, rng);
        List<Pos> out = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            out.add(edge.get(i % edge.size()));
        }
        return out;
    }

    /** Squared distance (x, z) to the nearest player; no player at all counts as infinitely far. */
    static double nearest(Pos p, List<double[]> players) {
        double best = Double.MAX_VALUE;
        for (double[] q : players) {
            double dx = p.x() + 0.5 - q[0];
            double dz = p.z() + 0.5 - q[1];
            best = Math.min(best, dx * dx + dz * dz);
        }
        return best;
    }
}
