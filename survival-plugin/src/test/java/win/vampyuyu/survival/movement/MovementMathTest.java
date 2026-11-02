package win.vampyuyu.survival.movement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import win.vampyuyu.survival.movement.MovementMath.Vec;

class MovementMathTest {
    @Test
    void capLimitsHorizontalAndVertical() {
        Vec v = MovementMath.cap(new Vec(10, 10, 0));
        assertEquals(MovementMath.MAX_HORIZONTAL, v.horizontal(), 1e-9);
        assertEquals(MovementMath.MAX_UP, v.y(), 1e-9);
        assertEquals(-MovementMath.MAX_DOWN, MovementMath.cap(new Vec(0, -9, 0)).y(), 1e-9);
        assertEquals(Vec.ZERO, MovementMath.cap(new Vec(Double.NaN, 0, 0)));
    }

    @Test
    void knockbackPointsAwayAndIsCapped() {
        Vec v = MovementMath.knockback(0, 0, 3, 0, 99, 5);
        assertEquals(MovementMath.MAX_KNOCKBACK, v.x(), 1e-9);
        assertEquals(0, v.z(), 1e-9);
        assertEquals(0.8, v.y(), 1e-9);
        assertTrue(MovementMath.knockback(0, 0, 0, -2, 1, 0).z() < 0);
        // on top of the source: still pushed
        assertTrue(MovementMath.knockback(1, 1, 1, 1, 1, 0).horizontal() > 0.9);
        assertEquals(0, MovementMath.knockback(0, 0, 1, 0, -3, -3).horizontal(), 1e-9);
    }

    @Test
    void pullPointsToCentreWithDeadZoneAndRadius() {
        Vec in = MovementMath.pull(5, 0, 0, 0, 0, 0, 0.2, 8, 0, 0, 0);
        assertTrue(in.x() < 0);
        assertEquals(Vec.ZERO, MovementMath.pull(9, 0, 0, 0, 0, 0, 0.2, 8, 0, 0, 0));
        assertEquals(Vec.ZERO, MovementMath.pull(0.5, 0, 0, 0, 0, 0, 0.2, 8, 0, 0, 0));
        Vec near = MovementMath.pull(1, 0, 0, 0, 0, 0, 0.2, 8, 0, 0, 0);
        assertTrue(Math.abs(near.x()) > Math.abs(in.x()), "stronger near the centre");
    }

    @Test
    void resistAndAnchorsCutThePull() {
        double full = Math.abs(MovementMath.pull(4, 0, 0, 0, 0, 0, 0.2, 8, 0, 3, 3).x());
        double half = Math.abs(MovementMath.pull(4, 0, 0, 0, 0, 0, 0.2, 8, 0.5, 3, 3).x());
        double oneAnchor = Math.abs(MovementMath.pull(4, 0, 0, 0, 0, 0, 0.2, 8, 0, 1, 3).x());
        assertEquals(full / 2, half, 1e-9);
        assertEquals(full / 3, oneAnchor, 1e-9);
        assertEquals(Vec.ZERO, MovementMath.pull(4, 0, 0, 0, 0, 0, 0.2, 8, 0, 0, 3));
        assertEquals(0, MovementMath.pull(4, 0, 0, 0, 0, 0, 0.2, 8, 1, 3, 3).x(), 1e-9);
    }

    @Test
    void repeatedPullNeverExceedsTheCap() {
        Vec v = Vec.ZERO;
        for (int i = 0; i < 200; i++) {
            v = MovementMath.applyPull(v, new Vec(-1, 0, 0));
        }
        assertEquals(MovementMath.MAX_HORIZONTAL, v.horizontal(), 1e-9);
    }

    @Test
    void movementDamageNeverKills() {
        assertEquals(19, MovementMath.safeDamage(20, 100), 1e-9);
        assertEquals(0, MovementMath.safeDamage(0.5, 4), 1e-9);
        assertEquals(3, MovementMath.safeDamage(20, 3), 1e-9);
    }

    @Test
    void effectLevelsAreClamped() {
        assertEquals(MovementMath.MAX_LEVITATION_AMP, MovementMath.levitationAmp(9));
        assertEquals(0, MovementMath.levitationAmp(-3));
        assertEquals(MovementMath.MAX_LEVITATION_TICKS, MovementMath.levitationTicks(9999));
        assertEquals(MovementMath.MAX_JUMP_AMP, MovementMath.jumpAmp(50));
    }

    @Test
    void columnAndSafeSpot() {
        assertTrue(MovementMath.inColumn(1, 5, 0, 0, 0, 0, 2, 30));
        assertFalse(MovementMath.inColumn(3, 5, 0, 0, 0, 0, 2, 30));
        assertFalse(MovementMath.inColumn(0, 31, 0, 0, 0, 0, 2, 30));
        assertTrue(MovementMath.isSafeSpot(true, true, false, 60, 0));
        assertFalse(MovementMath.isSafeSpot(false, true, false, 60, 0));
        assertFalse(MovementMath.isSafeSpot(true, true, true, 60, 0));
        assertFalse(MovementMath.isSafeSpot(true, true, false, 3, 0));
    }
}
