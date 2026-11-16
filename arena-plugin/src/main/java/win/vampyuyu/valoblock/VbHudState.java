package win.vampyuyu.valoblock;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import win.vampyuyu.valoblock.VbGlyphs.Glyph;

/**
 * What the glyph HUD of one player shows, as plain data in and a list of glyphs out (no Bukkit, no plugin): big health and
 * shield bottom left, four ability tiles with charge pips bottom centre, the magazine bottom right, the crosshair and the
 * hit marker in the middle. The order is the draw order (plates first, so the digits sit on them).
 */
public final class VbHudState {
  public static final int HP_LOW = 25, SHIELD_MAX = 50, MAX_PIPS = 3;

  /**
   * The values the HUD needs. {@code charges} / {@code maxCharges} hold ability slots 4 to 7 (null without an agent);
   * {@code ammo} is -1 when the hand holds no gun; {@code spread} is 0 (sneaking), 1 (still), 2 (moving), 3 (airborne);
   * {@code hit} is null, "w" (hit), "h" (head shot) or "k" (kill).
   */
  public record Input(boolean alive, int health, int shield, String agent, int[] charges, int[] maxCharges, int ammo, int magazine, int spread, String hit) {}

  private VbHudState() {}

  /** The digits of {@code value} right-aligned in {@code width} cells; leading cells are -1 (blank). */
  static int[] digits(int value, int width) {
    int v = Math.max(0, Math.min((int) Math.pow(10, width) - 1, value));
    int[] out = new int[width];
    for (int i = width - 1; i >= 0; i--) {
      out[i] = v > 0 || i == width - 1 ? v % 10 : -1;
      v /= 10;
    }
    return out;
  }

  private static void number(List<Glyph> out, String prefix, String sprites, int value, int width) {
    int[] d = digits(value, width);
    for (int i = 0; i < width; i++) out.add(new Glyph(prefix + (i + 1), d[i] < 0 ? sprites.substring(0, 3) + "blank" : sprites + d[i], 0));
  }

  /** "18 / 50" to {18, 50}, or null. */
  public static int[] parseAmmo(String text) {
    if (text == null) return null;
    String[] p = text.split("/");
    if (p.length != 2) return null;
    try {
      return new int[] {Integer.parseInt(p[0].trim()), Integer.parseInt(p[1].trim())};
    } catch (NumberFormatException e) {
      return null;
    }
  }

  /** The crosshair level from what the player is doing. */
  public static int spreadLevel(boolean sneaking, boolean moving, boolean airborne) {
    return airborne ? 3 : sneaking ? 0 : moving ? 2 : 1;
  }

  /** Ability slots 4 to 7 as (max charges) for the pips: buyable abilities carry up to maxCharges, the signature its own, the ultimate 1. */
  public static int maxCharges(VbAbility ab) {
    return switch (ab.kind()) {
      case BUY -> ab.maxCharges();
      case SIGNATURE -> ab.charges();
      case ULTIMATE -> 1;
    };
  }

  public static List<Glyph> glyphs(Input in) {
    List<Glyph> out = new ArrayList<>();
    if (!in.alive()) return out;
    // Bottom left: health (digits and bar), shield.
    boolean low = in.health() <= HP_LOW;
    out.add(new Glyph("hp_plate", "hp_plate", 0));
    number(out, "hp", low ? "bd_r_" : "bd_w_", in.health(), 3);
    out.add(new Glyph("hp_bar", low ? "hp_bar_low" : "hp_bar", (int) Math.round(Math.max(0, Math.min(100, in.health())) * 255 / 100.0)));
    if (in.shield() > 0) {
      number(out, "sh", "sd_b_", in.shield(), 3);
      out.add(new Glyph("sh_bar", "sh_bar", (int) Math.round(Math.min(SHIELD_MAX, in.shield()) * 255 / (double) SHIELD_MAX)));
    }
    // Bottom centre: four ability tiles, their icons and the charge pips.
    if (in.agent() != null && in.charges() != null && in.charges().length >= 4) {
      for (int i = 0; i < 4; i++) {
        int slot = 4 + i;
        String state = in.charges()[i] > 0 ? "on" : "off";
        out.add(new Glyph("tile" + i, "tile_" + slot + "_" + state, 0));
      }
      for (int i = 0; i < 4; i++) {
        int slot = 4 + i;
        out.add(new Glyph("icon" + i, "ic_" + in.agent().toLowerCase(Locale.ROOT) + "_" + slot + "_" + (in.charges()[i] > 0 ? "on" : "off"), 0));
      }
      for (int i = 0; i < 3; i++) { // the ultimate has no pips
        int max = in.maxCharges() == null || in.maxCharges().length < 4 ? 1 : Math.max(1, Math.min(MAX_PIPS, in.maxCharges()[i]));
        for (int j = 0; j < max; j++) out.add(new Glyph("pip" + i + "_" + j, j < in.charges()[i] ? "pip_on" : "pip_off", 0));
      }
    }
    // Bottom right: the magazine and its size.
    if (in.ammo() >= 0) {
      boolean empty = in.ammo() * 5 <= Math.max(1, in.magazine());
      out.add(new Glyph("am_plate", "am_plate", 0));
      number(out, "am", empty ? "bd_r_" : "bd_w_", in.ammo(), 2);
      number(out, "rs", "sd_g_", in.magazine(), 3);
    }
    // The middle: crosshair, then the hit marker on top.
    out.add(new Glyph("xh", "xh_" + Math.max(0, Math.min(3, in.spread())), 0));
    if (in.hit() != null) out.add(new Glyph("hit", "hit_" + in.hit(), 0));
    return out;
  }
}
