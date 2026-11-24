package win.vampyuyu.survival.island;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import org.bukkit.Material;
import org.junit.Test;

/** The Breach: the hub's one damage zone, on its own islet, reachable on foot, inside the hub's limits. */
public class HubBreachTest {
    private static final BlockPlan FULL = HubPlan.build(Integer.MAX_VALUE);

    @Test
    public void theIsletIsApartFromTheHubAndInsideItsLimits() {
        for (int x = HubBreach.X - HubBreach.RADIUS - 1; x <= HubBreach.X + HubBreach.RADIUS + 1; x++) {
            for (int z = HubBreach.Z - HubBreach.RADIUS - 1; z <= HubBreach.Z + HubBreach.RADIUS + 1; z++) {
                if (HubBreach.ground(x, z)) {
                    assertFalse("the safe hub and the Breach never share a column " + x + "," + z, HubPlan.ground(x, z));
                    assertTrue(Math.abs(x) <= 100 && Math.abs(z) <= 100);
                    assertTrue("clear of the Quai (z 50 to 94)", z < 50);
                    assertTrue("built", FULL.solidAt(x, 0, z));
                }
            }
        }
        assertTrue(HubPlan.outOfBounds(FULL, 100).isEmpty());
    }

    @Test
    public void onlyTheIsletCountsAsInside() {
        assertTrue(HubBreach.contains(HubBreach.X, 1, HubBreach.Z));
        assertFalse("the spawn is safe", HubBreach.contains(HubPlan.SPAWN_X, 1, HubPlan.SPAWN_Z));
        assertFalse("the bridge start is safe", HubBreach.contains(HubBreach.BRIDGE_FROM[0], 1, HubBreach.BRIDGE_FROM[1]));
        assertFalse("the void far under it does not count", HubBreach.contains(HubBreach.X, -60, HubBreach.Z));
        for (int x = -45; x <= 45; x++) {
            for (int z = -45; z <= 45; z++) {
                if (HubPlan.ground(x, z)) {
                    assertFalse("hub column " + x + "," + z + " stays safe", HubBreach.contains(x, 1, z));
                }
            }
        }
    }

    @Test
    public void aPlayerWalksFromTheSpawnToTheBreachOnTheBridge() {
        // flood the walkable surface (a solid block at y 0 with room above) from the spawn
        Set<Long> seen = new HashSet<>();
        ArrayDeque<int[]> todo = new ArrayDeque<>();
        todo.add(new int[] {HubPlan.SPAWN_X, HubPlan.SPAWN_Z});
        boolean reached = false;
        while (!todo.isEmpty() && !reached) {
            int[] c = todo.poll();
            if (!seen.add(((long) c[0] << 32) ^ (c[1] & 0xffffffffL)) || Math.abs(c[0]) > 100 || Math.abs(c[1]) > 100) {
                continue;
            }
            if (!FULL.solidAt(c[0], 0, c[1]) || FULL.solidAt(c[0], 1, c[1]) && FULL.get(c[0], 1, c[1]) != Material.MOSS_CARPET) {
                continue;
            }
            reached = HubBreach.contains(c[0], 1, c[1]);
            todo.add(new int[] {c[0] + 1, c[1]});
            todo.add(new int[] {c[0] - 1, c[1]});
            todo.add(new int[] {c[0], c[1] + 1});
            todo.add(new int[] {c[0], c[1] - 1});
        }
        assertTrue("the Breach is reachable on foot from the spawn", reached);
    }

    @Test
    public void spawnsLandOnTheIsletAwayFromTheBridge() {
        Random rng = new Random(7);
        for (int i = 0; i < 200; i++) {
            int[] c = HubBreach.randomColumn(rng);
            assertTrue(HubBreach.ground(c[0], c[1]));
            assertTrue(Math.hypot(c[0] - HubBreach.BRIDGE_TO[0], c[1] - HubBreach.BRIDGE_TO[1]) > 6 || (c[0] == HubBreach.X && c[1] == HubBreach.Z));
        }
    }

    @Test
    public void warningSignsStandAtBothEnds() {
        long signs = FULL.signs().stream().filter(s -> s.lines().get(0).equals("The Breach")).count();
        assertEquals(2, signs);
        assertNotNull(FULL.get(HubBreach.X, 1, HubBreach.Z));
    }
}
