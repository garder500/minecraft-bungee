package win.vampyuyu.valoblock;

import java.util.*;

/**
 * One bot's decisions, with no Bukkit: where to go, whom to shoot, when to plant or seal, which
 * ability to use. The service feeds it what the bot can see and does what it asks. Time is passed in.
 */
public final class BotBrain {
  /** An enemy in the bot's view. */
  public record Seen(UUID id, double x, double y, double z, boolean sneaking) {}

  /** What the bot knows this tick. */
  public record View(
      VbPhase phase,
      VbSide side,
      boolean carrier,
      double[] seedDrop,
      boolean chaseSeed,
      String plantedSite,
      String attackSite,
      boolean released,
      String defendSite,
      String anchorNode,
      boolean inPlantSite,
      List<Seen> visible,
      boolean smokeReady,
      boolean flashReady,
      boolean ultReady) {}

  /** What the bot does this tick. */
  public static final class Act {
    public UUID target;
    public boolean fire, holdPlant, holdSeal, pickUpSeed;
    public String ability; // "smoke", "flash", "ult" or null
    public double[] abilityAt;
    public double yaw, pitch;
  }

  private static final long ABILITY_GAP_MS = 1500;

  private final UUID id;
  private final BotConfig cfg;
  private final BotConfig.Level level;
  private final Waypoints g;
  private final BotMover mover = new BotMover();
  private final BotSight sight = new BotSight();
  private String goal = "";
  private boolean smoked, flashed;
  private long lastAbility = Long.MIN_VALUE / 2;
  private double yaw, pitch;

  public BotBrain(UUID id, BotConfig cfg, BotConfig.Level level, Waypoints g) {
    this.id = id;
    this.cfg = cfg;
    this.level = level;
    this.g = g;
  }

  public UUID id() {
    return id;
  }

  public BotMover mover() {
    return mover;
  }

  public BotConfig.Level level() {
    return level;
  }

  /** A new round: forget the plan, stand at the spawn. */
  public void reset(double x, double y, double z) {
    mover.teleport(x, y, z);
    sight.clear();
    goal = "";
    smoked = flashed = false;
  }

  private void goTo(String key, List<double[]> route) {
    if (key.equals(goal)) return;
    goal = key;
    mover.go(route);
  }

  private void goToNode(String node) {
    if (("n:" + node).equals(goal)) return;
    goTo("n:" + node, g.route(mover.x(), mover.y(), mover.z(), node));
  }

