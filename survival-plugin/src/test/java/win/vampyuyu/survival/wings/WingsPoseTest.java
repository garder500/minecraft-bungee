package win.vampyuyu.survival.wings;

import static org.junit.Assert.*;

import org.joml.Vector3f;
import org.junit.Test;
import win.vampyuyu.survival.wings.WingsPose.Input;
import win.vampyuyu.survival.wings.WingsPose.Look;
import win.vampyuyu.survival.wings.WingsPose.Stance;
import win.vampyuyu.survival.wings.WingsPose.Variant;

public class WingsPoseTest {
    @Test
    public void foldedWhenStanding() {
        Look l = WingsPose.decide(new Input(false, false, false, false));
        assertEquals(Variant.FOLDED, l.variant());
        assertFalse(l.flap());
        assertEquals(Stance.STAND, l.stance());
    }

    @Test
    public void sneakingMovesTheAnchorOnly() {
        Look l = WingsPose.decide(new Input(false, false, false, true));
        assertEquals(Variant.FOLDED, l.variant());
        assertEquals(Stance.SNEAK, l.stance());
    }

    @Test
    public void spreadWhenFlyingWithoutFlap() {
        Look l = WingsPose.decide(new Input(false, true, false, false));
        assertEquals(Variant.SPREAD, l.variant());
        assertFalse(l.flap());
    }

    @Test
    public void glidingSpreadsFlapsAndLeans() {
        Look l = WingsPose.decide(new Input(true, false, false, false));
        assertEquals(Variant.SPREAD, l.variant());
        assertTrue(l.flap());
        assertEquals(Stance.LEANED, l.stance());
        // gliding wins over everything else
        assertTrue(WingsPose.decide(new Input(true, true, true, true)).flap());
    }

    @Test
    public void swimmingFoldsAndLeans() {
        Look l = WingsPose.decide(new Input(false, false, true, false));
        assertEquals(Variant.FOLDED, l.variant());
        assertEquals(Stance.LEANED, l.stance());
    }

    @Test
    public void flapPhaseAlternatesSlowly() {
        int h = WingsPose.FlapCycle.HALF_PERIOD;
        assertEquals(0, WingsPose.FlapCycle.phase(0));
        assertEquals(0, WingsPose.FlapCycle.phase(h - 1));
        assertEquals(1, WingsPose.FlapCycle.phase(h));
        assertEquals(1, WingsPose.FlapCycle.phase(2L * h - 1));
        assertEquals(0, WingsPose.FlapCycle.phase(2L * h));
        assertTrue("a flap is slow: at least a second per half", h >= 10);
    }

    // ---------------------------------------------------------------- geometry

    @Test
    public void standingTranslationPutsTheWingsBehindTheBack() {
        Vector3f t = WingsMath.translation(Stance.STAND, 0);
        assertEquals(0, t.x, 1e-6);
        assertEquals(1.35 - 1.8, t.y, 1e-5);   // shoulder minus the passenger anchor
        assertEquals(-WingsMath.BACK, t.z, 1e-6);  // behind (forward is +z)
        assertTrue(WingsMath.BACK > 0.125);        // clear of the torso
    }

    @Test
    public void glidingAnchorFollowsTheLeanAboutTheFeet() {
        // looking straight ahead: body horizontal, the shoulder is 1.35 in front of the feet and BACK above them
        Vector3f t = WingsMath.translation(Stance.LEANED, WingsMath.lean(Stance.LEANED, 0));
        assertEquals(90, WingsMath.lean(Stance.LEANED, 0), 1e-9);
        assertEquals(1.35, t.z, 1e-4);
        assertEquals(WingsMath.BACK - 0.6, t.y, 1e-4);
        // diving straight down: upright reversed, shoulder below the feet height
        assertEquals(180, WingsMath.lean(Stance.LEANED, 90), 1e-9);
        assertEquals(0, WingsMath.lean(Stance.STAND, 40), 1e-9);
    }

    @Test
    public void wingsNeverBlockTheFirstPersonViewBelowThePitchLimit() {
        for (Stance s : new Stance[] {Stance.STAND, Stance.SNEAK}) {
            for (Variant v : Variant.values()) {
                for (double pitch = -90; pitch <= WingsMath.HIDE_PITCH; pitch += 1) {
                    assertFalse(s + " " + v + " pitch " + pitch,
                        WingsMath.inView(s, v, pitch, WingsMath.VIEW_CONE));
                }
            }
        }
        assertTrue(WingsMath.SHOW_PITCH < WingsMath.HIDE_PITCH);
    }

    @Test
    public void glidingWingsAreNeverInView() {
        for (double pitch = -90; pitch <= 90; pitch += 5) {
            assertFalse(WingsMath.inView(Stance.LEANED, Variant.SPREAD, pitch, WingsMath.VIEW_CONE));
        }
    }

    @Test
    public void lookingStraightDownWouldSeeTheWings() {
        // the reason the display is hidden from its owner at a steep downward pitch
        assertTrue(WingsMath.inView(Stance.STAND, Variant.SPREAD, 90, WingsMath.VIEW_CONE));
    }

    @Test
    public void everyVariantNameMatchesThePackFiles() {
        assertEquals("folded", Variant.FOLDED.key());
        assertEquals("spread", Variant.SPREAD.key());
        assertEquals("vampyuyu:wings/worn_tier3_spread".length(),
            ("vampyuyu:wings/worn_tier3_" + Variant.SPREAD.key()).length());
    }

    @Test
    public void flapScaleDrawsTheWingsIn() {
        assertEquals(1f, WingsMath.flapScale(0).x, 1e-6);
        assertTrue(WingsMath.flapScale(1).x < 1f);
    }
}
