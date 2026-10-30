package win.vampyuyu.survival.dungeon.run;

import static org.junit.Assert.*;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import win.vampyuyu.survival.SurvivalPlugin;
import win.vampyuyu.survival.dungeon.DungeonConfig;
import win.vampyuyu.survival.dungeon.DungeonService;
import win.vampyuyu.survival.dungeon.gen.DungeonBuilder;
import win.vampyuyu.survival.dungeon.gen.LayoutGenerator;
import win.vampyuyu.survival.dungeon.gen.Pos;
import win.vampyuyu.survival.dungeon.party.Party;

/**
 * The run state machine and the listener rules, driven directly (MockBukkit has no chunk tickets or real
 * monsters, so the world is a simple mock and nothing is spawned).
 */
public class DungeonRunTest {
    private ServerMock server;
    private SurvivalPlugin plugin;
    private DungeonService svc;
    private World world;
    private PlayerMock alice;
    private PlayerMock bob;
    private DungeonRun run;

    @Before
    public void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(SurvivalPlugin.class);
        svc = plugin.core().require(DungeonService.class);
        world = server.addSimpleWorld("sb_dungeon_501");
        alice = server.addPlayer("Alice");
        bob = server.addPlayer("Bob");
        alice.setGameMode(GameMode.SURVIVAL);
        Party party = svc.parties.ensure(alice.getUniqueId(), "Alice");
        svc.parties.invite(alice.getUniqueId(), "Alice", bob.getUniqueId(), "Bob");
        svc.parties.accept(bob.getUniqueId(), "Bob", "Alice");
        DungeonConfig.Floor floor = svc.cfg().floor(1);
        var layout = LayoutGenerator.generate(floor, 42);
        var built = DungeonBuilder.build(layout, floor, svc.cfg().baseY);
        run = new DungeonRun(svc.ctx, "501", floor, 42, layout, built, party);
        run.world = world;
        svc.runs.register(run);
        svc.parties.setRun(party, "501");
    }

    @After
    public void tearDown() {
        MockBukkit.unmock();
    }

    private void begin() {
        run.begin(world);
    }

    private void running() {
        begin();
        run.state = RunState.RUNNING;
    }

    @Test
    public void beginPutsPlayersInAdventureModeAtTheEntranceAndStartsTheCountdown() {
        begin();
        assertEquals(RunState.COUNTDOWN, run.state());
        assertEquals(GameMode.ADVENTURE, alice.getGameMode());
        assertEquals(world, alice.getWorld());
        Pos spawn = run.built.spawn();
        assertEquals(spawn.x() + 0.5, alice.getLocation().getX(), 0.01);
        assertSame(run, svc.runs.runOf(bob.getUniqueId()));
        assertTrue(run.isParticipant(alice.getUniqueId()));
        assertEquals(2, run.participants().size());
        assertEquals("countdown locks the entrance room", 1, run.roomsVisited);
    }

    @Test
    public void leavingRestoresTheGameModeAndEndsTheRunWhenNobodyIsLeft() {
        begin();
        run.startRunning();
        assertEquals(RunState.RUNNING, run.state());
        run.leave(alice.getUniqueId(), false);
        assertEquals(GameMode.SURVIVAL, alice.getGameMode());
        assertFalse(run.isParticipant(alice.getUniqueId()));
        assertTrue(run.state().active());
        run.leave(bob.getUniqueId(), false);
        assertTrue("an empty run closes itself", run.state() == RunState.CLOSED || run.state() == RunState.CLOSING
            || run.state() == RunState.FAILED);
    }

    @Test
    public void waitingPlayersAreNotTouchedWhenTheRunIsCancelledBeforeItStarts() {
        alice.setGameMode(GameMode.CREATIVE);
        // the run never began: leave() must not force a game mode on somebody still in the hub
        run.leave(alice.getUniqueId(), true);
        assertEquals(GameMode.CREATIVE, alice.getGameMode());
    }

    @Test
    public void quittingMidRunLeavesTheRunAndRestoresTheMode() {
        running();
        server.getPluginManager().callEvent(new PlayerQuitEvent(alice, net.kyori.adventure.text.Component.empty(),
            PlayerQuitEvent.QuitReason.DISCONNECTED));
        assertFalse(run.isParticipant(alice.getUniqueId()));
        assertEquals(GameMode.SURVIVAL, alice.getGameMode());
        assertNull(svc.runs.runOf(alice.getUniqueId()));
        assertNull("quitting also leaves the party", svc.parties.partyOf(alice.getUniqueId()));
    }

    @Test
    public void lethalDamageMakesAGhostWithoutDroppingAnything() {
        running();
        alice.getInventory().addItem(new org.bukkit.inventory.ItemStack(Material.DIAMOND, 5));
        alice.setHealth(2);
        EntityDamageEvent hit = new EntityDamageEvent(alice, EntityDamageEvent.DamageCause.CUSTOM, 50.0);
        server.getPluginManager().callEvent(hit);
        assertTrue("the lethal hit is cancelled", hit.isCancelled());
        RunPlayer rp = run.player(alice.getUniqueId());
        assertTrue(rp.ghost);
        assertEquals(1, rp.ghostDeaths);
        assertEquals(GameMode.SPECTATOR, alice.getGameMode());
        assertEquals(5, alice.getInventory().all(Material.DIAMOND).values().stream().mapToInt(i -> i.getAmount()).sum());
        assertEquals(1, run.deaths.teamDeaths());
    }

    @Test
    public void ghostLivesRunOutAndThePlayerIsEliminated() {
        running();
        RunPlayer rp = run.player(alice.getUniqueId());
        int allowed = svc.cfg().deathsAllowed;
        for (int i = 0; i < allowed; i++) {
            assertTrue(run.deaths.onLethal(alice));
            assertTrue(rp.ghost);
            run.deaths.revive(alice, rp, alice.getLocation(), null);
            assertFalse(rp.ghost);
            rp.invulnerableUntil = 0;
        }
        assertTrue(run.deaths.onLethal(alice));
        assertTrue(rp.eliminated);
        assertFalse(rp.ghost);
        assertEquals(GameMode.SPECTATOR, alice.getGameMode());
        assertEquals(RunState.RUNNING, run.state());
        assertEquals("the other player keeps the run alive", 1, run.aliveOrReturning());
    }

    @Test
    public void revivedPlayersAreProtectedForAMoment() {
        running();
        RunPlayer rp = run.player(alice.getUniqueId());
        run.deaths.onLethal(alice);
        run.deaths.revive(alice, rp, alice.getLocation(), "Bob");
        assertEquals(GameMode.ADVENTURE, alice.getGameMode());
        assertTrue(rp.invulnerableUntil > System.currentTimeMillis());
        EntityDamageEvent hit = new EntityDamageEvent(alice, EntityDamageEvent.DamageCause.CUSTOM, 500.0);
        server.getPluginManager().callEvent(hit);
        assertTrue(hit.isCancelled());
        assertFalse("no new death while protected", rp.ghost);
    }

    @Test
    public void theLastPlayerFallingFailsTheRun() {
        running();
        run.leave(bob.getUniqueId(), false);
        RunPlayer rp = run.player(alice.getUniqueId());
        rp.ghostDeaths = svc.cfg().deathsAllowed;
        run.deaths.onLethal(alice);
        assertTrue(rp.eliminated);
        assertFalse(run.success);
        assertTrue(run.state().over());
    }

    @Test
    public void damageIsCancelledOutsideTheActiveRun() {
        begin(); // countdown
        EntityDamageEvent hit = new EntityDamageEvent(alice, EntityDamageEvent.DamageCause.CUSTOM, 3.0);
        server.getPluginManager().callEvent(hit);
        assertTrue(hit.isCancelled());
    }

    @Test
    public void commandsAreLimitedToTheAllowedList() {
        running();
        PlayerCommandPreprocessEvent tp = new PlayerCommandPreprocessEvent(alice, "/tp 0 0 0");
        server.getPluginManager().callEvent(tp);
        assertTrue(tp.isCancelled());
        PlayerCommandPreprocessEvent namespaced = new PlayerCommandPreprocessEvent(alice, "/minecraft:tp 0 0 0");
        server.getPluginManager().callEvent(namespaced);
        assertTrue(namespaced.isCancelled());
        PlayerCommandPreprocessEvent sneaky = new PlayerCommandPreprocessEvent(alice, "/essentials:party list");
        server.getPluginManager().callEvent(sneaky);
        assertTrue("another plugin's namespace is never trusted", sneaky.isCancelled());
        PlayerCommandPreprocessEvent party = new PlayerCommandPreprocessEvent(alice, "/party list");
        server.getPluginManager().callEvent(party);
        assertFalse(party.isCancelled());
        PlayerCommandPreprocessEvent own = new PlayerCommandPreprocessEvent(alice, "/vampyuyusurvival:dungeon leave");
        server.getPluginManager().callEvent(own);
        assertFalse(own.isCancelled());
        PlayerCommandPreprocessEvent upper = new PlayerCommandPreprocessEvent(alice, "/TP 1 1 1");
        server.getPluginManager().callEvent(upper);
        assertTrue("case does not matter", upper.isCancelled());
    }

    @Test
    public void adminsBypassTheCommandLock() {
        running();
        alice.setOp(true);
        PlayerCommandPreprocessEvent tp = new PlayerCommandPreprocessEvent(alice, "/tp 0 0 0");
        server.getPluginManager().callEvent(tp);
        assertFalse(tp.isCancelled());
    }

    @Test
    public void teleportsAreBlockedInsideButTheFeatureItselfPassesThrough() {
        running();
        Location away = new Location(server.addSimpleWorld("elsewhere"), 0, 70, 0);
        PlayerTeleportEvent pearl = new PlayerTeleportEvent(alice, alice.getLocation(), away,
            PlayerTeleportEvent.TeleportCause.ENDER_PEARL);
        server.getPluginManager().callEvent(pearl);
        assertTrue(pearl.isCancelled());
        PlayerTeleportEvent plugin = new PlayerTeleportEvent(alice, alice.getLocation(), away,
            PlayerTeleportEvent.TeleportCause.PLUGIN);
        server.getPluginManager().callEvent(plugin);
        assertTrue(plugin.isCancelled());
        PlayerTeleportEvent spectate = new PlayerTeleportEvent(alice, alice.getLocation(), bob.getLocation(),
            PlayerTeleportEvent.TeleportCause.SPECTATE);
        server.getPluginManager().callEvent(spectate);
        assertFalse("ghosts may spectate a teammate in the same dungeon", spectate.isCancelled());
        assertTrue(svc.ctx.teleport(alice, away));
        assertEquals("the feature's own teleport is let through", "elsewhere", alice.getWorld().getName());
    }

    @Test
    public void outsidersCannotTeleportIntoADungeonWorld() {
        PlayerMock stranger = server.addPlayer("Stranger");
        Location in = new Location(world, 3, 70, 3);
        PlayerTeleportEvent event = new PlayerTeleportEvent(stranger, stranger.getLocation(), in,
            PlayerTeleportEvent.TeleportCause.COMMAND);
        server.getPluginManager().callEvent(event);
        assertTrue(event.isCancelled());
    }

    @Test
    public void dungeonBlocksCannotBeBroken() {
        running();
        var block = world.getBlockAt(10, 64, 10);
        block.setType(Material.STONE);
        BlockBreakEvent event = new BlockBreakEvent(block, alice);
        server.getPluginManager().callEvent(event);
        assertTrue(event.isCancelled());
        alice.setOp(true);
        alice.setGameMode(GameMode.CREATIVE);
        BlockBreakEvent admin = new BlockBreakEvent(block, alice);
        server.getPluginManager().callEvent(admin);
        assertFalse("an admin in creative may still edit", admin.isCancelled());
    }

    @Test
    public void scoreInputsCountWhatHappened() {
        running();
        run.mobsSpawned = 10;
        run.mobsKilled = 10;
        run.puzzlesSolved = run.layout.puzzleCount();
        run.secretsFound = run.layout.totalSecrets();
        var in = run.finale.inputs(true);
        assertEquals(run.layout.rooms().size(), in.roomsTotal());
        assertEquals(run.layout.puzzleCount(), in.puzzlesSolved());
        assertEquals(0, in.deaths());
        assertTrue(in.bossKilled());
    }

    @Test
    public void finishingTwiceScoresOnce() {
        running();
        run.mobsSpawned = 4;
        run.mobsKilled = 4;
        run.finish(true, "");
        assertEquals(RunState.SUCCESS, run.state());
        assertNotNull(run.result);
        var first = run.result;
        run.finish(false, "again");
        assertSame("the second finish is ignored", first, run.result);
        assertTrue(run.success);
    }

    @Test
    public void aFailedRunPaysNothingButCountsAsPlayed() {
        running();
        run.finish(false, "test");
        assertEquals(RunState.FAILED, run.state());
        assertTrue(run.result.drops().isEmpty());
        var data = plugin.core().profiles().section(alice.getUniqueId(), "dungeon",
            win.vampyuyu.survival.dungeon.DungeonData.class, win.vampyuyu.survival.dungeon.DungeonData::new);
        assertEquals(1, data.totalRuns);
        assertEquals(0, data.totalClears);
        assertFalse(data.cleared(1));
    }

    @Test
    public void aWonRunClearsTheFloorAndPaysEveryone() {
        running();
        run.mobsSpawned = 4;
        run.mobsKilled = 4;
        run.finish(true, "");
        for (PlayerMock p : new PlayerMock[] {alice, bob}) {
            var data = plugin.core().profiles().section(p.getUniqueId(), "dungeon",
                win.vampyuyu.survival.dungeon.DungeonData.class, win.vampyuyu.survival.dungeon.DungeonData::new);
            assertTrue(data.cleared(1));
            assertEquals(1, data.totalClears);
            assertTrue("rewards were handed out", run.result.drops().containsKey(p.getUniqueId()));
        }
        assertTrue(run.result.coins() > 0);
    }

    @Test
    public void destroyingARunReleasesTheParty() {
        running();
        svc.runs.abort(run, "test");
        assertNull(svc.runs.runOf(alice.getUniqueId()));
        assertNull(svc.parties.partyOf(alice.getUniqueId()).runId());
        assertEquals(GameMode.SURVIVAL, alice.getGameMode());
        assertEquals(0, svc.runs.all().size());
    }
}
