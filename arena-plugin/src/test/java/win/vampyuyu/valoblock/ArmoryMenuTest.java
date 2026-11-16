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

  @Test
  public void menuHasOneColumnPerClassAndThePlayersStates() {
    PlayerMock p = server.addPlayer();
    List<UUID> humans = new ArrayList<>(List.of(p.getUniqueId()));
    VbMatch m = new VbMatch(VbData.rules(), humans, new Random(1));
    m.advance(); // buy phase, 800 Shards
    Loadout lo = Loadout.starting(VbData.weapons());
    Inventory inv = ArmoryMenu.build(m, lo, p);
    assertEquals(54, inv.getSize());
    // spacer columns and the abilities row are empty
    for (int row = 0; row < 6; row++) {
      assertNull(inv.getItem(row * 9 + 1));
      assertNull(inv.getItem(row * 9 + 4));
    }
    for (int i = 45; i < 54; i++) assertNull(inv.getItem(i));
    ItemStack flux = inv.getItem(1 * 9 + 5);
    assertNotNull(flux);
    assertEquals("flux_rifle", WeaponItems.id(flux));
    // 800 Shards: Flux (2900) is locked, Pip (free) is owned, Gravebell (800) is affordable
    assertEquals("vampyuyu:valoblock/flux_rifle_locked", flux.getItemMeta().getItemModel().toString());
    assertEquals("vampyuyu:valoblock/gravebell", inv.getItem(2 * 9).getItemMeta().getItemModel().toString());
    assertTrue(inv.getItem(0).getItemMeta().getEnchantmentGlintOverride());
    List<String> lore = flux.getItemMeta().lore().stream().map(c -> PlainTextComponentSerializer.plainText().serialize(c)).toList();
    assertEquals("Missing 2,100 Shards", lore.get(lore.size() - 1));
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
