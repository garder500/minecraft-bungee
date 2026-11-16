package win.vampyuyu.valoblock;

import static org.junit.Assert.*;

import java.util.*;
import org.junit.Test;

public class BotBrainTest {
  private final Waypoints g = VbData.waypoints();
  private final BotConfig cfg = VbData.bots();
  private final UUID enemy = UUID.randomUUID();

  private BotBrain brain(String level, String spawn) {
    BotBrain b = new BotBrain(UUID.randomUUID(), cfg, cfg.level(level), g);
    Waypoints.Node n = g.node(spawn);
    b.reset(n.x(), n.y(), n.z());
    return b;
  }

  private BotBrain.View view(VbPhase phase, VbSide side, boolean released, String attackSite, String anchor, List<BotBrain.Seen> seen) {
    return new BotBrain.View(phase, side, false, null, false, null, attackSite, released, null, anchor, false, seen, false, false, false);
  }

  private BotBrain.Seen at(double x, double z) {
    return new BotBrain.Seen(enemy, x, 65, z, false);
  }

  @Test
  public void nothingHappensOutsideCombat() {
    BotBrain b = brain("veteran", "as1");
    double x = b.mover().x(), z = b.mover().z();
    BotBrain.Act a = b.think(0, 0.05, view(VbPhase.BUY, VbSide.SEEDBEARERS, true, "A", null, List.of()));
    assertFalse(a.fire);
    assertEquals(x, b.mover().x(), 1e-9);
    assertEquals(z, b.mover().z(), 1e-9);
  }

  @Test
  public void aBotFiresOnlyAfterItsReactionTime() {
    for (String level : List.of("recruit", "veteran", "elite")) {
      BotBrain b = brain(level, "ds1");
      int reaction = cfg.level(level).reactionMs();
      BotBrain.View v = view(VbPhase.COMBAT, VbSide.SEALERS, true, null, "a_anchor1", List.of(at(b.mover().x(), b.mover().z() - 20)));
      long t = 10_000;
      assertFalse(level, b.think(t, 0.05, v).fire); // first sight
      for (long at = t + 50; at < t + reaction; at += 50) assertFalse(level + " at " + (at - t), b.think(at, 0.05, v).fire); // watched every tick
      assertTrue(level, b.think(t + reaction, 0.05, v).fire);
    }
  }

  @Test
  public void aBriefGlanceLostForALongTimeRestartsTheClock() {
    BotBrain b = brain("veteran", "ds1");
    BotBrain.View seen = view(VbPhase.COMBAT, VbSide.SEALERS, true, null, "a_anchor1", List.of(at(b.mover().x(), b.mover().z() - 20)));
    BotBrain.View gone = view(VbPhase.COMBAT, VbSide.SEALERS, true, null, "a_anchor1", List.of());
    for (long t = 0; t < 300; t += 50) b.think(t, 0.05, seen);
    assertTrue(b.think(300, 0.05, seen).fire);
    for (long t = 350; t < 700; t += 50) b.think(t, 0.05, gone); // lost for longer than the grace
    assertFalse(b.think(700, 0.05, seen).fire);
    for (long t = 750; t < 1000; t += 50) assertFalse(b.think(t, 0.05, seen).fire);
    assertTrue(b.think(1000, 0.05, seen).fire);
  }

  @Test
  public void inContactTheBotStandsStill() {
    BotBrain b = brain("elite", "ds1");
    double z = b.mover().z();
    BotBrain.View v = view(VbPhase.COMBAT, VbSide.SEALERS, true, null, "a_anchor1", List.of(at(b.mover().x(), z - 15)));
    for (int i = 0; i < 20; i++) b.think(1000 + i * 50, 0.05, v);
    assertEquals(z, b.mover().z(), 1e-9);
  }

  @Test
  public void aBotAimsAtTheEnemy() {
    BotBrain b = brain("elite", "ds1");
    BotBrain.Act a = b.think(0, 0.05, view(VbPhase.COMBAT, VbSide.SEALERS, true, null, "a_anchor1", List.of(at(b.mover().x(), b.mover().z() - 15))));
    assertEquals(180, Math.abs(a.yaw), 1.0); // enemy at -z: Minecraft yaw 180
  }

