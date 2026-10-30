package win.vampyuyu.survival.island;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

/** A position that can be written to JSON (never store a Bukkit Location: it drags the World along). */
public final class Pos {
    public String world;
    public double x;
    public double y;
    public double z;
    public float yaw;
    public float pitch;

    public Pos() {}

    public Pos(String world, double x, double y, double z, float yaw, float pitch) {
        this.world = world;
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.pitch = pitch;
    }

    public static Pos of(Location location) {
        World w = location.getWorld();
        return new Pos(w == null ? null : w.getName(), location.getX(), location.getY(), location.getZ(),
            location.getYaw(), location.getPitch());
    }

    /** The location, or null when the world is not loaded. */
    public Location toLocation() {
        World w = world == null ? null : Bukkit.getWorld(world);
        return w == null ? null : new Location(w, x, y, z, yaw, pitch);
    }
}
