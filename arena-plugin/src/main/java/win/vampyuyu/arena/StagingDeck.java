package win.vampyuyu.arena;

import static org.bukkit.Material.*;

import java.util.List;
import org.bukkit.Material;

/**
 * The ValoBlock waiting hub in vb_arena: the Staging Deck, a floating octagonal deck (radius {@value #R}) with a
 * briefing table under a holographic map, a mezzanine ring on pillars reached by two stairs, five deploy pods per team
 * under it (cyan west, coral east), a launch gate on a pad to the north, two antenna masts off the corners and a stepped
 * keel with a glowing thruster under it. The vb_arena world stays at noon, so the look relies on contrast: white
 * concrete, graphite deepslate, light strips under cyan glass. Coordinates: see {@link HubPlan}.
 */
final class StagingDeck {
  static final int R = 16;
  static final int MEZZ_Y = 5;
  /** Player spawn, relative: south of the table, facing the gate (north). */
  static final double SPAWN_X = 0.5, SPAWN_Z = 9.5;

  private StagingDeck() {}

  /** The octagon "radius" of a cell: a regular-ish octagon of flat sides on the axes and the diagonals. */
  static double oct(int x, int z) {
    int ax = Math.abs(x), az = Math.abs(z);
    return Math.max(Math.max(ax, az), (ax + az) * 0.7071);
  }

  static boolean deck(int x, int z) {
    return oct(x, z) <= R + 0.5;
  }

  /** The opening of the mezzanine toward the launch gate. */
  static boolean gateGap(int x, int z) {
    return z < -10 && Math.abs(x) <= 5;
  }

  static boolean mezz(int x, int z) {
    double o = oct(x, z);
    return o > R - 2.5 && o <= R + 0.5 && !gateGap(x, z);
  }

  static boolean pad(int x, int z) {
    return z >= -22 && z <= -16 && Math.abs(x) <= 8;
  }

  /** The cells of the pods: x = +-16, one per team slot. */
  static final List<Integer> POD_Z = List.of(-6, -3, 0, 3, 6);

  static HubPlan plan() {
    HubPlan p = new HubPlan();
    deckAndKeel(p);
    table(p);
    mezzanine(p);
    pods(p);
    rails(p);
    gate(p);
    mast(p, -14, -14, 34);
    mast(p, 14, -14, 24);
    skyBridge(p);
    return p;
  }

  private static void deckAndKeel(HubPlan p) {
    for (int x = -R - 1; x <= R + 1; x++) {
      for (int z = -R - 1; z <= R + 1; z++) {
        if (!deck(x, z)) continue;
        double o = oct(x, z);
        int ax = Math.abs(x), az = Math.abs(z);
        Material floor;
        boolean strip = false;
        if (o > R - 0.5) floor = POLISHED_DEEPSLATE;
        else if (o > R - 2.5) floor = DEEPSLATE_TILES;
        else if ((x == 0 || z == 0) && o > 4) {
          floor = CYAN_STAINED_GLASS;
          strip = true;
        } else if (o > 8.5 && o <= 9.5) floor = GRAY_CONCRETE;
        else if (ax % 4 == 0 || az % 4 == 0) floor = LIGHT_GRAY_CONCRETE;
        else floor = WHITE_CONCRETE;
        p.set(x, 0, z, floor);
        p.set(x, -1, z, strip ? SEA_LANTERN : POLISHED_DEEPSLATE);
      }
    }
    // The keel: a stepped octagon shell narrowing to a thruster, banded with cyan glass over lanterns.
    for (int d = 2; d <= 15; d++) {
      double r = R - (d - 1) * 1.0;
      boolean band = d == 4 || d == 9;
      for (int x = -R; x <= R; x++) {
        for (int z = -R; z <= R; z++) {
          double o = oct(x, z);
          if (o > r) continue;
          boolean shell = o > r - 1.6 || d == 15;
          if (!shell) continue;
          Material m =
              d == 15 ? (o <= 1.5 ? SEA_LANTERN : LIGHT_BLUE_STAINED_GLASS)
                  : band ? CYAN_STAINED_GLASS
                  : d % 2 == 0 ? DEEPSLATE_TILES : POLISHED_DEEPSLATE;
          p.set(x, -d, z, m);
          if (band && o > r - 1.0) p.set(x, -d, z, CYAN_STAINED_GLASS);
        }
      }
      if (band) // lanterns just inside the glass band
        for (int x = -R; x <= R; x++)
          for (int z = -R; z <= R; z++) {
            double o = oct(x, z);
            if (o <= r - 1.6 && o > r - 2.6) p.set(x, -d, z, SEA_LANTERN);
          }
    }
    for (int x = -1; x <= 1; x++)
      for (int z = -1; z <= 1; z++) p.set(x, -16, z, END_ROD, "[facing=down]");
    // Cable chains hanging off the rim, outside the keel.
    int[][] cables = {{16, 3, 7}, {-16, -4, 9}, {4, 16, 5}, {-5, 16, 8}, {11, -11, 6}, {-11, 11, 4}, {12, -10, 3}};
    for (int[] c : cables) {
      for (int y = -2; y >= -2 - c[2]; y--) p.soft(c[0], y, c[1], IRON_CHAIN);
      p.set(c[0], -3 - c[2], c[1], END_ROD, "[facing=down]");
    }
  }

