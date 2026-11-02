package win.vampyuyu.survival.quarry;

import static org.junit.Assert.*;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.Test;
import win.vampyuyu.survival.quarry.EnergyNetwork.Pos;

public class EnergyNetworkTest {
    private static final MachineSettings CFG = new MachineSettings(win.vampyuyu.survival.data.Settings.empty("quarry"));

    private static MachineData machine(MachineType type) {
        MachineData m = new MachineData();
        m.type = type.name();
        return m;
    }

    @Test
    public void aNetworkHasFluxFromSunCoalOrACharge() {
        var cable = machine(MachineType.CABLE);
        var coal = machine(MachineType.COAL_GENERATOR);
        var battery = machine(MachineType.BATTERY);
        var solar = machine(MachineType.SOLAR_PANEL);
        java.util.Map<Pos, MachineData> net = new java.util.LinkedHashMap<>();
        net.put(new Pos(0, 0, 0), cable);
        net.put(new Pos(1, 0, 0), coal);
        net.put(new Pos(2, 0, 0), battery);
        net.put(new Pos(3, 0, 0), solar);
        assertFalse("no fuel, no charge, night: the cables stay dark", EnergyNetwork.hasFlux(net, p -> false));
        assertTrue("a solar panel in the sun lights them", EnergyNetwork.hasFlux(net, p -> true));
        coal.fuel = 3;
        assertTrue(EnergyNetwork.hasFlux(net, p -> false));
        coal.fuel = 0;
        battery.charge = 10;
        assertTrue(EnergyNetwork.hasFlux(net, p -> false));
    }

    @Test
    public void touchingMachinesFormOneNetworkAndApartOnesDoNot() {
        var set = new LinkedHashSet<>(List.of(new Pos(0, 0, 0), new Pos(1, 0, 0), new Pos(2, 0, 0), new Pos(10, 0, 0),
            new Pos(10, 1, 0)));
        var groups = EnergyNetwork.components(set);
        assertEquals(2, groups.size());
        assertEquals(3, groups.get(0).size());
        assertEquals(2, groups.get(1).size());
        assertEquals("a diagonal is not a connection", 2,
            EnergyNetwork.components(new LinkedHashSet<>(List.of(new Pos(0, 0, 0), new Pos(1, 1, 0)))).size());
    }

    @Test
    public void aBurningGeneratorFeedsAMinerAndTheBatteryKeepsTheRest() {
        Map<Pos, MachineData> net = new LinkedHashMap<>();
        MachineData gen = machine(MachineType.COAL_GENERATOR);
        gen.fuel = 5;
        MachineData battery = machine(MachineType.BATTERY);
        MachineData miner = machine(MachineType.MINER_1);
        net.put(new Pos(0, 0, 0), gen);
        net.put(new Pos(1, 0, 0), battery);
        net.put(new Pos(2, 0, 0), miner);
        // miner I: 2 Flux and 0.5 cycles per second; the generator makes 5
        var first = EnergyNetwork.tick(net, CFG, p -> true);
        assertTrue("half a cycle is not a cycle yet", first.isEmpty());
        assertEquals(4, gen.fuel);
        assertEquals("5 made, 2 used, 3 stored", 3, battery.charge);
        var second = EnergyNetwork.tick(net, CFG, p -> true);
        assertEquals(1, second.size());
        assertEquals(1, second.get(0).cycles());
        assertEquals(new Pos(2, 0, 0), second.get(0).pos());
        assertEquals("5 + 3 stored, 2 used", 6, battery.charge);
    }

    @Test
    public void withoutEnergyNothingMinesAndBatteriesDrainUntilEmpty() {
        Map<Pos, MachineData> net = new LinkedHashMap<>();
        MachineData battery = machine(MachineType.BATTERY);
        battery.charge = 6;
        MachineData miner = machine(MachineType.MINER_2); // 6 Flux per second
        net.put(new Pos(0, 0, 0), battery);
        net.put(new Pos(1, 0, 0), miner);
        assertEquals(1, EnergyNetwork.tick(net, CFG, p -> true).size());
        assertEquals(0, battery.charge);
        assertTrue("the battery is empty now", EnergyNetwork.tick(net, CFG, p -> true).isEmpty());
    }

    @Test
    public void aMinerWithNowhereToPutItsOreDrawsNothing() {
        Map<Pos, MachineData> net = new LinkedHashMap<>();
        MachineData battery = machine(MachineType.BATTERY);
        battery.charge = 100;
        net.put(new Pos(0, 0, 0), battery);
        net.put(new Pos(1, 0, 0), machine(MachineType.MINER_2));
        assertTrue(EnergyNetwork.tick(net, CFG, p -> false).isEmpty());
        assertEquals(100, battery.charge);
    }