  public Act think(long now, double dt, View v) {
    Act a = new Act();
    boolean fighting = v.phase() == VbPhase.COMBAT || v.phase() == VbPhase.PLANTED;
    if (!fighting) return a; // buying, selecting, round end: stand still
    Set<UUID> ids = new HashSet<>();
    for (Seen s : v.visible()) ids.add(s.id());
    sight.update(now, ids);

    // Shooting: the nearest enemy that has been in view for the reaction time. While one is in view the bot stands and fights.
    Seen nearest = null, ready = null;
    double nd = Double.MAX_VALUE, rd = Double.MAX_VALUE;
    for (Seen s : v.visible()) {
      double d = dist(s);
      if (d > cfg.sightRange()) continue;
      if (d < nd) {
        nd = d;
        nearest = s;
      }
      if (sight.ready(s.id(), now, level.reactionMs()) && d < rd) {
        rd = d;
        ready = s;
      }
    }
    if (ready != null) {
      a.target = ready.id();
      a.fire = true;
    }
    Seen look = ready != null ? ready : nearest;
    if (look != null) {
      face(look);
    }
    a.yaw = look != null ? yaw : mover.yaw();
    a.pitch = look != null ? pitch : 0;
    if (look != null) {
      if (v.ultReady()) {
        int near = 0;
        for (Seen s : v.visible()) if (dist(s) <= cfg.ultRange()) near++;
        if (near >= cfg.ultMinEnemies() && ready(now)) {
          a.ability = "ult";
          lastAbility = now;
        }
      }
      return a; // in contact: no walking
    }

    // Seed pickup: a bot standing on a dropped Seed takes it.
    if (v.seedDrop() != null && v.side() == VbSide.SEEDBEARERS && mover.distanceTo(v.seedDrop()[0], v.seedDrop()[2]) <= cfg.pickupRadius()) a.pickUpSeed = true;

    String movingTo = null;
    if (v.side() == VbSide.SEEDBEARERS) {
      if (v.plantedSite() != null) {
        movingTo = nearestAnchor(v.plantedSite());
      } else if (v.chaseSeed() && v.seedDrop() != null) {
        if (!"seed".equals(goal)) {
          List<double[]> r = new ArrayList<>(g.route(mover.x(), mover.y(), mover.z(), g.nearest(v.seedDrop()[0], v.seedDrop()[1], v.seedDrop()[2]).id()));
          r.add(v.seedDrop());
          goTo("seed", r);
        }
        movingTo = "";
      } else if (!v.released()) {
        movingTo = g.site(v.attackSite()).stage();
      } else if (v.carrier()) {
        if (v.inPlantSite()) {
          a.holdPlant = true;
          mover.stop();
          goal = "plant";
        } else movingTo = g.site(v.attackSite()).plant();
      } else {
        List<String> anchors = g.site(v.attackSite()).anchors();
        movingTo = anchors.get(Math.floorMod(id.hashCode(), anchors.size()));
      }
      abilitiesAttack(a, now, v);
    } else {
      if (v.plantedSite() != null) {
        String plant = g.site(v.plantedSite()).plant();
        Waypoints.Node n = g.node(plant);
        if (mover.distanceTo(n.x(), n.z()) <= cfg.sealRadius()) {
          a.holdSeal = true;
          mover.stop();
          goal = "seal";
        } else movingTo = plant;
      } else if (v.anchorNode() != null) {
        movingTo = v.anchorNode();
      }
    }
    if (movingTo != null && !movingTo.isEmpty()) goToNode(movingTo);
    double moved = mover.step(dt, cfg.speed());
    if (moved > 0) a.yaw = mover.yaw();
    return a;
  }

  private boolean ready(long now) {
    return now - lastAbility >= ABILITY_GAP_MS;
  }

  private String nearestAnchor(String site) {
    List<String> anchors = g.site(site).anchors();
    String best = anchors.get(0);
    double bd = Double.MAX_VALUE;
    for (String a : anchors) {
      Waypoints.Node n = g.node(a);
      double d = mover.distanceTo(n.x(), n.z());
      if (d < bd) {
        bd = d;
        best = a;
      }
    }
    return best;
  }

  /** Smokes on the site's smoke spots once the group is released; a flash before a peek corner. */
  private void abilitiesAttack(Act a, long now, View v) {
    if (a.ability != null || !v.released() || v.attackSite() == null || !ready(now)) return;
    Waypoints.SiteInfo site = g.site(v.attackSite());
    if (v.smokeReady() && !smoked)
      for (Waypoints.Smoke s : site.smokes()) {
        Waypoints.Node n = g.node(s.node());
        if (mover.distanceTo(n.x(), n.z()) <= 2.5) {
          smoked = true;
          lastAbility = now;
          a.ability = "smoke";
          a.abilityAt = s.at();
          return;
        }
      }
    if (v.flashReady() && !flashed)
      for (String p : site.peeks()) {
        Waypoints.Node n = g.node(p);
        if (mover.distanceTo(n.x(), n.z()) <= 3.0) {
          Waypoints.Node plant = g.node(site.plant());
          double dx = plant.x() - n.x(), dz = plant.z() - n.z(), len = Math.max(0.001, Math.hypot(dx, dz));
          flashed = true;
          lastAbility = now;
          a.ability = "flash";
          a.abilityAt = new double[] {n.x() + dx / len * 6, n.y() + 1.5, n.z() + dz / len * 6};
          return;
        }
      }
  }

  private double dist(Seen s) {
    return Math.sqrt(Math.pow(s.x() - mover.x(), 2) + Math.pow(s.y() - mover.y(), 2) + Math.pow(s.z() - mover.z(), 2));
  }

  private void face(Seen s) {
    double dx = s.x() - mover.x(), dz = s.z() - mover.z(), dy = (s.y() + 1.0) - (mover.y() + 1.62);
    yaw = Math.toDegrees(Math.atan2(-dx, dz));
    pitch = -Math.toDegrees(Math.atan2(dy, Math.hypot(dx, dz)));
  }
}
