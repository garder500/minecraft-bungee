package win.vampyuyu.survival.boss;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.google.gson.JsonParser;
import java.util.List;
import java.util.Map;
import org.bukkit.util.Transformation;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.Test;

public class BossRigTest {
    private static final float[] REST = BossRig.REST;

    /** Where the display draws a model point v (model units) for a pose: the item renders (v - 8) / 16 turned half a turn
     * about y (the client's item display renderer does), then the display transform. */
    private static Vector3f drawn(Transformation t, Vector3f v) {
        Matrix4f m = new Matrix4f().translate(t.getTranslation()).rotate(t.getLeftRotation()).scale(t.getScale())
            .rotate(t.getRightRotation()).rotateY((float) Math.PI);
        return m.transformPosition(new Vector3f(v).sub(8, 8, 8).div(16));
    }

    @Test
    public void aForwardTiltStaysForwardThroughTheDisplaysHalfTurn() {
        // a bone tipping its top toward the model's front (-z): rotation about x by -30 degrees in the model frame
        float a = (float) Math.toRadians(-30) / 2;
        float[] pose = {0, 0, 0, (float) Math.sin(a), 0, 0, (float) Math.cos(a), 1};
        Vector3f top = new Vector3f(8, 16, 8);
        Vector3f rest = drawn(BossRig.transform(REST, 2f), top);
        Vector3f tilted = drawn(BossRig.transform(pose, 2f), top);
        Vector3f front = drawn(BossRig.transform(REST, 2f), new Vector3f(8, 8, 0)).sub(drawn(BossRig.transform(REST, 2f), new Vector3f(8, 8, 8)));
        // the top moves the way the model's front points on screen
        assertTrue(tilted.sub(rest).dot(front) > 0);
    }

    @Test
    public void theRestPoseIsTheOldSingleDisplayTransform() {
        Transformation t = BossRig.transform(REST, 2.4f);
        assertEquals(0, t.getTranslation().x, 1e-6);
        assertEquals(1.2, t.getTranslation().y, 1e-6);
        assertEquals(2.4, t.getScale().x, 1e-6);
        assertEquals(1, t.getLeftRotation().w, 1e-6);
        // the model's feet (y = 0) stand on the ground (display y = 0)
        assertEquals(0, drawn(t, new Vector3f(8, 0, 8)).y, 1e-5);
    }

    @Test
    public void aPoseMapsAModelPointLikeTheBakedAffine() {
        // a quarter turn about +y and a 1.5 scale about the centre, then 2 units up: k R (v - C) + C + t
        float h = (float) Math.sqrt(0.5);
        float[] pose = {0, 2, 0, 0, h, 0, h, 1.5f};
        Vector3f v = new Vector3f(12, 8, 8);                       // 4 units +x of the centre
        Vector3f want = new Vector3f(8, 10, 8 - 6);               // turned to -z, scaled 1.5, lifted 2
        float s = 3f;
        Vector3f got = drawn(BossRig.transform(pose, s), v);
        Vector3f expect = drawn(BossRig.transform(REST, s), want);
        assertEquals(expect.x, got.x, 1e-4);
        assertEquals(expect.y, got.y, 1e-4);
        assertEquals(expect.z, got.z, 1e-4);
    }

    static BossRig rig() {
        String json = "{\"bones\":[{\"name\":\"body\",\"parent\":null,\"model\":\"vampyuyu:boss/skin/x/body\"}],\"animations\":{"
            + clip("idle", true, false, 4) + "," + clip("walk", true, false, 4) + "," + clip("attack", false, false, 6) + ","
            + clip("ability", false, false, 8) + "," + clip("ground_slam", false, false, 8) + "," + clip("hurt", false, false, 2) + ","
            + clip("death", false, true, 6) + "}}";
        return BossRig.parse(JsonParser.parseString(json).getAsJsonObject());
    }

    /** A clip whose frame i lifts the bone by i units (so the frame index can be read back from a pose). */
    private static String clip(String name, boolean loop, boolean hold, int ticks) {
        StringBuilder f = new StringBuilder();
        for (int i = 0; i <= ticks; i += 2) {
            f.append(i == 0 ? "" : ",").append("[[0,").append(i / 2).append(",0,0,0,0,1,1]]");
        }
        return "\"" + name + "\":{\"loop\":" + loop + ",\"hold\":" + hold + ",\"ticks\":" + ticks + ",\"step\":2,\"frames\":[" + f + "]}";
    }

    @Test
    public void parsesBonesAndClips() {
        BossRig r = rig();
        assertEquals(List.of("body"), r.bones.stream().map(BossRig.Bone::name).toList());
        assertEquals(7, r.clips.size());
        assertEquals(4, r.clips.get("attack").frameCount());
        assertTrue(r.clips.get("death").hold());
    }

    @Test
    public void idleLoopsWithoutRepeatingItsClosingFrame() {
        BossAnimator a = new BossAnimator(rig(), 0);
        assertEquals("idle", a.current(0));
        // idle has frames 0, 1, 2 (the last equals the first in a closed loop): 0, 1, 0, 1 ...
        assertEquals(0f, a.poses(0)[0][1], 0);
        assertEquals(1f, a.poses(2)[0][1], 0);
        assertEquals(0f, a.poses(4)[0][1], 0);
        assertEquals(1f, a.poses(6)[0][1], 0);
    }

    @Test
    public void walksWhileMovingAndPlaysOneShotsOverTheBase() {
        BossAnimator a = new BossAnimator(rig(), 0);
        a.moving(true, 10);
        assertEquals("walk", a.current(10));
        assertTrue(a.cue("attack", 12));
        assertEquals("attack", a.current(14));
        assertEquals(1f, a.poses(14)[0][1], 0);
        assertEquals("walk", a.current(20));          // 6 ticks later the attack is over
    }

    @Test
    public void priorities() {
        BossAnimator a = new BossAnimator(rig(), 0);
        assertTrue(a.cue("ground_slam", 0));
        assertFalse("a hurt must not cut an ability", a.cue("hurt", 2));
        assertEquals("ground_slam", a.current(2));
        assertTrue("an unknown ability plays the generic clip", a.cue("blizzard", 3));
        assertEquals("ability", a.current(4));
        assertTrue(a.cue("death", 5));
        assertFalse(a.cue("attack", 6));
        assertEquals("death", a.current(100));         // a hold clip stays
        assertEquals(3f, a.poses(100)[0][1], 0);
        assertTrue(a.dying());
    }

    @Test
    public void aRigWithoutTheClipIgnoresTheCue() {
        BossRig r = BossRig.parse(JsonParser.parseString("{\"bones\":[{\"name\":\"b\",\"model\":\"m\"}],\"animations\":{}}").getAsJsonObject());
        BossAnimator a = new BossAnimator(r, 0);
        assertNull(a.clipFor("attack"));
        assertFalse(a.cue("attack", 0));
        assertEquals(1f, a.poses(0)[0][6], 0);        // the rest pose
        assertTrue(Map.of().isEmpty());
    }
}
