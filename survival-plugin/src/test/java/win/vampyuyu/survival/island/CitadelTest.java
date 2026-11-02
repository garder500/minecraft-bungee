package win.vampyuyu.survival.island;

import static org.junit.Assert.*;

import org.bukkit.Material;
import org.junit.Test;
import win.vampyuyu.survival.api.TimeTide;
import win.vampyuyu.survival.core.SkyblockWorlds;

/** The Citadel of Golden Sands: layout (pure data), the Time Tide and the effects of its phases. */
public class CitadelTest {
    @Test
    public void planStaysUnderTheBlockCapAndHasASolidLanding() {
        BlockPlan plan = CitadelPlan.build(45000);
        assertTrue("plan is not empty", plan.size() > 3000);
        assertTrue(plan.size() < 45000);
        int[] s = CitadelPlan.spawn();
        assertTrue("the landing platform is solid", plan.solidAt(s[0], 0, s[2]));
        assertNull("room to stand on the landing", plan.get(s[0], 1, s[2]));
        assertNull(plan.get(s[0], 2, s[2]));
        assertArrayEquals(s, plan.anchor("spawn"));
        assertNotNull(plan.anchor("pharaoh"));
    }

    @Test
    public void planIsDeterministicAndHasItsLandmarks() {
        BlockPlan a = CitadelPlan.build(45000);
        BlockPlan b = CitadelPlan.build(45000);
        assertEquals(a.size(), b.size());
        assertTrue(a.count(Material.GOLD_BLOCK) > 10);
        assertTrue(a.count(Material.YELLOW_STAINED_GLASS) > 40);
        assertEquals(1, a.signs().size());
        assertEquals("one column of dust each", 4, CitadelPlan.dustColumns().length);
        for (int[] c : CitadelPlan.dustColumns()) {
            assertTrue(a.solidAt(c[0], 12, c[1]));
        }
    }

    @Test
    public void thePyramidHasAChamberTheGuardianCanSleepIn() {
        BlockPlan plan = CitadelPlan.build(45000);
        int[] p = plan.anchor("pharaoh");
        assertTrue("floor under the Pharaoh", plan.solidAt(p[0], 0, p[2]) || plan.get(p[0], 1, p[2]) == Material.GOLD_BLOCK);
        assertEquals(Material.AIR, plan.get(2, 2, p[2] + 2));
        assertEquals("the gate is open", Material.AIR, plan.get(0, 1, p[2] + 8));
    }

    @Test
    public void everyIsletHasLand() {
        BlockPlan plan = CitadelPlan.build(45000);
        for (int i = 0; i < CitadelPlan.ISLET_COUNT; i++) {
            int[] c = CitadelPlan.isletCenter(i);
            assertTrue("islet " + i, plan.solidAt(c[0], 0, c[1]));
        }
    }

    @Test
    public void citadelIsASkyblockWorldThatIsNotADungeon() {
        assertTrue(SkyblockWorlds.isSkyblock(SkyblockWorlds.CITADEL));
        assertFalse(SkyblockWorlds.isDungeon(SkyblockWorlds.CITADEL));
    }

    @Test
    public void theTideCyclesFromTheClockAlone() {
        TimeTide tide = new TimeTide(60, 20, 10);
        assertEquals(90, tide.cycleSeconds());
        assertEquals(TimeTide.Phase.CALM, tide.at(0).phase());
        assertEquals(60, tide.at(0).remainingSeconds());
        assertEquals(TimeTide.Phase.CALM, tide.at(59_999).phase());
        assertEquals(TimeTide.Phase.SURGE, tide.at(60_000).phase());
        assertEquals(20, tide.at(60_000).remainingSeconds());
        assertEquals(TimeTide.Phase.STALL, tide.at(80_000).phase());
        assertEquals(1, tide.at(89_000).remainingSeconds());
        assertEquals("the cycle repeats", TimeTide.Phase.CALM, tide.at(90_000).phase());
        assertEquals("before the epoch too", TimeTide.Phase.STALL, tide.at(-1_000).phase());
    }

    @Test
    public void aBrokenTideSettingStillMakesACycle() {
        TimeTide tide = new TimeTide(0, -5, 0);
        assertEquals(3, tide.cycleSeconds());
        assertNotNull(tide.at(123_456));
    }

    @Test
    public void thePhasesGiveSpeedOrSlownessAndTheCalmGivesNothing() {
        assertEquals(0, CitadelRealm.effects(TimeTide.Phase.CALM).length);
        assertEquals(2, CitadelRealm.effects(TimeTide.Phase.SURGE).length);
        assertEquals(1, CitadelRealm.effects(TimeTide.Phase.STALL).length);
    }
}
