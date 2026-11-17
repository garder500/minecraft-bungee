package win.vampyuyu.survival.island;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;
import org.bukkit.Material;
import org.junit.Test;

/** The hub plan (the last anchored fragment of Aethelgard): ground, pads, reachability, light, bounds and cost. */
public class HubPlanTest {
    private static final BlockPlan FULL = HubPlan.build(Integer.MAX_VALUE);

    /** Blocks an entity can walk through. */
    private static boolean passable(BlockPlan plan, int x, int y, int z) {
        Material m = plan.get(x, y, z);
        return m == null || m.isAir() || m == Material.OAK_SIGN || m == Material.MOSS_CARPET || m == Material.AMETHYST_CLUSTER;
    }

    private static boolean standable(BlockPlan plan, int x, int y, int z) {
        return !passable(plan, x, y - 1, z) && passable(plan, x, y, z) && passable(plan, x, y + 1, z);
    }

    /** Standing levels reached by walking from (x, y, z) to a neighbouring column (one step up or down allowed). */
    private static Set<Long> reachable(BlockPlan plan, int sx, int sy, int sz) {
        Set<Long> seen = new HashSet<>();
        ArrayDeque<int[]> queue = new ArrayDeque<>();
        queue.add(new int[] {sx, sy, sz});
        seen.add(BlockPlan.key(sx, sy, sz));
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        while (!queue.isEmpty()) {
            int[] c = queue.poll();
            for (int[] d : dirs) {
                int nx = c[0] + d[0];
                int nz = c[2] + d[1];
                for (int dy = 1; dy >= -1; dy--) {
                    int ny = c[1] + dy;
                    if (standable(plan, nx, ny, nz) && (dy <= 0 || passable(plan, c[0], c[1] + 2, c[2]))) {
                        if (seen.add(BlockPlan.key(nx, ny, nz))) {
                            queue.add(new int[] {nx, ny, nz});
                        }
                        break;
                    }
                }
            }
        }
        return seen;
    }

