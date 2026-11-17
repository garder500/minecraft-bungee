package win.vampyuyu.valoblock;

import static org.junit.Assert.*;

import java.util.*;
import org.junit.Test;

public class BotMoveTest {
  @Test
  public void moverFollowsTheRouteAtItsSpeedAndInterpolatesY() {
    BotMover m = new BotMover();
    m.teleport(0, 10, 0);
    m.go(List.of(new double[] {10, 20, 0}, new double[] {10, 20, 10}));
    assertEquals(4.3, m.step(1, 4.3), 1e-9);
    assertEquals(4.3, m.x(), 1e-9);
    assertEquals(10 + 10 * 0.43, m.y(), 1e-9);
    assertEquals(-90, m.yaw(), 1e-9); // moving +x
    m.step(10, 4.3);
    assertTrue(m.arrived());
    assertEquals(10, m.x(), 1e-9);
    assertEquals(10, m.z(), 1e-9);
    assertEquals(20, m.y(), 1e-9);
  }

  @Test
  public void aBigStepTurnsCornersWithoutLosingDistance() {
    BotMover m = new BotMover();
    m.teleport(0, 0, 0);
    m.go(List.of(new double[] {3, 0, 0}, new double[] {3, 0, 5}));
    assertEquals(6, m.step(1, 6), 1e-9);
    assertEquals(3, m.x(), 1e-9);
    assertEquals(3, m.z(), 1e-9);
    assertFalse(m.arrived());
  }

  @Test
  public void stoppedMoverDoesNotMove() {
    BotMover m = new BotMover();
    m.teleport(1, 2, 3);
    m.go(List.of(new double[] {9, 2, 3}));
    m.stop();
    assertEquals(0, m.step(1, 5), 1e-9);
    assertEquals(1, m.x(), 1e-9);
  }

  @Test
  public void sightNeedsTheReactionTimeAndForgetsAfterTheGrace() {
    BotSight s = new BotSight();
    UUID e = UUID.randomUUID();
    for (long t = 0; t <= 300; t += 50) s.update(t, Set.of(e));
    assertTrue(s.ready(e, 300, 300));
    assertFalse(s.ready(e, 300, 400));
    s.update(350, Set.of()); // a short loss (one tick) keeps the clock
    s.update(400, Set.of(e));
    assertTrue(s.ready(e, 400, 300));
    for (long t = 450; t < 800; t += 50) s.update(t, Set.of()); // lost past the grace
    s.update(800, Set.of(e));
    assertFalse(s.ready(e, 800, 300));
    for (long t = 850; t <= 1100; t += 50) s.update(t, Set.of(e));
    assertTrue(s.ready(e, 1100, 300));
  }

  @Test
  public void levelsMakeBetterAimAndAbigRecoilCostsTheWeakOnes() {
    VbWeapon flux = VbData.weapons().get("flux_rifle");
    double recruit = BotAim.cone(VbData.bots().level("recruit"), flux, 5, false);
    double veteran = BotAim.cone(VbData.bots().level("veteran"), flux, 5, false);
    double elite = BotAim.cone(VbData.bots().level("elite"), flux, 5, false);
    assertTrue(recruit > veteran);
    assertTrue(veteran > elite);
    // The first shot is only the aim error plus the gun's own spread; later shots add the recoil it does not cancel.
    assertTrue(BotAim.cone(VbData.bots().level("veteran"), flux, 8, false) > BotAim.cone(VbData.bots().level("veteran"), flux, 0, false));
  }

  @Test
  public void aShotConeStaysInsideItsAngle() {
    Random r = new Random(5);
    double[] aim = {0, 0, 1};
    double cone = 3;
    for (int i = 0; i < 500; i++) {
      double[] d = GunMath.jitter(aim, cone, r);
      double angle = Math.toDegrees(Math.acos(Math.max(-1, Math.min(1, d[2]))));
      assertTrue(angle <= cone + 1e-6);
    }
  }
}
