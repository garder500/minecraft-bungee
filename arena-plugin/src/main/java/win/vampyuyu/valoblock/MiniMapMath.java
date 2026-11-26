package win.vampyuyu.valoblock;

import java.util.ArrayList;
import java.util.List;

/**
 * The minimap's geometry, no Bukkit. The minimap is the "minimap" element of the glyph HUD (top-left corner): the pack draws the framed
 * plan of each map as one sprite ({@code mm_<map id>}, resource-pack/art/valoblock_minimap.py) and the core shader centres each marker
 * glyph on the point its colour carries: red {@link #R_SMALL} or {@link #R_BIG} (the marker's size), green and blue the point on the plan
 * from 0 (west, north edge) to 255 (east, south edge). North is up and the map does not turn; your own arrow does (16 headings).
 */
final class MiniMapMath {
  /** Red channel of a marker glyph: 8 or 16 GUI px square (vb_layout.MM_R_SMALL / MM_R_BIG). */
  static final int R_SMALL = 86, R_BIG = 87;
  /** The self arrow is drawn turned to this many headings (vb_layout.MM_HEADINGS). */
  static final int HEADINGS = 16;

  enum Kind {
    SELF,
    ALLY,
    DEAD,
    ENEMY,
    SEED
  }

  /** A marker at a point in blocks from the map corner; {@code yaw} is the Minecraft yaw in degrees (only SELF shows it). */
  record Marker(Kind kind, double x, double z, float yaw) {}

  /** One marker glyph: the sprite, its size and the point on the plan, 0-255 each way. */
  record Mark(String sprite, boolean big, int x, int y) {}

  private MiniMapMath() {}

  /** The panel sprite of a map. */
  static String baseSprite(VbMap map) {
    return "mm_" + map.id();
  }

  /** The point (0-255, 0-255) of a position in blocks from the map corner, or null when it is off the plan. */
  static int[] project(VbMap map, double x, double z) {
    double w = (double) map.cell() * map.plan().get(0).length(), h = (double) map.cell() * map.plan().size();
    if (x < 0 || z < 0 || x >= w || z >= h) return null;
    return new int[] {(int) Math.round(x / w * 255), (int) Math.round(z / h * 255)};
  }

  /** The unit vector a Minecraft yaw looks along on the ground plane (x, z): yaw 0 faces +z (south, down the picture), 90 faces -x. */
  static double[] facing(float yaw) {
    double r = Math.toRadians(yaw);
    return new double[] {-Math.sin(r), Math.cos(r)};
  }

  /** The heading sprite (0 = north, up; counted clockwise in 22.5 degree steps) of a Minecraft yaw (180 = north, 270 = east). */
  static int heading(float yaw) {
    double clockwiseFromNorth = ((yaw - 180.0) % 360.0 + 360.0) % 360.0;
    return (int) Math.round(clockwiseFromNorth / (360.0 / HEADINGS)) % HEADINGS;
  }

  /** The glyphs of the markers, in the order given (the last one is drawn on top). Markers off the plan are left out. */
  static List<Mark> marks(VbMap map, List<Marker> markers) {
    List<Mark> out = new ArrayList<>();
    for (Marker m : markers) {
      int[] p = project(map, m.x(), m.z());
      if (p == null) continue;
      out.add(switch (m.kind()) {
        case SELF -> new Mark("mm_self_" + heading(m.yaw()), true, p[0], p[1]);
        case ALLY -> new Mark("mm_ally", false, p[0], p[1]);
        case DEAD -> new Mark("mm_dead", false, p[0], p[1]);
        case ENEMY -> new Mark("mm_enemy", false, p[0], p[1]);
        case SEED -> new Mark("mm_seed", false, p[0], p[1]);
      });
    }
    return out;
  }
}
