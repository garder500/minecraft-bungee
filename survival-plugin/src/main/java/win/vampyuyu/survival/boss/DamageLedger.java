package win.vampyuyu.survival.boss;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import win.vampyuyu.survival.api.event.BossKillEvent.Damager;

/**
 * Who dealt how much damage to one boss. Main thread only. A hit is capped at the health that was left, so an
 * overkill never inflates a share, and shares always add up to 1.
 */
public final class DamageLedger {
    private final Map<UUID, Double> damage = new LinkedHashMap<>();
    private double total;

    /**
     * Records a hit.
     *
     * @param remainingHp boss health before this hit
     * @return the damage that was actually credited (0 for a dead boss or a non-positive hit)
     */
    public double record(UUID player, double amount, double remainingHp) {
        if (!(amount > 0) || !(remainingHp > 0) || Double.isNaN(amount)) {
            return 0;
        }
        double credited = Math.min(amount, remainingHp);
        damage.merge(player, credited, Double::sum);
        total += credited;
        return credited;
    }

    public double damageOf(UUID player) {
        return damage.getOrDefault(player, 0.0);
    }

    public double total() {
        return total;
    }

    public int size() {
        return damage.size();
    }

    public boolean contains(UUID player) {
        return damage.containsKey(player);
    }

    public Set<UUID> participants() {
        return Set.copyOf(damage.keySet());
    }

    /** Fraction of all damage dealt by the player, 0 when nothing was dealt. */
    public double share(UUID player) {
        return total <= 0 ? 0 : damageOf(player) / total;
    }

    /** All participants, highest damage first (first hit wins a tie). */
    public List<Damager> ranking() {
        List<Damager> list = new ArrayList<>(damage.size());
        damage.forEach((id, value) -> list.add(new Damager(id, value)));
        list.sort((a, b) -> Double.compare(b.damage(), a.damage()));
        return list;
    }

    /** The {@code n} best damagers. */
    public List<Damager> top(int n) {
        List<Damager> all = ranking();
        return all.subList(0, Math.max(0, Math.min(n, all.size())));
    }

    /** 1-based rank, 0 when the player never hit the boss. */
    public int rankOf(UUID player) {
        if (!damage.containsKey(player)) {
            return 0;
        }
        List<Damager> all = ranking();
        for (int i = 0; i < all.size(); i++) {
            if (all.get(i).player().equals(player)) {
                return i + 1;
            }
        }
        return 0;
    }
}
