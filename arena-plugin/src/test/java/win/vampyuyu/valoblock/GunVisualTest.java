package win.vampyuyu.valoblock;

import static org.junit.Assert.*;

import org.junit.Test;

public class GunVisualTest {
  @Test
  public void reloadWinsThenTheFlashThenTheAimingHold() {
    assertEquals("reload", GunVisual.state(true, 0, true));
    assertEquals("fire", GunVisual.state(false, 0, true));
    assertEquals("fire", GunVisual.state(false, 99, false));
    assertEquals("aim", GunVisual.state(false, 100, true));
    assertEquals("", GunVisual.state(false, 100, false));
  }

  @Test
  public void inspectShowsAfterASwapBelowTheFlashAndReload() {
    assertEquals("inspect", GunVisual.state(false, 500, true, true));
    assertEquals("fire", GunVisual.state(false, 0, false, true));
    assertEquals("reload", GunVisual.state(true, 0, false, true));
    assertEquals("aim", GunVisual.state(false, 500, true, false));
  }

  @Test
  public void aScopedSniperDrawsNoGunWhateverTheOtherStates() {
    assertEquals("scope", GunVisual.state(false, Long.MAX_VALUE / 2, true, false, 3.5));
    assertEquals("scope", GunVisual.state(false, 0, true, true, 2.0)); // the flash and the inspect pose hide too
    assertEquals("reload", GunVisual.state(true, 0, true, false, 3.5)); // reloading drops the scope
    assertEquals("aim", GunVisual.state(false, 500, true, false, 1.0)); // a rifle keeps its aiming pose
    assertEquals("", GunVisual.state(false, 500, false, false, 3.5));   // not sneaking: plain hold
    assertEquals("fire", GunVisual.state(false, 0, false, false, 3.5));
  }

  @Test
  public void aGunNeverFiredIsNotFlashing() {
    assertEquals("", GunVisual.state(false, Long.MAX_VALUE / 2, false));
    assertEquals("aim", GunVisual.state(false, Long.MAX_VALUE / 2, true));
  }

  @Test
  public void onlyZoomingGunsScopeWhileAimingAndNotReloading() {
    assertTrue(GunVisual.scoped(2.0, true, false));
    assertFalse(GunVisual.scoped(1.0, true, false)); // zoom 1: no scope
    assertFalse(GunVisual.scoped(3.5, false, false));
    assertFalse(GunVisual.scoped(3.5, true, true));
  }

  @Test
  public void gunStateReportsTheTimeSinceTheShot() {
    VbWeapon pip = VbData.weapons().get("pip");
    GunState gs = new GunState(pip);
    assertTrue(GunVisual.state(false, gs.sinceShot(1_000_000), false).isEmpty());
    assertTrue(gs.poll(1_000_000, true, false));
    assertEquals("fire", GunVisual.state(false, gs.sinceShot(1_000_050), false));
    assertEquals("", GunVisual.state(false, gs.sinceShot(1_000_200), false));
  }
}