  private static void table(HubPlan p) {
    for (int x = -2; x <= 2; x++) {
      for (int z = -2; z <= 2; z++) {
        boolean rim = Math.max(Math.abs(x), Math.abs(z)) == 2;
        p.set(x, 1, z, rim ? POLISHED_DEEPSLATE : LIGHT_BLUE_STAINED_GLASS);
        p.set(x, 0, z, rim ? POLISHED_DEEPSLATE : SEA_LANTERN);
      }
    }
  }

  private static void mezzanine(HubPlan p) {
    for (int x = -R; x <= R; x++) {
      for (int z = -R; z <= R; z++) {
        if (!mezz(x, z)) continue;
        double o = oct(x, z);
        boolean strip = o > R - 1.5 && o <= R - 0.5;
        p.set(x, MEZZ_Y, z, o > R - 0.5 ? POLISHED_DEEPSLATE : strip ? CYAN_STAINED_GLASS : WHITE_CONCRETE);
        if (strip) p.set(x, MEZZ_Y - 1, z, SEA_LANTERN);
      }
    }
    int[][] pillars = {
      {14, 5}, {14, -5}, {-14, 5}, {-14, -5}, {5, 14}, {-5, 14}, {10, 10}, {-10, 10}, {10, -10}, {-10, -10}, {6, -14},
      {-6, -14}
    };
    for (int[] c : pillars) {
      for (int y = 1; y < MEZZ_Y; y++) p.set(c[0], y, c[1], y == MEZZ_Y - 1 ? SEA_LANTERN : POLISHED_DEEPSLATE);
    }
    // Stairs east and west: steps at |x| 10..13 (y 1..4), landing on the mezzanine at |x| = 14.
    for (int side : new int[] {1, -1}) {
      String facing = side > 0 ? "east" : "west";
      for (int i = 0; i < 4; i++) {
        int x = side * (10 + i);
        for (int z = -1; z <= 1; z++) {
          for (int y = 1; y <= i; y++) p.set(x, y, z, POLISHED_DEEPSLATE);
          p.set(x, i + 1, z, POLISHED_DEEPSLATE_STAIRS, "[facing=" + facing + ",half=bottom]");
        }
      }
      for (int z = -1; z <= 1; z++) {
        p.set(side * 14, MEZZ_Y, z, WHITE_CONCRETE);
        p.clear(side * 14, MEZZ_Y - 1, z);
      }
    }
  }

  private static void pods(HubPlan p) {
    for (int side : new int[] {-1, 1}) {
      boolean west = side < 0;
      Material glass = west ? CYAN_STAINED_GLASS : ORANGE_STAINED_GLASS;
      Material light = west ? SEA_LANTERN : SHROOMLIGHT;
      Material pad = west ? CYAN_CONCRETE : ORANGE_CONCRETE;
      int wx = side * 16;
      for (int z = -7; z <= 7; z++) {
        for (int y = 1; y < MEZZ_Y; y++) p.set(wx, y, z, WHITE_CONCRETE);
      }
      for (int z : POD_Z) {
        for (int y = 1; y <= 3; y++) {
          p.set(wx, y, z, glass);
          p.set(wx + side, y, z, light);
          p.set(wx - side, y, z - 1, POLISHED_DEEPSLATE_WALL);
          p.set(wx - side, y, z + 1, POLISHED_DEEPSLATE_WALL);
        }
        p.set(wx - side, 0, z, pad);
        p.set(wx + side, 0, z, POLISHED_DEEPSLATE);
        p.set(wx + side, 4, z, POLISHED_DEEPSLATE);
      }
    }
  }

