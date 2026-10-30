package win.vampyuyu.survival.skills;

import com.destroystokyo.paper.event.player.PlayerLaunchProjectileEvent;
import java.util.Locale;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Allay;
import org.bukkit.entity.Animals;
import org.bukkit.entity.Player;
import org.bukkit.entity.Tameable;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockDispenseEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.BrewEvent;
import org.bukkit.event.inventory.BrewingStandFuelEvent;
import org.bukkit.event.inventory.FurnaceBurnEvent;
import org.bukkit.event.inventory.FurnaceSmeltEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import win.vampyuyu.survival.core.Cooldowns;
import win.vampyuyu.survival.core.Core;

/**
 * Keeps the {@code enchanted_*} resources from behaving like the plain material they are drawn on: they cannot be
 * placed, planted, eaten, burned, smelted, thrown, dispensed, fed to animals, composted, brewed or used in vanilla
 * recipes (the 160-item investment would turn into a plain block or crop). Recipes of other plugins (their namespace
 * is not {@code minecraft}) are left alone, so enchanted items stay usable in custom recipes.
 */
final class EnchantedGuard implements Listener {
    private final Core core;
    private final SkillsService service;
    private final Cooldowns notices = new Cooldowns();

    EnchantedGuard(Core core, SkillsService service) {
        this.core = core;
        this.service = service;
    }

    /** True when the stack is one of the configured enchanted resources. */
    boolean isEnchanted(ItemStack stack) {
        if (stack == null || stack.getType().isAir() || !service.config().guardEnchantedItems) {
            return false;
        }
        return core.items().idOf(stack).map(id -> service.config().enchantedItems.containsKey(id)).orElse(false);
    }

    private void notice(Player player) {
        if (player != null && notices.tryUse(player.getUniqueId(), "guard", 3000)) {
            core.msg().warn(player, "Enchanted resources are crafting materials, they cannot be used like the "
                + "plain item.");
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (isEnchanted(event.getItemInHand())) {
            event.setCancelled(true);
            notice(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent event) {
        if (isEnchanted(event.getItem())) {
            event.setCancelled(true);
            notice(event.getPlayer());
        }
    }

    /** Right-click with the item: pearls, eyes, planting, composters. Chests and NPCs still open normally. */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInteract(PlayerInteractEvent event) {
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        ItemStack held = event.getItem();
        if (!isEnchanted(held)) {
            return;
        }
        event.setUseItemInHand(Event.Result.DENY);
        Block clicked = event.getClickedBlock();
        boolean specialBlock = clicked != null
            && (clicked.getType() == Material.COMPOSTER || clicked.getType().name().endsWith("CAULDRON"));
        if (specialBlock) {
            event.setUseInteractedBlock(Event.Result.DENY);
        }
        // Only warn when the plain item would really have done something: opening a chest or a crafting table with
        // an enchanted stack in hand (the normal way to craft with it) stays silent. Placing warns in onPlace.
        Material type = held.getType();
        boolean wouldAct = specialBlock || type.isEdible() || type == Material.ENDER_PEARL || type == Material.ENDER_EYE;
        if (wouldAct && (clicked == null || specialBlock || !clicked.getType().isInteractable())) {
            notice(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onLaunch(PlayerLaunchProjectileEvent event) {
        if (isEnchanted(event.getItemStack())) {
            event.setCancelled(true);
            notice(event.getPlayer());
        }
    }

    /** Feeding and taming: only the animals, so right-clicking a merchant NPC while holding the item still works. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFeed(PlayerInteractEntityEvent event) {
        var target = event.getRightClicked();
        if (!(target instanceof Animals || target instanceof Tameable || target instanceof Allay)) {
            return;
        }
        Player player = event.getPlayer();
        ItemStack held = player.getInventory().getItem(event.getHand());
        if (isEnchanted(held)) {
            event.setCancelled(true);
            notice(player);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBurn(FurnaceBurnEvent event) {
        if (isEnchanted(event.getFuel())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSmelt(FurnaceSmeltEvent event) {
        if (isEnchanted(event.getSource())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBrew(BrewEvent event) {
        if (isEnchanted(event.getContents().getIngredient())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBrewFuel(BrewingStandFuelEvent event) {
        if (isEnchanted(event.getFuel())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDispense(BlockDispenseEvent event) {
        if (isEnchanted(event.getItem())) {
            event.setCancelled(true);
        }
    }

    /** A vanilla recipe (namespace minecraft) never accepts an enchanted resource as an ingredient. */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPrepare(PrepareItemCraftEvent event) {
        Recipe recipe = event.getRecipe();
        if (recipe == null || !(recipe instanceof org.bukkit.Keyed keyed)
                || !keyed.getKey().getNamespace().toLowerCase(Locale.ROOT).equals("minecraft")) {
            return;
        }
        for (ItemStack stack : event.getInventory().getMatrix()) {
            if (isEnchanted(stack)) {
                event.getInventory().setResult(null);
                return;
            }
        }
    }
}
