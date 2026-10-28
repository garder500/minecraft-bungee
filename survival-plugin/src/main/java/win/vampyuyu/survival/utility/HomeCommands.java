package win.vampyuyu.survival.utility;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionAttachmentInfo;
import org.bukkit.permissions.PermissionDefault;
import win.vampyuyu.survival.api.IslandApi;
import win.vampyuyu.survival.core.Core;
import win.vampyuyu.survival.core.Gui;
import win.vampyuyu.survival.core.Guis;
import win.vampyuyu.survival.core.Msg;
import win.vampyuyu.survival.core.SkyblockWorlds;

/** /home, /sethome, /delhome and the /homes menu. Homes live in the profile section {@code utility}. */
final class HomeCommands {
    private static final Material[] BEDS = {
        Material.RED_BED, Material.ORANGE_BED, Material.YELLOW_BED, Material.LIME_BED, Material.GREEN_BED,
        Material.CYAN_BED, Material.LIGHT_BLUE_BED, Material.BLUE_BED, Material.PURPLE_BED, Material.MAGENTA_BED,
        Material.PINK_BED, Material.WHITE_BED, Material.LIGHT_GRAY_BED, Material.GRAY_BED, Material.BROWN_BED,
        Material.BLACK_BED};

    private final Core core;
    private final Msg msg;
    private final TeleportService teleports;
    private final Supplier<UtilityConfig> config;

    HomeCommands(Core core, TeleportService teleports, Supplier<UtilityConfig> config) {
        this.core = core;
        this.msg = core.msg();
        this.teleports = teleports;
        this.config = config;
    }

    void register() {
        core.permissions().register("vampyuyu.sb.utility.homes.unlimited",
            "Home limit set to the configured maximum", PermissionDefault.OP);
        core.commands().command("home").description("Teleport to one of your homes")
            .usage("/home [name]").permission(Perms.HOME, PermissionDefault.TRUE)
            .executor(this::home).tab((s, c, l, a) -> names(s, a, 0)).register();
        core.commands().command("sethome").description("Save your current position as a home")
            .usage("/sethome [name]").permission(Perms.HOME, PermissionDefault.TRUE)
            .executor(this::setHome).tab((s, c, l, a) -> names(s, a, 0)).register();
        core.commands().command("delhome").aliases("removehome").description("Delete one of your homes")
            .usage("/delhome <name>").permission(Perms.HOME, PermissionDefault.TRUE)
            .executor(this::delHome).tab((s, c, l, a) -> names(s, a, 0)).register();
        core.commands().command("homes").description("List your homes in a menu")
            .usage("/homes").permission(Perms.HOME, PermissionDefault.TRUE)
            .executor(this::homes).register();
    }

    private UtilityData data(UUID player) {
        return core.profiles().section(player, "utility", UtilityData.class, UtilityData::new);
    }

    private List<String> names(CommandSender sender, String[] args, int index) {
        if (!(sender instanceof Player player) || args.length != index + 1) {
            return List.of();
        }
        return Cmd.match(data(player.getUniqueId()).homes.keySet(), args[index]);
    }

    /** Number of homes this player may own. */
    int limit(Player player) {
        UtilityConfig cfg = config.get();
        List<String> granted = new ArrayList<>();
        for (PermissionAttachmentInfo info : player.getEffectivePermissions()) {
            if (info.getValue() && info.getPermission().startsWith(HomeBook.LIMIT_PREFIX)) {
                granted.add(info.getPermission());
            }
        }
        return HomeBook.limit(granted, cfg.homesDefault(), cfg.homesMax());
    }

    // ---------------------------------------------------------------- /sethome

