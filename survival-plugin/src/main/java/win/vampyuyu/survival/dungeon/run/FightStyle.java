package win.vampyuyu.survival.dungeon.run;

import java.util.List;
import java.util.Random;

/** What a mob room asks of the party, so that one room is not the same as the last. */
enum FightStyle {
    /** The waves, one after the other. */
    WAVES(4, 1),
    /** Everything at once. */
    AMBUSH(3, 1),
    /** One strong enemy with a small escort. */
    ELITE(3, 1),
    /** Stand in the ring until the time is up, while enemies keep coming: killing them all is not the goal. */
    HOLD(2, 2);

    private final int weight;
    private final int minFloor;

    FightStyle(int weight, int minFloor) {
        this.weight = weight;
        this.minFloor = minFloor;
    }

    /** A style for a room; never the one the last room had, when another one exists on this floor. */
    static FightStyle pick(int floor, FightStyle last, Random rng) {
        List<FightStyle> pool = java.util.Arrays.stream(values()).filter(s -> floor >= s.minFloor && s != last).toList();
        int total = pool.stream().mapToInt(s -> s.weight).sum();
        int r = rng.nextInt(total);
        for (FightStyle s : pool) {
            r -= s.weight;
            if (r < 0) {
                return s;
            }
        }
        return pool.get(0);
    }
}
