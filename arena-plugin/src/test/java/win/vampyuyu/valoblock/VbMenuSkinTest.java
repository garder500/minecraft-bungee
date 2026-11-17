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

  @Test
  public void theTacticalMapIsTheSharedChromeThenOnePlanGlyphThenTheTitle() {
    for (int zone : new int[] {-1, 0, 12, 24}) {
      char plan = VbMenuSkin.planGlyph(2, zone);
      String run = VbMenuSkin.tacGlyphs(plan);
      assertEquals(VbMenuSkin.TEXT_X, VbMenuSkin.cursorAfter(run, plan));
      assertEquals(VbMenuSkin.FROM_PLAN, run.charAt(run.length() - 1));
      assertEquals(plan, run.charAt(run.length() - 2));
      assertEquals(VbMenuSkin.TO_PLAN, run.charAt(run.length() - 3));
      long tiles = run.chars().filter(c -> c >= VbMenuSkin.TAC_CHROME && c < VbMenuSkin.TAC_CHROME + 24).count();
      assertEquals(24, tiles);
    }
    assertEquals('\uE502', VbMenuSkin.planGlyph(2, -1));
    assertEquals('\uE600', VbMenuSkin.planGlyph(0, 0));
    assertEquals('\uE64A', VbMenuSkin.planGlyph(2, 24));
    assertEquals(VbMenuSkin.PLAN_X - VbMenuSkin.ROW_WIDTH, VbMenuSkin.advance(VbMenuSkin.TO_PLAN));
    assertEquals(VbMenuSkin.TEXT_X - (VbMenuSkin.PLAN_X + VbMenuSkin.PLAN_W + 1), VbMenuSkin.advance(VbMenuSkin.FROM_PLAN));
    Component title = VbMenuSkin.tacMap(0, -1, "Sand Veil  x2");
    assertEquals(VbMenuSkin.FONT, ((TextComponent) title.children().get(0)).font());
    assertEquals("Sand Veil  x2", ((TextComponent) title.children().get(1)).content());
  }

  @Test
  public void theVoteWindowIsAFullBackgroundPerChoiceThenTheTitle() {
    for (int picked = -1; picked < 3; picked++) {
      Component title = VbMenuSkin.vote(picked, "Vote for the next map");
      String run = ((TextComponent) title.children().get(0)).content();
      char base = picked < 0 ? VbMenuSkin.VOTE_NONE : (char) (VbMenuSkin.VOTE_MAP + 0x100 * picked);
      assertEquals(VbMenuSkin.TEXT_X, VbMenuSkin.cursorAfter(run, (char) 0));
      assertEquals(24, run.chars().filter(c -> c >= base && c < base + 24).count());
      assertEquals(VbMenuSkin.FONT, ((TextComponent) title.children().get(0)).font());
    }
  }
}
