package win.vampyuyu.valoblock;

import java.util.Random;

/** The shooting maths, with no Bukkit: hit zones, damage with range falloff, spread, ray against a hitbox. */
public final class GunMath {
  public enum Zone {
    HEAD,
    BODY,
    LEGS
  }

  /** Player hitbox: 0.6 wide, 1.8 tall (1.5 sneaking). */
  public static final double HALF_WIDTH = 0.3;
  public static final double HEIGHT = 1.8;
  public static final double HEIGHT_SNEAKING = 1.5;
  /** Fractions of the box height: below 0.42 (0.75 blocks) legs, from 0.78 (1.4 blocks) head. */
  public static final double LEGS_TOP = 0.42;
  public static final double HEAD_BOTTOM = 0.78;
  /** Moving or airborne spread multipliers on top of the weapon's own {@code moveMult}. */
  public static final double AIR_MULT = 2.5;
  /** Spread cannot grow past this many times the base in one spray. */
  public static final double SPRAY_CAP = 3.0;

  private GunMath() {}

  public static double height(boolean sneaking) {
    return sneaking ? HEIGHT_SNEAKING : HEIGHT;
  }

  /** The zone for a hit at {@code y} above the feet of a box {@code height} tall. */
  public static Zone zone(double y, double height) {
    double f = y / height;
    if (f >= HEAD_BOTTOM) return Zone.HEAD;
    if (f < LEGS_TOP) return Zone.LEGS;
    return Zone.BODY;
  }

  public static int base(VbWeapon w, Zone z) {
    return switch (z) {
      case HEAD -> w.head();
      case BODY -> w.body();
      case LEGS -> w.legs();
    };
  }

  /** 1 up to falloffStart, linear down to falloffMin at falloffEnd. */
  public static double falloff(VbWeapon w, double distance) {
    if (distance <= w.falloffStart()) return 1;
    if (distance >= w.falloffEnd()) return w.falloffMin();
    double t = (distance - w.falloffStart()) / (w.falloffEnd() - w.falloffStart());
    return 1 - t * (1 - w.falloffMin());
  }

  /** Damage of one projectile (a pellet for a shotgun), at least 1. {@code wallFactor} is 1 when nothing was crossed. */
  public static int damage(VbWeapon w, Zone z, double distance, double wallFactor) {
    int d = (int) Math.round(base(w, z) * falloff(w, distance) * wallFactor);
    return Math.max(1, d);
  }

  /**
   * Melee: a backstab when the attacker stands behind the victim, that is the victim's facing and the
   * direction from the victim to the attacker (both horizontal) point opposite ways (dot below -0.5).
   */
  public static int knifeDamage(VbWeapon knife, double victimFaceX, double victimFaceZ, double toAttackerX, double toAttackerZ) {
    double a = Math.hypot(toAttackerX, toAttackerZ), v = Math.hypot(victimFaceX, victimFaceZ);
    if (a == 0 || v == 0) return knife.body();
    double dot = (toAttackerX * victimFaceX + toAttackerZ * victimFaceZ) / (a * v);
    return dot < -0.5 && knife.backstab() > 0 ? knife.backstab() : knife.body();
  }

  /** Cone half-angle in degrees for a shot: sneak tightens, moving widens, air widens more, a spray grows it. */
  public static double spread(VbWeapon w, boolean sneaking, boolean moving, boolean airborne, int shotInSpray) {
    if (w.firstShotPerfect() && shotInSpray == 0 && !moving && !airborne) return 0;
    double s = w.spread();
    double grow = Math.min(SPRAY_CAP, 1 + w.sprayGrow() * Math.max(0, shotInSpray));
    s *= grow;
    if (sneaking) s *= w.sneakMult();
    if (moving) s *= w.moveMult();
    if (airborne) s *= AIR_MULT;
    return s;
  }

  /** A direction inside a cone of {@code degrees} half-angle around {@code dir} (unit vector), uniform over the disc. */
  public static double[] jitter(double[] dir, double degrees, Random rnd) {
    if (degrees <= 0) return dir.clone();
    double r = Math.tan(Math.toRadians(degrees)) * Math.sqrt(rnd.nextDouble());
    double a = rnd.nextDouble() * 2 * Math.PI;
    return offset(dir, r * Math.cos(a), r * Math.sin(a));
  }

  /** {@code dir} bent by (right, up) tangent offsets, renormalised. */
  static double[] offset(double[] dir, double right, double up) {
    double[] ref = Math.abs(dir[1]) > 0.99 ? new double[] {1, 0, 0} : new double[] {0, 1, 0};
    double[] u = norm(cross(dir, ref)); // right
    double[] v = cross(u, dir); // up
    return norm(new double[] {dir[0] + u[0] * right + v[0] * up, dir[1] + u[1] * right + v[1] * up, dir[2] + u[2] * right + v[2] * up});
  }

  private static double[] cross(double[] a, double[] b) {
    return new double[] {a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]};
  }

  private static double[] norm(double[] a) {
    double l = Math.sqrt(a[0] * a[0] + a[1] * a[1] + a[2] * a[2]);
    return new double[] {a[0] / l, a[1] / l, a[2] / l};
  }

  /** A ray against a player's hitbox. */
  public record Hit(double distance, Zone zone) {}

  /**
   * Ray from {@code o} along unit {@code d} against the box of a player whose feet are at
   * (fx, fy, fz). Returns null on a miss or when the hit is beyond {@code max}.
   */
  public static Hit rayHitbox(double[] o, double[] d, double max, double fx, double fy, double fz, boolean sneaking) {
    double h = height(sneaking);
    double[] min = {fx - HALF_WIDTH, fy, fz - HALF_WIDTH};
    double[] mx = {fx + HALF_WIDTH, fy + h, fz + HALF_WIDTH};
    double t0 = 0, t1 = max;
    for (int i = 0; i < 3; i++) {
      if (Math.abs(d[i]) < 1e-9) {
        if (o[i] < min[i] || o[i] > mx[i]) return null;
      } else {
        double a = (min[i] - o[i]) / d[i], b = (mx[i] - o[i]) / d[i];
        if (a > b) {
          double t = a;
          a = b;
          b = t;
        }
        t0 = Math.max(t0, a);
        t1 = Math.min(t1, b);
        if (t0 > t1) return null;
      }
    }
    double y = o[1] + d[1] * t0 - fy;
    return new Hit(t0, zone(Math.max(0, Math.min(h, y)), h));
  }
}
