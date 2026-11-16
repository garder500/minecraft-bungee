package win.vampyuyu.valoblock;

/** The two sides of a round. Squads (the two fixed teams of a match) swap sides after round 6. */
public enum VbSide {
  SEEDBEARERS("Seedbearers"),
  SEALERS("Sealers");

  private final String label;

  VbSide(String label) {
    this.label = label;
  }

  public String label() {
    return label;
  }

  public VbSide other() {
    return this == SEEDBEARERS ? SEALERS : SEEDBEARERS;
  }
}
