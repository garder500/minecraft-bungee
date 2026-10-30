package win.vampyuyu.survival.dungeon;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/** Rolls the item part of a run reward from the weighted table of {@code dungeon.yml}. */
public final class RewardTable {
    private RewardTable() {}

    /** An item reward: custom item id or {@code mc:MATERIAL}, and an amount. */
    public record Drop(String item, int amount) {}

    /** Entries a (floor, grade) can roll. */
    public static List<DungeonConfig.RewardEntry> eligible(DungeonConfig cfg, int floor, Grade grade) {
        List<DungeonConfig.RewardEntry> out = new ArrayList<>();
        for (DungeonConfig.RewardEntry e : cfg.rewardItems) {
            if (floor >= e.minFloor() && grade.atLeast(e.minGrade())) {
                out.add(e);
            }
        }
        return out;
    }

    /** Rolls the item drops of one player (equal items merged). */
    public static List<Drop> roll(DungeonConfig cfg, int floor, Grade grade, Random rng) {
        DungeonConfig.Tier tier = cfg.tiers.get(grade);
        return rollN(cfg, floor, grade, tier == null ? 0 : tier.rolls(), rng);
    }

    /** Rolls exactly {@code rolls} entries among those a (floor, grade) can get (hidden chests use this). */
    public static List<Drop> rollN(DungeonConfig cfg, int floor, Grade grade, int rolls, Random rng) {
        List<DungeonConfig.RewardEntry> pool = eligible(cfg, floor, grade);
        Map<String, Integer> merged = new LinkedHashMap<>();
        if (rolls <= 0 || pool.isEmpty()) {
            return List.of();
        }
        int totalWeight = 0;
        for (DungeonConfig.RewardEntry e : pool) {
            totalWeight += e.weight();
        }
        for (int r = 0; r < rolls; r++) {
            int pick = rng.nextInt(totalWeight);
            for (DungeonConfig.RewardEntry e : pool) {
                pick -= e.weight();
                if (pick < 0) {
                    int amount = e.min() + (e.max() > e.min() ? rng.nextInt(e.max() - e.min() + 1) : 0);
                    merged.merge(e.item(), amount, Integer::sum);
                    break;
                }
            }
        }
        List<Drop> out = new ArrayList<>();
        merged.forEach((item, amount) -> out.add(new Drop(item, amount)));
        return out;
    }

    /** Coins for a grade on a floor. */
    public static double coins(DungeonConfig cfg, DungeonConfig.Floor floor, Grade grade) {
        DungeonConfig.Tier t = cfg.tiers.get(grade);
        return t == null ? 0 : Math.floor(t.coins() * floor.reward());
    }

    /** Combat xp for a grade on a floor. */
    public static double xp(DungeonConfig cfg, DungeonConfig.Floor floor, Grade grade) {
        DungeonConfig.Tier t = cfg.tiers.get(grade);
        return t == null ? 0 : Math.floor(t.xp() * floor.reward());
    }
}
