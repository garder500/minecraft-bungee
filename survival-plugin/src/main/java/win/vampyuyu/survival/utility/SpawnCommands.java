package win.vampyuyu.survival.utility;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionDefault;
import win.vampyuyu.survival.api.IslandApi;
import win.vampyuyu.survival.core.Core;
import win.vampyuyu.survival.core.Msg;
import win.vampyuyu.survival.core.SkyblockWorlds;

/** /spawn and /setspawn. Inside Skyblock worlds /spawn goes to the hub through {@link IslandApi#teleportHub}. */
final class SpawnCommands {
    private final Core core;
    private final Msg msg;
    private final TeleportService teleports;
    private final Supplier<UtilityConfig> config;

    SpawnCommands(Core core, TeleportService teleports, Supplier<UtilityConfig> config) {
        this.core = core;
        this.msg = core.msg();
        this.teleports = teleports;
        this.config = config;
    }

    void register() {
        core.commands().command("spawn").description("Go to the spawn (your island when you are in Skyblock)")
            .usage("/spawn").permission(Perms.SPAWN, PermissionDefault.TRUE).executor(this::spawn).register();
        core.commands().command("setspawn").description("Set the spawn of the survival world")
            .usage("/setspawn").permission(Perms.ADMIN, PermissionDefault.OP).executor(this::setSpawn).register();
    }

    /** The world /spawn uses outside Skyblock: the configured one, else the first loaded world that is not Skyblock. */
    World spawnWorld() {
        String name = config.get().spawnWorld();
        if (name != null && !name.isBlank()) {
            World configured = Bukkit.getWorld(name.trim());
            if (configured != null && !SkyblockWorlds.isSkyblock(configured.getName())) {
                return configured;
            }
        }
        for (World world : Bukkit.getWorlds()) {
            if (!SkyblockWorlds.isSkyblock(world.getName())) {
                return world;
            }
        }
        return null;
    }

    private boolean spawn(CommandSender sender, Command command, String label, String[] args) {
        Player player = Cmd.player(msg, sender);
        if (player == null) {
            return true;
        }
        boolean inSkyblock = SkyblockWorlds.isSkyblock(player.getWorld().getName());
        if (inSkyblock) {
            teleports.start(player, "your island", this::hub, () -> msg.ok(player, "Welcome to your island."));
        } else {
            teleports.start(player, "spawn", this::vanillaSpawn, () -> msg.ok(player, "Teleported to spawn."));
        }
        return true;
    }

    private CompletableFuture<TeleportService.Outcome> hub(Player player) {
        IslandApi islands = core.service(IslandApi.class).orElse(null);
        if (islands == null) {
            return CompletableFuture.completedFuture(new TeleportService.Outcome.Fail(
                "Your island is not available right now."));
        }
        boolean moved = islands.teleportHub(player);
        return CompletableFuture.completedFuture(moved ? new TeleportService.Outcome.Handled()
            : new TeleportService.Outcome.Fail("Your island is not available right now."));
    }

    private CompletableFuture<TeleportService.Outcome> vanillaSpawn(Player player) {
        World world = spawnWorld();
        if (world == null) {
            return CompletableFuture.completedFuture(new TeleportService.Outcome.Fail("There is no spawn to go to."));
        }
        Location spawn = world.getSpawnLocation();
        Location target = new Location(world, spawn.getBlockX() + 0.5, spawn.getY(), spawn.getBlockZ() + 0.5,
            spawn.getYaw(), spawn.getPitch());
        CompletableFuture<TeleportService.Outcome> result = new CompletableFuture<>();
        Platform.loadChunk(world, target.getBlockX(), target.getBlockZ()).whenComplete((chunk, error) -> core.scheduler().sync(() -> {
            if (error != null) {
                result.complete(new TeleportService.Outcome.Fail("The spawn could not be loaded."));
                return;
            }
            Location safe = target;
            if (!SafeSpot.isSafe(target)) {
                int y = world.getHighestBlockYAt(target) + 1;
                safe = new Location(world, target.getX(), y, target.getZ(), target.getYaw(), target.getPitch());
            }
            result.complete(new TeleportService.Outcome.Go(safe));
        }));
        return result;
    }

    private boolean setSpawn(CommandSender sender, Command command, String label, String[] args) {
        Player player = Cmd.player(msg, sender);
        if (player == null) {
            return true;
        }
        World world = player.getWorld();
        if (SkyblockWorlds.isSkyblock(world.getName())) {
            msg.error(player, "Skyblock worlds have their own spawn (the hub is managed by the island feature).");
            return true;
        }
        World spawnWorld = spawnWorld();
        if (spawnWorld != null && !spawnWorld.equals(world)) {
            msg.error(player, "/spawn uses the world '" + spawnWorld.getName() + "'. Run /setspawn there, "
                + "or change spawn.world in utility.yml.");
            return true;
        }
        Location here = player.getLocation();
        if (world.setSpawnLocation(here)) {
            msg.ok(player, "Spawn of '" + world.getName() + "' set to " + Cmd.coords(here) + ".");
        } else {
            msg.error(player, "Could not set the spawn here.");
        }
        return true;
    }

}
