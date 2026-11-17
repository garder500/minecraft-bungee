package win.vampyuyu.valoblock;

import static org.junit.Assert.*;

import java.util.*;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

public class ArmoryMenuTest {
  private ServerMock server;

  @Before
  public void setUp() {
    server = MockBukkit.mock();
  }

  @After
  public void tearDown() {
    MockBukkit.unmock();
  }

  private static String model(ItemStack it) {
    return it.getItemMeta().getItemModel().toString();
  }

  private static List<String> lore(ItemStack it) {
    return it.getItemMeta().lore().stream().map(c -> PlainTextComponentSerializer.plainText().serialize(c)).toList();
  }

  @Test
  public void tabsListTheSelectedClassAsTwoByTwoCardsWithStateFrames() {
    PlayerMock p = server.addPlayer();
    List<UUID> humans = new ArrayList<>(List.of(p.getUniqueId()));
    VbMatch m = new VbMatch(VbData.rules(), humans, new Random(1));
    m.advance(); // buy phase, 800 Shards
    Loadout lo = Loadout.starting(VbData.weapons());
    ArmoryMenu.setTab(p.getUniqueId(), 3); // Rifles
    Inventory inv = ArmoryMenu.build(m, lo, p);
    assertEquals(54, inv.getSize());
    // six class tabs down column 0, the selected one wears the _sel sprite
    for (int i = 0; i < 6; i++) {
      assertEquals("tab:" + i, AgentItems.action(inv.getItem(i * 9)));
      assertEquals(i == 3, model(inv.getItem(i * 9)).endsWith("_sel"));
    }
    assertEquals("vampyuyu:valoblock/tab_rifles_sel", model(inv.getItem(27)));
    // Rifles: Bronzite (2100) then Flux (2900), cheapest first; at 800 Shards both are too expensive
    int[] first = VbArmoryLayout.cardSlots(0);
    assertEquals("bronzite_carbine", WeaponItems.id(inv.getItem(first[0])));
    assertEquals("vampyuyu:valoblock/card_bronzite_carbine_locked", model(inv.getItem(first[0])));
    for (int i = 1; i < 4; i++) {
      assertEquals("bronzite_carbine", WeaponItems.id(inv.getItem(first[i])));
      assertEquals("the other three slots of a card are invisible", "vampyuyu:valoblock/blank", model(inv.getItem(first[i])));
    }
    ItemStack flux = inv.getItem(VbArmoryLayout.card(1));
    assertEquals("flux_rifle", WeaponItems.id(flux));
    assertEquals("Missing 2,100 Shards", lore(flux).get(lore(flux).size() - 1));
    assertNull(inv.getItem(VbArmoryLayout.card(2)));
    // the Wards below: Light is affordable, Heavy (1000) is not
    assertEquals("vampyuyu:valoblock/card_light_ward", model(inv.getItem(VbArmoryLayout.shield(0))));
    assertEquals("vampyuyu:valoblock/card_heavy_ward_locked", model(inv.getItem(VbArmoryLayout.shield(1))));
    // the sidearm tab: Pip is free and owned (green frame), Gravebell (800) affordable
    ArmoryMenu.setTab(p.getUniqueId(), 0);
    inv = ArmoryMenu.build(m, lo, p);
    assertEquals("vampyuyu:valoblock/card_pip_owned", model(inv.getItem(VbArmoryLayout.card(0))));
    assertEquals("vampyuyu:valoblock/card_gravebell", model(inv.getItem(VbArmoryLayout.card(2))));
    assertNotNull(inv.getItem(VbArmoryLayout.tab(0)));
    ArmoryMenu.forget(p.getUniqueId());
  }

  @Test
  public void everyCardSlotMapsBackToItsWeaponAndNothingOverlaps() {
    Set<Integer> used = new HashSet<>();
    for (int i = 0; i < VbArmoryLayout.CARD_CAPACITY; i++) {
      for (int s : VbArmoryLayout.cardSlots(i)) {
        assertEquals("slot " + s, i, VbArmoryLayout.cardAt(s));
        assertTrue("slot " + s + " is in two parts", used.add(s));
        assertEquals(-1, VbArmoryLayout.tabAt(s));
      }
    }
    for (int i = 0; i < 2; i++)
      for (int s : VbArmoryLayout.shieldSlots(i)) {
        assertEquals(i, VbArmoryLayout.shieldAt(s));
        assertTrue(used.add(s));
        assertEquals(-1, VbArmoryLayout.cardAt(s));
      }
    for (int i = 0; i < VbArmoryLayout.TABS.length; i++) {
      assertEquals(i, VbArmoryLayout.tabAt(VbArmoryLayout.tab(i)));
      assertTrue(used.add(VbArmoryLayout.tab(i)));
    }
    for (int s : VbArmoryLayout.ABILITY) assertTrue("ability slot " + s, used.add(s));
    // every weapon sold by class lands on a tab and fits its tab
    for (VbWeapon w : VbData.weapons().all()) {
      if (w.isShield() || w.slot() == VbWeapon.Slot.MELEE) {
        assertEquals(w.id(), -1, VbArmoryLayout.tabOf(w));
        continue;
      }
      int tab = VbArmoryLayout.tabOf(w);
      assertTrue(w.id(), tab >= 0 && tab < 6);
      assertTrue(w.id() + " must be shown", VbArmoryLayout.weaponsOf(tab, VbData.weapons().all()).contains(w));
      for (int i = 0; i < VbArmoryLayout.weaponsOf(tab, VbData.weapons().all()).size(); i++)
        if (VbArmoryLayout.weaponsOf(tab, VbData.weapons().all()).get(i) == w)
          for (int s : VbArmoryLayout.cardSlots(i)) assertSame(w, ArmoryMenu.at(tab, s));
    }
    assertEquals("Snipers", VbArmoryLayout.TABS[VbArmoryLayout.tabOf(VbData.weapons().get("longwind"))]);
    assertEquals("Heavy", VbArmoryLayout.TABS[VbArmoryLayout.tabOf(VbData.weapons().get("grinder"))]);
    assertNull(ArmoryMenu.at(3, 0));
    assertEquals("light_ward", ArmoryMenu.at(3, VbArmoryLayout.shield(0)).id());
  }

  @Test
  public void carriedGunShowsItsRoundsAsTheStackSize() {
    ItemStack it = WeaponItems.carried(VbData.weapons().get("grinder"), 80);
    assertEquals(80, it.getAmount());
    assertEquals("grinder", WeaponItems.id(it));
    assertEquals(80, WeaponItems.mag(it, -1));
    assertEquals("vampyuyu:valoblock/grinder", it.getItemMeta().getItemModel().toString());
  }
}
