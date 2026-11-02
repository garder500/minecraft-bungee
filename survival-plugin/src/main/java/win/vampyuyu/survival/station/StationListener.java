package win.vampyuyu.survival.station;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.persistence.PersistentDataType;
import win.vampyuyu.survival.api.event.BossKillEvent;
import win.vampyuyu.survival.core.Core;
import win.vampyuyu.survival.core.SkyblockWorlds;
import win.vampyuyu.survival.quarry.BlockMarks;

/** Station blocks (place, use, pick up), the Alembic flasks and the kills the contracts count. */
final class StationListener implements Listener {
    /** A station block: its item, the block it is, its marks in the chunk. */
    private record Station(String item, Material block, BlockMarks marks) {}

    private final Core core;
    private final StationService service;
    private final StationGui gui;
    private final Station[] stations = {
        new Station(StationItems.ALEMBIC, Material.BREWING_STAND, new BlockMarks("alembic")),
        new Station(StationItems.CRUCIBLE, Material.CAULDRON, new BlockMarks("crucible")),
        new Station(StationItems.RANCH, Material.COMPOSTER, new BlockMarks("ranch"))};

    StationListener(Core core, StationService service, StationGui gui) {
        this.core = core;
        this.service = service;
        this.gui = gui;
    }

    private Station at(Block block) {
        for (Station s : stations) {
            if (block.getType() == s.block() && s.marks().has(block)) {
                return s;
            }
        }
        return null;
    }

    private static boolean island(String world) {
        return SkyblockWorlds.WORLD.equals(world) || SkyblockWorlds.NETHER.equals(world);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        for (Station s : stations) {
            if (!core.items().is(event.getItemInHand(), s.item())) {
                continue;
            }
            if (!island(event.getBlockPlaced().getWorld().getName())) {
                event.setCancelled(true);
                core.msg().warn(event.getPlayer(), "Stations only work on your Skyblock island.");
                return;
            }
            s.marks().add(event.getBlockPlaced());
            if (StationItems.RANCH.equals(s.item())) {
                service.startRanch(event.getPlayer().getUniqueId());
            }
            return;
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Station s = at(event.getBlock());
        if (s == null) {
            return;
        }
        s.marks().remove(event.getBlock());
        event.setDropItems(false);
        event.getBlock().getWorld().dropItemNaturally(event.getBlock().getLocation().add(0.5, 0.5, 0.5),
            core.items().create(s.item()));
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || event.getAction() != Action.RIGHT_CLICK_BLOCK
                || event.getClickedBlock() == null || event.getPlayer().isSneaking()) {
            return;
        }
        Station s = at(event.getClickedBlock());
        if (s == null) {
            return;
        }
        event.setCancelled(true);
        Player player = event.getPlayer();
        if (!service.config().enabled) {
            core.msg().warn(player, "The stations are switched off.");
            return;
        }
        switch (s.item()) {
            case StationItems.ALEMBIC -> gui.alembic(player);
            case StationItems.CRUCIBLE -> gui.crucible(player);
            default -> gui.ranch(player);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDrink(PlayerItemConsumeEvent event) {
        if (!core.items().is(event.getItem(), StationItems.FLASK) || !event.getItem().hasItemMeta()) {
            return;
        }
        String recipe = event.getItem().getItemMeta().getPersistentDataContainer().get(StationService.RECIPE, PersistentDataType.STRING);
        if (recipe != null) {
            service.drink(event.getPlayer(), recipe);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onKill(EntityDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer != null && service.config().enabled) {
            service.countKill(killer, "kill", event.getEntity().getType().name());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onBossKill(BossKillEvent event) {
        if (!service.config().enabled) {
            return;
        }
        for (BossKillEvent.Damager d : event.getTopDamagers()) {
            Player player = org.bukkit.Bukkit.getPlayer(d.player());
            if (player != null) {
                service.countKill(player, "boss", event.getBossId());
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        service.forget(event.getPlayer().getUniqueId());
    }
}
