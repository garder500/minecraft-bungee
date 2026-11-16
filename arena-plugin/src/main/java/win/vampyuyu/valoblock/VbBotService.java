package win.vampyuyu.valoblock;

import com.destroystokyo.paper.profile.ProfileProperty;
import io.papermc.paper.datacomponent.item.ResolvableProfile;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;
import win.vampyuyu.arena.NexusArenaEngine;

/**
 * The bodies of the bots (Paper mannequins wearing their agent profile) and the glue between their pure
 * brains ({@link BotBrain}) and the world: sight rays, steering, shooting through the same gun maths as
 * the humans', buying, abilities, death and the dropped gun. Everything that touches Bukkit runs on the
 * server thread; match callbacks that arrive elsewhere are moved there.
 */
public final class VbBotService implements BotService {
  /** One bot. */
  static final class Bot {
    final UUID id;
    final VbMatch match;
    BotBrain brain;
    Mannequin body;
    Loadout loadout;
    final Map<String, GunState> guns = new HashMap<>();
    AgentKit kit;
    String name;
    List<BotBrain.Seen> visible = List.of();
    int visibleCountdown;
    Bot(UUID id, VbMatch match) {
      this.id = id;
      this.match = match;
    }

    VbWeapon held() {
      VbWeapon p = loadout == null ? null : loadout.get(VbWeapon.Slot.PRIMARY);
      return p != null ? p : loadout == null ? null : loadout.get(VbWeapon.Slot.SIDEARM);
    }
  }

  /** What a match's bots share this round. */
  static final class Round {
    int planRound = -1;
    final BotPlan.Attack[] attack = new BotPlan.Attack[2];
    final BotPlan.Defense[] defense = new BotPlan.Defense[2];
    UUID lastCarrier;
    double[] carrierPos, seedDrop;
    long lastTick;
    boolean botSealing; // a bot held the seal last tick: only then does a release (null holder) need reporting
  }

  private final NexusArenaEngine plugin;
  private final Map<UUID, Bot> bots = new ConcurrentHashMap<>();
  private final Map<UUID, UUID> bodies = new ConcurrentHashMap<>(); // entity id -> bot
  private final Map<VbMatch, Round> rounds = new ConcurrentHashMap<>();
  private final Map<UUID, AgentKit> kits = new ConcurrentHashMap<>();
  private final Map<UUID, String> names = new ConcurrentHashMap<>();
  private final Random random = new Random();
  private final VbCombat combat;
  private volatile String level;
  private int tickCount;

  public VbBotService(NexusArenaEngine plugin) {
    this.plugin = plugin;
    this.combat = new VbCombat(plugin);
  }

