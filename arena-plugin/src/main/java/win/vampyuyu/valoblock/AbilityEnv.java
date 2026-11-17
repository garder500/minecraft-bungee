package win.vampyuyu.valoblock;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;
import win.vampyuyu.arena.ArenaSession;
import win.vampyuyu.arena.NexusArenaEngine;

/** The building blocks abilities are made of: who is an enemy, where the player aims, thrown things, displays, clouds, reveals, temporary blocks. */
final class AbilityEnv {
  final NexusArenaEngine plugin;
  final Effects fx;
  final TempBlocks temp = new TempBlocks();
  final Reveals reveals;
  final VbCombat combat;
  final Random random = new Random();

  /** Players held still by Hold the Moment. */
  final Set<UUID> frozen = java.util.concurrent.ConcurrentHashMap.newKeySet();
  /** Sahrin's Rewind marks and the tick they expire. */
  record Mark(Location where, int until) {}

  final java.util.Map<UUID, Mark> marks = new java.util.concurrent.ConcurrentHashMap<>();
  /** Vesper's ghosts. */
  record Ghost(org.bukkit.entity.Mannequin body, UUID owner, int squad, Effects.Fx fx, int revealTicks) {}

  final List<Ghost> ghosts = new ArrayList<>();
  /** Murk's gas wall by owner, to switch on and off. */
  final java.util.Map<UUID, Runnable> walls = new java.util.concurrent.ConcurrentHashMap<>();
  /** Whether each owner's gas wall is switched on (for /vb debug wall). */
  final java.util.Map<UUID, Boolean> wallOn = new java.util.concurrent.ConcurrentHashMap<>();
  /** Something a player placed that stays a while (a smoke, a wall, a field): shown on the tactical map. */
  record Placed(UUID owner, String ability, Location where) {}

  final List<Placed> placed = new ArrayList<>();
  int now;

  /** Lists a placed thing until its effect ends. */
  void track(Ctx c, Location where, Effects.Fx f) {
    Placed p = new Placed(c.id(), c.ab().id(), where.clone());
    placed.add(p);
    f.onEnd(() -> placed.remove(p));
  }

  /** What this player has placed that is still there. */
  List<Placed> placedBy(UUID owner) {
    List<Placed> out = new ArrayList<>();
    for (Placed p : placed) if (p.owner().equals(owner)) out.add(p);
    return out;
  }

  AbilityEnv(NexusArenaEngine plugin) {
    this.plugin = plugin;
    this.fx = new Effects(plugin.getLogger());
    this.reveals = new Reveals(plugin);
    this.combat = new VbCombat(plugin);
  }

  /** One use of an ability: who, in which match, which agent and ability. */
  record Ctx(Player p, VbMatch m, VbAgent agent, VbAbility ab, AbilityEnv env, List<Location> spots) {
    /** A use with no spots picked: the ability aims or throws in the world. */
    Ctx(Player p, VbMatch m, VbAgent agent, VbAbility ab, AbilityEnv env) {
      this(p, m, agent, ab, env, List.of());
    }

    /** The spot number {@code i} picked on the tactical map, or null when the ability is thrown or aimed. */
    Location spot(int i) {
      return i < spots.size() ? spots.get(i).clone() : null;
    }

    double n(String key, double def) {
      return ab.num(key, def);
    }

    UUID id() {
      return p.getUniqueId();
    }

    int ticks(String key, double defSeconds) {
      return (int) Math.round(n(key, defSeconds) * 20);
    }
  }

  // ------------------------------------------------------------------ who

  private boolean fighting(Player p, VbMatch m) {
    return p.isOnline() && p.getGameMode() != GameMode.SPECTATOR && m.alive().contains(p.getUniqueId());
  }

