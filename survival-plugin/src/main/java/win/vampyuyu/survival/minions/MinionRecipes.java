package win.vampyuyu.survival.minions;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.recipe.CraftingBookCategory;
import org.bukkit.plugin.Plugin;
import win.vampyuyu.survival.core.Items;
import win.vampyuyu.survival.minions.MinionConfig.Type;

/**
 * Crafting recipes. Tier I minions: eight of the type's resource around its tool. Higher tiers are bought in the
 * minion menu (see {@link MinionManager#tierUp}), because a recipe cannot ask for a stack of 160. Upgrades, hats and
 * fuels come from the {@code recipes} section of the config: shaped recipes whose ingredients are the Enchanted
 * items the menu's Compress button makes, so the whole upgrade system is reachable in normal play. (The enchanted
 * items themselves cannot be a crafting recipe: a crafting grid takes one item per slot, never a stack of 160.)
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
        for (MinionConfig.ItemRecipe def : config.itemRecipes.values()) {
            try {
                NamespacedKey key = new NamespacedKey(plugin, "minion_item_" + def.id());
                ShapedRecipe recipe = new ShapedRecipe(key, stackOf(items, config, def.result()));
                recipe.shape(def.shape().toArray(String[]::new));
                for (var e : def.ingredients().entrySet()) {
                    if (def.shape().stream().anyMatch(row -> row.indexOf(e.getKey()) >= 0)) {
                        recipe.setIngredient(e.getKey(), new RecipeChoice.ExactChoice(stackOf(items, config, e.getValue())));
                    }
                }
                recipe.setCategory(CraftingBookCategory.MISC);
                recipe.setGroup("minion_items");
                Bukkit.removeRecipe(key);
                if (Bukkit.addRecipe(recipe)) {
                    keys.add(key);
                }
            } catch (RuntimeException e) {
                plugin.getLogger().log(Level.WARNING, "minions: could not register the recipe '" + def.id() + "': "
                    + e.getMessage());
            }
        }
    }

    /** The item a recipe spec stands for. @throws IllegalArgumentException when it does not exist */
    static ItemStack stackOf(Items items, MinionConfig config, String spec) {
        int colon = spec.indexOf(':');
        if (colon < 0) {
            Material material = Material.matchMaterial(spec);
            if (material == null || !material.isItem()) {
                throw new IllegalArgumentException("unknown item '" + spec + "'");
            }
            return new ItemStack(material);
        }
        String kind = spec.substring(0, colon);
        String rest = spec.substring(colon + 1);
        String id = switch (kind) {
            case "upgrade" -> MinionItems.upgradeId(rest);
            case "skin" -> MinionItems.skinId(rest);
            case "fuel" -> MinionItems.fuelId(rest);
            case "enchanted", "super" -> {
                Material material = Material.matchMaterial(rest);
                if (material == null) {
                    throw new IllegalArgumentException("unknown material '" + rest + "'");
                }
                yield kind.equals("super") ? MinionItems.superId(material) : MinionItems.enchantedId(material);
            }
            default -> throw new IllegalArgumentException("unknown item kind '" + kind + "'");
        };
        if (items.def(id).isEmpty()) {
            throw new IllegalArgumentException("'" + spec + "' is not an item of this feature");
        }
        return items.create(id);
    }

    List<NamespacedKey> keys() {
        return List.copyOf(keys);
    }

    /** True for the upgrade, hat and fuel recipes (exact ingredient choices), false for the tier I minions. */
    boolean isItemRecipe(NamespacedKey key) {
        return keys.contains(key) && key.getKey().startsWith("minion_item_");
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
