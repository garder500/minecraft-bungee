package win.vampyuyu.valoblock;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;
import win.vampyuyu.valoblock.AbilityEnv.Ctx;

/** The Vanguards and Wardens: Cinder, Gale, Thorn and Rime. Each returns false when it could not be used (the charge is given back). */
final class AbilitiesA {
  private AbilitiesA() {}

  static boolean cast(Ctx c) {
    return switch (c.ab().id()) {
      case "slag_pot" -> slagPot(c);
      case "heat_shimmer" -> heatShimmer(c);
      case "ember_dash" -> emberDash(c);
      case "colossus_step" -> colossusStep(c);
      case "wind_pad" -> windPad(c);
      case "static_orb" -> staticOrb(c);
      case "charged_slide" -> chargedSlide(c);
      case "storm_call" -> stormCall(c);
      case "root_snare" -> trap(c, Particle.HAPPY_VILLAGER, false);
      case "spore_mend" -> sporeMend(c);
      case "bark_wall" -> wall(c, Material.OAK_LOG, c.ticks("seconds", 12), (int) c.n("hits", 12), (int) c.n("width", 5), (int) c.n("height", 3), 8);
      case "overgrowth" -> overgrowth(c);
      case "frost_mine" -> trap(c, Particle.SNOWFLAKE, true);
      case "stasis_pane" -> wall(c, Material.PACKED_ICE, c.ticks("seconds", 20), (int) c.n("hits", 3), (int) c.n("width", 3), (int) c.n("height", 3), 8);
      case "hold_moment" -> holdMoment(c);
      case "glacial_hour" -> glacialHour(c);
      default -> false;
    };
  }

  private static Vector flat(Player p) {
    Vector d = p.getLocation().getDirection().setY(0);
    return d.lengthSquared() < 1e-6 ? new Vector(0, 0, 1) : d.normalize();
  }

  // ------------------------------------------------------------------ Cinder

  /** Thrown pot: temporary magma blocks under a pool, 30 damage a second to the enemies on it. */
  private static boolean slagPot(Ctx c) {
    AbilityEnv e = c.env();
    e.ball(c, Material.MAGMA_CREAM, 1, l -> {}, land -> {
      Location centre = e.floor(land.clone().add(0, 0.6, 0));
      double r = c.n("radius", 2.5);
      Effects.Fx f = e.fx.add(c.m(), c.id(), c.ticks("seconds", 6), age -> {
        if (age % 3 == 0) centre.getWorld().spawnParticle(Particle.FLAME, centre.clone().add(0, 0.2, 0), 6, r / 2, 0.1, r / 2, 0.01);
        if (age % 10 == 0) centre.getWorld().spawnParticle(Particle.LAVA, centre.clone().add(0, 0.3, 0), 2, r / 2, 0, r / 2, 0);
        if (age % 20 == 0)
          for (Player v : e.enemies(c.m(), c.id())) if (AbilityEnv.near(v, centre, r) && v.getLocation().getY() < centre.getY() + 1.2) e.combat.damage(c.m(), v, c.id(), (int) c.n("dps", 30));
        return true;
      });
      int placed = 0;
      for (int dx = (int) -Math.ceil(r); dx <= Math.ceil(r) && placed < 30; dx++)
        for (int dz = (int) -Math.ceil(r); dz <= Math.ceil(r) && placed < 30; dz++) {
          if (dx * dx + dz * dz > r * r) continue;
          Location col = e.floor(new Location(centre.getWorld(), centre.getX() + dx, centre.getY() + 1.2, centre.getZ() + dz));
          Block under = col.clone().subtract(0, 0.2, 0).getBlock();
          if (Math.abs(col.getY() - centre.getY()) > 1.1 || !under.getType().isOccluding()) continue;
          e.temp.set(under, Material.MAGMA_BLOCK);
          f.onEnd(() -> e.temp.release(under));
          placed++;
        }
      e.sound(centre, "block.lava.pop", 1f, 0.8f);
    });
    return true;
  }

