package win.vampyuyu.survival.minions;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.random.RandomGenerator;
import org.bukkit.Material;
import org.jspecify.annotations.Nullable;
import win.vampyuyu.survival.minions.MinionConfig.Drop;
import win.vampyuyu.survival.minions.MinionConfig.Effect;
import win.vampyuyu.survival.minions.MinionConfig.Fuel;
import win.vampyuyu.survival.minions.MinionConfig.Type;
import win.vampyuyu.survival.minions.MinionConfig.Upgrade;

/**
 * Everything a minion produces, computed from its saved data: speed, drops, upgrades, storage and the catch-up of
 * a long absence. No world access, so it runs the same for a minion in view and one in an unloaded chunk.
 */
public final class MinionLogic {
    /** Speed multipliers of a minion. */
    public record Speeds(double plain, double fuel, double burn) {}

    private final MinionConfig config;
    private final Compaction compaction;
    private final RandomGenerator random;

    public MinionLogic(MinionConfig config, RandomGenerator random) {
        this.config = config;
        this.compaction = new Compaction(config);
        this.random = random;
    }

    public MinionConfig config() {
        return config;
    }

    public Compaction compaction() {
        return compaction;
    }

    // ---------------------------------------------------------------- derived values

    public List<Upgrade> upgradesOf(MinionData d) {
        List<Upgrade> out = new ArrayList<>();
        for (String id : d.upgrades) {
            Upgrade up = config.upgrades.get(id);
            if (up != null) {
                out.add(up);
            }
        }
        return out;
    }

    /** The fuel burning in the minion, or null. */
    public @Nullable Fuel fuelOf(MinionData d) {
        return d.fuel == null || d.fuelMs <= 0 ? null : config.fuels.get(d.fuel);
    }

    /**
     * Speed = 1 + fuel + every speed upgrade (all additive). Fuel that does not suit the minion's kind adds nothing.
     * Burn rate = product of the upgrades' burn factors.
     */
    public Speeds speeds(MinionData d, Type type) {
        double plain = 1;
        double burn = 1;
        for (Upgrade up : upgradesOf(d)) {
            if (up.effect() == Effect.SPEED) {
                plain += up.speed();
            }
            burn *= up.burn();
        }
        Fuel fuel = fuelOf(d);
        double withFuel = plain + (fuel != null && fuel.accepts(type.kind()) ? fuel.speed() : 0);
        return new Speeds(plain, withFuel, Math.max(0.1, burn));
    }

    public double cycleMillis(MinionData d, Type type) {
        return config.cycleMillis(type, d.tier);
    }

    /** Seconds one resource takes right now (with the current fuel). */
    public double secondsPerItem(MinionData d, Type type) {
        Speeds s = speeds(d, type);
        return Production.cycleSeconds(cycleMillis(d, type), fuelOf(d) != null ? s.fuel() : s.plain());
    }

    public int radius(MinionData d) {
        int bonus = 0;
        for (Upgrade up : upgradesOf(d)) {
            if (up.effect() == Effect.EXPAND) {
                bonus += up.radius();
            }
        }
        return config.radius(d.tier, bonus);
    }

    public Storage storage(MinionData d) {
        d.storage.values().removeIf(n -> n == null || n <= 0);
        return new Storage(d.storage, config.storageSlots(d.tier));
    }

    private boolean has(MinionData d, Effect effect) {
        return upgradesOf(d).stream().anyMatch(u -> u.effect() == effect);
    }

    /** SUPER_COMPACT replaces COMPACT; null when neither is installed. */
    public @Nullable Effect compactMode(MinionData d) {
        if (has(d, Effect.SUPER_COMPACT)) {
            return Effect.SUPER_COMPACT;
        }
        return has(d, Effect.COMPACT) ? Effect.COMPACT : null;
    }

    // ---------------------------------------------------------------- production

    /** Rolls the drops of one cycle (before upgrades). */
    public Map<Material, Integer> roll(Type type) {
        Map<Material, Integer> out = new LinkedHashMap<>();
        if (type.weighted()) {
            double total = 0;
            for (Drop drop : type.drops()) {
                total += drop.chance();
            }
            double pick = random.nextDouble() * total;
            for (Drop drop : type.drops()) {
                pick -= drop.chance();
                if (pick < 0) {
                    out.merge(drop.item(), amount(drop), Integer::sum);
                    break;
                }
            }
        } else {
            for (Drop drop : type.drops()) {
                if (drop.chance() >= 1 || random.nextDouble() < drop.chance()) {
                    out.merge(drop.item(), amount(drop), Integer::sum);
                }
            }
        }
        out.values().removeIf(n -> n <= 0);
        return out;
    }

    private int amount(Drop drop) {
        return drop.min() + (drop.max() > drop.min() ? random.nextInt(drop.max() - drop.min() + 1) : 0);
    }

    /**
     * One finished cycle: roll, smelt, spread, store, compact. All or nothing: false (and nothing changed) when the
     * result does not fit, so a full minion never loses an item.
     */
    public boolean produce(MinionData d, Type type) {
        Map<Material, Integer> rolled = roll(type);
        boolean smelting = has(d, Effect.SMELT);
        Map<Material, Long> byMaterial = new LinkedHashMap<>();
        long units = 0;
        for (Map.Entry<Material, Integer> e : rolled.entrySet()) {
            Material material = smelting ? config.smelted(e.getKey()) : e.getKey();
            byMaterial.merge(material, (long) e.getValue(), Long::sum);
            units += e.getValue();
        }
        int spread = d.spread;
        long bonus = 0;
        if (has(d, Effect.SPREAD)) {
            long total = d.spread + units;
            bonus = total / config.spreadEvery;
            spread = (int) (total % config.spreadEvery);
            if (bonus > 0) {
                byMaterial.merge(Material.DIAMOND, bonus, Long::sum);
            }
        }
        Map<String, Long> items = new LinkedHashMap<>();
        byMaterial.forEach((m, n) -> items.put(ItemKey.vanilla(m), n));
        Storage storage = storage(d);
        if (!storage.addAll(items)) {
            return false;
        }
        d.spread = spread;
        d.produced += units + bonus;
        compaction.apply(storage, d.storage, compactMode(d), items.keySet());
        return true;
    }

    /**
     * Lets real time pass: from {@code d.lastTick} to {@code now} (at most the catch-up window), producing as many
     * cycles as fit. When {@code workable} is false (blocked terrain, missing island) time passes without work and
     * without burning fuel.
     */
    public Production.Outcome advance(MinionData d, Type type, long now, boolean workable) {
        long elapsed = Math.max(0, Math.min(now - d.lastTick, config.maxCatchupMillis()));
        d.lastTick = now;
        if (d.fuel != null && (d.fuelMs <= 0 || config.fuels.get(d.fuel) == null)) {
            d.fuel = null;
            d.fuelMs = 0;
        }
        if (!workable) {
            return new Production.Outcome(0, false);
        }
        Speeds speeds = speeds(d, type);
        Production.Clock clock = new Production.Clock(d.progress, d.fuelMs);
        Production.Outcome outcome = Production.advance(clock, elapsed, cycleMillis(d, type), speeds.plain(),
            speeds.fuel(), speeds.burn(), () -> produce(d, type));
        d.progress = clock.progress;
        d.fuelMs = clock.fuelMs;
        if (d.fuelMs <= 0) {
            d.fuel = null;
            d.fuelMs = 0;
        }
        return outcome;
    }
}
