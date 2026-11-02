package win.vampyuyu.survival.depths;

import static org.junit.Assert.*;

import java.util.UUID;
import org.bukkit.Material;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import win.vampyuyu.survival.api.Realm;
import win.vampyuyu.survival.boss.AbilityType;
import win.vampyuyu.survival.boss.BossConfig;
import win.vampyuyu.survival.boss.BossDefinition;
import win.vampyuyu.survival.boss.BossItems;
import win.vampyuyu.survival.boss.BossTestData;
import win.vampyuyu.survival.boss.Guardians;
import win.vampyuyu.survival.island.BlockPlan;

/** The Abyssal Depths: layout, pressure and breath rules, quest progress, and the Chained Leviathan's data. */
public class DepthsTest {
    @Before
    public void setUp() {
        MockBukkit.mock();
    }

    @After
    public void tearDown() {
        MockBukkit.unmock();
    }

    // ---------------------------------------------------------------- pressure

    @Test
    public void tiersRiseWithDepthAndAPumpTakesOneOff() {
        assertEquals(0, DepthsMath.tier(0, false));
        assertEquals(0, DepthsMath.tier(DepthsMath.SHALLOW, false));
        assertEquals(1, DepthsMath.tier(DepthsMath.SHALLOW + 1, false));
        assertEquals(1, DepthsMath.tier(DepthsMath.MID, false));
        assertEquals(2, DepthsMath.tier(DepthsMath.MID + 1, false));
        assertEquals(0, DepthsMath.tier(DepthsMath.SHALLOW + 1, true));
        assertEquals(1, DepthsMath.tier(DepthsMath.MID + 5, true));
        assertEquals(0, DepthsMath.tier(0, true));
        assertTrue(DepthsMath.required(1) < DepthsMath.required(2));
    }

    @Test
    public void damageGrowsWithTheMissingResistanceAndNeverTakesTheLastHearts() {
        assertEquals(0.0, DepthsMath.damage(0, 0), 0.0);
        assertEquals(0.0, DepthsMath.damage(2, DepthsMath.required(2)), 0.0);
        assertFalse(DepthsMath.hurts(1, DepthsMath.required(1)));
        assertTrue(DepthsMath.hurts(1, DepthsMath.required(1) - 1));
        assertTrue(DepthsMath.damage(2, 0) > DepthsMath.damage(1, 0));
        assertTrue(DepthsMath.damage(2, 0) <= 6.0);
        assertEquals(10.0, DepthsMath.healthAfter(20.0, 10.0), 0.0);
        assertEquals(DepthsMath.FLOOR, DepthsMath.healthAfter(5.0, 100.0), 0.0);
        assertEquals(1.0, DepthsMath.healthAfter(1.0, 4.0), 0.0);
    }

    @Test
    public void respirationAndTheTurtleShellCountAndAFullKitBeatsTierOne() {
        assertEquals(30, DepthsMath.respirationPoints(3));
        assertEquals(0, DepthsMath.respirationPoints(-1));
        assertEquals(10, DepthsMath.helmetPoints("TURTLE_HELMET"));
        assertEquals(0, DepthsMath.helmetPoints("IRON_HELMET"));
        assertTrue(DepthsMath.respirationPoints(3) + DepthsMath.helmetPoints("TURTLE_HELMET") >= DepthsMath.required(1));
        // respiration spares the breath, the depth costs it
        assertEquals(0, DepthsMath.airDrain(0, 0));
        assertTrue(DepthsMath.airDrain(2, 0) > DepthsMath.airDrain(1, 0));
        assertEquals(0, DepthsMath.airDrain(1, 3));
    }

    @Test
    public void driftLiftsForTheFirstSecondsOfEveryPeriod() {
        assertTrue(DepthsMath.drifting(0));
        assertTrue(DepthsMath.drifting(20L * DepthsMath.DRIFT_LIFT_SECONDS - 1));
        assertFalse(DepthsMath.drifting(20L * DepthsMath.DRIFT_LIFT_SECONDS));
        assertTrue(DepthsMath.drifting(20L * DepthsMath.DRIFT_PERIOD_SECONDS));
    }

    @Test
    public void extraCatchesAreRareAndKnownMaterials() {
        assertNull(DepthsMath.extraCatch(0.99));
        for (double roll = 0; roll < 0.3; roll += 0.01) {
            String name = DepthsMath.extraCatch(roll);
            assertNotNull("roll " + roll, name);
            assertNotNull(name, Material.matchMaterial(name));
        }
    }

    // ---------------------------------------------------------------- layout

    @Test
    public void thePlanFitsItsCapAndHasAWalkableLandingWithAnAirSpawn() {
        BlockPlan plan = DepthsPlan.build();
        assertTrue("size " + plan.size(), plan.size() > 5_000 && plan.size() < DepthsLayout.CAP);
        int[] spawn = DepthsLayout.SPAWN;
        assertNotNull(plan.get(spawn[0], spawn[1] - 1, spawn[2]));
        assertNull(plan.get(spawn[0], spawn[1], spawn[2]));
        assertNull(plan.get(spawn[0], spawn[1] + 1, spawn[2]));
        assertNotNull(plan.anchor("spawn"));
    }

