package win.vampyuyu.valoblock;

/**
 * A hold-to-complete action (planting, sealing). Progress grows while somebody holds; letting go
 * drops it back to the last checkpoint reached (0 when there is none). Pure, driven by dt.
 */
public final class HoldTimer {
  private final double required, checkpoint;
  private double progress;

  /** A checkpoint of 0 means "no checkpoint": a release resets everything. */
  public HoldTimer(double required, double checkpoint) {
    if (required <= 0 || checkpoint < 0 || checkpoint >= required) throw new IllegalArgumentException("Bad hold times");
    this.required = required;
    this.checkpoint = checkpoint;
  }

  /** Advances by dt seconds; returns true once complete. */
  public boolean tick(boolean holding, double dt) {
    if (holding) {
      progress = Math.min(required, progress + dt);
    } else if (checkpoint > 0 && progress >= checkpoint) {
      progress = checkpoint;
    } else {
      progress = 0;
    }
    return done();
  }

  public boolean done() {
    return progress >= required;
  }

  public double progress() {
    return progress;
  }

  public double fraction() {
    return progress / required;
  }

  public void reset() {
    progress = 0;
  }
}
