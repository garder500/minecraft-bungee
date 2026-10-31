package win.vampyuyu.survival.quarry;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * The cobble draw: pure logic, no Bukkit. Ores are tried from the first (rarest) to the last; one ore per block at
 * most. Every ore that exists at the generator tier counts the blocks since its last drop and is forced once the
 * counter reaches its pity. Mining luck multiplies the chances, never the pity.
 */
public final class OreRoller {
    /** One ore of the table. {@code chance} is a percent per block for each generator tier (index 0 = tier I). */
    public record Ore(String id, String item, double[] chance, int pity, boolean geode) {
        public double at(int tier) {
            return tier >= 1 && tier <= chance.length ? chance[tier - 1] : 0;
        }
    }

    public record Drop(String ore, int amount) {}

    /** What one broken block gave. {@code geode} is true when the drops come from a geode. */
    public record Roll(List<Drop> drops, boolean geode, boolean pity) {
        public static final Roll NOTHING = new Roll(List.of(), false, false);
    }

    private final List<Ore> ores;
    private final double geodeChance;
    private final int geodeMin;
    private final int geodeMax;
    private final double luckCap;
    private final String bonusOre;
    private final double bonusPercent;
    private final int bonusTier;

    public OreRoller(List<Ore> ores, double geodeChance, int geodeMin, int geodeMax, double luckCap) {
        this(ores, geodeChance, geodeMin, geodeMax, luckCap, null, 0, 0);
    }

    /** {@code bonusOre}: an ore a geode adds one of, {@code bonusPercent} of the time, from generator tier {@code bonusTier}. */
    public OreRoller(List<Ore> ores, double geodeChance, int geodeMin, int geodeMax, double luckCap, String bonusOre,
            double bonusPercent, int bonusTier) {
        this.bonusOre = bonusOre;
        this.bonusPercent = bonusPercent;
        this.bonusTier = bonusTier;
        this.ores = List.copyOf(ores);
        this.geodeChance = geodeChance;
        this.geodeMin = Math.max(1, geodeMin);
        this.geodeMax = Math.max(this.geodeMin, geodeMax);
        this.luckCap = luckCap;
    }

    public List<Ore> ores() {
        return ores;
    }

    /** The multiplier that {@code luck} gives to every chance. */
    public double multiplier(double luck) {
        return 1 + Math.max(0, Math.min(luck, luckCap)) / 100.0;
    }

    /**
     * Draws one broken block. {@code counters} (blocks since the last drop, per ore id) is updated in place.
     */
    public Roll roll(int tier, double luck, Map<String, Integer> counters, Random random) {
        double mult = multiplier(luck);
        for (Ore ore : ores) {
            if (ore.at(tier) > 0) {
                counters.merge(ore.id(), 1, Integer::sum);
            }
        }
        if (random.nextDouble() * 100 < geodeChance * mult) {
            Ore best = best(tier);
            if (best != null) {
                int amount = geodeMin + random.nextInt(geodeMax - geodeMin + 1);
                counters.put(best.id(), 0);
                List<Drop> drops = new ArrayList<>(List.of(new Drop(best.id(), amount)));
                if (bonusOre != null && tier >= bonusTier && random.nextDouble() * 100 < bonusPercent * mult) {
                    drops.add(new Drop(bonusOre, 1));
                }
                return new Roll(drops, true, false);
            }
        }
        for (Ore ore : ores) {
            double chance = ore.at(tier);
            if (chance <= 0) {
                continue;
            }
            boolean forced = ore.pity() > 0 && counters.getOrDefault(ore.id(), 0) >= ore.pity();
            if (forced || random.nextDouble() * 100 < chance * mult) {
                counters.put(ore.id(), 0);
                return new Roll(List.of(new Drop(ore.id(), 1)), false, forced);
            }
        }
        return Roll.NOTHING;
    }

    /** The first geode ore available at the tier (the config lists the rarest first): what a geode is made of. */
    public Ore best(int tier) {
        List<Ore> available = new ArrayList<>();
        for (Ore ore : ores) {
            if (ore.at(tier) > 0 && ore.geode()) {
                available.add(ore);
            }
        }
        return available.isEmpty() ? null : available.get(0);
    }
}
