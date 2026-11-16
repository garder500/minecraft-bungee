package win.vampyuyu.valoblock;

import java.util.function.Predicate;
import org.bukkit.Location;
import org.bukkit.World;

/**
 * Keeps the arena's lobby spawn from burying a player: when the configured point is inside solid blocks it moves up
 * to the nearest spot with two air blocks and a solid floor. Pure over a "is this block passable" test.
 */
final class SafeSpawn {
  /** How far up the search goes before giving up and leaving the spawn as it is. */
  static final int MAX_LIFT = 64;

  private SafeSpawn() {}

  /** A body fits at (x, y, z): the two blocks it occupies are passable and the block under its feet is not. */
  static boolean fits(Predicate<int[]> passable, int x, int y, int z) {
    return passable.test(new int[] {x, y, z}) && passable.test(new int[] {x, y + 1, z}) && !passable.test(new int[] {x, y - 1, z});
  }

  /**
   * The y to stand at: {@code y} itself when it is already safe or when the spawn is in the open air above a floor
   * (nothing to fix), otherwise the first safe y above it. -1 when none is found within {@link #MAX_LIFT} blocks.
   */
  static int safeY(Predicate<int[]> passable, int x, int y, int z, int minY, int maxY) {
    boolean buried = !passable.test(new int[] {x, y, z}) || !passable.test(new int[] {x, y + 1, z});
    if (!buried) return y;
    for (int dy = 1; dy <= MAX_LIFT && y + dy < maxY; dy++) if (fits(passable, x, y + dy, z)) return y + dy;
    return -1;
  }

  /** The location to use for a player at {@code where}: the same one when it is fine, a lifted copy when it was buried, null when nothing safe is above. */
  static Location lifted(World w, Location where) {
    int x = where.getBlockX(), y = where.getBlockY(), z = where.getBlockZ();
    Predicate<int[]> passable = c -> !w.getBlockAt(c[0], c[1], c[2]).getType().isSolid();
    int safe = safeY(passable, x, y, z, w.getMinHeight(), w.getMaxHeight());
    if (safe == y) return where;
    if (safe < 0) return null;
    Location out = where.clone();
    out.setY(safe);
    return out;
  }
}
