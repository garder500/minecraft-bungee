package win.vampyuyu.survival.promotion;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionDefault;
import win.vampyuyu.survival.api.PlayerClass;
import win.vampyuyu.survival.api.PromotionApi;
import win.vampyuyu.survival.classes.ClassService;
import win.vampyuyu.survival.core.Core;
import win.vampyuyu.survival.core.Feature;

/**
 * Class promotions (GDD 11.5): the Adept and Master ranks, reached through quest chains given by the Master NPC of each class
 * (placed by an admin in the Hub area), with two specializations per class at Adept and a third capacity at Master. Needs the
 * classes feature (listed before it). No content requires a promotion (GDD 11.4).
 */
public final class PromotionFeature implements Feature {
    static final String ADMIN = "vampyuyu.sb.promotion.admin";
    private PromotionService service;
    private PromotionNpcs npcs;

    @Override
    public String id() {
        return "promotion";
    }

    @Override
    public void enable(Core core) {
        PromotionConfig cfg = PromotionConfig.load(core.data());
        cfg.problems().forEach(p -> core.getPlugin().getLogger().warning("promotion data: " + p));
        service = new PromotionService(core, core.require(ClassService.class), cfg);
        core.register(PromotionApi.class, service);
        core.register(PromotionService.class, service);
        core.listen(new PromotionListener(core, service));
        npcs = new PromotionNpcs(core, service);
        npcs.enable();
        core.permissions().register(ADMIN, "Place the Master NPCs, reset a promotion", PermissionDefault.OP);
        core.onDataChange(domain -> {
            if (domain.equals("promotion")) {
                service.reload(PromotionConfig.load(core.data()));
            }
        });
        core.commands().command("promotion")
            .aliases("promote", "master")
            .description("Your class promotion: trials, specialization")
            .usage("/promotion [start [profile] | choose <specialization> | abandon]")
            .permission("vampyuyu.sb.promotion.use", PermissionDefault.TRUE)
            .tab((CommandSender sender, Command command, String alias, String[] args) -> tab(args))
            .executor((CommandSender sender, Command command, String label, String[] args) -> run(core, sender, args))
            .register();
    }

    @Override
    public void disable() {
        if (npcs != null) {
            npcs.disable();
            npcs = null;
        }
    }

    public PromotionService service() { return service; }

    private List<String> tab(String[] args) {
        if (args.length == 1) {
            return List.of("start", "choose", "abandon", "npc", "reload", "reset");
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("start")) {
            return Arrays.stream(Profile.values()).map(p -> p.name().toLowerCase(Locale.ROOT)).toList();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("npc")) {
            return List.of("add", "remove", "list");
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("npc") && args[1].equalsIgnoreCase("add")) {
            return Arrays.stream(PlayerClass.values()).map(c -> c.name().toLowerCase(Locale.ROOT)).toList();
        }
        return List.of();
    }

    private boolean run(Core core, CommandSender sender, String[] args) {
        String sub = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("npc") || sub.equals("reload") || sub.equals("reset")) {
            return admin(core, sender, sub, args);
        }
        if (!(sender instanceof Player player)) {
            core.msg().error(sender, "Only players can use /promotion.");
            return true;
        }
        switch (sub) {
            case "start" -> {
                Profile profile = args.length > 1 ? Profile.parse(args[1]) : null;
                String refusal = service.start(player, profile);
                if (refusal != null) {
                    core.msg().warn(player, refusal);
                } else {
                    var chain = service.activeChain(player.getUniqueId()).orElseThrow();
                    core.msg().ok(player, chain.title() + " begins. " + chain.intro());
                    core.msg().info(player, "Step 1: " + chain.steps().get(0).text());
                }
            }
            case "choose" -> {
                String refusal = args.length < 2 ? "Usage: /promotion choose <specialization>" : service.choose(player, args[1]);
                if (refusal != null) {
                    core.msg().warn(player, refusal);
                }
            }
            case "abandon" -> {
                if (service.abandon(player.getUniqueId())) {
                    core.msg().ok(player, "Trial abandoned. Talk to your Master to start another.");
                } else {
                    core.msg().warn(player, "You have no trial in progress.");
                }
            }
            default -> service.talk(player, null);
        }
        return true;
    }

    private boolean admin(Core core, CommandSender sender, String sub, String[] args) {
        if (!sender.hasPermission(ADMIN)) {
            core.msg().error(sender, "You cannot do that.");
            return true;
        }
        switch (sub) {
            case "reload" -> {
                PromotionConfig fresh = PromotionConfig.load(core.data());
                service.reload(fresh);
                core.msg().ok(sender, "Promotion data reloaded" + (fresh.problems().isEmpty() ? "." : " with " + fresh.problems().size() + " problem(s): "
                    + String.join("; ", fresh.problems())));
            }
            case "reset" -> {
                Player target = args.length < 2 ? null : org.bukkit.Bukkit.getPlayerExact(args[1]);
                if (target == null) {
                    core.msg().error(sender, "Usage: /promotion reset <online player>");
                } else {
                    service.reset(target.getUniqueId());
                    core.msg().ok(sender, target.getName() + " is a Novice again.");
                }
            }
            default -> npcCommand(core, sender, args);
        }
        return true;
    }

    private void npcCommand(Core core, CommandSender sender, String[] args) {
        String action = args.length < 2 ? "" : args[1].toLowerCase(Locale.ROOT);
        switch (action) {
            case "add" -> {
                PlayerClass vocation = args.length < 3 ? null : PlayerClass.parse(args[2]);
                if (!(sender instanceof Player player) || vocation == null) {
                    core.msg().error(sender, "Usage (as a player): /promotion npc add <vanguard|duelist|channeler|ranger>");
                } else {
                    var npc = npcs.create(vocation, player.getLocation());
                    core.msg().ok(sender, "Master " + vocation.displayName() + " placed (" + npc.id + ").");
                }
            }
            case "remove" -> {
                if (!(sender instanceof Player player)) {
                    core.msg().error(sender, "Stand next to the Master to remove.");
                } else if (npcs.removeNear(player.getLocation(), 4)) {
                    core.msg().ok(sender, "Master removed.");
                } else {
                    core.msg().warn(sender, "No Master within 4 blocks.");
                }
            }
            case "list" -> npcs.all().forEach(n -> core.msg().info(sender, n.id + ": " + n.vocation + " in " + n.world
                + " at " + Math.round(n.x) + " " + Math.round(n.y) + " " + Math.round(n.z)));
            default -> core.msg().error(sender, "Usage: /promotion npc <add <class> | remove | list>");
        }
    }
}