    private static boolean lightNear(BlockPlan plan, int cx, int cz, int range) {
        for (int x = cx - range; x <= cx + range; x++) {
            for (int z = cz - range; z <= cz + range; z++) {
                for (int y = 0; y <= 6; y++) {
                    Material m = plan.get(x, y, z);
                    if (m != null && HubPlan.emitsLight(m)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    @Test
    public void versionIsBumpedAndTheOldWipeSquareLeavesTheQuaiBridge() {
        assertEquals(3, HubPlan.VERSION);
        HubManager.Marker old = new HubManager.Marker();
        old.version = 2;
        old.complete = true;
        old.hubY = 100;
        assertTrue(HubManager.needsWipe(old, 100, true));
        assertTrue("an interrupted build leaves a pad and no marker", HubManager.needsWipe(new HubManager.Marker(), 100, true));
        assertFalse("a fresh world has nothing to wipe", HubManager.needsWipe(new HubManager.Marker(), 100, false));
        old.version = HubPlan.VERSION;
        assertFalse(HubManager.needsWipe(old, 100, true));
        assertTrue("wipe stops before the bridge (z 43)", HubManager.WIPE_CENTER_Z + HubManager.WIPE_RADIUS < QuaiPlan.CENTER_Z + QuaiPlan.BRIDGE_FROM - 1);
    }

    @Test
    public void bossSpotsAndTheEchoAltarStandOnOpenGround() {
        // boss_world_spot offsets are relative to the world spawn (0, 14), the Echo altar is a hub position
        for (int[] spot : new int[][] {{-8, -6 + HubPlan.SPAWN_Z}, {12, -14 + HubPlan.SPAWN_Z}}) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    assertTrue("ground " + spot[0] + "," + spot[1], HubPlan.ground(spot[0] + dx, spot[1] + dz));
                    assertTrue("open " + spot[0] + "," + spot[1], passable(FULL, spot[0] + dx, 1, spot[1] + dz)
                        && passable(FULL, spot[0] + dx, 2, spot[1] + dz));
                }
            }
        }
        assertTrue("the Echo altar (-26.5, 0.5) is in front of the Rift tear, on the fragment", HubPlan.ground(-27, 0));
        assertTrue(passable(FULL, -27, 1, 0) && passable(FULL, -27, 2, 0));
    }

    @Test
    public void padsAreTheContractAndFairySoulsAreGone() {
        assertEquals(11, HubPlan.PADS.size());
        for (String id : new String[] {"merchant", "archivist", "guard", "contract_hunter", "quest_master", "banker", "boss_arena",
            "island_gate", "dungeon_gate", "rift_gate", "eight_docks"}) {
            assertNotNull(id, HubPlan.pad(id));
        }
        assertEquals(null, HubPlan.pad("fairy_souls"));
    }

    @Test
    public void spawnIsASolidLitFloorWithRoomAbove() {
        BlockPlan spawn = HubPlan.emergencySpawn();
        assertTrue(spawn.size() < 300);
        for (BlockPlan plan : new BlockPlan[] {spawn, FULL}) {
            assertTrue(plan.solidAt(HubPlan.SPAWN_X, 0, HubPlan.SPAWN_Z));
            assertTrue(passable(plan, HubPlan.SPAWN_X, 1, HubPlan.SPAWN_Z));
            assertTrue(passable(plan, HubPlan.SPAWN_X, 2, HubPlan.SPAWN_Z));
            assertTrue("lit spawn", lightNear(plan, HubPlan.SPAWN_X, HubPlan.SPAWN_Z, 5));
        }
        assertTrue(HubPlan.ground(HubPlan.SPAWN_X, HubPlan.SPAWN_Z));
    }

    @Test
    public void everyPadIsSolidGroundWithRoomLitAndReachableFromSpawn() {
        Set<Long> walk = reachable(FULL, HubPlan.SPAWN_X, 1, HubPlan.SPAWN_Z);
        for (HubPlan.Pad pad : HubPlan.PADS) {
            for (int x = -2; x <= 2; x++) {
                for (int z = -2; z <= 2; z++) {
                    assertTrue(pad.id() + " ground at " + x + "," + z, HubPlan.ground(pad.x() + x, pad.z() + z));
                    assertTrue(pad.id() + " solid floor", FULL.solidAt(pad.x() + x, 0, pad.z() + z));
                }
            }
            assertTrue(pad.id() + " head room 1", passable(FULL, pad.x(), 1, pad.z()));
            assertTrue(pad.id() + " head room 2", passable(FULL, pad.x(), 2, pad.z()));
            assertTrue(pad.id() + " lit", lightNear(FULL, pad.x(), pad.z(), 4));
            assertTrue(pad.id() + " reachable on foot", walk.contains(BlockPlan.key(pad.x(), 1, pad.z())));
            assertNotNull(FULL.anchor("pad:" + pad.id()));
        }
    }

    @Test
    public void theProcessionalReachesTheQuaiBridgeAndTheGatesAreReachable() {
        Set<Long> walk = reachable(FULL, HubPlan.SPAWN_X, 1, HubPlan.SPAWN_Z);
        for (int x = -2; x <= 2; x++) {
            assertTrue("causeway end " + x, walk.contains(BlockPlan.key(x, 1, HubPlan.CAUSEWAY_END_Z)));
        }
        // the arches stand next to the road, not on it
        for (int z : new int[] {24, 32, 41}) {
            assertTrue("road open under the arch at " + z, walk.contains(BlockPlan.key(0, 1, z)));
        }
        // the landing beyond the Island Gate
        assertTrue(walk.contains(BlockPlan.key(0, 1, -38)));
    }

    @Test
    public void theBrokenRimNeverCutsARoadOrAnNpc() {
        for (int[] bite : new int[][] {{0, -30}, {0, 30}, {30, 0}, {-30, 0}}) {
            assertTrue("axis ground " + bite[0] + "," + bite[1], HubPlan.ground(bite[0], bite[1]));
        }
        boolean hole = false;
        for (int x = -40; x <= 40; x++) {
            for (int z = -40; z <= 40; z++) {
                if (Math.hypot(x, z) < 26 && !HubPlan.ground(x, z)) {
                    hole = true;
                }
            }
        }
        assertFalse("no hole inside radius 26", hole);
        boolean broken = false;
        for (double a = 0; a < Math.PI * 2; a += 0.02) {
            double r = HubPlan.radiusAt(a);
            if (!HubPlan.ground((int) Math.round((r - 1) * Math.cos(a)), (int) Math.round((r - 1) * Math.sin(a)))) {
                broken = true;
            }
        }
        assertTrue("the rim has bites", broken);
    }

    @Test
    public void everythingStaysInsideTheBorderAndOutOfTheQuaiFootprint() {
        assertTrue(HubPlan.outOfBounds(FULL, 100).isEmpty());
        for (long key : FULL.blocks().keySet()) {
            int x = BlockPlan.keyX(key);
            int z = BlockPlan.keyZ(key);
            if (z >= 45) {
                assertTrue("block in the Quai approach at " + x + "," + z, Math.abs(x) >= 26);
            }
            assertTrue("Quai deck " + x + "," + z, !(z >= QuaiPlan.CENTER_Z - QuaiPlan.RADIUS && Math.abs(x) <= QuaiPlan.RADIUS + 2));
        }
        BlockPlan quai = QuaiPlan.build(30000);
        assertNotNull("the bridge starts where the processional ends", quai.get(0, 0, QuaiPlan.BRIDGE_FROM - 1));
        assertEquals("bridge world z", HubPlan.CAUSEWAY_END_Z - 1, QuaiPlan.BRIDGE_FROM - 1 + QuaiPlan.CENTER_Z);
    }

    @Test
    public void thePlanFitsTheBlockCapAndTheCapCutsDecorationFirst() {
        int size = FULL.size();
        assertTrue("size " + size, size < 38000);
        assertEquals("nothing is cut at the real cap", size, HubPlan.build(45000).size());
        BlockPlan small = HubPlan.build(3000);
        assertTrue(small.size() <= 3000);
        assertTrue("spawn kept", small.solidAt(HubPlan.SPAWN_X, 0, HubPlan.SPAWN_Z));
        assertTrue("pads laid before decoration", small.solidAt(HubPlan.PADS.get(0).x(), 0, HubPlan.PADS.get(0).z()));
    }

    @Test
    public void theFragmentReadsAsAethelgardNotAPlaza() {
        assertTrue(FULL.count(Material.CALCITE) > 200);
        assertTrue(FULL.count(Material.AMETHYST_BLOCK) > 40);
        assertTrue(FULL.count(Material.LIGHT_BLUE_STAINED_GLASS) >= 8);
        assertTrue(FULL.count(Material.SEA_LANTERN) >= 8);
        assertTrue(FULL.count(Material.CRYING_OBSIDIAN) >= 4);
        assertTrue("memory spire", FULL.solidAt(0, 14, 0));
        assertEquals(Material.AMETHYST_CLUSTER, FULL.get(0, 16, 0));
        assertTrue("island arch", FULL.solidAt(-4, 5, -29));
        assertTrue("Threshold", FULL.solidAt(5, 5, 41) && FULL.solidAt(-5, 5, 41));
        assertEquals("no fountain water", 0, FULL.count(Material.WATER));
        for (Material gravity : new Material[] {Material.SAND, Material.GRAVEL, Material.RED_SAND}) {
            assertEquals(gravity.name(), 0, FULL.count(gravity));
        }
        // debris floats: a block with nothing but air in the column below it
        boolean floating = false;
        for (long key : FULL.blocks().keySet()) {
            if (BlockPlan.keyY(key) >= 2 && Math.abs(BlockPlan.keyX(key)) > 40 && !HubPlan.ground(BlockPlan.keyX(key), BlockPlan.keyZ(key))) {
                floating = true;
                break;
            }
        }
        assertTrue("drifting debris", floating);
    }

    @Test
    public void thePlanIsDeterministic() {
        BlockPlan again = HubPlan.build(Integer.MAX_VALUE);
        assertEquals(FULL.size(), again.size());
        assertEquals(FULL.get(7, 0, 7), again.get(7, 0, 7));
        assertEquals(FULL.get(-13, 3, 21), again.get(-13, 3, 21));
    }
}
