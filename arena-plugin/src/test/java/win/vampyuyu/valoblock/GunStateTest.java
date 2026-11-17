package win.vampyuyu.valoblock;

import static org.junit.Assert.*;

import org.junit.Test;

public class GunStateTest {
  private final VbWeapons cat = VbData.weapons();

  /** Holds the trigger for {@code ms} from t=0 polling every 50 ms (a server tick); counts shots. */
  private int hold(GunState g, long ms) {
    int shots = 0;
    for (long t = 0; t <= ms; t += 50) while (g.poll(t, t == 0, true)) shots++;
    return shots;
  }

  @Test
  public void fluxFiresTenPerSecondAtTwentyTps() {
    GunState g = new GunState(cat.get("flux_rifle"));
    assertEquals(10, hold(g, 950)); // 20 ticks => 10 shots (t=0..900 every 100 ms)
    assertEquals(25 - 10, g.magazine());
  }

  @Test
  public void gnatSixteenPerSecondNeedsCatchUpWithinTheTickGrid() {
    GunState g = new GunState(cat.get("gnat"));
    int shots = hold(g, 1950); // 2 seconds: 32 shots expected, 20 round magazine caps at 20
    assertEquals(20, shots);
    GunState big = new GunState(cat.get("mistral")); // 13/s, 40 rounds
    int s = hold(big, 1950);
    assertTrue("got " + s, s >= 25 && s <= 27);
  }

  @Test
  public void semiAutoNeedsAClickEveryShot() {
    GunState g = new GunState(cat.get("pip"));
    assertTrue(g.poll(0, true, true));
    assertFalse(g.poll(1000, false, true)); // held, not a click
    assertTrue(g.poll(1000, true, true));
  }

  @Test
  public void fireRateLimitsClicks() {
    GunState g = new GunState(cat.get("gravebell")); // 2/s
    assertTrue(g.poll(0, true, true));
    assertFalse(g.poll(300, true, true));
    assertTrue(g.poll(500, true, true));
  }

  @Test
  public void burstFiresThreeThenWaits() {
    GunState g = new GunState(cat.get("tickhand"));
    int shots = 0;
    for (long t = 0; t < 2000 && shots < 3; t += 10) if (g.poll(t, t == 0, false)) shots++;
    assertEquals(3, shots);
    assertEquals(15, g.magazine());
    assertFalse(g.poll(300, true, false)); // burst rate 2/s: not yet
    assertTrue(g.poll(500, true, false));
  }

  @Test
  public void reloadRefillsAfterTheReloadTime() {
    GunState g = new GunState(cat.get("flux_rifle"));
    hold(g, 450);
    assertTrue(g.magazine() < 25);
    assertTrue(g.startReload(1000));
    assertFalse(g.startReload(1100)); // already reloading
    assertFalse(g.poll(2000, true, true)); // cannot fire while reloading
    g.tick(3400);
    assertTrue(g.reloading());
    assertEquals(100, g.reloadLeft(3400));
    g.tick(3500);
    assertFalse(g.reloading());
    assertEquals(25, g.magazine());
  }

  @Test
  public void swappingAwayCancelsTheReload() {
    GunState g = new GunState(cat.get("flux_rifle"));
    g.poll(0, true, true);
    g.startReload(100);
    g.cancelReload();
    g.tick(10_000);
    assertEquals(24, g.magazine());
  }

  @Test
  public void anEmptyMagazineStartsTheReload() {
    GunState g = new GunState(cat.get("longwind")); // 7 rounds
    for (int i = 0; i < 7; i++) assertTrue(g.poll(i * 1000L, true, false));
    assertEquals(0, g.magazine());
    assertFalse(g.poll(8000, true, false));
    assertTrue(g.reloading());
  }

  @Test
  public void theShotThatEmptiesTheMagazineStartsTheReload() {
    // A scoped Watcher's Eye fires on release and never polls the trigger again while empty: no second pull is needed.
    GunState g = new GunState(cat.get("watchers_eye"));
    int rounds = g.magazine();
    for (int i = 0; i < rounds; i++) assertTrue(g.poll(i * 5000L, true, false));
    assertEquals(0, g.magazine());
    assertTrue("reloading right after the last shot", g.reloading());
    long done = (rounds - 1) * 5000L + Math.round(g.weapon().reload() * 1000);
    g.tick(done);
    assertFalse(g.reloading());
    assertEquals(rounds, g.magazine());
  }

  @Test
  public void aBurstCutShortByTheLastRoundDoesNotResumeAfterTheReload() {
    GunState g = new GunState(cat.get("tickhand"), 1); // a 3-round burst with 1 round left
    assertTrue(g.poll(0, true, false));
    assertTrue(g.reloading());
    g.tick(10_000);
    assertEquals(g.weapon().magazine(), g.magazine());
    assertFalse("no leftover burst shots", g.poll(10_050, false, false));
    assertEquals(g.weapon().magazine(), g.magazine());
  }

  @Test
  public void fullMagazineDoesNotReload() {
    assertFalse(new GunState(cat.get("pip")).startReload(0));
  }

  @Test
  public void sprayIndexClimbsAndResets() {
    GunState g = new GunState(cat.get("flux_rifle"));
    for (int i = 0; i < 4; i++) {
      assertTrue(g.poll(i * 100L, true, true));
      assertEquals(i, g.lastShotIndex());
    }
    assertTrue(g.poll(3000, true, true)); // a pause: a new spray
    assertEquals(0, g.lastShotIndex());
  }

  @Test
  public void grinderFiresAtHalfRateWhileSpinningUp() {
    GunState g = new GunState(cat.get("grinder")); // 12/s, 0.5 s spin-up
    int first = 0;
    for (long t = 0; t < 500; t += 10) while (g.poll(t, t == 0, true)) first++;
    int second = 0;
    for (long t = 500; t < 1000; t += 10) while (g.poll(t, false, true)) second++;
    assertTrue("spin-up " + first + " vs " + second, first < second);
    assertTrue(first >= 2 && first <= 4);
  }

  @Test
  public void knifeNeverShoots() {
    assertFalse(new GunState(cat.get("shard_knife")).poll(0, true, true));
  }
}
