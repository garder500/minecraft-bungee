package win.vampyuyu.survival.island;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Map;
import org.bukkit.Material;
import org.junit.Test;

/** The Devourer's mouth on the Quai: it must not touch the Gates, stay on the platform, be reachable and trigger only at its opening. */
public class QuaiMouthTest {
    private static final BlockPlan FULL = QuaiPlan.build(Integer.MAX_VALUE);
    private static final BlockPlan BASE = QuaiPlan.build(Integer.MAX_VALUE, false);

    private static boolean passable(int x, int y, int z) {
        Material m = FULL.get(x, y, z);
        return m == null || m.isAir();
    }

    @Test
    public void versionIsBumpedSoExistingQuaisRebuild() {
        assertTrue(QuaiPlan.VERSION >= 4);
    }

    @Test
    public void theMouthChangesNothingAboveTheDeckOfTheRestOfTheQuai() {
        for (Map.Entry<Long, BlockPlan.Blk> e : BASE.blocks().entrySet()) {
            int y = BlockPlan.keyY(e.getKey());
            if (y < 1) {
                continue;
            }
            assertEquals("the mouth overwrote a block of the Quai at " + BlockPlan.keyX(e.getKey()) + "," + y + ","
                + BlockPlan.keyZ(e.getKey()), e.getValue(), FULL.blocks().get(e.getKey()));
        }
    }

    @Test
    public void everyMouthBlockIsOnThePlatformAndUnderTheCap() {
        assertTrue(FULL.size() < 45000);
        for (Map.Entry<Long, BlockPlan.Blk> e : FULL.blocks().entrySet()) {
            if (BASE.blocks().containsKey(e.getKey())) {
                continue;
            }
            int x = BlockPlan.keyX(e.getKey());
            int z = BlockPlan.keyZ(e.getKey());
            assertTrue(Math.hypot(x, z) <= QuaiPlan.RADIUS - 1);
        }
    }

    @Test
    public void sealedInTheWorld() {
        assertFalse(QuaiMouth.MEMBRANE.isEmpty());
        assertEquals(QuaiMouth.MEMBRANE.size(), QuaiMouth.BACKING.size());
        for (int[] cell : QuaiMouth.MEMBRANE) {
            assertEquals(QuaiMouth.SEALED, FULL.get(cell[0], cell[1], cell[2]));
            assertEquals(QuaiMouth.SEALED, FULL.get(cell[0], cell[1], cell[2] + 1));
            assertNull("nothing but a tooth in front of the membrane", FULL.get(cell[0], cell[1], cell[2] - 1) == Material.POINTED_DRIPSTONE
                ? null : FULL.get(cell[0], cell[1], cell[2] - 1));
        }
    }

    @Test
    public void youCanWalkFromTheDaisToTheMouth() {
        for (int z = 8; z <= QuaiMouth.Z - 1; z++) {
            for (int x = -1; x <= 1; x++) {
                assertNotNull("floor at " + x + "," + z, FULL.get(x, 0, z));
                assertTrue("head room at " + x + "," + z, passable(x, 1, z) && passable(x, 2, z));
            }
        }
    }

    @Test
    public void theMouthStandsBetweenGates4And5NotInAGate() {
        double x = 0;
        for (QuaiPlan.Gate gate : QuaiPlan.GATES) {
            assertTrue("too close to gate " + gate.realm(), Math.hypot(gate.x() - x, gate.z() - QuaiMouth.Z) > 5);
        }
    }

    @Test
    public void theTriggerIsInFrontOfTheOpeningOnly() {
        double rz = QuaiMouth.Z - 0.5;
        assertTrue(QuaiMouth.inMouth(0.5, 1, rz));
        assertTrue(QuaiMouth.inMouth(0.5, 3, QuaiMouth.Z - 1.2));
        assertFalse("beside the opening", QuaiMouth.inMouth(4.5, 1, rz));
        assertFalse("too far from it", QuaiMouth.inMouth(0.5, 1, QuaiMouth.Z - 3));
        assertFalse("above it", QuaiMouth.inMouth(0.5, 12, rz));
        assertFalse("the dais", QuaiMouth.inMouth(0.5, 1, 0.5));
        assertFalse("the bridge", QuaiMouth.inMouth(0.5, 1, QuaiPlan.BRIDGE_FROM));
        assertFalse("the arrival point", QuaiMouth.inMouth(0.5, 1, QuaiPlan.SPAWN_Z + 0.5));
    }

    @Test
    public void theTriggerNeverOverlapsAGate() {
        for (double x = -QuaiPlan.RADIUS; x <= QuaiPlan.RADIUS; x += 0.25) {
            for (double rz = -QuaiPlan.RADIUS; rz <= QuaiPlan.RADIUS; rz += 0.25) {
                for (double y : new double[] {0, 1, 3, 6}) {
                    if (QuaiMouth.inMouth(x, y, rz)) {
                        assertNull("mouth and gate overlap at " + x + "," + rz,
                            SkyGates.gateAt(x, y, QuaiPlan.CENTER_Z + rz));
                    }
                }
            }
        }
    }

    @Test
    public void theSignSaysSealedAndTheLitStateIsTheClientsToChange() {
        BlockPlan.SignText sign = FULL.signs().stream().filter(s -> s.x() == QuaiMouth.SIGN[0] && s.z() == QuaiMouth.SIGN[1])
            .findFirst().orElseThrow();
        assertEquals("Sealed", sign.lines().get(3));
        assertEquals(QuaiMouth.Z - 2, FULL.anchor("mouth")[2]);
    }
}
