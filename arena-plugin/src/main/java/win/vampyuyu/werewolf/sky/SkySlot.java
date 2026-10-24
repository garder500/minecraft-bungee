package win.vampyuyu.werewolf.sky;

/** One platform slot of the sky world. Slot i is centred at x = i * SPACING, z = 0, platform Y = 200. */
public record SkySlot(int index) {
  /** Distance between two slot centres; the biggest platform is about 40 blocks wide. */
  public static final int SPACING = 256;

  public static final int PLATFORM_Y = 200;

  /** Highest slot count, keeps x far inside the world border. */
  public static final int MAX_SLOTS = 1024;

  public SkySlot {
    if (index < 0 || index >= MAX_SLOTS) throw new IllegalArgumentException("No such sky slot: " + index);
  }

  public int centreX() {
    return index * SPACING;
  }

  public int centreZ() {
    return 0;
  }
}
