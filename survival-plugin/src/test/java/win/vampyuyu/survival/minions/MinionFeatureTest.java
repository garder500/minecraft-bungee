package win.vampyuyu.survival.minions;

import static org.junit.Assert.*;

import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
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
import win.vampyuyu.survival.core.Guis;
import win.vampyuyu.survival.core.Items;

/** Events, commands, the menu and the recipes, through the real plugin. */
public class MinionFeatureTest {
    private ServerMock server;
    private SurvivalPlugin plugin;
    private Core core;
    private Items items;
    private MinionManager manager;
    private WorldMock world;
    private PlayerMock player;

    @Before
    public void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(SurvivalPlugin.class);
        core = plugin.core();
        items = core.items();
        world = server.addSimpleWorld("sb_world");
        for (int cx = -2; cx <= 2; cx++) {
            for (int cz = -2; cz <= 2; cz++) {
                world.loadChunk(cx, cz);
            }
        }
        player = server.addPlayer();
        core.register(IslandApi.class, new FakeIslands(player.getUniqueId()));
        manager = (MinionManager) core.require(MinionApi.class);
        world.getBlockAt(0, 63, 0).setType(Material.STONE);
        for (int i = 36; i < player.getInventory().getSize(); i++) {
            player.getInventory().setItem(i, new ItemStack(Material.BEDROCK));
        }
    }

    @After
    public void tearDown() {
        MockBukkit.unmock();
    }

    private long count(String key) {
        return manager.countIn(player, List.of(key));
    }

    // ---------------------------------------------------------------- placing by right click

    @Test
    public void rightClickingABlockWithAMinionPlacesItAndConsumesTheItem() {
        ItemStack item = MinionItems.createMinion(items, "coal", 2, null);
        player.getInventory().setItemInMainHand(item);
        Block ground = world.getBlockAt(0, 63, 0);
        PlayerInteractEvent event = new PlayerInteractEvent(player, Action.RIGHT_CLICK_BLOCK, item, ground, BlockFace.UP,
            EquipmentSlot.HAND);
        server.getPluginManager().callEvent(event);
        assertTrue(event.isCancelled());
        assertEquals(1, manager.store().size());
        assertTrue(player.getInventory().getItemInMainHand().getType().isAir());
        assertEquals("coal", manager.store().all().get(0).type);
    }

    @Test
    public void aRefusedPlacementKeepsTheItem() {
        world.getBlockAt(300, 63, 300).setType(Material.STONE);
        ItemStack item = MinionItems.createMinion(items, "coal", 2, null);
        player.getInventory().setItemInMainHand(item);
        PlayerInteractEvent event = new PlayerInteractEvent(player, Action.RIGHT_CLICK_BLOCK, item,
            world.getBlockAt(300, 63, 300), BlockFace.UP, EquipmentSlot.HAND);
        server.getPluginManager().callEvent(event);
        assertTrue(event.isCancelled());
        assertEquals(0, manager.store().size());
        assertFalse(player.getInventory().getItemInMainHand().getType().isAir());
    }

    @Test
    public void otherItemsAreLeftAlone() {
        ItemStack item = new ItemStack(Material.PAPER);
        PlayerInteractEvent event = new PlayerInteractEvent(player, Action.RIGHT_CLICK_BLOCK, item,
            world.getBlockAt(0, 63, 0), BlockFace.UP, EquipmentSlot.HAND);
        server.getPluginManager().callEvent(event);
        assertFalse(event.isCancelled());
    }

    @Test
    public void theIslandOwnerMayPlaceEvenWhenMembersLeavesTheOwnerOut() {
        FakeIslands islands = (FakeIslands) core.require(IslandApi.class);
        islands.members = java.util.Set.of(java.util.UUID.randomUUID());
        ItemStack item = MinionItems.createMinion(items, "coal", 1, null);
        player.getInventory().setItemInMainHand(item);
        server.getPluginManager().callEvent(new PlayerInteractEvent(player, Action.RIGHT_CLICK_BLOCK, item,
            world.getBlockAt(0, 63, 0), BlockFace.UP, EquipmentSlot.HAND));
        assertEquals(1, manager.store().size());
        MinionData d = manager.store().all().get(0);
        MinionStoreHelper.forceIslandCheck(manager, d);
        manager.tick();
        MinionStoreHelper.forceIslandCheck(manager, d);
        manager.tick();
        assertEquals("the minion must not be returned", 1, manager.store().size());
    }

    // ---------------------------------------------------------------- custom items keep to themselves

    @Test
    public void customMinionItemsCannotBeEatenPlacedOrBurned() {
        ItemStack enchanted = items.create(MinionItems.enchantedId(Material.COAL));
        org.bukkit.event.player.PlayerItemConsumeEvent eat = new org.bukkit.event.player.PlayerItemConsumeEvent(player,
            enchanted);
        server.getPluginManager().callEvent(eat);
        assertTrue(eat.isCancelled());

        org.bukkit.event.player.PlayerItemConsumeEvent plain = new org.bukkit.event.player.PlayerItemConsumeEvent(player,
            new ItemStack(Material.COAL));
        server.getPluginManager().callEvent(plain);
        assertFalse(plain.isCancelled());

        org.bukkit.event.inventory.FurnaceBurnEvent burn = new org.bukkit.event.inventory.FurnaceBurnEvent(
            world.getBlockAt(5, 64, 5), enchanted, 1600);
        server.getPluginManager().callEvent(burn);
        assertTrue(burn.isCancelled());
        org.bukkit.event.inventory.FurnaceBurnEvent burnPlain = new org.bukkit.event.inventory.FurnaceBurnEvent(
            world.getBlockAt(5, 64, 5), new ItemStack(Material.COAL), 1600);
        server.getPluginManager().callEvent(burnPlain);
        assertFalse(burnPlain.isCancelled());
    }

    // ---------------------------------------------------------------- recipes

    @Test
    public void tierOneRecipeIsEightResourcesAroundATool() {
        ShapedRecipe recipe = (ShapedRecipe) Bukkit.getRecipe(new NamespacedKey(plugin, "minion_iron"));
        assertNotNull(recipe);
        assertArrayEquals(new String[] {"III", "ICI", "III"}, recipe.getShape());
        assertTrue(items.is(recipe.getResult(), "minion_iron_1"));
        assertEquals(Material.IRON_INGOT, recipe.getIngredientMap().get('I').getType());
        assertEquals(Material.STONE_PICKAXE, recipe.getIngredientMap().get('C').getType());
    }

    @Test
    public void everyTypeHasARecipe() {
        for (String type : manager.config().types.keySet()) {
            assertNotNull(type, Bukkit.getRecipe(new NamespacedKey(plugin, "minion_" + type)));
        }
    }

    // ---------------------------------------------------------------- commands

    @Test
    public void adminGivesMinionsWithAFreshUid() {
        player.setOp(true);
        player.performCommand("minion give diamond 7");
        ItemStack given = null;
        for (ItemStack s : player.getInventory().getStorageContents()) {
            if (s != null && items.is(s, "minion_diamond_7")) {
                given = s;
            }
        }
        assertNotNull(given);
        assertNotNull(MinionItems.parse(items, given).uid());
    }

    @Test
    public void giveValidatesItsArguments() {
        player.setOp(true);
        player.performCommand("minion give nothing 3");
        player.performCommand("minion give coal 12");
        player.performCommand("minion give coal x");
        assertEquals(0, items.ids().stream().filter(i -> count("c:" + i) > 0).count());
    }

    @Test
    public void giveExtrasWorksForFuelUpgradeAndHat() {
        player.setOp(true);
        player.performCommand("minion give fuel hyper_catalyst 2");
        player.performCommand("minion give upgrade compactor");
        player.performCommand("minion give hat crown");
        player.performCommand("minion give hat wither_skull");
        assertEquals(2, count("c:minion_fuel_hyper_catalyst"));
        assertEquals(1, count("c:minion_upgrade_compactor"));
        assertEquals(1, count("c:minion_skin_wither_skull"));
    }

    @Test
    public void playersCannotGive() {
        player.setOp(false);
        player.performCommand("minion give coal 3");
        assertEquals(0, count("c:minion_coal_3"));
    }

    @Test
    public void listShowsYourMinions() {
        player.setOp(true);
        player.performCommand("minion give coal 1");
        ItemStack coal = null;
        for (ItemStack s : player.getInventory().getStorageContents()) {
            if (s != null && items.is(s, "minion_coal_1")) {
                coal = s;
            }
        }
        assertNull(manager.place(player, coal, world.getBlockAt(0, 63, 0), BlockFace.UP).error());
        player.performCommand("minion list");
        boolean sawIt = false;
        net.kyori.adventure.text.Component msg;
        while ((msg = player.nextComponentMessage()) != null) {
            sawIt |= net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(msg)
                .contains("Coal Minion I at sb_world 0, 64, 0");
        }
        assertTrue(sawIt);
    }

    @Test
    public void purgeNeedsConfirmAndThenOwesItemsBack() {
        player.setOp(true);
        ItemStack coal = MinionItems.createMinion(items, "coal", 1, null);
        manager.place(player, coal, world.getBlockAt(0, 63, 0), BlockFace.UP);
        player.performCommand("minion purge " + player.getName());
        assertEquals(1, manager.store().size());
        player.performCommand("minion purge " + player.getName() + " confirm");
        assertEquals(0, manager.store().size());
        assertEquals(1, count("c:minion_coal_1"));
    }

    @Test
    public void tabCompletion() {
        player.setOp(true);
        var command = Bukkit.getCommandMap().getCommand("minion");
        assertTrue(command.tabComplete(player, "minion", new String[] {"gi"}).contains("give"));
        assertTrue(command.tabComplete(player, "minion", new String[] {"give", "cob"}).contains("cobblestone"));
        assertTrue(command.tabComplete(player, "minion", new String[] {"give", "fuel", "cat"}).contains("catalyst"));
        assertEquals(11, command.tabComplete(player, "minion", new String[] {"give", "coal", ""}).size());
        player.setOp(false);
        assertFalse(command.tabComplete(player, "minion", new String[] {""}).contains("give"));
    }

    // ---------------------------------------------------------------- menu

    private MinionData place(String type, int tier) {
        ItemStack item = MinionItems.createMinion(items, type, tier, null);
        assertNull(manager.place(player, item, world.getBlockAt(0, 63, 0), BlockFace.UP).error());
        return manager.at(world.getBlockAt(0, 64, 0)).orElseThrow();
    }

    private void click(int slot, ClickType type) {
        InventoryClickEvent event = new InventoryClickEvent(player.getOpenInventory(), InventoryType.SlotType.CONTAINER,
            slot, type, InventoryAction.PICKUP_ALL);
        server.getPluginManager().callEvent(event);
        assertTrue("menu clicks are always cancelled", event.isCancelled());
        server.getScheduler().performOneTick();
    }

    @Test
    public void theMenuOpensAndCollectAllWorks() {
        MinionData d = place("cobblestone", 3);
        d.storage.put("m:COBBLESTONE", 200L);
        new MinionGui(core, manager).open(player, d);
        assertTrue(player.getOpenInventory().getTopInventory().getHolder() instanceof win.vampyuyu.survival.core.Gui);
        assertEquals(54, player.getOpenInventory().getTopInventory().getSize());
        click(1, ClickType.LEFT);
        assertEquals(200, count("m:COBBLESTONE"));
        assertTrue(d.storage.isEmpty());
    }

    @Test
    public void clickingAStackTakesOneStackAndShiftTakesAll() {
        MinionData d = place("cobblestone", 3);
        d.storage.put("m:COBBLESTONE", 150L);
        new MinionGui(core, manager).open(player, d);
        click(10, ClickType.LEFT);
        assertEquals(64, count("m:COBBLESTONE"));
        click(10, ClickType.SHIFT_LEFT);
        assertEquals(150, count("m:COBBLESTONE"));
    }

    @Test
    public void theTierButtonUpgradesWhenYouCanPay() {
        MinionData d = place("cobblestone", 1);
        player.getInventory().addItem(items.create("minion_enchanted_cobblestone", 8));
        new MinionGui(core, manager).open(player, d);
        click(3, ClickType.LEFT);
        assertEquals(2, d.tier);
        assertEquals(0, count("c:minion_enchanted_cobblestone"));
    }

    @Test
    public void fuelPickerAndPickUpButtonWork() {
        MinionData d = place("cobblestone", 3);
        player.getInventory().addItem(new ItemStack(Material.COAL, 5));
        MinionGui gui = new MinionGui(core, manager);
        gui.open(player, d);
        click(45, ClickType.LEFT); // opens the picker
        // picker: first item sits in the first inner slot (10); shift adds everything
        click(10, ClickType.SHIFT_LEFT);
        assertEquals(5 * 30 * 60_000.0, d.fuelMs, 1e-6);
        assertEquals(0, count("m:COAL"));
        click(53, ClickType.LEFT);
        assertEquals(0, manager.store().size());
        assertEquals(1, count("c:minion_cobblestone_3"));
    }

    @Test
    public void aStrangerCannotUseSomeoneElsesMenu() {
        MinionData d = place("cobblestone", 3);
        d.storage.put("m:COBBLESTONE", 10L);
        PlayerMock stranger = server.addPlayer();
        assertFalse(manager.canAccess(stranger, d));
        new MinionGui(core, manager).open(player, d);
        // the stranger gets hold of the owner's menu inventory: every action re-checks access
        stranger.openInventory(player.getOpenInventory().getTopInventory());
        InventoryClickEvent event = new InventoryClickEvent(stranger.getOpenInventory(), InventoryType.SlotType.CONTAINER,
            1, ClickType.LEFT, InventoryAction.PICKUP_ALL);
        server.getPluginManager().callEvent(event);
        server.getScheduler().performOneTick();
        assertEquals(10, d.storage.get("m:COBBLESTONE").longValue());
        assertEquals(0, manager.countIn(stranger, List.of("m:COBBLESTONE")));
    }

    @Test
    public void guisHelperIsAvailable() {
        assertNotNull(Guis.icon(Material.STONE, "x"));
    }
}
