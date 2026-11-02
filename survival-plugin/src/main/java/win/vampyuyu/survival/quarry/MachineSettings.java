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
    /** Base Flux per second of a Lightning Rod (the surge factor of {@link win.vampyuyu.survival.realm.StormMath} multiplies it). */
    final double rodOutput;
    /** Seconds one Thunder Quartz keeps a Lightning Rod burning when there is no thunderstorm. */
    final int rodQuartzSeconds;
    /** Custom item that fuels a Lightning Rod. */
    static final String ROD_FUEL_ITEM = "thunder_quartz";
    final long batteryCapacity;
    /** Flux per second of the passive generators (Spring Wheel, Heat Tap, Void Reactor, Astral Lattice), before the overload. */
    private final double[] passiveOutput = new double[PassiveGenerator.values().length];
    /** Overload: extra Blocks that can sit in a generator, and the share of its output each one adds. */
    final int overloadBlocks;
    final double overloadBonus;
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
    final double recyclerConsume;
    final double recyclerRate;
    final double recyclerSalvageChance;
    /** Chrono Module: factors on Flux use and speed, the tide's effect on speed, the buffer cap, what it banks and gives back. */
    final double chronoConsume;
    final double chronoRate;
    final double chronoSurge;
    final double chronoStall;
    final double chronoBufferCap;
    final double chronoBankFraction;
    final double chronoRelease;
    /** The Time Tide of the Citadel (the {@code island.citadel.tide.*} settings). */
    final win.vampyuyu.survival.api.TimeTide tide;
    final double tideConsume;
    final double tideRate;
    final double tideShardChance;

    MachineSettings(Settings s) {
        this(s, win.vampyuyu.survival.api.TimeTide.defaults());
    }

    MachineSettings(Settings s, win.vampyuyu.survival.api.TimeTide tide) {
        this.tide = tide;
        chronoConsume = positive(s, "module.chrono.consume-factor", 1.0);
        chronoRate = positive(s, "module.chrono.rate-factor", 1.0);
        chronoSurge = positive(s, "module.chrono.surge-factor", 1.5);
        chronoStall = positive(s, "module.chrono.stall-factor", 0.6);
        chronoBufferCap = positive(s, "module.chrono.buffer-cap", 120);
        chronoBankFraction = positive(s, "module.chrono.bank-fraction", 0.5);
        chronoRelease = positive(s, "module.chrono.release-per-second", 1);
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
        recyclerConsume = positive(s, "module.recycler.consume-factor", 0.7);
        recyclerRate = positive(s, "module.recycler.rate-factor", 0.9);
        recyclerSalvageChance = positive(s, "module.recycler.salvage-chance", 0.15);
        tideConsume = positive(s, "module.tide.consume-factor", 0.6);
        tideRate = positive(s, "module.tide.rate-factor", 0.9);
        tideShardChance = positive(s, "module.tide.shard-chance", 0.2);
        for (PassiveGenerator g : PassiveGenerator.values()) {
            passiveOutput[g.ordinal()] = positive(s, g.key + ".output", g.defaultOutput);
        }
        overloadBlocks = (int) Math.max(0, s.getDouble("machines.overload.blocks", 3));
        overloadBonus = positive(s, "overload.bonus", 0.25);
        generatorOutput = positive(s, "coal-generator.output", 5);
        coalSeconds = (int) Math.max(1, positive(s, "coal-generator.coal-seconds", 40));
        solarOutput = positive(s, "solar-panel.output", 2);
        rodOutput = positive(s, "lightning-rod.output", 30);
        rodQuartzSeconds = (int) Math.max(1, positive(s, "lightning-rod.quartz-seconds", 180));
        batteryCapacity = (long) positive(s, "battery.capacity", 10_000);
        miner1Consume = positive(s, "miner-1.consume", 2);
        miner1Rate = positive(s, "miner-1.cycles-per-second", 0.5);
        miner2Consume = positive(s, "miner-2.consume", 6);
        miner2Rate = positive(s, "miner-2.cycles-per-second", 1);
    }

    private static double positive(Settings s, String path, double fallback) {
        double v = s.getDouble("machines." + path, fallback);
        return v > 0 ? v : fallback;
    }

    /** Flux per second a passive generator makes with {@code blocks} compression Blocks in it (0 until the first one activates it). */
    double passiveOutput(PassiveGenerator generator, int blocks) {
        return passiveOutput[generator.ordinal()] * PassiveGenerator.overload(blocks, overloadBlocks, overloadBonus);
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
        return rate(data, tideFactor(System.currentTimeMillis()));
    }

    /** Same, with the factor of the Time Tide that a Chrono Module follows (others ignore it). */
    double rate(MachineData data, double tide) {
        double base = rate(data.kind());
        MachineModule module = MachineModule.of(data.module);
        return module == null ? base : base * rateFactor(module, tide);
    }

    /** Factor a module puts on the Miner's Flux use. */
    double consumeFactor(MachineModule module) {
        return switch (module) {
            case EMBER -> emberConsume;
            case BLOOM -> bloomConsume;
            case MEMORY -> memoryConsume;
            case CRYO -> cryoConsume;
            case RECYCLER -> recyclerConsume;
            case CHRONO -> chronoConsume;
            case TIDE -> tideConsume;
        };
    }

    /** Factor a module puts on the Miner's cycles per second ({@code tide}: the factor the Chrono Module follows). */
    double rateFactor(MachineModule module, double tide) {
        return switch (module) {
            case EMBER -> emberRate;
            case BLOOM -> bloomRate;
            case MEMORY -> memoryRate;
            case CRYO -> cryoRate;
            case RECYCLER -> recyclerRate;
            case CHRONO -> chronoRate * tide;
            case TIDE -> tideRate;
        };
    }

    /** Speed factor of the Time Tide on a Chrono Module at an instant: faster in a surge, slower in a stall. */
    double tideFactor(long epochMillis) {
        return switch (tide.at(epochMillis).phase()) {
            case SURGE -> chronoSurge;
            case STALL -> chronoStall;
            case CALM -> 1.0;
        };
    }
}
