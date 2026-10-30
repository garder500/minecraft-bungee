package win.vampyuyu.survival.dungeon;

import static org.junit.Assert.*;

import java.util.HashSet;
import java.util.Set;
import org.bukkit.Bukkit;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import win.vampyuyu.survival.dungeon.gen.BlockBuffer;
import win.vampyuyu.survival.dungeon.gen.BuiltDungeon;
import win.vampyuyu.survival.dungeon.gen.DungeonBuilder;
import win.vampyuyu.survival.dungeon.gen.Geo;
import win.vampyuyu.survival.dungeon.gen.Layout;
import win.vampyuyu.survival.dungeon.gen.LayoutGenerator;
import win.vampyuyu.survival.dungeon.gen.Palette;
import win.vampyuyu.survival.dungeon.gen.Pos;
import win.vampyuyu.survival.dungeon.gen.RoomBuild;
import win.vampyuyu.survival.dungeon.gen.RoomPlan;
import win.vampyuyu.survival.dungeon.gen.RoomType;

public class DungeonBuilderTest {
    private static final DungeonConfig CFG = DungeonConfig.parse(null);

    @Before
    public void setUp() {
        MockBukkit.mock();
    }

    @After
    public void tearDown() {
        MockBukkit.unmock();
    }

    /**
     * MockBukkit's block data does not know the "hanging" property of lanterns (vanilla does), so when the full string
     * is refused only for that property the material alone must still be valid.
     */
    private static void assertValid(String s) {
        try {
            assertNotNull(Bukkit.createBlockData(s));
        } catch (IllegalArgumentException e) {
            if (e.getMessage() != null && e.getMessage().contains("hanging") && s.contains("lantern")) {
                assertNotNull(Bukkit.createBlockData(s.substring(0, s.indexOf('['))));
                return;
            }
            fail("invalid block data '" + s + "': " + e.getMessage());
        }
    }

    private BuiltDungeon build(int floor, long seed) {
        return DungeonBuilder.build(LayoutGenerator.generate(CFG.floor(floor), seed), CFG.floor(floor), 64);
    }

    @Test
    public void buildingIsDeterministic() {
        for (int f : new int[] {1, 4, 7}) {
            BuiltDungeon a = build(f, 77);
            BuiltDungeon b = build(f, 77);
            assertEquals(a.buffer.totalBlocks(), b.buffer.totalBlocks());
            assertEquals(a.buffer.palette(), b.buffer.palette());
            assertEquals(a.spawn(), b.spawn());
            for (int i : a.rooms.keySet()) {
                assertEquals(a.room(i).mobSpawns, b.room(i).mobSpawns);
                assertEquals(a.room(i).secretChests, b.room(i).secretChests);
            }
        }
    }

    @Test
    public void everyBlockStringIsValidBlockData() {
        Set<String> checked = new HashSet<>();
        for (int f = 1; f <= 7; f++) {
            for (long seed = 1; seed <= 12; seed++) {
                for (String s : build(f, seed).buffer.palette()) {
                    if (checked.add(s)) {
                        assertValid(s);
                    }
                }
            }
        }
        assertTrue(checked.size() > 40);
    }

    @Test
    public void everyPaletteResolves() {
        for (String name : DungeonConfig.PALETTES) {
            Palette p = Palette.byName(name);
            assertEquals(name, p.name());
            for (String b : new String[] {p.floor(), p.floorAlt(), p.wall(), p.wallAlt(), p.pillar(), p.ceiling(), p.slab(),
                    p.fence(), p.wallTorch() + "[facing=north]", p.lantern() + "[hanging=true]", p.accent()}) {
                assertValid(b);
            }
        }
    }

    @Test
    public void spawnAndRoomsAreInsideTheGrid() {
        for (int f = 1; f <= 7; f++) {
            BuiltDungeon d = build(f, 5);
            Pos spawn = d.spawn();
            RoomBuild entrance = d.roomAt(spawn.x(), spawn.z());
            assertNotNull(entrance);
            assertEquals(RoomType.ENTRANCE, entrance.plan.type);
            int span = d.layout.size * Geo.CELL;
            for (RoomBuild r : d.rooms.values()) {
                assertTrue(r.minX >= 0 && r.minX + Geo.CELL <= span);
                assertTrue(r.spawn != null && r.contains(r.spawn.x(), r.spawn.z()));
                assertEquals("hidden chests match the plan", r.plan.secrets, r.secretChests.size());
                for (Pos c : r.secretChests) {
                    assertTrue(r.contains(c.x(), c.z()));
                }
            }
            assertEquals(RoomType.BOSS, d.roomAt(d.room(d.layout.boss().index).spawn.x(), d.room(d.layout.boss().index).spawn.z()).plan.type);
        }
    }

    @Test
    public void everyFightRoomHasSpawnPointsAndSecretDoorsHaveLevers() {
        for (int f = 1; f <= 7; f++) {
            for (long seed = 1; seed <= 25; seed++) {
                BuiltDungeon d = build(f, seed);
                for (RoomBuild r : d.rooms.values()) {
                    if (r.plan.type == RoomType.MOB || r.plan.type == RoomType.MINIBOSS || r.plan.type == RoomType.ENTRANCE) {
                        assertTrue("room " + r.plan + " needs spawn points", r.mobSpawns.size() >= 6);
                    }
                    if (r.plan.type == RoomType.PUZZLE) {
                        assertNotNull(r.puzzle);
                    }
                    if (r.plan.type == RoomType.BOSS) {
                        assertNotNull(r.bossSpawn);
                    }
                    if (r.plan.type == RoomType.TRAP) {
                        assertTrue(r.fallY > Integer.MIN_VALUE);
                    }
                }
                for (int i = 0; i < d.layout.doors().size(); i++) {
                    var door = d.layout.doors().get(i);
                    if (door.kind == win.vampyuyu.survival.dungeon.gen.DoorKind.SECRET) {
                        int parent = d.layout.room(door.a).type == RoomType.SECRET ? door.b : door.a;
                        assertTrue("secret door " + i + " has a lever in its neighbour", d.room(parent).hiddenLevers.containsKey(i));
                    }
                }
            }
        }
    }

    @Test
    public void doorwaysAreFourByFourAndOnBothWalls() {
        BuiltDungeon d = build(3, 9);
        for (BuiltDungeon.DoorBuild door : d.doors) {
            assertEquals(2 * Geo.DOOR_W * Geo.DOOR_H, door.blocks().size());
            assertEquals(new HashSet<>(door.blocks()).size(), door.blocks().size());
        }
    }

    @Test
    public void blocksAreSplitPerChunkAndStayInRange() {
        BuiltDungeon d = build(7, 2);
        long sum = 0;
        for (BlockBuffer.ChunkOps ops : d.buffer.chunks()) {
            sum += ops.size();
            assertTrue(ops.chunkX >= 0 && ops.chunkZ >= 0);
            assertTrue(ops.chunkX <= d.layout.size * 2 && ops.chunkZ <= d.layout.size * 2);
            for (int i = 0; i < ops.size(); i++) {
                assertTrue(ops.y(i) >= 63 - 1 && ops.y(i) < 64 + Geo.BOSS_H + 2);
                assertTrue(ops.paletteIndex(i) < d.buffer.palette().size());
            }
        }
        assertEquals(d.buffer.totalBlocks(), sum);
        assertTrue("a big floor is tens of thousands of blocks", sum > 50_000);
    }
}
