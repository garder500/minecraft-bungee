package win.vampyuyu.survival.utility;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import win.vampyuyu.survival.api.DungeonApi;
import win.vampyuyu.survival.back.TeleportGate;
import win.vampyuyu.survival.core.Core;
import win.vampyuyu.survival.core.Msg;
import win.vampyuyu.survival.core.Scheduler;
import win.vampyuyu.survival.core.SkyblockWorlds;

/**
 * The single door every teleport of the utility feature (and /back) goes through: refusal inside dungeons and in
 * combat, a warm-up that is cancelled by moving or taking damage, one teleport at a time per player, and the combat
 * tag itself. All state is touched on the main thread.
 */
public final class TeleportService implements TeleportGate, Listener {
    /** What a {@link Destination} produced. */
    public sealed interface Outcome {
        /** Teleport the player here. */
        record Go(Location location) implements Outcome {}

        /** The destination already moved the player itself (for example the hub teleport of the island feature). */
        record Handled() implements Outcome {}

        /** Nothing to do, tell the player why. */
        record Fail(String reason) implements Outcome {}
    }

    /** Resolves where the player goes, called on the main thread once the warm-up is over. */
    @FunctionalInterface
    public interface Destination {
        CompletableFuture<Outcome> resolve(Player player);
    }

    private static final long TICK_STEP = 5;

    private final class Session {
        final Player player;
        final Location start;
        final String label;
        final Destination destination;
        final Runnable onArrive;
        final long warmupTicks;
        long elapsed;
        boolean resolving;
        Scheduler.Handle task;

        Session(Player player, String label, Destination destination, Runnable onArrive, long warmupTicks) {
            this.player = player;
            this.start = player.getLocation().clone();
            this.label = label;
            this.destination = destination;
            this.onArrive = onArrive;
            this.warmupTicks = warmupTicks;
        }
    }

    private final Core core;
    private final Msg msg;
    private final Supplier<UtilityConfig> config;
    private final CombatTag combat;
    private final Map<UUID, Session> sessions = new ConcurrentHashMap<>();

    public TeleportService(Core core, Supplier<UtilityConfig> config, CombatTag combat) {
        this.core = core;
        this.msg = core.msg();
        this.config = config;
        this.combat = combat;
    }

    public CombatTag combat() {
        return combat;
    }

    /** True while the player is in a warm-up or waiting for a destination. */
    public boolean isTeleporting(UUID player) {
        return sessions.containsKey(player);
    }

    /** True inside a dungeon run (through {@link DungeonApi} when present, and by world name). */
    public boolean inDungeon(Player player) {
        if (player.getWorld() != null && SkyblockWorlds.isDungeon(player.getWorld().getName())) {
            return true;
        }
        try {
            return core.service(DungeonApi.class).map(api -> api.inDungeon(player)).orElse(false);
        } catch (RuntimeException e) {
            core.debug("DungeonApi.inDungeon failed: " + e);
            return false;
        }
    }

    // ---------------------------------------------------------------- TeleportGate

    @Override
    public String denial(Player player, Location destination) {
        if (inDungeon(player)) {
            return "Teleporting is disabled inside dungeons.";
        }
        if (destination != null && destination.getWorld() != null
                && SkyblockWorlds.isDungeon(destination.getWorld().getName())) {
            return "You cannot teleport into a dungeon.";
        }
        UtilityConfig cfg = config.get();
        if (cfg.combatEnabled() && !player.hasPermission(Perms.BYPASS_COMBAT)) {
            long left = combat.remaining(player.getUniqueId());
            if (left > 0) {
                return "You are in combat. You can teleport in " + Msg.duration(left) + ".";
            }
        }
        return null;
    }

    @Override
    public void teleport(Player player, String label, Location destination, Runnable onArrive) {
        Location target = destination.clone();
        start(player, label, p -> CompletableFuture.completedFuture(new Outcome.Go(target)), onArrive);
    }

    // ---------------------------------------------------------------- sessions

    /**
     * Starts a teleport with warm-up. Returns false (after telling the player) when it was refused.
     * {@code onArrive} runs on the main thread after the player really got there.
     */
    public boolean start(Player player, String label, Destination destination, Runnable onArrive) {
        UUID id = player.getUniqueId();
        if (sessions.containsKey(id)) {
            msg.error(player, "You are already teleporting.");
            return false;
        }
        String denial = denial(player, null);
        if (denial != null) {
            msg.error(player, denial);
            return false;
        }
        UtilityConfig cfg = config.get();
        long warmup = player.hasPermission(Perms.BYPASS_WARMUP) ? 0 : cfg.warmupSeconds() * 20L;
        Session session = new Session(player, label, destination, onArrive, warmup);
        sessions.put(id, session);
        if (warmup <= 0) {
            resolve(session);
            return true;
        }
        msg.info(player, "Teleporting to " + label + " in " + cfg.warmupSeconds()
            + " seconds. Do not move" + (cfg.cancelOnDamage() ? " or take damage" : "") + ".");
        showCountdown(session);
        session.task = core.scheduler().repeating(TICK_STEP, TICK_STEP, () -> tick(session));
        return true;
    }

