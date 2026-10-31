package win.vampyuyu.survival.quarry;

import org.bukkit.configuration.ConfigurationSection;

/** The numbers of the machines (quarry.yml {@code machines}). Flux is the energy unit; rates are per second. */
final class MachineSettings {
    final int maxPerIsland;
    /** Flux per second a burning coal generator makes. */
    final double generatorOutput;
    /** Seconds one coal burns. */
    final int coalSeconds;
    /** Flux per second a solar panel makes in daylight under the open sky. */
    final double solarOutput;
    final long batteryCapacity;
    /** Flux per second and mining cycles per second of the miners. */
    final double miner1Consume;
    final double miner1Rate;
    final double miner2Consume;
    final double miner2Rate;

    MachineSettings(ConfigurationSection s) {
        maxPerIsland = Math.max(1, s == null ? 6 : s.getInt("max-per-island", 6));
        generatorOutput = positive(s, "coal-generator.output", 10);
        coalSeconds = (int) Math.max(1, positive(s, "coal-generator.coal-seconds", 40));
        solarOutput = positive(s, "solar-panel.output", 6);
        batteryCapacity = (long) positive(s, "battery.capacity", 10_000);
        miner1Consume = positive(s, "miner-1.consume", 8);
        miner1Rate = positive(s, "miner-1.cycles-per-second", 0.5);
        miner2Consume = positive(s, "miner-2.consume", 20);
        miner2Rate = positive(s, "miner-2.cycles-per-second", 1);
    }

    private static double positive(ConfigurationSection s, String path, double fallback) {
        double v = s == null ? fallback : s.getDouble(path, fallback);
        return v > 0 ? v : fallback;
    }

    double consume(MachineType type) {
        return switch (type) {
            case MINER_1 -> miner1Consume;
            case MINER_2 -> miner2Consume;
            default -> 0;
        };
    }

    double rate(MachineType type) {
        return switch (type) {
            case MINER_1 -> miner1Rate;
            case MINER_2 -> miner2Rate;
            default -> 0;
        };
    }
}
