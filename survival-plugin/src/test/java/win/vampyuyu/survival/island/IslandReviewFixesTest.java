package win.vampyuyu.survival.island;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Item;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import win.vampyuyu.survival.SurvivalPlugin;
import win.vampyuyu.survival.api.DungeonApi;
import win.vampyuyu.survival.api.IslandApi;
import win.vampyuyu.survival.core.SkyblockWorlds;
import win.vampyuyu.survival.island.IslandAccess.Action;

/**
 * Regression tests of the island review: lifecycle state machine (owed kit, failed wipes and builds, stale callbacks,
 * per-cell job order, platform recovery), full-height sweeps, protection allow-list, pickups, void rescue, dungeon
 * guard, shortcuts, hidden minion upgrade and the join-time entry check. The block jobs are scripted; MockBukkit has
 * no chunk tickets, so a real world sweep is never exercised here.
 */
public class IslandReviewFixesTest {
    private ServerMock server;
    private SurvivalPlugin plugin;
    private IslandService service;
    private World overworld;
    private FakeJobs fake;

    // ---------------------------------------------------------------- scripted jobs

    /** Jobs that finish in one step unless told to hold or fail; records what ran and in which order. */
    private static final class FakeJobs implements IslandService.JobFactory {
        final List<String> started = new ArrayList<>();
        final List<String> finished = new ArrayList<>();
        final List<BlockPlan> plans = new ArrayList<>();
        final List<String> planLabels = new ArrayList<>();
        Predicate<String> hold = l -> false;
        Predicate<String> fail = l -> false;
        long stoneCount = 100_000;

        private final class Scripted implements IslandService.Counting {
            private final String label;

            Scripted(String label) {
                this.label = label;
            }

            @Override
            public String name() {
                return label;
            }

            @Override
            public boolean step(PlanRunner.Budget budget) {
                if (!started.contains(label)) {
                    started.add(label);
                }
                if (hold.test(label)) {
                    return false;
                }
                if (fail.test(label)) {
                    throw new IllegalStateException("scripted failure of " + label);
                }
                finished.add(label);
                return true;
            }

            @Override
            public void abort() {}

            @Override
            public Map<Material, Long> counts() {
                Map<Material, Long> counts = new EnumMap<>(Material.class);
                counts.put(Material.STONE, stoneCount);
                return counts;
            }
        }

        @Override
        public PlanRunner.Job plan(String label, World world, BlockPlan plan, int ox, int oy, int oz) {
            plans.add(plan);
            planLabels.add(label);
            return new Scripted(label);
        }

        @Override
        public PlanRunner.Job wipe(String label, World world, int centerX, int centerZ, int radius) {
            return new Scripted(label);
        }

        @Override
        public IslandService.Counting scan(String label, World world, int centerX, int centerZ, int radius) {
            return new Scripted(label);
        }

        /** The starter chest items of the most recent overworld plan. */
        List<IslandConfig.ItemSpec> lastOverworldChest() {
            for (int i = planLabels.size() - 1; i >= 0; i--) {
                if (planLabels.get(i).endsWith("overworld")) {
                    return plans.get(i).chests().get(0).items();
                }
            }
            throw new AssertionError("no overworld plan was generated");
        }

        List<IslandConfig.ItemSpec> lastOverworldChest(Island island) {
            for (int i = planLabels.size() - 1; i >= 0; i--)
                if (planLabels.get(i).equals("island " + island.id + " overworld"))
                    return plans.get(i).chests().get(0).items();
            throw new AssertionError("no overworld plan for " + island.id);
        }

        long count(String prefixOrSuffix) {
            return planLabels.stream().filter(l -> l.endsWith(prefixOrSuffix)).count();
        }
    }

