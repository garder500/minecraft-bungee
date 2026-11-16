package win.vampyuyu.valoblock;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import win.vampyuyu.arena.NexusArenaEngine;

/**
 * The ValoBlock HUD as Adventure text (boss bars and the action bar), laid out like VB-HUD: team pips,
 * score and the clock on top (a pink fuse bar once the Seed is planted), the last kills under it for 4 s,
 * and health, shield, ability charges and ammo at the bottom. It is the only writer of the action bar of
 * a ValoBlock player (other code calls {@link #notice}); the bar is resent only when it changes or every
 * 2 s so it does not fade. Not the pack's glyph HUD: that needs new sprites, geometry and a shader entry.
 */
public final class VbHudService implements VbHud {
  static final TextColor ALLY = TextColor.color(0xff9a7a), ENEMY = TextColor.color(0x7fd8ff);
  static final long FEED_MS = 4000, NOTICE_MS = 2000, FRAME_MS = 200, RESEND_MS = 2000;
  static final int FEED_LINES = 3;

  private record Feed(UUID killer, UUID victim, String weapon, int killerSquad, int victimSquad, long at) {}

  private static final class Viewer {
    BossBar top;
    final List<BossBar> feed = new ArrayList<>();
    String lastFeedKey = "";
    String lastAction = "";
    long lastActionAt;
    Component notice;
    long noticeUntil;
  }

  private final NexusArenaEngine plugin;
  private final Map<UUID, Viewer> viewers = new ConcurrentHashMap<>();
  private final Map<VbMatch, Deque<Feed>> feeds = new ConcurrentHashMap<>();
  private final Map<VbMatch, Long> lastFrame = new ConcurrentHashMap<>();

  public VbHudService(NexusArenaEngine plugin) {
    this.plugin = plugin;
  }

  private void onServer(Runnable r) {
    if (Bukkit.isPrimaryThread()) r.run();
    else Bukkit.getScheduler().runTask(plugin, r);
  }

  // ---------------------------------------------------------------- events

  @Override
  public void killed(VbMatch m, UUID victim, UUID killer, String weapon) {
    Deque<Feed> q = feeds.computeIfAbsent(m, k -> new ArrayDeque<>());
    synchronized (q) {
      q.addLast(new Feed(killer, victim, weapon == null ? "" : weapon, killer == null ? -1 : m.squadOf(killer), m.squadOf(victim), System.currentTimeMillis()));
      while (q.size() > FEED_LINES) q.removeFirst();
    }
  }

  @Override
  public void notice(Player p, Component text) {
    Viewer v = viewers.computeIfAbsent(p.getUniqueId(), k -> new Viewer());
    v.notice = text;
    v.noticeUntil = System.currentTimeMillis() + NOTICE_MS;
    v.lastAction = ""; // draw it at the next frame
  }

  @Override
  public void hitMarker(UUID shooter, boolean head, boolean kill) {
    Player p = Bukkit.getPlayer(shooter);
    if (p == null) return;
    Viewer v = viewers.computeIfAbsent(shooter, k -> new Viewer());
    v.notice = kill ? Component.text("✖ ELIMINATED", NamedTextColor.RED) : head ? Component.text("● HEADSHOT", NamedTextColor.YELLOW) : Component.text("● HIT", NamedTextColor.WHITE);
    v.noticeUntil = System.currentTimeMillis() + 700;
    v.lastAction = "";
  }

  // ---------------------------------------------------------------- frames

  @Override
  public void render(VbMatch m, long now, long phaseEndsAt) {
    long wall = System.currentTimeMillis();
    Long last = lastFrame.get(m);
    if (last != null && wall - last < FRAME_MS) return;
    lastFrame.put(m, wall);
    onServer(() -> draw(m, now, phaseEndsAt, wall));
  }

  private void draw(VbMatch m, long now, long phaseEndsAt, long wall) {
    VbPhase phase = m.vbPhase();
    long left = Math.max(0, (phaseEndsAt - now + 999) / 1000);
    int total = Math.max(1, m.seconds());
    for (UUID id : m.participants()) {
      if (m.isBot(id)) continue;
      Player p = Bukkit.getPlayer(id);
      if (p == null) continue;
      Viewer v = viewers.computeIfAbsent(id, k -> new Viewer());
      int mine = m.squadOf(id);
      top(p, v, m, mine, phase, left, Math.min(1.0, left / (double) total));
      feedBars(p, v, m, mine, wall);
      action(p, v, m, id, phase, wall);
    }
  }

