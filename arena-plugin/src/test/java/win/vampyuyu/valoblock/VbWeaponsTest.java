package win.vampyuyu.valoblock;

import static org.junit.Assert.*;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.*;
import org.junit.Test;

public class VbWeaponsTest {
  private final VbWeapons cat = VbData.weapons();

  private VbWeapon w(String id) {
    return Objects.requireNonNull(cat.get(id), id);
  }

  @Test
  public void catalogueMatchesTheDesign() {
    assertEquals(15, cat.all().size()); // 13 weapons incl. the knife + 2 shields
    assertEquals(2, cat.all().stream().filter(VbWeapon::isShield).count());
    VbWeapon flux = w("flux_rifle");
    assertEquals(2900, flux.price());
    assertEquals(156, flux.head());
    assertEquals(39, flux.body());
    assertEquals(33, flux.legs()); // armory tooltip: Legs 33
    assertEquals(10.0, flux.rate(), 0);
    assertEquals(25, flux.magazine());
    assertEquals(2.5, flux.reload(), 0);
    assertEquals(8, w("scrapper").pellets());
    assertEquals(30, w("scrapper").head());
    assertEquals(3, w("tickhand").burst());
    assertEquals(255, w("watchers_eye").head());
    assertEquals(25, w("light_ward").shield());
    assertEquals(50, w("heavy_ward").shield());
    assertEquals(150, w("shard_knife").backstab());
    assertEquals("pip", cat.defaultSidearm().id());
    assertTrue(cat.defaultSidearm().free());
  }

  @Test
  public void priceTiersFollowTheDesign() {
    int[] expected = {0, 500, 800, 0, 900, 1600, 1000, 1850, 2100, 2900, 1100, 4500, 3200, 400, 1000};
    List<VbWeapon> all = cat.all();
    for (int i = 0; i < expected.length; i++) assertEquals(all.get(i).id(), expected[i], all.get(i).price());
  }

  @Test
  public void menuCellsFollowTheArmoryColumns() {
    // columns 1 and 4 are spacers, and row 6 (index 5) is for abilities
    for (VbWeapon w : cat.all()) {
      if (w.menuRow() < 0) continue;
      assertNotEquals(w.id(), 1, w.menuCol());
      assertNotEquals(w.id(), 4, w.menuCol());
      assertTrue(w.menuRow() <= 2);
    }
    assertEquals(0, w("pip").menuCol());
    assertEquals(2, w("gnat").menuCol());
    assertEquals(5, w("flux_rifle").menuCol());
    assertEquals(8, w("heavy_ward").menuCol());
  }

  @Test
  public void aBrokenFileIsRefused() {
    JsonObject j = JsonParser.parseString("{\"weapons\":[{\"id\":\"x\",\"name\":\"X\",\"kind\":\"k\",\"metal\":\"m\",\"metalName\":\"m\",\"texture\":\"pistol\",\"slot\":\"primary\",\"note\":\"n\",\"magazine\":0}],\"defaultSidearm\":\"x\",\"melee\":\"x\"}").getAsJsonObject();
    assertThrows(IllegalArgumentException.class, () -> VbWeapons.parse(j));
  }
}
