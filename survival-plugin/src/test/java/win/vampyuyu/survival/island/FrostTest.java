package win.vampyuyu.survival.island;

import static org.junit.Assert.*;

import java.util.UUID;
import org.bukkit.Material;
import org.junit.Test;
import win.vampyuyu.survival.core.SkyblockWorlds;

/** The Frozen Heights: layout (pure data) and affinity tiers. */
public class FrostTest {
    @Test
    public void planStaysUnderTheBlockCapAndHasASolidLanding() {
        BlockPlan plan = FrostPlan.build(45000);
        assertTrue("plan is not empty", plan.size() > 3000);
        assertTrue(plan.size() < 45000);
        int[] s = FrostPlan.spawn();
        assertTrue("the landing platform is solid", plan.solidAt(s[0], 0, s[2]));
        assertNull("room to stand on the landing", plan.get(s[0], 1, s[2]));
        assertNull(plan.get(s[0], 2, s[2]));
        assertArrayEquals(s, plan.anchor("spawn"));
    }

    @Test
    public void planIsDeterministicAndHasItsLandmarks() {
        BlockPlan a = FrostPlan.build(45000);
        BlockPlan b = FrostPlan.build(45000);
        assertEquals(a.size(), b.size());
        assertTrue(a.count(Material.BLUE_ICE) > 20);
        assertTrue(a.count(Material.PACKED_ICE) > 100);
        assertTrue(a.count(Material.AMETHYST_CLUSTER) > 2);
        assertEquals(1, a.signs().size());
    }

    @Test
    public void everyPeakHasLandAndABridge() {
        BlockPlan plan = FrostPlan.build(45000);
        for (int i = 0; i < FrostPlan.PEAK_COUNT; i++) {
            int[] c = FrostPlan.peakCenter(i);
            assertTrue("peak " + i, plan.solidAt(c[0], 0, c[1]));
            double f = (FrostPlan.PEAK_ORBIT - FrostPlan.PEAK_RADIUS) / (double) FrostPlan.PEAK_ORBIT;
            boolean ice = false;
            for (int dx = -1; dx <= 1 && !ice; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    ice |= plan.get((int) Math.round(c[0] * f) + dx, 0, (int) Math.round(c[1] * f) + dz) == Material.PACKED_ICE;
                }
            }
            assertTrue("bridge to peak " + i, ice);
        }
    }

    @Test
    public void peaksStayInsideTheWorldBorder() {
        for (int i = 0; i < FrostPlan.PEAK_COUNT; i++) {
            int[] c = FrostPlan.peakCenter(i);
            assertTrue(Math.abs(c[0]) + FrostPlan.PEAK_RADIUS < FrostPlan.BORDER / 2.0 + 10);
            assertTrue(Math.abs(c[1]) + FrostPlan.PEAK_RADIUS < FrostPlan.BORDER / 2.0 + 10);
        }
    }

    @Test
    public void affinityTiersFollowTheThresholdsAndSurviveSaving() {
        assertEquals(0, FrostAffinity.tierOf(FrostAffinity.THRESHOLDS[0] - 1));
        assertEquals(1, FrostAffinity.tierOf(FrostAffinity.THRESHOLDS[0]));
        assertEquals(2, FrostAffinity.tierOf(FrostAffinity.THRESHOLDS[1]));
        FrostAffinity affinity = new FrostAffinity();
        UUID id = UUID.randomUUID();
        affinity.set(id, FrostAffinity.THRESHOLDS[0] - 1);
        assertEquals(-1, affinity.add(id, 0));
        assertEquals(1, affinity.add(id, 1));
        FrostAffinity back = FrostAffinity.fromDoc(affinity.toDoc());
        assertEquals(1, back.tier(id));
        assertEquals(0, FrostAffinity.fromDoc(null).points(id));
    }

    @Test
    public void onlyIceCountsAndTheWorldIsSkyblock() {
        assertTrue(FrostRealm.isIce(Material.BLUE_ICE));
        assertTrue(FrostRealm.isIce(Material.PACKED_ICE));
        assertFalse(FrostRealm.isIce(Material.SNOW_BLOCK));
        assertTrue(SkyblockWorlds.isSkyblock(SkyblockWorlds.HEIGHTS));
        assertFalse(SkyblockWorlds.isDungeon(SkyblockWorlds.HEIGHTS));
    }

    @Test
    public void markerOnlyCountsWhenCompleteAndCurrent() {
        assertFalse(FrostRealm.upToDate(null));
        FrostRealm.Marker m = new FrostRealm.Marker();
        m.version = FrostPlan.VERSION;
        assertFalse(FrostRealm.upToDate(m));
        m.complete = true;
        assertTrue(FrostRealm.upToDate(m));
        m.version = FrostPlan.VERSION - 1;
        assertFalse(FrostRealm.upToDate(m));
    }
}
