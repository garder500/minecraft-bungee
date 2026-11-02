package win.vampyuyu.survival.realm;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.HashSet;
import java.util.Set;
import org.bukkit.Material;
import org.junit.Test;
import win.vampyuyu.survival.island.BlockPlan;

/** The layout of the Tempestuous Archipelago: the build and the rules read the same lists, so they must agree. */
public class StormPlanTest {
    private static final BlockPlan PLAN = StormPlan.build();

    @Test
    public void theBuildFitsItsCapAndTheKeyRange() {
        assertTrue("plan has " + PLAN.size() + " blocks", PLAN.size() > 5000 && PLAN.size() < StormPlan.CAP);
        for (StormPlan.Islet islet : StormPlan.ISLETS) {
            assertTrue(islet.id(), Math.abs(islet.x()) + islet.radius() < 2000 && Math.abs(islet.z()) + islet.radius() < 2000);
        }
        assertTrue("the whole Realm stays under the world roof", StormPlan.ORIGIN_Y + 20 + 25 < 320);
    }

    @Test
    public void sevenIslandsKeepTheirDistanceAndTheJourneyIsAChain() {
        assertEquals(7, StormPlan.ISLETS.size());
        for (int i = 0; i < StormPlan.ISLETS.size(); i++) {
            for (int j = i + 1; j < StormPlan.ISLETS.size(); j++) {
                StormPlan.Islet a = StormPlan.ISLETS.get(i);
                StormPlan.Islet b = StormPlan.ISLETS.get(j);
                double gap = Math.hypot(a.x() - b.x(), a.z() - b.z()) - a.radius() - b.radius();
                assertTrue(a.id() + "/" + b.id() + " gap " + gap, gap >= 8);
            }
        }
        Set<String> linked = new HashSet<>();
        StormPlan.PLATFORMS.forEach(p -> linked.add(p.from() + "-" + p.to()));
        StormPlan.PADS.forEach(p -> linked.add(p.from() + "-" + p.to()));
        for (int i = 0; i + 1 < StormPlan.ISLETS.size(); i++) {
            String a = StormPlan.ISLETS.get(i).id();
            String b = StormPlan.ISLETS.get(i + 1).id();
            assertTrue(a + " to " + b, linked.contains(a + "-" + b));
        }
        assertEquals(3, StormPlan.PLATFORMS.size());
        assertEquals("a pad and its way back", 6, StormPlan.PADS.size());
    }

    @Test
    public void aPlatformNeverTouchesAnIslandAnywhereOnItsTrack() {
        int h = StormPlan.PLATFORM_HALF;
        for (StormPlan.Platform platform : StormPlan.PLATFORMS) {
            int steps = platform.track().steps();
            assertTrue(platform.id(), steps >= 8 && steps <= 40);
            for (int i = 0; i <= steps; i++) {
                int[] c = platform.track().at(i);
                for (StormPlan.Islet islet : StormPlan.ISLETS) {
                    for (int dx = -h; dx <= h; dx++) {
                        for (int dz = -h; dz <= h; dz++) {
                            double d = Math.hypot(c[0] + dx - islet.x(), c[2] + dz - islet.z());
                            assertTrue(platform.id() + " step " + i + " touches " + islet.id(),
                                d > islet.radius() + 0.5 || c[1] - 1 > islet.y() + 8 || c[1] + 12 < islet.y() - 12);
                        }
                    }
                }
            }
            // and it ends within a jump of the islands it joins
            double fromGap = gapToRim(platform.track().at(0), StormPlan.at(platform.from()));
            double toGap = gapToRim(platform.track().at(steps), StormPlan.at(platform.to()));
            assertTrue(platform.id() + " start gap " + fromGap, fromGap <= 3.5);
            assertTrue(platform.id() + " end gap " + toGap, toGap <= 3.5);
            assertEquals(StormPlan.at(platform.from()).y(), platform.track().at(0)[1]);
            assertEquals(StormPlan.at(platform.to()).y(), platform.track().at(steps)[1]);
        }
    }

    private static double gapToRim(int[] centre, StormPlan.Islet islet) {
        double nearest = Double.MAX_VALUE;
        int h = StormPlan.PLATFORM_HALF;
        for (int dx = -h; dx <= h; dx++) {
            for (int dz = -h; dz <= h; dz++) {
                nearest = Math.min(nearest, Math.hypot(centre[0] + dx - islet.x(), centre[2] + dz - islet.z()) - islet.radius());
            }
        }
        return nearest;
    }

