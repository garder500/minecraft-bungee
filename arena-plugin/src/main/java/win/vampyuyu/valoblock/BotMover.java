package win.vampyuyu.valoblock;

import java.util.List;

/** A bot's body on the waypoint graph: a position that follows a list of positions in straight lines, y interpolated along each leg. Pure. */
public final class BotMover {
  private double x, y, z;
  private double yaw;
  private List<double[]> route = List.of();
  private int next;
  private double legStartY, legLen, legDone;

  public void teleport(double x, double y, double z) {
    this.x = x;
    this.y = y;
    this.z = z;
    route = List.of();
    next = 0;
  }

  public double x() {
    return x;
  }

  public double y() {
    return y;
  }

  public double z() {
    return z;
  }

  /** Minecraft yaw in degrees of the last step (0 = +Z, 90 = -X). */
  public double yaw() {
    return yaw;
  }

  public boolean arrived() {
    return next >= route.size();
  }

  /** Follows these positions from the current one. */
  public void go(List<double[]> positions) {
    route = positions;
    next = 0;
    startLeg();
  }

  public void stop() {
    route = List.of();
    next = 0;
  }

  private void startLeg() {
    if (next >= route.size()) return;
    double[] t = route.get(next);
    legStartY = y;
    legLen = Math.hypot(t[0] - x, t[2] - z);
    legDone = 0;
  }

  /** Moves {@code speed * seconds} blocks along the route; returns the distance covered. */
  public double step(double seconds, double speed) {
    double budget = speed * seconds, covered = 0;
    while (budget > 1e-9 && next < route.size()) {
      double[] t = route.get(next);
      double dx = t[0] - x, dz = t[2] - z, d = Math.hypot(dx, dz);
      if (d < 1e-6) {
        y = t[1];
        next++;
        startLeg();
        continue;
      }
      double move = Math.min(d, budget);
      x += dx / d * move;
      z += dz / d * move;
      legDone += move;
      y = legLen < 1e-6 ? t[1] : legStartY + (t[1] - legStartY) * Math.min(1, legDone / legLen);
      yaw = Math.toDegrees(Math.atan2(-dx, dz));
      budget -= move;
      covered += move;
      if (move >= d - 1e-9) {
        x = t[0];
        z = t[2];
        y = t[1];
        next++;
        startLeg();
      }
    }
    return covered;
  }

  /** Horizontal distance to a point. */
  public double distanceTo(double px, double pz) {
    return Math.hypot(px - x, pz - z);
  }
}