    @Test
    public void everyDomeIsJoinedToTheLandingByAFloorAndInsideTheBorder() {
        BlockPlan plan = DepthsPlan.build();
        for (DepthsLayout.Dome dome : DepthsLayout.DOMES) {
            assertTrue(dome.id(), Math.hypot(dome.x(), dome.z()) + dome.radius() + DepthsLayout.RIM < 450);
            // the rim, on the side of the landing, is a floor
            double len = Math.hypot(dome.x(), dome.z());
            int rx = (int) Math.round(dome.x() - dome.x() / len * (dome.radius() + 1));
            int rz = (int) Math.round(dome.z() - dome.z() / len * (dome.radius() + 1));
            assertTrue(dome.id() + " rim", plan.get(rx, 0, rz) != null || plan.get(rx + 1, 0, rz) != null
                || plan.get(rx, 0, rz + 1) != null || plan.get(rx - 1, 0, rz) != null || plan.get(rx, 0, rz - 1) != null);
        }
        // the road: every landmark and pump stands on or next to a floor block
        for (DepthsLayout.Pump pump : DepthsLayout.PUMPS) {
            assertEquals(pump.id(), Material.CONDUIT, plan.get(pump.x(), pump.y(), pump.z()));
            assertNotNull(pump.id(), plan.get(pump.x(), pump.y() - 1, pump.z()));
        }
    }

    @Test
    public void waterIsContainedSoItNeverFlowsIntoTheVoid() {
        BlockPlan plan = DepthsPlan.build();
        int water = 0;
        for (var entry : plan.blocks().entrySet()) {
            if (entry.getValue().material() != Material.WATER) {
                continue;
            }
            water++;
            int x = BlockPlan.keyX(entry.getKey());
            int y = BlockPlan.keyY(entry.getKey());
            int z = BlockPlan.keyZ(entry.getKey());
            assertNotNull("under water " + x + "," + y + "," + z, plan.get(x, y - 1, z));
            for (int[] d : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                assertNotNull("beside water " + x + "," + y + "," + z, plan.get(x + d[0], y, z + d[1]));
            }
        }
        assertTrue("water " + water, water > 10_000);
    }

    @Test
    public void waterAtFindsTheDomesAndDepthCountsFromTheSurface() {
        DepthsLayout.Dome chain = DepthsLayout.dome("chain");
        assertEquals(chain, DepthsLayout.waterAt(chain.x(), -3, chain.z()));
        assertNull(DepthsLayout.waterAt(chain.x(), 2, chain.z()));
        assertNull(DepthsLayout.waterAt(0, -3, 0));
        assertNull(DepthsLayout.waterAt(chain.x() + chain.radius() + 3, -3, chain.z()));
        assertEquals(0, DepthsLayout.depth(0));
        assertEquals(0, DepthsLayout.depth(-1));
        assertEquals(9, DepthsLayout.depth(-10));
        // the deepest dome reaches the deepest tier, the shallowest never does
        assertTrue(DepthsLayout.dome("chain").radius() - 1 > DepthsMath.MID);
        assertTrue(DepthsLayout.dome("shoals").radius() - 1 <= DepthsMath.MID);
    }

    @Test
    public void gravityZonesCoverTheirDomesAndNothingElse() {
        DepthsLayout.Dome trench = DepthsLayout.dome("trench");
        assertEquals(DepthsLayout.Gravity.Kind.DRIFT, DepthsLayout.gravityAt(trench.x(), 5, trench.z()).kind());
        DepthsLayout.Dome shoals = DepthsLayout.dome("shoals");
        assertEquals(DepthsLayout.Gravity.Kind.LOW, DepthsLayout.gravityAt(shoals.x(), 5, shoals.z()).kind());
        assertNull(DepthsLayout.gravityAt(0, 5, 0));
        assertNull(DepthsLayout.gravityAt(shoals.x(), 60, shoals.z()));
    }

    @Test
    public void protectedSpotsAreTheLandingTheRimsAndTheShells() {
        assertTrue(DepthsLayout.protectedSpot(3, 0, 3));
        assertFalse(DepthsLayout.protectedSpot(40, 0, 10));
        DepthsLayout.Dome kelp = DepthsLayout.dome("kelp");
        assertTrue(DepthsLayout.protectedSpot(kelp.x() + kelp.radius() + 1, 0, kelp.z()));
        assertTrue(DepthsLayout.protectedSpot(kelp.x(), -kelp.radius(), kelp.z()));
        assertFalse("the water inside", DepthsLayout.protectedSpot(kelp.x(), -4, kelp.z()));
    }

    // ---------------------------------------------------------------- quests

