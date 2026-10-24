package win.vampyuyu.arena;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

/**
 * What a ballot looks like in the world, with no plugin on the client: a counter over every player
 * who has votes against them ("✖ 3", it pops and chimes when it grows) seen by whoever may see the
 * tally, and a glowing frame around the player each voter picked, seen by that voter alone. All
 * of it is display entities hidden by default and shown per viewer, so it needs no packets.
 */
final class VoteMarks {
  private static final int GOLD = 0xffd36e, HUNT = 0xff5a5a;

  /** Size of a counter at rest: big enough to read across the table. */
  private static final float REST = 1.6f;

  /** A counter over one player. */
  static final class Tag {
    volatile Entity entity;
    volatile boolean dead;
    int count;
    final Set<UUID> shown = ConcurrentHashMap.newKeySet();
  }

  /** The glowing frame one voter sees around their pick. */
  static final class Glow {
    volatile Entity entity;
    volatile boolean dead;
    UUID target;
    boolean shown;
  }

  private final NexusArenaEngine plugin;

  VoteMarks(NexusArenaEngine plugin) {
    this.plugin = plugin;
  }

  // ------------------------------------------------------------------ geometry

  private static Location where(ArenaSession s, UUID id) {
    World w = Bukkit.getWorld(s.config.worldName());
    var seat = s.seats.get(id);
    if (w != null && seat != null) return new Location(w, seat.at().x(), seat.at().y(), seat.at().z());
    Player p = Bukkit.getPlayer(id);
    return p == null ? null : p.getLocation();
  }

  /** Practice bodies stand even on a chair seat; a seated player is shorter. */
  private static boolean seated(ArenaSession s, UUID id) {
    var seat = s.seats.get(id);
    return seat != null && seat.sit() && !s.bots.contains(id);
  }

  // ------------------------------------------------------------------ tick

  /** Brings the markers of a session in line with the game; runs on the clock, five times a second. */
  void tick(ArenaSession s) {
    if (!(s.game instanceof WerewolfGame game)
        || s.stopping
        || s.finishing
        || !s.presentation.isDone()
        || !plugin.gui.inGame(s)) {
      clear(s);
      return;
    }
    Set<UUID> audience = new HashSet<>();
    for (UUID id : s.players.keySet()) if (game.tallyVisible(id)) audience.add(id);
    Map<UUID, Integer> totals = audience.isEmpty() ? Map.of() : game.votes(audience.iterator().next());
    int color = game.phase().equals("NIGHT") ? HUNT : GOLD;

    // Counters.
    for (var entry : totals.entrySet()) {
      if (entry.getValue() <= 0 || !game.alive().contains(entry.getKey())) continue;
      counter(s, entry.getKey(), entry.getValue(), audience, color);
    }
    for (var tag : List.copyOf(s.voteTags.entrySet())) {
      int now = totals.getOrDefault(tag.getKey(), 0);
      if (now <= 0 || !game.alive().contains(tag.getKey()) || audience.isEmpty()) {
        s.voteTags.remove(tag.getKey());
        retire(tag.getValue());
      }
    }

    // Frames: only the voter sees theirs.
    Map<UUID, UUID> picks = new HashMap<>();
    for (UUID voter : audience) {
      UUID pick = game.alive().contains(voter) ? game.ballot(voter) : null;
      if (pick != null && game.alive().contains(pick) && Bukkit.getPlayer(voter) != null)
        picks.put(voter, pick);
    }
    for (var voter : List.copyOf(s.voteGlows.entrySet())) {
      UUID pick = picks.get(voter.getKey());
      if (pick == null || !pick.equals(voter.getValue().target)) {
        s.voteGlows.remove(voter.getKey());
        retire(voter.getValue());
      }
    }
    for (var pick : picks.entrySet()) frame(s, pick.getKey(), pick.getValue(), color);
  }

  // ------------------------------------------------------------------ counters

  private void counter(ArenaSession s, UUID target, int count, Set<UUID> audience, int color) {
    Tag tag = s.voteTags.get(target);
    Location at = where(s, target);
    if (at == null) return;
    if (tag == null) {
      tag = new Tag();
      tag.count = count;
      s.voteTags.put(target, tag);
      final Tag created = tag;
      double lift = seated(s, target) ? 2.0 : 2.5;
      spawn(
          at.clone().add(0, lift, 0),
          loc ->
              loc.getWorld()
                  .spawn(
                      loc,
                      TextDisplay.class,
                      t -> {
                        base(t);
                        t.setBillboard(Display.Billboard.CENTER);
                        t.setBackgroundColor(Color.fromARGB(170, 24, 10, 30));
                        t.setShadowed(true);
                        t.setAlignment(TextDisplay.TextAlignment.CENTER);
                        t.setTransformation(scale(REST));
                        t.text(label(count, color));
                      }),
          e -> adopt(created, e));
      return;
    }
    if (tag.entity == null) return; // still spawning
    if (tag.count != count) {
      boolean grew = count > tag.count;
      tag.count = count;
      Entity e = tag.entity;
      e.getScheduler()
          .run(
              plugin,
              t -> {
                if (!(e instanceof TextDisplay text)) return;
                text.text(label(count, color));
                if (grew) pop(text);
              },
              null);
      if (grew) chime(s, target, count, audience);
    }
    for (UUID viewer : audience) {
      if (!tag.shown.add(viewer)) continue;
      Player p = Bukkit.getPlayer(viewer);
      if (p != null) show(p, tag.entity);
    }
    for (UUID viewer : List.copyOf(tag.shown)) {
      if (audience.contains(viewer)) continue;
      tag.shown.remove(viewer);
      Player p = Bukkit.getPlayer(viewer);
      if (p != null) hide(p, tag.entity);
    }
  }