    @Before
    public void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(SurvivalPlugin.class);
        service = (IslandService) plugin.core().require(IslandApi.class);
        overworld = Bukkit.getWorld(SkyblockWorlds.WORLD);
        fake = new FakeJobs();
        service.jobs(fake);
    }

    @After
    public void tearDown() {
        MockBukkit.unmock();
    }

    // ---------------------------------------------------------------- helpers

    private static String said(PlayerMock player) {
        StringBuilder out = new StringBuilder();
        Component next;
        while ((next = player.nextComponentMessage()) != null) {
            out.append(PlainTextComponentSerializer.plainText().serialize(next)).append('\n');
        }
        return out.toString();
    }

    private PlayerMock player(String name) {
        PlayerMock player = server.addPlayer(name);
        player.setOp(false);
        return player;
    }

    private Island readyIsland(PlayerMock owner) {
        Island island = service.registry().create(owner.getUniqueId(), owner.getName(), "classic", System.currentTimeMillis());
        island.state(Island.State.READY);
        island.platformsReady = true;
        return island;
    }

    private Location center(Island island) {
        return new Location(overworld, service.grid().centerOf(island.cellX) + 0.5, 65,
            service.grid().centerOf(island.cellZ) + 0.5);
    }

    private void tick(int ticks) {
        server.getScheduler().performTicks(ticks);
    }

    private Island islandOf(PlayerMock p) {
        return service.registry().ofPlayer(p.getUniqueId()).orElseThrow();
    }

    private static boolean has(List<IslandConfig.ItemSpec> items, Material material) {
        return items.stream().anyMatch(i -> i.material() == material);
    }

    // ---------------------------------------------------------------- PlanRunner lanes

    private static final class Steps implements PlanRunner.Job {
        final String name;
        final int steps;
        final List<String> log;
        int done;

        Steps(String name, int steps, List<String> log) {
            this.name = name;
            this.steps = steps;
            this.log = log;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public boolean step(PlanRunner.Budget budget) {
            if (done == 0) {
                log.add("start " + name);
            }
            if (++done >= steps) {
                log.add("end " + name);
                return true;
            }
            return false;
        }

        @Override
        public void abort() {}
    }

    @Test
    public void jobsOfOneLaneRunInOrderAndOtherLanesRunSideBySide() {
        PlanRunner runner = new PlanRunner(java.util.logging.Logger.getLogger("test"), 1000, 20);
        List<String> log = new ArrayList<>();
        runner.submit("a", new Steps("plan", 3, log));
        runner.submit("a", new Steps("wipe", 1, log));
        runner.submit("b", new Steps("other", 1, log));
        runner.submit(new Steps("free", 1, log));
        assertEquals(3, runner.pendingIn("a") + runner.pendingIn("b"));
        runner.tick();
        assertTrue(log.contains("start plan"));
        assertFalse("the wipe waits for the plan of its lane", log.contains("start wipe"));
        assertTrue("another lane is not held back", log.contains("end other"));
        assertTrue(log.contains("end free"));
        runner.tick();
        assertFalse(log.contains("start wipe"));
        runner.tick();
        assertTrue(log.contains("end plan"));
        assertFalse("not in the tick the plan ends", log.contains("start wipe"));
        runner.tick();
        assertEquals("end plan", log.get(log.indexOf("start wipe") - 1));
        assertEquals(0, runner.pending());
    }

    @Test
    public void aBusyLaneDoesNotStarveOthers() {
        PlanRunner runner = new PlanRunner(java.util.logging.Logger.getLogger("test"), 1000, 20);
        List<String> log = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            runner.submit("same", new Steps("s" + i, 2, log));
        }
        runner.submit("elsewhere", new Steps("x", 1, log));
        runner.tick();
        assertTrue("queued behind five jobs of another lane, still runs now", log.contains("end x"));
    }

    // ---------------------------------------------------------------- owed kit, retries

    @Test
    public void theStarterKitIsOnlyClaimedOnceTheIslandIsReady() {
        PlayerMock p = player("Founder");
        fake.fail = l -> l.endsWith("overworld");
        p.performCommand("island create classic");
        Island island = islandOf(p);
        assertTrue("owed, not given yet", island.kitOwed);
        tick(2500);
        assertEquals("every retry was tried", 1 + service.config().buildRetries, fake.count("overworld"));
        assertEquals(Island.State.BUILDING, island.state());
        assertFalse("not claimed by a build that never finished", service.data(p.getUniqueId()).starterClaimed);
        assertTrue(island.kitOwed);
        assertTrue(said(p).contains("could not be built"));

        fake.fail = l -> false;
        assertTrue(service.rebuild(island).ok());
        tick(10);
        assertEquals(Island.State.READY, island.state());
        assertTrue("the rebuild after an interrupted first build gives the real kit",
            has(fake.lastOverworldChest(), Material.ICE) && has(fake.lastOverworldChest(), Material.LAVA_BUCKET));
        assertTrue(service.data(p.getUniqueId()).starterClaimed);
        assertFalse(island.kitOwed);
    }

    @Test
    public void aTransientBuildFailureIsRetriedAndStillGivesTheKit() {
        PlayerMock p = player("Patient");
        AtomicInteger failures = new AtomicInteger(2);
        fake.fail = l -> l.endsWith("overworld") && failures.getAndDecrement() > 0;
        p.performCommand("island create classic");
        tick(1000);
        Island island = islandOf(p);
        assertEquals(Island.State.READY, island.state());
        assertTrue(has(fake.lastOverworldChest(), Material.ICE));
        assertTrue(island.platformsReady);
        assertTrue(service.data(p.getUniqueId()).lootClaimed);
    }

    @Test
    public void laterIslandsOfTheSamePlayerGetTheRepeatChest() {
        PlayerMock p = player("Repeater");
        p.performCommand("island create classic");
        tick(10);
        Island island = islandOf(p);
        assertEquals(Island.State.READY, island.state());
        assertTrue(has(fake.lastOverworldChest(), Material.ICE));
        island.lastResetAt = 0;
        p.performCommand("island reset");
        p.performCommand("island reset");
        tick(20);
        assertEquals(Island.State.READY, island.state());
        assertFalse("anti farming: no second kit", has(fake.lastOverworldChest(), Material.ICE));
        assertTrue(has(fake.lastOverworldChest(), Material.BONE_MEAL));
        assertEquals("a reset draws another generation", 3, island.generation);
    }

    @Test
    public void aTransferDuringTheBuildDoesNotMoveTheFirstTimeClaim() {
        PlayerMock resetter = player("Resetter");
        PlayerMock member = player("Member");
        Island island = readyIsland(resetter);
        service.registry().addMember(island, member.getUniqueId(), IslandRole.MEMBER);
        // the member already got their kit once; the resetter never did
        service.data(member.getUniqueId()).starterClaimed = true;
        service.data(member.getUniqueId()).lootClaimed = true;
        fake.hold = l -> l.startsWith("wipe");
        island.lastResetAt = 0;
        resetter.performCommand("island reset");
        resetter.performCommand("island reset");
        assertTrue(island.kitOwed);
        assertEquals(resetter.getUniqueId(), island.kitFor);
        service.registry().transfer(island, member.getUniqueId()); // owner changes while the island is RESETTING
        fake.hold = l -> false;
        tick(30);
        assertEquals(Island.State.READY, island.state());
        assertTrue("the resetter is the one marked as served", service.data(resetter.getUniqueId()).starterClaimed);
        assertTrue(service.data(resetter.getUniqueId()).lootClaimed);
        assertTrue(has(fake.lastOverworldChest(island), Material.ICE));
        // The former owner now owns a personal fallback island. Never overwrite it by transferring back.
        assertFalse(service.registry().transfer(island, resetter.getUniqueId()));
        island.lastResetAt = 0;
        member.performCommand("island reset");
        member.performCommand("island reset");
        tick(30);
        assertFalse(has(fake.lastOverworldChest(island), Material.ICE));
    }

    @Test
    public void generationsChangeTheSeedButRetriesRepeatIt() {
        UUID id = UUID.randomUUID();
        assertEquals(IslandTemplates.random(id, 2, 0).nextLong(), IslandTemplates.random(id, 2, 0).nextLong());
        assertNotEquals(IslandTemplates.random(id, 2, 0).nextLong(), IslandTemplates.random(id, 3, 0).nextLong());
        assertNotEquals(IslandTemplates.random(id, 2, 1).nextLong(), IslandTemplates.random(id, 2, 2).nextLong());
    }

    // ---------------------------------------------------------------- wipes

    @Test
    public void aFailedWipeNeverBuildsOnTopAndLeavesTheIslandResetting() {
        PlayerMock owner = player("Unlucky");
        Island island = readyIsland(owner);
        fake.fail = l -> l.startsWith("wipe");
        owner.performCommand("island reset");
        owner.performCommand("island reset");
        assertEquals(Island.State.RESETTING, island.state());
        tick(3000);
        assertEquals(0, fake.plans.size());
        assertEquals(Island.State.RESETTING, island.state());
        String told = said(owner);
        assertTrue(told, told.contains("could not be cleared"));

        fake.fail = l -> false;
        assertTrue(service.rebuild(island).ok());
        tick(10);
        assertEquals(Island.State.READY, island.state());
        assertFalse(fake.plans.isEmpty());
    }

    @Test
    public void aFailedDeleteKeepsTheCellParkedUntilTheWipeWorks() {
        PlayerMock owner = player("Doomed");
        Island island = readyIsland(owner);
        int cx = island.cellX;
        int cz = island.cellZ;
        fake.fail = l -> l.startsWith("wipe");
        assertTrue(service.delete(owner.getUniqueId()).ok());
        tick(3000);
        assertTrue("not handed to the next owner", service.registry().isDirty(cx, cz));
        fake.fail = l -> false;
        service.recover();
        tick(10);
        assertFalse(service.registry().isDirty(cx, cz));
    }

    @Test
    public void deleteWaitsForTheGenerationStillRunningOnTheCell() {
        PlayerMock owner = player("Hasty");
        fake.hold = l -> l.endsWith("overworld");
        owner.performCommand("island create classic");
        tick(3);
        Island island = islandOf(owner);
        int cx = island.cellX;
        int cz = island.cellZ;
        String wipeWorld = "wipe " + SkyblockWorlds.WORLD + " " + cx + "," + cz;
        assertTrue(service.delete(owner.getUniqueId()).ok());
        tick(10);
        assertFalse("the wipe must not interleave with the running plan", fake.started.contains(wipeWorld));
        fake.hold = l -> false;
        tick(10);
        assertTrue(fake.finished.indexOf(wipeWorld) > fake.finished.indexOf(fake.planLabels.get(0)));
        assertFalse(service.registry().isDirty(cx, cz));
        assertEquals("no platform was started for a deleted island", 1, fake.plans.size());
        assertTrue(service.registry().byId(island.id).isEmpty());
    }

    @Test
    public void rebuildRefusesWhileJobsAreQueued() {
        PlayerMock owner = player("Busy");
        fake.hold = l -> l.endsWith("overworld");
        owner.performCommand("island create classic");
        tick(3);
        assertTrue(service.busy(islandOf(owner)));
        assertFalse(service.rebuild(islandOf(owner)).ok());
    }

    // ---------------------------------------------------------------- stale callbacks

    @Test
    public void platformsFinishingAfterAResetDoNotMarkTheNewIslandReady() {
        PlayerMock owner = player("Quick");
        fake.hold = l -> l.startsWith("island") && !l.endsWith("overworld");
        owner.performCommand("island create classic");
        tick(5);
        Island island = islandOf(owner);
        assertEquals(Island.State.READY, island.state());
        assertFalse(island.platformsReady);
        island.lastResetAt = 0;
        owner.performCommand("island reset");
        owner.performCommand("island reset");
        assertEquals(Island.State.RESETTING, island.state());
        fake.hold = l -> l.startsWith("wipe");
        tick(5);
        assertFalse("old platform jobs finished, their callback is stale", island.platformsReady);
        assertFalse("and must not claim the loot", service.data(owner.getUniqueId()).lootClaimed);
        fake.hold = l -> false;
        tick(30);
        assertEquals(Island.State.READY, island.state());
        assertTrue(island.platformsReady);
        assertTrue(service.data(owner.getUniqueId()).lootClaimed);
    }

    @Test
    public void aLevelScanStartedBeforeAResetIsDiscarded() {
        PlayerMock owner = player("Scanner");
        Island island = readyIsland(owner);
        fake.hold = l -> l.startsWith("level");
        assertTrue(service.recalculate(island, false, null).ok());
        tick(3);
        owner.performCommand("island reset");
        owner.performCommand("island reset");
        fake.hold = l -> false;
        tick(30);
        assertEquals(Island.State.READY, island.state());
        assertEquals(0, island.level);
        assertEquals(0.0, island.value, 0.0);
    }

    @Test
    public void aLevelScanOfAReadyIslandStillWorks() {
        PlayerMock owner = player("Counter");
        Island island = readyIsland(owner);
        assertTrue(service.recalculate(island, false, null).ok());
        tick(3);
        assertTrue(island.level > 0);
    }

    // ---------------------------------------------------------------- platform recovery

    @Test
    public void platformsThatFailedAreRetriedAndRecoveredAtStart() {
        PlayerMock owner = player("Platformer");
        AtomicInteger failures = new AtomicInteger(1);
        fake.fail = l -> l.endsWith("nether") && l.startsWith("island") && failures.getAndDecrement() > 0;
        owner.performCommand("island create classic");
        tick(5);
        Island island = islandOf(owner);
        assertFalse(island.platformsReady);
        tick(400);
        assertTrue("retried", island.platformsReady);

        // a READY island whose platforms never finished is regenerated by recover()
        PlayerMock other = player("Stranded");
        Island stranded = readyIsland(other);
        stranded.platformsReady = false;
        int before = fake.plans.size();
        service.recover();
        tick(20);
        assertTrue(stranded.platformsReady);
        assertTrue(fake.plans.size() > before);
        assertTrue("the platform cells were wiped first",
            fake.finished.stream().anyMatch(l -> l.startsWith("wipe " + SkyblockWorlds.NETHER + " " + stranded.cellX)));
        assertFalse("the overworld is left alone",
            fake.finished.stream().anyMatch(l -> l.startsWith("wipe " + SkyblockWorlds.WORLD + " " + stranded.cellX + ","
                + stranded.cellZ)));
    }

    // ---------------------------------------------------------------- full height

    @Test
    public void wipeAndScanCoverTheWholeWorldHeight() {
        WipeJob wipe = new WipeJob(plugin, "w", overworld, 0, 0, 10, 100);
        assertEquals(overworld.getMinHeight(), wipe.minY());
        assertEquals(overworld.getMaxHeight() - 1, wipe.maxY());
        ScanJob scan = new ScanJob(plugin, "s", overworld, 0, 0, 10, 2, 100);
        assertEquals(overworld.getMinHeight(), scan.minY());
        assertEquals(overworld.getMaxHeight() - 1, scan.maxY());
    }

    // ---------------------------------------------------------------- dungeon guard

    @Test
    public void playerTeleportsRefuseDuringADungeonRunButTheApiStaysOpen() {
        plugin.core().replace(DungeonApi.class, p -> p.getName().equals("Runner"));
        PlayerMock runner = player("Runner");
        PlayerMock host = player("Host");
        Island runnerIsland = readyIsland(runner);
        Island hostIsland = readyIsland(host);
        service.hubState(true, true);
        runner.teleport(center(runnerIsland));
        tick(3);
        assertEquals(SkyblockWorlds.WORLD, runner.getWorld().getName());
        runner.performCommand("hub");
        assertTrue(said(runner).contains("dungeon"));
        runner.performCommand("island home");
        assertTrue(said(runner).contains("dungeon"));
        runner.performCommand("island nether");
        assertTrue(said(runner).contains("dungeon"));
        assertFalse(service.visit(runner, hostIsland, false).ok());
        tick(3);
        assertEquals("nothing moved the runner", SkyblockWorlds.WORLD, runner.getWorld().getName());
        // the dungeon / menu features take players out through the api
        assertTrue(service.teleportHub(runner));
        tick(3);
        assertEquals(SkyblockWorlds.WORLD, runner.getWorld().getName());
        // others are not affected
        assertTrue(service.goHub(host).ok());
    }

    // ---------------------------------------------------------------- shortcuts

    @Test
    public void netherAndEndShortcutsNeedReadyPlatformsAndCoolDown() {
        PlayerMock p = player("Hopper");
        Island island = readyIsland(p);
        island.platformsReady = false;
        assertFalse(service.shortcut(p, true).ok());
        island.platformsReady = true;
        assertTrue(service.shortcut(p, true).ok());
        tick(3);
        assertEquals(SkyblockWorlds.NETHER, p.getWorld().getName());
        assertFalse("cooldown", service.shortcut(p, false).ok());
        p.performCommand("island nether");
        assertTrue(said(p).contains("Slow down"));
        PlayerMock nobody = player("Nobody");
        assertFalse(service.shortcut(nobody, false).ok());
    }

    // ---------------------------------------------------------------- minion upgrade

    @Test
    public void theMinionUpgradeIsHiddenUntilEnabled() {
        PlayerMock p = player("Spender");
        Island island = readyIsland(p);
        assertFalse(service.config().minionUpgrade);
        assertFalse(service.upgrade(p, IslandService.Upgrade.MINIONS).ok());
        assertEquals(0, island.minionTier);
        assertEquals(0, service.minionSlotBonus(p.getUniqueId()));
        p.performCommand("island upgrade minions");
        assertTrue(said(p).contains("Unknown upgrade"));
        assertFalse(IslandCommands.buyable(service.config()).contains(IslandService.Upgrade.MINIONS));
        p.performCommand("island upgrades");
        var gui = (win.vampyuyu.survival.core.Gui) p.getOpenInventory().getTopInventory().getHolder();
        assertNotNull(gui);
        assertNull("no minion icon", p.getOpenInventory().getTopInventory().getItem(15));
        assertNotNull(p.getOpenInventory().getTopInventory().getItem(12));
    }

    // ---------------------------------------------------------------- protection allow-list

    private Block at(Island island, int dx, Material type) {
        Block block = overworld.getBlockAt(service.grid().centerOf(island.cellX) + dx, 65, service.grid().centerOf(island.cellZ));
        block.setType(type);
        return block;
    }

    private PlayerInteractEvent click(PlayerMock player, Block block, Material held) {
        PlayerInteractEvent event = new PlayerInteractEvent(player, org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK,
            held == null ? null : new ItemStack(held), block, BlockFace.UP);
        server.getPluginManager().callEvent(event);
        return event;
    }

    @Test
    public void blocksThatReactToAClickAreBuildActionsInertOnesAreFree() {
        PlayerMock owner = player("Owner");
        Island island = readyIsland(owner);
        assertEquals(Action.BUILD, IslandProtection.blockAction(at(island, 1, Material.REPEATER)));
        assertEquals(Action.BUILD, IslandProtection.blockAction(at(island, 2, Material.COMPARATOR)));
        assertEquals(Action.BUILD, IslandProtection.blockAction(at(island, 3, Material.NOTE_BLOCK)));
        assertEquals(Action.BUILD, IslandProtection.blockAction(at(island, 4, Material.DAYLIGHT_DETECTOR)));
        assertEquals(Action.BUILD, IslandProtection.blockAction(at(island, 5, Material.OAK_SIGN)));
        assertEquals(Action.BUILD, IslandProtection.blockAction(at(island, 6, Material.RED_BED)));
        assertEquals(Action.BUILD, IslandProtection.blockAction(at(island, 7, Material.ANVIL)));
        assertEquals(Action.INTERACT, IslandProtection.blockAction(at(island, 8, Material.OAK_DOOR)));
        assertEquals(Action.INTERACT, IslandProtection.blockAction(at(island, 9, Material.STONE_BUTTON)));
        assertEquals(Action.INTERACT, IslandProtection.blockAction(at(island, 10, Material.LEVER)));
        assertEquals(Action.INTERACT, IslandProtection.blockAction(at(island, 11, Material.OAK_FENCE_GATE)));
        assertEquals(Action.INTERACT, IslandProtection.blockAction(at(island, 12, Material.CRAFTING_TABLE)));
        assertEquals(Action.CONTAINER, IslandProtection.blockAction(at(island, 13, Material.CHEST)));
        assertNull("nothing happens when you click stone", IslandProtection.blockAction(at(island, 14, Material.STONE)));
        assertNull(IslandProtection.blockAction(at(island, 15, Material.DIRT)));
        assertNull(IslandProtection.blockAction(at(island, 16, Material.OAK_STAIRS)));
        assertNull(IslandProtection.blockAction(at(island, 17, Material.OAK_FENCE)));
        assertNull(IslandProtection.blockAction(at(island, 18, Material.STONE_SLAB)));
        assertEquals(Action.BUILD, IslandProtection.blockAction(at(island, 19, Material.REDSTONE_WIRE)));
    }

    @Test
    public void visitorsCannotTamperWithRedstoneOrSignsButCanUseDoorsAndEat() {
        PlayerMock owner = player("Host");
        PlayerMock visitor = player("Visitor");
        Island island = readyIsland(owner);
        Block repeater = at(island, 1, Material.REPEATER);
        Block door = at(island, 2, Material.OAK_DOOR);
        Block stone = at(island, 3, Material.STONE);
        Block sign = at(island, 4, Material.OAK_SIGN);

        assertTrue(click(visitor, repeater, null).useInteractedBlock() == org.bukkit.event.Event.Result.DENY);
        PlayerInteractEvent snack = click(visitor, repeater, Material.COOKED_BEEF);
        assertEquals(org.bukkit.event.Event.Result.DENY, snack.useInteractedBlock());
        assertNotEquals("eating is not blocked, only the repeater", org.bukkit.event.Event.Result.DENY,
            snack.useItemInHand());
        assertFalse("doors work for visitors by default", click(visitor, door, null).isCancelled());
        PlayerInteractEvent eat = click(visitor, stone, Material.COOKED_BEEF);
        assertFalse("pointing at plain stone never blocks eating or throwing", eat.isCancelled());
        assertTrue("a hoe changes the ground", click(visitor, stone, Material.DIAMOND_HOE).isCancelled());
        assertFalse(click(owner, repeater, null).isCancelled());

        SignChangeEvent edit = new SignChangeEvent(sign, visitor, new String[] {"x", "", "", ""});
        server.getPluginManager().callEvent(edit);
        assertTrue("visitors cannot edit signs", edit.isCancelled());
        SignChangeEvent own = new SignChangeEvent(sign, owner, new String[] {"x", "", "", ""});
        server.getPluginManager().callEvent(own);
        assertFalse(own.isCancelled());

        island.flag(IslandFlag.VISITOR_INTERACT, false);
        assertTrue(click(visitor, door, null).isCancelled());
    }

    // ---------------------------------------------------------------- pickups, drops, fishing

    private Item dropped(Location at, PlayerMock thrower) {
        UUID thrownBy = thrower == null ? null : thrower.getUniqueId();
        // MockBukkit cannot drop items: a stand-in answering the three questions the protection asks
        return (Item) java.lang.reflect.Proxy.newProxyInstance(Item.class.getClassLoader(), new Class<?>[] {Item.class},
            (proxy, method, args) -> switch (method.getName()) {
                case "getLocation" -> at.clone();
                case "getWorld" -> at.getWorld();
                case "getThrower" -> thrownBy;
                case "getOwner" -> null;
                case "hasMetadata" -> false;
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy == args[0];
                case "toString" -> "ItemStandIn";
                default -> {
                    Class<?> type = method.getReturnType();
                    yield type == boolean.class ? (Object) false : type == int.class ? (Object) 0
                        : type == long.class ? (Object) 0L : type == double.class ? (Object) 0.0
                        : type == float.class ? (Object) 0f : null;
                }
            });
    }

    private boolean pickupCancelled(PlayerMock player, Item item) {
        EntityPickupItemEvent event = new EntityPickupItemEvent(player, item, 0);
        server.getPluginManager().callEvent(event);
        return event.isCancelled();
    }

    @Test
    public void strangersCannotPickUpLootOnSomeoneElsesIsland() {
        PlayerMock owner = player("Farmer");
        PlayerMock visitor = player("Thief");
        PlayerMock friend = player("Friend");
        PlayerMock banned = player("Banned");
        Island island = readyIsland(owner);
        island.trusted.add(friend.getUniqueId());
        island.banned.add(banned.getUniqueId());
        Item loot = dropped(center(island), owner);
        assertFalse(pickupCancelled(owner, loot));
        assertTrue("visitor", pickupCancelled(visitor, loot));
        assertTrue("banned", pickupCancelled(banned, loot));
        assertFalse("trusted guest", pickupCancelled(friend, loot));
        Item own = dropped(center(island), visitor);
        assertFalse("their own drop is always theirs", pickupCancelled(visitor, own));
        Item far = dropped(new Location(overworld, 5, 70, 5), null);
        assertFalse("nothing to protect outside islands", pickupCancelled(visitor, far));
    }

    @Test
    public void visitorsCannotDropItemsOnSomeoneElsesIsland() {
        PlayerMock owner = player("Host2");
        PlayerMock visitor = player("Dumper");
        Island island = readyIsland(owner);
        visitor.teleport(new Location(overworld, 0, 120, 0));
        Item item = dropped(center(island), null);
        PlayerDropItemEvent ownDrop = new PlayerDropItemEvent(owner, item);
        owner.teleport(center(island));
        server.getPluginManager().callEvent(ownDrop);
        assertFalse(ownDrop.isCancelled());
        // the visitor cannot get there (teleport rules) unless allowed: use a trusted-free open island
        visitor.teleport(center(island));
        PlayerDropItemEvent drop = new PlayerDropItemEvent(visitor, item);
        server.getPluginManager().callEvent(drop);
        assertTrue(drop.isCancelled());
    }

    @Test
    public void fishingHooksCannotPullLootOrAnimalsOutOfAnotherIsland() {
        PlayerMock owner = player("Pond");
        PlayerMock visitor = player("Angler");
        Island island = readyIsland(owner);
        Item loot = dropped(center(island), owner);
        PlayerFishEvent event = new PlayerFishEvent(visitor, loot, null, PlayerFishEvent.State.CAUGHT_ENTITY);
        server.getPluginManager().callEvent(event);
        assertTrue(event.isCancelled());
        PlayerFishEvent own = new PlayerFishEvent(owner, loot, null, PlayerFishEvent.State.CAUGHT_ENTITY);
        server.getPluginManager().callEvent(own);
        assertFalse(own.isCancelled());
    }

    // ---------------------------------------------------------------- void rescue, respawn, eviction

    private IslandTracker tracker() {
        for (var listener : org.bukkit.event.HandlerList.getRegisteredListeners(plugin)) {
            if (listener.getListener() instanceof IslandTracker t) {
                return t;
            }
        }
        throw new AssertionError("no tracker");
    }

    @Test
    public void voidDamageIsOnlyCancelledWhenSomeoneCanBeRescued() {
        PlayerMock nobody = player("Faller");
        Location below = new Location(overworld, 3, overworld.getMinHeight() - 40, 3);
        nobody.teleport(below);
        service.hubState(false, false);
        assertTrue("a missing personal island is created for rescue", tracker().rescue(nobody, below));
        EntityDamageEvent noTarget = new EntityDamageEvent(nobody, EntityDamageEvent.DamageCause.VOID, 4.0);
        server.getPluginManager().callEvent(noTarget);
        assertTrue("rescue stays within Skyblock", noTarget.isCancelled());

        service.hubState(true, true);
        EntityDamageEvent saved = new EntityDamageEvent(nobody, EntityDamageEvent.DamageCause.VOID, 4.0);
        server.getPluginManager().callEvent(saved);
        assertTrue(saved.isCancelled());
        EntityDamageEvent again = new EntityDamageEvent(nobody, EntityDamageEvent.DamageCause.VOID, 4.0);
        server.getPluginManager().callEvent(again);
        assertTrue("still cancelled while the rescue is under way", again.isCancelled());
    }

    @Test
    public void evictionStillWorksWithoutAHub() {
        PlayerMock owner = player("Landlord");
        PlayerMock banned = player("Evictee");
        Island island = readyIsland(owner);
        service.hubState(false, false);
        server.addSimpleWorld("vanilla_like");
        banned.teleport(center(island));
        island.banned.add(banned.getUniqueId());
        assertTrue(service.evictPlayer(banned, island, "Out."));
        tick(3);
        assertEquals(SkyblockWorlds.WORLD,banned.getWorld().getName());
        assertNotEquals(island.id,service.islandAtLocation(banned.getLocation()).id);
    }

    @Test
    public void aPlayerBannedWhileOfflineIsMovedOutAfterLogin() {
        PlayerMock owner = player("Owner3");
        Island island = readyIsland(owner);
        service.hubState(true, true);
        PlayerMock griefer = player("Griefer");
        griefer.teleport(center(island));
        island.banned.add(griefer.getUniqueId());
        assertEquals(SkyblockWorlds.WORLD, griefer.getWorld().getName());
        tick(service.config().joinCheckTicks + 5);
        assertEquals(SkyblockWorlds.WORLD, griefer.getWorld().getName());
        assertNotEquals("entry check moved them to their own island", island.id,service.islandAtLocation(griefer.getLocation()).id);
        assertTrue(said(griefer).contains("banned"));
        // and the check itself, called directly, does nothing to someone who is allowed
        island.banned.clear();
        griefer.teleport(center(island));
        tracker().enforceEntry(griefer);
        tick(5);
        assertEquals(SkyblockWorlds.WORLD, griefer.getWorld().getName());
    }

    @Test
    public void membersAreNotMovedByTheEntryCheck() {
        PlayerMock owner = player("Owner4");
        Island island = readyIsland(owner);
        island.locked = true;
        owner.teleport(center(island));
        tracker().enforceEntry(owner);
        tick(5);
        assertEquals(SkyblockWorlds.WORLD, owner.getWorld().getName());
    }
}
