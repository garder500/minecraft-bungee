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

public class AgentSelectMenuTest {
  private ServerMock server;

  @Before
  public void setUp() {
    server = MockBukkit.mock();
  }

  @After
  public void tearDown() {
    MockBukkit.unmock();
  }

  private static String plain(net.kyori.adventure.text.Component c) {
    return PlainTextComponentSerializer.plainText().serialize(c);
  }

  @Test
  public void layoutFollowsTheDesign() {
    PlayerMock me = server.addPlayer();
    PlayerMock mate = server.addPlayer();
    PlayerMock other = server.addPlayer();
    VbMatch m = new VbMatch(VbData.rules(), List.of(me.getUniqueId(), mate.getUniqueId(), other.getUniqueId(), server.addPlayer().getUniqueId()), new Random(1));
    int mySquad = m.squadOf(me.getUniqueId());
    UUID teammate = m.squad(mySquad).stream().filter(id -> !id.equals(me.getUniqueId())).findFirst().orElseThrow();
    m.lockAgent(teammate, "cinder");
    Map<UUID, AgentSelectMenu.State> states = new HashMap<>();
    Inventory inv = AgentSelectMenu.build(m, me, id -> states.computeIfAbsent(id, k -> new AgentSelectMenu.State()), 44);
    assertEquals(54, inv.getSize());
    // roster: 8 agents in slots 19-26, in order
    List<String> ids = VbData.agents().ids();
    for (int i = 0; i < 8; i++) {
      ItemStack it = inv.getItem(19 + i);
      assertNotNull("roster " + i, it);
      String action = AgentItems.action(it);
      if (ids.get(i).equals("cinder")) assertEquals("taken by the teammate: not clickable", "none", action);
      else assertEquals("pick:" + ids.get(i), action);
    }
    assertEquals("vampyuyu:valoblock/agent_cinder_dim", inv.getItem(19).getItemMeta().getItemModel().toString());
    assertEquals("vampyuyu:valoblock/agent_gale", inv.getItem(20).getItemMeta().getItemModel().toString());
    // role filters on 28/30/32/34, none between
    assertEquals("role:Vanguard", AgentItems.action(inv.getItem(28)));
    assertEquals("role:Warden", AgentItems.action(inv.getItem(30)));
    assertEquals("role:Seer", AgentItems.action(inv.getItem(32)));
    assertEquals("role:Weaver", AgentItems.action(inv.getItem(34)));
    assertNull(inv.getItem(29));
    // Lock in is three wide, Random on 51
    for (int s : new int[] {47, 48, 49}) assertEquals("lock", AgentItems.action(inv.getItem(s)));
    assertEquals("random", AgentItems.action(inv.getItem(51)));
    // the team row: teamSize 5 seats, the locked teammate wears their agent
    int heads = 0;
    for (int s = 2; s <= 6; s++) assertNotNull("seat " + s, inv.getItem(s));
    for (int s = 2; s <= 6; s++) if (inv.getItem(s).getItemMeta().getItemModel().toString().equals("vampyuyu:valoblock/agent_cinder")) heads++;
    assertEquals(1, heads);
    // tooltip: name, role and realm, quote, four abilities
    List<String> lore = inv.getItem(23).getItemMeta().lore().stream().map(AgentSelectMenuTest::plain).toList(); // vesper
    assertEquals("Vesper", plain(inv.getItem(23).getItemMeta().displayName()));
    assertEquals("Seer · Ethereal Necropolis", lore.get(0));
    assertTrue(lore.contains("[5] Judgment · 250"));
    assertTrue(lore.contains("[6] Soul Lantern · free"));
    assertEquals("Click to select, then Lock in.", lore.get(lore.size() - 1));
    // nothing else: no stray items in the spacer rows
    for (int s : new int[] {0, 1, 7, 8, 9, 10, 17, 18, 27, 29, 31, 33, 35, 36, 45, 46, 50, 52, 53}) assertNull("slot " + s, inv.getItem(s));
  }

  @Test
  public void pickRingFilterDimAndLockedStatesShowOnTheRoster() {
    PlayerMock me = server.addPlayer();
    VbMatch m = new VbMatch(VbData.rules(), List.of(me.getUniqueId(), server.addPlayer().getUniqueId()), new Random(2));
    Map<UUID, AgentSelectMenu.State> states = new HashMap<>();
    AgentSelectMenu.State mine = states.computeIfAbsent(me.getUniqueId(), k -> new AgentSelectMenu.State());
    mine.picked = "vesper";
    mine.filter = "Seer";
    Inventory inv = AgentSelectMenu.build(m, me, id -> states.computeIfAbsent(id, k -> new AgentSelectMenu.State()), 60);
    assertEquals("vampyuyu:valoblock/agent_vesper_ring", inv.getItem(23).getItemMeta().getItemModel().toString()); // vesper is the 5th: slot 23
    assertEquals("vampyuyu:valoblock/agent_nacre", inv.getItem(24).getItemMeta().getItemModel().toString()); // the other Seer stays lit
    assertEquals("vampyuyu:valoblock/agent_cinder_dim", inv.getItem(19).getItemMeta().getItemModel().toString()); // Vanguard dimmed
    assertTrue(inv.getItem(32).getItemMeta().getEnchantmentGlintOverride()); // the active filter glows
    mine.locked = true;
    m.lockAgent(me.getUniqueId(), "vesper");
    AgentSelectMenu.fill(inv, m, me.getUniqueId(), id -> states.computeIfAbsent(id, k -> new AgentSelectMenu.State()));
    assertEquals("none", AgentItems.action(inv.getItem(47)));
    assertEquals("Locked in", plain(inv.getItem(47).getItemMeta().displayName()));
  }

  @Test
  public void abilityItemsShowChargesAndPointsInTheirTooltipAndStackSize() {
    VbAgent cinder = VbData.agents().get("cinder");
    AgentKit kit = new AgentKit(cinder, VbData.agents().ultimate());
    ItemStack sig = AgentItems.ability(cinder, cinder.ability(6), kit);
    assertEquals(6, AgentItems.slotOf(sig));
    assertEquals(1, sig.getAmount());
    assertEquals("[6] Ember Dash", plain(sig.getItemMeta().displayName()));
    ItemStack ult = AgentItems.ability(cinder, cinder.ability(7), kit);
    assertTrue(plain(ult.getItemMeta().displayName()).endsWith("(not ready)"));
    assertTrue(plain(ult.getItemMeta().lore().get(1)).contains("0 / 7 pts"));
    kit.use(6);
    assertTrue(plain(AgentItems.ability(cinder, cinder.ability(6), kit).getItemMeta().displayName()).endsWith("(not ready)"));
  }
}
