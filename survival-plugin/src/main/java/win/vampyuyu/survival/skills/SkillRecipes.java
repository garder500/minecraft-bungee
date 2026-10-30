package win.vampyuyu.survival.skills;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.CrafterCraftEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import win.vampyuyu.survival.core.Core;
import win.vampyuyu.survival.core.Items;
import win.vampyuyu.survival.core.Msg;

/**
 * Collection-gated crafting. Each recipe of {@code skills.yml} is registered as a Bukkit shapeless recipe whose slots
 * use {@link RecipeChoice.ExactChoice} (so a plain Cobblestone and an Enchanted Cobblestone never share a recipe
 * shell). Vanilla only consumes one item per slot, so the real cost (for example 5 slots of 32) is enforced here:
 * the prepare handler hides the result when a stack is too small or a collection tier is missing, and the craft
 * handler cancels the vanilla craft and performs it by hand (cursor click, shift click) so nothing can be duplicated.
 */
final class SkillRecipes implements Listener {
    private final Core core;
    private final SkillsService service;
    private final Logger log;
    private final List<NamespacedKey> registered = new ArrayList<>();

    SkillRecipes(Core core, SkillsService service) {
        this.core = core;
        this.service = service;
        this.log = core.getPlugin().getLogger();
    }

    // ---------------------------------------------------------------- registration

    /** Registers every configured recipe; skips (with a warning) the ones whose items are unknown. */
    void registerAll() {
        unregisterAll();
        bulk = true;
        for (SkillsConfig.RecipeDef def : service.config().recipes.values()) {
            try {
                register(def);
            } catch (RuntimeException e) {
                log.warning("skills: recipe " + def.id() + " not registered: " + e.getMessage());
            }
        }
        bulk = false;
        refreshClients();
    }

    /** Recipes are added without resending them to every player each time; one update at the end. */
    private boolean bulk;

    private void refreshClients() {
        try {
            Bukkit.updateRecipes();
        } catch (RuntimeException ignored) {
            // not supported (tests)
        }
    }

    private void register(SkillsConfig.RecipeDef def) {
        ItemStack result = resultOf(def);
        NamespacedKey key = service.recipeKey(def.id());
        Bukkit.removeRecipe(key, false);
        ShapelessRecipe recipe = new ShapelessRecipe(key, result);
        for (SkillsConfig.Ingredient ingredient : def.ingredients()) {
            ItemStack sample = sampleOf(ingredient);
            for (int i = 0; i < ingredient.slots(); i++) {
                recipe.addIngredient(new RecipeChoice.ExactChoice(sample));
            }
        }
        if (!Bukkit.addRecipe(recipe, !bulk)) {
            throw new IllegalStateException("the server refused the recipe");
        }
        registered.add(key);
    }

    void unregisterAll() {
        for (NamespacedKey key : registered) {
            try {
                Bukkit.removeRecipe(key, false);
            } catch (RuntimeException ignored) {
                // server shutting down
            }
        }
        if (!registered.isEmpty()) {
            refreshClients();
        }
        registered.clear();
    }

    int registeredCount() {
        return registered.size();
    }

    ItemStack resultOf(SkillsConfig.RecipeDef def) {
        if (def.resultCustom() != null) {
            return core.items().create(def.resultCustom(), def.amount());
        }
        return new ItemStack(def.resultMaterial(), def.amount());
    }

    private ItemStack sampleOf(SkillsConfig.Ingredient ingredient) {
        return ingredient.custom() ? core.items().create(ingredient.customId()) : new ItemStack(ingredient.material());
    }

    // ---------------------------------------------------------------- matching (pure)

    /** True when the stack is the item the ingredient asks for (custom id, or a vanilla stack without one). */
    static boolean matches(SkillsConfig.Ingredient ingredient, ItemStack stack, Items items) {
        if (stack == null || stack.getType().isAir()) {
            return false;
        }
        if (ingredient.custom()) {
            return items.is(stack, ingredient.customId());
        }
        return stack.getType() == ingredient.material() && !items.isCustom(stack);
    }

