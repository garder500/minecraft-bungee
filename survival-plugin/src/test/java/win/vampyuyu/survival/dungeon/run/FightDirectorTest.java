package win.vampyuyu.survival.dungeon.run;

import static org.junit.Assert.*;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import win.vampyuyu.survival.SurvivalPlugin;
import win.vampyuyu.survival.dungeon.DungeonService;
import win.vampyuyu.survival.dungeon.gen.DoorKind;
import win.vampyuyu.survival.dungeon.gen.DungeonBuilder;
import win.vampyuyu.survival.dungeon.gen.LayoutGenerator;
import win.vampyuyu.survival.dungeon.gen.Pos;
import win.vampyuyu.survival.dungeon.gen.RoomBuild;
import win.vampyuyu.survival.dungeon.gen.RoomType;
import win.vampyuyu.survival.dungeon.party.Party;

/** Doors, keys, secrets, chests and fights of a run on a mock world (the blocks are placed by hand). */
public class FightDirectorTest {
    private ServerMock server;
    private DungeonService svc;
    private World world;
    private PlayerMock alice;
    private DungeonRun run;

    @Before
    public void setUp() {
        server = MockBukkit.mock();
        SurvivalPlugin plugin = MockBukkit.load(SurvivalPlugin.class);
        svc = plugin.core().require(DungeonService.class);
        world = server.addSimpleWorld("sb_dungeon_601");
        alice = server.addPlayer("Alice");
        Party party = svc.parties.ensure(alice.getUniqueId(), "Alice");
        var floor = svc.cfg().floor(4);
        var layout = LayoutGenerator.generate(floor, 9);
        var built = DungeonBuilder.build(layout, floor, svc.cfg().baseY);
        run = new DungeonRun(svc.ctx, "601", floor, 9, layout, built, party);
        run.world = world;
        svc.runs.register(run);
        run.begin(world);
        run.state = RunState.RUNNING;
        // put the door blocks in the mock world like the applier would
        for (var d : built.doors) {
            for (Pos p : d.blocks()) {
                world.getBlockAt(p.x(), p.y(), p.z()).setType(Material.AIR);
            }
        }
    }

    @After
    public void tearDown() {
        MockBukkit.unmock();
    }

    private Block block(Pos p) {
        return world.getBlockAt(p.x(), p.y(), p.z());
    }

    private int doorOfKind(DoorKind kind) {
        for (int i = 0; i < run.layout.doors().size(); i++) {
            if (run.layout.doors().get(i).kind == kind) {
                return i;
            }
        }
        fail("no " + kind + " door on this floor");
        return -1;
    }

    @Test
    public void everyDoorBlockMapsBackToItsDoor() {
        for (var d : run.built.doors) {
            for (Pos p : d.blocks()) {
                assertEquals(d.index(), run.fights.doorAt(block(p)));
            }
        }
        assertEquals(-1, run.fights.doorAt(world.getBlockAt(-5, 10, -5)));
    }

    @Test
    public void lockingBarsTheDoorwaysAndUnlockingOpensThem() {
        int room = run.layout.entrance().index;
        var plain = run.built.doors.stream().filter(d -> d.plan().touches(room) && d.plan().kind == DoorKind.OPEN).toList();
        assertFalse(plain.isEmpty());
        run.fights.unlockRoom(room); // from the countdown lock
        assertEquals(Material.AIR, block(plain.get(0).blocks().get(0)).getType());
        run.fights.lockRoom(room);
        assertEquals(Material.IRON_BARS, block(plain.get(0).blocks().get(0)).getType());
        run.fights.lockRoom(room);
        run.fights.unlockRoom(room);
        assertEquals("two locks need two unlocks", Material.IRON_BARS, block(plain.get(0).blocks().get(0)).getType());
        run.fights.unlockRoom(room);
        assertEquals(Material.AIR, block(plain.get(0).blocks().get(0)).getType());
    }

    @Test
    public void aWitherDoorSpendsOneKeyAndStaysShutWithoutOne() {
        int i = doorOfKind(DoorKind.WITHER);
        Block b = block(run.built.doors.get(i).blocks().get(0));
        b.setType(Material.COAL_BLOCK);
        run.witherKeys = 0;
        assertTrue("the click is consumed", run.fights.clickDoor(alice, b));
        assertFalse(run.doorOpen[i]);
        assertEquals(Material.COAL_BLOCK, b.getType());
        run.witherKeys = 2;
        run.fights.clickDoor(alice, b);
        assertTrue(run.doorOpen[i]);
        assertEquals(Material.AIR, b.getType());
        assertEquals(1, run.witherKeys);
        run.fights.clickDoor(alice, b);
        assertEquals("an open door costs nothing", 1, run.witherKeys);
    }

    @Test
    public void theBloodDoorNeedsTheForgedKey() {
        int i = doorOfKind(DoorKind.BLOOD);
        Block b = block(run.built.doors.get(i).blocks().get(0));
        b.setType(Material.RED_CONCRETE);
        run.fights.clickDoor(alice, b);
        assertFalse(run.doorOpen[i]);
        run.fights.bloodKey = true;
        run.fights.clickDoor(alice, b);
        assertTrue(run.doorOpen[i]);
        assertEquals(Material.AIR, b.getType());
    }