    @Test
    public void aSolarPanelOnlyMakesFluxInDaylight() {
        Map<Pos, MachineData> net = new LinkedHashMap<>();
        MachineData panel = machine(MachineType.SOLAR_PANEL);
        MachineData battery = machine(MachineType.BATTERY);
        net.put(new Pos(0, 0, 0), panel);
        net.put(new Pos(1, 0, 0), battery);
        EnergyNetwork.tick(net, CFG, p -> true, p -> false);
        assertEquals("night: nothing", 0, battery.charge);
        EnergyNetwork.tick(net, CFG, p -> true, p -> true);
        assertEquals((long) CFG.solarOutput, battery.charge);
    }

    @Test
    public void aBatteryNeverStoresMoreThanItsCapacity() {
        Map<Pos, MachineData> net = new LinkedHashMap<>();
        MachineData gen = machine(MachineType.COAL_GENERATOR);
        gen.fuel = 1_000_000;
        MachineData battery = machine(MachineType.BATTERY);
        battery.charge = CFG.batteryCapacity - 3;
        net.put(new Pos(0, 0, 0), gen);
        net.put(new Pos(1, 0, 0), battery);
        EnergyNetwork.tick(net, CFG, p -> true);
        assertEquals(CFG.batteryCapacity, battery.charge);
    }

    @Test
    public void cyclesPerSecondAreCapped() {
        Map<Pos, MachineData> net = new LinkedHashMap<>();
        MachineData battery = machine(MachineType.BATTERY);
        battery.charge = 10_000;
        MachineData miner = machine(MachineType.MINER_2);
        miner.progress = 50;
        net.put(new Pos(0, 0, 0), battery);
        net.put(new Pos(1, 0, 0), miner);
        assertEquals(EnergyNetwork.MAX_CYCLES_PER_SECOND, EnergyNetwork.tick(net, CFG, p -> true).get(0).cycles());
    }

    @Test
    public void moduleNumbersTradeSpeedForFlux() {
        var plain = machine(MachineType.MINER_1);
        var ember = machine(MachineType.MINER_1);
        ember.module = "EMBER";
        var bloom = machine(MachineType.MINER_1);
        bloom.module = "BLOOM";
        assertEquals(CFG.consume(plain), CFG.consume(machine(MachineType.MINER_1)), 0);
        assertTrue("Ember is faster", CFG.rate(ember) > CFG.rate(plain));
        assertTrue("and hungrier per cycle", CFG.consume(ember) / CFG.rate(ember) > CFG.consume(plain) / CFG.rate(plain));
        assertTrue("Bloom is slower", CFG.rate(bloom) < CFG.rate(plain));
        assertTrue("and cheaper per cycle", CFG.consume(bloom) / CFG.rate(bloom) < CFG.consume(plain) / CFG.rate(plain));
        var memory = machine(MachineType.MINER_1);
        memory.module = "MEMORY";
        assertTrue("Memory is slower", CFG.rate(memory) < CFG.rate(plain));
        assertTrue("and dearer per cycle", CFG.consume(memory) / CFG.rate(memory) > CFG.consume(plain) / CFG.rate(plain));
        ember.module = "NOPE";
        assertEquals("an unknown module is ignored", CFG.rate(plain), CFG.rate(ember), 0);
    }

    @Test
    public void aSlottedModuleChangesWhatTheNetworkSpends() {
        var miner = machine(MachineType.MINER_1);
        miner.module = "EMBER";
        var battery = machine(MachineType.BATTERY);
        battery.charge = 100;
        Map<Pos, MachineData> net = new LinkedHashMap<>();
        net.put(new Pos(0, 0, 0), miner);
        net.put(new Pos(1, 0, 0), battery);
        EnergyNetwork.tick(net, CFG, p -> true);
        assertEquals("3.6 Flux drawn, the rest floored", 96, battery.charge);
        assertEquals(0.75, miner.progress, 1e-9);
    }

    @Test
    public void emberSmeltsAndOtherBlocksStay() {
        org.mockbukkit.mockbukkit.MockBukkit.mock(); // an ItemStack without a mock server breaks the mock for the whole JVM
        try {
            assertEquals(org.bukkit.Material.STONE, MachineModule.smelt(new org.bukkit.inventory.ItemStack(org.bukkit.Material.COBBLESTONE)).getType());
            assertEquals(org.bukkit.Material.GLASS, MachineModule.smelt(new org.bukkit.inventory.ItemStack(org.bukkit.Material.SAND)).getType());
            assertEquals(org.bukkit.Material.PACKED_ICE, MachineModule.smelt(new org.bukkit.inventory.ItemStack(org.bukkit.Material.PACKED_ICE)).getType());
        } finally {
            org.mockbukkit.mockbukkit.MockBukkit.unmock();
        }
        assertEquals(MachineModule.BLOOM, MachineModule.ofItem("bloom_module").orElseThrow());
        assertEquals(MachineModule.MEMORY, MachineModule.ofItem("memory_module").orElseThrow());
        assertEquals(0, MachineModule.memoryShards(0.0, new java.util.Random(1)));
        assertEquals(1, MachineModule.memoryShards(1.0, new java.util.Random(1)));
        assertEquals(MachineModule.CRYO, MachineModule.ofItem("cryo_module").orElseThrow());
        assertEquals(MachineModule.CRYO, MachineModule.of("cryo"));
    }

