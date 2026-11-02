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

    /** A cable's block is replaceable: a sneaking click on it would put the new block in its place and lose the cable. */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onReplace(BlockPlaceEvent event) {
        Block block = event.getBlockPlaced();
        MachineData data = machines.store().get(block.getWorld().getName(), MachineStore.pos(block));
        if (data != null && data.kind() != null && event.getBlockReplacedState().getType() == data.kind().block) {
            event.setCancelled(true);
        }
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
        MachineData data = new MachineData();
        data.type = type.get().name();
        data.owner = island.owner().toString();
        data.island = island.id().toString();
        data.yaw = type.get() == MachineType.CABLE ? MachineService.CABLE_YAW : BlockModels.facing(player.getLocation().getYaw());
        machines.store().put(block.getWorld().getName(), MachineStore.pos(block), data);
        block.setType(type.get().block, false); // the vanilla block the item placed becomes an invisible hitbox
        fire(player, "machine_placed:" + type.get().itemId);
        fire(player, "machine_placed");
        core.getPlugin().getServer().getScheduler().runTask(core.getPlugin(),
            () -> {
                BlockModels.show(block, machines.modelItem(block, data), data.yaw);
                machines.refreshCablesAround(block);
            });
    }

    /** Slots the module held into a Miner; the one already there comes back to the player. */
    private void installModule(Player player, Block block, MachineData data, MachineModule module) {
        MachineModule old = MachineModule.of(data.module);
        if (old == module) {
            core.msg().info(player, "This Miner already has the " + module.title + ".");
            return;
        }
        ItemStack held = player.getInventory().getItemInMainHand();
        held.setAmount(held.getAmount() - 1);
        if (old != null) {
            giveBack(player, block, old);
        }
        data.module = module.name();
        machines.store().markDirty();
        core.msg().ok(player, module.title + " installed. " + describe(module));
    }

    private void removeModule(Player player, Block block, MachineData data) {
        MachineModule old = MachineModule.of(data.module);
        data.module = "";
        machines.store().markDirty();
        giveBack(player, block, old);
        core.msg().ok(player, old.title + " taken out.");
    }

    private void giveBack(Player player, Block block, MachineModule module) {
        for (ItemStack left : player.getInventory().addItem(core.items().create(module.itemId)).values()) {
            block.getWorld().dropItemNaturally(block.getLocation().add(0.5, 1.2, 0.5), left);
        }
    }

    private String describe(MachineModule module) {
        MachineSettings cfg = machines.settings();
        return switch (module) {
            case EMBER -> String.format(Locale.ROOT, "Speed x%.1f, Flux use x%.1f, smelts what it breaks.", cfg.emberRate, cfg.emberConsume);
            case BLOOM -> String.format(Locale.ROOT, "Speed x%.1f, Flux use x%.1f, %.0f%% of cycles also give a bone meal.",
                cfg.bloomRate, cfg.bloomConsume, cfg.bloomBoneMealChance * 100);
            case MEMORY -> String.format(Locale.ROOT, "Speed x%.1f, Flux use x%.1f, %.0f%% of cycles distil a Memory Shard for the owner.",
                cfg.memoryRate, cfg.memoryConsume, cfg.memoryShardChance * 100);
            case CRYO -> String.format(Locale.ROOT, "Speed x%.1f, Flux use x%.1f, %.0f%% of cycles also give a packed ice.",
                cfg.cryoRate, cfg.cryoConsume, cfg.cryoIceChance * 100);
        };
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
        machines.refreshCablesAround(block);
        event.setDropItems(false);
        if (type != null) {
            block.getWorld().dropItemNaturally(block.getLocation().add(0.5, 0.5, 0.5), core.items().create(type.itemId));
        }
    }

    /** A barrier cannot be broken in survival: a machine or Forge barrier breaks at the first punch instead. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(org.bukkit.event.block.BlockDamageEvent event) {
        Block block = event.getBlock();
        if (block.getType() == Material.BARRIER
                && machines.store().get(block.getWorld().getName(), MachineStore.pos(block)) != null) {
            event.setInstaBreak(true);
        }
    }

    /** A cable whose block underneath goes away gives back the cable item, never plain redstone dust. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPhysics(org.bukkit.event.block.BlockPhysicsEvent event) {
        Block block = event.getBlock();
        if (block.getType() != MachineType.CABLE.block || block.getRelative(org.bukkit.block.BlockFace.DOWN).getType().isSolid()) {
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
                || event.getClickedBlock() == null) {
            return;
        }
        Block block = event.getClickedBlock();
        MachineData data = machines.store().get(block.getWorld().getName(), MachineStore.pos(block));
        MachineType type = data == null ? null : data.kind();
        if (type == null) {
            return;
        }
        Player player = event.getPlayer();
        ItemStack held = player.getInventory().getItemInMainHand();
        if (player.isSneaking()) { // sneaking keeps placing blocks, except an empty hand which takes the module out
            if (type.miner() && held.getType().isAir() && MachineModule.of(data.module) != null) {
                event.setCancelled(true);
                removeModule(player, block, data);
            }
            return;
        }
        event.setCancelled(true);
        Optional<MachineModule> module = core.items().idOf(held).flatMap(MachineModule::ofItem);
        if (module.isPresent()) {
            if (type.miner()) {
                installModule(player, block, data, module.get());
            } else {
                core.msg().warn(player, module.get().title + " fits a Miner.");
            }
            return;
        }
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
            default -> {
                MachineModule slotted = MachineModule.of(data.module);
                core.msg().info(player, type.title + ": needs generator cobble and a chest or barrel next to it, and "
                    + String.format(Locale.ROOT, "%.0f", cfg.consume(data)) + " Flux/s from a network."
                    + (slotted == null ? " Right-click with a Realm module to slot it in."
                        : " " + slotted.title + " slotted (sneak with an empty hand to take it out)."));
            }
        }
    }

    /** A deleted island leaves no machines behind (they would keep working on the empty cell). */
    @EventHandler
    public void onIslandDeleted(win.vampyuyu.survival.api.event.IslandDeleteEvent event) {
        machines.store().removeIsland(event.getIslandId().toString());
    }
}
