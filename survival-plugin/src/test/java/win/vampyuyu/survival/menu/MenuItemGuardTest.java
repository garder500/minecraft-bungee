package win.vampyuyu.survival.menu;

import static org.junit.Assert.*;

import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;
import org.junit.Before;
import org.junit.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

public class MenuItemGuardTest extends MenuTestBase {
    private SkyblockMenuItem item;
    private MenuItemGuard guard;
    private int opened;

    @Before
    public void setUp() {
        item = new SkyblockMenuItem(core);
        item.register();
        guard = new MenuItemGuard(rt, item, p -> opened++);
        core.listen(guard);
    }

    private int count(PlayerMock player) {
        int n = 0;
        for (ItemStack stack : player.getInventory().getContents()) {
            if (item.is(stack)) {
                n++;
            }
        }
        return n;
    }

    @Test
    public void itemIsNetherStarWithTheMenuModelAndOneStack() {
        ItemStack stack = item.create();
        assertEquals(Material.NETHER_STAR, stack.getType());
        assertEquals("vampyuyu:menu/skyblock_menu", stack.getItemMeta().getItemModel().toString());
        assertEquals(1, stack.getMaxStackSize());
        assertTrue(item.is(stack));
        assertFalse(item.is(new ItemStack(Material.NETHER_STAR)));
    }

    @Test
    public void givenInSkyblockWorldsOnlyAndInTheConfiguredSlot() {
        PlayerMock inSb = playerIn("sb_hub");
        guard.sync(inSb);
        assertTrue(item.is(inSb.getInventory().getItem(8)));
        assertEquals(1, count(inSb));
        PlayerMock outside = playerIn("world");
        guard.sync(outside);
        assertEquals(0, count(outside));
    }

    @Test
    public void leavingSkyblockRemovesIt() {
        PlayerMock player = playerIn("sb_hub");
        guard.sync(player);
        player.teleport(new org.bukkit.Location(world("world"), 0, 64, 0));
        guard.sync(player);
        assertEquals(0, count(player));
    }

    @Test
    public void strayCopiesAreSweptAndOnlyOneRemains() {
        PlayerMock player = playerIn("sb_world");
        player.getInventory().setItem(3, item.create());
        player.getInventory().setItem(20, item.create());
        player.getInventory().setItemInOffHand(item.create());
        guard.sync(player);
        assertEquals(1, count(player));
        assertTrue(item.is(player.getInventory().getItem(8)));
        guard.sync(player);
        assertEquals(1, count(player));
    }

    @Test
    public void playersOwnItemInTheSlotIsMovedNotDeleted() {
        PlayerMock player = playerIn("sb_hub");
        ItemStack sword = new ItemStack(Material.DIAMOND_SWORD);
        player.getInventory().setItem(8, sword);
        guard.sync(player);
        assertTrue(item.is(player.getInventory().getItem(8)));
        assertTrue(player.getInventory().contains(Material.DIAMOND_SWORD));
    }

    @Test
    public void fullInventoryNeverDestroysAnItem() {
        PlayerMock player = playerIn("sb_hub");
        for (int i = 0; i < 36; i++) {
            player.getInventory().setItem(i, new ItemStack(Material.DIRT, 1 + i));
        }
        guard.sync(player);
        assertEquals(0, count(player));
        assertEquals(Material.DIRT, player.getInventory().getItem(8).getType());
        assertEquals(9, player.getInventory().getItem(8).getAmount());
    }

    @Test
    public void clickingTheItemIsCancelled() {
        PlayerMock player = playerIn("sb_hub");
        guard.sync(player);
        player.openInventory(server.createInventory(null, 27));
        var view = player.getOpenInventory();
        int menuRaw = -1;
        for (int raw = 0; raw < view.countSlots(); raw++) {
            if (item.is(view.getItem(raw))) {
                menuRaw = raw;
            }
        }
        assertTrue("menu item visible in the view", menuRaw >= 0);
        for (ClickType type : new ClickType[] {ClickType.LEFT, ClickType.RIGHT, ClickType.SHIFT_LEFT, ClickType.DROP,
                ClickType.DOUBLE_CLICK, ClickType.SWAP_OFFHAND, ClickType.CREATIVE}) {
            InventoryClickEvent event = new InventoryClickEvent(view, InventoryType.SlotType.QUICKBAR, menuRaw, type,
                InventoryAction.PICKUP_ALL);
            // the raw slot of the menu item in the open view
            server.getPluginManager().callEvent(event);
            assertTrue(type + " must be cancelled", event.isCancelled());
        }
    }

