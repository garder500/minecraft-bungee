package win.vampyuyu.valoblock;

import java.util.*;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

/**
 * Name tags show for allies only. A player's own tag is the vanilla one: each match has its own scoreboard (so no other
 * game's teams or sidebar are touched) with one team per squad and NAME_TAG_VISIBILITY = FOR_OWN_TEAM; the player's previous
 * scoreboard comes back when the match ends or they leave. A bot's tag is a text display shown (showEntity) only to the
 * humans of its squad. A squad is the same people for the whole match: the sides swap, the teams do not (a team per side would
 * only have to be re-filled with the same names).
 */
public final class VbNameTags {
  private static final long EVERY_MS = 250;

  private static final class Board {
    final Scoreboard sb = Bukkit.getScoreboardManager().getNewScoreboard();
    final Team[] teams = new Team[2];
    final Map<UUID, Scoreboard> previous = new HashMap<>();
    long last;
  }

  private final Map<VbMatch, Board> boards = new IdentityHashMap<>();

  /** Team name of a squad of a match: stable for the whole match, whatever side the squad plays. Pure. */
  static String teamName(String matchKey, int squad) {
    return "vb" + matchKey + (squad == 0 ? "a" : "b");
  }

  /** Whether a viewer of {@code viewerSquad} sees the tag of a body of {@code bodySquad}. Pure. */
  static boolean shows(int viewerSquad, int bodySquad) {
    return viewerSquad == bodySquad;
  }

  private final java.util.concurrent.atomic.AtomicLong queuedAt = new java.util.concurrent.atomic.AtomicLong();

  /** Called by the game clock (not the server thread): scoreboards and entity visibility are changed on the server thread. */
  public void sync(ValoBlockModule module, VbMatch m, long now) {
    if (Bukkit.isPrimaryThread()) {
      syncNow(module, m);
      return;
    }
    long wall = System.currentTimeMillis();
    long last = queuedAt.get();
    if (wall - last < EVERY_MS || !queuedAt.compareAndSet(last, wall)) return;
    Bukkit.getScheduler().runTask(module.plugin, () -> syncNow(module, m));
  }

  private synchronized void syncNow(ValoBlockModule module, VbMatch m) {
    long wall = System.currentTimeMillis();
    Board b = boards.get(m);
    if (b != null && wall - b.last < EVERY_MS) return;
    if (b == null) {
      b = new Board();
      String key = Integer.toHexString(System.identityHashCode(m));
      for (int i = 0; i < 2; i++) {
        Team t = b.sb.registerNewTeam(teamName(key, i));
        t.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.FOR_OWN_TEAM);
        b.teams[i] = t;
      }
      boards.put(m, b);
    }
    b.last = wall;
    for (UUID id : m.participants()) {
      int squad = m.squadOf(id);
      if (m.isBot(id)) {
        Entity body = module.bots.body(id);
        if (body != null) put(b, body.getUniqueId().toString(), squad);
        Entity tag = module.bots.tag(id);
        if (tag != null) showToAllies(module.plugin, m, tag, squad);
        continue;
      }
      Player p = Bukkit.getPlayer(id);
      if (p == null) continue;
      if (!b.previous.containsKey(id)) b.previous.put(id, p.getScoreboard());
      if (p.getScoreboard() != b.sb) p.setScoreboard(b.sb);
      put(b, p.getName(), squad);
    }
  }

  private static void put(Board b, String entry, int squad) {
    int mine = Math.floorMod(squad, 2);
    if (!b.teams[mine].hasEntry(entry)) b.teams[mine].addEntry(entry); // moves it out of any other team of this board
  }

  private static void showToAllies(org.bukkit.plugin.Plugin plugin, VbMatch m, Entity tag, int squad) {
    for (UUID id : m.participants()) {
      if (m.isBot(id)) continue;
      Player p = Bukkit.getPlayer(id);
      if (p == null) continue;
      boolean want = shows(m.squadOf(id), squad);
      boolean sees = p.canSee(tag);
      if (want && !sees) p.showEntity(plugin, tag);
      else if (!want && sees) p.hideEntity(plugin, tag);
    }
  }

  /** One player leaves the match: their old scoreboard comes back. */
  public void left(ValoBlockModule module, VbMatch m, Player p) {
    if (!Bukkit.isPrimaryThread()) Bukkit.getScheduler().runTask(module.plugin, () -> left(module, m, p));
    else leftNow(m, p);
  }

  private synchronized void leftNow(VbMatch m, Player p) {
    Board b = boards.get(m);
    if (b == null) return;
    for (Team t : b.teams) t.removeEntry(p.getName());
    Scoreboard old = b.previous.remove(p.getUniqueId());
    if (old != null && p.isOnline()) p.setScoreboard(old);
  }

  /** The match is over: every player gets their scoreboard back, the teams go. */
  public void end(ValoBlockModule module, VbMatch m) {
    if (!Bukkit.isPrimaryThread()) Bukkit.getScheduler().runTask(module.plugin, () -> endNow(m));
    else endNow(m);
  }

  private synchronized void endNow(VbMatch m) {
    Board b = boards.remove(m);
    if (b == null) return;
    for (Map.Entry<UUID, Scoreboard> e : b.previous.entrySet()) {
      Player p = Bukkit.getPlayer(e.getKey());
      if (p != null && p.getScoreboard() == b.sb) p.setScoreboard(e.getValue());
    }
    for (Team t : b.teams) t.unregister();
  }
}
