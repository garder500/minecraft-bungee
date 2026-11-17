package win.vampyuyu.valoblock;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/** Pure geometry of a ValoBlock slot: the whole volume a map can touch, so a slot can be wiped whatever was built there before. */
public final class VbSlots {
  /** Extra blocks around the footprint and above/below the build range (leftovers of an older version of a theme). */
  public static final int MARGIN = 8;

  private VbSlots() {}

  /** An inclusive block box. */
  public record Box(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
    public boolean contains(int x, int y, int z) {
      return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
    }

    /** Chunk coordinates {cx, cz} the box touches, x-major. */
    public List<int[]> chunks() {
      List<int[]> out = new ArrayList<>();
      for (int cx = Math.floorDiv(minX, 16); cx <= Math.floorDiv(maxX, 16); cx++)
        for (int cz = Math.floorDiv(minZ, 16); cz <= Math.floorDiv(maxZ, 16); cz++) out.add(new int[] {cx, cz});
      return out;
    }

    /** First, middle and last chunk: enough to tell whether anything was ever generated here. */
    public List<int[]> sampleChunks() {
      List<int[]> all = chunks();
      return List.of(all.get(0), all.get(all.size() / 2), all.get(all.size() - 1));
    }
  }

  /** The largest width and depth of the maps. */
  public static int[] footprint(Collection<VbMap> maps) {
    int w = 0, d = 0;
    for (VbMap m : maps) {
      w = Math.max(w, m.width());
      d = Math.max(d, m.depth());
    }
    return new int[] {w, d};
  }

  /** The volume of a slot: x from the slot origin, z from 0, y around the floor level; covers every map and its border. */
  public static Box box(int slot, int spacing, int feetY, int width, int depth, int lowestY, int highestY) {
    int ox = slot * spacing;
    return new Box(ox - MARGIN, feetY + lowestY - MARGIN, -MARGIN, ox + width + MARGIN, feetY + highestY + MARGIN, depth + MARGIN);
  }

  /** Whether two slots' boxes are disjoint (the wipe of one never eats another's map). */
  public static boolean disjoint(Box a, Box b) {
    return a.maxX() < b.minX() || b.maxX() < a.minX() || a.maxZ() < b.minZ() || b.maxZ() < a.minZ() || a.maxY() < b.minY() || b.maxY() < a.minY();
  }
}
