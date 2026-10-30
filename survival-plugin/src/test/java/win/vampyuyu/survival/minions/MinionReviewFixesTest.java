package win.vampyuyu.survival.minions;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.inventory.ItemStack;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;
import win.vampyuyu.survival.SurvivalPlugin;
import win.vampyuyu.survival.api.IslandApi;
import win.vampyuyu.survival.api.MinionApi;
import win.vampyuyu.survival.core.Core;
import win.vampyuyu.survival.core.Items;

/** The fixes of the minions review: placed blocks, payments, access rules, owed items, orphans, recipes, menu. */
public class MinionReviewFixesTest {
    private ServerMock server;
    private SurvivalPlugin plugin;
    private Core core;
    private Items items;
    private MinionManager manager;
    private FakeIslands islands;
    private WorldMock world;
    private PlayerMock player;

    @Before
    public void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(SurvivalPlugin.class);
        core = plugin.core();
        items = core.items();
        world = server.addSimpleWorld("sb_world");
        player = server.addPlayer();
        islands = new FakeIslands(player.getUniqueId());
        core.replace(IslandApi.class, islands);
        manager = (MinionManager) core.require(MinionApi.class);
        manager.config().guiClickMillis = 0;
        for (int cx = -2; cx <= 2; cx++) {
            for (int cz = -2; cz <= 2; cz++) {
                world.loadChunk(cx, cz);
            }
        }
        for (int x = -4; x <= 4; x++) {
            for (int z = -4; z <= 4; z++) {
                world.getBlockAt(x, 63, z).setType(Material.STONE);
            }
        }
        for (int i = 36; i < player.getInventory().getSize(); i++) {
            player.getInventory().setItem(i, new ItemStack(Material.BEDROCK));
        }
    }

    @After
    public void tearDown() {
        MockBukkit.unmock();
    }

    private MinionData place(String type, int tier) {
        var r = manager.place(player, MinionItems.createMinion(items, type, tier, null), world.getBlockAt(0, 63, 0), BlockFace.UP);
        assertNull(r.error(), r.error());
        return manager.at(world.getBlockAt(0, 64, 0)).orElseThrow();
    }

    private long count(org.bukkit.entity.Player p, String key) {
        return manager.countIn(p, List.of(key));
    }

    /** A minion with a block placed for its current cycle, as the ticker does. */
    private Block track(MinionData d, int x, Material m) {
        Block b = world.getBlockAt(x, 64, 1);
        b.setType(m);
        manager.trackSpot(d, b);
        return b;
    }

    // ---------------------------------------------------------------- placed blocks cannot be farmed

    @Test
    public void aPlayerCannotBreakTheOreAMinionPlaced() {
        MinionData d = place("diamond", 1);
        Block ore = track(d, 1, Material.DIAMOND_ORE);
        BlockBreakEvent event = new BlockBreakEvent(ore, server.addPlayer());
        server.getPluginManager().callEvent(event);
        assertTrue(event.isCancelled());
        assertEquals(Material.DIAMOND_ORE, ore.getType());
    }

    @Test
    public void anotherBlockOfTheSameKindStaysBreakable() {
        MinionData d = place("diamond", 1);
        track(d, 1, Material.DIAMOND_ORE);
        Block mine = world.getBlockAt(2, 64, 2);
        mine.setType(Material.DIAMOND_ORE);
        BlockBreakEvent event = new BlockBreakEvent(mine, server.addPlayer());
        server.getPluginManager().callEvent(event);
        assertFalse(event.isCancelled());
    }

    @Test
    public void theGroundUnderAPlacedCropIsProtectedButNotUnderAPlacedOre() {
        MinionData d = place("wheat", 1);
        Block crop = track(d, 1, Material.WHEAT);
        BlockBreakEvent under = new BlockBreakEvent(crop.getRelative(BlockFace.DOWN), server.addPlayer());
        server.getPluginManager().callEvent(under);
        assertTrue("breaking the farmland would pop the crop", under.isCancelled());

        MinionData d2 = new MinionData();
        d2.id = "ore";
        d2.world = "sb_world";
        d2.owner = d.owner;
        d2.type = "coal";
        d2.x = 7;
        d2.y = 64;
        d2.z = 7;
        manager.store().add(d2);
        Block ore = world.getBlockAt(3, 64, 3);
        ore.setType(Material.COAL_ORE);
        manager.trackSpot(d2, ore);
        BlockBreakEvent below = new BlockBreakEvent(ore.getRelative(BlockFace.DOWN), server.addPlayer());
        server.getPluginManager().callEvent(below);
        assertFalse(below.isCancelled());
    }

    @Test
    public void dropsOfAPlacedBlockAreClearedIfSomethingElseLetItBreak() {
        MinionData d = place("diamond", 1);
        Block ore = track(d, 1, Material.DIAMOND_ORE);
        List<org.bukkit.entity.Item> drops = new ArrayList<>();
        BlockDropItemEvent event = new BlockDropItemEvent(ore, ore.getState(), player, drops);
        server.getPluginManager().callEvent(event);
        assertTrue(event.getItems().isEmpty());
        assertTrue(event.isCancelled());
    }

    @Test
    public void explosionsWaterAndEndermenLeavePlacedBlocksAlone() {
        MinionData d = place("melon", 1);
        Block melon = track(d, 1, Material.MELON);
        Block other = world.getBlockAt(2, 64, 2);
        other.setType(Material.MELON);

        List<Block> blocks = new ArrayList<>(List.of(melon, other));
        EntityExplodeEvent boom = new EntityExplodeEvent(player, melon.getLocation(), blocks, 1f, org.bukkit.ExplosionResult.DESTROY);
        server.getPluginManager().callEvent(boom);
        assertEquals(List.of(other), boom.blockList());

        BlockFromToEvent flow = new BlockFromToEvent(world.getBlockAt(1, 65, 1), melon);
        server.getPluginManager().callEvent(flow);
        assertTrue(flow.isCancelled());

        var enderman = world.spawn(new org.bukkit.Location(world, 5, 70, 5), org.bukkit.entity.Enderman.class);
        EntityChangeBlockEvent pick = new EntityChangeBlockEvent(enderman, melon, Material.AIR.createBlockData());
        server.getPluginManager().callEvent(pick);
        assertTrue(pick.isCancelled());
    }

    @Test
    public void thePlacedSpotSurvivesARestartAndIsFinishedByTheNextCycle() {
        MinionData d = place("coal", 1);
        Block ore = track(d, 1, Material.COAL_ORE);
        manager.store().saveNow(d.owner);
        // a fresh store and manager read the same files: the spot is remembered and protected again
        MinionStore reloaded = new MinionStore(core.store(), core.getDataFolder(), plugin.getLogger());
        reloaded.loadAll();
        MinionData back = reloaded.get(d.id).orElseThrow();
        assertTrue(back.placed);
        assertEquals(1, back.spotX);
        assertEquals("COAL_ORE", back.spotMaterial);
        MinionManager again = new MinionManager(core, manager.config(), reloaded, manager.logic(),
            new MinionVisuals(manager.config(), plugin.getLogger()), System::currentTimeMillis);
        assertTrue(again.isProtected(ore));
        assertFalse(again.isProtected(world.getBlockAt(2, 64, 2)));
        // finishing the cycle removes that block, and only that block
        Block wall = world.getBlockAt(-1, 64, 1);
        wall.setType(Material.COAL_ORE);
        Terrain.harvest(Terrain.areaOf(world, back, 2), manager.config().types.get("coal"), back, new MinionRuntime(),
            new java.util.SplittableRandom(1));
        assertEquals(Material.AIR, ore.getType());
        assertEquals(Material.COAL_ORE, wall.getType());
    }

    @Test
    public void pickingTheMinionUpRemovesItsPlacedBlockWithoutADrop() {
        MinionData d = place("diamond", 1);
        Block ore = track(d, 1, Material.DIAMOND_ORE);
        assertNull(manager.pickUp(player, d).error());
        assertEquals(Material.AIR, ore.getType());
        assertFalse(manager.isProtected(ore));
    }

    @Test
    public void theTickerPlacesTracksAndHarvestsTheBlockOfTheCycle() {
        AtomicLong now = new AtomicLong(5_000_000);
        MinionManager timed = new MinionManager(core, manager.config(), manager.store(), manager.logic(),
            new MinionVisuals(manager.config(), plugin.getLogger()), now::get);
        MinionData d = new MinionData();
        d.id = UUID.randomUUID().toString();
        d.owner = player.getUniqueId().toString();
        d.island = islands.id.toString();
        d.world = "sb_world";
        d.y = 64;
        d.type = "coal";
        d.tier = 1;
        d.lastTick = now.get();
        manager.store().add(d);
        player.teleport(new org.bukkit.Location(world, 3, 64, 3));
        long cycle = (long) manager.logic().cycleMillis(d, manager.config().types.get("coal"));
        now.addAndGet(cycle / 2 + 1000);
        timed.tick();
        assertTrue("an ore is placed after half a cycle", d.placed);
        Block ore = world.getBlockAt(d.spotX, d.spotY, d.spotZ);
        assertEquals(Material.COAL_ORE, ore.getType());
        assertTrue(timed.isProtected(ore));
        now.addAndGet(cycle / 2 - 1000 + 500); // the cycle ends, a little progress of the next one is left
        timed.tick();
        assertEquals(Material.AIR, ore.getType());
        assertFalse(d.placed);
        assertTrue(d.storage.getOrDefault("m:COAL", 0L) >= 1);
        assertFalse(timed.isProtected(ore));
    }

    // ---------------------------------------------------------------- access

    @Test
    public void aFreedIslandCellGivenToSomeoneElseGivesThemNoAccess() {
        MinionData d = place("cobblestone", 3);
        PlayerMock newcomer = server.addPlayer();
        islands.id = UUID.randomUUID(); // the cell now belongs to another island
        islands.owner = newcomer.getUniqueId();
        islands.members = Set.of(newcomer.getUniqueId());
        assertFalse(manager.canAccess(newcomer, d));
        assertFalse(manager.canPickUp(newcomer, d));
        assertTrue("the real owner still can", manager.canAccess(player, d));
    }

    @Test
    public void coopMembersUseTheMinionButCannotTakeUpgradesHatsOrItems() {
        MinionData d = place("cobblestone", 3);
        PlayerMock friend = server.addPlayer();
        islands.members = Set.of(player.getUniqueId(), friend.getUniqueId());
        d.storage.put("m:COBBLESTONE", 30L);
        d.upgrades.add("compactor");
        d.skin = "pumpkin_hat";
        assertTrue(manager.canAccess(friend, d));
        assertFalse(manager.canCollect(friend, d));
        assertEquals(0, manager.withdraw(friend, d, "m:COBBLESTONE", 30));
        assertEquals(0, manager.collectAll(friend, d));
        assertEquals(30, d.storage.get("m:COBBLESTONE").longValue());
        assertNotNull(manager.removeUpgrade(friend, d, "compactor").error());
        assertEquals(List.of("compactor"), d.upgrades);
        friend.getInventory().addItem(items.create("minion_skin_creeper_head"));
        assertNotNull(manager.setSkin(friend, d, manager.config().skins.get("creeper_head")).error());
        assertNotNull(manager.setSkin(friend, d, null).error());
        assertEquals("pumpkin_hat", d.skin);
        // but they can feed it
        friend.getInventory().addItem(new ItemStack(Material.COAL, 2));
        assertNull(manager.addFuel(friend, d, manager.config().fuels.get("coal"), false).error());
    }

    @Test
    public void membersMayCollectWhenTheConfigSaysSo() {
        MinionData d = place("cobblestone", 3);
        PlayerMock friend = server.addPlayer();
        islands.members = Set.of(player.getUniqueId(), friend.getUniqueId());
        d.storage.put("m:COBBLESTONE", 30L);
        manager.config().memberCollect = true;
        assertTrue(manager.canCollect(friend, d));
        assertEquals(30, manager.collectAll(friend, d));
    }

    @Test
    public void theIslandOwnerPickingUpAMembersMinionSendsEverythingToTheMinionsOwner() {
        PlayerMock owner = server.addPlayer();
        islands.owner = owner.getUniqueId();
        islands.members = Set.of(owner.getUniqueId(), player.getUniqueId());
        MinionData d = place("cobblestone", 3); // placed by `player`, a member
        d.storage.put("m:COBBLESTONE", 100L);
        d.upgrades.add("compactor");
        assertNull(manager.pickUp(owner, d).error());
        assertEquals(0, manager.store().size());
        assertEquals(0, count(owner, "m:COBBLESTONE"));
        assertEquals(0, count(owner, "c:minion_cobblestone_3"));
        assertEquals(100, count(player, "m:COBBLESTONE"));
        assertEquals(1, count(player, "c:minion_cobblestone_3"));
        assertEquals(1, count(player, "c:minion_upgrade_compactor"));
    }

    // ---------------------------------------------------------------- durable order of payments

    @Test
    public void paymentsAreWrittenToThePlayerFileBeforeTheMinionChanges() {
        MinionData d = place("cobblestone", 1);
        List<String> seen = new ArrayList<>();
        manager.playerSaver(p -> seen.add("tier=" + d.tier + " enchanted=" + count(p, "c:minion_enchanted_cobblestone")
            + " fuel=" + d.fuelMs + " upgrades=" + d.upgrades.size()));
        player.getInventory().addItem(items.create("minion_enchanted_cobblestone", 8));
        assertNull(manager.tierUp(player, d).error());
        assertEquals(2, d.tier);
        assertEquals("the payment was already taken, the tier not yet raised", "tier=1 enchanted=0 fuel=0.0 upgrades=0", seen.get(0));

        player.getInventory().addItem(new ItemStack(Material.COAL, 1));
        assertNull(manager.addFuel(player, d, manager.config().fuels.get("coal"), false).error());
        assertEquals("tier=2 enchanted=0 fuel=0.0 upgrades=0", seen.get(1));

        player.getInventory().addItem(items.create("minion_upgrade_flycatcher"));
        assertNull(manager.installUpgrade(player, d, manager.config().upgrades.get("flycatcher")).error());
        assertTrue(seen.get(2).endsWith("upgrades=0"));

        player.getInventory().addItem(items.create("minion_skin_pumpkin_hat"));
        assertNull(manager.setSkin(player, d, manager.config().skins.get("pumpkin_hat")).error());
        assertEquals(4, seen.size());
    }

    @Test
    public void placingConsumesTheItemAndSavesThePlayerBeforeTheMinionExists() {
        List<String> order = new ArrayList<>();
        manager.playerSaver(p -> order.add("save, minions=" + manager.store().size()));
        var r = manager.place(player, MinionItems.createMinion(items, "coal", 1, null), world.getBlockAt(0, 63, 0), BlockFace.UP,
            () -> order.add("consume, minions=" + manager.store().size()));
        assertNull(r.error());
        assertEquals(List.of("consume, minions=0", "save, minions=0"), order);
        assertEquals(1, manager.store().size());
    }

    @Test
    public void aRefusedPlacementDoesNotConsumeOrSave() {
        List<String> order = new ArrayList<>();
        manager.playerSaver(p -> order.add("save"));
        var r = manager.place(player, MinionItems.createMinion(items, "coal", 1, null), world.getBlockAt(40, 63, 40), BlockFace.UP,
            () -> order.add("consume"));
        assertNotNull(r.error());
        assertTrue(order.isEmpty());
    }

    @Test
    public void everyMinionItemIsCreatedWithAUid() {
        ItemStack stack = MinionItems.createMinion(items, "coal", 1, null);
        assertNotNull(MinionItems.parse(items, stack).uid());
        assertNotEquals(MinionItems.parse(items, MinionItems.createMinion(items, "coal", 1, null)).uid(),
            MinionItems.parse(items, stack).uid());
    }

    // ---------------------------------------------------------------- owed items are written before they are handed out

    @Test
    public void owedItemsAreClearedOnDiskBeforeTheyAreGiven() {
        MinionData d = place("cobblestone", 3);
        d.storage.put("m:COBBLESTONE", 70L);
        List<String> flushes = new ArrayList<>();
        manager.profileFlusher(() -> flushes.add("owed=" + manager.profileOf(player.getUniqueId()).owed.size()
            + " cobble=" + count(player, "m:COBBLESTONE") + " minionItem=" + count(player, "c:minion_cobblestone_3")));
        player.getInventory().clear();
        blockExtraSlots();
        // not online-delivered yet: park the owed list the way an offline return does
        player.setGameMode(org.bukkit.GameMode.SURVIVAL);
        manager.returnToOwner(d, "test");
        // flush 1 (after the owed list was filled, nothing given yet), flush 2 (list emptied, still nothing given)
        assertEquals("owed=2 cobble=0 minionItem=0", flushes.get(0));
        assertEquals("owed=0 cobble=0 minionItem=0", flushes.get(1));
        assertEquals(2, flushes.size());
        assertEquals(70, count(player, "m:COBBLESTONE"));
        assertTrue(manager.profileOf(player.getUniqueId()).owed.isEmpty());
    }

    @Test
    public void whatDidNotFitIsWrittenBackAfterwards() {
        MinionData d = place("cobblestone", 3);
        d.storage.put("m:COBBLESTONE", 64L * 40);
        blockExtraSlots();
        for (int i = 0; i < 35; i++) {
            player.getInventory().addItem(new ItemStack(Material.DIRT, 64));
        }
        List<Integer> owedAtFlush = new ArrayList<>();
        manager.profileFlusher(() -> owedAtFlush.add(manager.profileOf(player.getUniqueId()).owed.size()));
        manager.returnToOwner(d, "test");
        // flush after adding, flush after emptying, flush after writing the leftovers back
        assertEquals(List.of(3, 0, 1), owedAtFlush.subList(0, 3).stream().map(n -> n > 1 ? 3 : n).toList());
        assertFalse(manager.profileOf(player.getUniqueId()).owed.isEmpty());
    }

    @Test
    public void aFullInventoryDoesNotWriteTheProfileEveryHalfMinute() {
        MinionData d = place("cobblestone", 3);
        blockExtraSlots();
        for (int i = 0; i < 36; i++) {
            player.getInventory().addItem(new ItemStack(Material.DIRT, 64));
        }
        manager.returnToOwner(d, "test");
        AtomicLong flushes = new AtomicLong();
        manager.profileFlusher(flushes::incrementAndGet);
        manager.deliverOwed(player, false);
        manager.deliverOwed(player, false);
        assertEquals(0, flushes.get());
        assertFalse(manager.profileOf(player.getUniqueId()).owed.isEmpty());
    }

    private void blockExtraSlots() {
        for (int i = 36; i < player.getInventory().getSize(); i++) {
            player.getInventory().setItem(i, new ItemStack(Material.BEDROCK));
        }
    }

    // ---------------------------------------------------------------- orphans

    @Test
    public void purgeOrphansWorksWithoutTheTickerEverHavingSeenTheMinion() {
        MinionData d = place("cobblestone", 1);
        islands.exists = false; // island deleted, no replacement
        assertEquals(1, manager.purge(null, true));
        assertEquals(0, manager.store().size());
        assertEquals(1, count(player, "c:minion_cobblestone_1"));
    }

    @Test
    public void purgeOrphansLeavesMinionsOnALiveIslandAlone() {
        place("cobblestone", 1);
        assertEquals(0, manager.purge(null, true));
        assertEquals(1, manager.store().size());
    }

    @Test
    public void aMinionWhoseIslandStaysMissingForHoursGoesBackToItsOwner() {
        AtomicLong now = new AtomicLong(9_000_000);
        MinionManager timed = new MinionManager(core, manager.config(), manager.store(), manager.logic(),
            new MinionVisuals(manager.config(), plugin.getLogger()), now::get);
        MinionData d = place("cobblestone", 3);
        d.lastTick = now.get();
        d.storage.put("m:COBBLESTONE", 12L);
        islands.exists = false;
        MinionStoreHelper.forceIslandCheck(timed, d);
        timed.tick();
        assertEquals(Status.ISLAND_MISSING, timed.statusOf(d));
        assertEquals(1, manager.store().size());
        now.addAndGet(5 * 3_600_000L);
        MinionStoreHelper.forceIslandCheck(timed, d);
        timed.tick();
        assertEquals("not yet: the island may just be loading", 1, manager.store().size());
        now.addAndGet(2 * 3_600_000L);
        MinionStoreHelper.forceIslandCheck(timed, d);
        timed.tick();
        assertEquals(0, manager.store().size());
        assertEquals(12, count(player, "m:COBBLESTONE"));
    }

    @Test
    public void anIslandThatComesBackResetsTheMissingClock() {
        AtomicLong now = new AtomicLong(9_000_000);
        MinionManager timed = new MinionManager(core, manager.config(), manager.store(), manager.logic(),
            new MinionVisuals(manager.config(), plugin.getLogger()), now::get);
        MinionData d = place("cobblestone", 3);
        d.lastTick = now.get();
        islands.exists = false;
        MinionStoreHelper.forceIslandCheck(timed, d);
        timed.tick();
        now.addAndGet(5 * 3_600_000L);
        islands.exists = true;
        MinionStoreHelper.forceIslandCheck(timed, d);
        timed.tick();
        islands.exists = false;
        now.addAndGet(3_600_000L);
        MinionStoreHelper.forceIslandCheck(timed, d);
        timed.tick();
        now.addAndGet(5 * 3_600_000L);
        MinionStoreHelper.forceIslandCheck(timed, d);
        timed.tick();
        assertEquals(1, manager.store().size());
    }

    // ---------------------------------------------------------------- what you made while away

    @Test
    public void theJoinSummaryTellsWhatWasMadeOnceAndMarksItSeen() {
        MinionData d = place("cobblestone", 1);
        d.produced = 1234;
        manager.joinSummary(player);
        String message = String.valueOf(player.nextMessage());
        assertTrue(message, message.contains("1.2k") || message.contains("1,234"));
        assertEquals(1234, d.seenProduced);
        manager.joinSummary(player);
        assertNull("nothing new, nothing said", player.nextMessage());
    }

    @Test
    public void theJoinSummaryMentionsFullMinions() {
        MinionData d = place("cobblestone", 1);
        d.storage.put("m:COBBLESTONE", 64L);
        manager.joinSummary(player);
        String message = String.valueOf(player.nextMessage());
        assertTrue(message, message.contains("full"));
    }

    // ---------------------------------------------------------------- the menu

    @Test
    public void anOpenMenuRedrawsInPlaceWhenTheStorageChanges() {
        MinionData d = place("cobblestone", 3);
        MinionGui gui = new MinionGui(core, manager);
        gui.open(player, d);
        var inv = player.getOpenInventory().getTopInventory();
        assertEquals(Material.BLACK_STAINED_GLASS_PANE, inv.getItem(10).getType());
        d.storage.put("m:COBBLESTONE", 5L);
        gui.refreshAll();
        assertSame("same inventory, redrawn", inv, player.getOpenInventory().getTopInventory());
        assertEquals(Material.COBBLESTONE, inv.getItem(10).getType());
        assertEquals(5, inv.getItem(10).getAmount());
        d.storage.clear();
        gui.refreshAll();
        assertEquals(Material.BLACK_STAINED_GLASS_PANE, inv.getItem(10).getType());
        gui.shutdown();
    }

    @Test
    public void aClosedMenuStopsBeingRefreshed() {
        MinionData d = place("cobblestone", 3);
        MinionGui gui = new MinionGui(core, manager);
        gui.open(player, d);
        player.closeInventory();
        d.storage.put("m:COBBLESTONE", 5L);
        gui.refreshAll(); // must not throw
        gui.shutdown();
    }

    // ---------------------------------------------------------------- recipes and fuels

    @Test
    public void upgradeHatAndFuelRecipesAreRegisteredFromTheConfig() {
        for (String id : List.of("auto_smelter", "compactor", "flycatcher", "pumpkin_hat", "hamster_wheel")) {
            assertNotNull(id, Bukkit.getRecipe(new org.bukkit.NamespacedKey(plugin, "minion_item_" + id)));
        }
        var recipe = (org.bukkit.inventory.ShapedRecipe) Bukkit.getRecipe(new org.bukkit.NamespacedKey(plugin, "minion_item_compactor"));
        assertEquals(items.create("minion_upgrade_compactor").getItemMeta().getItemModel(),
            recipe.getResult().getItemMeta().getItemModel());
        assertTrue(recipe.getChoiceMap().get('E') instanceof org.bukkit.inventory.RecipeChoice.ExactChoice);
    }

    @Test
    public void theCompressedResourceIsTheFuelAndFuelsAreAnItemOfTheirOwn() {
        var fuel = manager.config().fuels.get("enchanted_coal");
        assertEquals("c:minion_enchanted_coal", fuel.key());
        assertEquals("c:minion_enchanted_charcoal", manager.config().fuels.get("enchanted_charcoal").key());
        MinionData d = place("cobblestone", 1);
        player.getInventory().addItem(items.create("minion_enchanted_coal", 2));
        assertNull(manager.addFuel(player, d, fuel, false).error());
        assertEquals(24 * 3_600_000.0, d.fuelMs, 1e-6);
        assertEquals(1, count(player, "c:minion_enchanted_coal"));
        assertEquals(Material.COAL, MinionItems.fuelStack(items, fuel).getType());
    }

    @Test
    public void twoFlycatchersStackButTwoCompactorsDoNot() {
        MinionData d = place("cobblestone", 1);
        player.getInventory().addItem(items.create("minion_upgrade_flycatcher", 2));
        var fly = manager.config().upgrades.get("flycatcher");
        assertNull(manager.installUpgrade(player, d, fly).error());
        assertNull(manager.installUpgrade(player, d, fly).error());
        assertEquals(List.of("flycatcher", "flycatcher"), d.upgrades);
        var type = manager.config().types.get("cobblestone");
        assertEquals(1.4, manager.logic().speeds(d, type).plain(), 1e-9);
        assertNull(manager.removeUpgrade(player, d, "flycatcher").error());
        assertEquals(List.of("flycatcher"), d.upgrades);
        player.getInventory().addItem(items.create("minion_upgrade_compactor", 2));
        d.upgrades.clear();
        var compactor = manager.config().upgrades.get("compactor");
        assertNull(manager.installUpgrade(player, d, compactor).error());
        assertNotNull(manager.installUpgrade(player, d, compactor).error());
    }

    @Test
    public void everyConfiguredItemRecipeIsRegisteredWithExactChoices() {
        assertTrue(manager.config().itemRecipes.size() >= 10);
        for (String id : manager.config().itemRecipes.keySet()) {
            var recipe = Bukkit.getRecipe(new org.bukkit.NamespacedKey(plugin, "minion_item_" + id));
            assertNotNull("recipe " + id + " was not registered (see the log)", recipe);
            var shaped = (org.bukkit.inventory.ShapedRecipe) recipe;
            assertFalse(shaped.getChoiceMap().isEmpty());
            shaped.getChoiceMap().values().stream().filter(java.util.Objects::nonNull)
                .forEach(c -> assertTrue(c instanceof org.bukkit.inventory.RecipeChoice.ExactChoice));
        }
    }

    @Test
    public void nonPlayerDestructionOfAPlacedBlockIsCancelled() {
        MinionData d = place("wheat", 1);
        Block crop = track(d, 1, Material.WHEAT);
        var destroy = new com.destroystokyo.paper.event.block.BlockDestroyEvent(crop, Material.AIR.createBlockData(),
            Material.AIR.createBlockData(), 0, true);
        server.getPluginManager().callEvent(destroy);
        assertTrue(destroy.isCancelled());
        Block other = world.getBlockAt(3, 64, 3);
        other.setType(Material.WHEAT);
        var free = new com.destroystokyo.paper.event.block.BlockDestroyEvent(other, Material.AIR.createBlockData(),
            Material.AIR.createBlockData(), 0, true);
        server.getPluginManager().callEvent(free);
        assertFalse(free.isCancelled());
    }

    @Test
    public void shearingAndBucketsCannotChangeAPlacedBlock() {
        MinionData d = place("pumpkin", 1);
        Block pumpkin = track(d, 1, Material.PUMPKIN);
        var shear = new io.papermc.paper.event.block.PlayerShearBlockEvent(player, pumpkin, new ItemStack(Material.SHEARS),
            org.bukkit.inventory.EquipmentSlot.HAND, new ArrayList<>());
        server.getPluginManager().callEvent(shear);
        assertTrue(shear.isCancelled());
        var bucket = new org.bukkit.event.player.PlayerBucketEmptyEvent(player, pumpkin, pumpkin.getRelative(BlockFace.DOWN),
            BlockFace.UP, Material.WATER_BUCKET, new ItemStack(Material.WATER_BUCKET), org.bukkit.inventory.EquipmentSlot.HAND);
        server.getPluginManager().callEvent(bucket);
        assertTrue(bucket.isCancelled());
    }

    @Test
    public void aLongCatchUpIsReportedToTheOnlineOwnerOncePerPass() {
        AtomicLong now = new AtomicLong(20_000_000);
        MinionManager timed = new MinionManager(core, manager.config(), manager.store(), manager.logic(),
            new MinionVisuals(manager.config(), plugin.getLogger()), now::get);
        MinionData a = place("cobblestone", 1);
        a.lastTick = now.get();
        now.addAndGet(2 * 3_600_000L);
        timed.tick();
        List<String> messages = new ArrayList<>();
        for (Object m = player.nextMessage(); m != null; m = player.nextMessage()) {
            messages.add(String.valueOf(m));
        }
        assertEquals(1, messages.stream().filter(m -> m.contains("While you were away")).count());
        assertEquals("seen, so the join summary stays quiet", a.produced, a.seenProduced);
        now.addAndGet(1000);
        timed.tick();
        for (Object m = player.nextMessage(); m != null; m = player.nextMessage()) {
            assertFalse("a normal tick says nothing about being away: " + m, String.valueOf(m).contains("While you were away"));
        }
    }
}
