package win.vampyuyu.valoblock;

/**
 * Which abilities are placed at a distance through the tactical map, and how: how many spots the player picks, and the longest reach
 * (from the agent's own numbers, "range", when it has one, else the whole map). Pure.
 */
final class TacTargeting {
  /** How many spots an ability needs; {@code noun} names them in the title ("end", "spot"). */
  record Spec(int spots, String noun) {}

  private TacTargeting() {}

  /** The targeting of an ability id, or null when it is thrown or aimed in the world as before. */
  static Spec of(String abilityId) {
    return switch (abilityId) {
      case "sand_veil", "rot_cloud", "undertow" -> new Spec(1, "spot");
      case "miasma_wall" -> new Spec(2, "end");
      case "storm_call" -> new Spec(3, "spot");
      default -> null;
    };
  }

  /** The spots an ability needs: the number of its Spec, but Storm Call follows its own "spots" number. */
  static int spots(VbAbility ab) {
    Spec s = of(ab.id());
    if (s == null) return 0;
    return ab.id().equals("storm_call") ? (int) ab.num("spots", s.spots()) : s.spots();
  }

  /** The longest distance (blocks, on the ground plane, from the player) a spot may be at, or infinity for the whole map. */
  static double range(VbAbility ab) {
    Double r = ab.num().get("range");
    return r == null || r <= 0 ? Double.POSITIVE_INFINITY : r;
  }

  /** The longest Miasma Wall (blocks between its two ends); a longer pick is cut short along the same line. */
  static final double MAX_WALL = 12;

  /** Whether a spot at ({@code x}, {@code z}) is within reach of a player standing at ({@code px}, {@code pz}). */
  static boolean inRange(double range, double px, double pz, double x, double z) {
    return Math.hypot(x - px, z - pz) <= range;
  }

  /** The title of a step: "Sand Veil 2 left" style words are the menu's; this builds them (the charges, which spot of how many). */
  static String title(VbAbility ab, int picked, int charges) {
    Spec s = of(ab.id());
    int total = spots(ab);
    String step = total > 1 ? " " + s.noun() + " " + Math.min(picked + 1, total) + "/" + total : "";
    return ab.name() + step + "  x" + charges;
  }
}
