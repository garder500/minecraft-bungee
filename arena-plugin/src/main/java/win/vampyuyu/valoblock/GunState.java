package win.vampyuyu.valoblock;

/**
 * Magazine, reload, fire rate, burst, spin-up and the spray counter of one gun in one player's
 * hands. Pure and time-driven (milliseconds passed in), so the maths is testable without a server.
 */
public final class GunState {
  private final VbWeapon w;
  private int mag;
  private long reloadEnd; // 0 = not reloading
  private long nextShot;
  private int burstLeft;
  private long burstStart;
  private long lastShot = Long.MIN_VALUE / 2;
  private long sprayStart = Long.MIN_VALUE / 2;
  private int shotIndex; // index of the shot to fire next in the current spray
  private int lastIndex; // index of the shot just fired
  private double reloadFactor = 1; // abilities: > 1 faster, <= 0 no reload

  public GunState(VbWeapon w) {
    this(w, w.magazine());
  }

  /** A gun picked back up with the rounds it had. */
  public GunState(VbWeapon w, int mag) {
    this.w = w;
    this.mag = Math.max(0, Math.min(w.magazine(), mag));
  }

  public VbWeapon weapon() {
    return w;
  }

  public int magazine() {
    return mag;
  }

  public boolean reloading() {
    return reloadEnd != 0;
  }

  /** Milliseconds since the last shot allowed (a huge number when none was fired). */
  public long sinceShot(long now) {
    return now - lastShot;
  }

  /** Index (0 = first) in the spray of the shot {@link #poll} just allowed. */
  public int lastShotIndex() {
    return lastIndex;
  }

  /** Whether the trigger is spinning the RK-90 Thresher up (first {@code spinUp} seconds of a spray). */
  public boolean spinningUp(long now) {
    return w.spinUp() > 0 && now - sprayStart < w.spinUp() * 1000;
  }

  /** Finishes a reload that is due. */
  public void tick(long now) {
    if (reloadEnd != 0 && now >= reloadEnd) {
      mag = w.magazine();
      reloadEnd = 0;
    }
  }

  /** Reload speed from abilities: 1 normal, 1.5 faster, 100 instant, 0 or less cannot reload. */
  public void setReloadFactor(double f) {
    reloadFactor = f;
  }

  /** Starts a reload; false when full, already reloading or nothing to reload. */
  public boolean startReload(long now) {
    tick(now);
    if (reloadEnd != 0 || mag >= w.magazine() || w.magazine() <= 0 || reloadFactor <= 0) return false;
    reloadEnd = now + Math.round(w.reload() * 1000 / reloadFactor);
    burstLeft = 0;
    return true;
  }

  /** Swapping away cancels a reload in progress (the bullets are not added). */
  public void cancelReload() {
    reloadEnd = 0;
    burstLeft = 0;
  }

  /** Remaining reload time in ms, 0 when not reloading. */
  public long reloadLeft(long now) {
    return reloadEnd == 0 ? 0 : Math.max(0, reloadEnd - now);
  }

  /**
   * One poll of the trigger. {@code click} is a fresh right-click, {@code held} that the button is
   * still down. Returns true when a shot leaves the barrel now (one per call: call again for a
   * burst or when behind). The shot that empties the magazine starts the reload at once (a scoped sniper
   * never sends another trigger poll while empty), and so does a pull on an empty magazine.
   */
  public boolean poll(long now, boolean click, boolean held) {
    boolean fired = trigger(now, click, held);
    if (fired && mag <= 0) startReload(now); // also ends a burst cut short by the empty magazine
    return fired;
  }

  private boolean trigger(long now, boolean click, boolean held) {
    tick(now);
    if (reloadEnd != 0 || w.magazine() <= 0) return false;
    if (burstLeft > 0) {
      if (now < nextShot) return false;
      if (mag <= 0) {
        burstLeft = 0;
        return false;
      }
      burstLeft--;
      shoot(now);
      nextShot = burstLeft > 0 ? nextShot + gapMs() : burstStart + intervalMs(now);
      return true;
    }
    boolean want = click || (held && w.mode() == VbWeapon.Mode.AUTO);
    if (!want || now < nextShot) return false;
    if (mag <= 0) {
      startReload(now);
      return false;
    }
    if (now - lastShot > resetMs()) {
      shotIndex = 0;
      sprayStart = now;
    }
    if (w.mode() == VbWeapon.Mode.BURST && w.burst() > 1) {
      burstStart = now;
      burstLeft = w.burst() - 1;
      shoot(now);
      nextShot = now + gapMs();
      return true;
    }
    long iv = intervalMs(now);
    shoot(now);
    // Stay on the schedule when the poll is a little late so 16 shots/s are really 16 per second.
    nextShot = (now - nextShot < 50 && nextShot > 0) ? nextShot + iv : now + iv;
    return true;
  }

  private void shoot(long now) {
    mag--;
    lastIndex = shotIndex++;
    lastShot = now;
  }

  private long gapMs() {
    return Math.max(1, Math.round(w.burstGap() * 1000));
  }

  private long intervalMs(long now) {
    double iv = w.interval() * 1000;
    if (spinningUp(now)) iv *= 2; // half rate while spinning up
    return Math.max(1, Math.round(iv));
  }

  /** A spray ends (index back to 0) after this long without a shot. */
  private long resetMs() {
    return Math.max(300, Math.round(w.interval() * 1000 * 2));
  }
}
