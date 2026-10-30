package win.vampyuyu.survival.boss;

import java.util.Optional;
import org.bukkit.Material;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockDispenseEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.ItemStack;
import org.jspecify.annotations.Nullable;
import win.vampyuyu.survival.core.Core;

/**
 * Boss drops are custom items on vanilla materials (cobweb, magma block, rotten flesh, ender pearl...). Without this
 * guard a Tarantula Web would be placed as a plain cobweb, a Null Sphere thrown, a Revenant Flesh eaten, a Wing Core
 * crafted into a Leviathan Scale. Material and summon items do nothing vanilla; weapons are left alone.
 */
public final class BossItemGuard implements Listener {
    private final Core core;

    public BossItemGuard(Core core) {
        this.core = core;
    }

    /** True for a boss item (summon item or drop). */
    boolean isBossItem(@Nullable ItemStack stack) {
        return bossType(stack) != null;
    }

    /** The boss item table type ("MATERIAL", "SUMMON ITEM", "SWORD"...), null for any other stack. */
    private @Nullable String bossType(@Nullable ItemStack stack) {
        if (stack == null || stack.getType().isAir()) {
            return null;
        }
        Optional<String> id = core.items().idOf(stack);
        if (id.isEmpty()) {
            return null;
        }
        BossItems.Spec spec = BossItems.byId().get(id.get());
        return spec == null ? null : spec.type();
    }

    private boolean inert(@Nullable ItemStack stack) {
        String type = bossType(stack);
        return "MATERIAL".equals(type) || "SUMMON ITEM".equals(type);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlace(BlockPlaceEvent event) {
        if (inert(event.getItemInHand())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onConsume(PlayerItemConsumeEvent event) {
        if (inert(event.getItem())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDispense(BlockDispenseEvent event) {
        if (isBossItem(event.getItem())) {
            event.setCancelled(true);
        }
    }

    /** Right-click: no throwing, no bone meal, no composting. Summon items are handled by the summon listener. */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onUse(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        if (!"MATERIAL".equals(bossType(event.getItem()))) {
            return;
        }
        event.setUseItemInHand(Event.Result.DENY);
        if (event.getClickedBlock() != null && event.getClickedBlock().getType() == Material.COMPOSTER) {
            event.setUseInteractedBlock(Event.Result.DENY);
        }
    }

    /** A boss item is never a crafting ingredient, whatever its vanilla material is. */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPrepareCraft(PrepareItemCraftEvent event) {
        if (event.getRecipe() instanceof org.bukkit.Keyed keyed && !guarded(keyed.getKey())) {
            return; // another feature's own recipe is its business
        }
        for (ItemStack ingredient : event.getInventory().getMatrix()) {
            if (isBossItem(ingredient)) {
                event.getInventory().setResult(null);
                return;
            }
        }
    }

    /** Vanilla recipes and ours (which take vanilla materials) are guarded; recipes of other plugins are left alone. */
    private boolean guarded(org.bukkit.NamespacedKey key) {
        return key.getNamespace().equals(org.bukkit.NamespacedKey.MINECRAFT)
            || key.getNamespace().equals(core.getPlugin().getName().toLowerCase(java.util.Locale.ROOT));
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onCraft(CraftItemEvent event) {
        if (event.getRecipe() instanceof org.bukkit.Keyed keyed && !guarded(keyed.getKey())) {
            return;
        }
        for (ItemStack ingredient : event.getInventory().getMatrix()) {
            if (isBossItem(ingredient)) {
                event.setCancelled(true);
                return;
            }
        }
    }
}