    private boolean setHome(CommandSender sender, Command command, String label, String[] args) {
        Player player = Cmd.player(msg, sender);
        if (player == null) {
            return true;
        }
        UtilityConfig cfg = config.get();
        String name = args.length == 0 ? cfg.defaultHomeName() : HomeBook.normalise(args[0]);
        String problem = HomeBook.validate(name, cfg.homeNameLength());
        if (problem != null) {
            msg.error(player, problem);
            return true;
        }
        if (args.length > 1) {
            msg.error(player, "A home name is a single word. Usage: /sethome [name]");
            return true;
        }
        Location here = player.getLocation();
        String place = placeDenial(player, here, true);
        if (place != null) {
            msg.error(player, place);
            return true;
        }
        if (!SafeSpot.isSafe(here)) {
            msg.error(player, "This spot is not safe to come back to (no ground, lava, fire or a blocked space).");
            return true;
        }
        UtilityData data = data(player.getUniqueId());
        boolean replacing = data.homes.containsKey(name);
        int limit = limit(player);
        if (!replacing && data.homes.size() >= limit) {
            msg.error(player, "You can only have " + limit + " home" + (limit == 1 ? "" : "s")
                + ". Delete one with /delhome <name> or replace an existing home.");
            return true;
        }
        data.homes.put(name, new UtilityData.Home(here.getWorld().getName(), here.getX(), here.getY(), here.getZ(),
            here.getYaw(), here.getPitch(), System.currentTimeMillis()));
        core.profiles().markDirty(player.getUniqueId());
        msg.ok(player, (replacing ? "Home '" + name + "' moved here" : "Home '" + name + "' set")
            + " (" + data.homes.size() + "/" + limit + ").");
        return true;
    }

    // ---------------------------------------------------------------- /delhome

    private boolean delHome(CommandSender sender, Command command, String label, String[] args) {
        Player player = Cmd.player(msg, sender);
        if (player == null) {
            return true;
        }
        if (args.length != 1) {
            msg.info(player, "Usage: /delhome <name>. Your homes: " + listOf(data(player.getUniqueId())));
            return true;
        }
        String name = HomeBook.normalise(args[0]);
        UtilityData data = data(player.getUniqueId());
        if (name == null || data.homes.remove(name) == null) {
            msg.error(player, "You have no home called '" + args[0] + "'. Your homes: " + listOf(data));
            return true;
        }
        core.profiles().markDirty(player.getUniqueId());
        msg.ok(player, "Home '" + name + "' deleted.");
        return true;
    }

    private static String listOf(UtilityData data) {
        return data.homes.isEmpty() ? "none" : String.join(", ", data.homes.keySet());
    }

    // ---------------------------------------------------------------- /home

    private boolean home(CommandSender sender, Command command, String label, String[] args) {
        Player player = Cmd.player(msg, sender);
        if (player == null) {
            return true;
        }
        UtilityData data = data(player.getUniqueId());
        if (data.homes.isEmpty()) {
            msg.info(player, "You have no homes yet. Stand somewhere safe and use /sethome [name].");
            return true;
        }
        String name;
        if (args.length == 0) {
            String fallback = config.get().defaultHomeName();
            if (data.homes.containsKey(fallback)) {
                name = fallback;
            } else if (data.homes.size() == 1) {
                name = data.homes.keySet().iterator().next();
            } else {
                msg.info(player, "Which home? Use /home <name>. Your homes: " + listOf(data));
                return true;
            }
        } else {
            name = HomeBook.normalise(args[0]);
        }
        if (name == null || !data.homes.containsKey(name)) {
            msg.error(player, "You have no home called '" + (args.length > 0 ? args[0] : name) + "'. Your homes: "
                + listOf(data));
            return true;
        }
        teleportHome(player, name);
        return true;
    }

    /** Starts the warm-up and teleport to the named home; checks are repeated when the warm-up is over. */
    void teleportHome(Player player, String name) {
        UtilityData.Home home = data(player.getUniqueId()).homes.get(name);
        if (home == null) {
            msg.error(player, "That home no longer exists.");
            return;
        }
        teleports.start(player, "home '" + name + "'", p -> resolve(p, name), () ->
            msg.ok(player, "Welcome home."));
    }

    private CompletableFuture<TeleportService.Outcome> resolve(Player player, String name) {
        UtilityData.Home home = data(player.getUniqueId()).homes.get(name);
        if (home == null) {
            return fail("That home no longer exists.");
        }
        World world = Bukkit.getWorld(home.world);
        if (world == null) {
            return fail("The world of that home is not available right now.");
        }
        Location target = new Location(world, home.x, home.y, home.z, home.yaw, home.pitch);
        String place = placeDenial(player, target, false);
        if (place != null) {
            return fail(place);
        }
        CompletableFuture<TeleportService.Outcome> result = new CompletableFuture<>();
        Platform.loadChunk(world, target.getBlockX(), target.getBlockZ()).whenComplete((chunk, error) -> core.scheduler().sync(() -> {
            if (error != null || chunk == null) {
                result.complete(new TeleportService.Outcome.Fail("That home could not be loaded."));
            } else if (!SafeSpot.isSafe(target)) {
                result.complete(new TeleportService.Outcome.Fail(
                    "Your home '" + name + "' is no longer safe (blocked, no ground, lava or fire). "
                        + "Fix it, or replace it with /sethome " + name + "."));
            } else {
                result.complete(new TeleportService.Outcome.Go(target));
            }
        }));
        return result;
    }