  @Test
  public void attackersWaitAtTheStageUntilReleasedThenPush() {
    BotBrain b = brain("veteran", "as1");
    Waypoints.Node stage = g.node(g.site("A").stage());
    long t = 0;
    for (int i = 0; i < 2000; i++) { // 100 s: far more than the walk
      b.think(t += 50, 0.05, view(VbPhase.COMBAT, VbSide.SEEDBEARERS, false, "A", null, List.of()));
      if (b.mover().arrived() && b.mover().distanceTo(stage.x(), stage.z()) < 0.01) break;
    }
    assertEquals(0, b.mover().distanceTo(stage.x(), stage.z()), 0.01);
    double x = b.mover().x(), z = b.mover().z();
    b.think(t += 50, 0.05, view(VbPhase.COMBAT, VbSide.SEEDBEARERS, false, "A", null, List.of()));
    assertEquals("stays at the stage", x, b.mover().x(), 1e-9);
    assertEquals(z, b.mover().z(), 1e-9);
    Waypoints.Node anchor = g.node(g.site("A").anchors().get(0));
    for (int i = 0; i < 2000; i++) b.think(t += 50, 0.05, view(VbPhase.COMBAT, VbSide.SEEDBEARERS, true, "A", null, List.of()));
    boolean atAnAnchor = g.site("A").anchors().stream().anyMatch(a -> b.mover().distanceTo(g.node(a).x(), g.node(a).z()) < 0.05);
    assertTrue("released: walks onto the site", atAnAnchor);
    assertNotNull(anchor);
  }

  @Test
  public void theCarrierPlantsOnTheSite() {
    BotBrain b = brain("veteran", "as1");
    Waypoints.Node plant = g.node(g.site("A").plant());
    b.mover().teleport(plant.x(), plant.y(), plant.z());
    BotBrain.View v = new BotBrain.View(VbPhase.COMBAT, VbSide.SEEDBEARERS, true, null, false, null, "A", true, null, null, true, List.of(), false, false, false);
    BotBrain.Act a = b.think(0, 0.05, v);
    assertTrue(a.holdPlant);
  }

  @Test
  public void notEveryoneCarryingTheSeedPlants() {
    BotBrain b = brain("veteran", "as1");
    Waypoints.Node plant = g.node(g.site("A").plant());
    b.mover().teleport(plant.x(), plant.y(), plant.z());
    BotBrain.View v = new BotBrain.View(VbPhase.COMBAT, VbSide.SEEDBEARERS, false, null, false, null, "A", true, null, null, true, List.of(), false, false, false);
    assertFalse(b.think(0, 0.05, v).holdPlant);
  }

  @Test
  public void aSealerHeadsForThePlantedSeedAndSealsNearIt() {
    BotBrain b = brain("veteran", "ds1");
    BotBrain.View v = new BotBrain.View(VbPhase.PLANTED, VbSide.SEALERS, false, null, false, "A", null, true, "B", "b_anchor1", false, List.of(), false, false, false);
    boolean sealed = false;
    long t = 0;
    for (int i = 0; i < 4000 && !sealed; i++) sealed = b.think(t += 50, 0.05, v).holdSeal;
    assertTrue(sealed);
    Waypoints.Node plant = g.node(g.site("A").plant());
    assertTrue(b.mover().distanceTo(plant.x(), plant.z()) <= cfg.sealRadius() + 0.01);
  }

  @Test
  public void defendersHoldTheirAnchor() {
    BotBrain b = brain("veteran", "ds1");
    BotBrain.View v = view(VbPhase.COMBAT, VbSide.SEALERS, true, null, "b_anchor2", List.of());
    long t = 0;
    for (int i = 0; i < 4000; i++) b.think(t += 50, 0.05, v);
    Waypoints.Node n = g.node("b_anchor2");
    assertEquals(0, b.mover().distanceTo(n.x(), n.z()), 0.05);
  }

  @Test
  public void aDefenderRotatesWhenItsAnchorChanges() {
    BotBrain b = brain("veteran", "ds1");
    long t = 0;
    for (int i = 0; i < 4000; i++) b.think(t += 50, 0.05, view(VbPhase.COMBAT, VbSide.SEALERS, true, null, "b_anchor2", List.of()));
    for (int i = 0; i < 4000; i++) b.think(t += 50, 0.05, view(VbPhase.COMBAT, VbSide.SEALERS, true, null, "a_anchor1", List.of()));
    Waypoints.Node n = g.node("a_anchor1");
    assertEquals(0, b.mover().distanceTo(n.x(), n.z()), 0.05);
  }

