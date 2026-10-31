package win.vampyuyu.survival.quarry;

import java.util.UUID;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import win.vampyuyu.survival.api.IslandApi;
import win.vampyuyu.survival.api.IslandInfo;
import win.vampyuyu.survival.core.Core;
import win.vampyuyu.survival.core.SkyblockWorlds;

/** The Forge block (place, open, pick up), the Forge recipe guard and the Bronze Core. Skyblock worlds only. */
final class ForgeListener implements Listener {
    private final Core core;
    private final QuarryService service;
    private final ForgeGui gui;
    private final BlockMarks forges = new BlockMarks("forge");

    ForgeListener(Core core, QuarryService service, ForgeGui gui) {
        this.core = core;
        this.service = service;
        this.gui = gui;
    }

    private boolean id(ItemStack stack, String id) {
        return core.items().idOf(stack).filter(id::equals).isPresent();
    }

    boolean isForge(Block block) {
        return (block.getType() == Material.BARRIER || block.getType() == Material.BLAST_FURNACE) && forges.has(block);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (!id(event.getItemInHand(), QuarryItems.FORGE)) {
            return;
        }
        if (!CobbleListener.skyblock(event.getBlockPlaced())) {
            event.setCancelled(true);
            core.msg().warn(event.getPlayer(), "The Forge only works on your Skyblock island.");
            return;
        }
        forges.add(event.getBlockPlaced());
        event.getBlockPlaced().setType(Material.BARRIER, false); // an invisible hitbox under the 3D model
        org.bukkit.Bukkit.getPluginManager().callEvent(
            new win.vampyuyu.survival.api.event.QuarryProgressEvent(event.getPlayer(), "EVENT", "forge_placed", 1));
        BlockModels.show(event.getBlockPlaced(), core.items().create(QuarryItems.FORGE),
            BlockModels.facing(event.getPlayer().getLocation().getYaw()));
    }

    /** A Forge from an older version (a blast furnace) becomes the barrier + model setup when its chunk loads. */
    @EventHandler
    public void onChunk(org.bukkit.event.world.ChunkLoadEvent event) {
        convert(event.getChunk());
    }

