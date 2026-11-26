package win.vampyuyu.werewolf.sky;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * The display models of the platforms (the moon, the mill sails, the belfry bell and vane): item displays
 * spawned when a platform is built and removed when it is cleared. They are not persistent, so a crash or
 * a restart never leaves one behind, and they have no hitbox, so they never get in the way of a click.
 * A spinning model turns one step every {@link #STEP_TICKS} with client-side interpolation.
 */
final class DecorModels implements SkyInstance.ModelHost {
  /** Scoreboard tag of every decor model, to find strays by hand: /kill @e[tag=ww_decor_model]. */
  static final String TAG = "ww_decor_model";

  static final int STEP_TICKS = 40;

  private final Plugin plugin;
  private final World world;
  private final SkyInstance.ChunkRunner runner;
  private final Map<Integer, List<ItemDisplay>> live = new ConcurrentHashMap<>();
  /** The chunks held loaded for each slot's models: a non-persistent entity dies with its chunk's unload. */
  private final Map<Integer, List<long[]>> held = new ConcurrentHashMap<>();

  DecorModels(Plugin plugin, World world, SkyInstance.ChunkRunner runner) {
    this.plugin = plugin;
    this.world = world;
    this.runner = runner;
  }

  @Override
  public void show(SkySlot slot, double ox, double oy, double oz, List<DecorConfig.Model> models) {
    List<ItemDisplay> spawned = live.computeIfAbsent(slot.index(), k -> new CopyOnWriteArrayList<>());
    List<long[]> chunks = held.computeIfAbsent(slot.index(), k -> new CopyOnWriteArrayList<>());
    for (DecorConfig.Model m : models) {
      Location at = new Location(world, ox + m.dx(), oy + m.dy(), oz + m.dz());
      int cx = at.getBlockX() >> 4, cz = at.getBlockZ() >> 4;
      runner.run(
          cx,
          cz,
          () -> {
            // The platform was cleared before this task ran: spawning now would leave a stray model.
            if (live.get(slot.index()) != spawned) return;
            // The platform is built before anyone stands on it: keep the chunk (and so the model) loaded.
            if (world.addPluginChunkTicket(cx, cz, plugin)) chunks.add(new long[] {cx, cz});
            try {
              spawned.add(spawn(at, m));
            } catch (RuntimeException e) {
              plugin.getLogger().warning("Sky decor: model " + m.item() + " did not spawn: " + e);
            }
          });
    }
  }

  @Override
  public void hide(SkySlot slot) {
    List<ItemDisplay> spawned = live.remove(slot.index());
    List<long[]> chunks = held.remove(slot.index());
    if (spawned != null)
      for (ItemDisplay d : spawned) {
        Location at = d.getLocation();
        runner.run(at.getBlockX() >> 4, at.getBlockZ() >> 4, d::remove);
      }
    if (chunks != null)
      for (long[] c : chunks)
        runner.run((int) c[0], (int) c[1], () -> world.removePluginChunkTicket((int) c[0], (int) c[1], plugin));
  }

  /** The rotation of a model at a spin angle: its yaw about Y, then the spin about its own axis. */
  static Quaternionf rotation(DecorConfig.Model m, float spinDegrees) {
    Quaternionf q = new Quaternionf().rotateY((float) Math.toRadians(-m.yaw()));
    float r = (float) Math.toRadians(spinDegrees);
    return switch (m.spinAxis()) {
      case 'x' -> q.rotateX(r);
      case 'z' -> q.rotateZ(r);
      default -> q.rotateY(r);
    };
  }

  static Transformation transformation(DecorConfig.Model m, float spinDegrees) {
    return new Transformation(new Vector3f(), rotation(m, spinDegrees), new Vector3f(m.scale()), new Quaternionf());
  }

  private ItemDisplay spawn(Location at, DecorConfig.Model m) {
    ItemStack item = new ItemStack(Material.PAPER);
    var meta = item.getItemMeta();
    meta.setItemModel(new NamespacedKey("vampyuyu", "werewolf/" + m.item()));
    item.setItemMeta(meta);
    ItemDisplay d =
        world.spawn(
            at,
            ItemDisplay.class,
            e -> {
              e.setPersistent(false);
              e.addScoreboardTag(TAG);
              e.setItemStack(item);
              e.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
              e.setTransformation(transformation(m, 0));
              e.setViewRange(m.viewRange());
              if (m.glow()) e.setBrightness(new Display.Brightness(15, 15));
              if (m.billboard()) e.setBillboard(Display.Billboard.CENTER);
            });
    if (m.spinPerSecond() != 0) {
      float step = m.spinPerSecond() * STEP_TICKS / 20f;
      float[] angle = {0};
      d.getScheduler()
          .runAtFixedRate(
              plugin,
              task -> {
                angle[0] = (angle[0] + step) % 360f;
                d.setInterpolationDelay(0);
                d.setInterpolationDuration(STEP_TICKS);
                d.setTransformation(transformation(m, angle[0]));
              },
              null,
              1,
              STEP_TICKS);
    }
    return d;
  }
}
