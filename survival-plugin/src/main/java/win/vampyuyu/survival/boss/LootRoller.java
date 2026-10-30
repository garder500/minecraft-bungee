package win.vampyuyu.survival.boss;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/** Rolls the drop table of one participant. Pure: no Bukkit, deterministic with a seeded {@link Random}. */
public final class LootRoller {
    private LootRoller() {}

    /** One drop that was won. */
    public record Drop(BossDefinition.LootEntry entry, int amount, double chance) {}

    /** Outcome of a roll: drops won and the updated pity counters. */
    public record Result(List<Drop> drops, Map<String, Integer> dry) {}

    public static String dryKey(String bossId, BossDefinition.LootEntry entry) {
        return bossId + "|" + entry.item();
    }

    /**
     * @param share damage share of the participant (0-1)
     * @param rank 1-based rank among the participants
     * @param magicFind the participant's Magic Find (already capped)
     * @param dry current pity counters of the participant (not modified)
     */
    public static Result roll(String bossId, BossDefinition.Loot loot, double share, int rank, double magicFind,
            Map<String, Integer> dry, Random random) {
        List<Drop> drops = new ArrayList<>();
        Map<String, Integer> updated = new HashMap<>(dry);
        double rankMult = LootMath.rankMultiplier(loot.rankBonus(), rank);
        for (BossDefinition.LootEntry entry : loot.entries()) {
            if (!LootMath.eligible(entry, share, rank)) {
                continue;
            }
            String key = dryKey(bossId, entry);
            int streak = dry.getOrDefault(key, 0);
            double pity = LootMath.pityBonus(streak, entry.pity(), entry.pityCap());
            double chance = LootMath.chance(entry.chance(), pity, rankMult, magicFind, entry.magicFind());
            if (LootMath.roll(chance, random)) {
                drops.add(new Drop(entry, LootMath.amount(entry, random), chance));
                updated.remove(key);
            } else if (entry.pity() > 0) {
                updated.put(key, streak + 1);
            }
        }
        return new Result(drops, updated);
    }
}
