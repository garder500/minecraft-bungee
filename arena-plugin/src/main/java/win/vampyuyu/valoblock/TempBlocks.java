package win.vampyuyu.valoblock;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;

/**
 * Blocks an ability changes for a while and the original blocks they come back to. A block changed twice
 * keeps its first original until the last holder lets go. Walls count the shots that hit them.
 */
final class TempBlocks {
  record Key(UUID world, int x, int y, int z) {
    static Key of(Block b) {
      return new Key(b.getWorld().getUID(), b.getX(), b.getY(), b.getZ());
    }
  }

  private static final class Saved {
    final BlockData original;
    final Block block;
    int refs;

    Saved(Block block) {
      this.block = block;
      this.original = block.getBlockData();
    }
  }

  /** A wall of temporary blocks with a hit counter: gunfire wears it down. */
  static final class Wall {
    final Set<Key> keys = new HashSet<>();
    final List<Block> blocks = new ArrayList<>();
    int hits;
    boolean gone;
  }

  private final Map<Key, Saved> saved = new HashMap<>();
  private final Map<Key, Wall> walls = new HashMap<>();

  /** Whether a block may be replaced by scenery of an ability: air and the like (grass, snow layers, torches). */
  static boolean free(Block b) {
    Material t = b.getType();
    return t.isAir() || (!t.isSolid() && t != Material.WATER && t != Material.LAVA && t != Material.BUBBLE_COLUMN);
  }

  /** Whether a player stands in the block. */
  static boolean occupied(Block b) {
    for (Player p : b.getWorld().getPlayers()) {
      Location l = p.getLocation();
      if (Math.abs(l.getX() - (b.getX() + 0.5)) < 0.9 && Math.abs(l.getZ() - (b.getZ() + 0.5)) < 0.9 && l.getY() > b.getY() - 1.8 && l.getY() < b.getY() + 1) return true;
    }
    return false;
  }

  /** Changes the block (kept for {@link #release}). Returns false when it was left alone. */
  boolean set(Block b, Material m) {
    Key k = Key.of(b);
    Saved s = saved.get(k);
    if (s == null) saved.put(k, s = new Saved(b));
    s.refs++;
    b.setType(m, false);
    return true;
  }

  /** Lets go of a block: its original comes back when nobody else holds it. */
  void release(Block b) {
    Key k = Key.of(b);
    Saved s = saved.get(k);
    if (s == null) return;
    if (--s.refs <= 0) {
      saved.remove(k);
      b.setBlockData(s.original, false);
    }
  }

  /** Builds a wall from free blocks only (none inside a player). Returns null when nothing could be placed. */
  Wall wall(List<Block> where, Material m, int hits) {
    Wall w = new Wall();
    w.hits = hits;
    for (Block b : where) {
      if (!free(b) || occupied(b)) continue;
      set(b, m);
      w.keys.add(Key.of(b));
      w.blocks.add(b);
      walls.put(Key.of(b), w);
    }
    if (w.blocks.isEmpty()) return null;
    return w;
  }

  void remove(Wall w) {
    if (w.gone) return;
    w.gone = true;
    for (Block b : w.blocks) {
      walls.remove(Key.of(b), w);
      release(b);
    }
  }

  /** A bullet stopped on this block. Returns the wall it wore down, or null when it is no wall. */
  Wall hit(Block b) {
    Wall w = walls.get(Key.of(b));
    if (w == null || w.gone) return null;
    if (--w.hits <= 0) remove(w);
    else if (w.keys.contains(Key.of(b)) && w.hits % 3 == 0) {
      // every third hit knocks the struck block out so the damage shows
      walls.remove(Key.of(b), w);
      w.keys.remove(Key.of(b));
      w.blocks.remove(b);
      release(b);
    }
    return w;
  }

  /** Puts every block back (match over, round over, plugin disable). */
  void restoreAll() {
    for (Saved s : new ArrayList<>(saved.values())) s.block.setBlockData(s.original, false);
    saved.clear();
    walls.clear();
  }

  int size() {
    return saved.size();
  }
}
