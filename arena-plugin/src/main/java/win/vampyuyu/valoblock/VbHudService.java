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
 * The ValoBlock HUD (design VB-UI2): agent heads, score and the clock on top as a boss bar (a pink fuse bar once the Seed is
 * planted), the last kills under it for 4 s with weapon icons, and the glyph HUD in the action bar: big health and shield,
 * four ability tiles with charge pips, the magazine, the crosshair and the hit marker (and, for the Rift Seed carrier only, the
 * Seed icon and the planting bar), all drawn by the ValoBlock pack's core
 * shader ({@link VbGlyphs}, {@link VbHudState}); the pack also hides the vanilla hearts, hunger, armour and XP bar. It is
 * the only writer of the action bar of a ValoBlock player (other code calls {@link #notice}); the bar is resent only when
 * it changes or every 2 s so it does not fade.
 */
public final class VbHudService implements VbHud {
  static final TextColor ALLY = TextColor.color(0xff9a7a), ENEMY = TextColor.color(0x7fd8ff);
  static final long FEED_MS = 4000, NOTICE_MS = 2000, FRAME_MS = 200, RESEND_MS = 2000;
  static final int FEED_LINES = 3;
  static final long HIT_MS = 700, HEAD_MS = 1000;

  record Feed(UUID killer, UUID victim, String weapon, int killerSquad, int victimSquad, long at, boolean head) {}

  static final class Viewer {
    BossBar top;
    final List<BossBar> feed = new ArrayList<>();
    String lastFeedKey = "";
    String lastAction = "";
    long lastActionAt;
    Component notice;
    long noticeUntil;
    String hit; // "w", "h" or "k" while the hit marker shows
    long hitUntil;
    double lastX, lastZ;
    long movedAt;
  }

  private final NexusArenaEngine plugin;
  private final Map<UUID, Viewer> viewers = new ConcurrentHashMap<>();
  private final Map<VbMatch, Deque<Feed>> feeds = new ConcurrentHashMap<>();
  /** Matches that are over: the clock may still have a frame queued, which must draw nothing (no stuck boss bar). */
  private final Set<VbMatch> cleared = Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));
  private final Map<VbMatch, Long> lastFrame = new ConcurrentHashMap<>();
  /** When each shooter last killed with a head shot: the kill and its hit marker arrive in either order. */
  private final Map<UUID, Long> headKillAt = new ConcurrentHashMap<>();

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
      long now = System.currentTimeMillis();
      boolean head = killer != null && now - headKillAt.getOrDefault(killer, 0L) < HEAD_MS;
      q.addLast(new Feed(killer, victim, weapon == null ? "" : weapon, killer == null ? -1 : m.squadOf(killer), m.squadOf(victim), now, head));
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
    long now = System.currentTimeMillis();
    Viewer v = viewers.computeIfAbsent(shooter, k -> new Viewer());
    v.hit = kill ? "k" : head ? "h" : "w";
    v.hitUntil = now + HIT_MS;
    if (kill && head) {
      headKillAt.put(shooter, now);
      // the kill may already be in the feed (it is recorded while the damage is applied): add the skull to it
      for (Map.Entry<VbMatch, Deque<Feed>> e : feeds.entrySet()) {
        Deque<Feed> q = e.getValue();
        synchronized (q) {
          List<Feed> all = new ArrayList<>(q);
          q.clear();
          for (Feed f : all) q.addLast(shooter.equals(f.killer()) && now - f.at() < HEAD_MS ? new Feed(f.killer(), f.victim(), f.weapon(), f.killerSquad(), f.victimSquad(), f.at(), true) : f);
        }
      }
    }
  }

  // ---------------------------------------------------------------- frames

  @Override
  public void render(VbMatch m, long now, long phaseEndsAt) {
    if (cleared.contains(m)) return;
    long wall = System.currentTimeMillis();
    Long last = lastFrame.get(m);
    if (last != null && wall - last < FRAME_MS) return;
    lastFrame.put(m, wall);
    onServer(() -> draw(m, now, phaseEndsAt, wall));
  }

  void markCleared(VbMatch m) {
    cleared.add(m);
  }

  boolean isCleared(VbMatch m) {
    return cleared.contains(m);
  }

  private void draw(VbMatch m, long now, long phaseEndsAt, long wall) {
    if (cleared.contains(m)) return; // checked here, on the server thread, after any frame queued before clear()
    VbPhase phase = m.vbPhase();
    long left = Math.max(0, (phaseEndsAt - now + 999) / 1000);
    int total = Math.max(1, m.seconds());
    for (UUID id : m.participants()) {
      if (m.isBot(id) || m.hasLeft(id)) continue;
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

  /** One head glyph per squad member: their agent (a crossed-out face once dead), a grey blank until they locked one in. */
  static Component heads(VbMatch m, int squad) {
    return heads(m, squad, -1);
  }

  /** The heads of a squad as seen by a viewer of squad {@code viewerSquad}: the Seed carrier wears the badge for their own squad only. */
  static Component heads(VbMatch m, int squad, int viewerSquad) {
    VbGlyphs g = VbGlyphs.get();
    Component out = Component.empty();
    Set<UUID> alive = m.alive();
    for (UUID id : m.squad(squad)) out = out.append(g.inline(headName(m.agent(id), alive.contains(id), SeedSight.badgeOnHead(m, viewerSquad, id), g)));
    return out;
  }

  /** The head with the seed badge in its corner (a carrier is alive by definition). */
  static String headName(String agent, boolean alive, boolean seed, VbGlyphs g) {
    String plain = headName(agent, alive, g);
    return seed && alive && g.hasInline(plain + "_seed") ? plain + "_seed" : plain;
  }

  /** The inline sprite of a squad member: head_<agent>, head_<agent>_dead or head_none. */
  static String headName(String agent, boolean alive, VbGlyphs g) {
    if (agent == null || agent.isEmpty() || !g.hasInline("head_" + agent)) return "head_none";
    return alive ? "head_" + agent : "head_" + agent + "_dead";
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
        .append(heads(m, mine, mine))
        .append(Component.text("  " + m.score().wins(mine) + "  ", ALLY))
        .append(time)
        .append(Component.text("· " + label, NamedTextColor.GRAY))
        .append(Component.text("  " + m.score().wins(theirs) + "  ", ENEMY))
        .append(heads(m, theirs, mine));
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
    String key = live.stream().map(f -> f.at() + (f.head() ? "h" : "")).reduce("", (a, b) -> a + "," + b);
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
    return Component.text(name(m, f.killer()), killer).append(weaponMark(f)).append(v);
  }

  /** The weapon icon between killer and victim (and a skull for a head shot); the weapon's name when it has no icon. */
  static Component weaponMark(Feed f) {
    VbGlyphs g = VbGlyphs.get();
    String icon = null;
    for (VbWeapon w : VbData.weapons().all()) if (w.name().equals(f.weapon()) && g.hasInline("gun_" + w.id())) icon = "gun_" + w.id();
    Component out = Component.text("  ", NamedTextColor.GOLD);
    out = out.append(icon == null ? Component.text(f.weapon().isEmpty() ? "·" : f.weapon(), NamedTextColor.GOLD) : g.inline(icon));
    if (f.head()) out = out.append(Component.text(" ")).append(g.inline("skull"));
    return out.append(Component.text("  ", NamedTextColor.GOLD));
  }

  private String name(VbMatch m, UUID id) {
    if (m.isBot(id)) return plugin.valoblock.bots.name(id);
    Player p = Bukkit.getPlayer(id);
    return p == null ? "Player" : p.getName();
  }

  /** The values the glyph HUD of this player shows right now. */
  VbHudState.Input input(VbMatch m, UUID id, Player p, Viewer v, long wall) {
    String agent = m.agent(id);
    int[] ammo = VbHudState.parseAmmo(plugin.valoblock.weapons.ammoText(id));
    var loc = p.getLocation();
    if (Math.abs(loc.getX() - v.lastX) + Math.abs(loc.getZ() - v.lastZ) > 0.05) v.movedAt = wall;
    v.lastX = loc.getX();
    v.lastZ = loc.getZ();
    String hit = v.hit != null && wall < v.hitUntil ? v.hit : null;
    return new VbHudState.Input(m.alive().contains(id), m.health(id), m.shield(id), agent == null || agent.isEmpty() ? null : agent,
        plugin.valoblock.agents.charges(id), plugin.valoblock.agents.maxCharges(id), ammo == null ? -1 : ammo[0], ammo == null ? 0 : ammo[1],
        VbHudState.crosshair(WeaponItems.isScope(p.getInventory().getHelmet()), VbHudState.spreadLevel(p.isSneaking(), wall - v.movedAt < 300, !p.isOnGround())), hit,
        SeedSight.hud(m, id, SeedSight.siteAt(VbData.sites(), loc.getX(), loc.getY(), loc.getZ()) != null, wall));
  }

  private void action(Player p, Viewer v, VbMatch m, UUID id, VbPhase phase, long wall) {
    var input = input(m, id, p, v, wall);
    var glyphs = VbHudState.glyphs(input);
    boolean notice = v.notice != null && wall < v.noticeUntil;
    if (!notice) v.notice = null;
    Component c = VbGlyphs.get().glyphs(glyphs);
    if (notice) c = c.append(v.notice);
    else if (!input.alive()) c = Component.text("Eliminated: spectating", NamedTextColor.GRAY);
    // The colours carry the element and the bar width, so the key is the glyph list itself, not its text.
    String key = glyphs + "|" + (notice ? PlainTextComponentSerializer.plainText().serialize(v.notice) : input.alive() ? "" : "dead");
    if (key.equals(v.lastAction) && wall - v.lastActionAt < RESEND_MS) return;
    v.lastAction = key;
    v.lastActionAt = wall;
    p.sendActionBar(c);
  }

  @Override
  public void clear(VbMatch m) {
    markCleared(m);
    feeds.remove(m);
    lastFrame.remove(m);
    for (UUID id : m.participants()) {
      headKillAt.remove(id);
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

  @Override
  public void left(VbMatch m, UUID id) {
    Viewer v = viewers.remove(id);
    Player p = Bukkit.getPlayer(id);
    if (v == null || p == null) return;
    onServer(() -> {
      if (v.top != null) p.hideBossBar(v.top);
      for (BossBar b : v.feed) p.hideBossBar(b);
      p.sendActionBar(Component.empty());
    });
  }
}
