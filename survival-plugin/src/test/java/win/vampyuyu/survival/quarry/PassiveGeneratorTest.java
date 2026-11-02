package win.vampyuyu.survival.quarry;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.junit.Test;
import win.vampyuyu.survival.quarry.EnergyNetwork.Pos;
import win.vampyuyu.survival.quarry.PassiveGenerator.Cell;

/** The passive generators (GDD 7.3) and the compression Blocks that activate and overload them (GDD 7.4). */
public class PassiveGeneratorTest {
    private static final MachineSettings CFG = new MachineSettings(win.vampyuyu.survival.data.Settings.empty("quarry"));

    private static MachineData machine(MachineType type) {
        MachineData m = new MachineData();
        m.type = type.name();
        return m;
    }

    /** A sky that says whether the cells of every generator are in place. */
    private static EnergyNetwork.Sky fed(boolean fed) {
        return new EnergyNetwork.Sky() {
            @Override
            public boolean stormy(Pos pos) {
                return false;
            }

            @Override
            public long second() {
                return 0;
            }

            @Override
            public boolean fed(Pos pos, PassiveGenerator generator) {
                return fed;
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

    private static long storedFrom(MachineType generator, int blocks, boolean fed) {
        MachineData gen = machine(generator);
        gen.blocks = blocks;
        MachineData battery = machine(MachineType.BATTERY);
        EnergyNetwork.tick(network(gen, battery), CFG, p -> true, p -> false, EnergyNetwork.Modifiers.NONE, fed(fed));
        return battery.charge;
    }

    @Test
    public void theFourGeneratorsAreMachinesWithTheGddNumbers() {
        assertEquals(MachineType.SPRING_WHEEL, MachineType.ofItem("spring_wheel").orElseThrow());
        assertEquals(MachineType.HEAT_TAP, MachineType.ofItem("heat_tap").orElseThrow());
        assertEquals(MachineType.VOID_REACTOR, MachineType.ofItem("void_reactor").orElseThrow());
        assertEquals(MachineType.ASTRAL_LATTICE, MachineType.ofItem("astral_lattice").orElseThrow());
        assertEquals(10, CFG.passiveOutput(PassiveGenerator.SPRING_WHEEL, 1), 0);
        assertEquals(30, CFG.passiveOutput(PassiveGenerator.HEAT_TAP, 1), 0);
        assertEquals(80, CFG.passiveOutput(PassiveGenerator.VOID_REACTOR, 1), 0);
        assertEquals(200, CFG.passiveOutput(PassiveGenerator.ASTRAL_LATTICE, 1), 0);
        assertEquals(5, CFG.generatorOutput, 0);
        assertEquals(2, CFG.solarOutput, 0);
        for (MachineType type : new MachineType[] {MachineType.SPRING_WHEEL, MachineType.HEAT_TAP, MachineType.VOID_REACTOR,
                MachineType.ASTRAL_LATTICE}) {
            assertFalse("a generator joins the Flux networks", type.passive());
            assertFalse(type.miner());
            assertEquals(type.itemId, type.generator().itemId);
        }
        assertEquals(null, MachineType.COAL_GENERATOR.generator());
    }

    @Test
    public void eachGeneratorNeedsTheBlockOfItsTier() {
        assertEquals("bronzite_block", PassiveGenerator.SPRING_WHEEL.blockItem);
        assertEquals("sunstone_block", PassiveGenerator.HEAT_TAP.blockItem);
        assertEquals("voidglass_block", PassiveGenerator.VOID_REACTOR.blockItem);
        assertEquals("starcore_block", PassiveGenerator.ASTRAL_LATTICE.blockItem);
        assertEquals("Starcore Block", PassiveGenerator.ASTRAL_LATTICE.blockTitle());
        assertTrue(PassiveGenerator.isBlock("frostium_block"));
        assertFalse(PassiveGenerator.isBlock("frostium_bar"));
        assertFalse(PassiveGenerator.isBlock("bronzite"));
    }

    @Test
    public void theFirstBlockActivatesAndThreeMoreOverloadUpToX175() {
        assertEquals("no Block, no Flux", 0, PassiveGenerator.overload(0, 3, 0.25), 0);
        assertEquals(1.0, PassiveGenerator.overload(1, 3, 0.25), 1e-9);
        assertEquals(1.25, PassiveGenerator.overload(2, 3, 0.25), 1e-9);
        assertEquals(1.5, PassiveGenerator.overload(3, 3, 0.25), 1e-9);
        assertEquals(1.75, PassiveGenerator.overload(4, 3, 0.25), 1e-9);
        assertEquals("more Blocks than the cap add nothing", 1.75, PassiveGenerator.overload(9, 3, 0.25), 1e-9);
        assertEquals(1.75, CFG.passiveOutput(PassiveGenerator.SPRING_WHEEL, 4) / CFG.passiveOutput(PassiveGenerator.SPRING_WHEEL, 1), 1e-9);
        assertEquals(350, CFG.passiveOutput(PassiveGenerator.ASTRAL_LATTICE, 4), 1e-9);
    }

    @Test
    public void aGeneratorWithoutABlockMakesNothingAndOneWithCellsFeedsTheNetwork() {
        assertEquals("no Block inside", 0, storedFrom(MachineType.HEAT_TAP, 0, true));
        assertEquals("cells missing", 0, storedFrom(MachineType.HEAT_TAP, 1, false));
        assertEquals(30, storedFrom(MachineType.HEAT_TAP, 1, true));
        assertEquals(37, storedFrom(MachineType.HEAT_TAP, 2, true)); // 30 x 1.25 = 37.5, the battery stores whole Flux
        assertEquals(52, storedFrom(MachineType.HEAT_TAP, 4, true)); // 30 x 1.75 = 52.5
        assertEquals(200, storedFrom(MachineType.ASTRAL_LATTICE, 1, true));
    }

    @Test
    public void aGeneratorLightsTheCablesOnlyWhenItRuns() {
        MachineData gen = machine(MachineType.SPRING_WHEEL);
        Map<Pos, MachineData> net = network(machine(MachineType.CABLE), gen);
        gen.blocks = 1;
        assertFalse(EnergyNetwork.hasFlux(net, p -> false, fed(false)));
        assertTrue(EnergyNetwork.hasFlux(net, p -> false, fed(true)));
        gen.blocks = 0;
        assertFalse("not activated", EnergyNetwork.hasFlux(net, p -> false, fed(true)));
    }

    @Test
    public void eachGeneratorChecksItsOwnCells() {
        Set<Cell> none = EnumSet.noneOf(Cell.class);
        assertTrue(PassiveGenerator.SPRING_WHEEL.met(EnumSet.of(Cell.SPRING), false));
        assertFalse(PassiveGenerator.SPRING_WHEEL.met(EnumSet.of(Cell.MAGMA), true));
        assertTrue(PassiveGenerator.HEAT_TAP.met(EnumSet.of(Cell.MAGMA), false));
        assertFalse(PassiveGenerator.HEAT_TAP.met(none, true));
        assertTrue(PassiveGenerator.VOID_REACTOR.met(EnumSet.of(Cell.VOID), false));
        assertFalse(PassiveGenerator.VOID_REACTOR.met(EnumSet.of(Cell.GEODE), true));
        // the Lattice wants a Void cell and a geode touching it, and the open sky
        assertTrue(PassiveGenerator.ASTRAL_LATTICE.met(EnumSet.of(Cell.VOID, Cell.GEODE), true));
        assertFalse("under a roof", PassiveGenerator.ASTRAL_LATTICE.met(EnumSet.of(Cell.VOID, Cell.GEODE), false));
        assertFalse("no geode", PassiveGenerator.ASTRAL_LATTICE.met(EnumSet.of(Cell.VOID), true));
        assertEquals("a Void Cell (crying obsidian) touching it, a block of amethyst (the geode) touching it, the open sky above it",
            PassiveGenerator.ASTRAL_LATTICE.missing(none, false).replace("a block of amethyst (the geode)", "a block of amethyst (the geode)"));
        assertEquals("", PassiveGenerator.HEAT_TAP.missing(EnumSet.of(Cell.MAGMA), false));
    }

    @Test
    public void theNumberOfMinersIsLimitedByFluxNotByACap() {
        // Spring Wheel with one Block: 10 Flux/s, a Miner II draws 6: one runs. A second wheel makes it 20: three run.
        assertEquals(1, runningMiners(1, 5));
        assertEquals(3, runningMiners(2, 5));
        MachineData lattice = machine(MachineType.ASTRAL_LATTICE);
        lattice.blocks = 4; // 350 Flux/s feed 58 Miner IIs
        Map<Pos, MachineData> net = network(lattice);
        for (int i = 0; i < 100; i++) {
            net.put(new Pos(i + 1, 0, 0), machine(MachineType.MINER_2));
        }
        for (MachineData m : net.values()) {
            if (m.kind().miner()) {
                m.progress = 1; // ready to fire this second
            }
        }
        assertEquals(58, EnergyNetwork.tick(net, CFG, p -> true, p -> false, EnergyNetwork.Modifiers.NONE, fed(true)).size());
    }

    private static int runningMiners(int wheels, int miners) {
        Map<Pos, MachineData> net = new LinkedHashMap<>();
        int x = 0;
        for (int i = 0; i < wheels; i++) {
            MachineData wheel = machine(MachineType.SPRING_WHEEL);
            wheel.blocks = 1;
            net.put(new Pos(x++, 0, 0), wheel);
        }
        for (int i = 0; i < miners; i++) {
            MachineData miner = machine(MachineType.MINER_2);
            miner.progress = 1;
            net.put(new Pos(x++, 0, 0), miner);
        }
        return EnergyNetwork.tick(net, CFG, p -> true, p -> false, EnergyNetwork.Modifiers.NONE, fed(true)).size();
    }
}
