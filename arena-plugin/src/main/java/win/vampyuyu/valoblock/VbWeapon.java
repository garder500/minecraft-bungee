package win.vampyuyu.valoblock;

/**
 * One weapon, knife or shield from valoblock/weapons.json. Pure data. Damage fields are per pellet
 * (a shotgun's "8x30" is pellets 8, head 30). Shields have only {@code shield} and a price.
 */
public record VbWeapon(
    String id,
    String name,
    int price,
    String kind,
    String metal,
    String metalName,
    String texture,
    Slot slot,
    String baseItem,
    int menuRow,
    int menuCol,
    int head,
    int body,
    int legs,
    int pellets,
    double rate,
    Mode mode,
    int burst,
    double burstGap,
    int magazine,
    double reload,
    double spread,
    double sneakMult,
    double moveMult,
    double sprayGrow,
    boolean firstShotPerfect,
    double falloffStart,
    double falloffEnd,
    double falloffMin,
    double range,
    int penetration,
    double penetrationFactor,
    double spinUp,
    double slowOnHit,
    double zoom,
    double scopedSlow,
    Recoil recoil,
    int shield,
    int backstab,
    double reach,
    double speed,
    String killSound,
    String note) {

  public enum Slot {
    PRIMARY,
    SIDEARM,
    MELEE,
    SHIELD
  }

  public enum Mode {
    SEMI,
    AUTO,
    BURST
  }

  /** Per-shot camera kick in degrees: climbs for {@code climb} shots, then only drifts sideways. */
  public record Recoil(double pitch, double yaw, int climb, double drift) {
    /** {pitch up, yaw right} for the shot with this index in the spray (0 = first). */
    public double[] at(int shot) {
      if (shot < 0) shot = 0;
      return shot < climb ? new double[] {pitch, yaw} : new double[] {pitch * 0.15, drift};
    }
  }

  public boolean isGun() {
    return magazine > 0 && (slot == Slot.PRIMARY || slot == Slot.SIDEARM);
  }

  public boolean isShield() {
    return slot == Slot.SHIELD;
  }

  /** The design's own "free" items cannot be sold back (nothing to refund). */
  public boolean free() {
    return price == 0;
  }

  /** Seconds between two shots (between bursts for a burst weapon). 0 for items that do not shoot. */
  public double interval() {
    return rate <= 0 ? 0 : 1.0 / rate;
  }
}
