package win.vampyuyu.valoblock;

import static org.junit.Assert.*;

import java.util.UUID;
import org.junit.Test;
import win.vampyuyu.valoblock.ScopeInput.Action;
import win.vampyuyu.valoblock.ScopeInput.Click;

public class ScopeInputTest {
  private static VbWeapon w(String id) {
    return VbData.weapons().get(id);
  }

  @Test
  public void theWatchersEyeIsASpyglassThatScopesOnRightAndLeftDoesNothing() {
    VbWeapon eye = w("watchers_eye");
    assertEquals("SPYGLASS", eye.baseItem());
    assertTrue(ScopeInput.holdScope(eye));
    assertEquals(Action.SCOPE, ScopeInput.action(eye, false, Click.RIGHT));
    assertEquals(Action.NONE, ScopeInput.action(eye, false, Click.LEFT));
  }

  @Test
  public void otherGunsKeepRightClickToFireAndLeftClickDoesNothing() {
    for (String id : new String[] {"longwind", "pip", "flux_rifle"}) {
      assertEquals(id, Action.FIRE, ScopeInput.action(w(id), false, Click.RIGHT));
      assertEquals(id, Action.NONE, ScopeInput.action(w(id), false, Click.LEFT));
    }
    assertFalse(ScopeInput.holdScope(w("longwind")));
    assertTrue(ScopeInput.sneakZoom(w("longwind")));
    assertFalse(ScopeInput.sneakZoom(w("watchers_eye")));
    assertFalse(ScopeInput.sneakZoom(w("pip")));
  }

  @Test
  public void theKnifeStabsOnLeftOnlyAndTheArmoryOpensOnRight() {
    VbWeapon knife = VbData.weapons().all().stream().filter(x -> x.slot() == VbWeapon.Slot.MELEE).findFirst().orElseThrow();
    assertEquals(Action.STAB, ScopeInput.action(knife, false, Click.LEFT));
    assertEquals(Action.NONE, ScopeInput.action(knife, false, Click.RIGHT));
    assertEquals(Action.OPEN_ARMORY, ScopeInput.action(null, true, Click.RIGHT));
    assertEquals(Action.NONE, ScopeInput.action(null, true, Click.LEFT));
    assertEquals(Action.NONE, ScopeInput.action(null, false, Click.LEFT));
  }

  @Test
  public void noGunButTheKnifeEverStabsFromAClick() {
    for (VbWeapon x : VbData.weapons().all())
      for (Click c : Click.values())
        if (ScopeInput.action(x, false, c) == Action.STAB) assertEquals(x.id(), VbWeapon.Slot.MELEE, x.slot());
  }

  @Test
  public void scopedMeansTheHeldButtonForTheEyeAndSneakingForLongwind() {
    VbWeapon eye = w("watchers_eye"), lw = w("longwind");
    assertTrue(ScopeInput.scoped(eye, false, true, false));
    assertFalse(ScopeInput.scoped(eye, true, false, false)); // sneaking alone does nothing
    assertFalse(ScopeInput.scoped(eye, false, true, true)); // reloading drops it
    assertTrue(ScopeInput.scoped(lw, true, false, false));
    assertFalse(ScopeInput.scoped(lw, false, true, false));
    assertFalse(ScopeInput.scoped(lw, true, false, true));
    assertFalse(ScopeInput.scoped(w("pip"), true, true, false));
    assertFalse(ScopeInput.scoped(null, true, true, false));
  }

  @Test
  public void anUnscopedEyeShotIsWild() {
    VbWeapon eye = w("watchers_eye");
    assertEquals(ScopeInput.UNSCOPED_SPREAD, ScopeInput.spread(eye, 0.0, false), 1e-9); // sneak multiplier 0 does not rescue it
    assertEquals(0.6, ScopeInput.spread(eye, 0.6, true), 1e-9);
    assertEquals(0.8, ScopeInput.spread(w("longwind"), 0.8, false), 1e-9);
  }

  @Test
  public void theHintsNameTheControls() {
    assertEquals("Hold right-click to aim, release to fire, F to cancel", ScopeInput.hint(w("watchers_eye")));
    assertEquals("Right-click fire", ScopeInput.hint(w("pip")));
  }

