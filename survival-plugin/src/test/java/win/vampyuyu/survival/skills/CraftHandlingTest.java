package win.vampyuyu.survival.skills;

import static org.junit.Assert.*;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.junit.Before;
import org.junit.Test;
import win.vampyuyu.survival.api.Skill;

/** The hand-made craft of the collection-gated recipes: costs, cursor, shift click, locks. */
public class CraftHandlingTest extends SkillsBase {
    private CraftingInventory grid;
    private InventoryView view;
    private Recipe recipe;

    @Before
    public void openBench() {
        grid = (CraftingInventory) server.createInventory(player, InventoryType.WORKBENCH);
        player.openInventory(grid);
        view = player.getOpenInventory();
        recipe = Bukkit.getRecipe(service.recipeKey("enchanted_cobblestone"));
        assertNotNull(recipe);
    }

    private void fill(int amount, int slots) {
        ItemStack[] matrix = new ItemStack[9];
        for (int i = 0; i < slots; i++) {
            matrix[i] = new ItemStack(Material.COBBLESTONE, amount);
        }
        grid.setMatrix(matrix);
    }

    private CraftItemEvent click(ClickType type) {
        CraftItemEvent event = new CraftItemEvent(recipe, view, org.bukkit.event.inventory.InventoryType.SlotType.RESULT, 0,
            type, InventoryAction.PICKUP_ALL);
        server.getPluginManager().callEvent(event);
        return event;
    }

    private void unlock() {
        service.adminSetCollection(player.getUniqueId(), service.config().collections.get("COBBLESTONE"), 100_000);
    }

    private int count(String id) {
        int total = 0;
        for (ItemStack s : player.getInventory().getContents()) {
            if (s != null && core.items().is(s, id)) {
                total += s.getAmount();
            }
        }
        return total;
    }

    @Test
    public void lockedRecipeConsumesNothing() {
        fill(64, 5);
        CraftItemEvent event = click(ClickType.LEFT);
        assertTrue(event.isCancelled());
        assertTrue(player.getItemOnCursor().getType().isAir());
        for (ItemStack s : grid.getMatrix()) {
            if (s != null) {
                assertEquals(64, s.getAmount());
            }
        }
    }

    @Test
    public void leftClickPaysTheFullCostOncePutsTheResultOnTheCursor() {
        unlock();
        fill(40, 5);
        assertTrue(click(ClickType.LEFT).isCancelled()); // always cancelled: vanilla never runs
        assertTrue(core.items().is(player.getItemOnCursor(), "enchanted_cobblestone"));
        assertEquals(1, player.getItemOnCursor().getAmount());
        for (ItemStack s : grid.getMatrix()) {
            if (s != null) {
                assertEquals("32 taken from each slot", 8, s.getAmount());
            }
        }
        // 8 per slot is not enough for a second craft
        click(ClickType.LEFT);
        assertEquals(1, player.getItemOnCursor().getAmount());
        assertEquals(1, player.getItemOnCursor().getAmount());
    }

    @Test
    public void cursorHoldingSomethingElseBlocksTheCraftWithoutLoss() {
        unlock();
        fill(32, 5);
        player.setItemOnCursor(new ItemStack(Material.DIRT, 3));
        click(ClickType.LEFT);
        assertEquals(Material.DIRT, player.getItemOnCursor().getType());
        for (int i = 0; i < 5; i++) {
            assertEquals(32, grid.getMatrix()[i].getAmount());
        }
    }

    @Test
    public void cursorStackGrowsOnlyUpToTheStackLimit() {
        unlock();
        player.setItemOnCursor(core.items().create("enchanted_cobblestone", 63));
        fill(64, 5);
        click(ClickType.LEFT);
        assertEquals(64, player.getItemOnCursor().getAmount());
        click(ClickType.RIGHT); // full: refused, nothing consumed
        assertEquals(64, player.getItemOnCursor().getAmount());
        assertEquals(32, grid.getMatrix()[0].getAmount());
    }

    @Test
    public void shiftClickCraftsAsManyAsTheGridAllowsIntoTheInventory() {
        unlock();
        fill(64, 5);
        click(ClickType.SHIFT_LEFT);
        assertEquals(2, count("enchanted_cobblestone")); // 64 per slot allows exactly two crafts
        assertTrue(player.getItemOnCursor().getType().isAir());
        for (ItemStack s : grid.getMatrix()) {
            assertTrue(s == null || s.getAmount() == 0);
        }
    }

    @Test
    public void shiftClickWithAFullInventoryConsumesNothing() {
        unlock();
        for (int i = 0; i < 36; i++) {
            player.getInventory().setItem(i, new ItemStack(Material.STONE, 64));
        }
        fill(64, 5);
        click(ClickType.SHIFT_LEFT);
        assertEquals(0, count("enchanted_cobblestone"));
        assertEquals(64, grid.getMatrix()[0].getAmount());
    }

    @Test
    public void numberKeysAndDropsAreRefused() {
        unlock();
        fill(64, 5);
        for (ClickType type : new ClickType[] {ClickType.NUMBER_KEY, ClickType.DROP, ClickType.CONTROL_DROP,
                ClickType.DOUBLE_CLICK, ClickType.SWAP_OFFHAND}) {
            assertTrue(click(type).isCancelled());
        }
        assertEquals(0, count("enchanted_cobblestone"));
        assertTrue(player.getItemOnCursor().getType().isAir());
        assertEquals(64, grid.getMatrix()[0].getAmount());
    }

    @Test
    public void craftingCountsAsEnchantingExperience() {
        unlock();
        fill(32, 5);
        double before = service.xp(player.getUniqueId(), Skill.ENCHANTING);
        click(ClickType.LEFT);
        assertEquals(15, service.xp(player.getUniqueId(), Skill.ENCHANTING) - before, 1e-9);
        assertNotEquals(InventoryType.CHEST, view.getTopInventory().getType());
    }
}
