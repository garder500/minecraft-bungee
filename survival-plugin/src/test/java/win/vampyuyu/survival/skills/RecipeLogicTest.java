package win.vampyuyu.survival.skills;

import static org.junit.Assert.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.ShapelessRecipe;
import org.junit.Test;
import win.vampyuyu.survival.core.Items;

public class RecipeLogicTest extends SkillsBase {
    private SkillsConfig.RecipeDef recipe(String id) {
        return service.config().recipes.get(id);
    }

    private ItemStack[] grid(ItemStack... stacks) {
        ItemStack[] grid = new ItemStack[9];
        System.arraycopy(stacks, 0, grid, 0, stacks.length);
        return grid;
    }

    private ItemStack stack(Material m, int n) {
        return new ItemStack(m, n);
    }

    @Test
    public void everyConfiguredRecipeIsRegisteredWithTheServer() {
        int found = 0;
        java.util.Iterator<Recipe> it = Bukkit.recipeIterator();
        while (it.hasNext()) {
            if (it.next() instanceof ShapelessRecipe shapeless
                    && shapeless.getKey().getKey().startsWith(SkillsService.RECIPE_PREFIX)) {
                found++;
            }
        }
        assertEquals(service.config().recipes.size(), found);
        assertTrue(found >= 25);
        for (String id : service.config().recipes.keySet()) {
            Recipe r = Bukkit.getRecipe(service.recipeKey(id));
            assertNotNull(id, r);
            assertTrue(core.items().is(r.getResult(), id) || r.getResult().getType() != Material.AIR);
        }
    }

    @Test
    public void enchantedItemsAreRegisteredAsOwnedCustomItems() {
        for (String id : service.config().enchantedItems.keySet()) {
            assertTrue(id, core.items().def(id).isPresent());
            ItemStack item = core.items().create(id);
            assertTrue(core.items().is(item, id));
        }
        assertEquals("Enchanted Diamond", core.items().def("enchanted_diamond").orElseThrow().name());
    }

    @Test
    public void exactCostIsRequiredInEverySlot() {
        SkillsConfig.RecipeDef def = recipe("enchanted_cobblestone");
        Items items = core.items();
        ItemStack[] ok = grid(stack(Material.COBBLESTONE, 32), stack(Material.COBBLESTONE, 40), stack(Material.COBBLESTONE, 64),
            stack(Material.COBBLESTONE, 32), stack(Material.COBBLESTONE, 33));
        Optional<int[]> match = SkillRecipes.match(def, ok, items);
        assertTrue(match.isPresent());
        for (int i = 0; i < 5; i++) {
            assertEquals(32, match.get()[i]);
        }
        assertEquals(0, match.get()[5]);
        // one slot too small
        ItemStack[] small = ok.clone();
        small[2] = stack(Material.COBBLESTONE, 31);
        assertTrue(SkillRecipes.match(def, small, items).isEmpty());
        // wrong number of slots
        ItemStack[] four = grid(stack(Material.COBBLESTONE, 64), stack(Material.COBBLESTONE, 64),
            stack(Material.COBBLESTONE, 64), stack(Material.COBBLESTONE, 64));
        assertTrue(SkillRecipes.match(def, four, items).isEmpty());
        ItemStack[] six = grid(stack(Material.COBBLESTONE, 64), stack(Material.COBBLESTONE, 64),
            stack(Material.COBBLESTONE, 64), stack(Material.COBBLESTONE, 64), stack(Material.COBBLESTONE, 64),
            stack(Material.COBBLESTONE, 64));
        assertTrue(SkillRecipes.match(def, six, items).isEmpty());
        // a foreign item anywhere in the grid
        ItemStack[] foreign = ok.clone();
        foreign[7] = stack(Material.DIRT, 1);
        assertTrue(SkillRecipes.match(def, foreign, items).isEmpty());
    }

    @Test
    public void customAndPlainItemsAreNotInterchangeable() {
        Items items = core.items();
        SkillsConfig.RecipeDef raw = recipe("enchanted_coal");
        SkillsConfig.RecipeDef compact = recipe("enchanted_coal_block");
        ItemStack[] plain = new ItemStack[9];
        ItemStack[] enchanted = new ItemStack[9];
        for (int i = 0; i < 5; i++) {
            plain[i] = stack(Material.COAL, 64);
            ItemStack e = items.create("enchanted_coal");
            e.setAmount(32);
            enchanted[i] = e;
        }
        assertTrue(SkillRecipes.match(raw, plain, items).isPresent());
        assertTrue("enchanted coal is not raw coal", SkillRecipes.match(raw, enchanted, items).isEmpty());
        assertTrue(SkillRecipes.match(compact, enchanted, items).isPresent());
        assertTrue("raw coal is not enchanted coal", SkillRecipes.match(compact, plain, items).isEmpty());
    }

    @Test
    public void consumingNeverMutatesTheOriginalsAndNeverGoesNegative() {
        SkillsConfig.RecipeDef def = recipe("enchanted_cobblestone");
        ItemStack[] ok = grid(stack(Material.COBBLESTONE, 32), stack(Material.COBBLESTONE, 40), stack(Material.COBBLESTONE, 64),
            stack(Material.COBBLESTONE, 32), stack(Material.COBBLESTONE, 33));
        int[] consume = SkillRecipes.match(def, ok, core.items()).orElseThrow();
        ItemStack[] next = SkillRecipes.consumeFrom(ok, consume);
        assertNull(next[0]); // exactly 32 left nothing
        assertEquals(8, next[1].getAmount());
        assertEquals(32, next[2].getAmount());
        assertNull(next[3]);
        assertEquals(1, next[4].getAmount());
        assertEquals(32, ok[0].getAmount());
        assertEquals(64, ok[2].getAmount());
        // repeated crafts drain the grid then stop
        int crafts = 0;
        ItemStack[] grid = ok;
        while (true) {
            Optional<int[]> m = SkillRecipes.match(def, grid, core.items());
            if (m.isEmpty()) {
                break;
            }
            grid = SkillRecipes.consumeFrom(grid, m.get());
            crafts++;
        }
        assertEquals(1, crafts); // after one craft only 3 slots still hold items
        for (ItemStack s : grid) {
            assertTrue(s == null || s.getAmount() > 0);
        }
    }

    @Test
    public void requirementsListsWhatIsMissing() {
        SkillRecipes recipes = new SkillRecipes(core, service);
        SkillsConfig.RecipeDef def = recipe("enchanted_diamond");
        List<String> missing = recipes.missingRequirements(player.getUniqueId(), def);
        assertEquals(1, missing.size());
        assertTrue(missing.get(0), missing.get(0).contains("Diamond"));
        service.adminSetCollection(player.getUniqueId(), service.config().collections.get("DIAMOND"), 1000);
        assertTrue(recipes.missingRequirements(player.getUniqueId(), def).isEmpty());
    }

    @Test
    public void disablingTheFeatureRemovesRecipesAndItems() {
        NamespacedKey key = service.recipeKey("enchanted_diamond");
        assertNotNull(Bukkit.getRecipe(key));
        win.vampyuyu.survival.LegacyFeatureFixture.disable(plugin);
        server.getPluginManager().disablePlugin(plugin);
        assertNull(Bukkit.getRecipe(key));
        assertTrue(core.items().def("enchanted_diamond").isEmpty());
        assertTrue(core.service(win.vampyuyu.survival.api.SkillsApi.class).isEmpty());
        assertEquals(Map.of().size(), 0);
    }
}