  @Test
  public void theUltimateNeedsTwoEnemiesInRange() {
    BotBrain b = brain("veteran", "ds1");
    double x = b.mover().x(), z = b.mover().z();
    BotBrain.Seen e1 = new BotBrain.Seen(UUID.randomUUID(), x, 65, z - 8, false), e2 = new BotBrain.Seen(UUID.randomUUID(), x + 2, 65, z - 9, false);
    BotBrain.View one = new BotBrain.View(VbPhase.COMBAT, VbSide.SEALERS, false, null, false, null, null, true, null, "a_anchor1", false, List.of(e1), false, false, true);
    assertNull(b.think(0, 0.05, one).ability);
    BotBrain.View two = new BotBrain.View(VbPhase.COMBAT, VbSide.SEALERS, false, null, false, null, null, true, null, "a_anchor1", false, List.of(e1, e2), false, false, true);
    assertEquals("ult", b.think(100, 0.05, two).ability);
    assertNull("not again at once", b.think(150, 0.05, two).ability);
    BotBrain.View notReady = new BotBrain.View(VbPhase.COMBAT, VbSide.SEALERS, false, null, false, null, null, true, null, "a_anchor1", false, List.of(e1, e2), false, false, false);
    BotBrain other = brain("veteran", "ds1");
    assertNull(other.think(0, 0.05, notReady).ability);
  }

  @Test
  public void anEnemyOutOfRangeDoesNotCountForTheUltimate() {
    BotBrain b = brain("veteran", "ds1");
    double x = b.mover().x(), z = b.mover().z();
    BotBrain.Seen near = new BotBrain.Seen(UUID.randomUUID(), x, 65, z - 8, false), far = new BotBrain.Seen(UUID.randomUUID(), x, 65, z - 40, false);
    BotBrain.View v = new BotBrain.View(VbPhase.COMBAT, VbSide.SEALERS, false, null, false, null, null, true, null, "a_anchor1", false, List.of(near, far), false, false, true);
    assertNull(b.think(0, 0.05, v).ability);
  }

  @Test
  public void attackersSmokeAtTheSmokeSpotOnceAndFlashBeforeThePeek() {
    BotBrain b = brain("veteran", "as1");
    Waypoints.SiteInfo site = g.site("A");
    Waypoints.Node smokeNode = g.node(site.smokes().get(0).node());
    b.mover().teleport(smokeNode.x(), smokeNode.y(), smokeNode.z());
    BotBrain.View v = new BotBrain.View(VbPhase.COMBAT, VbSide.SEEDBEARERS, false, null, false, null, "A", true, null, null, false, List.of(), true, true, false);
    BotBrain.Act a = b.think(0, 0.05, v);
    assertEquals("smoke", a.ability);
    assertArrayEquals(site.smokes().get(0).at(), a.abilityAt, 1e-9);
    assertNotEquals("smoke", b.think(5000, 0.05, v).ability); // thrown once per round
    Waypoints.Node peek = g.node(site.peeks().get(0));
    b.mover().teleport(peek.x(), peek.y(), peek.z());
    assertEquals("flash", b.think(10_000, 0.05, v).ability);
    assertNull(b.think(20_000, 0.05, v).ability);
  }

  @Test
  public void noAbilityWithoutACharge() {
    BotBrain b = brain("veteran", "as1");
    Waypoints.Node smokeNode = g.node(g.site("A").smokes().get(0).node());
    b.mover().teleport(smokeNode.x(), smokeNode.y(), smokeNode.z());
    BotBrain.View v = new BotBrain.View(VbPhase.COMBAT, VbSide.SEEDBEARERS, false, null, false, null, "A", true, null, null, false, List.of(), false, false, false);
    assertNull(b.think(0, 0.05, v).ability);
  }

  @Test
  public void aBotOnTheDroppedSeedPicksItUp() {
    BotBrain b = brain("veteran", "as1");
    double[] drop = {b.mover().x() + 0.5, 65, b.mover().z()};
    BotBrain.View v = new BotBrain.View(VbPhase.COMBAT, VbSide.SEEDBEARERS, false, drop, true, null, "A", true, null, null, false, List.of(), false, false, false);
    assertTrue(b.think(0, 0.05, v).pickUpSeed);
  }

  @Test
  public void theChaserWalksToTheDroppedSeed() {
    BotBrain b = brain("veteran", "as1");
    Waypoints.Node far = g.node("a_site");
    double[] drop = {far.x(), far.y(), far.z()};
    BotBrain.View v = new BotBrain.View(VbPhase.COMBAT, VbSide.SEEDBEARERS, false, drop, true, null, "A", true, null, null, false, List.of(), false, false, false);
    boolean picked = false;
    long t = 0;
    for (int i = 0; i < 2000 && !picked; i++) picked = b.think(t += 50, 0.05, v).pickUpSeed;
    assertTrue(picked);
  }
}
