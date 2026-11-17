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
    /** Records calls like the entity side would. */
    static final class Fake implements WingsRig.Backend {
        final Set<UUID> live = new HashSet<>();
        final List<String> log = new ArrayList<>();
        boolean failSpawn;
        int lastTicks;

        public boolean spawn(UUID o, WingsRig.Desired d) {
            log.add("spawn");
            if (failSpawn) {
                return false;
            }
            live.add(o);
            return true;
        }

        public void update(UUID o, WingsRig.Desired p, WingsRig.Desired n, int ticks) {
            log.add("update");
            lastTicks = ticks;
        }

        public void remove(UUID o) {
            log.add("remove");
            live.remove(o);
        }

        public boolean alive(UUID o) {
            return live.contains(o);
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
        assertEquals(List.of("spawn"), f.log);
        assertEquals(1, f.live.size());
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
        assertEquals(1, f.live.size());
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
        assertEquals(1, f.log.stream().filter("remove"::equals).count());
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
        assertEquals(1, f.live.size());
        assertTrue(rig.attached(a));
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
        assertEquals(5, f.live.size());
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
