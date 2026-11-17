package win.vampyuyu.valoblock;

import static org.junit.Assert.*;

import org.junit.Test;

public class OutfitTest {
  private static String[] empty() {
    return new String[4];
  }

  private static boolean mine(String s) {
    return s.startsWith("outfit:");
  }

  @Test
  public void fillsEveryEmptySlotInArmourOrder() {
    String[] armor = empty();
    assertEquals(4, Outfit.fill(armor, p -> "outfit:" + p, s -> s == null));
    assertArrayEquals(new String[] {"outfit:FEET", "outfit:LEGS", "outfit:CHEST", "outfit:HEAD"}, armor);
  }

  @Test
  public void theShieldAndTheScopeKeepTheirSlotAndTheOutfitFillsTheRest() {
    String[] armor = empty();
    armor[2] = "shield";
    armor[3] = "scope";
    assertEquals(2, Outfit.fill(armor, p -> "outfit:" + p, s -> s == null));
    assertArrayEquals(new String[] {"outfit:FEET", "outfit:LEGS", "shield", "scope"}, armor);
    armor[3] = null; // the scope goes off: the hood comes back, nothing else moves
    assertEquals(1, Outfit.fill(armor, p -> "outfit:" + p, s -> s == null));
    assertEquals("outfit:HEAD", armor[3]);
    assertEquals("shield", armor[2]);
  }

  @Test
  public void fillingTwiceAddsNothing() {
    String[] armor = empty();
    Outfit.fill(armor, p -> "outfit:" + p, s -> s == null);
    assertEquals(0, Outfit.fill(armor, p -> "outfit:" + p, s -> s == null));
  }

  @Test
  public void stripRemovesOnlyOutfitPiecesWherever() {
    String[] inv = {"outfit:HEAD", "rifle", null, "outfit:CHEST", "shield"};
    assertEquals(2, Outfit.strip(inv, OutfitTest::mine));
    assertArrayEquals(new String[] {null, "rifle", null, null, "shield"}, inv);
    assertEquals(0, Outfit.strip(inv, OutfitTest::mine));
  }

  @Test
  public void everyPieceHasALabelAndTheAssetIdIsPerAgent() {
    assertEquals(4, Outfit.Piece.values().length);
    for (Outfit.Piece p : Outfit.Piece.values()) assertFalse(p.label.isEmpty());
    assertEquals("valoblock_cinder", Outfit.assetId("cinder"));
    assertNotEquals(Outfit.assetId("gale"), Outfit.assetId("murk"));
  }
}
