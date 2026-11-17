package win.vampyuyu.valoblock;

import java.util.List;
import java.util.UUID;

/** Who may seal the planted Seed, as plain rules (no Bukkit): side, alive, range, aim, and one sealer at a time. */
final class SealRules {
  static final double RANGE = 2.5;
  static final double MAX_ANGLE = 35;

  private SealRules() {}

  /** One player who might be sealing this tick. */
  record Candidate(UUID id, VbSide side, boolean alive, double distance, double angle, boolean scoped, boolean holding) {}

  /** A living Sealer within range, looking roughly at the Seed, not scoped in, holding right-click. */
  static boolean mayHold(Candidate c) {
    return c.side() == VbSide.SEALERS && c.alive() && c.holding() && !c.scoped() && c.distance() <= RANGE && c.angle() <= MAX_ANGLE;
  }

  /** A Sealer close enough to be told how to seal (no aim or hold needed). */
  static boolean inReach(VbSide side, boolean alive, double distance) {
    return side == VbSide.SEALERS && alive && distance <= RANGE;
  }

  /** The one who seals this tick: the current sealer while they still may, else the first candidate who may, else null. */
  static UUID pick(UUID current, List<Candidate> candidates) {
    if (current != null) for (Candidate c : candidates) if (c.id().equals(current) && mayHold(c)) return current;
    for (Candidate c : candidates) if (mayHold(c)) return c.id();
    return null;
  }

  /** Angle in degrees between a (unit) look direction and the direction to the target, 180 when on top of it is not a problem (0). */
  static double angle(double lx, double ly, double lz, double tx, double ty, double tz) {
    double tl = Math.sqrt(tx * tx + ty * ty + tz * tz);
    if (tl < 1e-6) return 0;
    double ll = Math.sqrt(lx * lx + ly * ly + lz * lz);
    if (ll < 1e-6) return 180;
    double d = (lx * tx + ly * ty + lz * tz) / (ll * tl);
    return Math.toDegrees(Math.acos(Math.max(-1, Math.min(1, d))));
  }

  /** Seconds between two ticks of the fuse: 1 s at the start, 0.15 s at the end. */
  static double tickInterval(double elapsed, double fuse) {
    double f = Math.max(0, Math.min(1, elapsed / Math.max(0.001, fuse)));
    return 1.0 - 0.85 * f * f;
  }
}
