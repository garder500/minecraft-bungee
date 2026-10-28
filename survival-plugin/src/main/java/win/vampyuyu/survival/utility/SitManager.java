package win.vampyuyu.survival.utility;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDismountEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.permissions.PermissionDefault;
import win.vampyuyu.survival.core.Core;
import win.vampyuyu.survival.core.Msg;
import win.vampyuyu.survival.core.Scheduler;

/**
 * /sit: the player rides an invisible, non persistent {@link Interaction} entity (no armor stand, no packets).
 * The seat is removed when the player sneaks off, teleports, dies, quits, when the block under it disappears, and on plugin disable. Interaction entities never serialise (non persistent),
 * so a crash cannot leave seats behind.
 */
final class SitManager implements Listener {
    private final Core core;
    private final Msg msg;
    private final Supplier<UtilityConfig> config;
    private final TeleportService teleports;
    private final NamespacedKey seatKey;
    private record Seat(Interaction entity, Location origin) {}

    private final Map<UUID, Seat> seats = new ConcurrentHashMap<>();
    private Scheduler.Handle watchdog;

    SitManager(Core core, Supplier<UtilityConfig> config, TeleportService teleports) {
        this.core = core;
        this.msg = core.msg();
        this.config = config;
        this.teleports = teleports;
        this.seatKey = new NamespacedKey(core.getPlugin(), "sit_seat");
    }

    void register() {
        core.listen(this);
        core.commands().command("sit").description("Sit down where you stand (sneak to get up)").usage("/sit")
            .permission(Perms.SIT, PermissionDefault.TRUE).executor(this::sit).register();
        watchdog = core.scheduler().repeating(10, 10, this::check);
    }

    boolean isSitting(UUID player) {
        return seats.containsKey(player);
    }

    private boolean sit(CommandSender sender, Command command, String label, String[] args) {
        Player player = Cmd.player(msg, sender);
        if (player == null) {
            return true;
        }
        if (seats.containsKey(player.getUniqueId())) {
            stand(player);
            msg.info(player, "You stood up.");
            return true;
        }
        String problem = canSit(player);
        if (problem != null) {
            msg.error(player, problem);
            return true;
        }
        UtilityConfig cfg = config.get();
        Location at = player.getLocation();
        Location seatAt = new Location(at.getWorld(), at.getX(), at.getY() + cfg.seatOffset(), at.getZ(),
            at.getYaw(), 0);
        Interaction seat = at.getWorld().spawn(seatAt, Interaction.class, entity -> {
            entity.setPersistent(false);
            entity.setInteractionWidth(0.2f);
            entity.setInteractionHeight((float) cfg.seatHeight());
            entity.setResponsive(false);
            entity.setInvulnerable(true);
            entity.setGravity(false);
            entity.getPersistentDataContainer().set(seatKey, PersistentDataType.BYTE, (byte) 1);
        });
        if (!seat.addPassenger(player)) {
            seat.remove();
            msg.error(player, "You cannot sit here.");
            return true;
        }
        seats.put(player.getUniqueId(), new Seat(seat, at.clone()));
        msg.info(player, "You sit down. Sneak to get up.");
        return true;
    }

    private String canSit(Player player) {
        if (teleports.isTeleporting(player.getUniqueId())) {
            return "You cannot sit while a teleport is pending.";
        }
        if (player.isInsideVehicle()) {
            return "You are already riding something.";
        }
        if (player.isFlying() || player.isGliding() || player.isSwimming() || player.isSleeping()
                || player.isInWater() || player.isClimbing()) {
            return "You cannot sit right now.";
        }
        Location feet = player.getLocation();
        boolean supported = feet.clone().subtract(0, 0.1, 0).getBlock().getType().isSolid();
        if (!supported || Math.abs(player.getVelocity().getY()) > 0.1) {
            return "You need to stand on solid ground to sit.";
        }
        if (SafeSpot.liquid(player.getLocation().getBlock().getType())) {
            return "You cannot sit in a liquid.";
        }
        return null;
    }

    /** Removes the seat of this player and puts them back where they sat down. */
    void stand(Player player) {
        Seat seat = seats.remove(player.getUniqueId());
        if (seat != null) {
            release(seat, player);
        }
    }

    private void release(Seat seat, Player rider) {
        Interaction entity = seat.entity();
        if (rider != null && rider.isOnline()) {
            if (entity.getPassengers().contains(rider)) {
                entity.removePassenger(rider);
            }
            Location origin = seat.origin();
            if (origin.getWorld() == rider.getWorld() && origin.distanceSquared(rider.getLocation()) < 9) {
                rider.teleport(origin, PlayerTeleportEvent.TeleportCause.PLUGIN);
            }
        }
        entity.remove();
    }

    private void check() {
        for (Map.Entry<UUID, Seat> entry : Map.copyOf(seats).entrySet()) {
            Interaction entity = entry.getValue().entity();
            Player rider = org.bukkit.Bukkit.getPlayer(entry.getKey());
            if (rider == null || !rider.isOnline() || !entity.isValid() || !entity.getPassengers().contains(rider)) {
                seats.remove(entry.getKey());
                entity.remove();
                continue;
            }
            Location origin = entry.getValue().origin();
            Block support = origin.getBlock().getRelative(0, -1, 0);
            if (!support.getType().isSolid() || SafeSpot.liquid(origin.getBlock().getType())) {
                stand(rider);
                msg.info(rider, "The ground under you is gone.");
            }
        }
    }

    // ---------------------------------------------------------------- events

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDismount(EntityDismountEvent event) {
        if (!(event.getEntity() instanceof Player player) || event.isCancelled()) {
            return;
        }
        Seat seat = seats.get(player.getUniqueId());
        if (seat != null && seat.entity().equals(event.getDismounted())) {
            seats.remove(player.getUniqueId());
            // Never move entities inside the dismount event: finish a tick later.
            core.scheduler().nextTick(() -> release(seat, player));
        }
    }

    /** Teleported away by something else (for example /home): the seat has no meaning any more. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        Seat seat = seats.remove(event.getPlayer().getUniqueId());
        if (seat != null) {
            seat.entity().remove();
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Seat seat = seats.remove(event.getPlayer().getUniqueId());
        if (seat != null) {
            seat.entity().remove();
        }
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        Seat seat = seats.remove(event.getEntity().getUniqueId());
        if (seat != null) {
            seat.entity().remove();
        }
    }

    /** Removes every seat (plugin disable). */
    void shutdown() {
        if (watchdog != null) {
            watchdog.cancel();
        }
        for (Map.Entry<UUID, Seat> entry : seats.entrySet()) {
            Player rider = org.bukkit.Bukkit.getPlayer(entry.getKey());
            release(entry.getValue(), rider);
        }
        seats.clear();
    }

    /** Seats are marked with a PDC key so a stray one can be recognised. */
    boolean isSeat(Entity entity) {
        return entity.getType() == EntityType.INTERACTION
            && entity.getPersistentDataContainer().has(seatKey, PersistentDataType.BYTE);
    }
}
