package win.vampyuyu.survival.menu;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import win.vampyuyu.survival.api.IslandApi;
import win.vampyuyu.survival.core.Msg;
import win.vampyuyu.survival.core.SkyblockWorlds;

/**
 * The ONLY join router of the Skyblock mode: on join it consumes the cross-server destination key (off the main thread),
 * asks {@link JoinRouter} what to do, teleports through {@code IslandApi}, then runs the arrival hooks (menu item, quests,
 * fairy modifiers, sidebar, welcome). It also routes deaths in Skyblock worlds to the island or the hub.
 */
final class JoinFlow implements Listener {
    private final MenuRuntime rt;
    private final HandoffSource source;
    private final Consumer<Player> onArrive;

    /**
     * @param source the handoff key backend, null when Redis is disabled
     * @param onArrive runs on the main thread once the player is settled (join processed and routed)
     */
    JoinFlow(MenuRuntime rt, HandoffSource source, Consumer<Player> onArrive) {
        this.rt = rt;
        this.source = source;
        this.onArrive = onArrive;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID id = player.getUniqueId();
        if (source == null) {
            rt.core.scheduler().nextTick(() -> route(player, Optional.empty()));
            return;
        }
        rt.core.scheduler().async(() -> {
            Optional<HandoffPayload> payload = Optional.empty();
            try {
                payload = HandoffPayload.parse(source.take(id));
            } catch (RedisHandoffSource.Unavailable e) {
                rt.core.debug("Handoff skipped (Redis in back-off) for " + player.getName());
            } catch (Exception | LinkageError e) {
                rt.core.debug("Handoff lookup failed for " + player.getName() + ": " + e);
            }
            Optional<HandoffPayload> result = payload;
            rt.core.scheduler().sync(() -> route(player, result));
        });
    }

    /** Ticks between two attempts to reach a hub that is still being generated. */
    static final int RETRY_TICKS = 20;
    /** Attempts after the first one (10 seconds in all). */
    static final int RETRY_ATTEMPTS = 10;

    /** Decides and applies the destination, then runs the arrival hooks. Main thread. */
    void route(Player player, Optional<HandoffPayload> payload) {
        if (!player.isOnline() || Bukkit.getPlayer(player.getUniqueId()) != player) {
            return;
        }
        MenuConfig cfg = rt.config();
        Optional<IslandApi> islands = rt.islands();
        boolean hasIsland = islands.map(api -> api.islandOf(player.getUniqueId()).isPresent()).orElse(false);
        JoinRouter.Decision decision = JoinRouter.decide(new JoinRouter.Context(payload, System.currentTimeMillis(),
            cfg.joinMaxAgeMs, hasIsland, rt.inDungeon(player), MenuRuntime.inSkyblock(player.getWorld()), cfg.noKey));
        rt.core.debug("Join route for " + player.getName() + ": " + decision.action() + " (" + decision.reason() + ")");
        boolean moved = false;
        if (decision.action() != JoinRouter.Action.STAY) {
            if (islands.isEmpty()) {
                rt.core.debug("Island feature unavailable: " + player.getName() + " is not moved");
            } else {
                moved = apply(player, islands.get(), decision.action());
                if (!moved) {
                    scheduleRetry(player, decision.action(), RETRY_ATTEMPTS);
                }
            }
        }
        scheduleArrival(player, moved ? 10 : 1);
    }

    private void scheduleArrival(Player player, int delay) {
        rt.core.scheduler().later(delay, () -> {
            if (player.isOnline()) {
                onArrive.accept(player);
                promptIfHomeless(player);
            }
        });
    }

    /** The hub may still be generating on a first boot: keep trying for a few seconds instead of giving up. */
    private void scheduleRetry(Player player, JoinRouter.Action action, int attemptsLeft) {
        if (attemptsLeft <= 0) {
            rt.core.debug("Gave up routing " + player.getName() + ": the destination never became available");
            return;
        }
        rt.core.scheduler().later(RETRY_TICKS, () -> {
            Optional<IslandApi> islands = rt.islands();
            if (!player.isOnline() || Bukkit.getPlayer(player.getUniqueId()) != player || islands.isEmpty()
                    || MenuRuntime.inSkyblock(player.getWorld()) || rt.inDungeon(player)) {
                return; // gone, or something else (a command, the island feature) already put them somewhere useful
            }
            if (apply(player, islands.get(), action)) {
                scheduleArrival(player, 10);
            } else {
                scheduleRetry(player, action, attemptsLeft - 1);
            }
        });
    }