  /** Living fighters of the other squad: human players and the bodies of bots (hit them through {@link VbCombat}). */
  List<LivingEntity> enemies(VbMatch m, UUID owner) {
    List<LivingEntity> out = new ArrayList<>();
    int squad = m.squadOf(owner);
    for (UUID id : m.alive()) {
      if (m.squadOf(id) == squad) continue;
      if (m.isBot(id)) {
        LivingEntity body = plugin.valoblock.bots.body(id);
        if (body != null) out.add(body);
        continue;
      }
      Player p = Bukkit.getPlayer(id);
      if (p != null && fighting(p, m)) out.add(p);
    }
    return out;
  }

  /** Living human players of the owner's squad, the owner included. */
  List<Player> allies(VbMatch m, UUID owner) {
    List<Player> out = new ArrayList<>();
    int squad = m.squadOf(owner);
    for (UUID id : m.alive()) {
      if (m.isBot(id) || m.squadOf(id) != squad) continue;
      Player p = Bukkit.getPlayer(id);
      if (p != null && fighting(p, m)) out.add(p);
    }
    return out;
  }

  Set<UUID> team(VbMatch m, UUID owner) {
    Set<UUID> ids = new HashSet<>();
    for (Player p : allies(m, owner)) ids.add(p.getUniqueId());
    return ids;
  }

  /** Every living human of the match. */
  List<Player> everyone(VbMatch m) {
    List<Player> out = new ArrayList<>();
    for (UUID id : m.alive()) {
      Player p = m.isBot(id) ? null : Bukkit.getPlayer(id);
      if (p != null && fighting(p, m)) out.add(p);
    }
    return out;
  }

  /** Whether a player is within a radius of a point on the ground plane, a little above or below it. */
  static boolean near(LivingEntity v, Location c, double r) {
    Location l = v.getLocation();
    double dx = l.getX() - c.getX(), dz = l.getZ() - c.getZ(), dy = l.getY() - c.getY();
    return dx * dx + dz * dz <= r * r && dy > -1.5 && dy < 3.5;
  }

  // ------------------------------------------------------------------ where

  /** Where the crosshair meets a block within {@code range}; the end of the range, dropped to the floor, when it meets none. */
  Location aim(Player p, double range) {
    Location eye = p.getEyeLocation();
    RayTraceResult r = p.getWorld().rayTraceBlocks(eye, eye.getDirection(), range, FluidCollisionMode.NEVER, true);
    if (r != null) {
      Vector pos = r.getHitPosition().add(r.getHitBlockFace().getDirection().multiply(0.05));
      return pos.toLocation(p.getWorld());
    }
    return floor(eye.clone().add(eye.getDirection().multiply(range)));
  }

  /** The point straight below {@code l} on the first solid block (within 12 blocks), a hair above its top. */
  Location floor(Location l) {
    RayTraceResult r = l.getWorld().rayTraceBlocks(l, new Vector(0, -1, 0), 12, FluidCollisionMode.NEVER, true);
    if (r == null) return l.clone();
    return r.getHitPosition().toLocation(l.getWorld()).add(0, 0.05, 0);
  }

  /** Whether nothing solid lies between two points. */
  static boolean clear(Location a, Location b) {
    Vector d = b.toVector().subtract(a.toVector());
    double len = d.length();
    if (len < 0.01) return true;
    return a.getWorld().rayTraceBlocks(a, d.normalize(), len, FluidCollisionMode.NEVER, true) == null;
  }

  // ------------------------------------------------------------------ effects on players

