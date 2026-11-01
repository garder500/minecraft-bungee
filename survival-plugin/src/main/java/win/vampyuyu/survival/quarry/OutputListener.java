package win.vampyuyu.survival.quarry;

import java.util.Optional;
import java.util.Random;
import java.util.UUID;
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
import win.vampyuyu.survival.api.IslandApi;
import win.vampyuyu.survival.api.event.QuarryProgressEvent;
import win.vampyuyu.survival.core.Core;

/**
 * The blocks of the dune, frost and ember generators: breaking one rolls the ores of its ring (the {@code frost} and
 * {@code ember} rows of the ore table, with the same pity counters as the cobble draw).
 */
final class OutputListener implements Listener {
    private final Core core;
    private final QuarryService service;
    private final BlockMarks outputs = new BlockMarks("genout");
    private final Random random;

    OutputListener(Core core, QuarryService service) {
        this(core, service, new Random());
    }

    OutputListener(Core core, QuarryService service, Random random) {
        this.core = core;
        this.service = service;
        this.random = random;
    }

    /** Which ring generator makes a block type; null for the others (dunes give nothing). */
    static String source(Material type) {
        return switch (type) {
            case SNOW_BLOCK, PACKED_ICE -> "frost";
            case NETHERRACK, SOUL_SAND -> "ember";
            default -> null;
        };
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (!CobbleListener.skyblock(block) || !outputs.has(block)) {
            return;
        }
        outputs.remove(block);
        String source = source(block.getType());
        if (source == null) {
            return;
        }
        Player player = event.getPlayer();
        UUID owner = core.service(IslandApi.class).flatMap(api -> api.islandAt(block.getLocation()))
            .map(win.vampyuyu.survival.api.IslandInfo::owner).orElse(player.getUniqueId());
        Optional<QuarryService.Drop> drop = service.drawOutput(owner, source, block.getType(), random);
        if (drop.isEmpty()) {
            return;
        }
        block.getWorld().dropItemNaturally(block.getLocation().add(0.5, 0.5, 0.5), drop.get().stack());
        if (core.items().idOf(drop.get().stack()).isPresent()) { // a custom ore is announced; plain quartz just drops
            String ore = drop.get().ore();
            player.sendMessage(Component.text("You found " + ore + " in the generator block!", NamedTextColor.GOLD));
            Bukkit.getPluginManager().callEvent(new QuarryProgressEvent(player, "ORE_FOUND", ore, 1));
        }
    }
}
