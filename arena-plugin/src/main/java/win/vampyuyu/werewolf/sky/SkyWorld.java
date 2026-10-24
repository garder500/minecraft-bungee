package win.vampyuyu.werewolf.sky;

import java.util.Random;
import org.bukkit.Bukkit;
import org.bukkit.Difficulty;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.WorldInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The dedicated void world {@value #NAME} that holds every sky platform, one per slot. The world is
 * created once and kept; platforms come and go inside it.
 *
 * <p>Folia: {@link WorldCreator#createWorld()} may be unavailable at runtime there. In that case
 * pre-create the world before the server starts: add to bukkit.yml
 *
 * <pre>worlds:
 *   ww_sky:
 *     generator: NexusArenaEngine</pre>
 *
 * and have the plugin main class return {@code new SkyWorld.VoidGenerator()} from {@code
 * getDefaultWorldGenerator}. {@link #world()} then simply finds the existing world.
 */
public final class SkyWorld {
  public static final String NAME = "ww_sky";

  private final SlotAllocator slots = new SlotAllocator(SkySlot.MAX_SLOTS);
  private volatile World world;

  /** Takes a free slot; the caller must {@link #release} it when its game ends. */
  public SkySlot allocate() {
    return new SkySlot(slots.allocate());
  }

  public void release(SkySlot slot) {
    slots.release(slot.index());
  }

  public boolean inUse(SkySlot slot) {
    return slots.inUse(slot.index());
  }

  /** The sky world when it is already loaded, else null. Safe from any thread. */
  public World loaded() {
    World w = world;
    if (w != null) return w;
    w = Bukkit.getWorld(NAME);
    if (w != null) world = w;
    return w;
  }

  /**
   * Creates the world (a void world, peaceful, no weather) unless it is loaded. Platforms are
   * ephemeral, so blocks a crashed server left in an older copy are discarded first. Must run on the
   * main thread (plugin enable). Returns null when the world is ready, else why it is not.
   */
  public synchronized String create() {
    try {
      if (loaded() == null) {
        deleteStale(Bukkit.getWorldContainer().toPath().resolve(NAME));
        World created = new WorldCreator(NAME).generator(new VoidGenerator()).createWorld();
        if (created == null) return "The server refused to create the world " + NAME;
        world = created;
      }
      World w = world;
      w.setDifficulty(Difficulty.PEACEFUL);
      w.setSpawnFlags(false, false);
      w.setStorm(false);
      w.setThundering(false);
      return null;
    } catch (Throwable e) {
      return "Could not create the world "
          + NAME
          + " ("
          + e
          + "). Pre-create it: see the SkyWorld javadoc";
    }
  }

  private static void deleteStale(java.nio.file.Path dir) throws java.io.IOException {
    if (!java.nio.file.Files.isDirectory(dir)) return;
    try (var walk = java.nio.file.Files.walk(dir)) {
      for (var path : walk.sorted(java.util.Comparator.reverseOrder()).toList())
        java.nio.file.Files.deleteIfExists(path);
    }
  }

  /** The sky world, created on first use. */
  public World world() {
    World w = world;
    if (w != null) return w;
    synchronized (this) {
      if (world == null) {
        World existing = Bukkit.getWorld(NAME);
        if (existing == null) {
          existing = new WorldCreator(NAME).generator(new VoidGenerator()).createWorld();
          if (existing == null) throw new IllegalStateException("Could not create the world " + NAME);
        }
        world = existing;
      }
      return world;
    }
  }

  /** A generator that produces nothing at all: every chunk is air. */
  public static final class VoidGenerator extends ChunkGenerator {
    @Override
    public @Nullable BiomeProvider getDefaultBiomeProvider(@NotNull WorldInfo worldInfo) {
      return null;
    }

    @Override
    public @Nullable Location getFixedSpawnLocation(@NotNull World world, @NotNull Random random) {
      return new Location(world, 0.5, SkySlot.PLATFORM_Y + 1, 0.5);
    }
  }
}