    /**
     * Checks the crafting grid against a recipe. Returns the amount to remove from each slot (index = grid slot),
     * or empty when a slot holds a foreign item, a stack is smaller than required or a group has the wrong number
     * of slots.
     */
    static Optional<int[]> match(SkillsConfig.RecipeDef def, ItemStack[] matrix, Items items) {
        int[] consume = new int[matrix.length];
        int[] seen = new int[def.ingredients().size()];
        for (int slot = 0; slot < matrix.length; slot++) {
            ItemStack stack = matrix[slot];
            if (stack == null || stack.getType().isAir()) {
                continue;
            }
            int group = -1;
            for (int g = 0; g < def.ingredients().size(); g++) {
                if (matches(def.ingredients().get(g), stack, items)) {
                    group = g;
                    break;
                }
            }
            if (group < 0) {
                return Optional.empty();
            }
            SkillsConfig.Ingredient ingredient = def.ingredients().get(group);
            if (stack.getAmount() < ingredient.perSlot()) {
                return Optional.empty();
            }
            consume[slot] = ingredient.perSlot();
            seen[group]++;
        }
        for (int g = 0; g < seen.length; g++) {
            if (seen[g] != def.ingredients().get(g).slots()) {
                return Optional.empty();
            }
        }
        return Optional.of(consume);
    }

    /** Grid after removing {@code consume} (null for an emptied slot). Never touches the originals. */
    static ItemStack[] consumeFrom(ItemStack[] matrix, int[] consume) {
        ItemStack[] next = new ItemStack[matrix.length];
        for (int i = 0; i < matrix.length; i++) {
            ItemStack stack = matrix[i];
            if (stack == null || stack.getType().isAir()) {
                continue;
            }
            int left = stack.getAmount() - consume[i];
            if (left > 0) {
                ItemStack copy = stack.clone();
                copy.setAmount(left);
                next[i] = copy;
            }
        }
        return next;
    }

    private static ItemStack[] copyOf(ItemStack[] matrix) {
        ItemStack[] copy = new ItemStack[matrix.length];
        for (int i = 0; i < matrix.length; i++) {
            copy[i] = matrix[i] == null ? null : matrix[i].clone();
        }
        return copy;
    }

    private SkillsConfig.RecipeDef ours(Recipe recipe) {
        if (recipe instanceof ShapelessRecipe shapeless) {
            String key = shapeless.getKey().getKey();
            if (key.startsWith(SkillsService.RECIPE_PREFIX)
                    && shapeless.getKey().getNamespace().equals(core.getPlugin().getName().toLowerCase(Locale.ROOT))) {
                return service.config().recipes.get(key.substring(SkillsService.RECIPE_PREFIX.length()));
            }
        }
        return null;
    }

    /** Lines describing the missing requirements of a recipe for this player (empty when unlocked). */
    List<String> missingRequirements(java.util.UUID player, SkillsConfig.RecipeDef def) {
        SkillsData data = service.data(player);
        List<String> missing = new ArrayList<>();
        for (var need : def.requires().entrySet()) {
            SkillsConfig.CollectionDef col = service.config().collections.get(need.getKey());
            if (col == null) {
                missing.add("Unknown collection " + need.getKey());
            } else if (col.tierOf(data.collected(col.id())) < need.getValue()) {
                missing.add(col.name() + " collection tier " + RewardText.roman(need.getValue()));
            }
        }
        return missing;
    }

    // ---------------------------------------------------------------- quick craft

    enum QuickResult { OK, LOCKED, MISSING, NO_ROOM, UNAVAILABLE }

    /**
     * Crafts straight from the inventory, without the 3x3 grid. Everything happens in one step on the main thread:
     * the ingredients are planned on a copy of the storage, the result is checked to fit, and only then is the copy
     * written back and the result handed over, so nothing can be duplicated or lost halfway.
     */
    QuickResult quickCraft(Player player, SkillsConfig.RecipeDef def) {
        if (!player.isOnline() || !Bukkit.isPrimaryThread()) {
            return QuickResult.UNAVAILABLE;
        }
        if (!missingRequirements(player.getUniqueId(), def).isEmpty()) {
            return QuickResult.LOCKED;
        }
        ItemStack[] storage = copyOf(player.getInventory().getStorageContents());
        for (SkillsConfig.Ingredient ingredient : def.ingredients()) {
            int need = ingredient.slots() * ingredient.perSlot();
            for (int i = 0; i < storage.length && need > 0; i++) {
                if (matches(ingredient, storage[i], core.items())) {
                    int take = Math.min(need, storage[i].getAmount());
                    need -= take;
                    if (storage[i].getAmount() == take) {
                        storage[i] = null;
                    } else {
                        storage[i].setAmount(storage[i].getAmount() - take);
                    }
                }
            }
            if (need > 0) {
                return QuickResult.MISSING;
            }
        }
        Inventory scratch = Bukkit.createInventory(null, 36);
        scratch.setStorageContents(copyOf(storage));
        if (!scratch.addItem(resultOf(def)).isEmpty()) {
            return QuickResult.NO_ROOM;
        }
        player.getInventory().setStorageContents(scratch.getStorageContents());
        if (def.xpSkill() != null && def.xpAmount() > 0) {
            service.grant(player, def.xpSkill(), def.xpAmount(), "recipe:" + def.id());
        }
        player.updateInventory();
        return QuickResult.OK;
    }

