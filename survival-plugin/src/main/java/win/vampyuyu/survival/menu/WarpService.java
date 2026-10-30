package win.vampyuyu.survival.menu;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import win.vampyuyu.survival.api.IslandApi;
import win.vampyuyu.survival.core.Cooldowns;
import win.vampyuyu.survival.core.Msg;
import win.vampyuyu.survival.core.Scheduler;
import win.vampyuyu.survival.core.SkyblockWorlds;

/**
 * Hub navigation (public island warps and visits belong to the island feature: {@code /island warp}, {@code /island visit}). Every warp runs through the same checks: the player must be in a Skyblock world (a warp is never a way
 * out of a plain survival world, into a dungeon run or out of one), a cooldown applies, and a warm-up (standing still)
 * protects against using warps as an escape in a fight. There is no combat tag API; taking damage during the warm-up
 * cancels it, which gives the same protection.
 */
final class WarpService {
    /** Where a warp goes. */
    enum Target { HUB, ISLAND, NETHER, END }

    private record Pending(Scheduler.Handle task, Location start, double health) {}

    private final MenuRuntime rt;
    private final Cooldowns cooldowns = new Cooldowns();
    private final Map<UUID, Pending> pending = new HashMap<>();

    WarpService(MenuRuntime rt) {
        this.rt = rt;
    }

    /** Why the player cannot warp right now, empty when they can. */
    Optional<String> blocked(Player player) {
        if (!MenuRuntime.inSkyblock(player.getWorld())) {
            return Optional.of("Warps only work inside the Skyblock worlds.");
        }
        if (SkyblockWorlds.isDungeon(player.getWorld().getName()) || rt.inDungeon(player)) {
            return Optional.of("You cannot warp out of a dungeon run.");
        }
        if (player.isDead()) {
            return Optional.of("You cannot warp while dead.");
        }
        long left = cooldowns.remaining(player.getUniqueId(), "warp");
        if (left > 0) {
            return Optional.of("Warp is on cooldown for " + Msg.duration(left) + ".");
        }
        return Optional.empty();
    }

    void warp(Player player, Target target) {
        Optional<String> block = blocked(player);
        if (block.isPresent()) {
            rt.core.msg().error(player, block.get());
            return;
        }
        switch (target) {
            case HUB -> start(player, "the hub", () -> rt.islands().map(api -> api.teleportHub(player)).orElse(false));
            case ISLAND -> {
                if (rt.islands().flatMap(api -> api.islandOf(player.getUniqueId())).isEmpty()) {
                    rt.core.msg().error(player, "You do not have an island yet.");
                    return;
                }
                start(player, "your island", () -> rt.islands().map(api -> api.teleportHome(player)).orElse(false));
            }
            case NETHER -> world(player, SkyblockWorlds.NETHER, rt.config().netherWarp, "the Nether");
            case END -> world(player, SkyblockWorlds.END, rt.config().endWarp, "the End");
        }
    }

    private void world(Player player, String worldName, org.bukkit.configuration.ConfigurationSection section,
            String label) {
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            rt.core.msg().error(player, "The destination is not available right now.");
            return;
        }
        start(player, label, () -> {
            Location destination = MenuConfig.warpLocation(section, world);
            return teleport(player, destination);
        });
    }

    private boolean teleport(Player player, Location destination) {
        try {
            player.teleportAsync(destination);
        } catch (UnsupportedOperationException e) {
            return player.teleport(destination);
        }
        return true;
    }

    private void start(Player player, String label, BooleanSupplier action) {
        UUID id = player.getUniqueId();
        cancel(id, null);
        int warmup = rt.config().warpWarmupSeconds;
        if (warmup <= 0) {
            complete(player, label, action);
            return;
        }
        rt.core.msg().info(player, "Warping to " + label + " in " + warmup + "s. Do not move!");
        Location start = player.getLocation().clone();
        double health = player.getHealth();
        int[] ticks = {0};
        Scheduler.Handle[] handle = new Scheduler.Handle[1];
        handle[0] = rt.core.scheduler().repeating(5, 5, () -> {
            ticks[0] += 5;
            if (!player.isOnline() || player.isDead()) {
                cancel(id, null);
                return;
            }
            Location now = player.getLocation();
            boolean moved = now.getWorld() != start.getWorld() || now.distanceSquared(start) > 0.6 * 0.6;
            if (moved || player.getHealth() < health - 0.01) {
                cancel(id, player);
                rt.core.msg().warn(player, moved ? "Warp cancelled: you moved." : "Warp cancelled: you took damage.");
                return;
            }
            if (ticks[0] >= warmup * 20) {
                cancel(id, null);
                complete(player, label, action);
            }
        });
        pending.put(id, new Pending(handle[0], start, health));
    }

    private void complete(Player player, String label, BooleanSupplier action) {
        Optional<String> block = blocked(player);
        if (block.isPresent()) {
            rt.core.msg().error(player, block.get());
            return;
        }
        boolean ok;
        try {
            ok = action.getAsBoolean();
        } catch (RuntimeException e) {
            rt.core.getPlugin().getLogger().warning("Warp failed for " + player.getName() + ": " + e);
            ok = false;
        }
        if (ok) {
            cooldowns.set(player.getUniqueId(), "warp", rt.config().warpCooldownSeconds * 1000L);
            rt.core.msg().ok(player, "Warped to " + label + ".");
            rt.play(player, "teleport");
        }
    }

    private void cancel(UUID id, Player notify) {
        Pending existing = pending.remove(id);
        if (existing != null) {
            existing.task().cancel();
        }
    }

    /** Drops a player's pending warp (quit). */
    void forget(UUID id) {
        cancel(id, null);
        cooldowns.clear(id, "warp");
    }

    void shutdown() {
        pending.values().forEach(p -> p.task().cancel());
        pending.clear();
    }

    boolean isWarping(UUID id) {
        return pending.containsKey(id);
    }
}
