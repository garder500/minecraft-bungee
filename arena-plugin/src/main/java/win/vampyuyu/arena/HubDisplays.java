package win.vampyuyu.arena;

import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * The display entities of one waiting hub: its title and the item-display models blocks cannot make (pack models
 * {@code vampyuyu:hub/<name>}, Blockbench sources in resource-pack/art/blockbench/hubs/). They are not persistent: they
 * are spawned while a player is near the hub and removed when nobody is, so a restart never leaves stale ones (a
 * leftover is swept by its mark). Spinning models turn a step per tick with client-side interpolation; an orbiting
 * model glides around a centre with teleport interpolation.
 */
final class HubDisplays {
  static final NamespacedKey MARK = new NamespacedKey("vampyuyu", "waiting_hub");
  static final int PERIOD = 10;
  private static final double NEAR = 96;

  /**
   * One display. {@code item} null makes a billboard text. Position relative to the hub origin (floor y = 0).
   * {@code axis} and {@code spin} (degrees per second) turn the model about its own axis; {@code orbit} (blocks) and
   * {@code orbitSpeed} (degrees per second) move it around (x, z). {@code block}/{@code sky} &lt; 0 keep the world light.
   */
  record Spec(
      String item,
      Component text,
      double x,
      double y,
      double z,
      float scale,
      float yaw,
      char axis,
      float spin,
      double orbit,
      float orbitSpeed,
      int block,
      int sky) {
    static Spec model(String item, double x, double y, double z, float scale) {
      return new Spec(item, null, x, y, z, scale, 0, 'y', 0, 0, 0, 15, 15);
    }

    static Spec title(Component text, double x, double y, double z) {
      return new Spec(null, text, x, y, z, 1, 0, 'y', 0, 0, 0, 15, 15);
    }

    Spec spin(char axis, float degreesPerSecond) {
      return new Spec(item, text, x, y, z, scale, yaw, axis, degreesPerSecond, orbit, orbitSpeed, block, sky);
    }

    Spec yaw(float degrees) {
      return new Spec(item, text, x, y, z, scale, degrees, axis, spin, orbit, orbitSpeed, block, sky);
    }

    Spec orbit(double radius, float degreesPerSecond) {
      return new Spec(item, text, x, y, z, scale, yaw, axis, spin, radius, degreesPerSecond, block, sky);
    }

    Spec scale(float scale) {
      return new Spec(item, text, x, y, z, scale, yaw, axis, spin, orbit, orbitSpeed, block, sky);
    }

    Spec light(int block, int sky) {
      return new Spec(item, text, x, y, z, scale, yaw, axis, spin, orbit, orbitSpeed, block, sky);
    }
  }

  private final Plugin plugin;
  private final String name;
  private final World world;
  private final double ox, oy, oz;
  private final List<Spec> specs;
  private final List<Display> live = new ArrayList<>();
  private final float[] angles;
  private final float[] orbits;

  HubDisplays(Plugin plugin, String name, World world, double ox, double oy, double oz, List<Spec> specs) {
    this.plugin = plugin;
    this.name = name;
    this.world = world;
    this.ox = ox;
    this.oy = oy;
    this.oz = oz;
    this.specs = List.copyOf(specs);
    this.angles = new float[specs.size()];
    this.orbits = new float[specs.size()];
    purge();
  }

  void start() {
    plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 1, PERIOD);
  }

  static Quaternionf spin(char axis, float degrees) {
    float r = (float) Math.toRadians(degrees);
    return switch (axis) {
      case 'x' -> new Quaternionf().rotateX(r);
      case 'z' -> new Quaternionf().rotateZ(r);
      default -> new Quaternionf().rotateY(r);
    };
  }

  static Transformation transformation(Spec s, float angle) {
    return new Transformation(
        new Vector3f(),
        new Quaternionf().rotateY((float) Math.toRadians(s.yaw())),
        new Vector3f(s.scale()),
        spin(s.axis(), angle));
  }

  private Location at(Spec s, float orbitAngle) {
    double x = ox + s.x(), z = oz + s.z();
    if (s.orbit() > 0) {
      double a = Math.toRadians(orbitAngle);
      x += Math.cos(a) * s.orbit();
      z += Math.sin(a) * s.orbit();
    }
    Location l = new Location(world, x, oy + s.y(), z);
    if (s.orbit() > 0) l.setYaw(orbitAngle + 180); // nose along the path
    return l;
  }

  void tick() {
    if (!anyoneNear()) {
      remove();
      return;
    }
    if (live.size() != specs.size() || live.stream().anyMatch(d -> !d.isValid())) {
      remove();
      spawn();
      return;
    }
    for (int i = 0; i < specs.size(); i++) {
      Spec s = specs.get(i);
      Display d = live.get(i);
      if (s.spin() != 0) {
        angles[i] = (angles[i] + s.spin() * PERIOD / 20f) % 360f;
        d.setInterpolationDelay(0);
        d.setInterpolationDuration(PERIOD);
        d.setTransformation(transformation(s, angles[i]));
      }
      if (s.orbit() > 0) {
        orbits[i] = (orbits[i] + s.orbitSpeed() * PERIOD / 20f) % 360f;
        d.teleport(at(s, orbits[i]));
      }
    }
  }

  void remove() {
    for (Display d : live) d.remove();
    live.clear();
  }

  private boolean anyoneNear() {
    if (!world.isChunkLoaded((int) Math.floor(ox) >> 4, (int) Math.floor(oz) >> 4)) return false;
    for (Player p : world.getPlayers()) {
      Location l = p.getLocation();
      if (Math.hypot(l.getX() - ox, l.getZ() - oz) < NEAR) return true;
    }
    return false;
  }

  private void purge() {
    try {
      for (Entity e : world.getEntities())
        if (e instanceof Display && name.equals(e.getPersistentDataContainer().get(MARK, PersistentDataType.STRING)))
          e.remove();
    } catch (RuntimeException | LinkageError e) {
      // no entities (tests)
    }
  }

  private static ItemStack stack(String item) {
    ItemStack stack = new ItemStack(Material.PAPER);
    ItemMeta meta = stack.getItemMeta();
    meta.setItemModel(new NamespacedKey("vampyuyu", "hub/" + item));
    stack.setItemMeta(meta);
    return stack;
  }

  private void spawn() {
    for (int i = 0; i < specs.size(); i++) {
      Spec s = specs.get(i);
      float angle = angles[i];
      Location l = at(s, orbits[i]);
      Display d;
      if (s.item() == null) {
        d =
            world.spawn(
                l,
                TextDisplay.class,
                t -> {
                  t.text(s.text());
                  t.setBillboard(Display.Billboard.CENTER);
                  t.setSeeThrough(false);
                  t.setShadowed(true);
                  t.setBackgroundColor(org.bukkit.Color.fromARGB(96, 8, 10, 16));
                  t.setTransformation(transformation(s, 0));
                });
      } else {
        d =
            world.spawn(
                l,
                ItemDisplay.class,
                m -> {
                  m.setItemStack(stack(s.item()));
                  m.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
                  m.setTransformation(transformation(s, angle));
                  m.setTeleportDuration(PERIOD);
                });
      }
      if (s.block() >= 0) d.setBrightness(new Display.Brightness(s.block(), s.sky()));
      d.setViewRange(4f);
      d.setShadowRadius(0);
      d.setPersistent(false);
      d.setInvulnerable(true);
      d.getPersistentDataContainer().set(MARK, PersistentDataType.STRING, name);
      live.add(d);
    }
  }
}
