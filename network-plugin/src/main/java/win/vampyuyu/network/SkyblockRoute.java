package win.vampyuyu.network;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * What a click on the Skyblock card does.
 *
 * <ul>
 *   <li>From lobby or creative: write {@code sb:transfer:<uuid>} (async), then, back on the main thread, send
 *       {@code Connect survival}. The key is always written BEFORE the connect so it is there when the
 *       survival plugin reads it on join. If Redis fails the player is still connected, without a key.
 *   <li>From survival itself a connect is a no-op (no join event fires), so the request is handed to the local
 *       survival plugin instead ({@link SkyblockHandoff#notifyLocal}); the key is written too so that side can
 *       consume it, and removed again if nobody took the request.
 * </ul>
 *
 * A player has at most one request in flight, so spam-clicking cannot queue several writes and connects.
 */
final class SkyblockRoute {
    static final String SURVIVAL = "survival";

    private final JavaPlugin plugin;
    private final String server;
    private final SkyblockHandoff handoff;
    private final Set<UUID> inFlight = ConcurrentHashMap.newKeySet();

    SkyblockRoute(JavaPlugin plugin, String server, SkyblockHandoff handoff) {
        this.plugin = plugin;
        this.server = server;
        this.handoff = handoff;
    }

    /** Main thread. */
    void go(Player player) {
        UUID id = player.getUniqueId();
        if (!inFlight.add(id)) return;
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.35f, 1.3f);
        player.closeInventory();
        player.sendMessage(WorldSelector.prefix().append(WorldSelector.text("Preparing your trip to ", WorldSelector.IVORY))
                .append(WorldSelector.text("Skyblock", WorldSelector.SKY)).append(WorldSelector.text("…", WorldSelector.IVORY)));
        long now = System.currentTimeMillis();
        try {
            plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                boolean written = false;
                try {
                    written = handoff != null && handoff.write(id, now);
                } finally { // whatever happens, the player's request is finished on the main thread
                    boolean wrote = written;
                    runSync(() -> finish(id, wrote, now));
                }
            });
        } catch (RuntimeException failure) { // the plugin is being disabled
            inFlight.remove(id);
        }
    }

    private void runSync(Runnable task) {
        if (!plugin.isEnabled()) return;
        try {
            plugin.getServer().getScheduler().runTask(plugin, task);
        } catch (RuntimeException ignored) {
            // disabled between the check and the call: nothing left to do
        }
    }

    private void finish(UUID id, boolean written, long ts) {
        inFlight.remove(id);
        Player player = Bukkit.getPlayer(id);
        if (player == null || !player.isOnline()) {
            if (written) discardAsync(id); // nobody left to route: drop the key instead of leaving it for a relog
            return;
        }
        if (SURVIVAL.equals(server)) {
            SkyblockHandoff.Local taken = handoff == null ? SkyblockHandoff.Local.NONE
                    : handoff.notifyLocal(player, SkyblockHandoff.payload(ts));
            if (taken == SkyblockHandoff.Local.NONE) {
                if (written) discardAsync(id);
                player.sendMessage(WorldSelector.prefix().append(WorldSelector.text(
                        "Skyblock is not ready on this server yet. Please try again shortly.", WorldSelector.MUTED)));
            }
            return;
        }
        if (!written) {
            player.sendMessage(WorldSelector.prefix().append(WorldSelector.text(
                    "Skyblock routing is unavailable, so you will be sent to the Survival server instead.", WorldSelector.MUTED)));
        }
        WorldSelector.connect(plugin, player, SURVIVAL);
        player.sendMessage(WorldSelector.prefix().append(WorldSelector.text("Connecting to ", WorldSelector.IVORY))
                .append(WorldSelector.text(written ? "Skyblock" : "Survival", written ? WorldSelector.SKY : WorldSelector.GOLD))
                .append(WorldSelector.text("… If unavailable, please try again shortly.", WorldSelector.MUTED)));
    }

    private void discardAsync(UUID id) {
        if (handoff == null || !plugin.isEnabled()) return;
        try {
            plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> handoff.discard(id));
        } catch (RuntimeException ignored) {
            // shutting down: the 30 s TTL removes it
        }
    }
}
