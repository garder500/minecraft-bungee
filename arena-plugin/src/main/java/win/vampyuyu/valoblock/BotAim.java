package win.vampyuyu.valoblock;

/** How wide a bot's shot cone is: the gun's own spread when standing, the level's aim error, and the part of the recoil it does not cancel. Pure. */
public final class BotAim {
  private BotAim() {}

  /** Half-angle in degrees of the cone around the true aim for the {@code shot}th round of a spray. */
  public static double cone(BotConfig.Level level, VbWeapon w, int shot, boolean moving) {
    double gun = GunMath.spread(w, false, moving, false, shot);
    int climbed = Math.min(Math.max(shot, 0), w.recoil().climb());
    double leftover = (1 - level.recoilControl()) * w.recoil().pitch() * climbed;
    return Math.sqrt(gun * gun + level.aimErrorDeg() * level.aimErrorDeg() + leftover * leftover);
  }
}
