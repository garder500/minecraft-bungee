package win.vampyuyu.valoblock;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;

/**
 * The window titles of the ValoBlock menus: a run of font glyphs that paints the whole 176 x 222 window
 * (the MenuSkin tile technique, font vampyuyu:vbmenus of the ValoBlock pack), optionally a portrait glyph, then the title
 * words. Every constant mirrors resource-pack/vb_layout.py; scripts/test_pack.py and VbMenuSkinTest keep them in step.
 */
public final class VbMenuSkin {
  public static final Key FONT = Key.key("vampyuyu", "vbmenus");
  public static final int COLS = 4, ROWS = 6, TILE_W = 44, ROW_WIDTH = COLS * (TILE_W + 1) - (COLS - 1);
  public static final char SELECT = '\uE100', ARMORY = '\uE200', PORTRAIT_BASE = '\uE300', PORTRAIT_NONE = '\uE3FF';
  public static final char SHIFT_BACK = '\uE000', ROW_BACK = '\uE001', SEAM = '\uE002';
  public static final char TO_PORTRAIT = '\uE010', FROM_PORTRAIT = '\uE011', TO_TEXT = '\uE012';
  public static final int PORTRAIT_W = 48, PORTRAIT_X = 10, TEXT_X = 8;
  /** The tactical map: one shared chrome, then one plan glyph over it (the whole map, or a zone zoomed), see vb_layout.py. */
  public static final char TAC_CHROME = '\uE400', TAC_PLAN = '\uE500', TAC_ZONE = '\uE600', TO_PLAN = '\uE013', FROM_PLAN = '\uE014';
  public static final int PLAN_X = 43, PLAN_W = 90;
  /** The map vote: a background with the three maps baked in, then the same with a gold ring round map 0, 1 or 2 (VOTE_MAP + 0x100 * map). */
  public static final char VOTE_NONE = '\uE700', VOTE_MAP = '\uE800';

  /** The advance of each space glyph, as the font declares it (a test compares these with the pack). */
  public static int advance(char space) {
    return switch (space) {
      case SHIFT_BACK -> -8;
      case ROW_BACK -> -ROW_WIDTH;
      case SEAM -> -1;
      case TO_PORTRAIT -> PORTRAIT_X - ROW_WIDTH;
      case FROM_PORTRAIT -> TEXT_X - (PORTRAIT_X + PORTRAIT_W + 1);
      case TO_TEXT -> TEXT_X - ROW_WIDTH;
      case TO_PLAN -> PLAN_X - ROW_WIDTH;
      case FROM_PLAN -> TEXT_X - (PLAN_X + PLAN_W + 1);
      default -> throw new IllegalArgumentException("not a space glyph");
    };
  }

  private VbMenuSkin() {}

  /** The tile run of one background plus the cursor moves that land on the title text; {@code portrait} is a glyph or 0 for none. */
  public static String glyphs(char base, char portrait) {
    StringBuilder s = new StringBuilder().append(SHIFT_BACK);
    for (int r = 0; r < ROWS; r++) {
      for (int c = 0; c < COLS; c++) {
        s.append((char) (base + r * COLS + c));
        if (c < COLS - 1) s.append(SEAM);
      }
      if (r < ROWS - 1) s.append(ROW_BACK);
    }
    if (portrait == 0) return s.append(TO_TEXT).toString();
    return s.append(TO_PORTRAIT).append(portrait).append(FROM_PORTRAIT).toString();
  }

  /** The cursor position (GUI px from the window's left edge; the title origin is x = 8) after the glyph run: the title text starts here. */
  public static int cursorAfter(char base, char portrait) {
    return cursorAfter(glyphs(base, portrait), portrait);
  }

  /** The same for any run: {@code wide} is the one glyph that is not a tile (the portrait or the plan). */
  public static int cursorAfter(String run, char wide) {
    int x = TEXT_X; // a window title starts at x = 8
    for (char c : run.toCharArray()) {
      boolean space = c == SHIFT_BACK || c == ROW_BACK || c == SEAM || c == TO_PORTRAIT || c == FROM_PORTRAIT || c == TO_TEXT || c == TO_PLAN || c == FROM_PLAN;
      if (space) x += advance(c);
      else x += (c == wide ? (wide >= TAC_PLAN ? PLAN_W : PORTRAIT_W) : TILE_W) + 1; // every bitmap glyph advances its width + 1
    }
    return x;
  }

  /** The glyph of the plan image over the chrome: the whole map of map number {@code map} ({@code zone} below 0), or one of its 25 zones. */
  public static char planGlyph(int map, int zone) {
    return zone < 0 ? (char) (TAC_PLAN + map) : (char) (TAC_ZONE + map * 25 + zone);
  }

  /** The tile run of the tactical map's chrome, then the plan glyph, then the cursor back at the title origin. */
  public static String tacGlyphs(char plan) {
    String chrome = glyphs(TAC_CHROME, (char) 0);
    return chrome.substring(0, chrome.length() - 1) + TO_PLAN + plan + FROM_PLAN;
  }

  /** The tactical map window: the whole map (zone below 0) or a zone of it, and the title words (the ability, which spot, the charges). */
  public static Component tacMap(int map, int zone, String title) {
    return compose(tacGlyphs(planGlyph(map, zone)), title);
  }

  /** The agent select window: the background, the picked agent's portrait (or the silhouette) and the title. */
  public static Component select(int portraitIndex, String title) {
    char portrait = portraitIndex < 0 ? PORTRAIT_NONE : (char) (PORTRAIT_BASE + portraitIndex);
    return compose(glyphs(SELECT, portrait), title);
  }

  /** The map vote window: {@code picked} is the map index the player voted for, or -1 for none. */
  public static Component vote(int picked, String title) {
    return compose(glyphs(picked < 0 ? VOTE_NONE : (char) (VOTE_MAP + 0x100 * picked), (char) 0), title);
  }

  public static Component armory(String title) {
    return compose(glyphs(ARMORY, (char) 0), title);
  }

  private static Component compose(String run, String title) {
    return Component.text()
        .append(Component.text(run, NamedTextColor.WHITE).font(FONT))
        .append(Component.text(title, TextColor.color(0xffffff)))
        .build();
  }
}
