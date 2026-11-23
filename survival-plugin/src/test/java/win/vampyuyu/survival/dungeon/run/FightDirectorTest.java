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
    private SurvivalPlugin plugin;
    private DungeonService svc;
    private World world;
    private PlayerMock alice;
    private DungeonRun run;

    @Before
    public void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(SurvivalPlugin.class);
        win.vampyuyu.survival.LegacyFeatureFixture.enable(plugin);
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
        win.vampyuyu.survival.LegacyFeatureFixture.disable(plugin);
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
        rt.style = FightStyle.WAVES; // the style is picked at random; a Hold room has its own test
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
    public void aHoldRoomOnlyCountsTimeWithSomeoneInTheRingThenOpens() {
        RoomRt rt = run.rooms.values().stream().filter(r -> r.build.plan.type == RoomType.MOB).findFirst().orElseThrow();
        run.fights.firstEnter(rt, alice);
        rt.style = FightStyle.HOLD;
        rt.heldAt = run.tick;
        var center = run.loc(rt.build.spawn);
        svc.ctx.teleport(alice, center.clone().add(20, 0, 0));
        for (int i = 0; i < 20; i++) {
            run.tick += 5;
            run.fights.tick();
        }
        assertEquals("nobody in the ring: the time stands still", 0, rt.held);
        assertEquals(RoomRt.State.ACTIVE, rt.state);
        svc.ctx.teleport(alice, center);
        for (int i = 0; i < svc.cfg().holdSeconds * 4 + 2 && rt.state == RoomRt.State.ACTIVE; i++) {
            run.tick += 5;
            run.fights.tick();
        }
        assertEquals("the time was held", RoomRt.State.CLEARED, rt.state);
    }

    @Test
    public void everyRoomOfTheFloorHasARuntime() {
        assertEquals(run.layout.rooms().size(), run.rooms.size());
        for (RoomRt rt : run.rooms.values()) {
            assertNotNull(rt.build.spawn);
        }
    }

    private RoomRt mobRoom() {
        return run.rooms.values().stream().filter(r -> r.build.plan.type == RoomType.MOB).findFirst().orElseThrow();
    }

    @Test
    public void anAmbushWarnsBeforeAnyoneSpawnsThenBringsTheSameEnemiesInTwoWaves() {
        RoomRt rt = mobRoom();
        run.fights.forcedStyle = FightStyle.AMBUSH;
        long start = run.tick;
        run.fights.firstEnter(rt, alice);
        assertEquals(FightStyle.AMBUSH, rt.style);
        assertEquals(RoomRt.State.ACTIVE, rt.state);
        assertEquals("the warning lasts the configured seconds", start + svc.cfg().ambushTelegraphSeconds * 20L, rt.telegraphUntil);
        assertEquals(svc.cfg().ambushWaves, rt.waves.size());
        int total = rt.waves.stream().mapToInt(java.util.List::size).sum();
        assertEquals("the room keeps its total", total, rt.fightTotal);
        assertTrue(total <= svc.cfg().maxMobsPerRoom);
        int queued = rt.queue.size();
        assertEquals(rt.waves.get(0).size(), queued);
        assertFalse("places are marked for the first wave", rt.spots.isEmpty());
        // during the warning nothing leaves the queue (the mock refuses every spawn, so a spawn attempt empties it)
        while (run.tick + DungeonRun.STEP < rt.telegraphUntil) {
            run.tick += DungeonRun.STEP;
            run.fights.tick();
            assertEquals(queued, rt.queue.size());
            assertEquals(0, rt.wave);
        }
        // after it the enemies come one at a time (the mock refuses every spawn, so each attempt drains one place)
        run.tick = rt.telegraphUntil;
        run.fights.tick();
        assertEquals("one enemy at the end of the warning", queued - 1, rt.queue.size());
        run.tick += DungeonRun.STEP;
        run.fights.tick();
        assertEquals("the next one a beat later, not at once", queued - 1, rt.queue.size());
        int guard = 0;
        while (rt.wave == 0 && guard++ < 500) {
            run.tick += DungeonRun.STEP;
            run.fights.tick();
        }
        assertEquals("the second wave came after the first", 1, rt.wave);
        assertTrue("and it is warned too", rt.telegraphUntil > run.tick);
        int leftOver = rt.queue.size() - rt.waves.get(1).size();
        assertTrue("it came when the first wave was down to the configured few", leftOver >= 0 && leftOver <= svc.cfg().ambushNextWaveLeft);
    }

    @Test
    public void anAmbushRoomIsClearedAfterAllItsWaves() {
        RoomRt rt = mobRoom();
        run.fights.forcedStyle = FightStyle.AMBUSH;
        run.fights.firstEnter(rt, alice);
        for (int i = 0; i < 200 && rt.state == RoomRt.State.ACTIVE; i++) {
            run.tick += DungeonRun.STEP;
            run.fights.tick();
        }
        assertEquals(RoomRt.State.CLEARED, rt.state);
        assertEquals(svc.cfg().ambushWaves - 1, rt.wave);
    }

    @Test
    public void theNumberAliveAtOnceFollowsThePartySize() {
        var c = svc.cfg();
        assertEquals(c.ambushSimultaneousBase, run.fights.ambushCap());
        for (int i = 0; i < 3; i++) {
            var id = java.util.UUID.randomUUID();
            run.players.put(id, new RunPlayer(id, "Extra" + i));
        }
        assertEquals(4, run.partySize());
        assertEquals(Math.min(c.maxMobsPerRoom, c.ambushSimultaneousBase + 3 * c.ambushSimultaneousPerExtra), run.fights.ambushCap());
        assertTrue(run.fights.ambushCap() > c.ambushSimultaneousBase);
    }

    @Test
    public void theDefaultAmbushSettingsMakeAFirstWaveThatFitsASoloPlayer() {
        var c = svc.cfg();
        assertEquals(2, c.ambushTelegraphSeconds);
        assertEquals(3, c.ambushSimultaneousBase);
        assertEquals(2, c.ambushSimultaneousPerExtra);
        assertEquals(2, c.ambushWaves);
    }
}
