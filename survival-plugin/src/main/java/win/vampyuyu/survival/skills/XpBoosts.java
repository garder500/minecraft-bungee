package win.vampyuyu.survival.skills;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;
import org.jetbrains.annotations.Nullable;
import win.vampyuyu.survival.api.Skill;

/**
 * Named, optionally timed, experience multipliers (a "double XP weekend", an event boost). In memory only: a
 * restart clears them. Boosts add up as {@code 1 + sum(multiplier - 1)} (two x2 boosts give x3, not x4) and the
 * total is capped by {@code boosts.max_total}.
 *
 * <p>Pets, accessories and other features do not use this class: they change the amount from a
 * {@code SkillXpEvent} listener ({@code event.setAmount(...)}), which runs after this multiplier.
 */
public final class XpBoosts {
    /** {@code skill == null} means every skill; {@code expiresAt <= 0} never expires. */
    public record Boost(String name, @Nullable Skill skill, double multiplier, long expiresAt) {}

    private final Map<String, Boost> boosts = new ConcurrentHashMap<>();
    private final LongSupplier clock;

    public XpBoosts() {
        this(System::currentTimeMillis);
    }

    public XpBoosts(LongSupplier clock) {
        this.clock = clock;
    }

    /** Adds or replaces the boost called {@code name}. A multiplier of 1 or less is rejected. */
    public void add(String name, @Nullable Skill skill, double multiplier, long durationMillis) {
        if (name == null || name.isBlank() || !(multiplier > 1) || Double.isInfinite(multiplier)) {
            throw new IllegalArgumentException("a boost needs a name and a multiplier above 1");
        }
        long expires = durationMillis > 0 ? clock.getAsLong() + durationMillis : 0;
        boosts.put(name, new Boost(name, skill, multiplier, expires));
    }

    public boolean remove(String name) {
        return boosts.remove(name) != null;
    }

    public void clear() {
        boosts.clear();
    }

    /** Combined multiplier for a skill right now (1 when no boost applies), at most {@code cap}. */
    public double multiplier(Skill skill, double cap) {
        long now = clock.getAsLong();
        double extra = 0;
        for (Boost boost : boosts.values()) {
            if (boost.expiresAt() > 0 && boost.expiresAt() <= now) {
                boosts.remove(boost.name(), boost);
                continue;
            }
            if (boost.skill() == null || boost.skill() == skill) {
                extra += boost.multiplier() - 1;
            }
        }
        return Math.min(Math.max(1, cap), 1 + extra);
    }

    /** Active boosts, soonest expiry first. */
    public List<Boost> active() {
        long now = clock.getAsLong();
        List<Boost> out = new ArrayList<>();
        for (Boost boost : boosts.values()) {
            if (boost.expiresAt() > 0 && boost.expiresAt() <= now) {
                boosts.remove(boost.name(), boost);
            } else {
                out.add(boost);
            }
        }
        out.sort(Comparator.comparingLong((Boost b) -> b.expiresAt() == 0 ? Long.MAX_VALUE : b.expiresAt())
            .thenComparing(Boost::name));
        return out;
    }

    public long now() {
        return clock.getAsLong();
    }
}