    @Test
    public void everyPadHasAFlightThatLandsOnTheIslandItNames() {
        for (StormPlan.Pad pad : StormPlan.PADS) {
            PadMath.Launch launch = pad.launch();
            assertNotNull(pad.id(), launch);
            double[] end = PadMath.landing(launch);
            double x = pad.x() + 0.5 + end[0];
            double z = pad.z() + 0.5 + end[2];
            StormPlan.Islet to = StormPlan.at(pad.to());
            assertTrue(pad.id() + " lands " + Math.hypot(x - to.x(), z - to.z()) + " from the centre of " + to.id(),
                Math.hypot(x - to.x(), z - to.z()) <= to.radius() - 2);
            assertEquals(pad.id(), to.y() + 1, pad.y() + 1 + end[1], 0.05);
            assertTrue(pad.id() + " apex " + PadMath.apexOf(launch), PadMath.apexOf(launch) <= 14);
            assertEquals("the pad stands on its own island", Material.LIGHT_BLUE_GLAZED_TERRACOTTA, PLAN.get(pad.x(), pad.y(), pad.z()));
            assertTrue(StormPlan.at(pad.from()).contains(pad.x() + 0.5, pad.z() + 0.5, 0));
        }
    }

    @Test
    public void theThunderQuartzNodesAreInThePlanOnIslandSurfaces() {
        assertEquals(14, StormPlan.NODES.size());
        for (StormPlan.Spot node : StormPlan.NODES) {
            assertEquals(node.id(), Material.CHISELED_QUARTZ_BLOCK, PLAN.get(node.x(), node.y(), node.z()));
            StormPlan.Islet islet = StormPlan.isletUnder(node.x() + 0.5, node.y() + 1, node.z() + 0.5);
            assertNotNull(node.id(), islet);
            assertEquals(islet.y(), node.y());
        }
    }

    @Test
    public void noNodeOrRescuePointSitsOnAPad() {
        for (StormPlan.Pad pad : StormPlan.PADS) {
            for (StormPlan.Spot node : StormPlan.NODES) {
                assertTrue(node.id() + " on " + pad.id(), Math.abs(node.x() - pad.x()) > 1 || Math.abs(node.z() - pad.z()) > 1
                    || node.y() != pad.y());
            }
            for (StormPlan.Islet islet : StormPlan.ISLETS) {
                int[] r = islet.rescue();
                assertTrue(islet.id() + " rescue on " + pad.id(), Math.abs(r[0] - pad.x()) > 1 || Math.abs(r[2] - pad.z()) > 1
                    || r[1] - 1 != pad.y());
            }
        }
    }

    @Test
    public void everyIslandHasASafeRescuePointAndTheSpawnIsOnTheLanding() {
        for (StormPlan.Islet islet : StormPlan.ISLETS) {
            int[] r = islet.rescue();
            assertTrue(islet.id() + " rescue is free", PLAN.get(r[0], r[1], r[2]) == null || PLAN.get(r[0], r[1], r[2]) == Material.AIR);
            assertTrue(islet.id() + " rescue is free above", PLAN.get(r[0], r[1] + 1, r[2]) == null || PLAN.get(r[0], r[1] + 1, r[2]) == Material.AIR);
            assertTrue(islet.id() + " rescue stands on something", PLAN.solidAt(r[0], r[1] - 1, r[2]));
        }
        assertTrue(PLAN.solidAt(StormPlan.SPAWN[0], StormPlan.SPAWN[1] - 1, StormPlan.SPAWN[2]));
        assertNotNull(PLAN.anchor("spawn"));
    }

    @Test
    public void theSealOfTheSarcophagusIsALodestoneBeforeACryingObsidianWall() {
        int[] seal = PLAN.anchor("seal");
        assertNotNull(seal);
        assertEquals(StormPlan.SEAL[0], seal[0]);
        assertEquals(StormPlan.SEAL[1], seal[1]);
        assertEquals(StormPlan.SEAL[2] + 1, seal[2]);
        assertEquals(Material.LODESTONE, PLAN.get(seal[0], seal[1], seal[2]));
        assertEquals(Material.CRYING_OBSIDIAN, PLAN.get(seal[0] - 3, seal[1], seal[2] - 1));
        assertTrue("the seal is on the Eyrie", StormPlan.at("eyrie").contains(seal[0] + 0.5, seal[2] + 0.5, 0));
    }

    @Test
    public void thePlanNeverPutsThePlatformMaterialsOnATrack() {
        // the runtime sweeps leftovers of a crash by those two materials along each track: the plan must not use them there
        for (StormPlan.Platform platform : StormPlan.PLATFORMS) {
            for (int i = 0; i <= platform.track().steps(); i++) {
                int[] c = platform.track().at(i);
                for (int dx = -2; dx <= 2; dx++) {
                    for (int dz = -2; dz <= 2; dz++) {
                        for (int dy = -1; dy <= 0; dy++) {
                            Material m = PLAN.get(c[0] + dx, c[1] + dy, c[2] + dz);
                            assertTrue(platform.id() + " step " + i + " " + m, m == null);
                        }
                    }
                }
            }
        }
    }
}