    // ---- Chrono Module (Citadel of Golden Sands)

    private static Map<Pos, MachineData> chronoNet(MachineData miner, MachineData battery) {
        Map<Pos, MachineData> net = new LinkedHashMap<>();
        net.put(new Pos(0, 0, 0), battery);
        net.put(new Pos(1, 0, 0), miner);
        return net;
    }

    private static MachineData chronoMiner() {
        MachineData miner = machine(MachineType.MINER_1);
        miner.module = "CHRONO";
        return miner;
    }

    private static MachineSettings tideSettings() {
        return new MachineSettings(win.vampyuyu.survival.data.Settings.empty("quarry"), new win.vampyuyu.survival.api.TimeTide(100, 100, 100));
    }

    @Test
    public void aChronoMinerFollowsTheTide() {
        MachineSettings cfg = tideSettings();
        long calm = 10_000L, surge = 150_000L, stall = 250_000L;
        assertEquals(1.0, cfg.tideFactor(calm), 0);
        assertEquals(1.5, cfg.tideFactor(surge), 0);
        assertEquals(0.6, cfg.tideFactor(stall), 0);
        MachineData miner = chronoMiner();
        assertEquals(0.5 * 1.5, cfg.rate(miner, cfg.tideFactor(surge)), 1e-9);
        assertEquals("an Ember module ignores the tide", 0.5 * 1.5, rateOf("EMBER", cfg, surge), 1e-9);
    }

    private static double rateOf(String module, MachineSettings cfg, long now) {
        MachineData m = machine(MachineType.MINER_1);
        m.module = module;
        return cfg.rate(m, cfg.tideFactor(now));
    }

    @Test
    public void aChronoMinerWithNothingToDoBanksCyclesAndPaysThemBack() {
        MachineSettings cfg = tideSettings();
        MachineData miner = chronoMiner();
        MachineData battery = machine(MachineType.BATTERY);
        battery.charge = 100_000;
        Map<Pos, MachineData> net = chronoNet(miner, battery);
        for (int i = 0; i < 20; i++) {
            EnergyNetwork.tick(net, cfg, p -> false, p -> false, EnergyNetwork.Modifiers.NONE, 10_000L); // chest full: cannot run
        }
        assertTrue("cycles banked: " + miner.buffer, miner.buffer > 4.9 && miner.buffer < 5.1); // 20 s x 0.5 x 0.5
        double before = miner.buffer;
        var fires = EnergyNetwork.tick(net, cfg, p -> true, p -> false, EnergyNetwork.Modifiers.NONE, 10_000L);
        assertEquals(before - 1, miner.buffer, 1e-9); // one extra cycle per second comes out
        assertEquals("0.5 cycle of its own + 1 from the buffer: one cycle fired", 1, fires.get(0).cycles());
    }

    @Test
    public void theBufferIsCappedAndAStallBanksWhatItCosts() {
        MachineSettings cfg = tideSettings();
        assertEquals(120.0, MachineModule.bank(119, 50, 1, cfg.chronoBufferCap), 0);
        assertEquals(0.0, MachineModule.bank(-4, -2, 1, 10), 0);
        assertEquals(0.5, MachineModule.release(0.5, 1), 0);
        assertEquals(1.0, MachineModule.release(40, 1), 0);
        MachineData miner = chronoMiner();
        MachineData battery = machine(MachineType.BATTERY);
        battery.charge = 100_000;
        Map<Pos, MachineData> net = chronoNet(miner, battery);
        EnergyNetwork.tick(net, cfg, p -> true, p -> false, EnergyNetwork.Modifiers.NONE, 250_000L); // a stall
        assertEquals("0.5 x 0.4 lost", 0.2, miner.buffer, 1e-9);
        assertEquals("only 0.6 x 0.5 done", 0.3, miner.progress, 1e-9);
    }

    @Test
    public void aMinerWithoutTheModuleNeverBanks() {
        MachineSettings cfg = tideSettings();
        MachineData miner = machine(MachineType.MINER_1);
        MachineData battery = machine(MachineType.BATTERY);
        Map<Pos, MachineData> net = chronoNet(miner, battery);
        EnergyNetwork.tick(net, cfg, p -> false, p -> false, EnergyNetwork.Modifiers.NONE, 10_000L);
        assertEquals(0.0, miner.buffer, 0);
    }
}
