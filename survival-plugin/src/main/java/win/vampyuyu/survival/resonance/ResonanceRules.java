package win.vampyuyu.survival.resonance;

import win.vampyuyu.survival.api.Resonance;
import win.vampyuyu.survival.resonance.ResonanceConfig.Bastion;

/** The bookkeeping rules on a {@link ResonanceData}, with no Bukkit so they are unit tested. Callers hold the lock. */
public final class ResonanceRules {
    private ResonanceRules() {}

    /** What a cleared run paid. */
    public record Clear(long shards, boolean firstOfDay, boolean newBest) {}

    public enum Forge { OK, NOT_FOUND, NOT_ENOUGH, WEEKLY_LIMIT }

    /** A boss was killed at this rank: counts the clear, opens the next rank and pays the Shards. */
    public static Clear clear(ResonanceData d, Bastion b, int rank, long now, ResonanceConfig cfg) {
        int r = Resonance.clamp(rank);
        String today = ResonanceMath.day(now);
        boolean first = !today.equals(d.lastFirstClear);
        long shards = ResonanceMath.killShards(b.baseShards(), r, cfg.rankBonus(), first, cfg.firstClearMultiplier());
        d.lastFirstClear = today;
        d.clears.merge(b.bossId(), 1, Integer::sum);
        boolean best = r > d.best.getOrDefault(b.bossId(), 0);
        if (best) {
            d.best.put(b.bossId(), r);
        }
        d.shards += shards;
        d.earned += shards;
        return new Clear(shards, first, best);
    }

    /** A fight that was lost after some progress still pays a small fraction of the Shards (GDD 15.4). */
    public static long defeat(ResonanceData d, Bastion b, int rank, double progress, ResonanceConfig cfg) {
        long shards = ResonanceMath.defeatShards(b.baseShards(), rank, cfg.rankBonus(), cfg.defeatFraction(), progress);
        d.shards += shards;
        d.earned += shards;
        return shards;
    }

    /**
     * Boss guarantee (GDD 15.2) after a run: true when the player must be handed the component now. A component that
     * dropped by itself resets the count; either way the boss counts as found, which opens the Shard forge.
     */
    public static boolean pity(ResonanceData d, Bastion b, int rank, boolean dropped, ResonanceConfig cfg) {
        if (!d.found.contains(b.bossId()) && dropped) {
            d.found.add(b.bossId());
        }
        if (dropped) {
            d.dry.remove(b.bossId());
            return false;
        }
        int limit = ResonanceMath.pityLimit(rank, cfg.pityRuns(), cfg.pityRunsHigh(), cfg.pityHighFrom());
        int dry = d.dry.getOrDefault(b.bossId(), 0);
        if (ResonanceMath.pityDue(dry, limit)) {
            d.dry.remove(b.bossId());
            if (!d.found.contains(b.bossId())) {
                d.found.add(b.bossId());
            }
            return true;
        }
        d.dry.put(b.bossId(), dry + 1);
        return false;
    }

    /** Spends the Shards on one component of a boss already discovered. The caller delivers the item on OK. */
    public static Forge forge(ResonanceData d, Bastion b, long now, ResonanceConfig cfg) {
        if (!d.found.contains(b.bossId())) {
            return Forge.NOT_FOUND;
        }
        String week = ResonanceMath.week(now);
        if (!week.equals(d.forgeWeek)) {
            d.forgeWeek = week;
            d.forgedThisWeek = 0;
        }
        if (d.forgedThisWeek >= cfg.weeklyForgeLimit()) {
            return Forge.WEEKLY_LIMIT;
        }
        if (d.shards < cfg.forgeCost()) {
            return Forge.NOT_ENOUGH;
        }
        d.shards -= cfg.forgeCost();
        d.forgedThisWeek++;
        d.forged.merge(b.bossId(), 1, Integer::sum);
        return Forge.OK;
    }
}
