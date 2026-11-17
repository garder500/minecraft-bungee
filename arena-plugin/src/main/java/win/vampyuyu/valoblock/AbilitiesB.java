package win.vampyuyu.valoblock;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;
import net.kyori.adventure.text.Component;
import win.vampyuyu.valoblock.AbilityEnv.Ctx;

/** The Seers and Weavers: Vesper, Nacre, Sahrin and Murk. Each returns false when it could not be used (the charge is given back). */
final class AbilitiesB {
  private AbilitiesB() {}

  static boolean cast(Ctx c) {
    return switch (c.ab().id()) {
      case "echo_step" -> echoStep(c);
      case "judgment" -> judgment(c);
      case "soul_lantern" -> soulLantern(c);
      case "verdict" -> verdict(c);
      case "ink_burst" -> inkBurst(c);
      case "pressure_bubble" -> pressureBubble(c);
      case "sonar_pearl" -> sonarPearl(c);
      case "undertow" -> undertow(c);
      case "rewind" -> rewind(c);
      case "hourglass_trap" -> hourglassTrap(c);
      case "sand_veil" -> sandVeil(c);
      case "time_tide" -> timeTide(c);
      case "leech_pool" -> leechPool(c);
      case "rot_cloud" -> rotCloud(c);
      case "miasma_wall" -> miasmaWall(c);
      case "bog_tide" -> bogTide(c);
      default -> false;
    };
  }

  /** A second use of an ability that is already out (Rewind back, the wall switched). True when it handled the click and nothing is spent. */
  static boolean reuse(Ctx c) {
    AbilityEnv e = c.env();
    switch (c.ab().id()) {
      case "rewind" -> {
        AbilityEnv.Mark mark = e.marks.get(c.id());
        if (mark == null || mark.until() <= e.now) return false;
        e.marks.remove(c.id());
        Location from = c.p().getLocation();
        // The engine cancels teleports of arena players unless they are allowed.
        c.env().plugin.arenas.allowTeleport(c.id(), true);
        try {
          c.p().teleport(mark.where());
        } finally {
          c.env().plugin.arenas.allowTeleport(c.id(), false);
        }
        c.p().setVelocity(new Vector());
        from.getWorld().spawnParticle(Particle.END_ROD, from.add(0, 1, 0), 25, 0.3, 0.6, 0.3, 0.05);
        e.sound(mark.where(), "block.beacon.deactivate", 1f, 1.5f);
        return true;
      }
      case "miasma_wall" -> {
        Runnable toggle = e.walls.get(c.id());
        if (toggle == null) return false;
        toggle.run();
        return true;
      }
      default -> {
        return false;
      }
    }
  }

  private static Vector flat(Player p) {
    Vector d = p.getLocation().getDirection().setY(0);
    return d.lengthSquared() < 1e-6 ? new Vector(0, 0, 1) : d.normalize();
  }

  // ------------------------------------------------------------------ Vesper

  /** A ghost of yourself walks forward; whoever shoots it is revealed to your team. */
  private static boolean echoStep(Ctx c) {
    AbilityEnv e = c.env();
    Player p = c.p();
    Location start = p.getLocation().clone();
    Mannequin body = start.getWorld().spawn(start, Mannequin.class, m -> {
      m.setPersistent(false);
      m.setInvulnerable(true);
      m.setGravity(false);
      m.setAI(false);
      m.setSilent(true);
      m.setCollidable(false);
      m.setImmovable(true);
      m.setDescription(Component.empty());
    });
    Vector dir = flat(p);
    int ticks = c.ticks("seconds", 4);
    double perTick = c.n("distance", 14) / ticks;
    Location pos = start.clone();
    Effects.Fx f = e.fx.add(c.m(), c.id(), ticks, age -> {
      Location next = pos.clone().add(dir.clone().multiply(perTick));
      Location probe = pos.clone().add(0, 0.6, 0);
      if (probe.getWorld().rayTraceBlocks(probe, dir, perTick + 0.4, org.bukkit.FluidCollisionMode.NEVER, true) == null) {
        Location ground = e.floor(next.clone().add(0, 1.2, 0));
        if (Math.abs(ground.getY() - pos.getY()) < 1.3) {
          next.setY(ground.getY());
          pos.setX(next.getX());
          pos.setY(next.getY());
          pos.setZ(next.getZ());
        }
      }
      body.teleport(pos);
      return true;
    });
    f.onEnd(() -> {
      body.remove();
      e.ghosts.removeIf(g -> g.body() == body);
    });
    e.ghosts.add(new AbilityEnv.Ghost(body, c.id(), c.m().squadOf(c.id()), f, c.ticks("revealSeconds", 3)));
    e.sound(start, "entity.allay.ambient_with_item", 1f, 0.6f);
    return true;
  }

