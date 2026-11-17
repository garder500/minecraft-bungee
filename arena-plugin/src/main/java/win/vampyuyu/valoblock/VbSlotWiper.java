package win.vampyuyu.valoblock;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.bukkit.Bukkit;
import org.bukkit.ChunkSnapshot;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import win.vampyuyu.arena.NexusArenaEngine;

/** Sets whole slot volumes to air, a few chunks per tick, and removes the display entities left in them. Jobs run one after the other. */
final class VbSlotWiper {
  static final String TAG = "valoblock";
  private static final int CHUNKS_PER_TICK = 2;

  private final NexusArenaEngine plugin;
  private CompletableFuture<Void> tail = CompletableFuture.completedFuture(null);

  VbSlotWiper(NexusArenaEngine plugin) {
    this.plugin = plugin;
  }

  /** Wipes the boxes; completes (on the server thread) when every chunk is done. */
  synchronized CompletableFuture<Void> wipe(World w, List<VbSlots.Box> boxes) {
    CompletableFuture<Void> mine = new CompletableFuture<>();
    tail.whenComplete((v, e) -> Bukkit.getScheduler().runTask(plugin, () -> run(w, boxes, mine)));
    tail = mine;
    return mine;
  }

  private void run(World w, List<VbSlots.Box> boxes, CompletableFuture<Void> done) {
    ArrayDeque<Object[]> todo = new ArrayDeque<>();
    for (VbSlots.Box b : boxes) for (int[] c : b.chunks()) todo.add(new Object[] {b, c[0], c[1]});
    new BukkitRunnable() {
      @Override
      public void run() {
        try {
          for (int i = 0; i < CHUNKS_PER_TICK && !todo.isEmpty(); i++) {
            Object[] t = todo.poll();
            wipeChunk(w, (VbSlots.Box) t[0], (int) t[1], (int) t[2]);
          }
          if (todo.isEmpty()) {
            cancel();
            done.complete(null);
          }
        } catch (Throwable e) {
          cancel();
          done.completeExceptionally(e);
        }
      }
    }.runTaskTimer(plugin, 0, 1);
  }

  private static void wipeChunk(World w, VbSlots.Box b, int cx, int cz) {
    if (!w.isChunkGenerated(cx, cz)) return; // never built here: nothing to clear (and no chunk made for nothing)
    var chunk = w.getChunkAt(cx, cz);
    for (Entity e : chunk.getEntities())
      if (!(e instanceof Player) && (e instanceof Display || e instanceof Interaction || e instanceof Item || e.getScoreboardTags().contains(TAG))) e.remove();
    ChunkSnapshot snap = chunk.getChunkSnapshot(false, false, false);
    int x0 = Math.max(b.minX(), cx * 16), x1 = Math.min(b.maxX(), cx * 16 + 15);
    int z0 = Math.max(b.minZ(), cz * 16), z1 = Math.min(b.maxZ(), cz * 16 + 15);
    int y0 = Math.max(b.minY(), w.getMinHeight()), y1 = Math.min(b.maxY(), w.getMaxHeight() - 1);
    for (int y = y0; y <= y1; y++)
      for (int x = x0; x <= x1; x++)
        for (int z = z0; z <= z1; z++)
          if (!snap.getBlockType(x - cx * 16, y, z - cz * 16).isAir()) w.getBlockAt(x, y, z).setType(Material.AIR, false);
  }

  /** The boxes among these whose chunks were ever generated (cheap check on three chunks). */
  static List<VbSlots.Box> withData(World w, List<VbSlots.Box> boxes) {
    List<VbSlots.Box> out = new ArrayList<>();
    for (VbSlots.Box b : boxes) for (int[] c : b.sampleChunks()) if (w.isChunkGenerated(c[0], c[1])) {
      out.add(b);
      break;
    }
    return out;
  }
}
