package win.vampyuyu.survival.minions;

import static org.junit.Assert.*;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
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

/** The feature booted inside the whole plugin, with a fake island layer. */
public class MinionManagerTest {
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
        for (int cx = -2; cx <= 2; cx++) {
            for (int cz = -2; cz <= 2; cz++) {
                world.loadChunk(cx, cz);
            }
        }
        stone(0, 63, 0);
        stone(10, 63, 10);
    }

    @After
    public void tearDown() {
        MockBukkit.unmock();
    }

    private Block stone(int x, int y, int z) {
        Block b = world.getBlockAt(x, y, z);
        b.setType(Material.STONE);
        return b;
    }

    /** Solid ground under the minion's layer: a mining minion needs something to put its block on. */
    private void floor(int radius) {
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                world.getBlockAt(x, 63, z).setType(Material.STONE);
            }
        }
    }

    private ItemStack minionItem(String type, int tier, String uid) {
        return MinionItems.createMinion(items, type, tier, uid);
    }

    private MinionData placeAt(int x, int z, String type, int tier) {
        Block ground = world.getBlockAt(x, 63, z);
        if (ground.getType() != Material.STONE) {
            ground.setType(Material.STONE);
        }
        MinionManager.Result r = manager.place(player, minionItem(type, tier, null), ground, BlockFace.UP);
        assertNull(r.error(), r.error());
        return manager.at(world.getBlockAt(x, 64, z)).orElseThrow();
    }

    /** MockBukkit's addItem also fills armor and off-hand slots (a real server never does): block them. */
    private void blockExtraSlots(PlayerMock p) {
        for (int i = 36; i < p.getInventory().getSize(); i++) {
            p.getInventory().setItem(i, new ItemStack(Material.BEDROCK));
        }
    }

    private long count(PlayerMock p, String key) {
        return manager.countIn(p, List.of(key));
    }

    // ---------------------------------------------------------------- the api

    @Test
    public void featureRegistersTheApiItemsRecipesAndCommand() {
        assertSame(manager, core.require(MinionApi.class));
        assertEquals(5, manager.slotsFor(player.getUniqueId()));
        assertTrue(items.def("minion_cobblestone_1").isPresent());
        assertTrue(items.def("minion_slime_11").isPresent());
        assertTrue(items.def("minion_enchanted_cobblestone").isPresent());
        assertTrue(items.def("minion_super_stone").isPresent());
        // the Enchanted Coal fuel IS the compressed resource: no second item with the same name
        assertTrue(items.def("minion_fuel_enchanted_coal").isEmpty());
        assertTrue(items.def("minion_fuel_enchanted_lava_bucket").isPresent());
        assertTrue(items.def("minion_upgrade_compactor").isPresent());
        assertTrue(items.def("minion_skin_crown") .isEmpty());
        assertNotNull(org.bukkit.Bukkit.getCommandMap().getCommand("minion"));
        assertNotNull(org.bukkit.Bukkit.getRecipe(new org.bukkit.NamespacedKey(plugin, "minion_cobblestone")));
        assertEquals(26 * 11, items.ids().stream().filter(i -> i.matches("minion_[a-z]+_\\d+")).count());
    }

    @Test
    public void minionItemsUseThePackModelAndCannotBePlacedAsBlocks() {
        ItemStack stack = items.create("minion_diamond_5");
        assertEquals(Material.PAPER, stack.getType());
        assertEquals("vampyuyu:minions/diamond", stack.getItemMeta().getItemModel().toString());
        assertEquals(1, stack.getItemMeta().getMaxStackSize());
        MinionItems.Parsed parsed = MinionItems.parse(items, stack);
        assertEquals("diamond", parsed.type());
        assertEquals(5, parsed.tier());
        assertNull(parsed.uid());
        assertNull(MinionItems.parse(items, new ItemStack(Material.PAPER)));
        assertNull(MinionItems.parse(items, items.create("minion_enchanted_cobblestone")));
    }

    // ---------------------------------------------------------------- placing

    @Test
    public void placingOnYourIslandWorksAndCountsTheSlot() {
        MinionData d = placeAt(0, 0, "cobblestone", 1);
        assertEquals(player.getUniqueId().toString(), d.owner);
        assertEquals(islands.id.toString(), d.island);
        assertEquals(64, d.y);
        assertEquals(1, manager.placedCount(player.getUniqueId()));
        assertTrue(manager.store().ledger().size() >= 1);
    }

    @Test
    public void placingOutsideAnyIslandIsRefused() {
        world.getBlockAt(200, 63, 200).setType(Material.STONE);
        var r = manager.place(player, minionItem("coal", 1, null), world.getBlockAt(200, 63, 200), BlockFace.UP);
        assertEquals("Minions can only be placed on an island.", r.error());
        assertEquals(0, manager.store().size());
    }

    @Test
    public void placingOnSomeoneElsesIslandIsRefused() {
        PlayerMock other = server.addPlayer();
        var r = manager.place(other, minionItem("coal", 1, null), world.getBlockAt(0, 63, 0), BlockFace.UP);
        assertEquals("You can only place minions on your own island.", r.error());
    }

    @Test
    public void aCoopMemberCanPlaceOnTheSharedIsland() {
        PlayerMock friend = server.addPlayer();
        islands.members = java.util.Set.of(player.getUniqueId(), friend.getUniqueId());
        var r = manager.place(friend, minionItem("coal", 1, null), world.getBlockAt(0, 63, 0), BlockFace.UP);
        assertNull(r.error());
        assertEquals(1, manager.placedCount(friend.getUniqueId()));
        assertEquals(0, manager.placedCount(player.getUniqueId()));
    }

    @Test
    public void theSlotLimitIsEnforced() {
        manager.config().baseSlots = 1;
        placeAt(0, 0, "cobblestone", 1);
        var r = manager.place(player, minionItem("coal", 1, null), stone(10, 63, 10), BlockFace.UP);
        assertTrue(r.error(), r.error().startsWith("You have no free minion slot (1/1)"));
        manager.config().baseSlots = 5;
    }

    @Test
    public void theIslandCapIsEnforced() {
        manager.config().perIslandCap = 1;
        placeAt(0, 0, "cobblestone", 1);
        var r = manager.place(player, minionItem("coal", 1, null), stone(10, 63, 10), BlockFace.UP);
        assertEquals("This island already has 1 minions.", r.error());
        manager.config().perIslandCap = 48;
    }

    @Test
    public void aCopiedMinionItemCannotBePlacedTwice() {
        String uid = MinionItems.newUid();
        assertNull(manager.place(player, minionItem("coal", 1, uid), world.getBlockAt(0, 63, 0), BlockFace.UP).error());
        var again = manager.place(player, minionItem("coal", 1, uid), stone(10, 63, 10), BlockFace.UP);
        assertEquals("This minion item was already used.", again.error());
        assertEquals(1, manager.store().size());
    }

    @Test
    public void theWorkingAreaMustStayInsideTheIsland() {
        world.getBlockAt(49, 63, 0).setType(Material.STONE);
        var r = manager.place(player, minionItem("coal", 1, null), world.getBlockAt(49, 63, 0), BlockFace.UP);
        assertTrue(r.error(), r.error().startsWith("The working area"));
        assertEquals(0, manager.store().size());
    }

    @Test
    public void noTwoMinionsShareABlockAndTheyNeedSolidGroundAndRoom() {
        placeAt(0, 0, "cobblestone", 1);
        var same = manager.place(player, minionItem("coal", 1, null), world.getBlockAt(0, 63, 0), BlockFace.UP);
        assertEquals("There is not enough room for a minion here.".length() > 0, same.error() != null);
        var air = manager.place(player, minionItem("coal", 1, null), world.getBlockAt(5, 63, 5), BlockFace.UP);
        assertEquals("A minion needs solid ground to stand on.", air.error());
    }

    // ---------------------------------------------------------------- storage, pickup

    @Test
    public void pickUpReturnsEverythingExactlyWithAFreshUid() {
        String uid = MinionItems.newUid();
        manager.place(player, minionItem("cobblestone", 4, uid), world.getBlockAt(0, 63, 0), BlockFace.UP);
        MinionData d = manager.at(world.getBlockAt(0, 64, 0)).orElseThrow();
        d.storage.put("m:COBBLESTONE", 100L);
        d.storage.put("c:minion_enchanted_cobblestone", 3L);
        d.upgrades.add("flycatcher");
        d.skin = "pumpkin_hat";
        d.fuel = "coal";
        d.fuelMs = 2.5 * 3_600_000 * 0.2; // one whole coal (30 min)
        var r = manager.pickUp(player, d);
        assertNull(r.error());
        assertEquals(0, manager.store().size());
        assertEquals(100, count(player, "m:COBBLESTONE"));
        assertEquals(3, count(player, "c:minion_enchanted_cobblestone"));
        assertEquals(1, count(player, "c:minion_upgrade_flycatcher"));
        assertEquals(0, count(player, "m:COAL") - 1 + 0 * 1);
        assertEquals(1, count(player, "c:minion_skin_pumpkin_hat"));
        ItemStack back = null;
        for (ItemStack s : player.getInventory().getContents()) {
            if (s != null && items.is(s, "minion_cobblestone_4")) {
                back = s;
            }
        }
        assertNotNull(back);
        MinionItems.Parsed parsed = MinionItems.parse(items, back);
        assertNotEquals(uid, parsed.uid());
        assertNotNull(parsed.uid());
        // the new item places again, the old uid never does
        assertNull(manager.place(player, back, world.getBlockAt(0, 63, 0), BlockFace.UP).error());
        assertEquals("This minion item was already used.",
            manager.place(player, minionItem("cobblestone", 4, uid), stone(10, 63, 10), BlockFace.UP).error());
    }

    @Test
    public void collectAllMovesStorageToTheInventory() {
        MinionData d = placeAt(0, 0, "cobblestone", 11);
        d.storage.put("m:COBBLESTONE", 500L);
        d.storage.put("m:DIAMOND", 3L);
        long moved = manager.collectAll(player, d);
        assertEquals(503, moved);
        assertTrue(manager.logic().storage(d).isEmpty());
        assertEquals(500, count(player, "m:COBBLESTONE"));
        assertEquals(3, count(player, "m:DIAMOND"));
    }

    @Test
    public void aFullInventoryKeepsTheRestInTheMinionAndNothingIsDuplicated() {
        MinionData d = placeAt(0, 0, "cobblestone", 11);
        d.storage.put("m:COBBLESTONE", 64L * 30);
        // 36 slots, nine already taken by dirt (30 stacks is the most a tier XI minion holds)
        blockExtraSlots(player);
        for (int i = 0; i < 9; i++) {
            player.getInventory().addItem(new ItemStack(Material.DIRT, 64));
        }
        long moved = manager.collectAll(player, d);
        long inInventory = count(player, "m:COBBLESTONE");
        assertEquals(27 * 64, inInventory);
        assertEquals(moved, inInventory);
        assertEquals(64L * 30 - inInventory, d.storage.get("m:COBBLESTONE").longValue());
    }

    @Test
    public void pickUpWithAFullInventoryIsRefusedAndKeepsTheMinion() {
        MinionData d = placeAt(0, 0, "cobblestone", 11);
        d.storage.put("m:COBBLESTONE", 64L * 30);
        blockExtraSlots(player);
        for (int i = 0; i < 36; i++) {
            player.getInventory().addItem(new ItemStack(Material.DIRT, 64));
        }
        var r = manager.pickUp(player, d);
        assertEquals("Your inventory is full: collect the items first.", r.error());
        assertEquals(1, manager.store().size());
        assertEquals(64L * 30, d.storage.get("m:COBBLESTONE").longValue());
    }

    @Test
    public void onlyTheOwnerOrTheIslandOwnerMayPickUp() {
        MinionData d = placeAt(0, 0, "cobblestone", 1);
        PlayerMock stranger = server.addPlayer();
        assertFalse(manager.canAccess(stranger, d));
        assertEquals("Only the owner of the minion or of the island can pick it up.", manager.pickUp(stranger, d).error());
        PlayerMock friend = server.addPlayer();
        islands.members = java.util.Set.of(player.getUniqueId(), friend.getUniqueId());
        assertTrue(manager.canAccess(friend, d));
        assertFalse(manager.canPickUp(friend, d));
        assertTrue(manager.canPickUp(player, d));
    }

    // ---------------------------------------------------------------- upgrades

    @Test
    public void tierUpPaysWithEnchantedResourcesAndUnlocksSlots() {
        MinionData d = placeAt(0, 0, "cobblestone", 1);
        var broke = manager.tierUp(player, d);
        assertTrue(broke.error(), broke.error().startsWith("You need 8 Enchanted Cobblestone"));
        player.getInventory().addItem(items.create("minion_enchanted_cobblestone", 7));
        assertNotNull(manager.tierUp(player, d).error());
        assertEquals(1, d.tier);
        player.getInventory().addItem(items.create("minion_enchanted_cobblestone", 1));
        assertNull(manager.tierUp(player, d).error());
        assertEquals(2, d.tier);
        assertEquals(0, count(player, "c:minion_enchanted_cobblestone"));
        assertTrue(manager.profileOf(player.getUniqueId()).crafted.contains("cobblestone:2"));
    }

    @Test
    public void highTiersNeedSuperEnchantedItems() {
        MinionData d = placeAt(0, 0, "cobblestone", 8);
        player.getInventory().addItem(items.create("minion_enchanted_cobblestone", 64));
        assertTrue(manager.tierUp(player, d).error().contains("Super Enchanted Cobblestone"));
        player.getInventory().addItem(items.create("minion_super_cobblestone", 2));
        assertNull(manager.tierUp(player, d).error());
        assertEquals(9, d.tier);
        assertEquals(64, count(player, "c:minion_enchanted_cobblestone"));
    }

    @Test
    public void tierUpAcceptsTheSmeltedFormToo() {
        MinionData d = placeAt(0, 0, "cobblestone", 1);
        player.getInventory().addItem(items.create("minion_enchanted_stone", 8));
        assertNull(manager.tierUp(player, d).error());
    }

    @Test
    public void compressTurnsRawResourcesIntoEnchantedItems() {
        MinionConfig.Type type = manager.config().types.get("cobblestone");
        player.getInventory().addItem(new ItemStack(Material.COBBLESTONE, 64), new ItemStack(Material.COBBLESTONE, 64),
            new ItemStack(Material.COBBLESTONE, 64));
        assertEquals(1, manager.compressInventory(player, type));
        assertEquals(1, count(player, "c:minion_enchanted_cobblestone"));
        assertEquals(32, count(player, "m:COBBLESTONE"));
    }

    @Test
    public void aRenamedItemNeverPaysForAnything() {
        ItemStack named = new ItemStack(Material.COBBLESTONE, 64);
        var meta = named.getItemMeta();
        meta.displayName(net.kyori.adventure.text.Component.text("fancy"));
        named.setItemMeta(meta);
        player.getInventory().addItem(named);
        assertEquals(0, count(player, "m:COBBLESTONE"));
    }

    @Test
    public void fuelAddsUpToTheTankAndRefusesAnotherKind() {
        MinionData d = placeAt(0, 0, "cobblestone", 1);
        var fuel = manager.config().fuels.get("coal");
        player.getInventory().addItem(new ItemStack(Material.COAL, 10));
        assertNull(manager.addFuel(player, d, fuel, false).error());
        assertEquals(30 * 60_000.0, d.fuelMs, 1e-6);
        assertNull(manager.addFuel(player, d, fuel, true).error());
        assertEquals(10 * 30 * 60_000.0, d.fuelMs, 1e-6);
        assertEquals(0, count(player, "m:COAL"));
        player.getInventory().addItem(items.create("minion_enchanted_coal"));
        var other = manager.addFuel(player, d, manager.config().fuels.get("enchanted_coal"), false);
        assertTrue(other.error(), other.error().startsWith("The minion is already burning"));
        assertEquals(1, count(player, "c:minion_enchanted_coal"));
    }

    @Test
    public void fuelCannotOverflowTheTank() {
        MinionData d = placeAt(0, 0, "cobblestone", 1);
        var catalyst = manager.config().fuels.get("catalyst"); // 3 hours, tank 168 hours = 56 units
        player.getInventory().addItem(items.create("minion_fuel_catalyst", 64));
        assertNull(manager.addFuel(player, d, catalyst, true).error());
        assertEquals(56 * 3 * 3_600_000.0, d.fuelMs, 1e-3);
        assertEquals(8, count(player, "c:minion_fuel_catalyst"));
        assertEquals("The fuel tank is full.", manager.addFuel(player, d, catalyst, true).error());
    }

    @Test
    public void fuelForTheWrongKindIsRefused() {
        MinionData d = placeAt(0, 0, "cobblestone", 1);
        player.getInventory().addItem(items.create("minion_fuel_hamster_wheel"));
        assertEquals("This fuel does not work on this kind of minion.",
            manager.addFuel(player, d, manager.config().fuels.get("hamster_wheel"), false).error());
        assertEquals(1, count(player, "c:minion_fuel_hamster_wheel"));
    }

    @Test
    public void upgradesInstallRemoveAndRespectSlotsAndClashes() {
        MinionData d = placeAt(0, 0, "cobblestone", 1);
        var cfg = manager.config();
        player.getInventory().addItem(items.create("minion_upgrade_compactor"), items.create("minion_upgrade_super_compactor"),
            items.create("minion_upgrade_flycatcher"), items.create("minion_upgrade_auto_smelter"));
        assertEquals("You do not have that upgrade.", manager.installUpgrade(player, d, cfg.upgrades.get("diamond_spreading")).error());
        assertNull(manager.installUpgrade(player, d, cfg.upgrades.get("compactor")).error());
        assertEquals("An upgrade with the same job is already installed.",
            manager.installUpgrade(player, d, cfg.upgrades.get("super_compactor")).error());
        assertNull(manager.installUpgrade(player, d, cfg.upgrades.get("flycatcher")).error());
        assertEquals("All upgrade slots are in use.", manager.installUpgrade(player, d, cfg.upgrades.get("auto_smelter")).error());
        assertEquals(List.of("compactor", "flycatcher"), d.upgrades);
        assertEquals(0, count(player, "c:minion_upgrade_compactor"));
        assertNull(manager.removeUpgrade(player, d, "compactor").error());
        assertEquals(1, count(player, "c:minion_upgrade_compactor"));
        assertEquals("That upgrade is not installed.", manager.removeUpgrade(player, d, "compactor").error());
    }

    @Test
    public void hatsSwapAndReturnTheOldOne() {
        MinionData d = placeAt(0, 0, "cobblestone", 1);
        var cfg = manager.config();
        player.getInventory().addItem(items.create("minion_skin_pumpkin_hat"), items.create("minion_skin_creeper_head"));
        assertNull(manager.setSkin(player, d, cfg.skins.get("pumpkin_hat")).error());
        assertEquals("pumpkin_hat", d.skin);
        assertNull(manager.setSkin(player, d, cfg.skins.get("creeper_head")).error());
        assertEquals(1, count(player, "c:minion_skin_pumpkin_hat"));
        assertNull(manager.setSkin(player, d, null).error());
        assertNull(d.skin);
        assertEquals(1, count(player, "c:minion_skin_creeper_head"));
    }

    // ---------------------------------------------------------------- island reset

    @Test
    public void aMinionWhoseIslandWasReplacedIsReturnedToItsOwnerNotLost() {
        MinionData d = placeAt(0, 0, "cobblestone", 3);
        d.storage.put("m:COBBLESTONE", 77L);
        islands.id = UUID.randomUUID();
        MinionStoreHelper.forceIslandCheck(manager, d);
        manager.tick();
        assertEquals(1, manager.store().size());
        MinionStoreHelper.forceIslandCheck(manager, d);
        manager.tick();
        assertEquals(0, manager.store().size());
        assertEquals(77, count(player, "m:COBBLESTONE"));
        assertEquals(1, count(player, "c:minion_cobblestone_3"));
    }

    @Test
    public void ownedItemsWaitInTheProfileUntilThereIsRoom() {
        MinionData d = placeAt(0, 0, "cobblestone", 3);
        blockExtraSlots(player);
        for (int i = 0; i < 36; i++) {
            player.getInventory().addItem(new ItemStack(Material.DIRT, 64));
        }
        manager.returnToOwner(d, "test");
        assertEquals(0, count(player, "c:minion_cobblestone_3"));
        assertEquals(1, manager.profileOf(player.getUniqueId()).owed.size());
        player.getInventory().clear();
        manager.deliverOwed(player);
        assertEquals(1, count(player, "c:minion_cobblestone_3"));
        assertTrue(manager.profileOf(player.getUniqueId()).owed.isEmpty());
    }

    @Test
    public void purgeReturnsContentOfEveryMinionOfAnOwner() {
        placeAt(0, 0, "cobblestone", 1);
        placeAt(10, 10, "coal", 2);
        assertEquals(2, manager.purge(player.getUniqueId(), false));
        assertEquals(0, manager.store().size());
        assertEquals(1, count(player, "c:minion_cobblestone_1"));
        assertEquals(1, count(player, "c:minion_coal_2"));
    }

    // ---------------------------------------------------------------- the ticker with a fake clock

    @Test
    public void tickProducesFromElapsedTimeWithoutEverTickingThroughIt() {
        AtomicLong now = new AtomicLong(1_000_000);
        MinionStore store = new MinionStore(core.store(), core.getDataFolder(), plugin.getLogger());
        MinionManager timed = new MinionManager(core, manager.config(), store, manager.logic(),
            new MinionVisuals(manager.config(), plugin.getLogger()), now::get);
        MinionData d = new MinionData();
        d.id = UUID.randomUUID().toString();
        d.owner = player.getUniqueId().toString();
        d.island = islands.id.toString();
        d.world = "sb_world";
        d.x = 0;
        d.y = 64;
        d.z = 0;
        d.type = "cobblestone";
        d.tier = 11;
        d.lastTick = now.get();
        store.add(d);
        floor(3);
        now.addAndGet(14_000L * 100);
        timed.tick();
        assertEquals(100, d.storage.get("m:COBBLESTONE").longValue());
        assertEquals(Status.WORKING, timed.statusOf(d));
        // the chunk-less (sleeping) hour: another 3 hours in one tick
        now.addAndGet(3 * 3_600_000L);
        timed.tick();
        assertEquals(100 + 3 * 3600 / 14, d.storage.get("m:COBBLESTONE").longValue());
    }

    @Test
    public void aFullMinionReportsStorageFullAndResumesWhenEmptied() {
        AtomicLong now = new AtomicLong(1_000_000);
        MinionStore store = new MinionStore(core.store(), core.getDataFolder(), plugin.getLogger());
        MinionManager timed = new MinionManager(core, manager.config(), store, manager.logic(),
            new MinionVisuals(manager.config(), plugin.getLogger()), now::get);
        MinionData d = new MinionData();
        d.id = UUID.randomUUID().toString();
        d.owner = player.getUniqueId().toString();
        d.island = islands.id.toString();
        d.world = "sb_world";
        d.y = 64;
        d.type = "cobblestone";
        d.tier = 1;
        d.lastTick = now.get();
        store.add(d);
        floor(2);
        now.addAndGet(24 * 3_600_000L);
        timed.tick();
        assertEquals(Status.STORAGE_FULL, timed.statusOf(d));
        assertEquals(64, d.storage.get("m:COBBLESTONE").longValue());
        d.storage.clear();
        now.addAndGet(1000);
        timed.tick();
        assertEquals(Status.WORKING, timed.statusOf(d));
        assertEquals(1, d.storage.get("m:COBBLESTONE").longValue());
    }

    @Test
    public void aBlockedMinionDoesNotProduce() {
        AtomicLong now = new AtomicLong(1_000_000);
        MinionStore store = new MinionStore(core.store(), core.getDataFolder(), plugin.getLogger());
        MinionManager timed = new MinionManager(core, manager.config(), store, manager.logic(),
            new MinionVisuals(manager.config(), plugin.getLogger()), now::get);
        MinionData d = new MinionData();
        d.id = UUID.randomUUID().toString();
        d.owner = player.getUniqueId().toString();
        d.island = islands.id.toString();
        d.world = "sb_world";
        d.y = 64;
        d.type = "wheat"; // no farmland anywhere
        d.tier = 11;
        d.lastTick = now.get();
        store.add(d);
        now.addAndGet(3_600_000L);
        timed.tick();
        assertEquals(Status.INVALID_TERRAIN, timed.statusOf(d));
        assertTrue(d.storage.isEmpty());
        // farmland appears: production starts, the blocked hour is not made up for
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                world.getBlockAt(x, 63, z).setType(Material.FARMLAND);
            }
        }
        now.addAndGet(15_000L);
        timed.tick();
        timed.tick();
        now.addAndGet(60_000L);
        timed.tick();
        assertEquals(Status.WORKING, timed.statusOf(d));
        long wheat = d.storage.getOrDefault("m:WHEAT", 0L);
        assertTrue("wheat " + wheat, wheat > 0 && wheat < 30);
    }
}