    private void tick(Session session) {
        Player player = session.player;
        if (sessions.get(player.getUniqueId()) != session || session.resolving) {
            return;
        }
        if (!player.isOnline() || !player.isValid()) {
            end(session);
            return;
        }
        UtilityConfig cfg = config.get();
        Location now = player.getLocation();
        if (now.getWorld() != session.start.getWorld() || horizontalSquared(now, session.start)
                > cfg.moveTolerance() * cfg.moveTolerance() || Math.abs(now.getY() - session.start.getY()) > 1.5) {
            cancelWith(session, "Teleport cancelled: you moved.");
            return;
        }
        String denial = denial(player, null);
        if (denial != null) {
            cancelWith(session, denial);
            return;
        }
        session.elapsed += TICK_STEP;
        if (session.elapsed >= session.warmupTicks) {
            if (session.task != null) {
                session.task.cancel();
            }
            resolve(session);
        } else if (session.elapsed % 20 == 0) {
            showCountdown(session);
        }
    }

    private static double horizontalSquared(Location a, Location b) {
        double dx = a.getX() - b.getX();
        double dz = a.getZ() - b.getZ();
        return dx * dx + dz * dz;
    }

    private void showCountdown(Session session) {
        UtilityConfig cfg = config.get();
        if (!cfg.showTitle() || !session.player.isOnline()) {
            return;
        }
        long left = Math.max(1, (session.warmupTicks - session.elapsed + 19) / 20);
        session.player.showTitle(Title.title(
            Component.text("Teleporting...", NamedTextColor.AQUA),
            Component.text(left + "s, stand still", NamedTextColor.GRAY),
            Title.Times.times(Duration.ZERO, Duration.ofMillis(1200), Duration.ofMillis(200))));
    }

    private void resolve(Session session) {
        session.resolving = true;
        Player player = session.player;
        CompletableFuture<Outcome> future;
        try {
            future = session.destination.resolve(player);
        } catch (RuntimeException e) {
            core.getPlugin().getLogger().log(java.util.logging.Level.WARNING, "Teleport destination failed", e);
            future = CompletableFuture.completedFuture(new Outcome.Fail("Could not find a place to teleport to."));
        }
        future.orTimeout(60, TimeUnit.SECONDS).whenComplete((outcome, error) -> core.scheduler().sync(() -> {
            if (error != null) {
                core.debug("Teleport to " + session.label + " failed: " + error);
                complete(session, new Outcome.Fail("Could not find a place to teleport to."));
            } else {
                complete(session, outcome);
            }
        }));
    }

    private void complete(Session session, Outcome outcome) {
        Player player = session.player;
        // Quit, death, disable or an explicit cancel removed the session: nothing left to do.
        if (sessions.get(player.getUniqueId()) != session || !player.isOnline()) {
            sessions.remove(player.getUniqueId(), session);
            return;
        }
        switch (outcome) {
            case Outcome.Fail fail -> {
                end(session);
                msg.error(player, fail.reason());
            }
            case Outcome.Handled handled -> {
                end(session);
                session.onArrive.run();
            }
            case Outcome.Go go -> {
                Location target = go.location();
                String denial = target.getWorld() == null ? "That place no longer exists." : denial(player, target);
                if (denial != null) {
                    end(session);
                    msg.error(player, denial);
                    return;
                }
                Platform.teleport(player, target).whenComplete((done, error) -> core.scheduler().sync(() -> {
                    sessions.remove(player.getUniqueId(), session);
                    if (!player.isOnline()) {
                        return;
                    }
                    if (error == null && Boolean.TRUE.equals(done)) {
                        session.onArrive.run();
                    } else {
                        msg.error(player, "The teleport failed.");
                    }
                }));
            }
        }
    }

    private void cancelWith(Session session, String reason) {
        end(session);
        if (session.player.isOnline()) {
            session.player.clearTitle();
            msg.warn(session.player, reason);
        }
    }

    private void end(Session session) {
        if (session.task != null) {
            session.task.cancel();
        }
        sessions.remove(session.player.getUniqueId(), session);
    }

    /** Cancels a running warm-up (reason is shown when not null). Returns true when there was one. */
    public boolean cancel(UUID player, String reason) {
        Session session = sessions.get(player);
        if (session == null) {
            return false;
        }
        end(session);
        if (reason != null && session.player.isOnline()) {
            session.player.clearTitle();
            msg.warn(session.player, reason);
        }
        return true;
    }

    /** Drops every session (plugin disable). */
    public void shutdown() {
        for (Session session : sessions.values()) {
            if (session.task != null) {
                session.task.cancel();
            }
        }
        sessions.clear();
    }

    // ---------------------------------------------------------------- events

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player) || event.getFinalDamage() <= 0) {
            return;
        }
        Session session = sessions.get(player.getUniqueId());
        if (session != null && !session.resolving && config.get().cancelOnDamage()) {
            cancelWith(session, "Teleport cancelled: you took damage.");
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent event) {
        UtilityConfig cfg = config.get();
        if (!cfg.combatEnabled() || cfg.combatSeconds() <= 0 || event.getFinalDamage() <= 0
                || !(event.getEntity() instanceof Player victim) || isNpc(victim)) {
            return;
        }
        Entity damager = event.getDamager();
        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Entity shooter) {
            damager = shooter;
        }
        if (damager.equals(victim)) {
            return;
        }
        long millis = cfg.combatSeconds() * 1000L;
        if (damager instanceof Player attacker) {
            if (!isNpc(attacker)) {
                combat.tag(attacker.getUniqueId(), millis);
                combat.tag(victim.getUniqueId(), millis);
            }
        } else if (damager instanceof LivingEntity && cfg.combatIncludeMobs()) {
            combat.tag(victim.getUniqueId(), millis);
        }
    }

    private static boolean isNpc(Player player) {
        return player.hasMetadata("NPC");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        UUID id = event.getEntity().getUniqueId();
        combat.clear(id);
        cancel(id, null);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        cancel(id, null);
        combat.clear(id);
    }
}
