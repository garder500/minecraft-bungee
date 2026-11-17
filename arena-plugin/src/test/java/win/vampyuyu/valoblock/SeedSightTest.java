package win.vampyuyu.valoblock;

import static org.junit.Assert.*;

import java.util.*;
import org.junit.Test;

/** Who sees what of the Rift Seed: the HUD icon, the back display, the head badge and the dropped Seed. */
public class SeedSightTest {
  private final List<UUID> humans = new ArrayList<>();

  private VbMatch combat() {
    for (int i = 0; i < 10; i++) humans.add(UUID.randomUUID());
    VbMatch m = new VbMatch(VbData.rules(), humans, new Random(7));
    m.advance();
    m.advance();
    assertEquals(VbPhase.COMBAT, m.vbPhase());
    assertNotNull(m.seedCarrier());
    return m;
  }

  private static List<UUID> all(VbMatch m, java.util.function.Predicate<UUID> p) {
    return m.participants().stream().filter(p).toList();
  }

  @Test
  public void onlyTheCarrierGetsTheHudSeedAndItsPlantBar() {
    VbMatch m = combat();
    UUID c = m.seedCarrier();
    for (UUID id : m.participants()) {
      if (id.equals(c)) assertNotNull(SeedSight.hud(m, id, false, 0));
      else assertNull(id + " carries nothing", SeedSight.hud(m, id, true, 0));
    }
    assertEquals(0, SeedSight.hud(m, c, false, 0).plant(), 1e-9);
    assertFalse(SeedSight.hud(m, c, false, 0).inSite());
    m.holdPlant(c, VbData.sites().get(0), m.rules().plantHoldSeconds() / 2);
    assertEquals(0.5, SeedSight.hud(m, c, true, 0).plant(), 1e-6);
  }

  @Test
  public void theIconPulsesOnlyInASite() {
    VbMatch m = combat();
    UUID c = m.seedCarrier();
    assertFalse("outside a site it does not pulse", SeedSight.hud(m, c, false, 0).pulse());
    assertFalse(SeedSight.hud(m, c, false, VbHudState.PULSE_MS).pulse());
    assertTrue(SeedSight.hud(m, c, true, 0).pulse());
    assertFalse(SeedSight.hud(m, c, true, VbHudState.PULSE_MS).pulse());
    assertTrue(SeedSight.hud(m, c, true, 2 * VbHudState.PULSE_MS).pulse());
  }

  @Test
  public void theCarriersSquadSeesTheBackDisplayAndTheBadgeButNobodyElseDoes() {
    VbMatch m = combat();
    UUID c = m.seedCarrier();
    int mine = m.squadOf(c);
    for (UUID id : m.participants()) {
      boolean mate = m.squadOf(id) == mine && !id.equals(c);
      assertEquals("back display for " + (mate ? "a squad-mate" : "somebody else"), mate, SeedSight.seesBack(m, id));
    }
    assertFalse("the carrier holds the item, the back display is not for them", SeedSight.seesBack(m, c));
    // the top bar: the carrier's head wears the badge in their own squad's bar only
    assertTrue(SeedSight.badgeOnHead(m, mine, c));
    assertFalse("the enemy squad's bar has no badge", SeedSight.badgeOnHead(m, 1 - mine, c));
    for (UUID id : m.squad(mine)) if (!id.equals(c)) assertFalse("only the carrier wears it", SeedSight.badgeOnHead(m, mine, id));
  }

  @Test
  public void aSquadMateWhoLeftStopsSeeingTheBackDisplay() {
    VbMatch m = combat();
    UUID c = m.seedCarrier();
    UUID mate = m.squad(m.squadOf(c)).stream().filter(id -> !id.equals(c)).findFirst().orElseThrow();
    assertTrue(SeedSight.seesBack(m, mate));
    m.remove(mate);
    assertFalse(SeedSight.seesBack(m, mate));
  }

  @Test
  public void aDroppedSeedIsForSeedbearersOnly() {
    VbMatch m = combat();
    UUID c = m.seedCarrier();
    UUID killer = all(m, id -> m.side(id) == VbSide.SEALERS).get(0);
    m.damage(c, killer, 1000);
    assertNull("nobody carries it any more", m.seedCarrier());
    assertNull(SeedSight.carrier(m));
    for (UUID id : m.participants()) {
      assertEquals(id + " sees the dropped Seed only as a Seedbearer", m.side(id) == VbSide.SEEDBEARERS, SeedSight.seesDrop(m, id));
      assertFalse("no back display without a carrier", SeedSight.seesBack(m, id));
      assertFalse(SeedSight.badgeOnHead(m, m.squadOf(id), id));
    }
    assertTrue("a Seedbearer who died still follows the Seed", SeedSight.seesDrop(m, c));
  }

