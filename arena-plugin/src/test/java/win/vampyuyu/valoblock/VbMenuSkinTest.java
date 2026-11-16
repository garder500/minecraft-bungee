package win.vampyuyu.valoblock;

import static org.junit.Assert.*;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import org.junit.Test;

public class VbMenuSkinTest {
  @Test
  public void theGlyphRunLandsTheTitleTextAtTheTitleOrigin() {
    assertEquals(VbMenuSkin.TEXT_X, VbMenuSkin.cursorAfter(VbMenuSkin.ARMORY, (char) 0));
    assertEquals(VbMenuSkin.TEXT_X, VbMenuSkin.cursorAfter(VbMenuSkin.SELECT, VbMenuSkin.PORTRAIT_BASE));
    assertEquals(VbMenuSkin.TEXT_X, VbMenuSkin.cursorAfter(VbMenuSkin.SELECT, VbMenuSkin.PORTRAIT_NONE));
  }

  @Test
  public void aRowIsOneHundredSeventySevenGuiPixelsWide() {
    assertEquals(177, VbMenuSkin.ROW_WIDTH);
    assertEquals(-177, VbMenuSkin.advance(VbMenuSkin.ROW_BACK));
  }

  @Test
  public void theBackgroundIsFourTilesBySixRows() {
    String run = VbMenuSkin.glyphs(VbMenuSkin.SELECT, (char) 0);
    long tiles = run.chars().filter(c -> c >= VbMenuSkin.SELECT && c < VbMenuSkin.SELECT + 24).count();
    assertEquals(24, tiles);
    assertEquals(5, run.chars().filter(c -> c == VbMenuSkin.ROW_BACK).count());
    assertEquals(18, run.chars().filter(c -> c == VbMenuSkin.SEAM).count());
    assertEquals(VbMenuSkin.SHIFT_BACK, run.charAt(0));
    assertEquals(VbMenuSkin.TO_TEXT, run.charAt(run.length() - 1));
  }

  @Test
  public void theSelectTitleCarriesTheAgentsPortraitGlyphOnTheMenuFont() {
    Component title = VbMenuSkin.select(3, "Choose your agent");
    TextComponent glyphs = (TextComponent) title.children().get(0);
    assertEquals(VbMenuSkin.FONT, glyphs.font());
    assertTrue(glyphs.content().indexOf((char) (VbMenuSkin.PORTRAIT_BASE + 3)) > 0);
    assertTrue(VbMenuSkin.select(-1, "").children().get(0) instanceof TextComponent t && t.content().indexOf(VbMenuSkin.PORTRAIT_NONE) > 0);
    assertEquals("Choose your agent", ((TextComponent) title.children().get(1)).content());
  }
}
