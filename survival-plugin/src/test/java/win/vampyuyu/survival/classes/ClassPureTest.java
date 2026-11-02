package win.vampyuyu.survival.classes;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.UUID;
import org.junit.Test;
import win.vampyuyu.survival.api.PlayerClass;

public class ClassPureTest {
    @Test
    public void everyClassStaysInsideTheMetaCaps() {
        for (PlayerClass c : PlayerClass.values()) {
            ClassBudget.Bonus b = ClassBudget.of(c);
            assertTrue(c + " Vitality " + b.vitality(), b.vitality() <= ClassBudget.META_VITALITY);
            assertTrue(c + " Might " + b.might(), b.might() <= ClassBudget.META_MIGHT);
            assertTrue(c + " Dexterity " + b.dexterity(), b.dexterity() <= ClassBudget.META_DEXTERITY);
            assertTrue(b.vitality() >= 0 && b.might() >= 0 && b.dexterity() >= 0);
        }
    }

    @Test
    public void classBonusesTakeFromTheCapNeverAddToIt() {
        ClassBudget.Bonus full = ClassBudget.of(PlayerClass.DUELIST, 100, 25, 25);
        assertEquals(0, full.vitality(), 0);
        assertEquals(0, full.might(), 0);
        assertEquals(0, full.dexterity(), 0);
        ClassBudget.Bonus partial = ClassBudget.of(PlayerClass.DUELIST, 0, 20, 10);
        assertEquals("only 5 Might left under the cap", 5, partial.might(), 0);
        assertEquals("only 15 Dexterity left", 15, partial.dexterity(), 0);
        assertEquals(20, partial.vitality(), 0);
        assertEquals(0, ClassBudget.of(null).vitality(), 0);
    }

    @Test
    public void bulwarkCutsThirtyPercentAndTheVanguardPassiveFivePercent() {
        assertEquals(70, ClassRules.damageTaken(100, true, false), 1e-9);
        assertEquals(95, ClassRules.damageTaken(100, false, true), 1e-9);
        assertEquals(66.5, ClassRules.damageTaken(100, true, true), 1e-9);
        assertEquals(100, ClassRules.damageTaken(100, false, false), 1e-9);
    }

    @Test
    public void riftStepGoesSixBlocksAndStopsInFrontOfAnObstacle() {
        assertEquals(6, ClassRules.riftDistance(ClassRules.RIFT_BLOCKS, d -> true), 0);
        assertEquals("a wall at 3.2 blocks stops at 3", 3.0, ClassRules.riftDistance(6, d -> d < 3.2), 0);
        assertEquals("blocked at once: no dash", 0, ClassRules.riftDistance(6, d -> false), 0);
        assertEquals("never past the free limit", 2.5, ClassRules.riftDistance(2.6, d -> true), 0);
    }

    @Test
    public void volleyIsThreeArrowsOfFortyPercent() {
        assertEquals(3, ClassRules.VOLLEY_ARROWS);
        assertEquals(0.40, ClassRules.VOLLEY_SHARE, 0);
    }

    @Test
    public void aetherRefillsOverTimeAndRefusesWhatItCannotPay() {
        AetherPool pool = new AetherPool();
        UUID id = UUID.randomUUID();
        assertEquals("a new player starts full", 100, pool.current(id, 100, 0), 0);
        assertTrue(pool.spend(id, 60, 100, 0));
        assertEquals(40, pool.current(id, 100, 0), 1e-9);
        assertFalse("40 Aether do not pay 50", pool.spend(id, 50, 100, 0));
        assertEquals("5 s of regeneration", 50, pool.current(id, 100, 5000), 1e-9);
        assertEquals(0, pool.waitFor(id, 50, 100, 5000));
        assertEquals("10 more Aether take 5 s", 5000, pool.waitFor(id, 60, 100, 5000));
        assertEquals("never above the maximum", 100, pool.current(id, 100, 3_600_000), 0);
    }
}
