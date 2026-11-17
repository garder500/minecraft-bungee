package win.vampyuyu.valoblock;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import win.vampyuyu.arena.ArenaSession;

/**
 * The map vote window: three big tiles (the pack paints the plan thumbnails in Realm-coloured frames, see VbMenuSkin#vote and
 * art/valoblock_vote.py), each covered by 3 x 3 invisible items that carry the tooltip and the click. A click is a vote; the voter's
 * choice has a gold ring (another background). All words are the window title and the tooltips.
 */
public final class MapVoteMenu implements InventoryHolder {
  /** The Realm colour of each map (the tooltip name; the frame in the pack uses the same, vb_layout.VOTE_REALM). */
  static final Map<String, String> REALM = Map.of("hourfall", "#d9b23c", "rootvault", "#46b866", "stormbreak", "#4aa3d8");
  static final int FIRST_ROW = 2, TILE_COLS = 3, TILE_ROWS = 3;

  final ArenaSession session;
  Inventory inventory;
  /** The seconds the title showed when it was built (the service redraws when this is 5 s stale). */
  int shown;

  MapVoteMenu(ArenaSession session) {
    this.session = session;
  }

  @Override
  public Inventory getInventory() {
    return inventory;
  }

  static boolean isMenu(Inventory inv) {
    return inv != null && inv.getHolder(false) instanceof MapVoteMenu;
  }

  /** "Vote for the next map · 1:25", or without the clock once it ran out (the vote stays open until the match starts). */
  static String title(int seconds) {
    if (seconds <= 0) return "Vote for the next map";
    return "Vote for the next map · " + (seconds / 60) + ":" + (seconds % 60 < 10 ? "0" : "") + (seconds % 60);
  }

  /** The map under a slot of the window, or -1 (outside the three tiles). */
  static int mapAt(int slot, int maps) {
    int row = slot / 9, col = slot % 9;
    if (row < FIRST_ROW || row >= FIRST_ROW + TILE_ROWS || col >= TILE_COLS * maps) return -1;
    return col / TILE_COLS;
  }

  /** The slot under the middle of tile {@code k}: its vote count as a stack. */
  static int tallySlot(int k) {
    return (FIRST_ROW + TILE_ROWS) * 9 + TILE_COLS * k + TILE_COLS / 2;
  }

  /** The tooltip of one map tile (pure). {@code mine}: the player voted for it; {@code leading}: it is ahead (or tied for it). */
  static List<AgentText.Line> tooltip(VbMap map, int votes, boolean mine, boolean leading) {
    List<AgentText.Line> l = new ArrayList<>();
    l.add(new AgentText.Line(map.name(), REALM.getOrDefault(map.id(), AgentText.GOLD)));
    l.add(new AgentText.Line("Realm: " + map.realm(), AgentText.GRAY));
    l.add(new AgentText.Line("Size: " + map.width() + " x " + map.depth() + " blocks", AgentText.GRAY));
    String mechanic = VbRealmService.mechanic(map.id());
    l.add(new AgentText.Line(mechanic != null ? mechanic : map.feature(), AgentText.GRAY));
    l.add(new AgentText.Line("Votes: " + votes + (leading ? " (leading)" : ""), AgentText.GOLD));
    l.add(mine ? new AgentText.Line("Your vote. Click another map to change it.", AgentText.GREEN) : new AgentText.Line("Click to vote for this map.", AgentText.YELLOW));
    return l;
  }

  /** Builds the window for one player from the session's ballot. */
  static Inventory build(Player p, ArenaSession s, MapVote ballot, long now) {
    MapVoteMenu holder = new MapVoteMenu(s);
    UUID me = p.getUniqueId();
    List<String> ids = ballot.maps();
    String mine = ballot.choice(me);
    int pick = mine == null ? -1 : ids.indexOf(mine);
    holder.shown = ballot.secondsLeft(now);
    holder.inventory = Bukkit.createInventory(holder, 54, VbMenuSkin.vote(pick, title(holder.shown)));
    Map<String, Integer> counts = ballot.counts();
    List<String> leaders = ballot.leaders();
    for (int k = 0; k < ids.size(); k++) {
      String id = ids.get(k);
      VbMap map = VbData.maps().get(id);
      if (map == null) continue;
      List<AgentText.Line> lines = tooltip(map, counts.getOrDefault(id, 0), id.equals(mine), leaders.contains(id));
      for (int r = 0; r < TILE_ROWS; r++)
        for (int c = 0; c < TILE_COLS; c++)
          holder.inventory.setItem((FIRST_ROW + r) * 9 + TILE_COLS * k + c, AgentItems.cell("blank", lines, "vote:" + id, id.equals(mine)));
      int votes = counts.getOrDefault(id, 0);
      if (votes > 0) { // the tally row under the tile: one ballot per vote (the stack count is the number)
        ItemStack tally = AgentItems.cell("tac_mark", lines, "vote:" + id, id.equals(mine));
        tally.setAmount(Math.min(64, votes));
        holder.inventory.setItem(tallySlot(k), tally);
      }
    }
    return holder.inventory;
  }

  /** The map id a clicked item votes for, or null. */
  static String voteOf(ItemStack it) {
    String action = AgentItems.action(it);
    return action != null && action.startsWith("vote:") ? action.substring(5) : null;
  }
}