    @Test
    public void bloodKeyIsForgedWhenTheLastGuardDies() {
        run.fights.spawnInitialGuards(); // starts the hunt (the mock cannot spawn them, so the totals are set by hand)
        run.fights.guardsTotal = 2;
        run.fights.guardsSpawned = 2;
        var a = java.util.UUID.randomUUID();
        var b = java.util.UUID.randomUUID();
        run.fights.guards.add(a);
        run.fights.guards.add(b);
        run.fights.guardDied(a);
        assertFalse(run.fights.bloodKey);
        run.fights.guardDied(b);
        assertTrue(run.fights.bloodKey);
        run.fights.guardDied(b);
        assertEquals("a duplicate death is ignored", 2, run.fights.guardKills);
    }

    @Test
    public void aGuardThatCannotSpawnDoesNotBlockTheKeyForever() {
        // MockBukkit cannot spawn monsters: every guard fails, so the requirement shrinks to what exists (none)
        run.fights.spawnInitialGuards();
        assertEquals(0, run.fights.guardsSpawned);
        run.fights.firstEnter(run.rooms.get(run.layout.blood().index), alice); // the blood room calls the rest
        run.fights.tick();
        assertTrue(run.fights.bloodKey);
    }

    @Test
    public void aSecretLeverOpensItsDoorAndCountsOnce() {
        RoomBuild withLever = null;
        for (RoomBuild rb : run.built.rooms.values()) {
            if (!rb.hiddenLevers.isEmpty()) {
                withLever = rb;
            }
        }
        assertNotNull("floor 4 has secret rooms", withLever);
        int doorIndex = withLever.hiddenLevers.keySet().iterator().next();
        Pos lever = withLever.hiddenLevers.get(doorIndex);
        Block b = block(lever);
        b.setType(Material.LEVER);
        assertTrue(run.fights.clickLever(alice, b));
        assertEquals(1, run.secretsFound);
        assertTrue(run.doorOpen[doorIndex]);
        assertTrue(run.fights.clickLever(alice, b));
        assertEquals("pulling again is not a new secret", 1, run.secretsFound);
        b.setType(Material.LEVER);
        assertFalse("an ordinary lever is not ours", run.fights.clickLever(alice, world.getBlockAt(1, 1, 1)));
    }

    @Test
    public void aSecretChestPaysOnceAndCountsAsASecret() {
        Pos chest = null;
        for (RoomBuild rb : run.built.rooms.values()) {
            if (!rb.secretChests.isEmpty()) {
                chest = rb.secretChests.get(0);
            }
        }
        assertNotNull(chest);
        Block b = block(chest);
        b.setType(Material.CHEST);
        assertTrue(run.fights.clickChest(alice, b));
        assertEquals(1, run.secretsFound);
        assertEquals("the chest is consumed", Material.AIR, b.getType());
        assertEquals(1, run.player(alice.getUniqueId()).secrets);
        b.setType(Material.CHEST);
        assertTrue(run.fights.clickChest(alice, b));
        assertEquals("no second payout", 1, run.secretsFound);
        assertFalse("a stray chest is not ours", run.fights.clickChest(alice, world.getBlockAt(2, 2, 2)));
    }

    @Test
    public void aGhostCannotOpenChests() {
        Pos chest = null;
        for (RoomBuild rb : run.built.rooms.values()) {
            if (!rb.secretChests.isEmpty()) {
                chest = rb.secretChests.get(0);
            }
        }
        Block b = block(chest);
        b.setType(Material.CHEST);
        run.player(alice.getUniqueId()).ghost = true;
        assertTrue(run.fights.clickChest(alice, b));
        assertEquals(0, run.secretsFound);
        assertEquals(Material.CHEST, b.getType());
    }

    @Test
    public void theRewardChestOfAPuzzleStaysLockedUntilSolved() {
        RoomBuild puzzle = null;
        for (RoomBuild rb : run.built.rooms.values()) {
            if (rb.plan.type == RoomType.PUZZLE) {
                puzzle = rb;
            }
        }
        assertNotNull(puzzle);
        Block b = block(puzzle.puzzle.chest());
        b.setType(Material.CHEST);
        assertTrue(run.fights.clickChest(alice, b));
        assertEquals("locked: still a chest", Material.CHEST, b.getType());
        run.rooms.get(puzzle.plan.index).done = true;
        run.fights.clickChest(alice, b);
        assertEquals(Material.AIR, b.getType());
    }

    @Test
    public void aMobRoomLocksThenClearsAndKeyRoomsGiveAWitherKey() {
        RoomRt rt = null;
        for (RoomRt r : run.rooms.values()) {
            if (r.build.plan.type == RoomType.MOB && r.build.plan.keyCarrier) {
                rt = r;
            }
        }
        if (rt == null) {
            for (RoomRt r : run.rooms.values()) {
                if (r.build.plan.type == RoomType.MOB) {
                    rt = r;
                    r.build.plan.keyCarrier = true;
                }
            }
        }
        assertNotNull(rt);
        int keysBefore = run.witherKeys;
        run.fights.firstEnter(rt, alice);
        assertEquals("every spawn failed on the mock, so the queue drains at once", RoomRt.State.ACTIVE, rt.state);
        for (int i = 0; i < 40 && rt.state == RoomRt.State.ACTIVE; i++) {
            run.tick += 5;
            run.fights.tick();
        }
        assertEquals(RoomRt.State.CLEARED, rt.state);
        assertTrue(rt.done);
        assertEquals(1, run.fightRoomsCleared);
        assertEquals(keysBefore + 1, run.witherKeys);
    }

    @Test
    public void everyRoomOfTheFloorHasARuntime() {
        assertEquals(run.layout.rooms().size(), run.rooms.size());
        for (RoomRt rt : run.rooms.values()) {
            assertNotNull(rt.build.spawn);
        }
    }
}
