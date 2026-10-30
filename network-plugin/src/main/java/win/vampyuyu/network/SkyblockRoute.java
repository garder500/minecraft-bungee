package win.vampyuyu.network;

import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * What a click on a world card does. Every card goes through here so that a player has ONE request in flight, and a
 * Skyblock key can never outlive the player's next choice.
 *
 * <ul>
 *   <li><b>Skyblock from lobby or creative</b>: write {@code sb:transfer:<uuid>} (async), then, back on the main thread,
 *       send {@code Connect survival}. The key is written BEFORE the connect. If Redis is known to be down, or fails,
 *       the player is still connected, without a key.
 *   <li><b>Any other card</b> (Survival, Creative, Lobby): delete a pending Skyblock key (async), THEN connect. Without
 *       this a key from a click that failed (survival restarting) would route the player to Skyblock up to 30 s later.
 *   <li><b>Skyblock from survival itself</b>: a connect is a no-op there (no join event), so the request is handed to the
 *       local survival plugin ({@link SkyblockHandoff#notifyLocal}). No Redis is involved.
 * </ul>
 *
 * After a request finishes the player must wait {@code cooldownMillis} before the next one.
 */
final class SkyblockRoute {
    static final String SURVIVAL = "survival";
    /** Wait after a finished request before the next one (production value; tests use a route without cooldown). */
    static final long COOLDOWN_MS = 3000;

    /** Where in Skyblock the player wants to land. {@code SKYBLOCK} lets the survival side decide (hub or island). */
    enum Dest {
        SKYBLOCK("skyblock", "Skyblock", "Welcome aboard"),
        ISLAND("island", "your island", "Heading home"),
        HUB("hub", "the Skyblock hub", "Setting sail");

        private final String id;
        private final String label;
        private final String subtitle;

        Dest(String id, String label, String subtitle) {
            this.id = id;
            this.label = label;
            this.subtitle = subtitle;
        }

        String id() {
            return id;
        }

        String label() {
            return label;
        }
    }

    private final JavaPlugin plugin;
    private final String server;
    private final SkyblockHandoff handoff;
    private final long cooldownMillis;
    private final Set<UUID> inFlight = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Long> finishedAt = new ConcurrentHashMap<>();

    SkyblockRoute(JavaPlugin plugin, String server, SkyblockHandoff handoff) {
        this(plugin, server, handoff, 0);
    }

    SkyblockRoute(JavaPlugin plugin, String server, SkyblockHandoff handoff, long cooldownMillis) {
        this.plugin = plugin;
        this.server = server;
        this.handoff = handoff;
        this.cooldownMillis = cooldownMillis;
    }

    /** Main thread. Skyblock, where the survival side decides the landing. */
    void go(Player player) {
        go(player, Dest.SKYBLOCK);
    }

    /** Main thread. */
    void go(Player player, Dest dest) {
        UUID id = player.getUniqueId();
        if (!begin(player)) return;
        if (SURVIVAL.equals(server)) { // same server: no Redis, no connect
            inFlight.remove(id);
            finishedAt.put(id, System.currentTimeMillis());
            SkyblockHandoff.Local taken = handoff == null ? SkyblockHandoff.Local.NONE
                    : handoff.notifyLocal(player, dest.id(), System.currentTimeMillis());
            if (taken == SkyblockHandoff.Local.NONE) {
                player.sendMessage(WorldSelector.prefix().append(WorldSelector.text(
                        "Skyblock is not ready on this server yet. Please try again shortly.", WorldSelector.MUTED)));
            } else {
                player.closeInventory();
            }
            return;
        }
        player.closeInventory();
        long now = System.currentTimeMillis();
        if (handoff == null || !handoff.available()) { // known down: do not make the player wait for a timeout
            finish(id, dest, false, now);
            return;
        }
        player.sendMessage(WorldSelector.prefix().append(WorldSelector.text("Preparing your trip to ", WorldSelector.IVORY))
                .append(WorldSelector.text(dest.label(), WorldSelector.SKY)).append(WorldSelector.text("…", WorldSelector.IVORY)));
        async(() -> {
            boolean written = false;
            try {
                written = handoff.write(id, now, dest.id());
            } finally { // whatever happens, the player's request is finished on the main thread
                boolean wrote = written;
                runSync(() -> finish(id, dest, wrote, now));
            }
        }, id);
    }

    /**
     * Main thread. Connects to a plain card's server after clearing a pending Skyblock key, so a request from a click
     * that did not complete cannot redirect the player later.
     */
    void leave(Player player, String targetServer, String label) {
        UUID id = player.getUniqueId();
        if (!begin(player)) return;
        if (handoff == null || !handoff.available()) { // nothing could have been written
            finishPlain(id, targetServer, label);
            return;
        }
        async(() -> {
            try {
                handoff.discard(id);
            } finally {
                runSync(() -> finishPlain(id, targetServer, label));
            }
        }, id);
    }

    private boolean begin(Player player) {
        UUID id = player.getUniqueId();
        long last = finishedAt.getOrDefault(id, 0L);
        if (cooldownMillis > 0 && System.currentTimeMillis() - last < cooldownMillis) return false;
        if (!inFlight.add(id)) return false;
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.35f, 1.3f);
        return true;
    }

    private void async(Runnable task, UUID id) {
        try {
            plugin.getServer().getScheduler().runTaskAsynchronously(plugin, task);
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

    private void finishPlain(UUID id, String targetServer, String label) {
        inFlight.remove(id);
        finishedAt.put(id, System.currentTimeMillis());
        Player player = Bukkit.getPlayer(id);
        if (player == null || !player.isOnline()) return;
        if (!WorldSelector.connect(plugin, player, targetServer)) return;
        player.closeInventory();
        player.sendMessage(WorldSelector.prefix()
                .append(WorldSelector.text("Connecting to ", WorldSelector.IVORY))
                .append(WorldSelector.text(label, WorldSelector.GOLD))
                .append(WorldSelector.text("… If unavailable, please try again shortly.", WorldSelector.MUTED)));
    }

    private void finish(UUID id, Dest dest, boolean written, long ts) {
        inFlight.remove(id);
        finishedAt.put(id, System.currentTimeMillis());
        Player player = Bukkit.getPlayer(id);
        if (player == null || !player.isOnline()) {
            if (written) discardAsync(id); // nobody left to route: drop the key instead of leaving it for a relog
            return;
        }
        if (!written) {
            player.sendMessage(WorldSelector.prefix().append(WorldSelector.text(
                    "Skyblock routing is unavailable, so you will be sent to the Survival server instead.", WorldSelector.MUTED)));
        }
        if (!WorldSelector.connect(plugin, player, SURVIVAL)) {
            if (written) discardAsync(id);
            return;
        }
        player.playSound(player.getLocation(), Sound.ENTITY_ENDER_EYE_LAUNCH, 0.5f, 1.2f);
        if (written) {
            player.showTitle(Title.title(WorldSelector.text("SKYBLOCK", WorldSelector.SKY).decorate(TextDecoration.BOLD),
                    WorldSelector.text(dest.subtitle, WorldSelector.IVORY),
                    Title.Times.times(Duration.ofMillis(150), Duration.ofMillis(1500), Duration.ofMillis(400))));
        }
        player.sendMessage(WorldSelector.prefix().append(WorldSelector.text("Connecting to ", WorldSelector.IVORY))
                .append(WorldSelector.text(written ? dest.label() : "Survival", written ? WorldSelector.SKY : WorldSelector.GOLD))
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

    /** Forget a player who left (called from the selector's quit handler). */
    void forget(UUID id) {
        inFlight.remove(id);
        finishedAt.remove(id);
    }
}
