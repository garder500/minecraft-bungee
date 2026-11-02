package win.vampyuyu.survival.station;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.function.ToIntFunction;
import win.vampyuyu.survival.station.StationConfig.Contract;
import win.vampyuyu.survival.station.StationConfig.Product;

/** The pure rules of the stations (no Bukkit): the Alembic queue, the Ranch clock, the Crucible refund and the daily contracts. */
final class StationRules {
    private StationRules() {}

    // ---------------------------------------------------------------- Alembic

    /**
     * Moves the queue to {@code now}: finished brews go to {@code ready}, and a waiting brew starts the moment a slot is
     * freed (so a queue keeps running while the player is away). Returns how many brews finished.
     */
    static int advance(StationData d, long now, int slots, ToIntFunction<String> secondsOf) {
        int finished = 0;
        while (true) {
            StationData.Job next = null;
            for (StationData.Job j : d.jobs) {
                if (j.endAt > 0 && j.endAt <= now && (next == null || j.endAt < next.endAt)) {
                    next = j;
                }
            }
            if (next == null) {
                break;
            }
            d.jobs.remove(next);
            d.ready.add(next.recipe);
            finished++;
            start(d, next.endAt, slots, secondsOf);
        }
        start(d, now, slots, secondsOf);
        return finished;
    }

    /** Starts waiting brews while a slot is free, all starting at {@code at}. */
    private static void start(StationData d, long at, int slots, ToIntFunction<String> secondsOf) {
        long running = d.jobs.stream().filter(j -> j.endAt > 0).count();
        for (StationData.Job j : d.jobs) {
            if (running >= slots) {
                break;
            }
            if (j.endAt == 0) {
                j.endAt = at + secondsOf.applyAsInt(j.recipe) * 1000L;
                running++;
            }
        }
    }

    // ---------------------------------------------------------------- Ranch

    /** Units of a product waiting after {@code sinceMillis}: the elapsed time is capped by the storage, and the tier adds output. */
    static int ranchUnits(long sinceMillis, long nowMillis, Product p, int tier, double tierBonus, int storageHours) {
        if (sinceMillis <= 0 || nowMillis <= sinceMillis || tier < p.minTier()) {
            return 0;
        }
        long elapsed = Math.min(nowMillis - sinceMillis, storageHours * 3_600_000L);
        double factor = 1 + tierBonus * Math.max(0, tier - 1);
        return (int) Math.floor(elapsed / 1000.0 * factor / p.seconds() + 1e-9);
    }

    // ---------------------------------------------------------------- Crucible

    /**
     * What the Crucible gives back for a recipe: boss components whole, everything else at {@code rate} (rounded down, so one
     * piece of something is not given back).
     */
    static Map<String, Integer> refund(Map<String, Integer> cost, double rate, Set<String> components) {
        Map<String, Integer> out = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> e : cost.entrySet()) {
            String item = e.getKey().startsWith("custom:") ? e.getKey().substring(7) : e.getKey();
            int back = components.contains(item) ? e.getValue() : (int) Math.floor(e.getValue() * rate + 1e-9);
            if (back > 0) {
                out.put(e.getKey(), back);
            }
        }
        return out;
    }

    // ---------------------------------------------------------------- Contracts

    /** The UTC day of a time, {@code yyyy-MM-dd}. */
    static String day(long millis) {
        return Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate().toString();
    }

    /** Today's contracts for a player: the same for the same player and day, within their generator tier. */
    static List<String> offer(String day, UUID player, Iterable<Contract> pool, int tier, int count) {
        List<String> ids = new ArrayList<>();
        for (Contract c : pool) {
            if (c.minTier() <= tier) {
                ids.add(c.id());
            }
        }
        Collections.shuffle(ids, new Random(day.hashCode() * 31L + player.getLeastSignificantBits() * 17L + player.getMostSignificantBits()));
        return new ArrayList<>(ids.subList(0, Math.min(count, ids.size())));
    }

    /** Starts the player's day: new contracts and a clean sheet when the UTC day changed. True when it did. */
    static boolean rollDay(StationData d, String today, UUID player, Iterable<Contract> pool, int tier, int count) {
        if (today.equals(d.contractDay)) {
            return false;
        }
        d.contractDay = today;
        d.offered = offer(today, player, pool, tier, count);
        d.progress = new java.util.HashMap<>();
        d.done = new ArrayList<>();
        d.coinsToday = 0;
        return true;
    }

    /** Counts one kill (kind {@code kill}, target an entity type) or boss kill (kind {@code boss}); returns the contracts that just became ready. */
    static List<String> count(StationData d, Map<String, Contract> all, String kind, String target) {
        List<String> ready = new ArrayList<>();
        for (String id : d.offered) {
            Contract c = all.get(id);
            if (c == null || !c.kind().equals(kind) || !c.target().equalsIgnoreCase(target) || d.done.contains(id)) {
                continue;
            }
            int before = d.progress.getOrDefault(id, 0);
            if (before < c.amount()) {
                d.progress.put(id, before + 1);
                if (before + 1 == c.amount()) {
                    ready.add(id);
                }
            }
        }
        return ready;
    }

    /** Coins a contract may still pay today under the daily cap. */
    static int payableCoins(StationData d, Contract c, int cap) {
        return Math.max(0, Math.min(c.coins(), cap - d.coinsToday));
    }
}
