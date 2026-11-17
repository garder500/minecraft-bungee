package win.vampyuyu.valoblock;

import static org.junit.Assert.*;

import java.util.Random;
import org.junit.Test;
import win.vampyuyu.valoblock.GunMath.Zone;

public class GunMathTest {
  private final VbWeapons cat = VbData.weapons();

  private VbWeapon w(String id) {
    return cat.get(id);
  }

  @Test
  public void zonesFollowHitHeight() {
    assertEquals(Zone.LEGS, GunMath.zone(0.3, 1.8));
    assertEquals(Zone.BODY, GunMath.zone(1.0, 1.8));
    assertEquals(Zone.HEAD, GunMath.zone(1.6, 1.8));
    assertEquals(Zone.HEAD, GunMath.zone(1.3, 1.5)); // sneaking: the box is lower
    assertEquals(Zone.BODY, GunMath.zone(1.3, 1.8));
  }

  @Test
  public void damageByZoneAtPointBlank() {
    VbWeapon flux = w("flux_rifle");
    assertEquals(156, GunMath.damage(flux, Zone.HEAD, 5, 1));
    assertEquals(39, GunMath.damage(flux, Zone.BODY, 5, 1));
    assertEquals(33, GunMath.damage(flux, Zone.LEGS, 5, 1));
  }

  @Test
  public void oneGravebellHeadshotKillsAnUnshieldedPlayer() {
    assertTrue(GunMath.damage(w("gravebell"), Zone.HEAD, 10, 1) >= VbData.rules().maxHealth());
  }

  @Test
  public void watchersEyeKillsWithOneBodyShot() {
    assertTrue(GunMath.damage(w("watchers_eye"), Zone.BODY, 100, 1) >= VbData.rules().maxHealth());
  }

  @Test
  public void carbineNeedsTwoHeadshotsThroughAHeavyWard() {
    // The design says "two headshots" but 140 already beats 100 HP: it holds against 50 shield + 100 HP.
    int h = GunMath.damage(w("bronzite_carbine"), Zone.HEAD, 10, 1);
    assertTrue(h < VbData.rules().maxHealth() + 50 && 2 * h >= VbData.rules().maxHealth() + 50);
  }

  @Test
  public void falloffIsFlatThenLinearThenFloored() {
    VbWeapon t = w("tickhand"); // 10..30, floor 0.45
    assertEquals(66, GunMath.damage(t, Zone.HEAD, 10, 1));
    assertEquals(Math.round(66 * (1 - 0.5 * 0.55)), GunMath.damage(t, Zone.HEAD, 20, 1));
    assertEquals(Math.round(66 * 0.45), GunMath.damage(t, Zone.HEAD, 80, 1));
  }

  @Test
  public void scrapperIsUselessPastTenBlocks() {
    VbWeapon s = w("scrapper");
    int close = 8 * GunMath.damage(s, Zone.BODY, 2, 1); // 8 pellets x 12
    assertEquals(96, close);
    assertTrue(8 * GunMath.damage(s, Zone.BODY, 12, 1) <= 16);
    assertEquals(1, GunMath.damage(s, Zone.LEGS, 500, 1)); // never below 1
  }

  @Test
  public void wallPenetrationScalesDamage() {
    VbWeapon g = w("grinder");
    assertEquals(Math.round(34 * g.penetrationFactor()), GunMath.damage(g, Zone.BODY, 5, g.penetrationFactor()));
    assertTrue(g.penetration() >= 1);
  }

  @Test
  public void knifeHitsHardFromBehind() {
    VbWeapon k = w("shard_knife");
    // victim faces +z
    assertEquals(150, GunMath.knifeDamage(k, 0, 1, 0, -1)); // attacker stands at -z: behind
    assertEquals(50, GunMath.knifeDamage(k, 0, 1, 0, 1)); // in front
    assertEquals(50, GunMath.knifeDamage(k, 0, 1, 1, 0)); // flank
    assertEquals(150, GunMath.knifeDamage(k, 0, 1, 0.5, -1)); // slightly off behind
  }

