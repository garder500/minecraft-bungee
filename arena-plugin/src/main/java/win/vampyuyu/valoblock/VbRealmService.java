package win.vampyuyu.valoblock;

import java.util.*;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;
import win.vampyuyu.arena.NexusArenaEngine;

/**
 * The Realm rules of the maps, run on the server thread from the combat phase to the end of the round: Hourfall's Time
 * Tide gates (real sand walls, a bell 3 s before), Rootvault's one-way root drops (slow fall in the shafts), Stormbreak's
 * wind pads, gusts and sea. The timing is in {@link TimeTide}, {@link GustClock}, {@link WindPads} and {@link RootDrops};
 * this class only applies it to the world and puts everything back ({@link #stop}) when the round or the match ends.
 */
final class VbRealmService {
  /** What one running map holds. */
  private static final class State {
    final VbMapService.Instance in;
    final VbPlan plan;
    final String realm;
    long tick, last;
    // Hourfall
    final List<int[]> gates;
    final Set<Integer> closed = new HashSet<>(); // gate indexes with a wall in them
    int wantClosedPair = -1;
    // Stormbreak
    final List<WindPads.Pad> pads;
    final Map<UUID, WindPads.Flight> flights = new HashMap<>();
    final Map<UUID, Integer> flightTick = new HashMap<>();
    final Map<UUID, Long> cooldown = new HashMap<>();
    // Rootvault
    final List<RootDrops.Root> roots;
    final Set<UUID> floating = new HashSet<>();

    State(VbMapService.Instance in) {
      this.in = in;
      this.plan = new VbPlan(in.map);
      this.realm = in.map.id();
      this.gates = realm.equals("hourfall") ? TimeTide.gates(plan) : List.of();
      this.pads = realm.equals("stormbreak") ? WindPads.pads(plan) : List.of();
      this.roots = realm.equals("rootvault") ? RootDrops.roots(plan) : List.of();
    }
  }

  private final NexusArenaEngine plugin;
  private final VbMapService maps;
  private final Map<VbMapService.Instance, State> states = new IdentityHashMap<>();

  VbRealmService(NexusArenaEngine plugin, VbMapService maps) {
    this.plugin = plugin;
    this.maps = maps;
  }

