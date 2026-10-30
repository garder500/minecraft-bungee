package win.vampyuyu.survival.menu;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

/** Consumes an explicit network selection; an ordinary reconnect stays in its saved mode. */
final class JoinFlow implements Listener {
    private final MenuRuntime rt;
    private final HandoffSource source;
    private final Consumer<Player> onArrive;
    static final int RETRY_TICKS = 20;
    static final int RETRY_ATTEMPTS = 10;

    JoinFlow(MenuRuntime rt, HandoffSource source, Consumer<Player> onArrive) {
        this.rt = rt;
        this.source = source;
        this.onArrive = onArrive;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (source == null) return;
        long selection = rt.core.modes().selection(player);
        rt.core.scheduler().async(() -> {
            Optional<HandoffPayload> payload = Optional.empty();
            try { payload = HandoffPayload.parse(source.take(player.getUniqueId())); }
            catch (Exception | LinkageError e) { rt.core.debug("Handoff lookup failed: " + e); }
            Optional<HandoffPayload> result = payload;
            rt.core.scheduler().sync(() -> {
                if (rt.core.modes().selection(player) == selection) route(player, result);
            });
        });
    }

    void route(Player player, Optional<HandoffPayload> payload) {
        if (!player.isOnline() || Bukkit.getPlayer(player.getUniqueId()) != player) return;
        JoinRouter.Decision decision = JoinRouter.decide(new JoinRouter.Context(payload, System.currentTimeMillis(),
            rt.config().joinMaxAgeMs, false, false, MenuRuntime.inSkyblock(player.getWorld()), JoinRouter.NoKey.STAY));
        switch (decision.action()) {
            case ISLAND -> {
                if (!rt.core.modes().enterIsland(player)) retry(player, RETRY_ATTEMPTS);
            }
            case SURVIVAL -> rt.core.modes().enterSurvival(player);
            default -> { }
        }
        onArrive.accept(player);
    }

    private void retry(Player player, int remaining) {
        if (remaining <= 0) return;
        rt.core.scheduler().later(RETRY_TICKS, () -> {
            if (!player.isOnline() || Bukkit.getPlayer(player.getUniqueId()) != player
                    || !rt.core.modes().awaitingIsland(player)) return;
            if (!rt.core.modes().enterIsland(player)) retry(player, remaining - 1);
        });
    }
}
