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

  private static String model(ItemStack it) {
    return it.getItemMeta().getItemModel().toString();
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
    Inventory inv = AgentSelectMenu.build(m, me, id -> states.computeIfAbsent(id, k -> new AgentSelectMenu.State()));
    assertEquals(54, inv.getSize());
    // roster: 8 agents as 2-slot tiles (3 per row), head on the left, an invisible twin on the right with the same action
    List<String> ids = VbData.agents().ids();
    for (int i = 0; i < 8; i++) {
      ItemStack head = inv.getItem(VbSelectLayout.head(i)), twin = inv.getItem(VbSelectLayout.twin(i));
      assertNotNull("roster " + i, head);
      assertNotNull("twin " + i, twin);
      assertEquals("vampyuyu:valoblock/blank", model(twin));
      assertEquals(AgentItems.action(head), AgentItems.action(twin));
      assertEquals(plain(head.getItemMeta().displayName()), plain(twin.getItemMeta().displayName()));
      if (ids.get(i).equals("cinder")) assertEquals("taken by the teammate: not clickable", "none", AgentItems.action(head));
      else assertEquals("pick:" + ids.get(i), AgentItems.action(head));
    }
    assertEquals("vampyuyu:valoblock/agent_cinder_dim", model(inv.getItem(VbSelectLayout.head(0))));
    assertEquals("vampyuyu:valoblock/agent_gale", model(inv.getItem(VbSelectLayout.head(1))));
    // role filters on the fifth row, cols 3-6 (slots 39-42)
    assertEquals("role:Vanguard", AgentItems.action(inv.getItem(39)));
    assertEquals("role:Warden", AgentItems.action(inv.getItem(40)));
    assertEquals("role:Seer", AgentItems.action(inv.getItem(41)));
    assertEquals("role:Weaver", AgentItems.action(inv.getItem(42)));
    // Lock in is four slots wide, Random two
    for (int s : VbSelectLayout.LOCK) assertEquals("lock", AgentItems.action(inv.getItem(s)));
    assertEquals("vampyuyu:valoblock/lock_in", model(inv.getItem(VbSelectLayout.LOCK[0])));
    assertEquals("vampyuyu:valoblock/blank", model(inv.getItem(VbSelectLayout.LOCK[1])));
    assertEquals("random", AgentItems.action(inv.getItem(VbSelectLayout.RANDOM)));
    assertEquals("random", AgentItems.action(inv.getItem(VbSelectLayout.RANDOM_TWIN)));
    // the team along the bottom: 5 seats, the locked teammate wears their agent
    int heads = 0;
    for (int i = 0; i < 5; i++) assertNotNull("seat " + i, inv.getItem(VbSelectLayout.seat(i)));
    for (int i = 0; i < 5; i++) if (model(inv.getItem(VbSelectLayout.seat(i))).equals("vampyuyu:valoblock/agent_cinder")) heads++;
    assertEquals(1, heads);
    // tooltip: name, role and realm, quote, four abilities
    int vesper = ids.indexOf("vesper");
    List<String> lore = inv.getItem(VbSelectLayout.head(vesper)).getItemMeta().lore().stream().map(AgentSelectMenuTest::plain).toList();
    assertEquals("Vesper", plain(inv.getItem(VbSelectLayout.head(vesper)).getItemMeta().displayName()));
    assertEquals("Seer · Ethereal Necropolis", lore.get(0));
    assertTrue(lore.contains("[5] Judgment · 250"));
    assertTrue(lore.contains("[6] Soul Lantern · free"));
    assertEquals("Click to select, then Lock in.", lore.get(lore.size() - 1));
    // nothing on the portrait panel or in the spacers of the bottom rows
    for (int s = 0; s < 54; s++) {
      if (VbSelectLayout.inPortrait(s)) assertNull("portrait panel slot " + s, inv.getItem(s));
    }
    for (int s : new int[] {38, 43, 44}) assertNull("spacer " + s, inv.getItem(s));
  }

  @Test
  public void everySlotBelongsToOnePartAndTheMapsRoundTrip() {
    Set<Integer> seen = new HashSet<>();
    for (int i = 0; i < VbSelectLayout.CAPACITY; i++) {
      int head = VbSelectLayout.head(i), twin = VbSelectLayout.twin(i);
      assertEquals(head + 1, twin);
      assertEquals(i, VbSelectLayout.tileAt(head));
      assertEquals(i, VbSelectLayout.tileAt(twin));
      assertTrue("tile " + i + " overlaps", seen.add(head) && seen.add(twin));
      assertFalse(VbSelectLayout.inPortrait(head));
      assertTrue("a tile stays inside the roster rows", head / 9 < 4 && head % 9 >= 3 && twin % 9 <= 8);
    }
    List<Integer> parts = new ArrayList<>();
    for (int s : VbSelectLayout.FILTERS) parts.add(s);
    for (int s : VbSelectLayout.LOCK) parts.add(s);
    parts.add(VbSelectLayout.RANDOM);
    parts.add(VbSelectLayout.RANDOM_TWIN);
    for (int i = 0; i < VbSelectLayout.SEATS; i++) parts.add(VbSelectLayout.seat(i));
    for (int s : parts) assertTrue("slot " + s + " is used twice", seen.add(s));
    for (int s : seen) assertFalse("slot " + s + " is under the portrait", VbSelectLayout.inPortrait(s));
    assertEquals(-1, VbSelectLayout.tileAt(0));
    assertEquals(-1, VbSelectLayout.tileAt(36));
  }

  @Test
  public void thePortraitFollowsThePickAndTheLock() {
    PlayerMock me = server.addPlayer();
    VbMatch m = new VbMatch(VbData.rules(), List.of(me.getUniqueId(), server.addPlayer().getUniqueId()), new Random(2));
    AgentSelectMenu.State mine = new AgentSelectMenu.State();
    assertEquals(-1, AgentSelectMenu.portraitOf(m, me.getUniqueId(), mine));
    mine.picked = "vesper";
    assertEquals(VbData.agents().ids().indexOf("vesper"), AgentSelectMenu.portraitOf(m, me.getUniqueId(), mine));
    m.lockAgent(me.getUniqueId(), "rime");
    assertEquals(VbData.agents().ids().indexOf("rime"), AgentSelectMenu.portraitOf(m, me.getUniqueId(), mine));
  }

  @Test
  public void pickRingFilterDimAndLockedStatesShowOnTheRoster() {
    PlayerMock me = server.addPlayer();
    VbMatch m = new VbMatch(VbData.rules(), List.of(me.getUniqueId(), server.addPlayer().getUniqueId()), new Random(2));
    Map<UUID, AgentSelectMenu.State> states = new HashMap<>();
    AgentSelectMenu.State mine = states.computeIfAbsent(me.getUniqueId(), k -> new AgentSelectMenu.State());
    mine.picked = "vesper";
    mine.filter = "Seer";
    Inventory inv = AgentSelectMenu.build(m, me, id -> states.computeIfAbsent(id, k -> new AgentSelectMenu.State()));
    List<String> ids = VbData.agents().ids();
    assertEquals("vampyuyu:valoblock/agent_vesper_ring", model(inv.getItem(VbSelectLayout.head(ids.indexOf("vesper")))));
    assertEquals("vampyuyu:valoblock/agent_nacre", model(inv.getItem(VbSelectLayout.head(ids.indexOf("nacre"))))); // the other Seer stays lit
    assertEquals("vampyuyu:valoblock/agent_cinder_dim", model(inv.getItem(VbSelectLayout.head(ids.indexOf("cinder"))))); // Vanguard dimmed
    assertTrue(inv.getItem(41).getItemMeta().getEnchantmentGlintOverride()); // the active filter glows
    mine.locked = true;
    m.lockAgent(me.getUniqueId(), "vesper");
    AgentSelectMenu.fill(inv, m, me.getUniqueId(), id -> states.computeIfAbsent(id, k -> new AgentSelectMenu.State()));
    assertEquals("none", AgentItems.action(inv.getItem(VbSelectLayout.LOCK[0])));
    assertEquals("Locked in", plain(inv.getItem(VbSelectLayout.LOCK[0]).getItemMeta().displayName()));
  }

  @Test
  public void abilityItemsShowChargesAndPointsInTheirTooltipAndStackSize() {
    VbAgent cinder = VbData.agents().get("cinder");
    AgentKit kit = new AgentKit(cinder, VbData.agents().ultimate());
    ItemStack sig = AgentItems.ability(cinder, cinder.ability(6), kit);
    assertEquals(6, AgentItems.slotOf(sig));
    assertEquals(1, sig.getAmount());
    assertEquals("Ember Dash", plain(sig.getItemMeta().displayName()));
    assertEquals("vampyuyu:valoblock/ab_cinder_ember_dash", sig.getItemMeta().getItemModel().toString());
    assertEquals("vampyuyu:vb_ab_cinder_ember_dash", sig.getItemMeta().getUseCooldown().getCooldownGroup().toString());
    ItemStack ult = AgentItems.ability(cinder, cinder.ability(7), kit);
    assertEquals("vampyuyu:valoblock/ab_cinder_colossus_step_empty", ult.getItemMeta().getItemModel().toString());
    assertTrue(plain(ult.getItemMeta().lore().get(1)).contains("0 / 7"));
    kit.use(6);
    ItemStack spent = AgentItems.ability(cinder, cinder.ability(6), kit);
    assertEquals(1, spent.getAmount());
    assertEquals(6, AgentItems.slotOf(spent)); // an empty item stays in its slot, still an ability
    assertEquals("vampyuyu:valoblock/ab_cinder_ember_dash_empty", spent.getItemMeta().getItemModel().toString());
  }
}
