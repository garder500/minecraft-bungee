package win.vampyuyu.survival.items.obtain;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Crafter;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.block.CrafterCraftEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.Keyed;
import win.vampyuyu.survival.core.SkyblockWorlds;
import win.vampyuyu.survival.items.ItemsSettings;
import win.vampyuyu.survival.items.gear.Gear;

/**
 * Bukkit crafting recipes of the starting catalog gear. Every recipe has the key {@code vampyuyu:sb_items_<name>}; they
 * are removed before they are added (reload) and when the feature disables. A craft is refused outside Skyblock worlds
 * (recipes are global on the server) and when any ingredient is a custom item that merely looks like the vanilla one.
 */
public final class ItemRecipes implements Listener {
    public static final String PREFIX = "sb_items_";

    private final Gear gear;
    private final Logger log;
    private volatile ItemsSettings settings;
    private final List<NamespacedKey> keys = new ArrayList<>();

    public ItemRecipes(Gear gear, ItemsSettings settings, Logger log) {
        this.gear = gear;
        this.settings = settings;
        this.log = log;
    }

    public void settings(ItemsSettings settings) { this.settings = settings; }

    /** Pure: true for the recipe keys this class owns. */
    public static boolean ours(NamespacedKey key) {
        return key.getNamespace().equals("vampyuyu") && key.getKey().startsWith(PREFIX);
    }

    /** Registers every recipe; returns how many were added. Bad definitions are logged and skipped. */
    public int register() {
        unregister();
        if (!settings.recipesEnabled) {
            return 0;
        }
        int added = 0;
        for (RecipeDef def : settings.recipeDefs) {
            String problem = def.problem();
            if (problem != null) {
                log.warning("Recipe " + def.name() + " skipped: " + problem);
                continue;
            }
            if (gear.items().def(def.result()).isEmpty()) {
                log.warning("Recipe " + def.name() + " skipped: unknown item " + def.result());
                continue;
            }
            NamespacedKey key = new NamespacedKey("vampyuyu", PREFIX + def.name());
            try {
                Bukkit.removeRecipe(key);
                ItemStack result = gear.items().create(def.result());
                result.setAmount(def.amount());
                gear.rebuild(result);
                ShapedRecipe recipe = new ShapedRecipe(key, result);
                recipe.shape(def.shape().toArray(new String[0]));
                def.ingredients().forEach((symbol, material) -> {
                    Material m = Material.matchMaterial(material);
                    if (m != null) {
                        recipe.setIngredient(symbol, m);
                    }
                });
                if (Bukkit.addRecipe(recipe)) {
                    keys.add(key);
                    added++;
                }
            } catch (RuntimeException e) {
                log.warning("Recipe " + def.name() + " could not be registered: " + e);
            }
        }
        return added;
    }

    /** Removes every recipe added by {@link #register}. */
    public void unregister() {
        for (NamespacedKey key : keys) {
            try {
                Bukkit.removeRecipe(key);
            } catch (RuntimeException ignored) {
                // server shutting down
            }
        }
        keys.clear();
    }

    public int count() { return keys.size(); }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPrepare(PrepareItemCraftEvent event) {
        Recipe recipe = event.getRecipe();
        if (!(recipe instanceof Keyed keyed) || !ours(keyed.getKey())) {
            return;
        }
        List<HumanEntity> viewers = event.getViewers();
        if (settings.recipesSkyblockOnly && (viewers.isEmpty() || viewers.get(0).getWorld() == null
                || !SkyblockWorlds.isSkyblock(viewers.get(0).getWorld().getName()))) {
            event.getInventory().setResult(null);
            return;
        }
        for (ItemStack ingredient : event.getInventory().getMatrix()) {
            if (ingredient != null && gear.items().isCustom(ingredient)) {
                event.getInventory().setResult(null);
                return;
            }
        }
    }

    /** Crafter blocks do not fire PrepareItemCraftEvent: same guards on their craft event. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCrafter(CrafterCraftEvent event) {
        if (!ours(event.getRecipe().getKey())) {
            return;
        }
        if (settings.recipesSkyblockOnly && !SkyblockWorlds.isSkyblock(event.getBlock().getWorld().getName())) {
            event.setCancelled(true);
            return;
        }
        if (event.getBlock().getState() instanceof Crafter crafter) {
            for (ItemStack ingredient : crafter.getInventory().getContents()) {
                if (ingredient != null && gear.items().isCustom(ingredient)) {
                    event.setCancelled(true);
                    return;
                }
            }
        }
    }

    /** Shows the recipes in the recipe book of players in Skyblock worlds. */
    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        discover(event.getPlayer());
    }

    @EventHandler
    public void onWorld(PlayerChangedWorldEvent event) {
        discover(event.getPlayer());
    }

    private void discover(Player player) {
        if (keys.isEmpty() || player.getWorld() == null || !SkyblockWorlds.isSkyblock(player.getWorld().getName())) {
            return;
        }
        try {
            player.discoverRecipes(List.copyOf(keys));
        } catch (RuntimeException ignored) {
            // the recipe book is cosmetic
        }
    }

    /** Test hook: the first registered key, if any. */
    Optional<NamespacedKey> first() { return keys.stream().findFirst(); }
}