  /** Called for every shot: a ghost the shot passes through is shot, so the shooter is revealed. */
  static void shotPassed(AbilityEnv e, Player shooter, double[] o, double[] d, double dist) {
    VbMatch m = e.matchOf(shooter.getUniqueId());
    if (m == null) return;
    for (AbilityEnv.Ghost g : new ArrayList<>(e.ghosts)) {
      if (g.squad() == m.squadOf(shooter.getUniqueId())) continue;
      Location l = g.body().getLocation();
      double px = l.getX() - o[0], py = l.getY() + 0.9 - o[1], pz = l.getZ() - o[2];
      double t = px * d[0] + py * d[1] + pz * d[2];
      if (t < 0 || t > dist + 0.5) continue;
      double dx = px - d[0] * t, dy = py - d[1] * t, dz = pz - d[2] * t;
      if (dx * dx + dy * dy + dz * dz > 0.45 * 0.45 + 0.4) continue;
      Set<UUID> team = e.team(m, g.owner());
      e.reveals.reveal(shooter, g.squad(), team, g.revealTicks());
      e.sound(l, "entity.allay.hurt", 1f, 0.7f);
      l.getWorld().spawnParticle(Particle.SOUL, l.add(0, 1, 0), 20, 0.3, 0.5, 0.3, 0.05);
      g.fx().stop();
    }
  }

