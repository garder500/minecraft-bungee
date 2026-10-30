package win.vampyuyu.survival.boss;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Supplier;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import win.vampyuyu.survival.core.Core;
import win.vampyuyu.survival.core.Msg;

/** {@code /boss list|info} for everybody, {@code spawn|kill|active|reload} for admins. */
public final class BossCommand implements CommandExecutor, TabCompleter {
    private final Core core;
    private final BossManager manager;
    private final Supplier<String> reload;

    /** @param reload re-reads boss.yml, returns a one line report */
    public BossCommand(Core core, BossManager manager, Supplier<String> reload) {
        this.core = core;
        this.manager = manager;
        this.reload = reload;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String sub = args.length == 0 ? "help" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "list" -> list(sender);
            case "info" -> info(sender, args);
            case "claim" -> claim(sender);
            case "spawn" -> {
                if (admin(sender)) {
                    spawn(sender, args);
                }
            }
            case "kill" -> {
                if (admin(sender)) {
                    kill(sender, args);
                }
            }
            case "active" -> {
                if (admin(sender)) {
                    active(sender);
                }
            }
            case "reload" -> {
                if (admin(sender)) {
                    core.msg().ok(sender, reload.get());
                }
            }
            default -> help(sender);
        }
        return true;
    }

    private boolean admin(CommandSender sender) {
        if (sender.hasPermission(BossListener.ADMIN)) {
            return true;
        }
        core.msg().error(sender, "You do not have permission.");
        return false;
    }

    private void help(CommandSender sender) {
        core.msg().info(sender, "Boss commands:");
        core.msg().info(sender, "/boss list - every boss");
        core.msg().info(sender, "/boss info <id> - health, abilities and drops");
        core.msg().info(sender, "/boss claim - collect boss loot that did not fit in your inventory");
        if (sender.hasPermission(BossListener.ADMIN)) {
            core.msg().info(sender, "/boss spawn <id> [x y z [world]] - spawn at you or at coordinates");
            core.msg().info(sender, "/boss kill [id|all] - remove bosses, no rewards");
            core.msg().info(sender, "/boss active - running fights");
            core.msg().info(sender, "/boss reload - re-read boss.yml");
        }
    }

    private void claim(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            core.msg().error(sender, "Only players can claim loot.");
            return;
        }
        manager.rewards().applyPending(player);
        int before = manager.delivery().pendingCount(player.getUniqueId());
        if (before == 0) {
            core.msg().info(player, "You have no boss loot waiting.");
            return;
        }
        int delivered = manager.delivery().claimPending(player);
        int left = manager.delivery().pendingCount(player.getUniqueId());
        if (delivered > 0) {
            core.msg().ok(player, "Delivered " + delivered + (delivered == 1 ? " item." : " items.")
                + (left > 0 ? " " + left + " still waiting, make more room." : ""));
        } else {
            core.msg().warn(player, "Your inventory is full. " + left + " item" + (left == 1 ? " is" : "s are") + " waiting.");
        }
    }

    private void list(CommandSender sender) {
        core.msg().info(sender, "Bosses (" + manager.config().bosses().size() + "):");
        for (BossDefinition def : manager.config().bosses().values()) {
            core.msg().mini(sender, " <gray>- <white>" + def.id() + " <dark_gray>" + def.kind().name().toLowerCase(Locale.ROOT)
                + " <gray>" + def.name() + " <red>" + Msg.number(def.health() * manager.settings().hpScale()) + " HP"
                + (manager.activeCount(def.id()) > 0 ? " <green>(" + manager.activeCount(def.id()) + " active)" : ""));
        }
        core.msg().info(sender, "Slayer bosses are started with /slayer.");
    }

    private void info(CommandSender sender, String[] args) {
        if (args.length < 2) {
            core.msg().info(sender, "Usage: /boss info <id>");
            return;
        }
        Optional<BossDefinition> found = manager.config().definition(args[1].toLowerCase(Locale.ROOT));
        if (found.isEmpty()) {
            core.msg().error(sender, "Unknown boss '" + args[1] + "'. Try /boss list.");
            return;
        }
        BossDefinition def = found.get();
        core.msg().mini(sender, def.name() + " <gray>(" + def.id() + ")");
        if (!def.description().isEmpty()) {
            core.msg().mini(sender, "<gray>" + MiniEsc.esc(def.description()));
        }
        core.msg().mini(sender, "<gray>Health: <red>" + Msg.number(def.health() * manager.settings().hpScale())
            + " <gray>Damage: <red>" + Msg.number(def.damage()) + " <gray>Defense: <green>" + Msg.number(def.armor()));
        BossDefinition.Arena a = def.arena();
        core.msg().mini(sender, "<gray>Arena: radius " + (int) a.radius() + (a.noFly() ? ", no flying" : "")
            + (a.noBuild() ? ", no building" : "") + (a.noTeleport() ? ", no pearls" : "")
            + (a.enrageSeconds() > 0 ? ", enrages after " + Msg.duration(a.enrageSeconds() * 1000L) : ""));
        for (int i = 0; i < def.phases().size(); i++) {
            BossDefinition.Phase phase = def.phases().get(i);
            StringBuilder abilities = new StringBuilder();
            for (AbilitySpec spec : concat(phase)) {
                if (abilities.length() > 0) {
                    abilities.append(", ");
                }
                abilities.append(spec.type().id());
            }
            core.msg().mini(sender, "<gray>Phase " + (i + 1) + " <dark_gray>(" + Math.round(phase.at() * 100) + "%) <white>"
                + MiniEsc.esc(phase.name()) + "<gray>: " + abilities);
        }
        for (BossDefinition.LootEntry entry : def.loot().entries()) {
            core.msg().mini(sender, "<gray> drop <white>" + entry.item() + " <dark_gray>" + (entry.chance() >= 1 ? "guaranteed"
                : String.format(Locale.ROOT, "%.2f%%", entry.chance() * 100)) + (entry.minShare() > 0 ? " (needs "
                + Math.round(entry.minShare() * 100) + "% damage)" : ""));
        }
        if (sender instanceof Player p) {
            BossData data = core.profiles().section(p.getUniqueId(), "boss", BossData.class, BossData::new);
            core.msg().mini(sender, "<gray>Your kills: <white>" + data.kills.getOrDefault(def.id(), 0));
        }
    }

    private static List<AbilitySpec> concat(BossDefinition.Phase phase) {
        List<AbilitySpec> all = new ArrayList<>(phase.enter());
        all.addAll(phase.abilities());
        return all;
    }

    private void spawn(CommandSender sender, String[] args) {
        if (args.length < 2) {
            core.msg().info(sender, "Usage: /boss spawn <id> [x y z [world]]");
            return;
        }
        Optional<BossDefinition> found = manager.config().definition(args[1].toLowerCase(Locale.ROOT));
        if (found.isEmpty()) {
            core.msg().error(sender, "Unknown boss '" + args[1] + "'.");
            return;
        }
        Location at;
        Player summoner = sender instanceof Player p ? p : null;
        if (args.length >= 5) {
            World world = args.length >= 6 ? Bukkit.getWorld(args[5]) : summoner != null ? summoner.getWorld() : null;
            if (world == null) {
                core.msg().error(sender, "Give a loaded world name.");
                return;
            }
            try {
                at = new Location(world, Double.parseDouble(args[2]), Double.parseDouble(args[3]), Double.parseDouble(args[4]));
            } catch (NumberFormatException e) {
                core.msg().error(sender, "Coordinates must be numbers.");
                return;
            }
        } else if (summoner != null) {
            at = summoner.getLocation();
        } else {
            core.msg().error(sender, "From the console give coordinates and a world.");
            return;
        }
        boolean personal = found.get().isSlayer();
        Optional<BossSession> session = manager.spawn(found.get(), at, summoner, new BossSession.SpawnOptions(
            personal, false, 0, 0, -1, null, null));
        if (session.isPresent()) {
            core.msg().ok(sender, "Spawned " + found.get().id() + ".");
        } else {
            core.msg().error(sender, "The boss could not spawn there (chunk not loaded, world missing or too many fights).");
        }
    }

    private void kill(CommandSender sender, String[] args) {
        String filter = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "all";
        int n = 0;
        for (BossSession s : manager.active()) {
            if (filter.equals("all") || s.definition().id().equals(filter)) {
                s.end(BossSession.End.ADMIN, null);
                n++;
            }
        }
        core.msg().ok(sender, "Removed " + n + (n == 1 ? " fight" : " fights") + " (no rewards given).");
    }

    private void active(CommandSender sender) {
        if (manager.active().isEmpty()) {
            core.msg().info(sender, "No boss fight is running.");
            return;
        }
        for (BossSession s : manager.active()) {
            Location at = s.location();
            core.msg().mini(sender, " <white>" + s.definition().id() + " <gray>" + Math.round(s.fraction() * 100) + "% <dark_gray>"
                + s.world().getName() + " " + at.getBlockX() + " " + at.getBlockY() + " " + at.getBlockZ()
                + " <gray>fighters: " + s.ledger().size() + (s.isPersonal() ? " <yellow>personal" : ""));
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        boolean admin = sender.hasPermission(BossListener.ADMIN);
        if (args.length == 1) {
            out.addAll(List.of("list", "info", "claim"));
            if (admin) {
                out.addAll(List.of("spawn", "kill", "active", "reload"));
            }
        } else if (args.length == 2) {
            switch (args[0].toLowerCase(Locale.ROOT)) {
                case "info" -> out.addAll(manager.config().bosses().keySet());
                case "spawn" -> {
                    if (admin) {
                        out.addAll(manager.config().bosses().keySet());
                        manager.config().slayers().values().forEach(s -> s.tiers().forEach(t -> out.add(t.boss().id())));
                    }
                }
                case "kill" -> {
                    if (admin) {
                        out.add("all");
                        manager.active().forEach(s -> out.add(s.definition().id()));
                    }
                }
                default -> { }
            }
        } else if (args.length == 6 && args[0].equalsIgnoreCase("spawn") && admin) {
            Bukkit.getWorlds().forEach(w -> out.add(w.getName()));
        }
        String prefix = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
        out.removeIf(s -> !s.toLowerCase(Locale.ROOT).startsWith(prefix));
        out.sort(Comparator.naturalOrder());
        return out;
    }
}