    private static CompletableFuture<TeleportService.Outcome> fail(String reason) {
        return CompletableFuture.completedFuture(new TeleportService.Outcome.Fail(reason));
    }

    /**
     * Rules for where a home may be: never in a dungeon or the hub; in other Skyblock worlds only where the
     * player can build (their island or one they are a member of), checked again at every teleport.
     */
    String placeDenial(Player player, Location location, boolean setting) {
        World world = location.getWorld();
        if (world == null) {
            return "That world is not available.";
        }
        String name = world.getName();
        if (SkyblockWorlds.isDungeon(name)) {
            return "Homes cannot be used inside dungeons.";
        }
        if (SkyblockWorlds.HUB.equals(name)) {
            return "The hub has no homes. Use /spawn to go there.";
        }
        if (SkyblockWorlds.isSkyblock(name)) {
            IslandApi islands = core.service(IslandApi.class).orElse(null);
            if (islands == null) {
                return "Island data is not available right now.";
            }
            if (!islands.canBuild(player, location)) {
                return setting ? "You can only set homes on your own island or one you are a member of."
                    : "You can no longer build there, so that home cannot be used.";
            }
        }
        return null;
    }

    // ---------------------------------------------------------------- /homes

    private boolean homes(CommandSender sender, Command command, String label, String[] args) {
        Player player = Cmd.player(msg, sender);
        if (player == null) {
            return true;
        }
        openMenu(player);
        return true;
    }

    void openMenu(Player player) {
        UtilityData data = data(player.getUniqueId());
        int limit = limit(player);
        Gui gui = core.guis().create(6, "<dark_gray>Your homes");
        gui.border(Guis.icon(Material.GRAY_STAINED_GLASS_PANE, " "));
        List<String> names = new ArrayList<>(data.homes.keySet());
        gui.set(49, Guis.icon(Material.BOOK, "<gold>Homes: <white>" + names.size() + "/" + limit,
            "<gray>/sethome [name] saves a new home",
            "<gray>/delhome <name> removes one"));
        if (names.isEmpty()) {
            gui.set(22, Guis.icon(Material.RED_BED, "<yellow>No homes yet",
                "<gray>Stand somewhere safe and use <white>/sethome"));
        } else {
            gui.paginate(names, gui.innerSlots(), name -> icon(data, names.indexOf(name), name),
                (click, name) -> onClick(click, name), 48, 50);
        }
        gui.open(player);
    }

    private org.bukkit.inventory.ItemStack icon(UtilityData data, int index, String name) {
        UtilityData.Home home = data.homes.get(name);
        Material bed = BEDS[Math.floorMod(index, BEDS.length)];
        if (home == null) {
            return Guis.icon(bed, "<white>" + name);
        }
        return Guis.icon(bed, "<green>" + name,
            "<gray>World: <white>" + Cmd.worldName(home.world),
            "<gray>Position: <white>" + (int) Math.floor(home.x) + ", " + (int) Math.floor(home.y) + ", "
                + (int) Math.floor(home.z),
            "",
            "<yellow>Click to teleport",
            "<red>Shift + right click to delete");
    }

    private void onClick(Gui.Click click, String name) {
        Player player = click.player();
        if (click.shift() && click.right()) {
            UtilityData data = data(player.getUniqueId());
            if (data.homes.remove(name) != null) {
                core.profiles().markDirty(player.getUniqueId());
                msg.ok(player, "Home '" + name + "' deleted.");
            }
            core.scheduler().nextTick(() -> {
                if (player.isOnline()) {
                    openMenu(player);
                }
            });
            return;
        }
        // Close our own menu next tick (never inside the click event), then start the teleport.
        core.scheduler().nextTick(() -> {
            org.bukkit.inventory.Inventory top = player.isOnline() ? player.getOpenInventory().getTopInventory() : null;
            if (top != null && top.getHolder(false) instanceof Gui) {
                player.closeInventory();
            }
        });
        teleportHome(player, name);
    }
}
