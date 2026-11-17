package win.vampyuyu.valoblock;

import static org.junit.Assert.assertEquals;

import java.util.UUID;
import org.junit.Test;
import win.vampyuyu.valoblock.PressGate.Verdict;

public class PressGateTest {
  private final UUID a = UUID.randomUUID();
  private final UUID b = UUID.randomUUID();

  @Test
  public void aHeldButtonIsOnePress() {
    PressGate g = new PressGate();
    assertEquals(Verdict.PRESS, g.accept(a, 0));
    assertEquals(Verdict.REPEAT, g.accept(a, 200));
    assertEquals(Verdict.REPEAT, g.accept(a, 400)); // the 350 ms gap alone would have let this one through
    assertEquals(Verdict.REPEAT, g.accept(a, 600));
    assertEquals(Verdict.PRESS, g.accept(a, 1000)); // released, then a fresh click
  }

  @Test
  public void twoPacketsOfOneClickAreOnePress() {
    PressGate g = new PressGate();
    assertEquals(Verdict.PRESS, g.accept(a, 5000));
    assertEquals(Verdict.REPEAT, g.accept(a, 5000));
  }

  @Test
  public void aSecondClickRightAfterIsTooSoonButNotARepeat() {
    PressGate g = new PressGate();
    assertEquals(Verdict.PRESS, g.accept(a, 0));
    assertEquals(Verdict.TOO_SOON, g.accept(a, 300)); // a gap above the repeat window, below the press gap
    assertEquals(Verdict.PRESS, g.accept(a, 700));
  }

  @Test
  public void playersAreIndependentAndForgetResets() {
    PressGate g = new PressGate();
    assertEquals(Verdict.PRESS, g.accept(a, 0));
    assertEquals(Verdict.PRESS, g.accept(b, 10));
    g.forget(a);
    assertEquals(Verdict.PRESS, g.accept(a, 20));
  }
}