  public void start() {
    Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1, 1);
  }

  /** Plugin disable: no body stays in the world. */
  public void shutdown() {
    for (Bot b : bots.values()) removeBody(b);
    bots.clear();
    rounds.clear();
  }

  private void sync(Runnable r) {
    if (Bukkit.isPrimaryThread()) r.run();
    else Bukkit.getScheduler().runTask(plugin, r);
  }

  private BotConfig cfg() {
    return VbData.bots();
  }

  // ------------------------------------------------------------------ BotService

  @Override
  public String name(UUID bot) {
    return names.getOrDefault(bot, BotService.super.name(bot));
  }

  @Override
  public void level(String l) {
    cfg().level(l); // throws for an unknown level
    level = l.toLowerCase(Locale.ROOT);
  }

  @Override
  public String level() {
    return level == null ? cfg().defaultLevel() : level;
  }

  @Override
  public void agentLocked(VbMatch m, UUID player, String agent) {
    if (!m.isBot(player)) return;
    VbAgent a = VbData.agents().get(agent);
    if (a == null) return;
    names.put(player, "[BOT] " + a.name());
    kits.put(player, new AgentKit(a, VbData.agents().ultimate()));
  }

  @Override
  public void roundStarted(VbMatch m, UUID id, boolean keep) {
    if (!m.isBot(id)) return;
    sync(() -> {
      rounds.computeIfAbsent(m, k -> new Round());
      Bot b = bots.computeIfAbsent(id, k -> new Bot(k, m));
      b.kit = kits.get(id);
      if (b.loadout == null || !keep) b.loadout = Loadout.starting(VbData.weapons());
      else b.loadout.newBuyPhase();
      b.guns.clear();
      if (b.kit != null) b.kit.roundStart(keep);
      BotBuyer.Outcome o = BotBuyer.buy(m, id, b.loadout, cfg(), VbData.weapons());
      if (o.fullBuy() && b.kit != null) BotBuyer.buyAbilities(m, id, b.kit);
      place(b, m);
    });
  }

  /** Puts the body (made if needed) on the spawn node of its side. */
  private void place(Bot b, VbMatch m) {
    World world = worldOf(m);
    if (world == null) return;
    Waypoints g = VbData.waypoints();
    VbSide side = m.side(b.id);
    List<String> spawns = g.spawns(side);
    // Humans take the first spawn points of their side (VbMapService), bots the ones after them.
    int index = 0;
    boolean mine = false;
    for (UUID other : m.squad(m.squadOf(b.id))) {
      if (!m.isBot(other)) index++;
      else if (!mine) {
        if (other.equals(b.id)) mine = true;
        else index++;
      }
    }
    Waypoints.Node node = g.node(spawns.get(index % spawns.size()));
    if (b.brain == null) b.brain = new BotBrain(b.id, cfg(), cfg().level(level()), g);
    b.brain.reset(node.x(), node.y(), node.z());
    b.visible = List.of();
    if (b.body == null || !b.body.isValid()) {
      removeBody(b);
      b.body = spawnBody(world, node, name(b.id), VbData.agents().get(m.agent(b.id)));
      bodies.put(b.body.getUniqueId(), b.id);
    } else b.body.teleport(new Location(world, node.x(), node.y(), node.z()));
    equip(b);
  }

  private Mannequin spawnBody(World world, Waypoints.Node at, String name, VbAgent agent) {
    return world.spawn(new Location(world, at.x(), at.y(), at.z()), Mannequin.class, mq -> {
      mq.setPersistent(false);
      mq.setAI(false);
      mq.setGravity(false);
      mq.setSilent(true);
      mq.setImmovable(true);
      mq.setCollidable(false);
      mq.setDescription(Component.empty());
      mq.customName(Component.text(name));
      mq.setCustomNameVisible(true);
      if (agent != null && agent.hasSkin())
        mq.setProfile(ResolvableProfile.resolvableProfile().addProperty(new ProfileProperty("textures", agent.skinValue(), agent.skinSignature())).build());
    });
  }

  private void equip(Bot b) {
    VbWeapon w = b.held();
    if (b.body == null || w == null) return;
    GunState gs = b.guns.get(w.id());
    b.body.getEquipment().setItemInMainHand(WeaponItems.carried(w, gs == null ? w.magazine() : gs.magazine()));
  }

  private World worldOf(VbMatch m) {
    World mapWorld = plugin.valoblock.map.world();
    if (mapWorld != null) return mapWorld;
    for (UUID id : m.participants()) {
      if (m.isBot(id)) continue;
      Player p = Bukkit.getPlayer(id);
      if (p != null) return p.getWorld();
    }
    return null;
  }

  @Override
  public void spawn(VbMatch m, UUID bot, VbSide side) {}

  @Override
  public void remove(UUID bot) {
    Bot b = bots.remove(bot);
    kits.remove(bot);
    names.remove(bot);
    if (b != null) sync(() -> removeBody(b));
  }

  private void removeBody(Bot b) {
    if (b.body != null) {
      bodies.remove(b.body.getUniqueId());
      b.body.remove();
      b.body = null;
    }
  }

  @Override
  public void matchEnded(VbMatch m) {
    rounds.remove(m);
    for (UUID id : m.bots()) remove(id);
  }

  @Override
  public void phaseChanged(VbMatch m, VbPhase from, VbPhase to) {
    if (to == VbPhase.ROUND_END || to == VbPhase.END) {
      Round r = rounds.get(m);
      if (r != null) {
        r.seedDrop = null;
        r.planRound = -1;
      }
    }
  }

  @Override
  public void replaced(VbMatch m, UUID bot, UUID human) {
    Bot b = bots.remove(bot);
    kits.remove(bot);
    names.remove(bot);
    if (b == null) return;
    sync(() -> {
      if (b.loadout != null) plugin.valoblock.weapons.adopt(human, b.loadout);
      removeBody(b);
    });
  }

  @Override
  public double[] position(UUID id) {
    Bot b = bots.get(id);
    if (b == null || b.body == null || b.brain == null || !b.match.alive().contains(id)) return null;
    BotMover mv = b.brain.mover();
    return new double[] {mv.x(), mv.y(), mv.z(), 0, mv.yaw()};
  }

  @Override
  public org.bukkit.entity.LivingEntity body(UUID id) {
    Bot b = bots.get(id);
    return b == null || b.body == null || !b.body.isValid() ? null : b.body;
  }

  @Override
  public UUID botOf(Entity body) {
    return body == null ? null : bodies.get(body.getUniqueId());
  }

  @Override
  public void damaged(UUID id) {
    Bot b = bots.get(id);
    if (b != null && b.body != null) b.body.playHurtAnimation(0);
  }

  @Override
  public void gunfire(VbMatch m, UUID shooter, double x, double y, double z) {
    heard(m, m.side(shooter), x, y, z);
  }

  /** Defenders of the squad hear shots of the attackers: some rotate to that site. */
  private void heard(VbMatch m, VbSide shooterSide, double x, double y, double z) {
    if (shooterSide != VbSide.SEEDBEARERS) return;
    Round r = rounds.get(m);
    if (r == null) return;
    String site = BotPlan.siteNear(VbData.waypoints(), x, y, z, cfg().gunfireSiteRadius());
    if (site == null) return;
    for (int squad = 0; squad < 2; squad++) {
      BotPlan.Defense d = r.defense[squad];
      if (d == null) continue;
      Map<UUID, double[]> pos = new HashMap<>();
      for (UUID id : m.squad(squad)) {
        Bot b = bots.get(id);
        if (b != null && b.brain != null && m.alive().contains(id)) pos.put(id, new double[] {b.brain.mover().x(), b.brain.mover().y(), b.brain.mover().z()});
      }
      d.rotate(site, pos, cfg().rotateCount());
    }
  }

  @Override
  public void killed(VbMatch m, UUID victim, UUID killer) {
    boolean botInvolved = m.isBot(victim) || (killer != null && m.isBot(killer));
    // With the HUD the kill feed shows it; the chat line stays for a server without one.
    String line = botInvolved && plugin.valoblock.hud == VbHud.NOOP ? (killer == null ? display(m, victim) + " was eliminated" : display(m, killer) + " eliminated " + display(m, victim)) : null;
    Round r = rounds.get(m);
    if (r != null && victim.equals(r.lastCarrier) && m.plantedSite() == null && r.carrierPos != null) r.seedDrop = r.carrierPos.clone();
    sync(() -> {
      if (line != null)
        for (UUID id : m.participants()) {
          Player p = m.isBot(id) ? null : Bukkit.getPlayer(id);
          if (p != null) p.sendMessage(Component.text(line, NamedTextColor.GRAY));
        }
      Bot k = killer == null ? null : bots.get(killer);
      if (k != null && k.kit != null) k.kit.onKill();
      Bot v = bots.get(victim);
      if (v != null) die(v);
    });
  }

  @Override
  public String weaponName(UUID id) {
    Bot b = bots.get(id);
    if (b == null || b.loadout == null) return null;
    VbWeapon w = b.loadout.get(VbWeapon.Slot.PRIMARY);
    if (w == null) w = b.loadout.get(VbWeapon.Slot.SIDEARM);
    return w == null ? null : w.name();
  }

  private String display(VbMatch m, UUID id) {
    if (m.isBot(id)) return name(id);
    Player p = Bukkit.getPlayer(id);
    return p == null ? "A player" : p.getName();
  }

  /** The body goes; its gun (the primary, else a bought sidearm) falls where it stood. */
  private void die(Bot b) {
    if (b.body == null) return;
    Location at = b.body.getLocation();
    at.getWorld().spawnParticle(Particle.POOF, at.clone().add(0, 1, 0), 20, 0.3, 0.5, 0.3, 0.05);
    VbWeapon w = b.held();
    if (w != null && !w.free() && w.isGun()) {
      GunState gs = b.guns.get(w.id());
      plugin.valoblock.weapons.dropGun(at, w, gs == null ? w.magazine() : gs.magazine());
    }
    removeBody(b);
  }

  // ------------------------------------------------------------------ the brain loop

  private void tick() {
    tickCount++;
    long now = System.currentTimeMillis();
    for (var e : rounds.entrySet()) {
      try {
        tickMatch(e.getKey(), e.getValue(), now);
      } catch (RuntimeException ex) {
        plugin.getLogger().warning("ValoBlock bots failed: " + ex);
      }
    }
    // A match that has bots but no Round yet (first round, nobody ticked): make one.
    for (Bot b : bots.values()) rounds.computeIfAbsent(b.match, k -> new Round());
  }

  private void tickMatch(VbMatch m, Round r, long now) {
    VbPhase phase = m.vbPhase();
    if (phase != VbPhase.COMBAT && phase != VbPhase.PLANTED) {
      r.lastTick = now;
      return;
    }
    World world = worldOf(m);
    if (world == null) return;
    double dt = r.lastTick == 0 ? 0.05 : Math.min(0.1, (now - r.lastTick) / 1000.0);
    r.lastTick = now;
    Waypoints g = VbData.waypoints();
    BotConfig cfg = cfg();
    if (r.planRound != m.round()) plan(m, r, g, now);

    List<Bot> living = new ArrayList<>();
    for (Bot b : bots.values()) if (b.match == m && b.body != null && b.brain != null && m.alive().contains(b.id)) living.add(b);

    // The Seed: remember where the carrier stands (it drops there when it dies), clear a drop once carried.
    UUID carrier = m.seedCarrier();
    if (carrier != null) {
      r.lastCarrier = carrier;
      r.seedDrop = null;
      r.carrierPos = locate(m, carrier);
    }
    UUID chaser = null;
    if (r.seedDrop != null && m.plantedSite() == null) {
      double bd = Double.MAX_VALUE;
      for (Bot b : living) {
        if (m.side(b.id) != VbSide.SEEDBEARERS) continue;
        double d = b.brain.mover().distanceTo(r.seedDrop[0], r.seedDrop[2]);
        if (d < bd) {
          bd = d;
          chaser = b.id;
        }
      }
    }

    // Who each squad's group is: release the attackers together.
    boolean[] released = new boolean[2];
    for (int squad = 0; squad < 2; squad++) {
      if (r.attack[squad] == null) continue;
      List<double[]> pos = new ArrayList<>();
      for (Bot b : living)
        if (m.squadOf(b.id) == squad) pos.add(new double[] {b.brain.mover().x(), b.brain.mover().y(), b.brain.mover().z()});
      released[squad] = r.attack[squad].update(now, g, cfg, pos);
    }

    List<Enemy> everyone = enemies(m, world);
    UUID plantHolder = null, sealHolder = null;
    VbSite plantBox = null;
    boolean botCarrier = carrier != null && m.isBot(carrier);
    boolean botSealer = false;
    for (Bot b : living) {
      if (!m.alive().contains(b.id)) continue; // died to an earlier bot this tick
      int squad = m.squadOf(b.id);
      VbSide side = m.side(b.id);
      BotBrain.View view = view(m, r, b, squad, side, released[squad], chaser, everyone, now);
      BotBrain.Act act = b.brain.think(now, dt, view);
      if (side == VbSide.SEALERS) botSealer = true;
      BotMover mv = b.brain.mover();
      Location to = new Location(world, mv.x(), mv.y(), mv.z(), (float) act.yaw, (float) act.pitch);
      b.body.teleport(to);
      if (act.holdPlant && b.id.equals(carrier)) {
        plantHolder = b.id;
        plantBox = siteBox(r.attack[squad].site());
      }
      if (act.holdSeal) sealHolder = b.id;
      if (act.pickUpSeed && m.pickUpSeed(b.id)) r.seedDrop = null;
      if (act.fire) fire(m, b, act.target, everyone, world, now);
      else idleReload(b, now);
      if (act.ability != null) cast(m, b, act, world, everyone);
    }
    if (m.vbPhase() == VbPhase.COMBAT && botCarrier) {
      VbSite box = plantBox != null ? plantBox : VbData.sites().get(0);
      m.holdPlant(plantHolder, plantHolder == null ? null : box, dt);
    } else if (m.vbPhase() == VbPhase.PLANTED && botSealer && (sealHolder != null || r.botSealing)) {
      // Never report "nobody holds" while no bot ever held: a human's own hold must not be reset every tick.
      r.botSealing = sealHolder != null;
      m.holdSeal(sealHolder, dt);
    }
  }

  private static VbSite siteBox(String id) {
    for (VbSite s : VbData.sites()) if (s.id().equals(id)) return s;
    return null;
  }

  private void plan(VbMatch m, Round r, Waypoints g, long now) {
    r.planRound = m.round();
    for (int squad = 0; squad < 2; squad++) {
      List<UUID> ids = new ArrayList<>();
      for (UUID id : m.squad(squad)) if (m.isBot(id) && m.alive().contains(id)) ids.add(id);
      if (m.score().sideOf(squad, m.round()) == VbSide.SEEDBEARERS) {
        r.attack[squad] = new BotPlan.Attack(BotPlan.pickSite(g, random), now);
        r.defense[squad] = null;
      } else {
        r.defense[squad] = BotPlan.Defense.allocate(g, ids, random);
        r.attack[squad] = null;
      }
    }
  }

  private double[] locate(VbMatch m, UUID id) {
    if (m.isBot(id)) {
      Bot b = bots.get(id);
      if (b == null || b.brain == null) return null;
      return new double[] {b.brain.mover().x(), b.brain.mover().y(), b.brain.mover().z()};
    }
    Player p = Bukkit.getPlayer(id);
    if (p == null) return null;
    Location l = p.getLocation();
    return new double[] {l.getX(), l.getY(), l.getZ()};
  }

  // ------------------------------------------------------------------ sight

  /** A living fighter that bots may shoot at. */
  private record Enemy(UUID id, int squad, double x, double y, double z, boolean sneaking, boolean bot) {}

  private List<Enemy> enemies(VbMatch m, World world) {
    List<Enemy> out = new ArrayList<>();
    for (UUID id : m.alive()) {
      if (m.isBot(id)) {
        Bot b = bots.get(id);
        if (b == null || b.brain == null || b.body == null) continue;
        BotMover mv = b.brain.mover();
        out.add(new Enemy(id, m.squadOf(id), mv.x(), mv.y(), mv.z(), false, true));
      } else {
        Player p = Bukkit.getPlayer(id);
        if (p == null || !p.getWorld().equals(world) || p.getGameMode() == org.bukkit.GameMode.SPECTATOR) continue;
        Location l = p.getLocation();
        out.add(new Enemy(id, m.squadOf(id), l.getX(), l.getY(), l.getZ(), p.isSneaking(), false));
      }
    }
    return out;
  }

  private BotBrain.View view(VbMatch m, Round r, Bot b, int squad, VbSide side, boolean released, UUID chaser, List<Enemy> all, long now) {
    BotMover mv = b.brain.mover();
    World world = b.body.getWorld();
    // Sight is looked at every other tick, bots in turns.
    if (b.visibleCountdown-- <= 0) {
      b.visibleCountdown = 1;
      Location eye = new Location(world, mv.x(), mv.y() + 1.62, mv.z());
      List<BotBrain.Seen> seen = new ArrayList<>();
      for (Enemy e : all) {
        if (e.squad() == squad) continue;
        double dx = e.x() - mv.x(), dy = e.y() - mv.y(), dz = e.z() - mv.z();
        if (dx * dx + dy * dy + dz * dz > cfg().sightRange() * cfg().sightRange()) continue;
        Location chest = new Location(world, e.x(), e.y() + 1.0, e.z());
        if (AbilityEnv.clear(eye, chest)) seen.add(new BotBrain.Seen(e.id(), e.x(), e.y(), e.z(), e.sneaking()));
      }
      b.visible = seen;
    }
    String attackSite = r.attack[squad] == null ? null : r.attack[squad].site();
    BotPlan.Defense d = r.defense[squad];
    boolean inSite = false;
    if (attackSite != null) {
      VbSite box = siteBox(attackSite);
      inSite = box != null && box.contains(mv.x(), mv.y(), mv.z());
    }
    VbSite planted = m.plantedSite();
    AgentKit kit = b.kit;
    return new BotBrain.View(
        m.vbPhase(), side, b.id.equals(m.seedCarrier()), r.seedDrop, b.id.equals(chaser), planted == null ? null : planted.id(), attackSite, released,
        d == null ? null : d.siteOf(b.id), d == null ? null : d.anchor(b.id), inSite, b.visible,
        kit != null && slotOfKind(kit, "smoke") > 0, kit != null && slotOfKind(kit, "flash") > 0, kit != null && kit.ultReady());
  }

  // ------------------------------------------------------------------ shooting

  private void idleReload(Bot b, long now) {
    VbWeapon w = b.held();
    if (w == null || !w.isGun()) return;
    GunState gs = b.guns.computeIfAbsent(w.id(), k -> new GunState(w));
    gs.tick(now);
    if (gs.magazine() < w.magazine() / 2 && gs.startReload(now)) equip(b);
  }

  private void fire(VbMatch m, Bot b, UUID targetId, List<Enemy> all, World world, long now) {
    VbWeapon w = b.held();
    if (w == null || !w.isGun()) return;
    Enemy target = null;
    for (Enemy e : all) if (e.id().equals(targetId)) target = e;
    if (target == null) return;
    GunState gs = b.guns.computeIfAbsent(w.id(), k -> new GunState(w));
    for (int i = 0; i < 3 && gs.poll(now, true, true); i++) {
      shoot(m, b, w, gs.lastShotIndex(), target, all, world);
      if (!m.alive().contains(b.id)) return;
    }
    b.body.swingMainHand();
    equip(b);
  }

  private void shoot(VbMatch m, Bot b, VbWeapon w, int shot, Enemy target, List<Enemy> all, World world) {
    BotMover mv = b.brain.mover();
    double[] o = {mv.x(), mv.y() + 1.62, mv.z()};
    double[] to = {target.x() - o[0], target.y() + 1.1 - o[1], target.z() - o[2]};
    double len = Math.sqrt(to[0] * to[0] + to[1] * to[1] + to[2] * to[2]);
    double[] aim = {to[0] / len, to[1] / len, to[2] / len};
    double cone = BotAim.cone(b.brain.level(), w, shot, false);
    Map<UUID, int[]> damage = new LinkedHashMap<>();
    int traced = 0;
    int squad = m.squadOf(b.id);
    for (int pellet = 0; pellet < w.pellets(); pellet++) {
      double[] d = GunMath.jitter(aim, cone, random);
      Location start = new Location(world, o[0], o[1], o[2]);
      Vector dir = new Vector(d[0], d[1], d[2]);
      RayTraceResult wall = world.rayTraceBlocks(start, dir, w.range(), FluidCollisionMode.NEVER, true);
      double best = wall == null ? w.range() : wall.getHitPosition().distance(start.toVector());
      Enemy victim = null;
      GunMath.Hit hit = null;
      for (Enemy e : all) {
        if (e.squad() == squad) continue;
        GunMath.Hit h = GunMath.rayHitbox(o, d, best, e.x(), e.y(), e.z(), e.sneaking());
        if (h == null) continue;
        best = h.distance();
        victim = e;
        hit = h;
      }
      if (victim != null) damage.computeIfAbsent(victim.id(), k -> new int[1])[0] += GunMath.damage(w, hit.zone(), hit.distance(), 1);
      if (traced++ < 2) {
        Particle.DustOptions dust = new Particle.DustOptions(WeaponItems.tracer(w.metal()), 0.6f);
        for (double t = 1.5; t < best; t += 0.5) world.spawnParticle(Particle.DUST, o[0] + d[0] * t, o[1] + d[1] * t - 0.1, o[2] + d[2] * t, 1, 0, 0, 0, 0, dust);
      }
    }
    world.playSound(new Location(world, o[0], o[1], o[2]), "entity.firework_rocket.blast", SoundCategory.PLAYERS, 0.7f, 1.6f);
    heard(m, m.side(b.id), mv.x(), mv.y(), mv.z());
    for (var e : damage.entrySet()) {
      UUID vid = e.getKey();
      if (m.isBot(vid)) {
        m.damage(vid, b.id, e.getValue()[0]); // the death arrives through killed()
        damaged(vid);
      } else {
        Player p = Bukkit.getPlayer(vid);
        if (p != null) combat.damage(m, p, b.id, e.getValue()[0]);
      }
    }
  }

  // ------------------------------------------------------------------ abilities (simplified: see the class notes)

  private int slotOfKind(AgentKit kit, String kind) {
    for (VbAbility ab : kit.agent().abilities())
      if (ab.slot() <= 6 && cfg().kind(ab.id()).equals(kind) && kit.canUse(ab.slot()) == AgentKit.Result.OK) return ab.slot();
    return -1;
  }

  private void cast(VbMatch m, Bot b, BotBrain.Act act, World world, List<Enemy> all) {
    AgentKit kit = b.kit;
    if (kit == null || !(plugin.valoblock.agents instanceof VbAgentService svc)) return;
    AbilityEnv env = svc.env();
    BotConfig cfg = cfg();
    switch (act.ability) {
      case "smoke" -> {
        int slot = slotOfKind(kit, "smoke");
        if (slot < 0) return;
        kit.use(slot);
        Location at = env.floor(new Location(world, act.abilityAt[0], act.abilityAt[1] + 1, act.abilityAt[2]));
        double radius = cfg.smokeRadius();
        Effects.Fx f = env.fx.add(m, b.id, (int) Math.round(cfg.smokeSeconds() * 20), age -> {
          if (age % 10 == 0) world.spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, at.clone().add(0, radius * 0.5, 0), 4, radius * 0.4, radius * 0.3, radius * 0.4, 0.01);
          return true;
        });
        env.cloud(f, at, Material.GRAY_CONCRETE, radius);
        env.sound(at, "entity.generic.extinguish_fire", 1f, 0.8f);
      }
      case "flash" -> {
        int slot = slotOfKind(kit, "flash");
        if (slot < 0) return;
        kit.use(slot);
        Location at = new Location(world, act.abilityAt[0], act.abilityAt[1], act.abilityAt[2]);
        world.spawnParticle(Particle.END_ROD, at, 40, 0.3, 0.3, 0.3, 0.25);
        env.sound(at, "entity.firework_rocket.twinkle", 1.2f, 1.6f);
        for (org.bukkit.entity.LivingEntity v : env.enemies(m, b.id)) {
          Location eye = v.getEyeLocation();
          if (eye.distance(at) > cfg.flashRange()) continue;
          Vector toward = at.toVector().subtract(eye.toVector()).normalize();
          if (eye.getDirection().dot(toward) < 0.35 || !AbilityEnv.clear(eye, at)) continue;
          env.blind(v, cfg.flashSeconds());
        }
      }
      case "ult" -> {
        if (kit.canUse(7) != AgentKit.Result.OK) return;
        kit.use(7);
        BotMover mv = b.brain.mover();
        Location c = new Location(world, mv.x(), mv.y() + 1, mv.z());
        world.spawnParticle(Particle.EXPLOSION, c, 6, cfg.ultRange() * 0.3, 0.5, cfg.ultRange() * 0.3, 0);
        env.sound(c, "entity.generic.explode", 1f, 0.9f);
        int squad = m.squadOf(b.id);
        for (Enemy e : all) {
          if (e.squad() == squad || Math.hypot(e.x() - mv.x(), e.z() - mv.z()) > cfg.ultRange()) continue;
          if (e.bot()) {
            m.damage(e.id(), b.id, cfg.ultDamage());
            damaged(e.id());
          } else {
            Player p = Bukkit.getPlayer(e.id());
            if (p != null) combat.damage(m, p, b.id, cfg.ultDamage());
          }
        }
      }
      default -> {}
    }
  }
}
