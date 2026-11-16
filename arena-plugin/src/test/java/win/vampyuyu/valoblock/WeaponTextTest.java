package win.vampyuyu.valoblock;

import static org.junit.Assert.*;

import java.util.List;
import org.junit.Test;
import win.vampyuyu.valoblock.WeaponText.Line;

public class WeaponTextTest {
  private final VbWeapons cat = VbData.weapons();

  private List<String> texts(String id, Armory.Display d, int missing) {
    return WeaponText.tooltip(cat.get(id), d, missing).stream().map(Line::text).toList();
  }

  @Test
  public void fluxTooltipIsTheDesignsExactly() {
    assertEquals(
        List.of(
            "Flux Rifle",
            "Rifle, full auto · Sunstone + Flux",
            "Head 156 · Body 39 · Legs 33",
            "10 shots/s · 25 rounds · reload 2.5 s",
            "The recoil climbs, then drifts right.",
            "Price: 2,900 Shards",
            "Click to buy · right-click to sell back"),
        texts("flux_rifle", Armory.Display.AFFORDABLE, 0));
  }

  @Test
  public void tooltipColoursFollowTheDesign() {
    List<Line> l = WeaponText.tooltip(cat.get("flux_rifle"), Armory.Display.AFFORDABLE, 0);
    assertEquals("#ffd36a", l.get(0).color());
    assertEquals("#a8a8a8", l.get(1).color());
    assertEquals("#eef0f4", l.get(2).color());
    assertEquals("#ffd36a", l.get(5).color());
    assertEquals("#5ad07a", l.get(6).color());
  }

  @Test
  public void lockedShowsHowManyShardsAreMissing() {
    List<Line> l = WeaponText.tooltip(cat.get("watchers_eye"), Armory.Display.LOCKED, 1250);
    assertEquals("Missing 1,250 Shards", l.get(l.size() - 1).text());
    assertEquals("#ff6b6b", l.get(l.size() - 1).color());
    assertTrue(l.stream().anyMatch(x -> x.text().equals("Price: 4,500 Shards")));
  }

  @Test
  public void ownedItemsCanBeSoldBackExceptFreeOnes() {
    assertEquals("Owned · right-click to sell back", texts("gnat", Armory.Display.OWNED, 0).get(texts("gnat", Armory.Display.OWNED, 0).size() - 1));
    List<String> pip = texts("pip", Armory.Display.OWNED, 0);
    assertEquals("Free", pip.get(pip.size() - 2));
    assertEquals("Owned", pip.get(pip.size() - 1));
  }

  @Test
  public void shotgunAndShieldAndBurstLines() {
    assertEquals("Head 8×30 · Body 8×12 · Legs 8×10", texts("scrapper", Armory.Display.AFFORDABLE, 0).get(2));
    assertEquals("Shield +50", texts("heavy_ward", Armory.Display.AFFORDABLE, 0).get(2));
    assertEquals("2 bursts/s · 3 rounds each · 18 rounds · reload 1.7 s", texts("tickhand", Armory.Display.AFFORDABLE, 0).get(3));
    assertEquals("0.6 shots/s · 5 rounds · reload 3.4 s", texts("watchers_eye", Armory.Display.AFFORDABLE, 0).get(3));
  }

  @Test
  public void titleShowsTheShards() {
    assertEquals("Armory · 3,900 Shards", WeaponText.title(3900));
  }
}