    // ---------------------------------------------------------------- events

    @EventHandler(priority = EventPriority.HIGH)
    public void onPrepare(PrepareItemCraftEvent event) {
        SkillsConfig.RecipeDef def = event.getRecipe() == null ? null : ours(event.getRecipe());
        if (def == null) {
            return;
        }
        CraftingInventory inv = event.getInventory();
        Optional<int[]> match = match(def, inv.getMatrix(), core.items());
        if (match.isEmpty() || !(event.getView().getPlayer() instanceof Player player)) {
            inv.setResult(null);
            return;
        }
        List<String> missing = missingRequirements(player.getUniqueId(), def);
        if (!missing.isEmpty()) {
            inv.setResult(locked(missing));
        }
    }

    private ItemStack locked(List<String> missing) {
        ItemStack stack = new ItemStack(Material.BARRIER);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Component.text("Recipe locked", NamedTextColor.RED).decoration(TextDecoration.ITALIC, false));
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("Requires:", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        for (String line : missing) {
            lore.add(Component.text(" " + line, NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
        }
        meta.lore(lore);
        stack.setItemMeta(meta);
        return stack;
    }

    /** A crafter block would consume one item per slot: refuse our recipes there. */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onCrafter(CrafterCraftEvent event) {
        if (ours(event.getRecipe()) != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onCraft(CraftItemEvent event) {
        SkillsConfig.RecipeDef def = ours(event.getRecipe());
        if (def == null) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        ClickType click = event.getClick();
        boolean shift = click.isShiftClick();
        if (click != ClickType.LEFT && click != ClickType.RIGHT && !shift) {
            return; // number keys, drops, double click... are refused
        }
        List<String> missing = missingRequirements(player.getUniqueId(), def);
        if (!missing.isEmpty()) {
            core.msg().warn(player, "This recipe is locked. Requires " + String.join(", ", missing) + ".");
            return;
        }
        CraftingInventory inv = event.getInventory();
        ItemStack[] matrix = copyOf(inv.getMatrix());
        int crafts = shift ? craftMany(player, def, inv, matrix) : craftOne(player, def, inv, matrix);
        if (crafts <= 0) {
            return;
        }
        if (def.xpSkill() != null && def.xpAmount() > 0) {
            service.grant(player, def.xpSkill(), def.xpAmount() * crafts, "recipe:" + def.id());
        }
        core.scheduler().nextTick(() -> {
            if (player.isOnline()) {
                player.updateInventory();
            }
        });
    }

    private int craftOne(Player player, SkillsConfig.RecipeDef def, CraftingInventory inv, ItemStack[] matrix) {
        Optional<int[]> consume = match(def, matrix, core.items());
        if (consume.isEmpty()) {
            return 0;
        }
        ItemStack result = resultOf(def);
        ItemStack cursor = player.getItemOnCursor();
        if (cursor.getType().isAir()) {
            player.setItemOnCursor(result);
        } else if (cursor.isSimilar(result) && cursor.getAmount() + result.getAmount() <= cursor.getMaxStackSize()) {
            ItemStack more = cursor.clone();
            more.setAmount(cursor.getAmount() + result.getAmount());
            player.setItemOnCursor(more);
        } else {
            return 0; // nothing consumed
        }
        inv.setMatrix(consumeFrom(matrix, consume.get()));
        return 1;
    }

    private int craftMany(Player player, SkillsConfig.RecipeDef def, CraftingInventory inv, ItemStack[] matrix) {
        Inventory scratch = Bukkit.createInventory(null, 36);
        scratch.setStorageContents(copyOf(player.getInventory().getStorageContents()));
        ItemStack[] grid = matrix;
        int crafts = 0;
        while (crafts < 64) {
            Optional<int[]> consume = match(def, grid, core.items());
            if (consume.isEmpty()) {
                break;
            }
            ItemStack result = resultOf(def);
            if (!scratch.addItem(result.clone()).isEmpty()) {
                break; // no room for another one
            }
            grid = consumeFrom(grid, consume.get());
            crafts++;
        }
        if (crafts == 0) {
            return 0;
        }
        // The scratch inventory proved there is room: hand the items over for real.
        for (int i = 0; i < crafts; i++) {
            var leftover = player.getInventory().addItem(resultOf(def));
            leftover.values().forEach(rest -> player.getWorld().dropItem(player.getLocation(), rest));
        }
        inv.setMatrix(grid);
        return crafts;
    }
}
