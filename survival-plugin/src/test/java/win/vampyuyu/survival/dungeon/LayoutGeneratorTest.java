package win.vampyuyu.survival.dungeon;

import static org.junit.Assert.*;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;
import org.junit.Test;
import win.vampyuyu.survival.dungeon.gen.DoorKind;
import win.vampyuyu.survival.dungeon.gen.DoorPlan;
import win.vampyuyu.survival.dungeon.gen.Layout;
import win.vampyuyu.survival.dungeon.gen.LayoutGenerator;
import win.vampyuyu.survival.dungeon.gen.RoomPlan;
import win.vampyuyu.survival.dungeon.gen.RoomType;

public class LayoutGeneratorTest {
    private static final DungeonConfig CFG = DungeonConfig.parse(null);

    @Test
    public void sameSeedGivesSameLayout() {
        for (int f = 1; f <= 7; f++) {
            Layout a = LayoutGenerator.generate(CFG.floor(f), 12345L);
            Layout b = LayoutGenerator.generate(CFG.floor(f), 12345L);
            assertEquals(a.ascii(), b.ascii());
            assertEquals(a.doors().size(), b.doors().size());
            for (int i = 0; i < a.doors().size(); i++) {
                assertEquals(a.doors().get(i).kind, b.doors().get(i).kind);
                assertEquals(a.doors().get(i).a, b.doors().get(i).a);
            }
            assertEquals(a.totalSecrets(), b.totalSecrets());
        }
    }

    @Test
    public void differentSeedsDiffer() {
        Set<String> shapes = new HashSet<>();
        for (long seed = 1; seed <= 20; seed++) {
            shapes.add(LayoutGenerator.generate(CFG.floor(4), seed).ascii());
        }
        assertTrue("20 seeds should give several shapes", shapes.size() > 5);
    }

    @Test
    public void everyRoomIsConnectedAndTheBossIsReachable() {
        for (int f = 1; f <= 7; f++) {
            for (long seed = 0; seed < 150; seed++) {
                Layout l = LayoutGenerator.generate(CFG.floor(f), seed * 7919 + f);
                String where = "floor " + f + " seed " + seed + "\n" + l.ascii();
                boolean[] seen = l.reachable(l.entrance().index, d -> true);
                for (RoomPlan r : l.rooms()) {
                    assertTrue("room " + r + " unreachable: " + where, seen[r.index]);
                }
                assertTrue(where, l.solvable());
            }
        }
    }

    @Test
    public void pathRunsFromEntranceThroughBloodToBoss() {
        for (int f = 1; f <= 7; f++) {
            for (long seed = 0; seed < 100; seed++) {
                Layout l = LayoutGenerator.generate(CFG.floor(f), seed);
                var path = l.path();
                assertEquals(l.entrance().index, (int) path.get(0));
                assertEquals(l.boss().index, (int) path.get(path.size() - 1));
                assertEquals(l.blood().index, (int) path.get(path.size() - 2));
                for (int i = 1; i < path.size(); i++) {
                    assertNotNull("path step has a doorway", l.doorBetween(path.get(i - 1), path.get(i)));
                }
                assertEquals(l.size - 1, l.entrance().cz);
                assertEquals(0, l.boss().cz);
                assertEquals(RoomType.ENTRANCE, l.entrance().type);
                assertEquals(RoomType.BLOOD, l.blood().type);
                assertEquals(RoomType.BOSS, l.boss().type);
                assertEquals("the boss room only opens onto the blood room", 1, l.boss().degree());
                assertEquals(DoorKind.BLOOD, l.doorBetween(l.blood().index, l.boss().index).kind);
                assertEquals(1, l.doors().stream().filter(d -> d.kind == DoorKind.BLOOD).count());
            }
        }
    }

