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

  /** The advance of each space glyph, as the font declares it (a test compares these with the pack). */
  public static int advance(char space) {
    return switch (space) {
      case SHIFT_BACK -> -8;
      case ROW_BACK -> -ROW_WIDTH;
      case SEAM -> -1;
      case TO_PORTRAIT -> PORTRAIT_X - ROW_WIDTH;
      case FROM_PORTRAIT -> TEXT_X - (PORTRAIT_X + PORTRAIT_W + 1);
      case TO_TEXT -> TEXT_X - ROW_WIDTH;
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
    int x = TEXT_X; // a window title starts at x = 8
    for (char c : glyphs(base, portrait).toCharArray()) {
      boolean space = c == SHIFT_BACK || c == ROW_BACK || c == SEAM || c == TO_PORTRAIT || c == FROM_PORTRAIT || c == TO_TEXT;
      if (space) x += advance(c);
      else x += (c == portrait ? PORTRAIT_W : TILE_W) + 1; // every bitmap glyph advances its width + 1
    }
    return x;
  }

  /** The agent select window: the background, the picked agent's portrait (or the silhouette) and the title. */
  public static Component select(int portraitIndex, String title) {
    char portrait = portraitIndex < 0 ? PORTRAIT_NONE : (char) (PORTRAIT_BASE + portraitIndex);
    return compose(glyphs(SELECT, portrait), title);
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
