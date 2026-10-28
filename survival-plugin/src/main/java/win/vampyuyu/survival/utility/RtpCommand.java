package win.vampyuyu.survival.utility;

import java.util.Optional;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionDefault;
import win.vampyuyu.survival.core.Cooldowns;
import win.vampyuyu.survival.core.Core;
import win.vampyuyu.survival.core.Msg;
import win.vampyuyu.survival.core.SkyblockWorlds;

/** /rtp: a random safe place in the vanilla survival world only. The cooldown starts when the teleport happens. */
final class RtpCommand {
    private static final String KEY = "rtp";

    private final Core core;
    private final Msg msg;
    private final TeleportService teleports;
    private final Supplier<UtilityConfig> config;
    private final Cooldowns cooldowns;
    private final RtpFinder finder = new RtpFinder(new Random());

    RtpCommand(Core core, TeleportService teleports, Supplier<UtilityConfig> config, Cooldowns cooldowns) {
        this.core = core;
        this.msg = core.msg();
        this.teleports = teleports;
        this.config = config;
        this.cooldowns = cooldowns;
    }

    void register() {
        core.commands().command("rtp").aliases("wild").description("Teleport to a random place in the survival world")
            .usage("/rtp").permission(Perms.RTP, PermissionDefault.TRUE).executor(this::rtp).register();
    }

    /** The configured world, or the first loaded world that is not part of Skyblock. */
    World world() {
        String name = config.get().rtpWorld();
        if (name != null && !name.isBlank()) {
            World configured = Bukkit.getWorld(name.trim());
            return configured != null && !SkyblockWorlds.isSkyblock(configured.getName())
                && configured.getEnvironment() == World.Environment.NORMAL ? configured : null;
        }
        for (World world : Bukkit.getWorlds()) {
            if (!SkyblockWorlds.isSkyblock(world.getName()) && world.getEnvironment() == World.Environment.NORMAL) {
                return world;
            }
        }
        return null;
    }

    private boolean rtp(CommandSender sender, Command command, String label, String[] args) {
        Player player = Cmd.player(msg, sender);
        if (player == null) {
            return true;
        }
        UtilityConfig cfg = config.get();
        if (!cfg.rtpEnabled()) {
            msg.error(player, "Random teleport is disabled.");
            return true;
        }
        World world = world();
        if (world == null) {
            msg.error(player, "There is no survival world to teleport in.");
            return true;
        }
        if (!player.getWorld().equals(world)) {
            msg.error(player, "/rtp only works in the survival world ('" + world.getName() + "'), "
                + "not in " + Cmd.worldName(player.getWorld()) + ".");
            return true;
        }
        boolean bypass = player.hasPermission(Perms.BYPASS_COOLDOWN);
        long wait = bypass ? 0 : cooldowns.remaining(player.getUniqueId(), KEY);
        if (wait > 0) {
            msg.error(player, "/rtp is on cooldown: " + Msg.duration(wait) + " left.");
            return true;
        }
        teleports.start(player, "a random place", p -> search(p, world), () -> {
            if (!bypass) {
                cooldowns.set(player.getUniqueId(), KEY, cfg.rtpCooldownSeconds() * 1000L);
            }
            Location at = player.getLocation();
            msg.ok(player, "Teleported to " + Cmd.coords(at) + ".");
        });
        return true;
    }

    private CompletableFuture<TeleportService.Outcome> search(Player player, World world) {
        UtilityConfig cfg = config.get();
        double cx = cfg.rtpCenterX();
        double cz = cfg.rtpCenterZ();
        if (cfg.rtpUseWorldSpawn()) {
            Location spawn = world.getSpawnLocation();
            cx = spawn.getX();
            cz = spawn.getZ();
        }
        msg.info(player, "Looking for a safe place...");
        CompletableFuture<Optional<Location>> found = finder.find(world, cx, cz, cfg.rtpMinRadius(),
            cfg.rtpMaxRadius(), cfg.rtpAttempts());
        return found.orTimeout(cfg.rtpTimeoutSeconds(), TimeUnit.SECONDS).handle((spot, error) -> {
            if (error != null || spot == null || spot.isEmpty()) {
                return new TeleportService.Outcome.Fail(
                    "Could not find a safe place. Try again, your cooldown was not used.");
            }
            return new TeleportService.Outcome.Go(spot.get());
        });
    }
}
