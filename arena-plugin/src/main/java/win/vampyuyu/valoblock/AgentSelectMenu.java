package win.vampyuyu.valoblock;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/**
 * The agent select chest (design VB-AgentSelect): row 1 the team (slots 2-6), row 3 the roster (19-26),
 * row 4 the role filters (28, 30, 32, 34), row 6 Lock in (47-49) and Random (51). All words are the
 * window title and item tooltips; the countdown is in the title.
 */
public final class AgentSelectMenu implements InventoryHolder {
  public static final int SEAT_FIRST = 2, ROSTER_FIRST = 19, RANDOM = 51;
  public static final int[] FILTERS = {28, 30, 32, 34}, LOCK = {47, 48, 49};

  /** What one player has done in the menu (the service keeps one per player). */
  public static final class State {
    public volatile String picked; // the agent clicked (not yet locked)
    public volatile String filter; // role name or null
    public volatile boolean locked;
    public volatile boolean dismissed; // closed the menu by hand: it does not pop back up
  }

  private Inventory inventory;

  @Override
  public Inventory getInventory() {
    return inventory;
  }

  public static Inventory build(VbMatch m, Player p, Function<UUID, State> states, int seconds) {
    AgentSelectMenu holder = new AgentSelectMenu();
    holder.inventory = Bukkit.createInventory(holder, 54, Component.text(AgentText.title(seconds)));
    fill(holder.inventory, m, p.getUniqueId(), states);
    return holder.inventory;
  }

  static boolean isMenu(Inventory inv) {
    return inv != null && inv.getHolder() instanceof AgentSelectMenu;
  }

  /** (Re)draws every cell for this viewer. */
  public static void fill(Inventory inv, VbMatch m, UUID me, Function<UUID, State> states) {
    inv.clear();
    VbAgents cat = VbData.agents();
    State mine = states.apply(me);
    // Row 1: the team, one head per teammate.
    List<UUID> team = new ArrayList<>(m.squad(m.squadOf(me)));
    for (int i = 0; i < m.rules().teamSize() && i < 5; i++) {
      int slot = SEAT_FIRST + i;
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
    // Row 3: the roster.
    int i = 0;
    for (VbAgent a : cat.all()) {
      boolean taken = !a.id().equals(m.agent(me)) && m.takenByTeammate(me, a.id());
      boolean dimmed = taken || (mine.filter != null && !mine.filter.equals(a.role()));
      boolean ring = a.id().equals(mine.picked) || a.id().equals(m.agent(me));
      String sprite = "agent_" + a.id() + (taken || dimmed ? "_dim" : ring ? "_ring" : "");
      String footer = taken ? "Taken by a teammate" : mine.locked ? "" : "Click to select, then Lock in.";
      List<AgentText.Line> lines = new ArrayList<>(AgentText.tooltip(a, footer));
      if (taken) lines.set(lines.size() - 1, new AgentText.Line(footer, AgentText.RED));
      inv.setItem(ROSTER_FIRST + i++, AgentItems.cell(sprite, lines, taken ? "none" : "pick:" + a.id(), ring));
    }
    // Row 4: role filters.
    for (int f = 0; f < cat.roles().size() && f < FILTERS.length; f++) {
      VbAgents.Role r = cat.roles().get(f);
      inv.setItem(FILTERS[f], AgentItems.cell("role_" + r.name().toLowerCase(java.util.Locale.ROOT), AgentText.role(r), "role:" + r.name(), r.name().equals(mine.filter)));
    }
    // Row 6: Lock in (three wide) and Random.
    VbAgent picked = mine.picked == null ? null : cat.get(mine.picked);
    List<AgentText.Line> lock = mine.locked ? AgentText.lockIn(cat.get(m.agent(me)), true) : AgentText.lockIn(picked, false);
    for (int slot : LOCK) inv.setItem(slot, AgentItems.cell("lock_in", lock, mine.locked ? "none" : "lock", mine.locked));
    inv.setItem(RANDOM, AgentItems.cell("random", AgentText.random(), mine.locked ? "none" : "random", false));
  }

  private static String name(UUID id) {
    Player p = Bukkit.getPlayer(id);
    if (p != null) return p.getName();
    String n = Bukkit.getOfflinePlayer(id).getName();
    return n == null ? "Player" : n;
  }
}