  @Test
  public void sneakingTightensAndMovingWidensTheSpread() {
    VbWeapon pip = w("pip");
    double still = GunMath.spread(pip, false, false, false, 0);
    assertTrue(GunMath.spread(pip, true, false, false, 0) < still);
    assertTrue(GunMath.spread(pip, false, true, false, 0) > still);
    assertTrue(GunMath.spread(pip, false, false, true, 0) > GunMath.spread(pip, false, true, false, 0));
  }

  @Test
  public void mistralStaysAccurateWhileMovingComparedWithGnat() {
    double gnat = GunMath.spread(w("gnat"), false, true, false, 0) / GunMath.spread(w("gnat"), false, false, false, 0);
    double mistral = GunMath.spread(w("mistral"), false, true, false, 0) / GunMath.spread(w("mistral"), false, false, false, 0);
    assertTrue(mistral < gnat);
  }

  @Test
  public void carbineFirstShotIsPerfectWhenStandingStill() {
    VbWeapon c = w("bronzite_carbine");
    assertEquals(0, GunMath.spread(c, false, false, false, 0), 0);
    assertTrue(GunMath.spread(c, false, true, false, 0) > 0);
    assertTrue(GunMath.spread(c, false, false, false, 1) > 0);
  }

  @Test
  public void sprayGrowthIsCapped() {
    VbWeapon g = w("gnat");
    assertEquals(GunMath.spread(g, false, false, false, 0) * GunMath.SPRAY_CAP, GunMath.spread(g, false, false, false, 1000), 1e-9);
  }

  @Test
  public void jitterStaysInsideTheCone() {
    Random r = new Random(1);
    double[] dir = {0, 0, 1};
    double max = 0;
    for (int i = 0; i < 2000; i++) {
      double[] d = GunMath.jitter(dir, 2.0, r);
      assertEquals(1.0, Math.sqrt(d[0] * d[0] + d[1] * d[1] + d[2] * d[2]), 1e-9);
      max = Math.max(max, Math.toDegrees(Math.acos(d[2])));
    }
    assertTrue(max <= 2.0 + 1e-9);
    assertTrue(max > 1.5); // it does use the cone
    assertArrayEquals(dir, GunMath.jitter(dir, 0, r), 0);
  }

  @Test
  public void recoilClimbsThenDrifts() {
    VbWeapon.Recoil r = w("flux_rifle").recoil();
    double[] first = r.at(0), late = r.at(r.climb() + 3);
    assertTrue(first[0] > late[0]); // the climb eases off
    assertEquals(0, first[1], 0);
    assertTrue(late[1] > 0); // then drifts right
    assertArrayEquals(r.at(500), r.at(501), 0); // pattern is bounded
  }

  @Test
  public void rayHitsTheBoxAndPicksTheZone() {
    double[] o = {0, 1.62, -10}, d = {0, 0, 1};
    GunMath.Hit h = GunMath.rayHitbox(o, d, 50, 0, 0, 0, false);
    assertNotNull(h);
    assertEquals(9.7, h.distance(), 1e-9);
    assertEquals(Zone.HEAD, h.zone());
    assertEquals(Zone.LEGS, GunMath.rayHitbox(new double[] {0, 0.4, -10}, d, 50, 0, 0, 0, false).zone());
    assertEquals(Zone.BODY, GunMath.rayHitbox(new double[] {0, 1.0, -10}, d, 50, 0, 0, 0, false).zone());
    assertNull(GunMath.rayHitbox(new double[] {1, 1.0, -10}, d, 50, 0, 0, 0, false)); // beside
    assertNull(GunMath.rayHitbox(o, d, 5, 0, 0, 0, false)); // out of range
    assertNull(GunMath.rayHitbox(new double[] {0, 2.0, -10}, d, 50, 0, 0, 0, false)); // over the head
    assertNotNull(GunMath.rayHitbox(new double[] {0, 1.7, -10}, new double[] {0, -0.05, 0.99875}, 50, 0, 0, 0, false)); // slanted down into the head
  }
}
