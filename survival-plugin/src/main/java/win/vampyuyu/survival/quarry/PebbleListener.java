package win.vampyuyu.survival.quarry;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import win.vampyuyu.survival.api.IslandApi;
import win.vampyuyu.survival.api.IslandInfo;
import win.vampyuyu.survival.api.event.QuarryProgressEvent;
import win.vampyuyu.survival.core.Core;

/**
 * The start of the game: right-click the ground of your island (empty hand or a stick) for a Stone Pebble, nine
 * pebbles make a cobblestone. A short delay between clicks and a daily cap keep it from replacing the generators, and
 * once a player has made a Fountain the pebbles stop.
 */
final class PebbleListener implements Listener {
    static final long COOLDOWN_MILLIS = 600;
    static final int DAILY_CAP = 200;

    private final Core core;
    private final QuarryService quarry;
    private final Map<UUID, Long> last = new HashMap<>();

    PebbleListener(Core core, QuarryService quarry) {
        this.core = core;
        this.quarry = quarry;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onGround(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || event.getAction() != Action.RIGHT_CLICK_BLOCK
                || event.getClickedBlock() == null || !CobbleListener.skyblock(event.getClickedBlock())) {
            return;
        }
        Player player = event.getPlayer();
        ItemStack held = event.getItem();
        boolean holdingPebbles = held != null && core.items().idOf(held).map(QuarryItems.PEBBLE::equals).orElse(false);
        if (held != null && !held.getType().isAir() && held.getType() != Material.STICK && !holdingPebbles) {
            return;
        }
        Material ground = event.getClickedBlock().getType();
        if (ground != Material.GRASS_BLOCK && ground != Material.DIRT && ground != Material.COARSE_DIRT
                && ground != Material.STONE && ground != Material.COBBLESTONE && ground != Material.SAND
                && ground != Material.GRAVEL && ground != Material.PODZOL && ground != Material.ROOTED_DIRT) {
            return;
        }
        IslandInfo island = core.service(IslandApi.class).flatMap(api -> api.islandAt(event.getClickedBlock().getLocation()))
            .orElse(null);
        if (island == null || !island.members().contains(player.getUniqueId())) {
            return;
        }
        event.setCancelled(true);
        QuarryData data = quarry.data(player.getUniqueId());
        if (data.fountainMade) {
            core.msg().info(player, "You have a Fountain now: no more pebbles to pick up.");
            return;
        }
        long now = System.currentTimeMillis();
        Long before = last.get(player.getUniqueId());
        if (before != null && now - before < COOLDOWN_MILLIS) {
            return;
        }
        long today = LocalDate.now(ZoneOffset.UTC).toEpochDay();
        if (data.pebbleDay != today) {
            data.pebbleDay = today;
            data.pebbles = 0;
        }
        if (data.pebbles >= DAILY_CAP) {
            core.msg().warn(player, "You found all the pebbles you can for today (" + DAILY_CAP + "). Come back tomorrow.");
            return;
        }
        last.put(player.getUniqueId(), now);
        data.pebbles++;
        core.profiles().markDirty(player.getUniqueId());
        ItemStack pebble = core.items().create(QuarryItems.PEBBLE);
        player.getInventory().addItem(pebble).values()
            .forEach(rest -> player.getWorld().dropItemNaturally(player.getLocation(), rest));
        player.playSound(player.getLocation(), Sound.BLOCK_GRAVEL_HIT, 0.6f, 1.6f);
        player.sendActionBar(net.kyori.adventure.text.Component.text("Stone Pebble (" + data.pebbles + "/" + DAILY_CAP
            + " today). 9 pebbles craft a cobblestone.", net.kyori.adventure.text.format.NamedTextColor.GRAY));
        org.bukkit.Bukkit.getPluginManager().callEvent(new QuarryProgressEvent(player, "EVENT", "pebble", 1));
    }

    /** Crafting the Fountain ends the pebbles for good. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCraft(CraftItemEvent event) {
        if (event.getWhoClicked() instanceof Player player && event.getRecipe().getResult() != null
                && core.items().idOf(event.getRecipe().getResult()).map(QuarryItems.FOUNTAIN::equals).orElse(false)) {
            quarry.data(player.getUniqueId()).fountainMade = true;
            core.profiles().markDirty(player.getUniqueId());
        }
    }
}