  private static boolean heatShimmer(Ctx c) {
    Player p = c.p();
    int ticks = c.ticks("seconds", 6);
    p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, ticks, 0, false, false, true));
    p.setSilent(true); // silent footsteps
    c.env().fx.add(c.m(), c.id(), ticks, age -> {
      if (age % 4 == 0) p.getWorld().spawnParticle(Particle.FLAME, p.getLocation().add(0, 0.1, 0), 2, 0.3, 0.05, 0.3, 0.005);
      return true;
    }).onEnd(() -> p.setSilent(false));
    return true;
  }

  /** Short dash with a fire trail that burns whoever stands on it. */
  private static boolean emberDash(Ctx c) {
    Player p = c.p();
    AbilityEnv e = c.env();
    p.setVelocity(flat(p).multiply(c.n("power", 1.6)).setY(0.2));
    int trail = c.ticks("trailSeconds", 3);
    List<Object[]> points = new ArrayList<>(); // {Location, birth age}
    e.fx.add(c.m(), c.id(), 10 + trail, age -> {
      if (age <= 10 && age % 2 == 0) points.add(new Object[] {p.getLocation().clone(), age});
      points.removeIf(o -> age - (int) o[1] > trail);
      for (Object[] o : points) {
        Location l = (Location) o[0];
        l.getWorld().spawnParticle(Particle.FLAME, l, 2, 0.25, 0.1, 0.25, 0.01);
        if (age % 10 == 0) for (Player v : e.enemies(c.m(), c.id())) if (AbilityEnv.near(v, l, 1.0) && Math.abs(v.getLocation().getY() - l.getY()) < 1.5) e.combat.damage(c.m(), v, c.id(), (int) (c.n("dps", 15) / 2));
      }
      return true;
    });
    e.sound(p.getLocation(), "entity.blaze.shoot", 0.8f, 1.2f);
    return true;
  }

  /** Leap, then a slam when landing: knockback ring, 60 damage at the centre. */
  private static boolean colossusStep(Ctx c) {
    Player p = c.p();
    AbilityEnv e = c.env();
    Vector fwd = flat(p).multiply(0.5);
    p.setVelocity(new Vector(fwd.getX(), c.n("leap", 1.0), fwd.getZ()));
    e.fx.add(c.m(), c.id(), 50, age -> {
      p.getWorld().spawnParticle(Particle.FLAME, p.getLocation(), 3, 0.2, 0.1, 0.2, 0.01);
      if (age < 6 || !p.isOnGround()) return true;
      Location at = p.getLocation();
      double r = c.n("radius", 6);
      for (Player v : e.enemies(c.m(), c.id())) {
        double d = v.getLocation().distance(at);
        if (d > r) continue;
        e.combat.damage(c.m(), v, c.id(), (int) Math.max(10, Math.round(c.n("damage", 60) * (1 - d / r))));
        Vector out = v.getLocation().toVector().subtract(at.toVector()).setY(0);
        out = out.lengthSquared() < 1e-6 ? new Vector(0, 0, 0) : out.normalize();
        v.setVelocity(out.multiply(c.n("knockback", 1.2)).setY(0.45));
      }
      AbilityEnv.ring(at.clone().add(0, 0.2, 0), r, Particle.FLAME, 48);
      AbilityEnv.ring(at.clone().add(0, 0.2, 0), r / 2, Particle.EXPLOSION, 12);
      e.sound(at, "entity.generic.explode", 1f, 0.7f);
      return false;
    });
    return true;
  }

  // ------------------------------------------------------------------ Gale

  /** A wind pad on the floor: anyone who steps on it is launched. */
  private static boolean windPad(Ctx c) {
    AbilityEnv e = c.env();
    Location at = e.floor(e.aim(c.p(), 6).add(0, 0.5, 0));
    Map<UUID, Integer> last = new HashMap<>();
    Effects.Fx f = e.fx.add(c.m(), c.id(), c.ticks("seconds", 20), null);
    e.block(f, at.clone().add(0, 0.01, 0), Material.LIGHT_BLUE_CONCRETE, 1.6f, 0.08f, 1.6f);
    Effects.Fx run = e.fx.add(c.m(), c.id(), c.ticks("seconds", 20), age -> {
      if (age % 6 == 0) at.getWorld().spawnParticle(Particle.CLOUD, at.clone().add(0, 0.2, 0), 3, 0.5, 0.05, 0.5, 0.02);
      for (Player v : e.everyone(c.m())) {
        Location l = v.getLocation();
        if (Math.abs(l.getX() - at.getX()) > 0.95 || Math.abs(l.getZ() - at.getZ()) > 0.95 || l.getY() < at.getY() - 0.6 || l.getY() > at.getY() + 1.2) continue;
        if (age - last.getOrDefault(v.getUniqueId(), -100) < 10) continue;
        last.put(v.getUniqueId(), age);
        Vector vel = v.getVelocity();
        v.setVelocity(new Vector(vel.getX(), c.n("launch", 1.4), vel.getZ()));
        e.sound(at, "entity.breeze.jump", 0.8f, 1.3f);
        at.getWorld().spawnParticle(Particle.CLOUD, at.clone().add(0, 0.3, 0), 12, 0.3, 0.1, 0.3, 0.1);
      }
      return true;
    });
    run.onEnd(f::stop);
    return true;
  }

  /** A bouncing orb: on its second bounce it flashes everyone facing it. */
  private static boolean staticOrb(Ctx c) {
    AbilityEnv e = c.env();
    e.ball(c, Material.HEART_OF_THE_SEA, (int) c.n("bounces", 2), l -> e.sound(l, "block.amethyst_block.chime", 0.8f, 1.6f), l -> e.flash(c, l.add(0, 0.3, 0), c.n("range", 24), c.n("blindSeconds", 1.5)));
    return true;
  }

  /** A low slide under fire: the push, and the reload is instant for as long as it lasts. */
  private static boolean chargedSlide(Ctx c) {
    Player p = c.p();
    AbilityEnv e = c.env();
    Vector dir = flat(p);
    double power = c.n("power", 1.1);
    int ticks = c.ticks("seconds", 1.5);
    e.plugin.valoblock.weapons.reloadModifier(c.id(), 100, c.n("seconds", 1.5));
    e.fx.add(c.m(), c.id(), ticks, age -> {
      if (age <= 8) p.setVelocity(dir.clone().multiply(power * (1 - age / 10.0)).setY(p.getVelocity().getY()));
      if (age % 2 == 0) p.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, p.getLocation().add(0, 0.2, 0), 4, 0.3, 0.1, 0.3, 0.05);
      return true;
    });
    e.sound(p.getLocation(), "entity.lightning_bolt.impact", 0.3f, 2f);
    return true;
  }

  /** Marks three spots (under the nearest enemies in range, else along the crosshair); lightning after 1.5 s. */
  private static boolean stormCall(Ctx c) {
    AbilityEnv e = c.env();
    double range = c.n("range", 30);
    List<Location> spots = new ArrayList<>();
    List<Player> near = new ArrayList<>(e.enemies(c.m(), c.id()));
    near.removeIf(v -> v.getLocation().distance(c.p().getLocation()) > range);
    near.sort((a, b) -> Double.compare(a.getLocation().distanceSquared(c.p().getLocation()), b.getLocation().distanceSquared(c.p().getLocation())));
    for (Player v : near) if (spots.size() < (int) c.n("spots", 3)) spots.add(v.getLocation().clone());
    Location aim = e.aim(c.p(), range);
    for (int i = spots.size(); i < (int) c.n("spots", 3); i++) spots.add(aim.clone().add((i - 1) * 2.5, 0, (i % 2) * 2.0));
    e.fx.add(c.m(), c.id(), c.ticks("delaySeconds", 1.5), age -> {
      for (Location s : spots) if (age % 3 == 0) AbilityEnv.ring(s.clone().add(0, 0.1, 0), c.n("radius", 3), Particle.ELECTRIC_SPARK, 14);
      return true;
    }).onEnd(() -> {
      for (Location s : spots) {
        s.getWorld().strikeLightningEffect(s);
        for (Player v : e.enemies(c.m(), c.id())) if (AbilityEnv.near(v, s, c.n("radius", 3))) e.combat.damage(c.m(), v, c.id(), (int) c.n("damage", 90));
      }
    });
    return true;
  }

  // ------------------------------------------------------------------ Thorn and Rime: hidden traps

  /** A trap nobody sees: the first enemy within reach is slowed, and (root) revealed. {@code mine}: the slow is Slowness II (the frost mine). */
  private static boolean trap(Ctx c, Particle mark, boolean mine) {
    AbilityEnv e = c.env();
    Location at = e.floor(e.aim(c.p(), 5).add(0, 0.5, 0));
    double radius = c.n("triggerRadius", 1.5);
    Set<UUID> team = e.team(c.m(), c.id());
    e.fx.add(c.m(), c.id(), 20 * 600, age -> {
      if (age % 20 == 0) for (UUID id : team) {
        Player a = org.bukkit.Bukkit.getPlayer(id);
        if (a != null) a.spawnParticle(mark, at.clone().add(0, 0.2, 0), 3, 0.3, 0.05, 0.3, 0.01); // only the owner's team sees it
      }
      if (age % 2 != 0) return true;
      for (Player v : e.enemies(c.m(), c.id())) {
        if (!AbilityEnv.near(v, at, radius)) continue;
        e.slow(v, c.n("slowSeconds", mine ? 4 : 3), mine ? (int) c.n("slowLevel", 1) : 1);
        if (!mine) e.reveal(c, v, c.n("revealSeconds", 3));
        at.getWorld().spawnParticle(mark, at.clone().add(0, 0.5, 0), 30, 0.5, 0.5, 0.5, 0.05);
        e.sound(at, mine ? "block.glass.break" : "block.grass.break", 1f, 0.8f);
        return false;
      }
      return true;
    });
    return true;
  }

  private static boolean sporeMend(Ctx c) {
    AbilityEnv e = c.env();
    Location at = e.floor(e.aim(c.p(), 6).add(0, 0.5, 0));
    double r = c.n("radius", 3.5), seconds = c.n("seconds", 4);
    int perSecond = (int) Math.round(c.n("heal", 40) / seconds);
    e.fx.add(c.m(), c.id(), c.ticks("seconds", 4), age -> {
      if (age % 3 == 0) at.getWorld().spawnParticle(Particle.SPORE_BLOSSOM_AIR, at.clone().add(0, 0.8, 0), 6, r / 2, 0.5, r / 2, 0);
      if (age % 20 == 0) for (Player a : e.allies(c.m(), c.id())) if (AbilityEnv.near(a, at, r)) e.combat.heal(c.m(), a, perSecond);
      return true;
    });
    e.sound(at, "block.moss.place", 1f, 0.8f);
    return true;
  }

  /** A wall of temporary blocks across the crosshair; shots wear it down. */
  private static boolean wall(Ctx c, Material m, int ticks, int hits, int width, int height, double range) {
    AbilityEnv e = c.env();
    Location base = e.floor(e.aim(c.p(), range).add(0, 0.5, 0));
    TempBlocks.Wall w = e.temp.wall(AbilityEnv.wallBlocks(base, c.p().getLocation().getDirection(), width, height), m, hits);
    if (w == null) return false;
    e.fx.add(c.m(), c.id(), ticks, null).onEnd(() -> e.temp.remove(w));
    e.sound(base, m == Material.PACKED_ICE ? "block.glass.place" : "block.wood.place", 1f, 0.8f);
    return true;
  }

  /** Vines (displays, never real blocks) over the nearest site: whoever touches them is slowed and revealed. */
  private static boolean overgrowth(Ctx c) {
    AbilityEnv e = c.env();
    Player p = c.p();
    Location centre = null;
    double best = 40;
    for (VbSite s : VbData.sites()) {
      Location mid = new Location(p.getWorld(), (s.min()[0] + s.max()[0]) / 2, (s.min()[1] + s.max()[1]) / 2, (s.min()[2] + s.max()[2]) / 2);
      double d = mid.distance(p.getLocation());
      if (d < best) {
        best = d;
        centre = mid;
      }
    }
    Location at = e.floor((centre == null ? e.aim(p, 12) : centre).clone().add(0, 1.5, 0));
    double r = c.n("radius", 7);
    Effects.Fx f = e.fx.add(c.m(), c.id(), c.ticks("seconds", 10), null);
    List<Location> vines = new ArrayList<>();
    for (double dx = -r; dx <= r; dx += 2)
      for (double dz = -r; dz <= r; dz += 2) {
        if (dx * dx + dz * dz > r * r || vines.size() >= 50) continue;
        Location v = e.floor(new Location(at.getWorld(), at.getX() + dx, at.getY() + 2, at.getZ() + dz));
        if (Math.abs(v.getY() - at.getY()) > 2.5) continue;
        vines.add(v);
        e.block(f, v, Material.MOSS_CARPET, 2f, 0.06f, 2f);
      }
    Effects.Fx run = e.fx.add(c.m(), c.id(), c.ticks("seconds", 10), age -> {
      if (age % 10 != 0) return true;
      for (Player v : e.enemies(c.m(), c.id())) {
        if (!AbilityEnv.near(v, at, r)) continue;
        for (Location l : vines)
          if (Math.abs(l.getX() - v.getLocation().getX()) < 1.1 && Math.abs(l.getZ() - v.getLocation().getZ()) < 1.1 && Math.abs(l.getY() - v.getLocation().getY()) < 1.3) {
            e.slow(v, c.n("slowSeconds", 2), 1);
            e.reveal(c, v, c.n("revealSeconds", 2));
            break;
          }
      }
      return true;
    });
    run.onEnd(f::stop);
    e.sound(at, "block.vine.place", 1f, 0.7f);
    return true;
  }

  // ------------------------------------------------------------------ Rime

  /** Freeze yourself: no damage, no action. */
  private static boolean holdMoment(Ctx c) {
    Player p = c.p();
    AbilityEnv e = c.env();
    int ticks = c.ticks("seconds", 2);
    c.m().setInvulnerable(c.id(), true);
    e.frozen.add(c.id());
    p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, ticks, 255, false, false, false));
    p.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, ticks, 200, false, false, false));
    Effects.Fx f = e.fx.add(c.m(), c.id(), ticks, age -> {
      p.setVelocity(new Vector(0, Math.min(0, p.getVelocity().getY()), 0));
      return true;
    });
    e.block(f, p.getLocation().add(0, -0.1, 0), Material.BLUE_ICE, 1.3f, 2.3f, 1.3f);
    f.onEnd(() -> {
      c.m().setInvulnerable(c.id(), false);
      e.frozen.remove(c.id());
      p.removePotionEffect(PotionEffectType.SLOWNESS);
      p.removePotionEffect(PotionEffectType.JUMP_BOOST);
    });
    e.sound(p.getLocation(), "block.glass.place", 1f, 0.5f);
    return true;
  }

  /** A dome: enemies inside are slowed and cannot reload. */
  private static boolean glacialHour(Ctx c) {
    AbilityEnv e = c.env();
    Location centre = c.p().getLocation().clone();
    double r = c.n("radius", 12);
    e.fx.add(c.m(), c.id(), c.ticks("seconds", 8), age -> {
      if (age % 2 == 0)
        for (int i = 0; i < 24; i++) {
          double a = e.random.nextDouble() * Math.PI * 2, b = e.random.nextDouble() * Math.PI / 2;
          centre.getWorld().spawnParticle(Particle.SNOWFLAKE, centre.getX() + r * Math.cos(a) * Math.cos(b), centre.getY() + r * Math.sin(b), centre.getZ() + r * Math.sin(a) * Math.cos(b), 1, 0, 0, 0, 0);
        }
      if (age % 5 == 0)
        for (Player v : e.enemies(c.m(), c.id())) {
          if (v.getLocation().distance(centre) > r) continue;
          e.slow(v, 0.6, 1);
          e.plugin.valoblock.weapons.reloadModifier(v.getUniqueId(), 0, 0.5);
        }
      return true;
    });
    e.sound(centre, "block.beacon.activate", 1f, 0.6f);
    return true;
  }
}