    @Test
    public void discoveriesPayOnceAndTheLastOneAddsTheBonus() {
        DepthsProgress progress = new DepthsProgress();
        UUID p = UUID.randomUUID();
        int total = 0;
        for (DepthsLayout.Landmark mark : DepthsLayout.LANDMARKS) {
            assertFalse(progress.exploreDone(p));
            int coins = progress.discover(p, mark.id());
            assertTrue(coins >= DepthsProgress.DISCOVER_COINS);
            total += coins;
            assertEquals(-1, progress.discover(p, mark.id()));
        }
        assertTrue(progress.exploreDone(p));
        assertEquals(DepthsProgress.DISCOVER_COINS * DepthsLayout.LANDMARKS.size() + DepthsProgress.EXPLORE_BONUS, total);
        assertEquals(DepthsLayout.LANDMARKS.size(), progress.fragments(p).size());
    }

    @Test
    public void fishingPaysAtTheGoalOnlyOnce() {
        DepthsProgress progress = new DepthsProgress();
        UUID p = UUID.randomUUID();
        int paid = 0;
        for (int i = 0; i < DepthsProgress.FISH_GOAL + 5; i++) {
            paid += progress.fish(p);
        }
        assertEquals(DepthsProgress.FISH_COINS, paid);
        assertEquals(DepthsProgress.FISH_GOAL + 5, progress.caught(p));
    }

    @Test
    public void startingEveryPumpGivesTheAttunementAndEachPumpLightensItsOwnDome() {
        DepthsProgress progress = new DepthsProgress();
        UUID p = UUID.randomUUID();
        int total = 0;
        for (DepthsLayout.Pump pump : DepthsLayout.PUMPS) {
            assertFalse(progress.attuned(p));
            assertFalse(progress.pumpedDome(p, pump.dome()));
            int coins = progress.startPump(p, pump.id());
            assertTrue(coins >= DepthsProgress.PUMP_COINS);
            total += coins;
            assertEquals(-1, progress.startPump(p, pump.id()));
            assertTrue(progress.pumpedDome(p, pump.dome()));
        }
        assertTrue(progress.attuned(p));
        assertEquals(DepthsProgress.PUMP_COINS * DepthsLayout.PUMPS.size() + DepthsProgress.FINISH_COINS, total);
        assertFalse(progress.pumpedDome(p, "chain"));
    }

    @Test
    public void progressSurvivesASaveAndAResetForgetsIt() {
        DepthsProgress progress = new DepthsProgress();
        UUID p = UUID.randomUUID();
        progress.discover(p, "shoals");
        progress.fish(p);
        progress.startPump(p, DepthsLayout.PUMPS.get(0).id());
        DepthsProgress back = DepthsProgress.fromDoc(progress.toDoc());
        assertTrue(back.discovered(p, "shoals"));
        assertEquals(1, back.caught(p));
        assertTrue(back.pumping(p, DepthsLayout.PUMPS.get(0).id()));
        back.reset(p);
        assertFalse(back.discovered(p, "shoals"));
        assertEquals(0, DepthsProgress.fromDoc(null).caught(p));
    }

    // ---------------------------------------------------------------- the Chained Leviathan

    @Test
    public void theLeviathanIsAGuardianOfTheDepthsWithItsOwnResourceAndComponent() {
        BossConfig config = BossTestData.shipped();
        Guardians.Guardian g = config.guardians().get("abyssal_leviathan");
        assertNotNull(g);
        assertEquals(Realm.DEPTHS, Realm.byId(g.realm()));
        assertEquals("pressure_pearl", g.resource());
        assertEquals(BossItems.LEVIATHAN_CORE, g.component());
        assertTrue(BossItems.tableIds().contains(g.component()));
        assertTrue(config.bosses().get("abyssal_leviathan").name().contains("Chained Leviathan"));
        assertFalse(g.line(Guardians.INTRO).isEmpty());
    }

    @Test
    public void theLeviathanChainsTheArenaFromTheSecondPhaseOn() {
        BossDefinition def = BossTestData.shipped().bosses().get("abyssal_leviathan");
        int phases = 0;
        for (int i = 0; i < def.phases().size(); i++) {
            boolean has = def.phases().get(i).abilities().stream().anyMatch(a -> a.type() == AbilityType.CHAIN_ANCHORS);
            assertEquals("phase " + i, i >= 1, has);
            if (has) {
                phases++;
                var spec = def.phases().get(i).abilities().stream().filter(a -> a.type() == AbilityType.CHAIN_ANCHORS).findFirst().orElseThrow();
                assertTrue(spec.integer("count", 0) >= 2 && spec.integer("count", 0) <= 6);
                assertTrue(spec.num("strength", 0) > 0 && spec.num("strength", 0) <= 0.3);
                assertTrue("anchors must be breakable before they overload", spec.integer("seconds", 0) >= 15);
            }
        }
        assertEquals(2, phases);
        assertEquals(AbilityType.CHAIN_ANCHORS, AbilityType.byId("chain-anchors").orElseThrow());
    }
}
