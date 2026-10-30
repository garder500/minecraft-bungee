package win.vampyuyu.survival.economy;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionDefault;
import win.vampyuyu.survival.core.Core;

/** /sbnpc: place and manage merchant NPCs. */
final class NpcCommand {
    private final Core core;
    private final NpcManager npcs;
    private final ShopService shops;

    NpcCommand(Core core, NpcManager npcs, ShopService shops) {
        this.core = core;
        this.npcs = npcs;
        this.shops = shops;
    }

    void register() {
        core.commands().command("sbnpc")
            .description("Place and manage Skyblock merchant NPCs")
            .usage("/sbnpc <create <shop> [name]|remove [id]|list|shops|tp <id>|move <id>|rename <id> <name>|shop <id> <shop>|refresh>")
            .permission("vampyuyu.sb.economy.admin", PermissionDefault.OP)
            .executor((s, c, l, a) -> run(s, a))
            .tab((s, c, l, a) -> switch (a.length) {
                case 1 -> Targets.filter(List.of("create", "remove", "list", "shops", "tp", "move", "rename", "shop",
                    "refresh"), a[0]);
                case 2 -> switch (a[0].toLowerCase(Locale.ROOT)) {
                    case "create" -> Targets.filter(new ArrayList<>(shops.catalog().shops().keySet()), a[1]);
                    case "remove", "tp", "move", "rename", "shop" -> Targets.filter(
                        npcs.all().stream().map(r -> r.id).toList(), a[1]);
                    default -> List.of();
                };
                case 3 -> a[0].equalsIgnoreCase("shop")
                    ? Targets.filter(new ArrayList<>(shops.catalog().shops().keySet()), a[2]) : List.of();
                default -> List.of();
            }).register();
    }

    private boolean run(CommandSender sender, String[] args) {
        if (args.length == 0) {
            return false;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "list" -> {
                if (npcs.all().isEmpty()) {
                    core.msg().info(sender, "No merchant NPCs yet. Use /sbnpc create <shop>.");
                }
                for (NpcManager.Npc r : npcs.all()) {
                    core.msg().info(sender, r.id + ": " + r.shop + " at " + r.world + " " + Math.round(r.x) + " "
                        + Math.round(r.y) + " " + Math.round(r.z));
                }
            }
            case "shops" -> core.msg().info(sender, "Merchants: " + String.join(", ", shops.catalog().shops().keySet()));
            case "refresh" -> {
                npcs.refreshAll();
                core.msg().ok(sender, "Merchant NPCs refreshed (" + npcs.liveCount() + " alive).");
            }
            case "create", "remove", "tp", "move", "rename", "shop" -> {
                return withPlayer(sender, sub, args);
            }
            default -> {
                return false;
            }
        }
        return true;
    }

    private boolean withPlayer(CommandSender sender, String sub, String[] args) {
        if (!(sender instanceof Player player)) {
            core.msg().error(sender, "Run this in game.");
            return true;
        }
        switch (sub) {
            case "create" -> {
                if (args.length < 2) {
                    return false;
                }
                var shop = shops.catalog().shop(args[1]);
                if (shop.isEmpty()) {
                    core.msg().error(sender, "Unknown shop '" + args[1] + "'. Try: "
                        + String.join(", ", shops.catalog().shops().keySet()));
                    return true;
                }
                Location at = player.getLocation();
                at.setYaw(at.getYaw() + 180f);
                at.setPitch(0f);
                String name = args.length > 2 ? String.join(" ", java.util.Arrays.copyOfRange(args, 2, args.length)) : "";
                var r = npcs.create(shop.get().id(), name, at);
                core.msg().ok(sender, "Placed " + r.id + " (" + shop.get().name() + "). Remove it with /sbnpc remove " + r.id);
            }
            case "remove" -> {
                String id = args.length > 1 ? args[1] : npcs.nearest(player.getLocation(), 6).map(r -> r.id).orElse(null);
                if (id == null) {
                    core.msg().error(sender, "No merchant within 6 blocks. Use /sbnpc remove <id>.");
                } else if (npcs.remove(id)) {
                    core.msg().ok(sender, "Removed " + id + ".");
                } else {
                    core.msg().error(sender, "Unknown NPC id " + id + ".");
                }
            }
            case "tp" -> {
                if (args.length < 2) {
                    return false;
                }
                var r = npcs.get(args[1]);
                var world = r.map(x -> org.bukkit.Bukkit.getWorld(x.world));
                if (r.isEmpty() || world.isEmpty() || world.get() == null) {
                    core.msg().error(sender, "Unknown NPC or its world is not loaded.");
                } else {
                    player.teleport(new Location(world.get(), r.get().x, r.get().y, r.get().z, r.get().yaw, 0f));
                }
            }
            case "move" -> {
                if (args.length < 2) {
                    return false;
                }
                var r = npcs.get(args[1]);
                if (r.isEmpty()) {
                    core.msg().error(sender, "Unknown NPC id " + args[1] + ".");
                } else {
                    Location at = player.getLocation();
                    at.setYaw(at.getYaw() + 180f);
                    npcs.move(r.get(), at);
                    core.msg().ok(sender, "Moved " + r.get().id + " to you.");
                }
            }
            case "rename" -> {
                if (args.length < 3) {
                    return false;
                }
                var r = npcs.get(args[1]);
                if (r.isEmpty()) {
                    core.msg().error(sender, "Unknown NPC id " + args[1] + ".");
                } else {
                    r.get().name = String.join(" ", java.util.Arrays.copyOfRange(args, 2, args.length));
                    npcs.update(r.get());
                    core.msg().ok(sender, "Renamed " + r.get().id + " (MiniMessage allowed).");
                }
            }
            default -> {
                if (args.length < 3) {
                    return false;
                }
                var r = npcs.get(args[1]);
                var shop = shops.catalog().shop(args[2]);
                if (r.isEmpty() || shop.isEmpty()) {
                    core.msg().error(sender, "Unknown NPC or shop.");
                } else {
                    r.get().shop = shop.get().id();
                    npcs.update(r.get());
                    core.msg().ok(sender, r.get().id + " now runs " + shop.get().name() + ".");
                }
            }
        }
        return true;
    }
}
