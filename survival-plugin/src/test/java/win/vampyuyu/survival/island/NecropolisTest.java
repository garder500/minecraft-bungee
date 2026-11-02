package win.vampyuyu.survival.island;

import static org.junit.Assert.*;

import java.util.UUID;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.junit.Test;
import win.vampyuyu.survival.api.Realm;
import win.vampyuyu.survival.core.SkyblockWorlds;

/** The Ethereal Necropolis: layout (pure data), affinity tiers, marker. */
public class NecropolisTest {
    @Test
    public void planStaysUnderTheBlockCapAndHasASolidLanding() {
        BlockPlan plan = NecropolisPlan.build(45000);
        assertTrue("plan is not empty", plan.size() > 3000);
        assertTrue(plan.size() < 45000);
        int[] s = NecropolisPlan.spawn();
        assertTrue("the landing platform is solid", plan.solidAt(s[0], 0, s[2]));
        assertNull("room to stand on the landing", plan.get(s[0], 1, s[2]));
        assertNull(plan.get(s[0], 2, s[2]));
        assertArrayEquals(s, plan.anchor("spawn"));
    }

    @Test
    public void planIsDeterministicAndHasItsLandmarks() {
        BlockPlan a = NecropolisPlan.build(45000);
        BlockPlan b = NecropolisPlan.build(45000);
        assertEquals(a.size(), b.size());
        assertTrue(a.count(Material.SOUL_SAND) > 30);
        assertTrue(a.count(Material.BONE_BLOCK) > 10);
        assertTrue(a.count(Material.SOUL_LANTERN) > 10);
        assertEquals(1, a.signs().size());
    }

    @Test
    public void everyIsletHasLandAndABridge() {
        BlockPlan plan = NecropolisPlan.build(45000);
        for (int i = 0; i < NecropolisPlan.ISLET_COUNT; i++) {
            int[] c = NecropolisPlan.isletCenter(i);
            assertTrue("islet " + i, plan.solidAt(c[0], 0, c[1]));
            double f = (NecropolisPlan.ISLET_ORBIT - NecropolisPlan.ISLET_RADIUS) / (double) NecropolisPlan.ISLET_ORBIT;
            boolean brick = false;
            for (int dx = -1; dx <= 1 && !brick; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    brick |= plan.get((int) Math.round(c[0] * f) + dx, 0, (int) Math.round(c[1] * f) + dz) == Material.POLISHED_BLACKSTONE_BRICKS;
                }
            }
            assertTrue("bridge to islet " + i, brick);
        }
    }

    @Test
    public void isletsStayInsideTheWorldBorder() {
        for (int i = 0; i < NecropolisPlan.ISLET_COUNT; i++) {
            int[] c = NecropolisPlan.isletCenter(i);
            assertTrue(Math.abs(c[0]) + NecropolisPlan.ISLET_RADIUS < NecropolisPlan.BORDER / 2.0 + 10);
            assertTrue(Math.abs(c[1]) + NecropolisPlan.ISLET_RADIUS < NecropolisPlan.BORDER / 2.0 + 10);
        }
    }

    @Test
    public void affinityTiersFollowTheThresholds() {
        assertEquals(0, NecropolisAffinity.tierOf(0));
        assertEquals(0, NecropolisAffinity.tierOf(NecropolisAffinity.THRESHOLDS[0] - 1));
        assertEquals(1, NecropolisAffinity.tierOf(NecropolisAffinity.THRESHOLDS[0]));
        assertEquals(2, NecropolisAffinity.tierOf(NecropolisAffinity.THRESHOLDS[1]));
    }

    @Test
    public void affinityReportsOnlyTheCrossingAndSurvivesSaving() {
        NecropolisAffinity affinity = new NecropolisAffinity();
        UUID id = UUID.randomUUID();
        affinity.set(id, NecropolisAffinity.THRESHOLDS[0] - 1);
        assertEquals(-1, affinity.add(id, 0));
        assertEquals(1, affinity.add(id, 1));
        assertEquals(-1, affinity.add(id, 1));
        NecropolisAffinity back = NecropolisAffinity.fromDoc(affinity.toDoc());
        assertEquals(NecropolisAffinity.THRESHOLDS[0] + 1, back.points(id));
        assertEquals(1, back.tier(id));
        assertEquals(0, NecropolisAffinity.fromDoc(null).points(id));
    }

    @Test
    public void onlyTheUndeadCountForTheAffinity() {
        assertTrue(NecropolisRealm.undead(EntityType.SKELETON));
        assertTrue(NecropolisRealm.undead(EntityType.WITHER_SKELETON));
        assertTrue(NecropolisRealm.undead(EntityType.ZOMBIE));
        assertFalse(NecropolisRealm.undead(EntityType.COW));
        assertFalse(NecropolisRealm.undead(EntityType.CREEPER));
    }

    @Test
    public void necropolisIsASkyblockWorldAndTheEighthRealm() {
        assertTrue(SkyblockWorlds.isSkyblock(SkyblockWorlds.NECROPOLIS));
        assertFalse(SkyblockWorlds.isDungeon(SkyblockWorlds.NECROPOLIS));
        assertEquals(8, Realm.NECROPOLIS.number());
    }

    @Test
    public void markerOnlyCountsWhenCompleteAndCurrent() {
        assertFalse(NecropolisRealm.upToDate(null));
        NecropolisRealm.Marker m = new NecropolisRealm.Marker();
        m.version = NecropolisPlan.VERSION;
        assertFalse(NecropolisRealm.upToDate(m));
        m.complete = true;
        assertTrue(NecropolisRealm.upToDate(m));
        m.version = NecropolisPlan.VERSION - 1;
        assertFalse(NecropolisRealm.upToDate(m));
    }
}
