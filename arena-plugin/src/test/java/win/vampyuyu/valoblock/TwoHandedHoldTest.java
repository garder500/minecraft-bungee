package win.vampyuyu.valoblock;

import static org.junit.Assert.*;

import io.papermc.paper.datacomponent.DataComponentTypes;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

/** The two-handed hold: every gun but the spyglass sniper is a charged crossbow that still stacks to its magazine. */
public class TwoHandedHoldTest {
  @Before
  public void setUp() {
    MockBukkit.mock();
  }

  @After
  public void tearDown() {
    MockBukkit.unmock();
  }

  @Test
  public void everyGunButTheSpyglassSniperIsHeldAsACrossbow() {
    for (VbWeapon w : VbData.weapons().all()) {
      String expected = w.isShield() ? "CHAINMAIL_CHESTPLATE" : !w.isGun() ? "STICK" : ScopeInput.holdScope(w) ? "SPYGLASS" : "CROSSBOW";
      assertEquals(w.id(), expected, w.baseItem().toUpperCase(java.util.Locale.ROOT));
    }
  }

  @Test
  public void aCarriedGunIsChargedStacksToItsMagazineAndHidesTheArrow() {
    VbWeapon flux = VbData.weapons().get("flux_rifle");
    ItemStack it = WeaponItems.carried(flux, 25);
    assertEquals(Material.CROSSBOW, it.getType());
    assertTrue(WeaponItems.twoHanded(it));
    assertEquals(25, it.getAmount());
    assertFalse("damageable items cannot stack", it.hasData(DataComponentTypes.MAX_DAMAGE));
    assertFalse(it.getData(DataComponentTypes.CHARGED_PROJECTILES).projectiles().isEmpty());
    assertTrue(it.getData(DataComponentTypes.TOOLTIP_DISPLAY).hiddenComponents().contains(DataComponentTypes.CHARGED_PROJECTILES));
    assertEquals("flux_rifle", WeaponItems.id(it));
    assertEquals(25, WeaponItems.mag(it, -1));
    assertEquals("the client must not play the crossbow's use (the gun would dip at each shot)",
        WeaponItems.GUN_COOLDOWN, it.getData(DataComponentTypes.USE_COOLDOWN).cooldownGroup());
    ItemStack knife = WeaponItems.carried(VbData.weapons().get("shard_knife"), 0);
    assertFalse(WeaponItems.twoHanded(knife));
  }
}
