package win.vampyuyu.survival.quarry;

import java.util.Random;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockFormEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import win.vampyuyu.survival.api.IslandApi;
import win.vampyuyu.survival.api.event.QuarryProgressEvent;
import win.vampyuyu.survival.api.IslandInfo;
import win.vampyuyu.survival.core.Core;
import win.vampyuyu.survival.core.SkyblockWorlds;

/**
 * Marks cobblestone that water and lava form on an island and rolls the ore table when a player breaks it. Only the
 * Skyblock overworld and Nether are concerned: nothing here ever fires in Survival.
 */
final class CobbleListener implements Listener {
    private static final NamespacedKey LUCK = new NamespacedKey("vampyuyu", QuarryItems.MINING_LUCK);

    private final Core core;
    private final QuarryService service;
    private final BlockMarks generated = new BlockMarks("cobble");
    private final Random random = new Random();

    CobbleListener(Core core, QuarryService service) {
        this.core = core;
        this.service = service;
    }

    static boolean skyblock(Block block) {
        String name = block.getWorld().getName();
        return SkyblockWorlds.WORLD.equals(name) || SkyblockWorlds.NETHER.equals(name);
    }

    /** Water meeting lava makes cobblestone (flowing sideways) or stone (lava falling): both are generator blocks. */
    static boolean generatorBlock(Material type) {
        return type == Material.COBBLESTONE || type == Material.STONE;
    }

    boolean isGenerated(Block block) {
        return generated.has(block);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onForm(BlockFormEvent event) {
        if (generatorBlock(event.getNewState().getType()) && skyblock(event.getBlock())) {
            generated.add(event.getBlock());
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (!generatorBlock(block.getType()) || !skyblock(block) || !generated.has(block)) {
            return;
        }
        generated.remove(block);
        Player player = event.getPlayer();
        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) {
            return;
        }
        UUID owner = core.service(IslandApi.class).flatMap(api -> api.islandAt(block.getLocation()))
            .map(IslandInfo::owner).orElse(null);
        if (owner == null) {
            return;
        }
        service.draw(owner, luck(player.getInventory().getItemInMainHand()), random, drop -> {
            Location at = block.getLocation().add(0.5, 0.5, 0.5);
            block.getWorld().dropItemNaturally(at, drop.stack());
            Bukkit.getPluginManager().callEvent(new QuarryProgressEvent(player, "ORE_FOUND", drop.ore(), drop.stack().getAmount()));
        }, (geode, pityForced, name) -> {
            if (geode) {
                player.sendMessage(Component.text("You cracked open a geode!", NamedTextColor.AQUA));
                player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1f, 1f);
            } else if (name != null) {
                player.sendMessage(Component.text("You found " + name + " in the cobble!", NamedTextColor.GOLD));
                player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8f, 1.3f);
            }
        });
    }

    private double luck(ItemStack held) {
        if (held == null || held.getType().isAir() || !held.hasItemMeta()) {
            return 0;
        }
        Long value = held.getItemMeta().getPersistentDataContainer().get(LUCK, PersistentDataType.LONG);
        return value == null ? 0 : value;
    }
}