  void slow(LivingEntity v, double seconds, int amp) {
    v.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, (int) Math.round(seconds * 20), amp, false, true, true));
  }

  void blind(LivingEntity v, double seconds) {
    v.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, (int) Math.round(seconds * 20), 0, false, false, false));
  }

  void reveal(Ctx c, LivingEntity target, double seconds) {
    reveals.reveal(target, c.m().squadOf(c.id()), team(c.m(), c.id()), (int) Math.round(seconds * 20));
  }

  /** Blinds the enemies who look at the point (and can see it) within the range: a flash. */
  void flash(Ctx c, Location at, double range, double seconds) {
    World w = at.getWorld();
    w.spawnParticle(Particle.END_ROD, at, 40, 0.3, 0.3, 0.3, 0.25);
    w.playSound(at, "entity.firework_rocket.twinkle", SoundCategory.PLAYERS, 1.2f, 1.6f);
    for (LivingEntity v : enemies(c.m(), c.id())) {
      Location eye = v.getEyeLocation();
      if (eye.distance(at) > range) continue;
      Vector to = at.toVector().subtract(eye.toVector()).normalize();
      if (eye.getDirection().dot(to) < 0.35 || !clear(eye, at)) continue;
      blind(v, seconds);
    }
  }

  // ------------------------------------------------------------------ entities

  /** A block_display that goes away with the effect. */
  BlockDisplay block(Effects.Fx f, Location at, Material m, float sx, float sy, float sz) {
    BlockDisplay d = at.getWorld().spawn(at, BlockDisplay.class, e -> {
      e.setBlock(m.createBlockData());
      e.setPersistent(false);
      e.setInvulnerable(true);
      e.setGravity(false);
      e.setBrightness(new Display.Brightness(12, 12));
      e.setViewRange(3f); // far placements (tactical map) must still be seen from where the caster stands
      e.setTransformation(new Transformation(new Vector3f(-sx / 2, 0, -sz / 2), new AxisAngle4f(), new Vector3f(sx, sy, sz), new AxisAngle4f()));
    });
    f.onEnd(d::remove);
    return d;
  }

  /**
   * A cloud: a round ball of the design radius made of block_display puffs (cubes turned at random). The outer shell is
   * a Fibonacci sphere of puffs a bit smaller than the gaps between them, and a few big puffs fill the core, so it reads
   * as a round smoke from every side (and from inside) instead of a column. The ball is centred half a radius above the
   * ground, the part under the floor is moved up. They all go with the effect.
   */
  void cloud(Effects.Fx f, Location centre, Material m, double radius) {
    for (double[] pf : puffs(radius)) {
      Location at = centre.clone().add(pf[0], pf[1], pf[2]);
      BlockDisplay d = block(f, at, m, (float) pf[3], (float) pf[3], (float) pf[3]);
      Vector3f scale = new Vector3f((float) pf[3]);
      // a cube turned about its own centre: translation = -(rotated half diagonal)
      org.joml.Quaternionf q = new org.joml.Quaternionf().rotateYXZ((float) (random.nextDouble() * Math.PI), (float) (random.nextDouble() * Math.PI), (float) (random.nextDouble() * Math.PI));
      Vector3f half = new Vector3f(-0.5f, -0.5f, -0.5f).mul(scale).rotate(q);
      d.setTransformation(new Transformation(half, q, scale, new org.joml.Quaternionf()));
    }
  }

  /** Puffs of a cloud as {dx, dy, dz, size} from its ground point: a shell, a core, nothing under the floor. Pure. */
  static List<double[]> puffs(double radius) {
    List<double[]> out = new ArrayList<>();
    double cy = radius * 0.6; // ball centre above the ground
    int shell = (int) Math.min(56, Math.max(18, Math.round(radius * radius * 3.4)));
    double golden = Math.PI * (3 - Math.sqrt(5)), rs = radius * 0.75, size = Math.max(0.7, radius * 0.38);
    for (int i = 0; i < shell; i++) {
      double y = 1 - 2 * (i + 0.5) / shell, rr = Math.sqrt(1 - y * y), a = golden * i;
      double sz = size * (0.8 + 0.4 * ((i * 7) % 5) / 4.0), py = cy + y * rs;
      if (py - sz / 2 < 0) py = sz / 2; // not under the floor
      out.add(new double[] {Math.cos(a) * rr * rs, py, Math.sin(a) * rr * rs, sz});
    }
    double core = Math.max(1.0, radius * 1.1);
    out.add(new double[] {0, Math.max(cy, core / 2), 0, core});
    return out;
  }

  /** A thrown item that flies, bounces and calls back: {@code impacts} blocks hit and it is over. */
  void ball(Ctx c, Material item, int impacts, Consumer<Location> bounce, Consumer<Location> end) {
    Location picked = c.spot(0); // chosen on the tactical map: it lands there, nothing is thrown
    if (picked != null) {
      end.accept(picked);
      return;
    }
    Player p = c.p();
    World w = p.getWorld();
    Location eye = p.getEyeLocation();
    Vector vel = eye.getDirection().multiply(1.1).add(new Vector(0, 0.1, 0));
    Location[] pos = {eye.clone().add(eye.getDirection().multiply(0.6))};
    int[] hits = {0};
    ItemDisplay shown = w.spawn(pos[0], ItemDisplay.class, e -> {
      e.setItemStack(new ItemStack(item));
      e.setPersistent(false);
      e.setBillboard(Display.Billboard.CENTER);
      e.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(), new Vector3f(0.5f, 0.5f, 0.5f), new AxisAngle4f()));
    });
    Effects.Fx f = fx.add(c.m(), c.id(), 120, age -> {
      vel.setY(vel.getY() - 0.05);
      double len = vel.length();
      RayTraceResult r = w.rayTraceBlocks(pos[0], vel.clone().normalize(), len, FluidCollisionMode.NEVER, true);
      if (r == null) {
        pos[0].add(vel);
      } else {
        Vector n = r.getHitBlockFace().getDirection();
        pos[0] = r.getHitPosition().toLocation(w).add(n.clone().multiply(0.1));
        hits[0]++;
        if (hits[0] >= impacts) {
          end.accept(pos[0].clone());
          return false;
        }
        vel.subtract(n.clone().multiply(2 * vel.dot(n))).multiply(0.55);
        bounce.accept(pos[0].clone());
      }
      shown.teleport(pos[0]);
      w.spawnParticle(Particle.CRIT, pos[0], 1, 0, 0, 0, 0);
      return true;
    });
    f.onEnd(shown::remove);
  }

  /** Points of a circle on the ground plane. */
  static void circle(Location c, double r, int points, Consumer<Location> each) {
    for (int i = 0; i < points; i++) {
      double a = 2 * Math.PI * i / points;
      each.accept(c.clone().add(Math.cos(a) * r, 0, Math.sin(a) * r));
    }
  }

  /** Spawns particles for the whole circle. */
  static void ring(Location c, double r, Particle particle, int points) {
    circle(c, r, points, l -> c.getWorld().spawnParticle(particle, l, 1, 0, 0, 0, 0, null, true));
  }

  void sound(Location at, String key, float vol, float pitch) {
    at.getWorld().playSound(at, key, SoundCategory.PLAYERS, vol, pitch);
  }

  /** The match of a player, or null. */
  VbMatch matchOf(UUID id) {
    ArenaSession s = plugin.arenas.session(id);
    return s != null && s.game instanceof VbMatch m ? m : null;
  }

  /** The vanilla players inside a collection, online ones only. */
  static Collection<Player> online(Collection<UUID> ids) {
    List<Player> out = new ArrayList<>();
    for (UUID id : ids) {
      Player p = Bukkit.getPlayer(id);
      if (p != null) out.add(p);
    }
    return out;
  }

  /** Removes entities of an effect that the server did not manage to clean. */
  static void discard(Entity e) {
    if (e != null) e.remove();
  }

  /** Blocks of a vertical wall centred on {@code centre}, {@code width} wide, {@code height} high, across the player's view. */
  static List<Block> wallBlocks(Location centre, Vector look, int width, int height) {
    boolean alongX = Math.abs(look.getZ()) >= Math.abs(look.getX());
    List<Block> out = new ArrayList<>();
    Block base = centre.getBlock();
    for (int i = -(width / 2); i <= width / 2 + (width % 2 == 0 ? -1 : 0); i++)
      for (int y = 0; y < height; y++) out.add(base.getRelative(alongX ? i : 0, y, alongX ? 0 : i));
    return out;
  }
}
