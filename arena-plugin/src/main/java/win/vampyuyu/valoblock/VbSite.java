package win.vampyuyu.valoblock;

/** A box where the Rift Seed can be planted. */
public record VbSite(String id, String name, double[] min, double[] max) {
  public VbSite {
    if (min.length != 3 || max.length != 3) throw new IllegalArgumentException("Site " + id + ": min and max need 3 numbers");
    double[] a = min.clone(), b = max.clone();
    for (int i = 0; i < 3; i++) {
      min[i] = Math.min(a[i], b[i]);
      max[i] = Math.max(a[i], b[i]);
    }
  }

  public boolean contains(double x, double y, double z) {
    return x >= min[0] && x <= max[0] && y >= min[1] && y <= max[1] + 1 && z >= min[2] && z <= max[2];
  }
}
