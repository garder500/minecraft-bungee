package win.vampyuyu.survival.quarry;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Pure energy logic. Machines that touch each other (cables conduct, every machine is a node) form a network. Once
 * per second a network burns fuel, pools its production with its batteries, lets every miner that can work draw its
 * Flux, and stores what is left in the batteries (what does not fit is lost).
 */
final class EnergyNetwork {
    /** A block position inside one world. */
    record Pos(int x, int y, int z) {
        List<Pos> neighbours() {
            return List.of(new Pos(x + 1, y, z), new Pos(x - 1, y, z), new Pos(x, y + 1, z), new Pos(x, y - 1, z),
                new Pos(x, y, z + 1), new Pos(x, y, z - 1));
        }
    }

    /** A miner that ran {@code cycles} mining cycles this second. */
    record Fire(Pos pos, int cycles) {}

    static final int MAX_CYCLES_PER_SECOND = 4;

    private EnergyNetwork() {}

    /** Groups machine positions into networks of touching machines. */
    static List<List<Pos>> components(Set<Pos> machines) {
        List<List<Pos>> out = new ArrayList<>();
        Set<Pos> seen = new LinkedHashSet<>();
        for (Pos start : machines) {
            if (!seen.add(start)) {
                continue;
            }
            List<Pos> group = new ArrayList<>();
            ArrayDeque<Pos> queue = new ArrayDeque<>();
            queue.add(start);
            while (!queue.isEmpty()) {
                Pos at = queue.poll();
                group.add(at);
                for (Pos next : at.neighbours()) {
                    if (machines.contains(next) && seen.add(next)) {
                        queue.add(next);
                    }
                }
            }
            out.add(group);
        }
        return out;
    }

    /**
     * One second of one network. {@code ready} says whether a miner has somewhere to put its ore; a miner that
     * cannot work draws nothing. Returns the miners that ran.
     */
    static List<Fire> tick(Map<Pos, MachineData> network, MachineSettings cfg, Predicate<Pos> ready) {
        return tick(network, cfg, ready, p -> false);
    }

    /**
     * True when the network has Flux this second: a solar panel in the sun, a burning coal generator or a charged
     * battery. Read before {@link #tick}, which spends the fuel and the charge.
     */
    static boolean hasFlux(Map<Pos, MachineData> network, Predicate<Pos> sunny) {
        return hasFlux(network, sunny, Sky.NONE);
    }

    /** Same, counting the Lightning Rods that are in a thunderstorm or burning Thunder Quartz. */
    static boolean hasFlux(Map<Pos, MachineData> network, Predicate<Pos> sunny, Sky sky) {
        for (Map.Entry<Pos, MachineData> entry : network.entrySet()) {
            MachineData m = entry.getValue();
            MachineType type = m.kind();
            if (type != null && type.generator() != null && m.blocks > 0 && sky.fed(entry.getKey(), type.generator())) {
                return true;
            }
            if ((type == MachineType.LIGHTNING_ROD && (m.fuel > 0 || sky.stormy(entry.getKey())))
                    || (type == MachineType.SOLAR_PANEL && sunny.test(entry.getKey()))
                    || (type == MachineType.COAL_GENERATOR && m.fuel > 0) || (type == MachineType.BATTERY && m.charge > 0)) {
                return true;
            }
        }
        return false;
    }

    /** Same, with {@code sunny}: whether a solar panel at that position is in daylight under the open sky. */
    static List<Fire> tick(Map<Pos, MachineData> network, MachineSettings cfg, Predicate<Pos> ready, Predicate<Pos> sunny) {
        return tick(network, cfg, ready, sunny, Modifiers.NONE);
    }

    /**
     * What the trades change for one miner (GDD 11.3): {@code energy} multiplies the Flux it draws per cycle (Artisan),
     * {@code speed} its cycles per second (Overclock). A faster miner draws proportionally more Flux per second.
     */
    interface Modifiers {
        Modifiers NONE = new Modifiers() {
            @Override
            public double energy(MachineData miner) {
                return 1;
            }

            @Override
            public double speed(MachineData miner) {
                return 1;
            }
        };

        double energy(MachineData miner);

        double speed(MachineData miner);
    }

    /** The weather above the machines: where a Lightning Rod stands in a thunderstorm, and which second it is (for the surges). */
    interface Sky {
        Sky NONE = new Sky() {
            @Override
            public boolean stormy(Pos pos) {
                return false;
            }

            @Override
            public long second() {
                return 0;
            }
        };

        /** True when the sky is open above the position during a thunderstorm. */
        boolean stormy(Pos pos);

        /** Any count that grows by one each second. */
        long second();

        /** True when the cells (and, for the Astral Lattice, the open sky) a passive generator needs are in place around it. */
        default boolean fed(Pos pos, PassiveGenerator generator) {
            return false;
        }
    }