  /** A soul flare at the crosshair: whoever looks at it is blinded. */
  private static boolean judgment(Ctx c) {
    AbilityEnv e = c.env();
    Location at = e.aim(c.p(), c.n("range", 20)).add(0, 1.8, 0);
    e.fx.add(c.m(), c.id(), c.ticks("flareSeconds", 2), age -> {
      at.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, at, 5, 0.15, 0.15, 0.15, 0.01);
      if (age == 10) e.flash(c, at, 30, c.n("blindSeconds", 1.5));
      return true;
    });
    e.sound(at, "entity.vex.charge", 1f, 1.2f);
    return true;
  }

  /** A lantern that pulses twice: enemies inside glow for 3 s (to the team). */
  private static boolean soulLantern(Ctx c) {
    AbilityEnv e = c.env();
    Location at = e.floor(e.aim(c.p(), 6).add(0, 0.5, 0));
    double r = c.n("radius", 5);
    int total = c.ticks("seconds", 4), pulses = (int) c.n("pulses", 2);
    Effects.Fx f = e.fx.add(c.m(), c.id(), total, age -> {
      for (int i = 1; i <= pulses; i++)
        if (age == total * i / (pulses + 1)) {
          AbilityEnv.ring(at.clone().add(0, 0.3, 0), r, Particle.SOUL, 40);
          e.sound(at, "block.beacon.power_select", 1f, 1.8f);
          for (LivingEntity v : e.enemies(c.m(), c.id())) if (AbilityEnv.near(v, at, r)) e.reveal(c, v, c.n("glowSeconds", 3));
        }
      return true;
    });
    e.block(f, at, Material.SOUL_LANTERN, 0.5f, 0.5f, 0.5f);
    return true;
  }

  /** Every enemy glows through walls (to the team). */
  private static boolean verdict(Ctx c) {
    AbilityEnv e = c.env();
    for (LivingEntity v : e.enemies(c.m(), c.id())) e.reveal(c, v, c.n("seconds", 4));
    e.sound(c.p().getLocation(), "entity.wither.spawn", 0.6f, 1.6f);
    return true;
  }

  // ------------------------------------------------------------------ Nacre

  /** A black cloud: blinds and muffles whoever is inside. */
  private static boolean inkBurst(Ctx c) {
    AbilityEnv e = c.env();
    e.ball(c, Material.INK_SAC, 1, l -> {}, land -> {
      Location centre = land.clone();
      double r = c.n("radius", 3.5);
      Effects.Fx f = e.fx.add(c.m(), c.id(), c.ticks("seconds", 6), null);
      Set<LivingEntity> muffled = new HashSet<>();
      e.cloud(f, centre, Material.BLACK_CONCRETE, r);
      Effects.Fx run = e.fx.add(c.m(), c.id(), c.ticks("seconds", 6), age -> {
        if (age % 3 == 0) centre.getWorld().spawnParticle(Particle.SQUID_INK, centre.clone().add(0, r / 2, 0), 8, r / 2, r / 3, r / 2, 0.01);
        if (age % 5 == 0)
          for (LivingEntity v : e.enemies(c.m(), c.id())) {
            boolean inside = v.getLocation().add(0, 1, 0).distance(centre.clone().add(0, r / 2, 0)) <= r;
            if (inside) {
              e.blind(v, 1.0);
              v.setSilent(true);
              muffled.add(v);
            } else if (muffled.remove(v)) v.setSilent(false);
          }
        return true;
      });
      run.onEnd(() -> {
        for (LivingEntity v : muffled) v.setSilent(false);
        f.stop();
      });
    });
    return true;
  }

  /** A dome that pushes every player out for a few seconds. */
  private static boolean pressureBubble(Ctx c) {
    AbilityEnv e = c.env();
    Location centre = e.aim(c.p(), 10).add(0, 0.2, 0);
    double r = c.n("radius", 5), push = c.n("push", 0.5);
    e.fx.add(c.m(), c.id(), c.ticks("seconds", 4), age -> {
      if (age % 2 == 0)
        for (int i = 0; i < 20; i++) {
          double a = e.random.nextDouble() * Math.PI * 2, b = e.random.nextDouble() * Math.PI / 2;
          centre.getWorld().spawnParticle(Particle.BUBBLE, centre.getX() + r * Math.cos(a) * Math.cos(b), centre.getY() + r * Math.sin(b), centre.getZ() + r * Math.sin(a) * Math.cos(b), 1, 0, 0, 0, 0);
        }
      for (Player v : e.everyone(c.m())) {
        Location l = v.getLocation();
        double dx = l.getX() - centre.getX(), dz = l.getZ() - centre.getZ();
        double d = Math.sqrt(dx * dx + dz * dz);
        if (d > r || l.getY() < centre.getY() - 1.5 || l.getY() > centre.getY() + r) continue;
        Vector out = d < 0.01 ? new Vector(1, 0, 0) : new Vector(dx / d, 0, dz / d);
        v.setVelocity(out.multiply(push).setY(0.12));
      }
      return true;
    });
    e.sound(centre, "block.bubble_column.upwards_ambient", 1f, 0.8f);
    return true;
  }

  /** A thrown pearl; one sonar ring reveals the enemies it passes. */
  private static boolean sonarPearl(Ctx c) {
    AbilityEnv e = c.env();
    e.ball(c, Material.ENDER_PEARL, 1, l -> {}, land -> {
      Location centre = land.clone().add(0, 0.3, 0);
      double r = c.n("radius", 12);
      Set<UUID> pinged = new HashSet<>();
      e.sound(centre, "block.note_block.bell", 1.2f, 0.5f);
      e.fx.add(c.m(), c.id(), 24, age -> {
        double ring = r * age / 24.0;
        AbilityEnv.ring(centre, ring, Particle.END_ROD, Math.max(16, (int) (ring * 6)));
        for (LivingEntity v : e.enemies(c.m(), c.id()))
          if (!pinged.contains(v.getUniqueId()) && v.getLocation().distance(centre) <= ring) {
            pinged.add(v.getUniqueId());
            e.reveal(c, v, c.n("glowSeconds", 2));
          }
        return true;
      });
    });
    return true;
  }

  /** A field that drags enemies to its centre (the pull is an impulse, never damage). */
  private static boolean undertow(Ctx c) {
    AbilityEnv e = c.env();
    Location picked = c.spot(0); // chosen on the tactical map
    Location centre = picked != null ? picked.add(0, 1, 0) : e.aim(c.p(), 14).add(0, 1, 0);
    double r = c.n("radius", 10), max = Math.min(0.35, c.n("pull", 0.22));
    Effects.Fx field = e.fx.add(c.m(), c.id(), c.ticks("seconds", 8), age -> {
      if (age % 2 == 0) {
        AbilityEnv.ring(centre, r * (1 - (age % 40) / 40.0), Particle.BUBBLE, 24);
        centre.getWorld().spawnParticle(Particle.SQUID_INK, centre, 4, 0.3, 0.3, 0.3, 0.01);
      }
      for (LivingEntity v : e.enemies(c.m(), c.id())) {
        Location l = v.getLocation().add(0, 1, 0);
        double d = l.distance(centre);
        if (d >= r || d < 0.6) continue;
        double pull = max * (1 - d / r);
        Vector to = centre.toVector().subtract(l.toVector()).normalize().multiply(pull);
        v.setVelocity(v.getVelocity().add(to));
      }
      return true;
    });
    e.track(c, centre, field);
    e.sound(centre, "entity.guardian.attack", 0.8f, 0.5f);
    return true;
  }

  // ------------------------------------------------------------------ Sahrin

  /** Marks your position; using it again within the window returns you there. */
  private static boolean rewind(Ctx c) {
    AbilityEnv e = c.env();
    int ticks = c.ticks("seconds", 8);
    Location mark = c.p().getLocation().clone();
    e.marks.put(c.id(), new AbilityEnv.Mark(mark, e.now + ticks));
    e.fx.add(c.m(), c.id(), ticks, age -> {
      if (age % 10 == 0) mark.getWorld().spawnParticle(Particle.END_ROD, mark.clone().add(0, 0.2, 0), 6, 0.2, 0.3, 0.2, 0.01);
      return e.marks.containsKey(c.id());
    }).onEnd(() -> e.marks.remove(c.id()));
    e.sound(mark, "block.respawn_anchor.charge", 1f, 1.4f);
    return true;
  }

  /** A sand zone that slows and reveals whoever walks in. */
  private static boolean hourglassTrap(Ctx c) {
    AbilityEnv e = c.env();
    Location at = e.floor(e.aim(c.p(), 6).add(0, 0.5, 0));
    double r = c.n("radius", 3);
    Effects.Fx f = e.fx.add(c.m(), c.id(), c.ticks("seconds", 20), null);
    e.block(f, at.clone().add(0, 0.01, 0), Material.SAND, (float) (r * 2), 0.05f, (float) (r * 2));
    Effects.Fx run = e.fx.add(c.m(), c.id(), c.ticks("seconds", 20), age -> {
      if (age % 10 == 0) at.getWorld().spawnParticle(Particle.DUST, at.clone().add(0, 0.2, 0), 6, r / 2, 0.1, r / 2, 0, new Particle.DustOptions(Color.fromRGB(0xE8C46A), 1f));
      if (age % 5 == 0)
        for (LivingEntity v : e.enemies(c.m(), c.id()))
          if (AbilityEnv.near(v, at, r)) {
            e.slow(v, c.n("slowSeconds", 3), 1);
            e.reveal(c, v, c.n("revealSeconds", 3));
          }
      return true;
    });
    run.onEnd(f::stop);
    return true;
  }

  /** A thrown cloud of sand that blocks sight; the grains rise. */
  private static boolean sandVeil(Ctx c) {
    AbilityEnv e = c.env();
    e.ball(c, Material.SAND, 1, l -> {}, land -> {
      Location centre = land.clone();
      double r = c.n("radius", 4);
      Effects.Fx f = e.fx.add(c.m(), c.id(), c.ticks("seconds", 15), age -> {
        if (age % 4 == 0) centre.getWorld().spawnParticle(Particle.CLOUD, centre.clone().add(0, 0.3, 0), 0, 0, 1, 0, 0.08); // grains rise upward
        if (age % 6 == 0) centre.getWorld().spawnParticle(Particle.DUST, centre.clone().add(0, r / 2, 0), 10, r / 2, r / 2, r / 2, 0, new Particle.DustOptions(Color.fromRGB(0xD8B868), 1.4f));
        return true;
      });
      e.cloud(f, centre, Material.SAND, r);
      e.track(c, centre, f);
      e.sound(centre, "block.sand.break", 1f, 0.6f);
    });
    return true;
  }

  /** Allies around you: Speed I and faster reloads. */
  private static boolean timeTide(Ctx c) {
    AbilityEnv e = c.env();
    Location centre = c.p().getLocation();
    double r = c.n("radius", 12), seconds = c.n("seconds", 8);
    for (Player a : e.allies(c.m(), c.id())) {
      if (a.getLocation().distance(centre) > r) continue;
      a.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, (int) (seconds * 20), 0, false, false, true));
      e.plugin.valoblock.weapons.reloadModifier(a.getUniqueId(), c.n("reloadFactor", 1.5), seconds);
      a.getWorld().spawnParticle(Particle.END_ROD, a.getLocation().add(0, 1, 0), 15, 0.4, 0.6, 0.4, 0.03);
    }
    AbilityEnv.ring(centre.clone().add(0, 0.2, 0), r, Particle.END_ROD, 80);
    e.sound(centre, "block.bell.use", 1f, 0.7f);
    return true;
  }

  // ------------------------------------------------------------------ Murk

  /** A puddle: slows and drains 10 HP a second, never below 1. */
  private static boolean leechPool(Ctx c) {
    AbilityEnv e = c.env();
    e.ball(c, Material.SLIME_BALL, 1, l -> {}, land -> {
      Location at = e.floor(land.clone().add(0, 0.5, 0));
      double r = c.n("radius", 3);
      Effects.Fx f = e.fx.add(c.m(), c.id(), c.ticks("seconds", 8), null);
      e.block(f, at.clone().add(0, 0.01, 0), Material.LIME_CONCRETE, (float) (r * 2), 0.05f, (float) (r * 2));
      Effects.Fx run = e.fx.add(c.m(), c.id(), c.ticks("seconds", 8), age -> {
        if (age % 4 == 0) at.getWorld().spawnParticle(Particle.COMPOSTER, at.clone().add(0, 0.3, 0), 4, r / 2, 0.1, r / 2, 0);
        if (age % 20 == 0)
          for (LivingEntity v : e.enemies(c.m(), c.id()))
            if (AbilityEnv.near(v, at, r)) {
              e.slow(v, 1.5, 1);
              e.combat.drain(c.m(), v, (int) c.n("drain", 10));
            }
        return true;
      });
      run.onEnd(f::stop);
    });
    return true;
  }

  /** A small poison cloud that blocks sight (the poison drains, never kills). */
  private static boolean rotCloud(Ctx c) {
    AbilityEnv e = c.env();
    e.ball(c, Material.FERMENTED_SPIDER_EYE, 1, l -> {}, land -> {
      Location centre = land.clone();
      double r = c.n("radius", 3.5);
      Effects.Fx f = e.fx.add(c.m(), c.id(), c.ticks("seconds", 10), age -> {
        if (age % 4 == 0) centre.getWorld().spawnParticle(Particle.DUST, centre.clone().add(0, r / 2, 0), 8, r / 2, r / 2, r / 2, 0, new Particle.DustOptions(Color.fromRGB(0x6A8A2A), 1.5f));
        if (age % 20 == 0)
          for (LivingEntity v : e.enemies(c.m(), c.id()))
            if (v.getLocation().add(0, 1, 0).distance(centre.clone().add(0, r / 2, 0)) <= r) e.combat.drain(c.m(), v, (int) c.n("dps", 6));
        return true;
      });
      e.cloud(f, centre, Material.LIME_STAINED_GLASS, r);
      e.track(c, centre, f);
      e.sound(centre, "entity.splash_potion.break", 1f, 0.6f);
    });
    return true;
  }

  /** A long gas wall in front of you, switched on and off by using the ability again; the fuel runs out. */
  private static boolean miasmaWall(Ctx c) {
    AbilityEnv e = c.env();
    Player p = c.p();
    Location endA = c.spot(0), endB = c.spot(1);
    Location centre;
    Vector along;
    List<Location> points = new ArrayList<>();
    if (endA != null && endB != null) { // both ends picked on the tactical map: the wall runs between them (cut short at the longest wall)
      Vector run = endB.toVector().subtract(endA.toVector()).setY(0);
      double dist = Math.min(run.length(), TacTargeting.MAX_WALL);
      along = run.lengthSquared() < 1e-6 ? new Vector(1, 0, 0) : run.normalize();
      int n = Math.max(2, (int) Math.floor(dist) + 1);
      for (int i = 0; i < n; i++) {
        double t = dist * i / (n - 1);
        Location at = endA.clone().add(along.clone().multiply(t));
        at.setY(endA.getY() + (endB.getY() - endA.getY()) * (dist < 1e-6 ? 0 : t / run.length()));
        points.add(e.floor(at.add(0, 1.5, 0)));
      }
      centre = points.get(points.size() / 2);
    } else {
      centre = e.floor(e.aim(p, 10).add(0, 0.5, 0));
      Vector look = flat(p);
      along = new Vector(-look.getZ(), 0, look.getX());
      int length = (int) Math.min(12, c.n("length", 8));
      for (int i = 0; i < length; i++) points.add(centre.clone().add(along.clone().multiply(i - (length - 1) / 2.0)));
    }
    float sx = (float) (0.35 + 0.75 * Math.abs(along.getX())), sz = (float) (0.35 + 0.75 * Math.abs(along.getZ()));
    int[] fuel = {c.ticks("fuelSeconds", 10)};
    boolean[] on = {true};
    Effects.Fx f = e.fx.add(c.m(), c.id(), 20 * 120, age -> {
      if (!on[0]) return true;
      if (--fuel[0] <= 0) {
        on[0] = false;
        return true;
      }
      for (Location l : points) {
        if (age % 3 == 0) l.getWorld().spawnParticle(Particle.DUST, l.clone().add(0, 1.2, 0), 4, 0.4, 1.0, 0.4, 0, new Particle.DustOptions(Color.fromRGB(0x7FA830), 1.6f));
      }
      if (age % 20 == 0)
        for (LivingEntity v : e.enemies(c.m(), c.id()))
          for (Location l : points)
            if (Math.abs(l.getX() - v.getLocation().getX()) < 1.2 && Math.abs(l.getZ() - v.getLocation().getZ()) < 1.2 && Math.abs(l.getY() - v.getLocation().getY()) < 3) {
              e.slow(v, 1.5, 0);
              e.combat.drain(c.m(), v, (int) c.n("dps", 6));
              break;
            }
      return true;
    });
    List<org.bukkit.entity.BlockDisplay> shown = new ArrayList<>();
    Runnable build = () -> {
      for (Location l : points) shown.add(e.block(f, l.clone(), Material.LIME_STAINED_GLASS, sx, 3f, sz));
    };
    build.run();
    e.track(c, centre, f);
    e.walls.put(c.id(), () -> {
      if (fuel[0] <= 0) return;
      on[0] = !on[0];
      for (var d : shown) d.setVisibleByDefault(on[0]);
      e.sound(centre, on[0] ? "block.fire.ambient" : "block.fire.extinguish", 0.8f, 0.6f);
    });
    f.onEnd(() -> e.walls.remove(c.id()));
    return true;
  }

  /** The marsh spreads in a cone: enemies inside are slowed and revealed to Murk alone. */
  private static boolean bogTide(Ctx c) {
    AbilityEnv e = c.env();
    Player p = c.p();
    Location o = p.getLocation().clone();
    Vector dir = flat(p);
    double range = c.n("range", 14), half = Math.toRadians(c.n("angle", 60) / 2);
    Set<UUID> murk = Set.of(c.id());
    e.fx.add(c.m(), c.id(), c.ticks("seconds", 8), age -> {
      if (age % 2 == 0)
        for (int i = 0; i < 14; i++) {
          double d = 1 + e.random.nextDouble() * (range - 1), a = (e.random.nextDouble() * 2 - 1) * half;
          double cos = Math.cos(a), sin = Math.sin(a);
          Vector v = new Vector(dir.getX() * cos - dir.getZ() * sin, 0, dir.getX() * sin + dir.getZ() * cos).multiply(d);
          o.getWorld().spawnParticle(Particle.DUST, o.clone().add(v).add(0, 0.4 + e.random.nextDouble() * 0.6, 0), 1, 0, 0, 0, 0, new Particle.DustOptions(Color.fromRGB(0x5A6A22), 1.8f));
        }
      if (age % 5 == 0)
        for (LivingEntity v : e.enemies(c.m(), c.id())) {
          Vector to = v.getLocation().toVector().subtract(o.toVector()).setY(0);
          double len = to.length();
          if (len > range || len < 0.01 || Math.acos(Math.max(-1, Math.min(1, to.normalize().dot(dir)))) > half) continue;
          e.slow(v, 1.0, 1);
          e.reveals.reveal(v, c.m().squadOf(c.id()), murk, 25);
        }
      return true;
    });
    e.sound(o, "entity.slime.squish", 1f, 0.5f);
    return true;
  }
}
