package win.vampyuyu.survival.quarry;

import java.util.Locale;
import java.util.Optional;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import win.vampyuyu.survival.api.IslandApi;
import win.vampyuyu.survival.api.IslandInfo;
import win.vampyuyu.survival.core.Core;
import win.vampyuyu.survival.core.Msg;

/** Placing, breaking, fuelling and reading the machines. Skyblock worlds and your own island only. */
final class MachineListener implements Listener {
    private final Core core;
    private final MachineService machines;

    MachineListener(Core core, MachineService machines) {
        this.core = core;
        this.machines = machines;
    }

    private Optional<MachineType> typeOf(ItemStack stack) {
        return core.items().idOf(stack).flatMap(MachineType::ofItem);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        Optional<MachineType> type = typeOf(event.getItemInHand());
        if (type.isEmpty()) {
            return;
        }
        Player player = event.getPlayer();
        Block block = event.getBlockPlaced();
        if (!CobbleListener.skyblock(block)) {
            event.setCancelled(true);
            core.msg().warn(player, "Machines only work on your Skyblock island.");
            return;
        }
        IslandInfo island = core.service(IslandApi.class).flatMap(api -> api.islandAt(block.getLocation())).orElse(null);
        if (island == null || !island.members().contains(player.getUniqueId())) {
            event.setCancelled(true);
            core.msg().warn(player, "Place machines on your own island.");
            return;
        }
        int max = machines.settings().maxPerIsland;
        if (machines.store().countOnIsland(island.id().toString()) >= max) {
            event.setCancelled(true);
            core.msg().warn(player, "This island already has " + max + " machines.");
            return;
        }
        MachineData data = new MachineData();
        data.type = type.get().name();
        data.owner = island.owner().toString();
        data.island = island.id().toString();
        data.yaw = BlockModels.facing(player.getLocation().getYaw());
        machines.store().put(block.getWorld().getName(), MachineStore.pos(block), data);
        fire(player, "machine_placed:" + type.get().itemId);
        fire(player, "machine_placed");
        core.getPlugin().getServer().getScheduler().runTask(core.getPlugin(),
            () -> BlockModels.show(block, machines.modelItem(block, data), data.yaw));
    }

    private static void fire(Player player, String key) {
        org.bukkit.Bukkit.getPluginManager().callEvent(new win.vampyuyu.survival.api.event.QuarryProgressEvent(player, "EVENT", key, 1));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        MachineData data = machines.store().get(block.getWorld().getName(), MachineStore.pos(block));
        if (data == null) {
            return;
        }
        MachineType type = data.kind();
        machines.store().remove(block.getWorld().getName(), MachineStore.pos(block));
        BlockModels.hide(block);
        event.setDropItems(false);
        if (type != null) {
            block.getWorld().dropItemNaturally(block.getLocation().add(0.5, 0.5, 0.5), core.items().create(type.itemId));
        }
    }

    /** A cable whose block underneath goes away gives back the cable item, never plain redstone dust. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPhysics(org.bukkit.event.block.BlockPhysicsEvent event) {
        Block block = event.getBlock();
        if (block.getType() != Material.REDSTONE_WIRE || block.getRelative(org.bukkit.block.BlockFace.DOWN).getType().isSolid()) {
            return;
        }
        MachineData data = machines.store().get(block.getWorld().getName(), MachineStore.pos(block));
        if (data == null || data.kind() != MachineType.CABLE) {
            return;
        }
        event.setCancelled(true);
        machines.store().remove(block.getWorld().getName(), MachineStore.pos(block));
        BlockModels.hide(block);
        block.setType(Material.AIR, false);
        block.getWorld().dropItemNaturally(block.getLocation().add(0.5, 0.5, 0.5), core.items().create("cable"));
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || event.getAction() != Action.RIGHT_CLICK_BLOCK
                || event.getClickedBlock() == null || event.getPlayer().isSneaking()) {
            return;
        }
        Block block = event.getClickedBlock();
        MachineData data = machines.store().get(block.getWorld().getName(), MachineStore.pos(block));
        MachineType type = data == null ? null : data.kind();
        if (type == null) {
            return;
        }
        event.setCancelled(true);
        Player player = event.getPlayer();
        ItemStack held = player.getInventory().getItemInMainHand();
        MachineSettings cfg = machines.settings();
        if (type == MachineType.COAL_GENERATOR && held.getType() == Material.COAL && core.items().idOf(held).isEmpty()) {
            int amount = held.getAmount();
            data.fuel += amount * cfg.coalSeconds;
            held.setAmount(0);
            machines.store().markDirty();
            core.msg().ok(player, "Added " + amount + " coal: " + Msg.duration(data.fuel * 1000L) + " of fuel.");
            return;
        }
        if (type == MachineType.SPRING_STONE && held.getType() == Material.BUCKET) {
            player.getInventory().setItemInMainHand(new ItemStack(Material.WATER_BUCKET));
            core.msg().ok(player, "The Spring Stone never runs dry: bucket filled.");
            fire(player, "spring_filled");
            return;
        }
        switch (type) {
            case FOUNTAIN -> {
                GeneratorStructure.Result found = machines.structureOf(block);
                if (found.complete()) {
                    core.msg().ok(player, found.kind().title + ": one block every second above this Fountain.");
                } else {
                    core.msg().info(player, "Fountain: one cobblestone every 2 seconds. To build the " + found.nearest().title
                        + ", still missing: " + String.join(", ", found.missing()) + ". See /guide.");
                }
            }
            case SPRING_STONE -> core.msg().info(player, "Spring Stone: right-click it with an empty bucket for water, "
                + "or put it on the south side of a Fountain's ring.");
            case COAL_GENERATOR -> core.msg().info(player, "Coal Generator: " + (data.fuel > 0
                ? Msg.duration(data.fuel * 1000L) + " of fuel left, " + (int) cfg.generatorOutput + " Flux/s."
                : "out of fuel. Right-click it holding coal."));
            case BATTERY -> core.msg().info(player, "Battery: " + data.charge + " / " + cfg.batteryCapacity + " Flux.");
            case CABLE -> core.msg().info(player, "Copper Cable: it joins the machines that touch it.");
            case SOLAR_PANEL -> core.msg().info(player, "Solar Panel: " + (int) cfg.solarOutput + " Flux/s in daylight with the sky open above it.");
            default -> core.msg().info(player, type.title + ": needs generator cobble and a chest or barrel next to it, and "
                + String.format(Locale.ROOT, "%.0f", cfg.consume(type)) + " Flux/s from a network.");
        }
    }
}