    /** The full tick, with the trade modifiers of each miner. */
    static List<Fire> tick(Map<Pos, MachineData> network, MachineSettings cfg, Predicate<Pos> ready, Predicate<Pos> sunny,
            Modifiers mods) {
        return tick(network, cfg, ready, sunny, mods, System.currentTimeMillis(), Sky.NONE);
    }

    /** Same, at a given instant (the Time Tide of the Chrono Modules depends on it). */
    static List<Fire> tick(Map<Pos, MachineData> network, MachineSettings cfg, Predicate<Pos> ready, Predicate<Pos> sunny,
            Modifiers mods, long now) {
        return tick(network, cfg, ready, sunny, mods, now, Sky.NONE);
    }

    /** The full tick, with the weather for the Lightning Rods. */
    static List<Fire> tick(Map<Pos, MachineData> network, MachineSettings cfg, Predicate<Pos> ready, Predicate<Pos> sunny,
            Modifiers mods, Sky sky) {
        return tick(network, cfg, ready, sunny, mods, System.currentTimeMillis(), sky);
    }

    /** Same, at a given instant and with the weather. */
    static List<Fire> tick(Map<Pos, MachineData> network, MachineSettings cfg, Predicate<Pos> ready, Predicate<Pos> sunny,
            Modifiers mods, long now, Sky sky) {
        double pool = 0;
        long capacity = 0;
        List<MachineData> batteries = new ArrayList<>();
        for (Map.Entry<Pos, MachineData> entry : network.entrySet()) {
            MachineData m = entry.getValue();
            MachineType type = m.kind();
            if (type != null && type.generator() != null) {
                if (m.blocks > 0 && sky.fed(entry.getKey(), type.generator())) { // the first Block activates it, the others overload it
                    pool += cfg.passiveOutput(type.generator(), m.blocks);
                }
            } else if (type == MachineType.LIGHTNING_ROD) {
                boolean burning = m.fuel > 0;
                if (burning) {
                    m.fuel--;
                }
                if (burning || sky.stormy(entry.getKey())) {
                    Pos at = entry.getKey();
                    pool += cfg.rodOutput * win.vampyuyu.survival.realm.StormMath.surge(
                        win.vampyuyu.survival.realm.StormMath.seedOf(at.x(), at.y(), at.z()), sky.second());
                }
            } else if (type == MachineType.SOLAR_PANEL && sunny.test(entry.getKey())) {
                pool += cfg.solarOutput;
            } else if (type == MachineType.COAL_GENERATOR && m.fuel > 0) {
                m.fuel--;
                pool += cfg.generatorOutput;
            } else if (type == MachineType.BATTERY) {
                pool += m.charge;
                m.charge = 0;
                capacity += cfg.batteryCapacity;
                batteries.add(m);
            }
        }
        List<Fire> fires = new ArrayList<>();
        for (Map.Entry<Pos, MachineData> entry : new LinkedHashMap<>(network).entrySet()) {
            MachineData m = entry.getValue();
            MachineType type = m.kind();
            if (type == null || !type.miner()) {
                continue;
            }
            boolean chrono = MachineModule.of(m.module) == MachineModule.CHRONO;
            double speed = Math.max(0, mods.speed(m));
            if (!ready.test(entry.getKey())) {
                if (chrono) { // nowhere to put the ore: the cycles it could have run are banked
                    m.buffer = MachineModule.bank(m.buffer, cfg.rate(m.kind()) * cfg.chronoRate * speed, cfg.chronoBankFraction, cfg.chronoBufferCap);
                }
                continue;
            }
            double need = cfg.consume(m) * mods.energy(m) * speed;
            if (pool < need) {
                if (chrono) { // no Flux: the same
                    m.buffer = MachineModule.bank(m.buffer, cfg.rate(m.kind()) * cfg.chronoRate * speed, cfg.chronoBankFraction, cfg.chronoBufferCap);
                }
                continue;
            }
            pool -= need;
            double tide = chrono ? cfg.tideFactor(now) : 1;
            m.progress += cfg.rate(m, tide) * speed;
            if (chrono) {
                if (tide < 1) { // a stalled tide slows the Miner: what it loses goes into the buffer
                    m.buffer = MachineModule.bank(m.buffer, cfg.rate(m.kind()) * cfg.chronoRate * speed * (1 - tide), 1, cfg.chronoBufferCap);
                }
                double extra = tide >= 1 ? MachineModule.release(m.buffer, cfg.chronoRelease) : 0; // nothing is paid back in a stall
                m.buffer -= extra;
                m.progress += extra;
            }
            int cycles = Math.min(MAX_CYCLES_PER_SECOND, (int) Math.floor(m.progress));
            m.progress -= cycles;
            if (cycles > 0) {
                fires.add(new Fire(entry.getKey(), cycles));
            }
        }
        long left = (long) Math.floor(pool);
        for (MachineData battery : batteries) {
            long put = Math.min(left, cfg.batteryCapacity);
            battery.charge = put;
            left -= put;
        }
        return fires;
    }
}