    void convert(org.bukkit.Chunk chunk) {
        for (Block block : forges.blocks(chunk)) {
            if (block.getType() == Material.BLAST_FURNACE) {
                block.setType(Material.BARRIER, false);
                core.scheduler().nextTick(() -> BlockModels.show(block, core.items().create(QuarryItems.FORGE), 180f));
            } else if (block.getType() == Material.BARRIER && !BlockModels.has(block)) {
                core.scheduler().nextTick(() -> BlockModels.show(block, core.items().create(QuarryItems.FORGE), 180f));
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(org.bukkit.event.block.BlockDamageEvent event) {
        if (event.getBlock().getType() == Material.BARRIER && forges.has(event.getBlock())) {
            event.setInstaBreak(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (!isForge(block)) {
            return;
        }
        forges.remove(block);
        BlockModels.hide(block);
        event.setDropItems(false);
        block.getWorld().dropItemNaturally(block.getLocation().add(0.5, 0.5, 0.5), core.items().create(QuarryItems.FORGE));
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Player player = event.getPlayer();
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK && event.getClickedBlock() != null
                && isForge(event.getClickedBlock()) && !player.isSneaking()) {
            event.setCancelled(true);
            gui.open(player);
            return;
        }
        if ((event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK)
                && id(event.getItem(), QuarryItems.BRONZE_CORE)) {
            event.setCancelled(true);
            useCore(player, event.getItem(), 2, "Bronze Core", false);
        } else if ((event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK)
                && id(event.getItem(), QuarryItems.VOID_LENS)) {
            event.setCancelled(true);
            useLens(player, event.getItem());
        } else if ((event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK)
                && id(event.getItem(), QuarryItems.EMBER_CORE)) {
            event.setCancelled(true);
            useCore(player, event.getItem(), 3, "Ember Core", false);
        }
    }

    private void useLens(Player player, ItemStack item) {
        if (!SkyblockWorlds.WORLD.equals(player.getWorld().getName())) {
            core.msg().warn(player, "Use the Void Lens on your Skyblock island.");
            return;
        }
        IslandInfo island = core.service(IslandApi.class).flatMap(api -> api.islandAt(player.getLocation())).orElse(null);
        if (island == null || !island.members().contains(player.getUniqueId())) {
            core.msg().warn(player, "Use the Void Lens on your own island.");
            return;
        }
        if (service.tier(island.owner()) < 3) {
            core.msg().warn(player, "Your generator must be tier III (Ember) before the Void Lens can work.");
            return;
        }
        boolean opened = service.unlockEnd(player.getUniqueId());
        boolean upgraded = service.tier(island.owner()) == 3 && service.upgrade(island.owner(), 4);
        if (!opened && !upgraded) {
            core.msg().warn(player, "You already used a Void Lens. Use /island end.");
            return;
        }
        item.setAmount(item.getAmount() - 1);
        if (upgraded) {
            org.bukkit.Bukkit.getPluginManager().callEvent(new win.vampyuyu.survival.api.event.QuarryProgressEvent(player, "GENERATOR_UPGRADE", "4", 4));
        }
        core.msg().ok(player, "The Void Lens shatters. The way to your End is open (/island end)"
            + (upgraded ? " and your generator is now tier IV (Void)!" : "."));
    }

    private void useCore(Player player, ItemStack item, int tier, String name, boolean nether) {
        String world = player.getWorld().getName();
        if (!SkyblockWorlds.WORLD.equals(world) && !SkyblockWorlds.NETHER.equals(world)) {
            core.msg().warn(player, "The " + name + " only works on your Skyblock island.");
            return;
        }
        if (nether && !SkyblockWorlds.NETHER.equals(world)) {
            core.msg().warn(player, "Use the " + name + " on the Nether platform of your island.");
            return;
        }
        IslandInfo island = core.service(IslandApi.class).flatMap(api -> api.islandAt(player.getLocation())).orElse(null);
        if (island == null || !island.members().contains(player.getUniqueId())) {
            core.msg().warn(player, "Use the " + name + " on your own island.");
            return;
        }
        UUID owner = island.owner();
        if (service.tier(owner) != tier - 1 || !service.upgrade(owner, tier)) {
            core.msg().warn(player, service.tier(owner) >= tier ? "This generator is already tier " + tier + " or higher."
                : "Your generator must be tier " + (tier - 1) + " first.");
            return;
        }
        item.setAmount(item.getAmount() - 1);
        org.bukkit.Bukkit.getPluginManager().callEvent(new win.vampyuyu.survival.api.event.QuarryProgressEvent(player, "GENERATOR_UPGRADE", String.valueOf(tier), tier));
        core.msg().ok(player, "Your cobble generator is now tier " + (tier == 2 ? "II (Vein)" : "III (Ember)") + "! New ores are waiting in the cobble.");
    }

    // The Forge recipe works in a Skyblock world only: Survival never gets the item.
    @EventHandler(priority = EventPriority.HIGH)
    public void onPrepare(PrepareItemCraftEvent event) {
        ItemStack result = event.getInventory().getResult();
        if (result != null && id(result, QuarryItems.FORGE) && !sky(event.getView().getPlayer().getWorld().getName())) {
            event.getInventory().setResult(null);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCraft(CraftItemEvent event) {
        if (id(event.getRecipe().getResult(), QuarryItems.FORGE) && !sky(event.getWhoClicked().getWorld().getName())) {
            event.setCancelled(true);
        }
    }

    private static boolean sky(String world) {
        return SkyblockWorlds.WORLD.equals(world) || SkyblockWorlds.NETHER.equals(world);
    }
}
