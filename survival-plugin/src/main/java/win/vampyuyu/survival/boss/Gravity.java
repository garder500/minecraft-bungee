package win.vampyuyu.survival.boss;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

/**
 * The Devourer's movement abilities (GDD 4.6 and 12.5). Everything here is made of what Minecraft really has: potion
 * effects and capped velocities. Nothing kills outright, every effect is announced and has a counter.
 */
final class Gravity {
    private Gravity() {}

    /** Horizontal speed cap of the current in {@link #weightless} (blocks per tick, see {@link Fx#push}). */
    static final double DRIFT_CAP = 0.45;
    /** Vertical cap of the current. */
    static final double LIFT_CAP = 0.15;

    // ---------------------------------------------------------------- pure rules (tested)

    /**
     * The spot a player is heading to: {@code lead} blocks ahead of where they look, flat. A player looking straight up or
     * down gets the spot under their feet, so the "future" mark never lands somewhere absurd.
     */
    static Vector leadPoint(Vector position, Vector direction, double lead) {
        Vector flat = new Vector(direction.getX(), 0, direction.getZ());
        if (flat.lengthSquared() < 1e-4 || !Double.isFinite(flat.lengthSquared())) {
            return position.clone();
        }
        return position.clone().add(flat.normalize().multiply(Math.max(0, lead)));
    }

    /** Pulls a mark back inside the arena: it never lands farther than {@code radius} from the centre. */
    static Vector clampToArena(Vector mark, Vector centre, double radius) {
        Vector flat = new Vector(mark.getX() - centre.getX(), 0, mark.getZ() - centre.getZ());
        double d = flat.length();
        if (d <= radius || d == 0) {
            return mark.clone();
        }
        flat.multiply(radius / d);
        return new Vector(centre.getX() + flat.getX(), mark.getY(), centre.getZ() + flat.getZ());
    }

    /** True when a player at {@code y} may still be lifted: they float up to {@code ceiling} blocks above the boss's feet. */
    static boolean mayRise(double playerY, double bossY, double ceiling) {
        return playerY < bossY + ceiling;
    }

    // ---------------------------------------------------------------- time echo (phase I)

    /**
     * Event Horizon, time: for every target the boss projects the past and the future. The cyan mark is where the player
     * stands now (it will have been their past when it lands), the violet one is where they are walking to. Both strike
     * together after the telegraph; a player standing on neither takes nothing, and nobody is hit twice.
     */
    static int timeEcho(BossSession s, AbilitySpec spec) {
        int count = Math.min(6, Math.max(1, spec.integer("count", 3)));
        double radius = Math.max(1.5, Math.min(6, spec.num("radius", 3.2)));
        double lead = spec.num("lead", 7.0);
        double arena = spec.num("arena", 40);
        int tele = Abilities.tele(spec, 50);
        List<Player> targets = s.targets(spec.target(), count);
        if (targets.isEmpty()) {
            return 20;
        }
        Location centre = s.anchor();
        List<Location> past = new ArrayList<>();
        List<Location> future = new ArrayList<>();
        for (Player p : targets) {
            Location here = p.getLocation();
            past.add(Abilities.ground(s.world, here));
            Vector ahead = clampToArena(leadPoint(here.toVector(), here.getDirection(), lead), centre.toVector(), arena);
            future.add(Abilities.ground(s.world, new Location(s.world, ahead.getX(), here.getY() + 1, ahead.getZ())));
        }
        Fx.sound(s.world, s.location(), Sound.BLOCK_BEACON_ACTIVATE, 3f, 0.6f);
        s.every(0, 4, Math.max(1, tele / 4), step -> {
            for (Location mark : past) {
                Fx.dustCircle(s.world, mark.clone().add(0, 0.1, 0), radius, Color.AQUA, 1.2f, 24);
                Fx.particle(s.world, Particle.REVERSE_PORTAL, mark.clone().add(0, 0.5, 0), 4, radius / 3, 0.4, radius / 3, 0.05);
            }
            for (Location mark : future) {
                Fx.dustCircle(s.world, mark.clone().add(0, 0.1, 0), radius, Color.FUCHSIA, 1.2f, 24);
                Fx.particle(s.world, Particle.END_ROD, mark.clone().add(0, 0.5, 0), 3, radius / 3, 0.4, radius / 3, 0.02);
            }
        }, () -> {
            List<Location> all = new ArrayList<>(past);
            all.addAll(future);
            for (Location mark : all) {
                Fx.particle(s.world, Particle.EXPLOSION, mark.clone().add(0, 0.5, 0), 1, 0, 0, 0, 0);
                Fx.particle(s.world, Particle.PORTAL, mark.clone().add(0, 1, 0), 50, radius / 2, 1.2, radius / 2, 0.4);
            }
            Fx.sound(s.world, s.location(), Sound.ENTITY_ENDERMAN_TELEPORT, 3f, 0.5f);
            for (Player p : s.players()) {
                for (Location mark : all) {
                    if (p.getLocation().distanceSquared(mark) <= radius * radius) {
                        s.hit(p, spec.damage());
                        break;
                    }
                }
            }
        });
        return tele + 25;
    }

    // ---------------------------------------------------------------- weightlessness (phase III)

    /**
     * Naked Singularity, zero gravity: for {@code duration} seconds everybody falls slowly, rises a little (Levitation, only
     * below {@code ceiling} blocks over the boss) and is carried by a gentle current around the boss. The player keeps
     * control: the current is capped, and the effects are cleared as soon as the cast ends.
     */
    static int weightless(BossSession s, AbilitySpec spec) {
        int duration = spec.seconds("duration", 8);
        double ceiling = spec.num("ceiling", 9);
        double current = Math.max(0, Math.min(DRIFT_CAP, spec.num("current", 0.06)));
        int tele = Abilities.tele(spec, 30);
        Fx.sound(s.world, s.location(), Sound.BLOCK_BEACON_DEACTIVATE, 3f, 0.5f);
        s.broadcastNearby(net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(
            "<light_purple><bold>GRAVITY FAILS! <gray>Let go, drift, and keep your feet toward the light."));
        s.later(tele, () -> s.every(0, 5, Math.max(1, duration / 5), step -> {
            Location core = s.location();
            double bossY = core.getY();
            for (Player p : s.players()) {
                p.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 40, 0, false, false, true));
                if (mayRise(p.getLocation().getY(), bossY, ceiling)) {
                    p.addPotionEffect(new PotionEffect(PotionEffectType.LEVITATION, win.vampyuyu.survival.movement.MovementMath.levitationTicks(12),
                        win.vampyuyu.survival.movement.MovementMath.levitationAmp(0), false, false, true));
                } else {
                    p.removePotionEffect(PotionEffectType.LEVITATION);
                }
                Vector to = core.toVector().subtract(p.getLocation().toVector()).setY(0);
                if (to.lengthSquared() > 4 && Double.isFinite(to.lengthSquared())) {
                    Vector tangent = new Vector(-to.getZ(), 0, to.getX()).normalize().multiply(current);
                    Fx.push(p, p.getVelocity().multiply(0.8).add(tangent), DRIFT_CAP, LIFT_CAP);
                }
            }
            Fx.particle(s.world, Particle.REVERSE_PORTAL, core.clone().add(0, 3, 0), 30, 6, 4, 6, 0.1);
        }, () -> {
            for (Player p : s.players()) {
                p.removePotionEffect(PotionEffectType.LEVITATION);
            }
        }));
        return tele + duration + 20;
    }
}