    @Test
    public void roomCountsFollowTheFloorConfig() {
        for (int f = 1; f <= 7; f++) {
            DungeonConfig.Floor floor = CFG.floor(f);
            for (long seed = 0; seed < 60; seed++) {
                Layout l = LayoutGenerator.generate(floor, seed);
                assertTrue(l.rooms().size() >= floor.grid() + 2);
                assertTrue(l.rooms().size() <= floor.grid() * floor.grid());
                assertTrue("rooms " + l.rooms().size() + " vs " + floor.rooms(), l.rooms().size() >= Math.min(floor.rooms(), floor.grid() * floor.grid()) - 3);
                assertTrue(l.count(RoomType.PUZZLE) <= floor.puzzles());
                assertTrue(l.count(RoomType.SECRET) <= floor.secretRooms());
                assertTrue(l.count(RoomType.MINIBOSS) <= floor.miniBosses());
                assertTrue(l.count(RoomType.FAIRY) <= 1 && l.count(RoomType.SHOP) <= 1);
            }
        }
        // the biggest floor really is bigger than the smallest
        assertTrue(LayoutGenerator.generate(CFG.floor(7), 1).rooms().size() > LayoutGenerator.generate(CFG.floor(1), 1).rooms().size());
    }

    @Test
    public void secretRoomsAreLeavesBehindSecretDoors() {
        for (int f = 2; f <= 7; f++) {
            for (long seed = 0; seed < 80; seed++) {
                Layout l = LayoutGenerator.generate(CFG.floor(f), seed);
                for (RoomPlan r : l.rooms()) {
                    if (r.type == RoomType.SECRET) {
                        assertEquals(1, r.degree());
                        DoorPlan d = l.doorsOf(r.index).get(0);
                        assertEquals(DoorKind.SECRET, d.kind);
                        assertTrue(r.secrets >= 1);
                    }
                }
            }
        }
    }

    @Test
    public void witherDoorsAlwaysHaveAKeySourceBeforeThem() {
        int withers = 0;
        for (int f = 2; f <= 7; f++) {
            for (long seed = 0; seed < 150; seed++) {
                Layout l = LayoutGenerator.generate(CFG.floor(f), seed);
                long doors = l.doors().stream().filter(d -> d.kind == DoorKind.WITHER).count();
                long carriers = l.rooms().stream().filter(r -> r.keyCarrier).count();
                withers += (int) doors;
                assertTrue("a key carrier per wither door", carriers >= doors);
                assertTrue(l.solvable());
                for (RoomPlan r : l.rooms()) {
                    if (r.keyCarrier) {
                        assertEquals(RoomType.MOB, r.type);
                    }
                }
            }
        }
        assertTrue("some wither doors exist on the higher floors", withers > 50);
    }

    @Test
    public void unsolvableLayoutsAreDetected() {
        // floor 1 has no loops: every doorway of the path is a bridge
        Layout l = LayoutGenerator.generate(CFG.floor(1), 5);
        for (RoomPlan r : l.rooms()) {
            r.keyCarrier = false;
        }
        // a wither door on the very first step of the path and no key anywhere: the boss cannot be reached
        DoorPlan first = l.doorBetween(l.path().get(0), l.path().get(1));
        DoorKind before = first.kind;
        for (DoorPlan d : l.doors()) {
            if (d.kind == DoorKind.WITHER) {
                d.kind = DoorKind.OPEN;
            }
        }
        assertTrue(l.solvable());
        first.kind = DoorKind.WITHER;
        assertFalse(l.solvable());
        // give the entrance room a key: solvable again
        l.entrance().keyCarrier = true;
        assertTrue(l.solvable());
        first.kind = before;
    }

    @Test
    public void secretsAreCounted() {
        Layout l = LayoutGenerator.generate(CFG.floor(5), 99);
        int expected = 0;
        for (RoomPlan r : l.rooms()) {
            expected += r.secrets + (r.type == RoomType.SECRET ? 1 : 0);
        }
        assertEquals(expected, l.totalSecrets());
        assertTrue(l.totalSecrets() > 0);
    }

    @Test
    public void depthIsBreadthFirstFromTheEntrance() {
        Layout l = LayoutGenerator.generate(CFG.floor(5), 3);
        assertEquals(0, l.entrance().depth);
        ArrayDeque<Integer> q = new ArrayDeque<>();
        for (RoomPlan r : l.rooms()) {
            if (r != l.entrance()) {
                assertTrue(r.depth >= 1);
                int best = Integer.MAX_VALUE;
                for (int nb : r.neighbors.values()) {
                    best = Math.min(best, l.room(nb).depth);
                }
                assertEquals(r.depth, best + 1);
            }
        }
        assertTrue(q.isEmpty());
    }
}
