package win.vampyuyu.survival.island;

import static org.junit.Assert.*;

import java.util.UUID;
import org.bukkit.Material;
import org.junit.Test;
import win.vampyuyu.survival.core.SkyblockWorlds;

/** The Whispering Grove: layout (pure data) and affinity tiers. */
public class GroveTest {
    @Test
    public void planStaysUnderTheBlockCapAndHasASolidLanding() {
        BlockPlan plan = GrovePlan.build(45000);
        assertTrue("plan is not empty", plan.size() > 3000);
        assertTrue(plan.size() < 45000);
        int[] s = GrovePlan.spawn();
        assertTrue("the landing platform is solid", plan.solidAt(s[0], 0, s[2]));
        assertNull("room to stand on the landing", plan.get(s[0], 1, s[2]));
        assertNull(plan.get(s[0], 2, s[2]));
        assertArrayEquals(s, plan.anchor("spawn"));
    }

    @Test
    public void planIsDeterministicAndHasItsLandmarks() {
        BlockPlan a = GrovePlan.build(45000);
        BlockPlan b = GrovePlan.build(45000);
        assertEquals(a.size(), b.size());
        assertTrue(a.count(Material.MANGROVE_ROOTS) > 20);
        assertTrue(a.count(Material.SHROOMLIGHT) > 10);
        assertTrue(a.count(Material.FARMLAND) > 40);
        assertEquals(1, a.signs().size());
    }

    @Test
    public void everyIsletHasLandAndABridge() {
        BlockPlan plan = GrovePlan.build(45000);
        for (int i = 0; i < GrovePlan.ISLET_COUNT; i++) {
            int[] c = GrovePlan.isletCenter(i);
            assertTrue("islet " + i, plan.solidAt(c[0], 0, c[1]));
            // the bridge reaches the islet: some plank sits between the main island and its edge
            double f = (GrovePlan.ISLET_ORBIT - GrovePlan.ISLET_RADIUS) / (double) GrovePlan.ISLET_ORBIT;
            boolean plank = false;
            for (int dx = -1; dx <= 1 && !plank; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    plank |= plan.get((int) Math.round(c[0] * f) + dx, 0, (int) Math.round(c[1] * f) + dz) == Material.MANGROVE_PLANKS;
                }
            }
            assertTrue("bridge to islet " + i, plank);
        }
    }

    @Test
    public void isletsStayInsideTheWorldBorder() {
        for (int i = 0; i < GrovePlan.ISLET_COUNT; i++) {
            int[] c = GrovePlan.isletCenter(i);
            assertTrue(Math.abs(c[0]) + GrovePlan.ISLET_RADIUS < GrovePlan.BORDER / 2.0 + 10);
            assertTrue(Math.abs(c[1]) + GrovePlan.ISLET_RADIUS < GrovePlan.BORDER / 2.0 + 10);
        }
    }

    @Test
    public void affinityTiersFollowTheThresholds() {
        assertEquals(0, GroveAffinity.tierOf(0));
        assertEquals(0, GroveAffinity.tierOf(GroveAffinity.THRESHOLDS[0] - 1));
        assertEquals(1, GroveAffinity.tierOf(GroveAffinity.THRESHOLDS[0]));
        assertEquals(2, GroveAffinity.tierOf(GroveAffinity.THRESHOLDS[1]));
    }

    @Test
    public void affinityReportsOnlyTheCrossingAndSurvivesSaving() {
        GroveAffinity affinity = new GroveAffinity();
        UUID id = UUID.randomUUID();
        affinity.set(id, GroveAffinity.THRESHOLDS[0] - 1);
        assertEquals(-1, affinity.add(id, 0));
        assertEquals(1, affinity.add(id, 1));
        assertEquals(-1, affinity.add(id, 1));
        GroveAffinity back = GroveAffinity.fromDoc(affinity.toDoc());
        assertEquals(GroveAffinity.THRESHOLDS[0] + 1, back.points(id));
        assertEquals(1, back.tier(id));
        assertEquals(0, GroveAffinity.fromDoc(null).points(id));
    }

    @Test
    public void groveIsASkyblockWorld() {
        assertTrue(SkyblockWorlds.isSkyblock(SkyblockWorlds.GROVE));
        assertFalse(SkyblockWorlds.isDungeon(SkyblockWorlds.GROVE));
    }

    @Test
    public void markerOnlyCountsWhenCompleteAndCurrent() {
        assertFalse(GroveRealm.upToDate(null));
        GroveRealm.Marker m = new GroveRealm.Marker();
        m.version = GrovePlan.VERSION;
        assertFalse(GroveRealm.upToDate(m));
        m.complete = true;
        assertTrue(GroveRealm.upToDate(m));
        m.version = GrovePlan.VERSION - 1;
        assertFalse(GroveRealm.upToDate(m));
    }
}