  @Test
  public void theModifierIsAddedOnceAndRemovedOnce() {
    ZoomModifier z = new ZoomModifier();
    UUID a = UUID.randomUUID(), b = UUID.randomUUID();
    assertEquals(ZoomModifier.Op.NONE, z.update(a, false));
    assertEquals(ZoomModifier.Op.ADD, z.update(a, true));
    assertEquals("never stacks", ZoomModifier.Op.NONE, z.update(a, true));
    assertEquals(ZoomModifier.Op.ADD, z.update(b, true));
    assertEquals(2, z.size());
    assertEquals(ZoomModifier.Op.REMOVE, z.update(a, false));
    assertEquals(ZoomModifier.Op.NONE, z.update(a, false));
    assertFalse(z.active(a));
    assertTrue(z.active(b));
    assertEquals(ZoomModifier.Op.ADD, z.update(a, true)); // zooms again after a removal
  }

  @Test
  public void theZoomIsWantedOnlyWhileSneakingWithLongwindAliveInTheFight() {
    VbWeapon lw = w("longwind");
    assertTrue(ZoomModifier.wanted(lw, true, false, true, true));
    assertFalse("unsneak", ZoomModifier.wanted(lw, false, false, true, true));
    assertFalse("weapon swap", ZoomModifier.wanted(w("pip"), true, false, true, true));
    assertFalse("no weapon", ZoomModifier.wanted(null, true, false, true, true));
    assertFalse("the Eye zooms by use", ZoomModifier.wanted(w("watchers_eye"), true, false, true, true));
    assertFalse("death", ZoomModifier.wanted(lw, true, false, true, false));
    assertFalse("round end", ZoomModifier.wanted(lw, true, false, false, true));
    assertFalse("reload", ZoomModifier.wanted(lw, true, true, true, true));
  }

  @Test
  public void theModifierIsLightAndNeverStrongerThanTheCap() {
    assertEquals(-0.4, ZoomModifier.amount(2.0), 1e-9);
    assertEquals(-ZoomModifier.MAX_SLOW, ZoomModifier.amount(10.0), 1e-9);
    assertTrue(ZoomModifier.amount(1.2) > -0.2);
    assertEquals(0, ZoomModifier.amount(1.0), 0);
  }

  @Test
  public void releasingAfterTheScopeTimeIsAccurateAndAQuickTapIsPenalised() {
    assertEquals(ScopeInput.Release.SCOPED, ScopeInput.release(6, false, false)); // 0.3 s
    assertEquals(ScopeInput.Release.SCOPED, ScopeInput.release(40, false, false));
    assertEquals(ScopeInput.Release.UNSCOPED, ScopeInput.release(5, false, false));
    assertEquals(ScopeInput.Release.UNSCOPED, ScopeInput.release(0, false, false));
    VbWeapon eye = w("watchers_eye");
    assertEquals(0.6, ScopeInput.spread(eye, 0.6, true), 1e-9);
    assertEquals(ScopeInput.UNSCOPED_SPREAD, ScopeInput.spread(eye, 0.6, false), 1e-9);
  }

  @Test
  public void cancelAndForcedDropsNeverFire() {
    for (int t : new int[] {0, 5, 6, 100}) {
      assertEquals(ScopeInput.Release.NONE, ScopeInput.release(t, true, false)); // F or slot change
      assertEquals(ScopeInput.Release.NONE, ScopeInput.release(t, false, true)); // reload, death, round end
      assertEquals(ScopeInput.Release.NONE, ScopeInput.release(t, true, true));
    }
  }

  @Test
  public void anEmptyMagazineIsADryClick() {
    assertTrue(ScopeInput.dryClick(0));
    assertFalse(ScopeInput.dryClick(1));
  }

  @Test
  public void aReleaseShotRespectsTheBoltCooldown() {
    VbWeapon eye = w("watchers_eye");
    GunState gs = new GunState(eye);
    assertTrue(gs.poll(10_000, true, false)); // first release fires
    assertFalse(gs.poll(10_050, true, false)); // the bolt is not cycled yet
    assertTrue(gs.poll(10_000 + (long) (eye.interval() * 1000) + 10, true, false));
  }
}
