package win.vampyuyu.survival.minions;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.recipe.CraftingBookCategory;
import org.bukkit.plugin.Plugin;
import win.vampyuyu.survival.core.Items;
import win.vampyuyu.survival.minions.MinionConfig.Type;

/**
 * Tier I crafting recipes: eight of the type's resource around its tool. Higher tiers are bought in the minion menu
 * (see {@link MinionManager#tierUp}), because a recipe cannot ask for a stack of 160.
 */
final class MinionRecipes {
    private final Plugin plugin;
    private final List<NamespacedKey> keys = new ArrayList<>();

    MinionRecipes(Plugin plugin) {
        this.plugin = plugin;
    }

    NamespacedKey keyOf(Type type) {
        return new NamespacedKey(plugin, "minion_" + type.id());
    }

    void register(Items items, MinionConfig config) {
        for (Type type : config.types.values()) {
            try {
                NamespacedKey key = keyOf(type);
                ItemStack result = items.create(MinionItems.minionId(type.id(), 1));
                ShapedRecipe recipe = new ShapedRecipe(key, result);
                recipe.shape("III", "ICI", "III");
                recipe.setIngredient('I', type.recipe().ingredient());
                recipe.setIngredient('C', type.recipe().center());
                recipe.setCategory(CraftingBookCategory.MISC);
                recipe.setGroup("minions");
                Bukkit.removeRecipe(key);
                if (Bukkit.addRecipe(recipe)) {
                    keys.add(key);
                }
            } catch (RuntimeException e) {
                plugin.getLogger().log(Level.WARNING, "minions: could not register the recipe of " + type.id(), e);
            }
        }
    }

    List<NamespacedKey> keys() {
        return List.copyOf(keys);
    }

    boolean isOurs(NamespacedKey key) {
        return keys.contains(key);
    }

    void unregister() {
        for (NamespacedKey key : keys) {
            try {
                Bukkit.removeRecipe(key);
            } catch (RuntimeException e) {
                plugin.getLogger().log(Level.FINE, "minions: could not remove recipe " + key, e);
            }
        }
        keys.clear();
    }
}