    @Test
    public void hotbarSwapWithTheMenuSlotIsCancelledFromAnyInventory() {
        PlayerMock player = playerIn("sb_hub");
        guard.sync(player);
        player.getInventory().setItem(5, new ItemStack(Material.STONE));
        player.openInventory(server.createInventory(null, 27));
        var view = player.getOpenInventory();
        InventoryClickEvent event = new InventoryClickEvent(view, InventoryType.SlotType.CONTAINER, 12,
            ClickType.NUMBER_KEY, InventoryAction.HOTBAR_SWAP, 8);
        server.getPluginManager().callEvent(event);
        assertTrue("swapping with hotbar 9 must be blocked", event.isCancelled());
        InventoryClickEvent other = new InventoryClickEvent(view, InventoryType.SlotType.CONTAINER, 12,
            ClickType.NUMBER_KEY, InventoryAction.HOTBAR_SWAP, 5);
        server.getPluginManager().callEvent(other);
        assertFalse("other hotbar slots stay usable", other.isCancelled());
    }

    @Test
    public void dropAndHandSwapAreCancelled() {
        PlayerMock player = playerIn("sb_hub");
        guard.sync(player);
        var dropped = player.getWorld().dropItem(player.getLocation(), item.create());
        PlayerDropItemEvent drop = new PlayerDropItemEvent(player, dropped);
        server.getPluginManager().callEvent(drop);
        assertTrue(drop.isCancelled());
        PlayerSwapHandItemsEvent swap = new PlayerSwapHandItemsEvent(player, item.create(), new ItemStack(Material.AIR));
        server.getPluginManager().callEvent(swap);
        assertTrue(swap.isCancelled());
        PlayerDropItemEvent stone = new PlayerDropItemEvent(player,
            player.getWorld().dropItem(player.getLocation(), new ItemStack(Material.STONE)));
        server.getPluginManager().callEvent(stone);
        assertFalse(stone.isCancelled());
    }

    @Test
    public void deathDropsNeverContainTheItem() {
        PlayerMock player = playerIn("sb_hub");
        guard.sync(player);
        java.util.List<ItemStack> drops = new java.util.ArrayList<>(java.util.List.of(item.create(),
            new ItemStack(Material.DIRT)));
        var event = new org.bukkit.event.entity.PlayerDeathEvent(player,
            org.bukkit.damage.DamageSource.builder(org.bukkit.damage.DamageType.GENERIC).build(), drops, 0,
            net.kyori.adventure.text.Component.text("died"), false);
        server.getPluginManager().callEvent(event);
        assertEquals(1, event.getDrops().size());
        assertEquals(Material.DIRT, event.getDrops().get(0).getType());
    }

    @Test
    public void rightClickOpensTheMenuOnceWithinTheCooldown() {
        PlayerMock player = playerIn("sb_hub");
        guard.sync(player);
        player.getInventory().setHeldItemSlot(8);
        var event = new org.bukkit.event.player.PlayerInteractEvent(player, org.bukkit.event.block.Action.RIGHT_CLICK_AIR,
            player.getInventory().getItem(8), null, org.bukkit.block.BlockFace.SELF, org.bukkit.inventory.EquipmentSlot.HAND);
        server.getPluginManager().callEvent(event);
        server.getPluginManager().callEvent(new org.bukkit.event.player.PlayerInteractEvent(player,
            org.bukkit.event.block.Action.RIGHT_CLICK_AIR, player.getInventory().getItem(8), null,
            org.bukkit.block.BlockFace.SELF, org.bukkit.inventory.EquipmentSlot.HAND));
        assertEquals(1, opened);
        assertTrue(event.useItemInHand() == org.bukkit.event.Event.Result.DENY);
    }

    @Test
    public void quitStripsTheItemFromTheSavedInventory() {
        PlayerMock player = playerIn("sb_hub");
        guard.sync(player);
        server.getPluginManager().callEvent(new org.bukkit.event.player.PlayerQuitEvent(player,
            net.kyori.adventure.text.Component.text("bye"), org.bukkit.event.player.PlayerQuitEvent.QuitReason.DISCONNECTED));
        assertEquals(0, count(player));
    }

    @Test
    public void creativeModeStillCannotKeepStrayCopies() {
        PlayerMock player = playerIn("sb_hub");
        player.setGameMode(GameMode.CREATIVE);
        player.getInventory().setItem(10, item.create());
        guard.sync(player);
        assertEquals(1, count(player));
    }
}
