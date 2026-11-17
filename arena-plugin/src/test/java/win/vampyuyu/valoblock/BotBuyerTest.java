package win.vampyuyu.valoblock;

import static org.junit.Assert.*;

import java.util.*;
import org.junit.Test;

public class BotBuyerTest {
  private final BotConfig cfg = VbData.bots();
  private final VbWeapons cat = VbData.weapons();

  /** A 1 human match in its first buy phase: the bots are filled in. */
  private VbMatch buyPhase() {
    VbMatch m = new VbMatch(VbData.rules(), List.of(UUID.randomUUID()), new Random(3));
    m.advance();
    assertEquals(VbPhase.BUY, m.vbPhase());
    return m;
  }

  private UUID aBot(VbMatch m) {
    return m.bots().stream().sorted().findFirst().orElseThrow();
  }

  /** Plays a round with the first squad winning, back to the next buy phase. */
  private void nextBuy(VbMatch m) {
    m.advance(); // combat
    m.forceRound(0);
    m.advance(); // next buy
    assertEquals(VbPhase.BUY, m.vbPhase());
  }

  @Test
  public void pistolRoundBuysTheLightShieldAndKeepsPip() {
    VbMatch m = buyPhase();
    UUID bot = aBot(m);
    Loadout lo = Loadout.starting(cat);
    BotBuyer.Outcome o = BotBuyer.buy(m, bot, lo, cfg, cat);
    assertFalse(o.fullBuy());
    assertEquals(List.of(cat.get("light_ward")), o.bought());
    assertEquals(25, m.shield(bot));
    assertEquals(800 - 400, m.credits(bot));
    assertEquals("pip", lo.get(VbWeapon.Slot.SIDEARM).id());
    assertNull(lo.get(VbWeapon.Slot.PRIMARY));
  }

  @Test
  public void fullBuyAtExactly3900IsTheFluxRifleAndTheHeavyShield() {
    VbMatch m = buyPhase();
    UUID bot = aBot(m);
    m.refund(bot, 3900 - m.credits(bot)); // exactly the threshold (the pistol round is not a full buy: play on)
    nextBuy(m);
    // Round 2: a pistol-round loser would have 800 + bonus; set the Shards to the threshold again.
    m.refund(bot, 3900 - m.credits(bot));
    if (m.credits(bot) > 3900) m.buy(bot, m.credits(bot) - 3900);
    assertEquals(3900, m.credits(bot));
    Loadout lo = Loadout.starting(cat);
    BotBuyer.Outcome o = BotBuyer.buy(m, bot, lo, cfg, cat);
    assertTrue(o.fullBuy());
    assertEquals("flux_rifle", lo.get(VbWeapon.Slot.PRIMARY).id());
    assertEquals(50, m.shield(bot));
    assertEquals(0, m.credits(bot));
  }

  @Test
  public void justUnderTheThresholdSaves() {
    VbMatch m = buyPhase();
    UUID bot = aBot(m);
    nextBuy(m);
    m.refund(bot, 3899 - m.credits(bot));
    if (m.credits(bot) > 3899) m.buy(bot, m.credits(bot) - 3899);
    assertEquals(3899, m.credits(bot));
    Loadout lo = Loadout.starting(cat);
    BotBuyer.Outcome o = BotBuyer.buy(m, bot, lo, cfg, cat);
    assertFalse(o.fullBuy());
    assertNull(lo.get(VbWeapon.Slot.PRIMARY));
    assertEquals(25, m.shield(bot)); // Pip and a light shield
    assertEquals(3899 - 400, m.credits(bot));
  }

  @Test
  public void savingBelowTheShieldFloorBuysNothing() {
    VbMatch m = buyPhase();
    UUID bot = aBot(m);
    nextBuy(m);
    if (m.credits(bot) > 1000) m.buy(bot, m.credits(bot) - 1000);
    Loadout lo = Loadout.starting(cat);
    BotBuyer.Outcome o = BotBuyer.buy(m, bot, lo, cfg, cat);
    assertTrue(o.bought().isEmpty());
    assertEquals(0, m.shield(bot));
  }

  @Test
  public void aSurvivorWithARifleOnlyTopsUpItsShield() {
    VbMatch m = buyPhase();
    UUID bot = aBot(m);
    nextBuy(m);
    Loadout lo = Loadout.starting(cat);
    lo.set(cat.get("flux_rifle"));
    if (m.credits(bot) > 1200) m.buy(bot, m.credits(bot) - 1200);
    int before = m.credits(bot);
    BotBuyer.Outcome o = BotBuyer.buy(m, bot, lo, cfg, cat);
    assertTrue(o.fullBuy());
    assertEquals(List.of(cat.get("heavy_ward")), o.bought());
    assertEquals(before - 1000, m.credits(bot));
    assertEquals("flux_rifle", lo.get(VbWeapon.Slot.PRIMARY).id());
  }

  @Test
  public void withTheShieldDoneItNeverBuysTwice() {
    VbMatch m = buyPhase();
    UUID bot = aBot(m);
    Loadout lo = Loadout.starting(cat);
    BotBuyer.buy(m, bot, lo, cfg, cat);
    int after = m.credits(bot);
    assertTrue(BotBuyer.buy(m, bot, lo, cfg, cat).bought().isEmpty());
    assertEquals(after, m.credits(bot));
  }

  @Test
  public void buyingOutsideTheBuyPhaseDoesNothing() {
    VbMatch m = buyPhase();
    UUID bot = aBot(m);
    m.advance(); // combat
    Loadout lo = Loadout.starting(cat);
    assertTrue(BotBuyer.buy(m, bot, lo, cfg, cat).bought().isEmpty());
  }

  @Test
  public void fullBuyAlsoSpendsTheLeftoverOnAbilityCharges() {
    VbMatch m = buyPhase();
    UUID bot = aBot(m);
    AgentKit kit = new AgentKit(VbData.agents().get(m.agent(bot)), VbData.agents().ultimate());
    nextBuy(m);
    m.refund(bot, 9000 - m.credits(bot));
    Loadout lo = Loadout.starting(cat);
    BotBuyer.Outcome o = BotBuyer.buy(m, bot, lo, cfg, cat);
    assertTrue(o.fullBuy());
    int n = BotBuyer.buyAbilities(m, bot, kit);
    assertEquals(2, n);
    assertEquals(1, kit.charges(4));
    assertEquals(1, kit.charges(5));
  }
}
