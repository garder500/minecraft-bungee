package win.vampyuyu.valoblock;

import java.util.List;
import java.util.Map;

/**
 * The themed builds of the shipped maps: one deterministic builder per map id (same plan, same blocks, every time).
 * A map without a builder (a map made in the editor) falls back to the greybox.
 */
public final class VbThemes {
  private VbThemes() {}

  /** A builder fills the canvas with the map's blocks (floor, walls, details) from its plan. */
  public interface Theme {
    void build(VbPlan plan, VbCanvas canvas);
  }

  private static final Map<String, Theme> THEMES = Map.of("hourfall", new HourfallTheme(), "rootvault", new RootvaultTheme(), "stormbreak", new StormbreakTheme());

  public static boolean hasTheme(String mapId) {
    return THEMES.containsKey(mapId);
  }

  /** Everything a match places for a map: the themed build and the invisible border, or the greybox. */
  public static VbCanvas canvas(VbMap map) {
    VbCanvas c = new VbCanvas();
    Theme t = THEMES.get(map.id());
    if (t == null) {
      map.greybox((x, y, z, k) -> c.set(x, y, z, map.material(k)));
      return c;
    }
    t.build(new VbPlan(map), c);
    border(map, c);
    return c;
  }

  /** The invisible wall that keeps players inside, up to the top of the clear box. */
  static void border(VbMap map, VbCanvas c) {
    int w = map.width(), d = map.depth();
    for (int y = -1; y <= map.highestY(); y++) {
      for (int x = -1; x <= w; x++) {
        c.set(x, y, -1, "barrier");
        c.set(x, y, d, "barrier");
      }
      for (int z = 0; z < d; z++) {
        c.set(-1, y, z, "barrier");
        c.set(w, y, z, "barrier");
      }
    }
  }

  /** Block-data strings that give light, for the "nothing pitch dark" check. */
  public static final List<String> LIGHTS = List.of("LANTERN", "SEA_LANTERN", "SHROOMLIGHT", "GLOWSTONE", "TORCH", "SOUL_LANTERN", "GLOW_LICHEN", "LIGHT", "OCHRE_FROGLIGHT", "VERDANT_FROGLIGHT", "PEARLESCENT_FROGLIGHT");
}
