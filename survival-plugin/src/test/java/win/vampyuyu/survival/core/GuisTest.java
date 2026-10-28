package win.vampyuyu.survival.core;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.bukkit.plugin.java.JavaPlugin;

public class GuisTest {
    private ServerMock server;
    private Guis guis;
    private PlayerMock player;

    @Before
    public void setUp() {
        server = MockBukkit.mock();
        JavaPlugin plugin = MockBukkit.createMockPlugin();
        guis = new Guis(new Scheduler(plugin), Logger.getAnonymousLogger());
        server.getPluginManager().registerEvents(guis, plugin);
        player = server.addPlayer();
    }

    @After
    public void tearDown() {
        MockBukkit.unmock();
    }

    private InventoryClickEvent click(InventoryView view, int rawSlot, ClickType type) {
        InventoryClickEvent event = new InventoryClickEvent(view, InventoryType.SlotType.CONTAINER, rawSlot, type,
            type.isShiftClick() ? InventoryAction.MOVE_TO_OTHER_INVENTORY : InventoryAction.PICKUP_ALL);
        server.getPluginManager().callEvent(event);
        return event;
    }

    @Test
    public void clickRunsTheSlotHandlerAndIsCancelled() {
        AtomicInteger lefts = new AtomicInteger();
        AtomicInteger shifts = new AtomicInteger();
        Gui gui = guis.create(3, Component.text("Test"));
        gui.set(13, Guis.icon(Material.DIAMOND, "<aqua>Gem", "<gray>Shiny"), c -> {
            if (c.shift()) shifts.incrementAndGet();
            else if (c.left()) lefts.incrementAndGet();
        });
        gui.open(player);
        InventoryView view = player.getOpenInventory();

        assertTrue(click(view, 13, ClickType.LEFT).isCancelled());
        assertTrue(click(view, 13, ClickType.SHIFT_LEFT).isCancelled());
        assertTrue(click(view, 5, ClickType.LEFT).isCancelled());
        assertEquals(1, lefts.get());
        assertEquals(1, shifts.get());
    }

    @Test
    public void clicksInThePlayerInventoryAreCancelledToo() {
        Gui gui = guis.create(1, Component.text("Test"));
        gui.open(player);
        InventoryView view = player.getOpenInventory();
        // raw slot 9 is the first slot below a 1-row menu: the player's own inventory
        assertTrue(click(view, 12, ClickType.SHIFT_LEFT).isCancelled());
    }

    @Test
    public void dragIntoAMenuIsCancelled() {
        Gui gui = guis.create(2, Component.text("Test"));
        gui.open(player);
        InventoryView view = player.getOpenInventory();
        InventoryDragEvent event = new InventoryDragEvent(view, null, new ItemStack(Material.DIRT),
            false, java.util.Map.of(3, new ItemStack(Material.DIRT)));
        server.getPluginManager().callEvent(event);
        assertTrue(event.isCancelled());
    }

    @Test
    public void ordinaryInventoriesAreUntouched() {
        player.openInventory(org.bukkit.Bukkit.createInventory(null, InventoryType.CHEST));
        InventoryView view = player.getOpenInventory();
        assertFalse(click(view, 2, ClickType.LEFT).isCancelled());
    }

    @Test
    public void borderFillAndRowsValidation() {
        Gui gui = guis.create(3, Component.text("T"));
        ItemStack pane = Guis.icon(Material.GRAY_STAINED_GLASS_PANE, " ");
        gui.border(pane);
        assertNotNull(gui.getInventory().getItem(0));
        assertNotNull(gui.getInventory().getItem(26));
        assertNull(gui.getInventory().getItem(13));
        gui.fill(pane);
        assertNotNull(gui.getInventory().getItem(13));
        assertEquals(7, gui.innerSlots().length);
        try {
            guis.create(7, Component.text("bad"));
            fail();
        } catch (IllegalArgumentException expected) {
        }
    }

    @Test
    public void paginationShowsPagesAndNavigates() {
        List<Integer> numbers = new ArrayList<>();
        for (int i = 0; i < 5; i++) numbers.add(i);
        List<Integer> clicked = new ArrayList<>();
        Gui gui = guis.create(3, Component.text("P"));
        Gui.Pager<Integer> pager = gui.paginate(numbers, new int[] {10, 11}, n -> new ItemStack(Material.STONE, n + 1),
            (c, n) -> clicked.add(n), 18, 26);
        assertEquals(3, pager.pages());
        assertNull(gui.getInventory().getItem(18));
        assertNotNull(gui.getInventory().getItem(26));
        gui.open(player);
        InventoryView view = player.getOpenInventory();
        click(view, 26, ClickType.LEFT);
        assertEquals(1, pager.page());
        assertEquals(3, gui.getInventory().getItem(10).getAmount());
        click(view, 11, ClickType.LEFT);
        assertEquals(List.of(3), clicked);
        pager.page(99);
        assertEquals(2, pager.page());
        assertNull(gui.getInventory().getItem(11));
    }

    @Test
    public void closeHandlerRuns() {
        AtomicInteger closed = new AtomicInteger();
        Gui gui = guis.create(1, Component.text("C")).onClose(p -> closed.incrementAndGet());
        gui.open(player);
        player.closeInventory();
        assertEquals(1, closed.get());
    }
}
