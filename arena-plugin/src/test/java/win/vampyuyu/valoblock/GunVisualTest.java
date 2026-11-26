package win.vampyuyu.valoblock;

import static org.junit.Assert.*;

import java.util.List;
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

  @Test
  public void theReloadPlaysSixFramesOverTheReloadTime() {
    VbWeapon flux = VbData.weapons().get("flux_rifle"); // 2.5 s
    GunState gs = new GunState(flux, 3);
    assertEquals(0, gs.reloadProgress(1_000_000), 0);
    assertTrue(gs.startReload(1_000_000));
    assertEquals(0.5, gs.reloadProgress(1_001_250), 1e-9);
    List<String> seen = new java.util.ArrayList<>();
    for (long t = 1_000_000; t < 1_002_500; t += 50) {
      String s = GunVisual.state(gs.reloadProgress(t), gs.reloading(), gs.sinceShot(t), false, false, flux.zoom());
      if (seen.isEmpty() || !seen.get(seen.size() - 1).equals(s)) seen.add(s);
    }
    assertEquals(List.of("reload_0", "reload_1", "reload_2", "reload_3", "reload_4", "reload_5"), seen);
    assertEquals(GunVisual.RELOAD_FRAMES, GunVisual.RELOAD_AT.length);
    assertEquals("not reloading: no frame", "", GunVisual.state(0, false, 10_000, false, false, flux.zoom()));
    assertEquals("the scope still wins", GunVisual.SCOPE, GunVisual.state(0, false, 10_000, true, false, 3.5));
  }
}