  @Test
  public void theDropFollowsTheSidesAfterTheSwap() {
    VbMatch m = combat();
    // whoever is a Seedbearer this round sees it; after the sides swap (round 7) the other squad does
    UUID someone = m.participants().iterator().next();
    VbSide before = m.side(someone);
    assertEquals(before == VbSide.SEEDBEARERS, SeedSight.seesDrop(m, someone));
    assertFalse("a stranger sees nothing", SeedSight.seesDrop(m, UUID.randomUUID()));
    assertFalse(SeedSight.seesBack(m, UUID.randomUUID()));
  }

  @Test
  public void droppingWithQKeepsTheSeedOnTheGroundForSeedbearers() {
    VbMatch m = combat();
    UUID c = m.seedCarrier();
    assertFalse("only the carrier can drop it", m.dropSeed(all(m, id -> !id.equals(c)).get(0)));
    assertTrue(m.dropSeed(c));
    assertNull(m.seedCarrier());
    assertTrue("the dropper is still alive", m.alive().contains(c));
    assertFalse("a second drop does nothing", m.dropSeed(c));
    assertTrue("a Seedbearer can pick it up again", m.pickUpSeed(c));
    assertTrue(SeedSight.carries(m, c));
    for (UUID id : m.participants()) assertEquals(id.equals(c), SeedSight.hud(m, id, false, 0) != null);
  }

  @Test
  public void plantingTakesTheSeedAwayFromEverybody() {
    VbMatch m = combat();
    UUID c = m.seedCarrier();
    assertTrue(m.holdPlant(c, VbData.sites().get(0), 100));
    assertNull(SeedSight.carrier(m));
    for (UUID id : m.participants()) {
      assertNull(SeedSight.hud(m, id, true, 0));
      assertFalse(SeedSight.seesBack(m, id));
    }
  }

  @Test
  public void aSiteIsFoundByItsBox() {
    List<VbSite> sites = VbData.sites();
    VbSite s = sites.get(0);
    double cx = (s.min()[0] + s.max()[0]) / 2, cy = s.min()[1], cz = (s.min()[2] + s.max()[2]) / 2;
    assertSame(s, SeedSight.siteAt(sites, cx, cy, cz));
    assertNull(SeedSight.siteAt(sites, cx, cy + 500, cz));
  }

  @Test
  public void theTrackerSaysWhatChangedAndWhy() {
    SeedTracker t = new SeedTracker();
    UUID a = UUID.randomUUID(), b = UUID.randomUUID();
    assertEquals(SeedTracker.Change.NONE, t.update(null, VbPhase.BUY, false));
    var gain = t.update(a, VbPhase.BUY, false);
    assertEquals(SeedTracker.Kind.GAINED, gain.kind());
    assertTrue("the title is for a gain in the buy phase", gain.roundStart());
    assertEquals(SeedTracker.Change.NONE, t.update(a, VbPhase.COMBAT, false));
    var drop = t.update(null, VbPhase.COMBAT, false);
    assertEquals(SeedTracker.Kind.DROPPED, drop.kind());
    assertEquals(a, drop.from());
    var pick = t.update(b, VbPhase.COMBAT, false);
    assertEquals(SeedTracker.Kind.GAINED, pick.kind());
    assertFalse("a pick-up in combat is not a round start", pick.roundStart());
    var plant = t.update(null, VbPhase.PLANTED, true);
    assertEquals("a plant is not a drop", SeedTracker.Kind.LOST, plant.kind());
    t.reset();
    assertNull(t.last());
    assertEquals(SeedTracker.Kind.GAINED, t.update(a, VbPhase.BUY, false).kind());
    assertEquals("a carrier who leaves in the buy phase loses it, nothing drops", SeedTracker.Kind.LOST, t.update(null, VbPhase.BUY, false).kind());
  }

  @Test
  public void theTooltipExplainsHowToPlant() {
    var lines = SeedItems.tooltip();
    assertEquals("Rift Seed", lines.get(0).text());
    String all = lines.stream().map(l -> l.text()).reduce("", (x, y) -> x + "\n" + y);
    assertTrue(all, all.contains("Hold right-click inside a site to plant"));
    assertEquals("the Seed is in hotbar slot 8, between the abilities and the Armory", 7, SeedItems.SLOT);
    assertTrue(SeedItems.SLOT > 6 && SeedItems.SLOT < VbWeaponService.SLOT_ARMORY);
  }
}
