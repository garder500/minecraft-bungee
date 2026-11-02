package win.vampyuyu.survival.movement;

import java.util.List;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionDefault;
import win.vampyuyu.survival.core.Core;
import win.vampyuyu.survival.core.Feature;

/**
 * The {@code movement} feature (GDD 12.5): the {@link MovementService} toolkit and the staff command {@code /vmove} used
 * to try every mechanic on the dev server. The moving platforms and the propulsion pads live in the Tempestuous
 * Archipelago ({@code StormRealm}); the pressure currents in the Depths.
 */
public final class MovementFeature implements Feature {
    private static final List<String> SUB = List.of("knock", "pull", "lift", "slowfall", "jump", "anchor", "stop", "status");
    private MovementService service;
    private Core core;

    @Override
    public String id() {
        return "movement";
    }

    @Override
    public void enable(Core core) {
        this.core = core;
        service = new MovementService(core);
        service.start();
        core.register(MovementService.class, service);
        core.commands().command("vmove").description("Try the movement mechanics (staff)")
            .usage("/vmove <knock [strength] | pull [strength] [radius] [seconds] [anchors] | lift [amp] [seconds] | slowfall [seconds] | jump [amp] [seconds] | anchor [zone] | stop | status>")
            .permission("vampyuyu.sb.movement.test", PermissionDefault.OP)
            .tab((CommandSender s, Command c, String a, String[] args) -> args.length == 1 ? SUB : List.<String>of())
            .executor(this::run).register();
    }

    @Override
    public void disable() {
        if (service != null) {
            service.stop();
            service = null;
        }
    }

    private static double num(String[] args, int i, double def) {
        if (args.length <= i) {
            return def;
        }
        try {
            double v = Double.parseDouble(args[i]);
            return Double.isFinite(v) ? v : def;
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private boolean run(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            core.msg().warn(sender, "Players only.");
            return true;
        }
        if (args.length == 0) {
            return false;
        }
        // A zone is opened 4 blocks in front of the player, with a 2 second telegraph.
        Location ahead = p.getLocation().add(p.getLocation().getDirection().setY(0).normalize().multiply(4));
        switch (args[0].toLowerCase()) {
            case "knock" -> {
                service.knockback(p, p.getLocation().add(p.getLocation().getDirection().setY(0).normalize().multiply(-1)), num(args, 1, 1.0), 0.3);
                core.msg().info(p, "Knockback sent (strength capped at " + MovementMath.MAX_KNOCKBACK + ").");
            }
            case "pull" -> {
                int anchors = (int) num(args, 4, 0);
                var z = service.open(MovementService.Kind.PULL, ahead.add(0, 2, 0), num(args, 2, 8), 0, num(args, 1, 0.12), 0, 40,
                    (int) (num(args, 3, 10) * 20), anchors);
                core.msg().info(p, "Pull zone #" + z.id() + " opens in 2 s. Sneak to resist" + (anchors > 0 ? "; /vmove anchor " + z.id() + " breaks an anchor." : "."));
            }
            case "lift" -> {
                var z = service.open(MovementService.Kind.LIFT, ahead, 2, 30, 0, (int) num(args, 1, 0), 40, (int) (num(args, 2, 10) * 20), 0);
                core.msg().info(p, "Levitation column #" + z.id() + " opens in 2 s.");
            }
            case "slowfall" -> {
                var z = service.open(MovementService.Kind.SLOW_FALL, ahead, 6, 40, 0, 0, 40, (int) (num(args, 1, 20) * 20), 0);
                core.msg().info(p, "Slow-fall zone #" + z.id() + " opens in 2 s; the effect goes when you leave it.");
            }
            case "jump" -> {
                var z = service.open(MovementService.Kind.JUMP, ahead, 5, 8, 0, (int) num(args, 1, 2), 40, (int) (num(args, 2, 20) * 20), 0);
                core.msg().info(p, "Jump boost zone #" + z.id() + " opens in 2 s (amplifier capped at " + MovementMath.MAX_JUMP_AMP + ").");
            }
            case "anchor" -> {
                int id = (int) num(args, 1, service.zones().isEmpty() ? 0 : service.zones().get(0).id());
                core.msg().info(p, "Anchors left on #" + id + ": " + service.breakAnchor(id));
            }
            case "stop" -> {
                service.closeAll();
                core.msg().info(p, "All movement zones closed.");
            }
            case "status" -> {
                core.msg().info(p, service.zones().size() + " zone(s) open; last safe spot: "
                    + (service.lastSafe(p) == null ? "none yet" : service.lastSafe(p).toVector().toString()));
            }
            default -> {
                return false;
            }
        }
        return true;
    }
}
