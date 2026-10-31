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

    /** Same, with {@code sunny}: whether a solar panel at that position is in daylight under the open sky. */
    static List<Fire> tick(Map<Pos, MachineData> network, MachineSettings cfg, Predicate<Pos> ready, Predicate<Pos> sunny) {
        double pool = 0;
        long capacity = 0;
        List<MachineData> batteries = new ArrayList<>();
        for (Map.Entry<Pos, MachineData> entry : network.entrySet()) {
            MachineData m = entry.getValue();
            MachineType type = m.kind();
            if (type == MachineType.SOLAR_PANEL && sunny.test(entry.getKey())) {
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
            if (type == null || !type.miner() || !ready.test(entry.getKey())) {
                continue;
            }
            double need = cfg.consume(type);
            if (pool < need) {
                continue;
            }
            pool -= need;
            m.progress += cfg.rate(type);
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
