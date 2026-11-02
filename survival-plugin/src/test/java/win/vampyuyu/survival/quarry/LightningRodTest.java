package win.vampyuyu.survival.quarry;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import win.vampyuyu.survival.quarry.EnergyNetwork.Pos;

/** The Lightning Rod (Tempestuous Archipelago): strong, unstable Flux in a storm or from Thunder Quartz; batteries smooth it. */
public class LightningRodTest {
    private static final MachineSettings CFG = new MachineSettings(win.vampyuyu.survival.data.Settings.empty("quarry"));

    private static MachineData machine(MachineType type) {
        MachineData m = new MachineData();
        m.type = type.name();
        return m;
    }

    private static EnergyNetwork.Sky sky(boolean stormy, long[] second) {
        return new EnergyNetwork.Sky() {
            @Override
            public boolean stormy(Pos pos) {
                return stormy;
            }

            @Override
            public long second() {
                return second[0];
            }
        };
    }

    private static Map<Pos, MachineData> network(MachineData... machines) {
        Map<Pos, MachineData> net = new LinkedHashMap<>();
        for (int i = 0; i < machines.length; i++) {
            net.put(new Pos(i, 0, 0), machines[i]);
        }
        return net;
    }

    @Test
    public void aRodWithNoStormAndNoQuartzMakesNothing() {
        MachineData rod = machine(MachineType.LIGHTNING_ROD);
        assertFalse(EnergyNetwork.hasFlux(network(rod), p -> false, sky(false, new long[] {0})));
        MachineData battery = machine(MachineType.BATTERY);
        long[] second = {0};
        for (int i = 0; i < 100; i++) {
            second[0] = i;
            EnergyNetwork.tick(network(rod, battery), CFG, p -> false, p -> false, EnergyNetwork.Modifiers.NONE, sky(false, second));
        }
        assertEquals(0, battery.charge);
    }

    @Test
    public void aStormMakesTheNetworkLiveAndTheOutputSwings() {
        MachineData rod = machine(MachineType.LIGHTNING_ROD);
        assertTrue(EnergyNetwork.hasFlux(network(rod), p -> false, sky(true, new long[] {0})));
        long[] second = {0};
        double lowest = Double.MAX_VALUE;
        double highest = 0;
        double total = 0;
        int seconds = 4000;
        for (int i = 0; i < seconds; i++) {
            second[0] = i;
            MachineData battery = machine(MachineType.BATTERY);
            EnergyNetwork.tick(network(rod, battery), CFG, p -> false, p -> false, EnergyNetwork.Modifiers.NONE, sky(true, second));
            lowest = Math.min(lowest, battery.charge);
            highest = Math.max(highest, battery.charge);
            total += battery.charge;
        }
        assertEquals("some seconds give nothing", 0, lowest, 0);
        assertEquals("the best seconds give four times the base", CFG.rodOutput * 4, highest, 1);
        assertEquals("on average well above a coal generator", CFG.rodOutput * 1.275, total / seconds, CFG.rodOutput * 0.06);
        assertTrue(total / seconds > 3 * CFG.generatorOutput);
    }

    @Test
    public void quartzBurnsOneSecondAtATimeWithNoStorm() {
        MachineData rod = machine(MachineType.LIGHTNING_ROD);
        rod.fuel = 5;
        assertTrue(EnergyNetwork.hasFlux(network(rod), p -> false, sky(false, new long[] {0})));
        long[] second = {0};
        for (int i = 0; i < 5; i++) {
            second[0] = i;
            EnergyNetwork.tick(network(rod), CFG, p -> false, p -> false, EnergyNetwork.Modifiers.NONE, sky(false, second));
        }
        assertEquals(0, rod.fuel);
        assertFalse(EnergyNetwork.hasFlux(network(rod), p -> false, sky(false, new long[] {0})));
    }

    @Test
    public void aBatteryKeepsMinersWorkingThroughTheDeadSeconds() {
        // the same rod feeds five Miner IIs (30 Flux/s, below its 38 average) with and without a battery, for ten minutes
        int withBattery = minerCycles(true);
        int without = minerCycles(false);
        assertTrue("with " + withBattery + " without " + without, withBattery > without);
        assertTrue("the battery smooths the dead seconds: " + withBattery, withBattery > 2900);
        assertTrue("alone the rod starves the miners often: " + without, without < 2600);
    }

    private static int minerCycles(boolean battery) {
        MachineData rod = machine(MachineType.LIGHTNING_ROD);
        MachineData[] miners = new MachineData[5];
        Map<Pos, MachineData> net = network(rod);
        for (int i = 0; i < miners.length; i++) {
            miners[i] = machine(MachineType.MINER_2);
            net.put(new Pos(net.size(), 0, 0), miners[i]);
        }
        if (battery) {
            net.put(new Pos(net.size(), 0, 0), machine(MachineType.BATTERY));
        }
        long[] second = {0};
        int cycles = 0;
        for (int i = 0; i < 600; i++) {
            second[0] = i;
            cycles += EnergyNetwork.tick(net, CFG, p -> true, p -> false, EnergyNetwork.Modifiers.NONE, sky(true, second)).size();
        }
        return cycles;
    }

    @Test
    public void theRodIsAMachineOfItsOwn() {
        assertEquals(MachineType.LIGHTNING_ROD, MachineType.ofItem("lightning_rod").orElseThrow());
        assertFalse(MachineType.LIGHTNING_ROD.passive());
        assertFalse(MachineType.LIGHTNING_ROD.miner());
        assertEquals(List.of(), EnergyNetwork.tick(network(machine(MachineType.LIGHTNING_ROD)), CFG, p -> false));
    }
}