  void start() {
    Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1, 1);
  }

  /** The phase changed: the clock starts with the combat phase (and keeps going when the seed is planted), everything stops otherwise. */
  void phaseChanged(VbMapService.Instance in, VbPhase from, VbPhase to) {
    if (to == VbPhase.COMBAT) maps.onServer(() -> begin(in));
    else if (to != VbPhase.PLANTED) stop(in);
  }

  private synchronized void begin(VbMapService.Instance in) {
    stopNow(in);
    State s = new State(in);
    if (s.gates.isEmpty() && s.pads.isEmpty() && s.roots.isEmpty() && !s.realm.equals("stormbreak")) return;
    states.put(in, s);
    if (!s.gates.isEmpty()) setClosedPair(s, TimeTide.closedPair(0));
    announce(s);
  }

  /** The round or the match ended (or the arena is cleared): gates open, flights end, effects go, bots may route anywhere again. Safe to call twice. */
  void stop(VbMapService.Instance in) {
    maps.onServer(() -> stopNow(in));
  }

  synchronized void stopNow(VbMapService.Instance in) {
    State s = states.remove(in);
    if (s == null) return;
    World w = maps.world();
    for (int i = 0; i < s.gates.size(); i++) if (s.closed.contains(i)) fillGate(s, w, i, false);
    s.closed.clear();
    Waypoints g = VbData.waypoints();
    if (g != null) g.setBlocked(Set.of());
    for (UUID id : s.floating) {
      Player p = Bukkit.getPlayer(id);
      if (p != null) p.removePotionEffect(PotionEffectType.SLOW_FALLING);
    }
    s.flights.clear();
  }

  synchronized void stopAll() {
    for (VbMapService.Instance in : new ArrayList<>(states.keySet())) stopNow(in);
  }

  private void announce(State s) {
    String text = switch (s.realm) {
      case "hourfall" -> "Time Tide: the gates swap every 30 s, a bell rings 3 s before.";
      case "rootvault" -> "Roots: step off a canopy branch into a root hole to drop into a site. There is no way back up.";
      case "stormbreak" -> "Crouch on a wind pad to ride the wind. The sea is deadly; a gust blows every 45 s.";
      default -> null;
    };
    if (text == null) return;
    for (Player p : humans(s)) plugin.valoblock.hud.notice(p, Component.text(text, NamedTextColor.GOLD));
  }

  private synchronized void tick() {
    for (State s : new ArrayList<>(states.values())) {
      VbMatch m = s.in.match;
      VbPhase ph = m == null ? null : m.vbPhase();
      if (m == null || (ph != VbPhase.COMBAT && ph != VbPhase.PLANTED)) {
        stopNow(s.in);
        continue;
      }
      long now = ++s.tick;
      try {
        switch (s.realm) {
          case "hourfall" -> tide(s, m, now);
          case "rootvault" -> roots(s, m, now);
          case "stormbreak" -> storm(s, m, now);
          default -> {}
        }
      } finally {
        s.last = now;
      }
    }
  }

  // ------------------------------------------------------------------ helpers

  private List<Player> humans(State s) {
    VbMatch m = s.in.match;
    List<Player> out = new ArrayList<>();
    if (m == null) return out;
    Set<UUID> alive = m.alive();
    for (UUID id : m.participants()) {
      if (m.isBot(id) || !alive.contains(id)) continue;
      Player p = Bukkit.getPlayer(id);
      if (p != null && p.getWorld().getName().equals(VbMapService.WORLD)) out.add(p);
    }
    return out;
  }

  /** Everyone of the match, alive or not (for messages). */
  private List<Player> audience(State s) {
    VbMatch m = s.in.match;
    List<Player> out = new ArrayList<>();
    if (m == null) return out;
    for (UUID id : m.participants()) {
      if (m.isBot(id)) continue;
      Player p = Bukkit.getPlayer(id);
      if (p != null) out.add(p);
    }
    return out;
  }

  private double relX(State s, Player p) {
    return p.getLocation().getX() - s.in.ox();
  }

  // ------------------------------------------------------------------ Hourfall: the Time Tide

  private void tide(State s, VbMatch m, long now) {
    if (TimeTide.bellBetween(s.last, now)) {
      int opening = TimeTide.closedPair(now), closing = TimeTide.openPair(now);
      String text = "The Time Tide turns in 3 s: the " + (opening == 0 ? "north" : "south") + " gates open, the " + (closing == 0 ? "north" : "south") + " gates close.";
      for (Player p : audience(s)) {
        plugin.valoblock.hud.notice(p, Component.text("🔔 " + text, NamedTextColor.GOLD));
        p.playSound(p.getLocation(), Sound.BLOCK_BELL_USE, SoundCategory.AMBIENT, 1.5f, 0.8f);
      }
    }
    if (TimeTide.flipBetween(s.last, now)) setClosedPair(s, TimeTide.closedPair(now));
    // a gate waits while someone stands in it, then closes
    if (s.wantClosedPair >= 0) setClosedPair(s, s.wantClosedPair);
  }

  /** Opens the other pair and closes this one (a gate with a player in it stays open until the player leaves). */
  private void setClosedPair(State s, int pair) {
    World w = maps.world();
    if (w == null) return;
    s.wantClosedPair = -1;
    List<Player> people = s.in.match == null ? List.of() : humans(s);
    for (int i = 0; i < s.gates.size(); i++) {
      boolean shouldClose = TimeTide.pairOf(s.plan, s.gates.get(i)[1]) == pair;
      if (!shouldClose) {
        if (s.closed.remove(i)) fillGate(s, w, i, false);
      } else if (!s.closed.contains(i)) {
        if (occupied(s, i, people)) {
          s.wantClosedPair = pair;
        } else {
          fillGate(s, w, i, true);
          s.closed.add(i);
        }
      }
    }
    Waypoints g = VbData.waypoints();
    if (g != null) g.setBlocked(TimeTide.nodeIds(s.plan, pair));
  }

  private boolean occupied(State s, int gate, List<Player> people) {
    int[] g = s.gates.get(gate);
    double x0 = g[0] * s.plan.cell - 0.4, x1 = (g[0] + 1) * s.plan.cell + 0.4, z0 = g[1] * s.plan.cell - 0.4, z1 = (g[1] + 1) * s.plan.cell + 0.4;
    for (Player p : people) {
      double x = relX(s, p), z = p.getLocation().getZ(), y = p.getLocation().getY() - VbMapService.FEET_Y;
      if (x >= x0 && x <= x1 && z >= z0 && z <= z1 && y > -1.5 && y < 4.5) return true;
    }
    return false;
  }

  /** A gate is a sand wall filling the doorway cell, 3 high under the arch (real blocks: collision and no sight). */
  private void fillGate(State s, World w, int gate, boolean wall) {
    if (w == null) return;
    int[] g = s.gates.get(gate);
    int cell = s.plan.cell, ox = s.in.ox();
    for (int dx = 0; dx < cell; dx++)
      for (int dz = 0; dz < cell; dz++)
        for (int y = 0; y < TimeTide.GATE_HEIGHT; y++) {
          boolean centre = dx >= 1 && dx <= cell - 2 && dz >= 1 && dz <= cell - 2 && (y == 1 || y == 2);
          Material m = !wall ? Material.AIR : centre ? Material.CHISELED_SANDSTONE : Material.SMOOTH_SANDSTONE;
          w.getBlockAt(ox + g[0] * cell + dx, VbMapService.FEET_Y + y, g[1] * cell + dz).setType(m, false);
        }
    if (wall) {
      Location at = new Location(w, ox + (g[0] + 0.5) * cell, VbMapService.FEET_Y + 1, (g[1] + 0.5) * cell);
      w.playSound(at, Sound.BLOCK_SAND_PLACE, SoundCategory.BLOCKS, 2f, 0.6f);
    }
  }

  // ------------------------------------------------------------------ Rootvault: one-way drops

  private void roots(State s, VbMatch m, long now) {
    if (now % 2 != 0) return;
    for (Player p : humans(s)) {
      double x = relX(s, p), y = p.getLocation().getY() - VbMapService.FEET_Y, z = p.getLocation().getZ();
      if (RootDrops.inShaft(s.roots, x, y, z)) {
        p.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, RootDrops.EFFECT_TICKS, 0, false, false, false));
        s.floating.add(p.getUniqueId());
      } else if (s.floating.remove(p.getUniqueId())) {
        p.removePotionEffect(PotionEffectType.SLOW_FALLING);
        p.setFallDistance(0);
      }
    }
  }

  // ------------------------------------------------------------------ Stormbreak: pads, gusts, sea

  private void storm(State s, VbMatch m, long now) {
    World w = maps.world();
    if (w == null) return;
    int ox = s.in.ox();
    if (GustClock.announceBetween(s.last, now))
      for (Player p : audience(s)) {
        plugin.valoblock.hud.notice(p, Component.text("A gust is coming in 3 s: get off the bridges!", NamedTextColor.AQUA));
        p.playSound(p.getLocation(), Sound.ENTITY_PHANTOM_FLAP, SoundCategory.AMBIENT, 1.5f, 0.5f);
      }
    long gust = GustClock.pushing(now);
    for (Player p : humans(s)) {
      UUID id = p.getUniqueId();
      double x = relX(s, p), y = p.getLocation().getY() - VbMapService.FEET_Y, z = p.getLocation().getZ();
      if (x < 0 || z < 0 || x > s.plan.width || z > s.plan.depth) continue;
      // the sea
      if (WindPads.drowned(y) && !s.flights.containsKey(id)) {
        w.spawnParticle(Particle.SPLASH, p.getLocation(), 40, 0.4, 0.2, 0.4, 0.1);
        w.playSound(p.getLocation(), Sound.ENTITY_PLAYER_SPLASH_HIGH_SPEED, SoundCategory.PLAYERS, 1.2f, 1f);
        m.kill(id, null);
        continue;
      }
      // a flight in progress
      WindPads.Flight f = s.flights.get(id);
      if (f != null) {
        int t = s.flightTick.merge(id, 1, Integer::sum) - 1;
        if (f.done(t)) {
          s.flights.remove(id);
          s.flightTick.remove(id);
          p.setFallDistance(0);
          p.setVelocity(new Vector());
        } else {
          double[] v = f.velocity(t, new double[] {x, y, z});
          p.setVelocity(new Vector(v[0], v[1], v[2]));
          p.setFallDistance(0);
        }
        continue;
      }
      // the gust, on a one-cell bridge
      if (gust > 0 && Math.abs(y) < 0.4) {
        char axis = GustClock.pushAxis(s.plan, s.plan.col((int) Math.floor(x)), s.plan.col((int) Math.floor(z)));
        if (axis != 0) {
          double[] push = GustClock.push(axis, gust);
          p.setVelocity(p.getVelocity().add(new Vector(push[0], 0.04, push[1])));
        }
      }
      // a pad: crouch on it
      if (p.isSneaking() && s.cooldown.getOrDefault(id, 0L) <= now) {
        WindPads.Pad pad = WindPads.padAt(s.plan, s.pads, x, y, z);
        if (pad != null) {
          int cell = s.plan.cell;
          double[] to = {(pad.toCol() + 0.5) * cell, s.in.map.standY(s.plan.at(pad.toCol(), pad.toRow())), (pad.toRow() + 0.5) * cell};
          s.flights.put(id, new WindPads.Flight(new double[] {x, y, z}, to));
          s.flightTick.put(id, 0);
          s.cooldown.put(id, now + WindPads.FLIGHT_TICKS + WindPads.COOLDOWN_TICKS);
          w.playSound(p.getLocation(), Sound.ENTITY_PHANTOM_FLAP, SoundCategory.PLAYERS, 2f, 1.4f);
          w.spawnParticle(Particle.CLOUD, p.getLocation(), 30, 0.5, 0.1, 0.5, 0.15);
        }
      }
    }
    if (now % 10 == 0)
      for (WindPads.Pad pad : s.pads)
        w.spawnParticle(Particle.CLOUD, ox + (pad.col() + 0.5) * s.plan.cell, VbMapService.FEET_Y + 0.6, (pad.row() + 0.5) * s.plan.cell, 2, 0.8, 0.2, 0.8, 0.01);
  }
}
