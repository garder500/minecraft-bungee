package win.vampyuyu.valoblock;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

/**
 * The agent select window (VbSelectLayout): the pack paints the background and the picked agent's portrait through the title
 * glyphs, the roster is a grid of 2-slot tiles, the team and Lock in run along the bottom. All words are the window title and
 * item tooltips. The countdown is the clock of the top bar (a window title cannot change its font while open).
 */
public final class AgentSelectMenu implements InventoryHolder {
  public static final String TITLE = "Choose your agent";

  /** What one player has done in the menu (the service keeps one per player). */
  public static final class State {
    public volatile String picked; // the agent clicked (not yet locked)
    public volatile String filter; // role name or null
    public volatile boolean locked;
    public volatile boolean dismissed; // closed the menu by hand: it does not pop back up
    public volatile boolean reopening; // the service is redrawing the window: the close that follows is not a dismissal
  }

  private Inventory inventory;

  @Override
  public Inventory getInventory() {
    return inventory;
  }

  /** The index (in agents/index.json order) of the agent whose portrait the player's window shows, or -1 for the silhouette. */
  static int portraitOf(VbMatch m, UUID me, State mine) {
    String shown = !m.agent(me).isEmpty() ? m.agent(me) : mine.picked;
    return shown == null ? -1 : VbData.agents().ids().indexOf(shown);
  }

  public static Inventory build(VbMatch m, Player p, Function<UUID, State> states) {
    AgentSelectMenu holder = new AgentSelectMenu();
    holder.inventory = Bukkit.createInventory(holder, 54, VbMenuSkin.select(portraitOf(m, p.getUniqueId(), states.apply(p.getUniqueId())), TITLE));
    fill(holder.inventory, m, p.getUniqueId(), states);
    return holder.inventory;
  }

  static boolean isMenu(Inventory inv) {
    return inv != null && inv.getHolder() instanceof AgentSelectMenu;
  }

  private static ItemStack twin(ItemStack of) {
    ItemStack it = of.clone();
    var meta = it.getItemMeta();
    meta.setItemModel(AgentItems.model("blank"));
    meta.removeEnchantments();
    meta.setEnchantmentGlintOverride(false);
    it.setItemMeta(meta);
    return it;
  }

  /** (Re)draws every cell for this viewer. */
  public static void fill(Inventory inv, VbMatch m, UUID me, Function<UUID, State> states) {
    inv.clear();
    VbAgents cat = VbData.agents();
    State mine = states.apply(me);
    // The team: one head per teammate along the bottom row.
    List<UUID> team = new ArrayList<>(m.squad(m.squadOf(me)));
    for (int i = 0; i < m.rules().teamSize() && i < VbSelectLayout.SEATS; i++) {
      int slot = VbSelectLayout.seat(i);
      if (i >= team.size()) {
        inv.setItem(slot, AgentItems.cell("agent_empty", List.of(new AgentText.Line("Empty seat", AgentText.GRAY), new AgentText.Line("A bot takes it when the time runs out.", AgentText.GRAY)), "none", false));
        continue;
      }
      UUID id = team.get(i);
      State st = states.apply(id);
      String name = name(id);
      String agent = m.agent(id);
      boolean locked = !agent.isEmpty();
      String shown = locked ? agent : st.picked;
      if (shown == null || cat.get(shown) == null) {
        inv.setItem(slot, AgentItems.cell("agent_empty", List.of(new AgentText.Line(name, AgentText.GRAY), new AgentText.Line("Still choosing", AgentText.GRAY)), "none", false));
      } else {
        VbAgent a = cat.get(shown);
        inv.setItem(slot, AgentItems.cell("agent_" + a.id() + (locked ? "" : "_dim"),
            List.of(new AgentText.Line(name, "#ffffff"), new AgentText.Line(a.name() + (locked ? " · locked in" : " · hovering"), locked ? a.roleColor() : AgentText.GRAY)), "none", false));
      }
    }
    // The roster: 2-slot tiles, the head on the left and an invisible twin with the same tooltip on the right.
    int i = 0;
    for (VbAgent a : cat.all()) {
      if (i >= VbSelectLayout.CAPACITY) break;
      boolean taken = !a.id().equals(m.agent(me)) && m.takenByTeammate(me, a.id());
      boolean dimmed = taken || (mine.filter != null && !mine.filter.equals(a.role()));
      boolean ring = a.id().equals(mine.picked) || a.id().equals(m.agent(me));
      String sprite = "agent_" + a.id() + (taken || dimmed ? "_dim" : ring ? "_ring" : "");
      String footer = taken ? "Taken by a teammate" : mine.locked ? "" : "Click to select, then Lock in.";
      List<AgentText.Line> lines = new ArrayList<>(AgentText.tooltip(a, footer));
      if (taken) lines.set(lines.size() - 1, new AgentText.Line(footer, AgentText.RED));
      ItemStack head = AgentItems.cell(sprite, lines, taken ? "none" : "pick:" + a.id(), ring);
      inv.setItem(VbSelectLayout.head(i), head);
      inv.setItem(VbSelectLayout.twin(i), twin(head));
      i++;
    }
    // Role filters.
    for (int f = 0; f < cat.roles().size() && f < VbSelectLayout.FILTERS.length; f++) {
      VbAgents.Role r = cat.roles().get(f);
      inv.setItem(VbSelectLayout.FILTERS[f], AgentItems.cell("role_" + r.name().toLowerCase(java.util.Locale.ROOT), AgentText.role(r), "role:" + r.name(), r.name().equals(mine.filter)));
    }
    // Lock in (four slots wide) and Random (two).
    VbAgent picked = mine.picked == null ? null : cat.get(mine.picked);
    List<AgentText.Line> lock = mine.locked ? AgentText.lockIn(cat.get(m.agent(me)), true) : AgentText.lockIn(picked, false);
    ItemStack plate = AgentItems.cell("lock_in", lock, mine.locked ? "none" : "lock", mine.locked);
    for (int k = 0; k < VbSelectLayout.LOCK.length; k++) inv.setItem(VbSelectLayout.LOCK[k], k == 0 ? plate : twin(plate));
    ItemStack random = AgentItems.cell("random", AgentText.random(), mine.locked ? "none" : "random", false);
    inv.setItem(VbSelectLayout.RANDOM, random);
    inv.setItem(VbSelectLayout.RANDOM_TWIN, twin(random));
  }

  private static String name(UUID id) {
    Player p = Bukkit.getPlayer(id);
    if (p != null) return p.getName();
    String n = Bukkit.getOfflinePlayer(id).getName();
    return n == null ? "Player" : n;
  }
}
