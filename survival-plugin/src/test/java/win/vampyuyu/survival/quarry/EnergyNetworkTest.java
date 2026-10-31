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
    private static final MachineSettings CFG = new MachineSettings(new YamlConfiguration());

    private static MachineData machine(MachineType type) {
        MachineData m = new MachineData();
        m.type = type.name();
        return m;
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
        // miner I: 8 Flux and 0.5 cycles per second; the generator makes 10
        var first = EnergyNetwork.tick(net, CFG, p -> true);
        assertTrue("half a cycle is not a cycle yet", first.isEmpty());
        assertEquals(4, gen.fuel);
        assertEquals("10 made, 8 used, 2 stored", 2, battery.charge);
        var second = EnergyNetwork.tick(net, CFG, p -> true);
        assertEquals(1, second.size());
        assertEquals(1, second.get(0).cycles());
        assertEquals(new Pos(2, 0, 0), second.get(0).pos());
        assertEquals(4, battery.charge);
    }

    @Test
    public void withoutEnergyNothingMinesAndBatteriesDrainUntilEmpty() {
        Map<Pos, MachineData> net = new LinkedHashMap<>();
        MachineData battery = machine(MachineType.BATTERY);
        battery.charge = 20;
        MachineData miner = machine(MachineType.MINER_2); // 20 Flux per second
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
}
