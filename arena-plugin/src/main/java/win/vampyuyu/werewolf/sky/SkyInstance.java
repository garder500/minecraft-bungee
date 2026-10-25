package win.vampyuyu.werewolf.sky;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;

/**
 * Places and removes the blocks of the platforms in the sky world. Work is split per chunk and run on
 * the region that owns the chunk (Folia style); the blocks written by {@link #build} are remembered per
 * slot so {@link #clear} removes exactly those and nothing else.
 */
public final class SkyInstance {
  /** Runs a task on whatever thread owns the given chunk. */
  public interface ChunkRunner {
    void run(int chunkX, int chunkZ, Runnable task);
  }

  /** Writes one block; only ever called from inside a {@link ChunkRunner} task for that block's chunk. */
  public interface BlockWriter {
    void set(int x, int y, int z, Material material);
  }

  /** An absolute block position that {@link #build} wrote. */
  record Pos(int x, int y, int z) {}

  private final World world;
  private final ChunkRunner runner;
  private final BlockWriter writer;
  private final DecorConfig decor;
  private final Map<Integer, List<Pos>> placed = new ConcurrentHashMap<>();

  public SkyInstance(World world, ChunkRunner runner, BlockWriter writer, DecorConfig decor) {
    this.world = world;
    this.runner = runner;
    this.writer = writer;
    this.decor = decor;
  }

  /** The production wiring: region scheduler per chunk, plain setType without physics. */
  public static SkyInstance forPlugin(Plugin plugin, World world, DecorConfig decor) {
    ChunkRunner runner =
        (cx, cz, task) -> Bukkit.getRegionScheduler().run(plugin, world, cx, cz, scheduled -> task.run());
    BlockWriter writer = (x, y, z, m) -> world.getBlockAt(x, y, z).setType(m, false);
    return new SkyInstance(world, runner, writer, decor);
  }

  /**
   * Places the platform for this many players. The future completes once every chunk task ran.
   *
   * @throws IllegalStateException when the slot is already built
   * @throws IllegalArgumentException when the player count is out of range
   */
  public CompletableFuture<Void> build(SkySlot slot, int players) {
    return build(slot, players, fraction -> {});
  }

  /** Same, reporting the share of chunk tasks done (0..1) every time one finishes. */
  public CompletableFuture<Void> build(SkySlot slot, int players, java.util.function.DoubleConsumer progress) {
    List<DecorConfig.Placement> decorBlocks = decor.generate(players);
    List<Pos> positions = new ArrayList<>(decorBlocks.size());
    Map<Long, List<Runnable>> byChunk = new TreeMap<>();
    for (var b : decorBlocks) {
      Pos p = absolute(slot, b.dx(), b.dy(), b.dz());
      positions.add(p);
      Material m = b.material();
      chunkTasks(byChunk, p).add(() -> writer.set(p.x(), p.y(), p.z(), m));
    }
    if (placed.putIfAbsent(slot.index(), positions) != null)
      throw new IllegalStateException("Sky slot " + slot.index() + " is already built");
    return dispatch(byChunk, progress);
  }

  /** Removes every block {@link #build} placed for this slot; harmless when nothing was built. */
  public CompletableFuture<Void> clear(SkySlot slot) {
    List<Pos> positions = placed.remove(slot.index());
    if (positions == null) return CompletableFuture.completedFuture(null);
    Map<Long, List<Runnable>> byChunk = new TreeMap<>();
    for (Pos p : positions) chunkTasks(byChunk, p).add(() -> writer.set(p.x(), p.y(), p.z(), Material.AIR));
    return dispatch(byChunk);
  }

  public boolean isBuilt(SkySlot slot) {
    return placed.containsKey(slot.index());
  }

  /** How many blocks {@link #clear} would remove for this slot. */
  public int placedBlocks(SkySlot slot) {
    List<Pos> positions = placed.get(slot.index());
    return positions == null ? 0 : positions.size();
  }

  /** Absolute seat locations (player standing spot, facing the campfire), one per player. */
  public List<Location> seatLocations(SkySlot slot, int players) {
    List<Location> out = new ArrayList<>(players);
    for (var seat : PlatformLayout.of(players).seats())
      out.add(
          new Location(
              world, slot.centreX() + 0.5 + seat.x(), SkySlot.PLATFORM_Y + 1, slot.centreZ() + 0.5 + seat.z(), seat.yaw(), 0f));
    return out;
  }

  /** Where the camp waits before sitting down: half way between the fire and the seat ring, facing the fire. */
  public Location spawnPoint(SkySlot slot, int players) {
    double half = PlatformLayout.of(players).ringRadius() / 2;
    // Seat 0 sits on +z at the ring radius; the spawn is on the same side, closer to the fire.
    return new Location(world, slot.centreX() + 0.5, SkySlot.PLATFORM_Y + 1, slot.centreZ() + 0.5 + half, 180f, 0f);
  }

  private static Pos absolute(SkySlot slot, int dx, int dy, int dz) {
    return new Pos(slot.centreX() + dx, SkySlot.PLATFORM_Y + dy, slot.centreZ() + dz);
  }

  private static List<Runnable> chunkTasks(Map<Long, List<Runnable>> byChunk, Pos p) {
    long key = ((long) (p.x() >> 4) << 32) | ((p.z() >> 4) & 0xFFFFFFFFL);
    return byChunk.computeIfAbsent(key, k -> new ArrayList<>());
  }

  /** One scheduler task per chunk; the returned future completes when the last one finished. */
  private CompletableFuture<Void> dispatch(Map<Long, List<Runnable>> byChunk) {
    return dispatch(byChunk, fraction -> {});
  }

  private CompletableFuture<Void> dispatch(
      Map<Long, List<Runnable>> byChunk, java.util.function.DoubleConsumer progress) {
    final int total = byChunk.size();
    CompletableFuture<Void> done = new CompletableFuture<>();
    if (byChunk.isEmpty()) {
      done.complete(null);
      return done;
    }
    AtomicInteger left = new AtomicInteger(byChunk.size());
    // The future only completes once every chunk task is over, even after a failure: a slot is
    // reused as soon as its clear() future completes, so no write of the old platform may be pending.
    AtomicReference<Throwable> failure = new AtomicReference<>();
    Runnable finished =
        () -> {
          int remaining = left.decrementAndGet();
          try {
            progress.accept(total == 0 ? 1.0 : (total - remaining) / (double) total);
          } catch (RuntimeException ignored) {
            // Progress is cosmetic.
          }
          if (remaining != 0) return;
          Throwable first = failure.get();
          if (first != null) done.completeExceptionally(first);
          else done.complete(null);
        };
    for (var entry : byChunk.entrySet()) {
      int cx = (int) (entry.getKey() >> 32), cz = (int) (long) entry.getKey();
      List<Runnable> tasks = entry.getValue();
      Runnable chunk =
          () -> {
            try {
              // One bad block must not leave the rest of the chunk unwritten.
              for (Runnable t : tasks) {
                try {
                  t.run();
                } catch (RuntimeException e) {
                  failure.compareAndSet(null, e);
                }
              }
            } finally {
              finished.run();
            }
          };
      try {
        runner.run(cx, cz, chunk);
      } catch (RuntimeException e) {
        // The scheduler refused this chunk: count it as finished so the future still completes.
        failure.compareAndSet(null, e);
        finished.run();
      }
    }
    return done;
  }
}
