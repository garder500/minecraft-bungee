package win.vampyuyu.survival.boss;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

/** Particle, sound and geometry helpers shared by every ability. Never throws: a bad particle is skipped. */
public final class Fx {
    private Fx() {}

    public static void particle(World world, Particle particle, Location at, int count, double dx, double dy,
            double dz, double speed) {
        if (world == null) {
            return;
        }
        try {
            world.spawnParticle(particle, at, count, dx, dy, dz, speed);
        } catch (RuntimeException e) {
            // particles that need extra data are simply skipped
        }
    }

    public static void dust(World world, Location at, Color color, float size, int count, double spread) {
        if (world == null) {
            return;
        }
        try {
            world.spawnParticle(Particle.DUST, at, count, spread, spread, spread, 0, new Particle.DustOptions(color, size));
        } catch (RuntimeException e) {
            // ignored
        }
    }

    public static void sound(World world, Location at, Sound sound, float volume, float pitch) {
        if (world != null) {
            try {
                world.playSound(at, sound, volume, pitch);
            } catch (RuntimeException e) {
                // ignored
            }
        }
    }

    /** Points of a horizontal circle (at most {@code points}). */
    public static List<Location> circlePoints(Location center, double radius, int points) {
        List<Location> list = new ArrayList<>(points);
        for (int i = 0; i < points; i++) {
            double angle = 2 * Math.PI * i / points;
            list.add(center.clone().add(Math.cos(angle) * radius, 0, Math.sin(angle) * radius));
        }
        return list;
    }

    public static void circle(World world, Location center, double radius, Particle particle, int points) {
        for (Location point : circlePoints(center, radius, Math.min(points, 160))) {
            particle(world, particle, point, 1, 0, 0, 0, 0);
        }
    }

    public static void dustCircle(World world, Location center, double radius, Color color, float size, int points) {
        for (Location point : circlePoints(center, radius, Math.min(points, 160))) {
            dust(world, point, color, size, 1, 0);
        }
    }

    /** Disc of dust (telegraph of a zone). */
    public static void dustDisc(World world, Location center, double radius, Color color, float size) {
        for (double r = radius; r > 0.3; r -= Math.max(1.0, radius / 3)) {
            dustCircle(world, center, r, color, size, (int) Math.min(48, Math.max(8, r * 6)));
        }
    }

    /** Particles along a segment. */
    public static void line(World world, Location from, Location to, Particle particle, double step) {
        Vector path = to.toVector().subtract(from.toVector());
        double length = path.length();
        if (length < 0.01) {
            return;
        }
        Vector dir = path.clone().multiply(1 / length);
        double s = Math.max(0.2, step);
        int guard = 0;
        for (double d = 0; d < length && guard++ < 200; d += s) {
            particle(world, particle, from.clone().add(dir.clone().multiply(d)), 1, 0, 0, 0, 0);
        }
    }

    public static void dustLine(World world, Location from, Location to, Color color, float size, double step) {
        Vector path = to.toVector().subtract(from.toVector());
        double length = path.length();
        if (length < 0.01) {
            return;
        }
        Vector dir = path.clone().multiply(1 / length);
        int guard = 0;
        for (double d = 0; d < length && guard++ < 200; d += Math.max(0.2, step)) {
            dust(world, from.clone().add(dir.clone().multiply(d)), color, size, 1, 0);
        }
    }

    /** True for players a boss may hurt or aim at: online, alive, survival or adventure, not an NPC. */
    public static boolean targetable(Player player) {
        if (player == null || !player.isOnline() || player.isDead() || player.getHealth() <= 0) {
            return false;
        }
        GameMode mode = player.getGameMode();
        return (mode == GameMode.SURVIVAL || mode == GameMode.ADVENTURE) && !player.hasMetadata("NPC");
    }

    /** Horizontal distance. */
    public static double flat(Location a, Location b) {
        double dx = a.getX() - b.getX();
        double dz = a.getZ() - b.getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }

    /** Applies a push but keeps it safe over a void: limited speed, limited lift. */
    public static void push(Player player, Vector velocity, double maxHorizontal, double maxVertical) {
        Vector v = velocity.clone();
        double h = Math.sqrt(v.getX() * v.getX() + v.getZ() * v.getZ());
        if (h > maxHorizontal && h > 0) {
            v.setX(v.getX() / h * maxHorizontal);
            v.setZ(v.getZ() / h * maxHorizontal);
        }
        v.setY(Math.max(-maxVertical, Math.min(maxVertical, v.getY())));
        if (Double.isFinite(v.getX()) && Double.isFinite(v.getY()) && Double.isFinite(v.getZ())) {
            player.setVelocity(v);
        }
    }

    /** Runs {@code action} for each point and returns nothing; tiny helper for readability. */
    public static void each(List<Location> points, Consumer<Location> action) {
        points.forEach(action);
    }
}
