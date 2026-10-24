package win.vampyuyu.werewolf.sky;

import java.util.Random;
import org.bukkit.Bukkit;
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
