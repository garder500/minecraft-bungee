package win.vampyuyu.valoblock;

import static org.junit.Assert.*;

import java.util.*;
import org.junit.Test;
import win.vampyuyu.valoblock.Armory.Result;

public class ArmoryTest {
  private final VbWeapons cat = VbData.weapons();
  private final VbRules rules = VbData.rules();
  private VbMatch m;
  private UUID p;
  private final Loadout lo = Loadout.starting(cat);

  private void buyPhase() {
    List<UUID> humans = new ArrayList<>();
    for (int i = 0; i < 10; i++) humans.add(UUID.randomUUID());
    m = new VbMatch(rules, humans, new Random(3));
    m.advance();
    assertEquals(VbPhase.BUY, m.vbPhase());
    p = humans.get(0);
  }

  private int credits() {
    return m.credits(p);
  }

  @Test
  public void pistolRoundStartsWith800() {
    buyPhase();
    assertEquals(800, credits());
  }

  @Test
  public void tooPoorSpendsNothing() {
    buyPhase();
    assertEquals(Result.TOO_POOR, Armory.buy(m, lo, p, cat.get("gnat"))); // 900 > 800
    assertEquals(800, credits());
    assertNull(lo.get(VbWeapon.Slot.PRIMARY));
    assertEquals(Armory.Display.LOCKED, Armory.display(m, lo, p, cat.get("gnat")));
    assertEquals(100, Armory.missing(m, lo, p, cat.get("gnat")));
  }

  @Test
  public void aGunThatFitsIsBoughtAndSoldBackInFull() {
    buyPhase();
    assertEquals(Result.BOUGHT, Armory.buy(m, lo, p, cat.get("gravebell"))); // 800 revolver replaces Pip (free)
    assertEquals(0, credits());
    assertEquals("gravebell", lo.get(VbWeapon.Slot.SIDEARM).id());
    assertEquals(Armory.Display.OWNED, Armory.display(m, lo, p, cat.get("gravebell")));
    assertEquals(Result.ALREADY_OWNED, Armory.buy(m, lo, p, cat.get("gravebell")));
    assertEquals(Result.SOLD, Armory.sell(m, lo, p, cat.get("gravebell")));
    assertEquals(800, credits());
    assertEquals("pip", lo.get(VbWeapon.Slot.SIDEARM).id()); // the free sidearm comes back
  }

  @Test
  public void freeItemsCannotBeSoldBack() {
    buyPhase();
    assertEquals(Result.NOT_SELLABLE, Armory.sell(m, lo, p, cat.get("pip")));
    assertEquals(Result.NOT_SELLABLE, Armory.sell(m, lo, p, cat.get("shard_knife")));
    assertEquals(800, credits());
  }

  @Test
  public void buyingOverAGunRefundsTheOldOne() {
    buyPhase();
    m.refund(p, 3000); // 3800
    Armory.buy(m, lo, p, cat.get("gnat")); // 900
    assertEquals(2900, credits());
    assertEquals(Result.BOUGHT, Armory.buy(m, lo, p, cat.get("mistral"))); // 1600, gnat comes back +900
    assertEquals(2900 - 1600 + 900, credits());
    assertEquals("mistral", lo.get(VbWeapon.Slot.PRIMARY).id());
  }

  @Test
  public void theRefundCountsWhenCheckingAffordability() {
    buyPhase();
    m.refund(p, 100); // 900
    assertEquals(Result.BOUGHT, Armory.buy(m, lo, p, cat.get("gnat")));
    assertEquals(0, credits());
    // 0 Shards + 900 refund covers a 900 swap but not the 1600 one
    assertEquals(Result.TOO_POOR, Armory.buy(m, lo, p, cat.get("mistral")));
    assertEquals("gnat", lo.get(VbWeapon.Slot.PRIMARY).id());
    assertEquals(0, credits());
  }

  @Test
  public void refundNeverLosesMoneyAtTheCap() {
    buyPhase();
    m.refund(p, 8200); // 9000, the cap
    Armory.buy(m, lo, p, cat.get("gnat")); // 8100
    assertEquals(Result.BOUGHT, Armory.buy(m, lo, p, cat.get("mistral")));
    assertEquals(8100 - 1600 + 900, credits());
  }

  @Test
  public void shieldsSetTheMatchShield() {
    buyPhase();
    m.refund(p, 1000);
    assertEquals(Result.BOUGHT, Armory.buy(m, lo, p, cat.get("light_ward")));
    assertEquals(25, m.shield(p));
    assertEquals(1400, credits());
    assertEquals(Result.NOT_BETTER, Armory.buy(m, lo, p, cat.get("light_ward")));
    assertEquals(Result.BOUGHT, Armory.buy(m, lo, p, cat.get("heavy_ward")));
    assertEquals(50, m.shield(p));
    assertEquals(Result.SOLD, Armory.sell(m, lo, p, cat.get("heavy_ward")));
    assertEquals(0, m.shield(p));
    assertEquals(1400 - 1000 + 1000, credits());
  }

  @Test
  public void nothingWorksOutsideTheBuyPhase() {
    buyPhase();
    m.advance(); // combat
    assertEquals(Result.NOT_BUY_PHASE, Armory.buy(m, lo, p, cat.get("gravebell")));
    assertEquals(Result.NOT_BUY_PHASE, Armory.sell(m, lo, p, cat.get("pip")));
    assertEquals(800, credits());
  }
}