  private static void rails(HubPlan p) {
    for (int x = -R - 1; x <= R + 1; x++) {
      for (int z = -R - 1; z <= R + 1; z++) {
        if (!deck(x, z)) continue;
        double o = oct(x, z);
        boolean toPad = pad(x, z) && Math.abs(x) <= 5;
        if (o > R - 0.5 && !toPad) {
          for (int y = 1; y <= 2; y++) p.soft(x, y, z, WHITE_STAINED_GLASS_PANE);
          if (mezz(x, z)) for (int y = MEZZ_Y + 1; y <= MEZZ_Y + 2; y++) p.soft(x, y, z, WHITE_STAINED_GLASS_PANE);
        }
        if (!mezz(x, z)) continue;
        // inner edge of the mezzanine (and the sides of its gate opening)
        boolean inner = false;
        for (int[] d : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}})
          if (!mezz(x + d[0], z + d[1]) && deck(x + d[0], z + d[1])) inner = true;
        if (inner && Math.abs(x) != 14 || inner && Math.abs(z) > 1) {
          p.soft(x, MEZZ_Y + 1, z, WHITE_STAINED_GLASS_PANE);
          p.soft(x, MEZZ_Y + 2, z, BARRIER);
        }
      }
    }
  }

  private static void gate(HubPlan p) {
    for (int x = -8; x <= 8; x++) {
      for (int z = -22; z <= -16; z++) {
        if (deck(x, z) && z > -17) continue;
        int ax = Math.abs(x);
        Material m = ax <= 2 && Math.floorMod(z + ax, 3) == 0 ? CYAN_STAINED_GLASS : ax > 5 ? POLISHED_DEEPSLATE : GRAY_CONCRETE;
        p.set(x, 0, z, m);
        p.set(x, -1, z, m == CYAN_STAINED_GLASS ? SEA_LANTERN : POLISHED_DEEPSLATE);
        p.set(x, -2, z, DEEPSLATE_TILES);
      }
    }
    for (int z = -20; z <= -16; z++) {
      for (int y = 1; y <= 2; y++) {
        p.set(-6, y, z, WHITE_STAINED_GLASS_PANE);
        p.set(6, y, z, WHITE_STAINED_GLASS_PANE);
      }
    }
    for (int x = -6; x <= 6; x++) for (int y = 1; y <= 3; y++) p.set(x, y, -21, BARRIER);
    // Two pylons and a beam framing the gate ring (the ring itself is a display model).
    for (int sx : new int[] {-1, 1}) {
      for (int y = 1; y <= 16; y++) {
        for (int x : new int[] {sx * 7, sx * 8}) {
          for (int z = -22; z <= -21; z++) {
            Material m = y % 4 == 0 ? WHITE_CONCRETE : POLISHED_DEEPSLATE;
            if (y % 4 == 2 && x == sx * 7 && z == -21) m = CYAN_STAINED_GLASS;
            p.set(x, y, z, m);
          }
        }
      }
    }
    for (int x = -8; x <= 8; x++) {
      for (int z = -22; z <= -21; z++) {
        p.set(x, 15, z, POLISHED_DEEPSLATE);
        p.set(x, 16, z, Math.abs(x) <= 1 ? SEA_LANTERN : DEEPSLATE_TILES);
      }
    }
    for (int x : new int[] {-8, 8}) {
      p.set(x, 17, -22, LIGHTNING_ROD);
      p.set(x, 17, -21, END_ROD, "[facing=up]");
    }
  }

  /** The walkway linking the two masts high over the gate, with hangers down to the gate beam. */
  private static void skyBridge(HubPlan p) {
    int y = 24;
    for (int x = -12; x <= 12; x++) {
      p.set(x, y, -14, Math.floorMod(x, 6) == 0 ? SEA_LANTERN : POLISHED_DEEPSLATE);
      p.set(x, y, -15, POLISHED_DEEPSLATE_SLAB, "[type=bottom]");
      p.set(x, y, -13, POLISHED_DEEPSLATE_SLAB, "[type=bottom]");
      p.set(x, y + 1, -15, WHITE_STAINED_GLASS_PANE);
      p.set(x, y + 1, -13, WHITE_STAINED_GLASS_PANE);
    }
    for (int x : new int[] {-4, 4})
      for (int yy = 17; yy < y; yy++) p.set(x, yy, -14, IRON_CHAIN);
  }

  /** An antenna mast standing in the void off a deck corner, tied to the deck by a strut. */
  private static void mast(HubPlan p, int cx, int cz, int top) {
    for (int y = -8; y <= top; y++) {
      for (int dx = -1; dx <= 1; dx++) {
        for (int dz = -1; dz <= 1; dz++) {
          if (dx == 0 && dz == 0) continue;
          boolean face = dx == 0 || dz == 0;
          Material m = Math.floorMod(y, 6) == 0 ? WHITE_CONCRETE : POLISHED_DEEPSLATE;
          if (face && Math.floorMod(y, 6) == 3) m = CYAN_STAINED_GLASS;
          p.set(cx + dx, y, cz + dz, m);
        }
      }
      p.set(cx, y, cz, Math.floorMod(y, 6) == 3 ? SEA_LANTERN : POLISHED_DEEPSLATE);
    }
    for (int y = -11; y <= -9; y++) p.set(cx, y, cz, y == -11 ? END_ROD : POLISHED_DEEPSLATE, y == -11 ? "[facing=down]" : null);
    for (int dx = -2; dx <= 2; dx++)
      for (int dz = -2; dz <= 2; dz++)
        p.set(cx + dx, top + 1, cz + dz, Math.abs(dx) == 2 || Math.abs(dz) == 2 ? POLISHED_DEEPSLATE_SLAB : SEA_LANTERN);
    for (int y = top + 2; y <= top + 5; y++) p.set(cx, y, cz, y == top + 5 ? LIGHTNING_ROD : END_ROD, y == top + 5 ? null : "[facing=up]");
    p.set(cx + 2, top + 2, cz + 2, END_ROD, "[facing=up]");
    p.set(cx - 2, top + 2, cz - 2, END_ROD, "[facing=up]");
    // strut to the deck corner
    int sx = Integer.signum(cx), sz = Integer.signum(cz);
    for (int i = 2; i <= 3; i++) {
      p.set(cx - sx * i, 0, cz - sz * i, POLISHED_DEEPSLATE);
      p.set(cx - sx * i, -1, cz - sz * i, POLISHED_DEEPSLATE);
    }
  }
}
