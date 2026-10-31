package win.vampyuyu.survival.quarry;

import java.util.Random;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;
import win.vampyuyu.survival.api.event.QuarryProgressEvent;
import win.vampyuyu.survival.core.Core;

/** The blocks of the dune, frost and ember generators: breaking one has a small chance to drop their ore. */
final class OutputListener implements Listener {
    private final Core core;
    private final BlockMarks outputs = new BlockMarks("genout");
    private final Random random;

    OutputListener(Core core) {
        this(core, new Random());
    }

    OutputListener(Core core, Random random) {
        this.core = core;
        this.random = random;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (!CobbleListener.skyblock(block) || !outputs.has(block)) {
            return;
        }
        outputs.remove(block);
        Material type = block.getType();
        String ore = null;
        String quartz = null;
        if ((type == Material.SNOW_BLOCK || type == Material.PACKED_ICE) && random.nextInt(100) < 6) {
            ore = QuarryItems.FROSTIUM;
        } else if ((type == Material.NETHERRACK || type == Material.SOUL_SAND) && random.nextInt(100) < 3) {
            ore = QuarryItems.SUNSTONE;
        } else if (type == Material.NETHERRACK && random.nextInt(100) < 12) {
            quartz = "quartz";
        }
        Player player = event.getPlayer();
        if (ore != null) {
            ItemStack drop = core.items().create(ore);
            block.getWorld().dropItemNaturally(block.getLocation().add(0.5, 0.5, 0.5), drop);
            player.sendMessage(Component.text("You found " + ore + " in the generator block!", NamedTextColor.GOLD));
            Bukkit.getPluginManager().callEvent(new QuarryProgressEvent(player, "ORE_FOUND", ore, 1));
        } else if (quartz != null) {
            block.getWorld().dropItemNaturally(block.getLocation().add(0.5, 0.5, 0.5), new ItemStack(Material.QUARTZ));
        }
    }
}
