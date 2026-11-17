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
        Vector3f t = WingsMath.translation(Stance.STAND, 0, Variant.FOLDED, WingsPose.Side.LEFT);
        assertEquals(0, t.x, 1e-6);
        assertEquals(1.35 - 1.8, t.y, 1e-5);   // shoulder minus the passenger anchor
        assertEquals(-WingsMath.BACK, t.z, 1e-6);  // behind (forward is +z)
        assertTrue(WingsMath.BACK > 0.125);        // clear of the torso
    }

    @Test
    public void glidingAnchorFollowsTheLeanAboutTheFeet() {
        // looking straight ahead: body horizontal, the shoulder is 1.35 in front of the feet and BACK above them
        Vector3f t = WingsMath.translation(Stance.LEANED, WingsMath.lean(Stance.LEANED, 0), Variant.FOLDED, WingsPose.Side.RIGHT);
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
    public void rootsLeaveAClearGapInTheMiddleOfTheGlideView() {
        // camera at the feet end, shoulders 1.35 ahead: the gap between the two roots must span at least +-10 degrees
        assertTrue(Math.toDegrees(Math.atan2(WingsMath.ROOT_X - 0.05, 1.35)) >= 10);
    }

    @Test
    public void glidingWingsAreInViewSoTheRootsLeaveTheCentreClear() {
        for (double pitch = -90; pitch <= 90; pitch += 5) {
            assertTrue(WingsMath.inView(Stance.LEANED, Variant.SPREAD, pitch, WingsMath.VIEW_CONE));
            assertEquals(WingsMath.HIDE_OWN_WHILE_GLIDING, WingsMath.hideFromOwner(Stance.LEANED, pitch, false));
        }
        assertFalse(WingsMath.hideFromOwner(Stance.STAND, 0, false));
        assertTrue(WingsMath.hideFromOwner(Stance.STAND, 21, false));
        assertTrue("hysteresis", WingsMath.hideFromOwner(Stance.STAND, 16, true));
        assertFalse(WingsMath.hideFromOwner(Stance.STAND, 16, false));
        assertFalse(WingsMath.hideFromOwner(Stance.STAND, 10, true));
    }

    @Test
    public void lookingStraightDownWouldSeeTheWings() {
        // the reason the display is hidden from its owner at a steep downward pitch
        assertTrue(WingsMath.inView(Stance.STAND, Variant.SPREAD, 90, WingsMath.VIEW_CONE));
    }

    @Test
    public void everyWingModelNameMatchesThePackFiles() throws Exception {
        assertEquals("folded", Variant.FOLDED.key());
        assertEquals("spread", Variant.SPREAD.key());
        java.nio.file.Path models = java.nio.file.Path.of("..", "resource-pack", "pack", "assets", "vampyuyu", "models", "item", "wings");
        if (!java.nio.file.Files.isDirectory(models)) {
            return;  // run from another working directory: the pack test covers it
        }
        for (int tier = 1; tier <= 5; tier++) {
            for (Variant v : Variant.values()) {
                for (WingsPose.Side side : WingsPose.Side.values()) {
                    String name = "worn_tier" + tier + "_" + v.key() + "_" + side.key();
                    assertTrue(name, java.nio.file.Files.exists(models.resolve(name + ".json")));
                    org.bukkit.inventory.ItemStack st = WingsDisplay.stack(tier, v, side);
                    assertEquals("vampyuyu:wings/" + name, st.getItemMeta().getItemModel().toString());
                }
            }
        }
    }

    @Test
    public void spreadWingsMirrorEachOtherAndFoldedOnesDoNotTurn() {
        for (Stance s : Stance.values()) {
            for (int phase = 0; phase < 2; phase++) {
                Vector3f l = new Vector3f(1, 0, 0).rotate(WingsMath.wingRotation(WingsPose.Side.LEFT, Variant.SPREAD, s, true, phase));
                Vector3f r = new Vector3f(-1, 0, 0).rotate(WingsMath.wingRotation(WingsPose.Side.RIGHT, Variant.SPREAD, s, true, phase));
                assertEquals(l.x, -r.x, 1e-5);
                assertEquals(l.y, r.y, 1e-5);
                assertEquals(l.z, r.z, 1e-5);
            }
        }
        assertEquals(new org.joml.Quaternionf().identity().w, WingsMath.wingRotation(WingsPose.Side.LEFT, Variant.FOLDED, Stance.STAND, false, 0).w, 1e-6);
        assertEquals(1, WingsMath.translation(Stance.STAND, 0, Variant.SPREAD, WingsPose.Side.LEFT).x / WingsMath.ROOT_X, 1e-5);
        assertEquals(-1, WingsMath.translation(Stance.STAND, 0, Variant.SPREAD, WingsPose.Side.RIGHT).x / WingsMath.ROOT_X, 1e-5);
        assertEquals(0, WingsMath.translation(Stance.STAND, 0, Variant.FOLDED, WingsPose.Side.RIGHT).x, 1e-6);
    }

    @Test
    public void glidingWingsFormAVSweptBack() {
        // world frame while gliding level: body leaned 90 degrees about x; the tip of each wing relative to its root
        for (WingsPose.Side side : WingsPose.Side.values()) {
            for (int phase = 0; phase < 2; phase++) {
                Vector3f tip = new Vector3f(-side.sign(), 0, 0);   // model +x is the wearer's right: display x = -sign
                tip.mul(-1);                                       // wing direction in the display frame: outward = +sign
                tip.rotate(WingsMath.wingRotation(side, Variant.SPREAD, Stance.LEANED, true, phase));
                tip.rotate(WingsMath.leanRotation(90));
                double up = Math.toDegrees(Math.asin(tip.y));
                double back = Math.toDegrees(Math.asin(-tip.z));   // toward the tail: leaned body, feet are at -z
                assertTrue("raised " + up, up >= 14 && up <= 40);
                assertTrue("swept back " + back, back >= 12 && back <= 25);
            }
        }
        assertTrue(WingsMath.dihedral(Variant.SPREAD, true, 0) > WingsMath.dihedral(Variant.SPREAD, true, 1));
        assertEquals(0, WingsMath.dihedral(Variant.FOLDED, true, 0), 1e-9);
    }
}