  private static Component label(int count, int color) {
    return Component.text("✖ " + count, TextColor.color(color));
  }

  /** The counter swells and settles, so a new vote is noticed. */
  private void pop(TextDisplay t) {
    t.setInterpolationDelay(0);
    t.setInterpolationDuration(3);
    t.setTransformation(scale(REST * 1.5f));
    t.getScheduler()
        .runDelayed(
            plugin,
            x -> {
              t.setInterpolationDelay(0);
              t.setInterpolationDuration(6);
              t.setTransformation(scale(REST));
            },
            null,
            4);
  }

  private static Transformation scale(float k) {
    return new Transformation(new Vector3f(), new AxisAngle4f(), new Vector3f(k, k, k), new AxisAngle4f());
  }

  /** A chime for the table, a low note for the one being voted against. */
  private void chime(ArenaSession s, UUID target, int count, Set<UUID> audience) {
    for (UUID viewer : audience) {
      Player p = Bukkit.getPlayer(viewer);
      if (p == null) continue;
      boolean me = viewer.equals(target);
      plugin.onPlayer(
          p,
          () -> {
            if (me) p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.9f, 0.5f);
            else
              p.playSound(
                  p.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 0.5f, Math.min(2f, 0.7f + 0.15f * count));
            return null;
          });
    }
  }

  // ------------------------------------------------------------------ frames

  private void frame(ArenaSession s, UUID voter, UUID target, int color) {
    Glow glow = s.voteGlows.get(voter);
    if (glow == null) {
      Location at = where(s, target);
      if (at == null) return;
      glow = new Glow();
      glow.target = target;
      s.voteGlows.put(voter, glow);
      final Glow created = glow;
      boolean sits = seated(s, target);
      float height = sits ? 1.5f : 2.0f;
      spawn(
          at,
          loc ->
              loc.getWorld()
                  .spawn(
                      loc,
                      BlockDisplay.class,
                      b -> {
                        base(b);
                        b.setBlock(Material.YELLOW_STAINED_GLASS.createBlockData());
                        b.setTransformation(
                            new Transformation(
                                new Vector3f(-0.4f, -0.05f, -0.4f),
                                new AxisAngle4f(),
                                new Vector3f(0.8f, height, 0.8f),
                                new AxisAngle4f()));
                        b.setGlowing(true);
                        b.setGlowColorOverride(Color.fromRGB(color));
                      }),
          e -> adopt(created, e));
      return;
    }
    if (glow.entity == null || glow.shown) return;
    Player p = Bukkit.getPlayer(voter);
    if (p == null) return;
    glow.shown = true;
    show(p, glow.entity);
  }

  // ------------------------------------------------------------------ entities

  private static void base(Display d) {
    d.setVisibleByDefault(false);
    d.setPersistent(false);
    d.setInvulnerable(true);
    d.setGravity(false);
    d.setBrightness(new Display.Brightness(15, 15));
    d.setViewRange(0.9f);
  }

  private interface Maker {
    Entity make(Location at);
  }

  private void spawn(Location at, Maker maker, java.util.function.Consumer<Entity> done) {
    Bukkit.getRegionScheduler()
        .execute(
            plugin,
            at.getWorld(),
            at.getBlockX() >> 4,
            at.getBlockZ() >> 4,
            () -> {
              try {
                done.accept(maker.make(at));
              } catch (RuntimeException e) {
                plugin.getLogger().warning("Vote marker: " + e.getMessage());
              }
            });
  }

  private void show(Player p, Entity e) {
    plugin.onPlayer(
        p,
        () -> {
          try {
            p.showEntity(plugin, e);
          } catch (RuntimeException ignored) {
            // Cosmetic: a marker that cannot be shown is just not drawn.
          }
          return null;
        });
  }

  private void hide(Player p, Entity e) {
    plugin.onPlayer(
        p,
        () -> {
          try {
            p.hideEntity(plugin, e);
          } catch (RuntimeException ignored) {
          }
          return null;
        });
  }

  /** A marker that was retired while its entity was still being spawned is removed on arrival. */
  private void adopt(Object marker, Entity e) {
    if (marker instanceof Tag t) {
      t.entity = e;
      if (t.dead) discard(e);
    } else if (marker instanceof Glow g) {
      g.entity = e;
      if (g.dead) discard(e);
    }
  }

  private void retire(Tag t) {
    t.dead = true;
    discard(t.entity);
  }

  private void retire(Glow g) {
    g.dead = true;
    discard(g.entity);
  }

  private void discard(Entity e) {
    if (e == null) return;
    e.getScheduler().run(plugin, t -> e.remove(), null);
  }

  /** Removes every marker of the session; safe to call again and again. */
  void clear(ArenaSession s) {
    if (s.voteTags.isEmpty() && s.voteGlows.isEmpty()) return;
    for (var tag : List.copyOf(s.voteTags.values())) retire(tag);
    for (var glow : List.copyOf(s.voteGlows.values())) retire(glow);
    s.voteTags.clear();
    s.voteGlows.clear();
  }
}