    private boolean apply(Player player, IslandApi islands, JoinRouter.Action action) {
        try {
            if (action == JoinRouter.Action.ISLAND) {
                return islands.teleportHome(player) || islands.teleportHub(player);
            }
            return islands.teleportHub(player);
        } catch (RuntimeException e) {
            rt.core.getPlugin().getLogger().warning("Could not route " + player.getName() + ": " + e);
            return false;
        }
    }

    /** True when the player stands in the hub and has no island yet: they get the create-island prompt. */
    boolean needsIslandPrompt(Player player) {
        if (!SkyblockWorlds.HUB.equals(player.getWorld().getName()) || rt.inDungeon(player)) {
            return false;
        }
        Optional<IslandApi> islands = rt.islands();
        return islands.isPresent() && islands.get().islandOf(player.getUniqueId()).isEmpty();
    }

    private void promptIfHomeless(Player player) {
        if (needsIslandPrompt(player)) {
            createIslandPrompt(player);
        }
    }

    /** Chat prompt with a clickable button that runs the configured island creation command. */
    void createIslandPrompt(Player player) {
        Optional<String> found = rt.createIslandCommand(player);
        String command = found.orElse("");
        Component line;
        if (found.isPresent()) {
            line = Component.text("You do not have an island yet! ", NamedTextColor.YELLOW)
                .append(Component.text("[CREATE YOUR ISLAND]", NamedTextColor.GREEN, TextDecoration.BOLD)
                    .clickEvent(ClickEvent.runCommand("/" + command))
                    .hoverEvent(Component.text("Click to create your island", NamedTextColor.GRAY)));
        } else {
            line = Component.text("You do not have an island yet. Island creation is not available right now.",
                NamedTextColor.YELLOW);
        }
        player.sendMessage(rt.core.msg().prefix().append(line));
    }

    // ---------------------------------------------------------------- respawn

    /**
     * Opt-in respawn routing. The island feature owns respawns (it checks that the island is ready), so the default
     * route is {@code none}. With {@code island} or {@code hub} this only teleports one tick AFTER the respawn through
     * {@link IslandApi}; it never edits the respawn location.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent event) {
        String route = rt.config().respawnRoute;
        Player player = event.getPlayer();
        if (route.equals("none") || event.isBedSpawn() || event.isAnchorSpawn()
                || !MenuRuntime.inSkyblock(player.getWorld())
                || SkyblockWorlds.isDungeon(player.getWorld().getName()) || rt.inDungeon(player)) {
            return;
        }
        rt.core.scheduler().later(1, () -> {
            Optional<IslandApi> islands = rt.islands();
            if (islands.isEmpty() || !player.isOnline() || player.isDead() || rt.inDungeon(player)) {
                return;
            }
            try {
                if (route.equals("island")) {
                    if (!islands.get().teleportHome(player)) {
                        islands.get().teleportHub(player);
                    }
                } else {
                    islands.get().teleportHub(player);
                }
            } catch (RuntimeException e) {
                rt.core.getPlugin().getLogger().warning("Could not route the respawn of " + player.getName() + ": " + e);
            }
        });
    }

    // ---------------------------------------------------------------- welcome

    /** First arrival in a Skyblock world: title, sound, tip. Marks the flag before showing so it never repeats. */
    void welcome(Player player) {
        if (!MenuRuntime.inSkyblock(player.getWorld())) {
            return;
        }
        MenuData prefs = rt.prefs(player.getUniqueId());
        if (prefs.welcomed) {
            return;
        }
        prefs.welcomed = true;
        rt.dirty(player.getUniqueId());
        player.showTitle(Title.title(
            Msg.component("<gradient:#7b2ff7:#00d4ff><bold>Welcome to Skyblock</bold></gradient>"),
            Msg.component("<gray>Your adventure starts here"),
            Title.Times.times(java.time.Duration.ofMillis(500), java.time.Duration.ofMillis(3500),
                java.time.Duration.ofMillis(1000))));
        rt.core.msg().mini(player, "<gray>Use your <aqua>Skyblock Menu</aqua> (slot 9) or <yellow>/sbmenu</yellow> to see "
            + "everything you can do. Check <yellow>/quests</yellow> for your first goals.");
        rt.playAlways(player, "welcome");
    }
}
