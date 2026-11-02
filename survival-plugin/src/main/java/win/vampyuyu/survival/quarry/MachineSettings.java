package win.vampyuyu.survival.quarry;

import win.vampyuyu.survival.data.Settings;

/** The numbers of the machines (the {@code quarry.machines.*} settings). Flux is the energy unit; rates are per second. There is no machine limit per island. */
final class MachineSettings {
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

    /** Module numbers: factors on the Miner's Flux use and speed, and the Bloom bone meal chance per cycle. */
    final double emberConsume;
    final double emberRate;
    final double bloomConsume;
    final double bloomRate;
    final double bloomBoneMealChance;
    final double memoryConsume;
    final double memoryRate;
    final double memoryShardChance;
    final double cryoConsume;
    final double cryoRate;
    final double cryoIceChance;

    MachineSettings(Settings s) {
        emberConsume = positive(s, "module.ember.consume-factor", 1.8);
        emberRate = positive(s, "module.ember.rate-factor", 1.5);
        bloomConsume = positive(s, "module.bloom.consume-factor", 0.5);
        bloomRate = positive(s, "module.bloom.rate-factor", 0.8);
        bloomBoneMealChance = positive(s, "module.bloom.bone-meal-chance", 0.25);
        memoryConsume = positive(s, "module.memory.consume-factor", 1.3);
        memoryRate = positive(s, "module.memory.rate-factor", 0.7);
        memoryShardChance = positive(s, "module.memory.shard-chance", 0.03);
        cryoConsume = positive(s, "module.cryo.consume-factor", 1.3);
        cryoRate = positive(s, "module.cryo.rate-factor", 1.3);
        cryoIceChance = positive(s, "module.cryo.ice-chance", 0.3);
        generatorOutput = positive(s, "coal-generator.output", 10);
        coalSeconds = (int) Math.max(1, positive(s, "coal-generator.coal-seconds", 40));
        solarOutput = positive(s, "solar-panel.output", 6);
        batteryCapacity = (long) positive(s, "battery.capacity", 10_000);
        miner1Consume = positive(s, "miner-1.consume", 8);
        miner1Rate = positive(s, "miner-1.cycles-per-second", 0.5);
        miner2Consume = positive(s, "miner-2.consume", 20);
        miner2Rate = positive(s, "miner-2.cycles-per-second", 1);
    }

    private static double positive(Settings s, String path, double fallback) {
        double v = s.getDouble("machines." + path, fallback);
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

    /** Flux per second of this machine, with its module. */
    double consume(MachineData data) {
        double base = consume(data.kind());
        MachineModule module = MachineModule.of(data.module);
        return module == null ? base : base * consumeFactor(module);
    }

    /** Mining cycles per second of this machine, with its module. */
    double rate(MachineData data) {
        double base = rate(data.kind());
        MachineModule module = MachineModule.of(data.module);
        return module == null ? base : base * rateFactor(module);
    }

    /** Factor a module puts on the Miner's Flux use. */
    double consumeFactor(MachineModule module) {
        return switch (module) {
            case EMBER -> emberConsume;
            case BLOOM -> bloomConsume;
            case MEMORY -> memoryConsume;
            case CRYO -> cryoConsume;
        };
    }

    /** Factor a module puts on the Miner's cycles per second. */
    double rateFactor(MachineModule module) {
        return switch (module) {
            case EMBER -> emberRate;
            case BLOOM -> bloomRate;
            case MEMORY -> memoryRate;
            case CRYO -> cryoRate;
        };
    }
}
