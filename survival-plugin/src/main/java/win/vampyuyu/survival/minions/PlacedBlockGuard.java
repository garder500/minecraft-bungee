package win.vampyuyu.survival.minions;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFadeEvent;
import org.bukkit.event.block.BlockFertilizeEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.world.StructureGrowEvent;
import win.vampyuyu.survival.core.Core;
import win.vampyuyu.survival.core.Cooldowns;

/**
 * The ore, crop or log a minion places is a real block, but it belongs to the minion's cycle, not to the world: if a
 * player could break it they would get the vanilla drops (with Fortune) and experience on top of what the minion
 * credits, and a bone-mealed or physics-popped block would do the same. So nobody can break, push, blow up, burn,
 * wash away, trample or grow it. The block is removed by the minion itself at the end of the cycle (no drops), and
 * by pick-up. Cancelling the break (instead of stripping its drops) also keeps other features from awarding skill XP
 * or loot for it.
 */
final class PlacedBlockGuard implements Listener {
    private final Core core;
    private final MinionManager manager;
    private final Cooldowns warned = new Cooldowns();

    PlacedBlockGuard(Core core, MinionManager manager) {
        this.core = core;
        this.manager = manager;
    }

    private void tell(Player p) {
        if (warned.tryUse(p.getUniqueId(), "spot", 3000)) {
            core.msg().info(p, "That block was placed by a minion. It disappears when the minion has finished with it.");
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onBreak(BlockBreakEvent event) {
        if (manager.isProtected(event.getBlock())) {
            event.setCancelled(true);
            tell(event.getPlayer());
        }
    }

    /** Defence in depth: some other plugin un-cancelled the break. No drops, no experience. */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDrop(BlockDropItemEvent event) {
        if (manager.isProtected(event.getBlock())) {
            event.getItems().clear();
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        event.blockList().removeIf(manager::isProtected);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        event.blockList().removeIf(manager::isProtected);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        Block head = event.getBlock().getRelative(event.getDirection());
        if (manager.isProtected(head) || event.getBlocks().stream().anyMatch(b -> manager.isProtected(b)
                || manager.isProtected(b.getRelative(event.getDirection())))) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        if (event.getBlocks().stream().anyMatch(manager::isProtected)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityChange(EntityChangeBlockEvent event) {
        if (manager.isProtected(event.getBlock())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTrample(PlayerInteractEvent event) {
        if (event.getAction() == Action.PHYSICAL && event.getClickedBlock() != null
                && manager.isProtected(event.getClickedBlock())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFertilize(BlockFertilizeEvent event) {
        if (manager.isProtected(event.getBlock()) || event.getBlocks().stream()
                .anyMatch(state -> manager.isProtected(state.getBlock()))) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onStructureGrow(StructureGrowEvent event) {
        if (manager.isProtected(event.getLocation().getBlock())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFlow(BlockFromToEvent event) {
        if (manager.isProtected(event.getToBlock())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBurn(BlockBurnEvent event) {
        if (manager.isProtected(event.getBlock())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFade(BlockFadeEvent event) {
        if (manager.isProtected(event.getBlock())) {
            event.setCancelled(true);
        }
    }

    /**
     * Catch-all for a placed block that popped off by physics (its support went away some way not covered above):
     * the item that would spawn at its position is cancelled, so it still yields nothing.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onItemSpawn(ItemSpawnEvent event) {
        Block at = event.getLocation().getBlock();
        manager.minionAtSpot(at).ifPresent(d -> {
            Material expected = d.spotMaterial == null ? null : Material.matchMaterial(d.spotMaterial);
            if (expected != null && at.getType() != expected) {
                event.setCancelled(true);
            }
        });
    }
}
