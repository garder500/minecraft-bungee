package win.vampyuyu.valoblock;

import static org.junit.Assert.assertTrue;

import java.util.List;
import org.junit.Test;

public class CloudPuffsTest {
  @Test
  public void aCloudIsRoundAndNotUnderTheFloor() {
    for (double r : new double[] {3.5, 4, 5}) {
      List<double[]> puffs = AbilityEnv.puffs(r);
      double minX = 1e9, maxX = -1e9, minY = 1e9, maxY = -1e9, minZ = 1e9, maxZ = -1e9;
      for (double[] p : puffs) {
        assertTrue("under the floor", p[1] - p[3] / 2 >= -1e-9);
        minX = Math.min(minX, p[0] - p[3] / 2);
        maxX = Math.max(maxX, p[0] + p[3] / 2);
        minY = Math.min(minY, p[1]);
        maxY = Math.max(maxY, p[1] + p[3] / 2);
        minZ = Math.min(minZ, p[2] - p[3] / 2);
        maxZ = Math.max(maxZ, p[2] + p[3] / 2);
      }
      double w = maxX - minX, d = maxZ - minZ, h = maxY;
      assertTrue("as wide as the radius says, " + w, w >= r * 1.5 && w <= r * 2.6);
      assertTrue("round, not a column: " + w + " x " + h, h >= r * 0.8 && h <= w * 1.4 && d >= r * 1.5);
      assertTrue("a shell of puffs", puffs.size() >= 14 && puffs.size() <= 60);
    }
  }
}
