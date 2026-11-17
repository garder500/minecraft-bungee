package win.vampyuyu.survival.wings;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.Test;
import win.vampyuyu.survival.wings.WingsPose.Stance;
import win.vampyuyu.survival.wings.WingsPose.Variant;

public class WingsRigTest {
    /** Records calls like the entity side would: one entity per (owner, side). */
    static final class Fake implements WingsRig.Backend {
        final Set<String> live = new HashSet<>();
        final List<String> log = new ArrayList<>();
        boolean failSpawn;
        boolean failRightSpawn;
        int lastTicks;

        public boolean spawn(UUID o, WingsPose.Side s, WingsRig.Desired d) {
            log.add("spawn");
            if (failSpawn || failRightSpawn && s == WingsPose.Side.RIGHT) {
                return false;
            }
            live.add(o + "/" + s);
            return true;
        }

        public void update(UUID o, WingsPose.Side s, WingsRig.Desired p, WingsRig.Desired n, int ticks) {
            log.add("update");
            lastTicks = ticks;
        }

        public void remove(UUID o, WingsPose.Side s) {
            log.add("remove");
            live.remove(o + "/" + s);
        }

        public boolean alive(UUID o, WingsPose.Side s) {
            return live.contains(o + "/" + s);
        }
    }

    private static WingsRig.Desired d(Variant v, int phase, int yaw, int lean, boolean hide) {
        return new WingsRig.Desired(3, v, phase, v == Variant.SPREAD && lean > 0, lean > 0 ? Stance.LEANED : Stance.STAND,
            yaw, lean, hide);
    }

    @Test
    public void oneDisplayPerWearerAndNothingSentWhileStill() {
        Fake f = new Fake();
        WingsRig rig = new WingsRig(f);
        UUID a = UUID.randomUUID();
        assertTrue(rig.sync(a, d(Variant.FOLDED, 0, 10, 0, false)));
        assertFalse(rig.sync(a, d(Variant.FOLDED, 0, 10, 0, false)));
        assertFalse(rig.sync(a, d(Variant.FOLDED, 0, 10, 0, false)));
        assertEquals(1, rig.spawns());
        assertEquals(0, rig.updates());
        assertEquals(List.of("spawn", "spawn"), f.log);  // the pair: left and right
        assertEquals(2, f.live.size());
    }

    @Test
    public void rotationAndVariantChangesUpdateTheSameDisplay() {
        Fake f = new Fake();
        WingsRig rig = new WingsRig(f);
        UUID a = UUID.randomUUID();
        rig.sync(a, d(Variant.FOLDED, 0, 10, 0, false));
        assertTrue(rig.sync(a, d(Variant.FOLDED, 0, 20, 0, false)));
        assertEquals(WingsRig.FAST, f.lastTicks);
        assertTrue(rig.sync(a, d(Variant.SPREAD, 0, 20, 0, false)));
        assertEquals(1, rig.spawns());
        assertEquals(2, rig.updates());
        assertEquals(2, f.live.size());  // still one pair, every update touched both wings
        assertEquals(4, f.log.stream().filter("update"::equals).count());
    }

    @Test
    public void aFlapAloneInterpolatesSlowly() {
        Fake f = new Fake();
        WingsRig rig = new WingsRig(f);
        UUID a = UUID.randomUUID();
        rig.sync(a, d(Variant.SPREAD, 0, 10, 90, false));
        rig.sync(a, d(Variant.SPREAD, 1, 10, 90, false));
        assertEquals(WingsRig.SLOW, f.lastTicks);
        rig.sync(a, d(Variant.SPREAD, 0, 14, 90, false));
        assertEquals(WingsRig.FAST, f.lastTicks);
    }

    @Test
    public void nullDetachesAndDoubleDetachIsHarmless() {
        Fake f = new Fake();
        WingsRig rig = new WingsRig(f);
        UUID a = UUID.randomUUID();
        rig.sync(a, d(Variant.FOLDED, 0, 0, 0, false));
        assertTrue(rig.attached(a));
        assertTrue(rig.sync(a, null));
        assertFalse(rig.attached(a));
        assertFalse(rig.sync(a, null));
        assertFalse(rig.detach(a));
        assertEquals(0, f.live.size());
        assertEquals(2, f.log.stream().filter("remove"::equals).count());  // both wings, once
    }

    @Test
    public void aDeadEntityIsReplacedNotLeaked() {
        Fake f = new Fake();
        WingsRig rig = new WingsRig(f);
        UUID a = UUID.randomUUID();
        rig.sync(a, d(Variant.FOLDED, 0, 0, 0, false));
        f.live.clear();  // chunk unloaded, dismounted, killed...
        assertTrue(rig.sync(a, d(Variant.FOLDED, 0, 0, 0, false)));
        assertEquals(2, rig.spawns());
        assertEquals(2, f.live.size());
        assertTrue(rig.attached(a));
    }

    @Test
    public void oneLostWingReplacesTheWholePair() {
        Fake f = new Fake();
        WingsRig rig = new WingsRig(f);
        UUID a = UUID.randomUUID();
        rig.sync(a, d(Variant.SPREAD, 0, 0, 90, false));
        f.live.remove(a + "/" + WingsPose.Side.RIGHT);  // one of the two was killed
        assertTrue(rig.sync(a, d(Variant.SPREAD, 0, 0, 90, false)));
        assertEquals(2, rig.spawns());
        assertEquals(2, f.live.size());
        assertEquals(Set.of(a + "/LEFT", a + "/RIGHT"), f.live);
    }

    @Test
    public void aFailedSecondWingLeavesNoLoneWing() {
        Fake f = new Fake();
        f.failRightSpawn = true;
        WingsRig rig = new WingsRig(f);
        UUID a = UUID.randomUUID();
        assertFalse(rig.sync(a, d(Variant.SPREAD, 0, 0, 90, false)));
        assertFalse(rig.attached(a));
        assertTrue("the left wing is removed again", f.live.isEmpty());
    }

    @Test
    public void aFailedSpawnLeavesNothingAttached() {
        Fake f = new Fake();
        f.failSpawn = true;
        WingsRig rig = new WingsRig(f);
        UUID a = UUID.randomUUID();
        assertFalse(rig.sync(a, d(Variant.FOLDED, 0, 0, 0, false)));
        assertFalse(rig.attached(a));
        assertTrue(rig.owners().isEmpty());
    }

    @Test
    public void detachAllRemovesEveryDisplay() {
        Fake f = new Fake();
        WingsRig rig = new WingsRig(f);
        for (int i = 0; i < 5; i++) {
            rig.sync(UUID.randomUUID(), d(Variant.FOLDED, 0, i, 0, false));
        }
        assertEquals(10, f.live.size());
        rig.detachAll();
        assertEquals(0, f.live.size());
        assertTrue(rig.owners().isEmpty());
    }

    @Test
    public void hidingFromTheOwnerIsAnUpdateNotARespawn() {
        Fake f = new Fake();
        WingsRig rig = new WingsRig(f);
        UUID a = UUID.randomUUID();
        rig.sync(a, d(Variant.FOLDED, 0, 0, 0, false));
        assertTrue(rig.sync(a, d(Variant.FOLDED, 0, 0, 0, true)));
        assertEquals(1, rig.spawns());
    }
}
