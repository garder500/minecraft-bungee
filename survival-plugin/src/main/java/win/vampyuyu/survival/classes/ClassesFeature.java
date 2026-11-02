package win.vampyuyu.survival.classes;

import java.util.List;
import java.util.Locale;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionDefault;
import win.vampyuyu.survival.api.ClassApi;
import win.vampyuyu.survival.api.PlayerClass;
import win.vampyuyu.survival.core.Core;
import win.vampyuyu.survival.core.Feature;
import win.vampyuyu.survival.core.Msg;

/**
 * The four classes at the Novice rank (GDD 11): Vanguard, Duelist, Channeler, Ranger. Chosen once at the start, changed
 * later by the class-change quests through {@link ClassApi#set}. No content requires a class (GDD 11.4): the services
 * of this feature only add to what a player does, nothing else asks for them.
 */
public final class ClassesFeature implements Feature {
    private ClassService service;

    @Override
    public String id() {
        return "classes";
    }

    @Override
    public void enable(Core core) {
        service = new ClassService(core);
        core.register(ClassApi.class, service);
        core.register(ClassService.class, service);
        core.listen(new ClassListener(core, service));
        core.permissions().register("vampyuyu.sb.classes.admin", "Set or reset the class of a player", PermissionDefault.OP);
        core.commands().command("class")
            .aliases("classes", "vocation")
            .description("Choose your class and use its active")
            .usage("/class [choose <class> | cast | reset <player>]")
            .permission("vampyuyu.sb.classes.use", PermissionDefault.TRUE)
            .tab((CommandSender sender, Command command, String alias, String[] args) -> {
                if (args.length == 1) {
                    return List.of("choose", "cast", "reset");
                }
                if (args.length == 2 && args[0].equalsIgnoreCase("choose")) {
                    return java.util.Arrays.stream(PlayerClass.values()).map(c -> c.name().toLowerCase(Locale.ROOT)).toList();
                }
                return List.of();
            })
            .executor((CommandSender sender, Command command, String label, String[] args) -> run(core, sender, args))
            .register();
        core.scheduler().repeating(20, 20, () -> {
            for (Player player : org.bukkit.Bukkit.getOnlinePlayers()) {
                if (service.inCombat(player.getUniqueId())) {
                    continue;
                }
                service.endCombat(player);
            }
        });
        core.scheduler().nextTick(() -> org.bukkit.Bukkit.getOnlinePlayers().forEach(service::refresh));
    }

    @Override
    public void disable() {
        if (service != null) {
            org.bukkit.Bukkit.getOnlinePlayers().forEach(p -> {
                var health = p.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
                if (health != null) {
                    health.removeModifier(ClassService.HEALTH);
                }
            });
        }
    }

    private boolean run(Core core, CommandSender sender, String[] args) {
        String sub = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("reset")) {
            if (!sender.hasPermission("vampyuyu.sb.classes.admin")) {
                core.msg().error(sender, "You cannot do that.");
            } else if (args.length != 2 || org.bukkit.Bukkit.getPlayerExact(args[1]) == null) {
                core.msg().error(sender, "Usage: /class reset <online player>");
            } else {
                Player target = org.bukkit.Bukkit.getPlayerExact(args[1]);
                service.set(target.getUniqueId(), null);
                core.msg().ok(sender, target.getName() + " can choose a class again.");
                core.msg().info(target, "Your class was reset. Choose a new one with /class.");
            }
            return true;
        }
        if (!(sender instanceof Player player)) {
            core.msg().error(sender, "Only players can use /class.");
            return true;
        }
        switch (sub) {
            case "choose" -> {
                PlayerClass chosen = args.length < 2 ? null : PlayerClass.parse(args[1]);
                if (chosen == null) {
                    core.msg().error(player, "Pick one: vanguard, duelist, channeler or ranger.");
                } else if (service.choose(player.getUniqueId(), chosen)) {
                    core.msg().ok(player, "You are now a " + chosen.displayName() + ". Active: " + chosen.active()
                        + " (sneak + swap hands, or /class cast).");
                } else {
                    core.msg().warn(player, "You already have a class. A class-change quest will let you change it.");
                }
            }
            case "cast" -> cast(core, service, player);
            default -> {
                var current = service.classOf(player.getUniqueId());
                if (current.isEmpty()) {
                    sendChoice(core, player);
                } else {
                    PlayerClass c = current.get();
                    core.msg().info(player, c.displayName() + " (Novice) - " + c.role());
                    core.msg().info(player, "Active " + c.active() + ": " + c.activeText() + " - " + Msg.number(c.aetherCost())
                        + " Aether, " + c.cooldownSeconds() + " s cooldown");
                    core.msg().info(player, "Passive: " + c.passiveText());
                    core.msg().info(player, "Aether: " + Msg.number(service.aether(player)) + " / " + Msg.number(service.maxAether(player)));
                }
            }
        }
        return true;
    }

    /** Casts and tells the player what happened. */
    static void cast(Core core, ClassService service, Player player) {
        ClassService.Cast result = service.cast(player);
        PlayerClass chosen = service.classOf(player.getUniqueId()).orElse(null);
        switch (result) {
            case OK -> player.sendActionBar(Msg.plain(chosen.active() + "!  Aether " + Msg.number(service.aether(player)), NamedTextColor.AQUA));
            case NO_CLASS -> sendChoice(core, player);
            case COOLDOWN -> core.msg().warn(player, chosen.active() + " is recharging: " + Msg.duration(service.cooldownLeft(player)) + ".");
            case NO_AETHER -> core.msg().warn(player, "Not enough Aether for " + chosen.active() + " (" + Msg.number(chosen.aetherCost())
                + " needed, " + Msg.duration(service.aetherWait(player, chosen)) + " to wait).");
            case NO_WEAPON -> core.msg().warn(player, "Hold your weapon to cast " + chosen.active() + ".");
            case NO_BOW -> core.msg().warn(player, "Hold your bow to cast " + chosen.active() + ".");
            case NO_ENGINE -> core.msg().warn(player, chosen.active() + " is not available on this server.");
        }
    }

    /** The first choice: one clickable line per class. */
    static void sendChoice(Core core, Player player) {
        core.msg().info(player, "Choose your class (you keep it until a class-change quest):");
        for (PlayerClass c : PlayerClass.values()) {
            Component line = Component.text(" [" + c.displayName() + "] ", NamedTextColor.GOLD)
                .append(Component.text(c.role() + " - " + c.active() + ": " + c.activeText(), NamedTextColor.GRAY))
                .clickEvent(ClickEvent.runCommand("/class choose " + c.name().toLowerCase(Locale.ROOT)))
                .hoverEvent(HoverEvent.showText(Component.text("Passive: " + c.passiveText() + "\nClick to choose " + c.displayName(),
                    NamedTextColor.YELLOW)));
            player.sendMessage(line);
        }
    }
}