  static String clock(long seconds) {
    return String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60);
  }

  private static Component pips(VbMatch m, int squad, TextColor team) {
    Component out = Component.empty();
    Set<UUID> alive = m.alive();
    for (UUID id : m.squad(squad)) out = out.append(Component.text("■", alive.contains(id) ? team : NamedTextColor.DARK_GRAY));
    return out;
  }

  /** The top line, as plain data so a test can read it. */
  static Component topLine(VbMatch m, int mine, VbPhase phase, long left) {
    int theirs = 1 - mine;
    String label = switch (phase) {
      case AGENT_SELECT -> "AGENT SELECT";
      case BUY -> "BUY PHASE";
      case COMBAT -> "ROUND " + m.round();
      case PLANTED -> "SEED PLANTED";
      case ROUND_END -> "ROUND OVER";
      default -> m.winner().isEmpty() ? "MATCH OVER" : "MATCH OVER \u00B7 " + m.winner().toUpperCase(Locale.ROOT);
    };
    Component time = phase == VbPhase.END ? Component.empty() : Component.text(clock(left) + " ", phase == VbPhase.PLANTED ? NamedTextColor.LIGHT_PURPLE : NamedTextColor.WHITE);
    return Component.empty()
        .append(pips(m, mine, ALLY))
        .append(Component.text("  " + m.score().wins(mine) + "  ", ALLY))
        .append(time)
        .append(Component.text("· " + label, NamedTextColor.GRAY))
        .append(Component.text("  " + m.score().wins(theirs) + "  ", ENEMY))
        .append(pips(m, theirs, ENEMY));
  }

  private void top(Player p, Viewer v, VbMatch m, int mine, VbPhase phase, long left, double fraction) {
    BossBar.Color color =
        switch (phase) {
          case PLANTED -> BossBar.Color.PINK;
          case AGENT_SELECT -> BossBar.Color.PURPLE;
          case BUY -> BossBar.Color.GREEN;
          case ROUND_END, END -> BossBar.Color.YELLOW;
          default -> BossBar.Color.WHITE;
        };
    Component line = topLine(m, mine, phase, left);
    // The fuse drains toward the explosion; the other bars show the time left in the phase.
    float progress = (float) Math.max(0, Math.min(1, fraction));
    if (v.top == null) {
      v.top = BossBar.bossBar(line, progress, color, BossBar.Overlay.PROGRESS);
      p.showBossBar(v.top);
    } else v.top.name(line).progress(progress).color(color);
  }

  private void feedBars(Player p, Viewer v, VbMatch m, int mine, long wall) {
    List<Feed> live = new ArrayList<>();
    Deque<Feed> q = feeds.get(m);
    if (q != null)
      synchronized (q) {
        for (Feed f : q) if (wall - f.at() < FEED_MS) live.add(f);
      }
    String key = live.stream().map(f -> f.at() + "").reduce("", (a, b) -> a + "," + b);
    if (key.equals(v.lastFeedKey)) return;
    v.lastFeedKey = key;
    for (BossBar b : v.feed) p.hideBossBar(b);
    v.feed.clear();
    if (live.isEmpty()) return;
    // Boss bars stack in the order they are shown: the score bar first, then the feed lines.
    if (v.top != null) p.hideBossBar(v.top);
    if (v.top != null) p.showBossBar(v.top);
    for (Feed f : live) {
      BossBar b = BossBar.bossBar(feedLine(m, f, mine), 0f, BossBar.Color.WHITE, BossBar.Overlay.PROGRESS);
      v.feed.add(b);
      p.showBossBar(b);
    }
  }

  private Component feedLine(VbMatch m, Feed f, int mine) {
    TextColor victim = f.victimSquad() == mine ? ALLY : ENEMY;
    TextColor killer = f.killerSquad() == mine ? ALLY : ENEMY;
    Component v = Component.text(name(m, f.victim()), victim);
    if (f.killer() == null) return v.append(Component.text(" was eliminated", NamedTextColor.GRAY));
    return Component.text(name(m, f.killer()), killer)
        .append(Component.text(f.weapon().isEmpty() ? " · " : " · " + f.weapon() + " · ", NamedTextColor.GOLD))
        .append(v);
  }

  private String name(VbMatch m, UUID id) {
    if (m.isBot(id)) return plugin.valoblock.bots.name(id);
    Player p = Bukkit.getPlayer(id);
    return p == null ? "Player" : p.getName();
  }

  /** The bottom line: health and shield, the four ability slots, the magazine. */
  Component status(VbMatch m, UUID id) {
    Component out = Component.text("❤ " + m.health(id), NamedTextColor.WHITE);
    int shield = m.shield(id);
    if (shield > 0) out = out.append(Component.text("  ◆ " + shield, TextColor.color(0x7fc8e8)));
    int[] c = plugin.valoblock.agents.charges(id);
    if (c != null) {
      out = out.append(Component.text("    "));
      TextColor[] colors = {TextColor.color(0xffd36a), TextColor.color(0xffd36a), TextColor.color(0x5ad07a), TextColor.color(0xc9a8ff)};
      for (int i = 0; i < 4; i++) {
        boolean ready = c[i] > 0;
        String text = (i == 3 ? "ULT" : "" + (4 + i)) + (i == 3 ? "" : ":" + c[i]);
        out = out.append(Component.text("[" + text + "] ", ready ? colors[i] : NamedTextColor.DARK_GRAY));
      }
    }
    String ammo = plugin.valoblock.weapons.ammoText(id);
    if (ammo != null) out = out.append(Component.text("   " + ammo, NamedTextColor.WHITE));
    return out;
  }

  private void action(Player p, Viewer v, VbMatch m, UUID id, VbPhase phase, long wall) {
    Component c;
    if (v.notice != null && wall < v.noticeUntil) c = v.notice;
    else {
      v.notice = null;
      c = m.alive().contains(id) ? status(m, id) : Component.text("Eliminated: spectating", NamedTextColor.GRAY);
    }
    String key = PlainTextComponentSerializer.plainText().serialize(c);
    if (key.equals(v.lastAction) && wall - v.lastActionAt < RESEND_MS) return;
    v.lastAction = key;
    v.lastActionAt = wall;
    p.sendActionBar(c);
  }

  @Override
  public void clear(VbMatch m) {
    feeds.remove(m);
    lastFrame.remove(m);
    for (UUID id : m.participants()) {
      Viewer v = viewers.remove(id);
      Player p = Bukkit.getPlayer(id);
      if (v == null || p == null) continue;
      onServer(() -> {
        if (v.top != null) p.hideBossBar(v.top);
        for (BossBar b : v.feed) p.hideBossBar(b);
        p.sendActionBar(Component.empty());
      });
    }
  }
}
