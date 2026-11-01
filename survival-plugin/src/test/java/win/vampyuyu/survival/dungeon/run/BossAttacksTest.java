package win.vampyuyu.survival.dungeon.run;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import java.util.Random;
import org.junit.Test;

public class BossAttacksTest {
    @Test
    public void aSlamHitsInsideItsCircleAndANovaEverywhereElse() {
        assertTrue(BossAttacks.hitsCircle(BossAttacks.Kind.SLAM, 3, 3));
        assertFalse("out of the red circle is safe", BossAttacks.hitsCircle(BossAttacks.Kind.SLAM, 8, 0));
        assertFalse("inside the green circle is safe", BossAttacks.hitsCircle(BossAttacks.Kind.NOVA, 3, 3));
        assertTrue(BossAttacks.hitsCircle(BossAttacks.Kind.NOVA, 8, 0));
    }

    @Test
    public void aChargeHitsCloseToItsLineOnly() {
        assertTrue("on the line", BossAttacks.hitsLine(0, 0, 10, 0, 5, 0));
        assertTrue("next to it", BossAttacks.hitsLine(0, 0, 10, 0, 5, 1.5));
        assertFalse("stepped off", BossAttacks.hitsLine(0, 0, 10, 0, 5, 3));
        assertFalse("behind where it stops", BossAttacks.hitsLine(0, 0, 10, 0, 14, 0));
    }

    @Test
    public void twoRoomsInARowNeverHaveTheSameStyleAndTheFirstFloorHasNoHoldRoom() {
        Random rng = new Random(5);
        FightStyle last = null;
        for (int i = 0; i < 500; i++) {
            FightStyle next = FightStyle.pick(1, last, rng);
            assertNotEquals(FightStyle.HOLD, next);
            assertNotEquals(last, next);
            last = next;
        }
        boolean hold = false;
        for (int i = 0; i < 500; i++) {
            hold |= FightStyle.pick(2, null, rng) == FightStyle.HOLD;
        }
        assertTrue(hold);
    }
}
