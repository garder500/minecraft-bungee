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
        server.addSimpleWorld("main_world"); // the fallback exit when there is no island feature
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
        assertEquals("an empty run closes at once, not after the close delay", RunState.CLOSED, run.state());
        assertEquals("its slot is free again", 0, svc.runs.all().size());
        assertEquals(0, svc.runs.load());
        assertFalse("the party can be used again", svc.parties.partyOf(alice.getUniqueId()).inRun());
        assertNull(svc.parties.partyOf(alice.getUniqueId()).runId());
    }

    @Test
    public void aSoloPlayerWhoLeavesFreesTheSlotAndTheParty() {
        run.leave(bob.getUniqueId(), false); // bob goes first, still GENERATING
        begin();
        run.startRunning();
        assertTrue(svc.runs.leave(alice));
        assertEquals(RunState.CLOSED, run.state());
        assertEquals(0, svc.runs.load());
        assertNull(svc.runs.runOf(alice.getUniqueId()));
        assertFalse(svc.parties.partyOf(alice.getUniqueId()).inRun());
    }

    @Test
    public void aPlayerWhoLeftWhileTheCryptWasBeingBuiltIsNeverTeleportedIn() {
        bob.setGameMode(GameMode.CREATIVE);
        World before = bob.getWorld();
        assertEquals(RunState.GENERATING, run.state());
        assertTrue(svc.runs.leave(bob));
        begin();
        assertEquals("not teleported into the crypt", before, bob.getWorld());
        assertEquals("game mode untouched", GameMode.CREATIVE, bob.getGameMode());
        assertFalse(run.isParticipant(bob.getUniqueId()));
        assertFalse(run.player(bob.getUniqueId()).prepared);
        assertEquals(1, run.participants().size());
        assertEquals(world, alice.getWorld());
        assertFalse("the dungeon API does not call them a participant", new win.vampyuyu.survival.dungeon.DungeonApiImpl(svc.runs).inDungeon(bob));
    }

    @Test
    public void everybodyLeavingBeforeTheCryptOpensClosesTheRunAtOnce() {
        svc.runs.leave(bob);
        svc.runs.leave(alice);
        begin();
        assertEquals(RunState.CLOSED, run.state());
        assertEquals(0, svc.runs.all().size());
    }

    private static boolean shows(PlayerMock p, net.kyori.adventure.bossbar.BossBar bar) {
        for (net.kyori.adventure.bossbar.BossBar b : p.activeBossBars()) {
            if (b == bar) {
                return true;
            }
        }
        return false;
    }

    @Test
    public void theBuildProgressBarDisappearsWhenSomeoneLeavesOrTheRunIsAborted() {
        var bar = net.kyori.adventure.bossbar.BossBar.bossBar(net.kyori.adventure.text.Component.text("Building"), 0.4f,
            net.kyori.adventure.bossbar.BossBar.Color.PURPLE, net.kyori.adventure.bossbar.BossBar.Overlay.PROGRESS);
        svc.runs.progressBars().put("501", bar);
        alice.showBossBar(bar);
        bob.showBossBar(bar);
        assertTrue(shows(alice, bar));
        svc.runs.leave(bob);
        assertFalse("the leaver's bar is gone", shows(bob, bar));
        assertTrue(shows(alice, bar));
        svc.runs.abort(run, "admin");
        assertFalse("everybody's bar is gone, also in the hub", shows(alice, bar));
        assertTrue(svc.runs.progressBars().isEmpty());
    }

    @Test
    public void leavingARunInProgressCountsAsAFailedRunForTheLeaver() {
        running();
        run.player(bob.getUniqueId()).kills = 4;
        run.player(bob.getUniqueId()).secrets = 2;
        run.leave(bob.getUniqueId(), false);
        var data = plugin.core().profiles().section(bob.getUniqueId(), "dungeon",
            win.vampyuyu.survival.dungeon.DungeonData.class, win.vampyuyu.survival.dungeon.DungeonData::new);
        assertEquals(1, data.totalRuns);
        assertEquals(0, data.totalClears);
        assertEquals(4, data.totalKills);
        assertEquals(2, data.totalSecrets);
        run.finish(false, "over");
        var again = plugin.core().profiles().section(bob.getUniqueId(), "dungeon",
            win.vampyuyu.survival.dungeon.DungeonData.class, win.vampyuyu.survival.dungeon.DungeonData::new);
        assertEquals("recorded once, not again by the finale", 1, again.totalRuns);
    }

    @Test
    public void leavingBeforeTheCryptOpensIsNotARun() {
        svc.runs.leave(bob);
        var data = plugin.core().profiles().section(bob.getUniqueId(), "dungeon",
            win.vampyuyu.survival.dungeon.DungeonData.class, win.vampyuyu.survival.dungeon.DungeonData::new);
        assertEquals(0, data.totalRuns);
    }

    @Test
    public void closingTheRunTwiceDoesNotCountTheRunTwice() {
        running();
        svc.runs.abort(run, "one");
        svc.runs.abort(run, "two");
        var data = plugin.core().profiles().section(alice.getUniqueId(), "dungeon",
            win.vampyuyu.survival.dungeon.DungeonData.class, win.vampyuyu.survival.dungeon.DungeonData::new);
        assertEquals(1, data.totalRuns);
    }

    @Test
    public void mvpIsTheBestContributorOfAtLeastTwoPlayers() {
        running();
        RunPlayer a = run.player(alice.getUniqueId());
        RunPlayer b = run.player(bob.getUniqueId());
        assertNull("nothing happened yet", Finale.mvp(run.players.values()));
        a.kills = 10;
        b.kills = 3;
        b.secrets = 3;
        assertSame(b, Finale.mvp(run.players.values()));
        a.totalDeaths = 0;
        b.totalDeaths = 4;
        assertSame("deaths cost points", a, Finale.mvp(run.players.values()));
        b.left = true;
        assertNull("a lone survivor is not an MVP", Finale.mvp(run.players.values()));
    }

    // ------------------------------------------------------------------ leaving the dungeon world

    /** An island feature whose hub teleport reports success but has not moved the player yet (teleportAsync). */
    private final java.util.concurrent.atomic.AtomicInteger hubCalls = new java.util.concurrent.atomic.AtomicInteger();

    private void asyncHub(boolean moves) {
        win.vampyuyu.survival.api.IslandApi fake = (win.vampyuyu.survival.api.IslandApi) java.lang.reflect.Proxy.newProxyInstance(
            getClass().getClassLoader(), new Class<?>[] {win.vampyuyu.survival.api.IslandApi.class}, (proxy, method, args) -> {
                if (method.getName().equals("teleportHub")) {
                    hubCalls.incrementAndGet();
                    if (moves) {
                        ((org.bukkit.entity.Player) args[0]).teleport(server.getWorld("main_world").getSpawnLocation());
                    }
                    return true;
                }
                return switch (method.getReturnType().getName()) {
                    case "boolean" -> false;
                    case "java.util.Optional" -> java.util.Optional.empty();
                    case "java.util.Collection" -> java.util.List.of();
                    default -> null;
                };
            });
        svc.ctx.override(win.vampyuyu.survival.api.IslandApi.class, fake);
    }

    @Test
    public void aHubTeleportThatIsStillInFlightGetsNoCompetingFallback() {
        asyncHub(false);
        running();
        RunManager.sendOut(svc.ctx, alice);
        assertEquals(1, hubCalls.get());
        assertEquals("still in the dungeon: the async teleport has not landed, no second teleport yet", world, alice.getWorld());
        RunManager.sendOut(svc.ctx, alice);
        assertEquals("a second request inside the grace period asks nothing", 1, hubCalls.get());
        assertEquals(world, alice.getWorld());
        server.getScheduler().performTicks(60);
        assertNotEquals("the fallback ran after the grace period", world, alice.getWorld());
        assertFalse(win.vampyuyu.survival.core.SkyblockWorlds.isDungeon(alice.getWorld().getName()));
    }

    @Test
    public void aHubTeleportThatLandsLeavesNothingForTheFallback() {
        asyncHub(true);
        running();
        RunManager.sendOut(svc.ctx, alice);
        World landed = alice.getWorld();
        assertNotEquals(world, landed);
        server.getScheduler().performTicks(60);
        assertEquals(landed, alice.getWorld());
        assertEquals(1, hubCalls.get());
    }

    @Test
    public void quitAndShutdownMoveThePlayerAtOnceWithoutWaitingForTheHub() {
        asyncHub(false);
        running();
        RunManager.sendOut(svc.ctx, alice, true);
        assertEquals("immediate exits skip the hub request", 0, hubCalls.get());
        assertNotEquals(world, alice.getWorld());
    }

    @Test
    public void destroyingARunWithAnAsyncHubEventuallyMovesEveryone() {
        asyncHub(false);
        running();
        svc.runs.abort(run, "test");
        server.getScheduler().performTicks(400);
        assertFalse(win.vampyuyu.survival.core.SkyblockWorlds.isDungeon(alice.getWorld().getName()));
        assertFalse(win.vampyuyu.survival.core.SkyblockWorlds.isDungeon(bob.getWorld().getName()));
        assertEquals(RunState.CLOSED, run.state());
    }

    // ------------------------------------------------------------------ which Crypt Lord

    private int bossSpawns(int externalFromFloor) {
        var calls = new java.util.concurrent.atomic.AtomicInteger();
        win.vampyuyu.survival.api.BossApi fake = (win.vampyuyu.survival.api.BossApi) java.lang.reflect.Proxy.newProxyInstance(
            getClass().getClassLoader(), new Class<?>[] {win.vampyuyu.survival.api.BossApi.class}, (proxy, method, args) -> {
                switch (method.getName()) {
                    case "spawn" -> {
                        calls.incrementAndGet();
                        return true;
                    }
                    case "bossIds" -> {
                        return java.util.List.of("crypt_lord");
                    }
                    default -> {
                        return null;
                    }
                }
            });
        svc.ctx.override(win.vampyuyu.survival.api.BossApi.class, fake);
        svc.ctx.cfg.bossExternalFromFloor = externalFromFloor;
        running();
        run.boss.onFirstEnter(alice);
        server.getScheduler().performTicks(110);
        return calls.get();
    }

    @Test
    public void theBossFeatureIsNotUsedUnlessTheFloorAsksForIt() {
        assertEquals("default 0: always the scripted, floor scaled Crypt Lord", 0, bossSpawns(0));
    }

    @Test
    public void theBossFeatureSpawnsTheCryptLordFromTheConfiguredFloor() {
        assertEquals(1, bossSpawns(1));
        assertTrue(run.boss.isExternal());
    }

    @Test
    public void aLowerFloorKeepsTheScriptedBossEvenWhenTheBossFeatureExists() {
        assertEquals("floor 1 < from-floor 3", 0, bossSpawns(3));
    }

    // ------------------------------------------------------------------ strays

    @Test
    public void aStrayInADungeonWorldCanLeaveWithTheCommandAndIsNotInTheDungeonForTheRouter() {
        PlayerMock stray = server.addPlayer("Stray");
        stray.setGameMode(GameMode.ADVENTURE);
        svc.ctx.teleport(stray, new Location(world, 0, 70, 0));
        assertEquals(world, stray.getWorld());
        assertTrue(svc.runs.isStray(stray));
        var api = new win.vampyuyu.survival.dungeon.DungeonApiImpl(svc.runs);
        assertFalse("strays are left to the join router", api.inDungeon(stray));
        server.dispatchCommand(stray, "dungeon leave");
        assertNotEquals(world, stray.getWorld());
        assertEquals("the adventure mode of the crash is undone", GameMode.SURVIVAL, stray.getGameMode());
        assertFalse(svc.runs.isStray(stray));
    }

    @Test
    public void participantsAreInTheDungeonForTheApi() {
        begin();
        var api = new win.vampyuyu.survival.dungeon.DungeonApiImpl(svc.runs);
        assertTrue(api.inDungeon(alice));
        assertFalse(api.inDungeon(null));
        run.leave(alice.getUniqueId(), false);
        assertFalse(api.inDungeon(alice));
    }

    @Test
    public void aCommandOutsideAnyDungeonStillSaysNotInARun() {
        PlayerMock lobby = server.addPlayer("Lobby");
        assertFalse(svc.runs.sendOutStray(lobby));
        assertFalse(svc.runs.isStray(lobby));
    }

    // ------------------------------------------------------------------ damage scaling order

    /** Stands in for the items feature: its damage formula replaces the event damage at HIGH. */
    private void itemsFormula(double damage) {
        server.getPluginManager().registerEvents(new org.bukkit.event.Listener() {
            @org.bukkit.event.EventHandler(priority = org.bukkit.event.EventPriority.HIGH)
            public void on(org.bukkit.event.entity.EntityDamageByEntityEvent e) {
                e.setDamage(damage);
            }
        }, plugin);
    }

    private org.bukkit.entity.Zombie mob(double damageMult) {
        var z = world.spawn(new Location(world, 3, 70, 3), org.bukkit.entity.Zombie.class);
        z.getPersistentDataContainer().set(svc.ctx.keys.run, org.bukkit.persistence.PersistentDataType.STRING, "501");
        z.getPersistentDataContainer().set(svc.ctx.keys.damage, org.bukkit.persistence.PersistentDataType.DOUBLE, damageMult);
        return z;
    }

    @Test
    public void floorDamageMultiplierRunsAfterTheItemsFormula() {
        running();
        itemsFormula(10.0);
        alice.setHealth(20);
        var hit = new org.bukkit.event.entity.EntityDamageByEntityEvent(mob(3.0), alice,
            EntityDamageEvent.DamageCause.ENTITY_ATTACK, 4.0);
        server.getPluginManager().callEvent(hit);
        assertEquals("items formula (10) first, then the floor multiplier (x3)", 30.0, hit.getDamage(), 0.001);
    }

    @Test
    public void aHitThatOnlyBecomesLethalAfterTheMultiplierMakesAGhost() {
        running();
        itemsFormula(10.0);
        alice.setHealth(20);
        var hit = new org.bukkit.event.entity.EntityDamageByEntityEvent(mob(3.0), alice,
            EntityDamageEvent.DamageCause.ENTITY_ATTACK, 4.0);
        server.getPluginManager().callEvent(hit);
        assertTrue("30 damage against 20 hearts of health is lethal and cancelled into a ghost", hit.isCancelled());
        assertTrue(run.player(alice.getUniqueId()).ghost);
    }

    @Test
    public void playersAreDividedByTheHealthFactorAfterTheItemsFormula() {
        running();
        itemsFormula(100.0);
        var z = mob(1.0);
        z.getPersistentDataContainer().set(svc.ctx.keys.factor, org.bukkit.persistence.PersistentDataType.DOUBLE, 4.0);
        var hit = new org.bukkit.event.entity.EntityDamageByEntityEvent(alice, z,
            EntityDamageEvent.DamageCause.ENTITY_ATTACK, 5.0);
        server.getPluginManager().callEvent(hit);
        assertEquals(25.0, hit.getDamage(), 0.001);
    }

    @Test
    public void friendlyFireStaysCancelled() {
        running();
        var hit = new org.bukkit.event.entity.EntityDamageByEntityEvent(alice, bob, EntityDamageEvent.DamageCause.ENTITY_ATTACK, 5.0);
        server.getPluginManager().callEvent(hit);
        assertTrue(hit.isCancelled());
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
        assertNotEquals("the quitter is moved out of the dungeon world, not on the next login", world, alice.getWorld());
        assertFalse(win.vampyuyu.survival.core.SkyblockWorlds.isDungeon(alice.getWorld().getName()));
    }

    @Test
    public void quittingWhileGhostRestoresTheModeAndRemovesTheBody() {
        running();
        run.deaths.onLethal(alice);
        assertEquals(GameMode.SPECTATOR, alice.getGameMode());
        server.getPluginManager().callEvent(new PlayerQuitEvent(alice, net.kyori.adventure.text.Component.empty(),
            PlayerQuitEvent.QuitReason.DISCONNECTED));
        assertEquals(GameMode.SURVIVAL, alice.getGameMode());
        assertNull(run.player(alice.getUniqueId()).bodyStand);
    }

    @Test
    public void armorStandsCannotBeStrippedOrPosed() {
        running();
        org.bukkit.entity.ArmorStand stand = world.spawn(new Location(world, 5, 70, 5), org.bukkit.entity.ArmorStand.class);
        var manipulate = new org.bukkit.event.player.PlayerArmorStandManipulateEvent(alice, stand,
            new org.bukkit.inventory.ItemStack(Material.AIR), new org.bukkit.inventory.ItemStack(Material.LEATHER_CHESTPLATE),
            org.bukkit.inventory.EquipmentSlot.CHEST);
        server.getPluginManager().callEvent(manipulate);
        assertTrue("no free chestplate or head from a ghost body", manipulate.isCancelled());
        var at = new org.bukkit.event.player.PlayerInteractAtEntityEvent(alice, stand, new org.bukkit.util.Vector(0, 1, 0));
        server.getPluginManager().callEvent(at);
        assertTrue(at.isCancelled());
        alice.setOp(true);
        var admin = new org.bukkit.event.player.PlayerArmorStandManipulateEvent(alice, stand,
            new org.bukkit.inventory.ItemStack(Material.AIR), new org.bukkit.inventory.ItemStack(Material.LEATHER_CHESTPLATE),
            org.bukkit.inventory.EquipmentSlot.CHEST);
        server.getPluginManager().callEvent(admin);
        assertFalse(admin.isCancelled());
    }

    @Test
    public void orphanCleanupKeepsLiveRunsAndLoadedWorlds() throws Exception {
        java.nio.file.Path root = java.nio.file.Files.createTempDirectory("dungeon-orphans");
        try {
            for (String n : new String[] {"sb_dungeon_501", "sb_dungeon_700", "sb_dungeon_701", "world", "sb_dungeon_x"}) {
                java.nio.file.Files.createDirectories(root.resolve(n).resolve("region"));
                java.nio.file.Files.writeString(root.resolve(n).resolve("region").resolve("r.0.0.mca"), "x");
            }
            int removed = svc.runs.deleteOrphans(root, java.util.List.of("sb_dungeon_701"));
            assertEquals(1, removed);
            assertTrue("the live run 501 stays", java.nio.file.Files.exists(root.resolve("sb_dungeon_501")));
            assertTrue("a loaded world stays", java.nio.file.Files.exists(root.resolve("sb_dungeon_701")));
            assertFalse(java.nio.file.Files.exists(root.resolve("sb_dungeon_700")));
            assertTrue("other folders are never touched", java.nio.file.Files.exists(root.resolve("world")));
            assertTrue(java.nio.file.Files.exists(root.resolve("sb_dungeon_x")));
        } finally {
            win.vampyuyu.survival.dungeon.run.WorldFiles.deleteWorldFolder(root, root.resolve("sb_dungeon_501"));
            win.vampyuyu.survival.dungeon.run.WorldFiles.deleteWorldFolder(root, root.resolve("sb_dungeon_701"));
        }
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
    public void aClearGivesCatacombsXpAndTheLevelBonusOnCoins() {
        java.util.Map<java.util.UUID, Double> paid = new java.util.HashMap<>();
        win.vampyuyu.survival.api.EconomyApi fake = (win.vampyuyu.survival.api.EconomyApi) java.lang.reflect.Proxy.newProxyInstance(
            getClass().getClassLoader(), new Class<?>[] {win.vampyuyu.survival.api.EconomyApi.class}, (proxy, method, args) -> {
                if (method.getName().equals("add")) {
                    paid.merge((java.util.UUID) args[0], (Double) args[1], Double::sum);
                }
                return method.getReturnType() == boolean.class ? Boolean.TRUE : method.getReturnType() == double.class ? (Object) 0.0 : null;
            });
        svc.ctx.override(win.vampyuyu.survival.api.EconomyApi.class, fake);
        var aliceData = plugin.core().profiles().section(alice.getUniqueId(), "dungeon",
            win.vampyuyu.survival.dungeon.DungeonData.class, win.vampyuyu.survival.dungeon.DungeonData::new);
        aliceData.catacombsXp = 40_000; // level 10 = +10 percent
        running();
        run.mobsSpawned = 4;
        run.mobsKilled = 4;
        run.finish(true, "");
        double base = run.result.coins();
        assertTrue(base > 0);
        assertEquals("level 10 pays 10 percent more", Math.floor(base * 1.10), paid.get(alice.getUniqueId()), 0.001);
        assertEquals("level 0 pays the base", base, paid.get(bob.getUniqueId()), 0.001);
        long gain = win.vampyuyu.survival.dungeon.Catacombs.gain(svc.cfg(), run.result.xp());
        assertTrue(gain > 0);
        assertEquals(40_000 + gain, aliceData.catacombsXp);
        var bobData = plugin.core().profiles().section(bob.getUniqueId(), "dungeon",
            win.vampyuyu.survival.dungeon.DungeonData.class, win.vampyuyu.survival.dungeon.DungeonData::new);
        assertEquals(gain, bobData.catacombsXp);
    }

    @Test
    public void aFailedRunGivesNoCatacombsXp() {
        running();
        run.finish(false, "test");
        var data = plugin.core().profiles().section(alice.getUniqueId(), "dungeon",
            win.vampyuyu.survival.dungeon.DungeonData.class, win.vampyuyu.survival.dungeon.DungeonData::new);
        assertEquals(0, data.catacombsXp);
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
