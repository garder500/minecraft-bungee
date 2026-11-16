package win.vampyuyu.valoblock;

import static org.junit.Assert.*;

import org.junit.Test;

public class HoldTimerTest {
  @Test
  public void sealResetsBeforeCheckpointAndResumesAfterIt() {
    HoldTimer t = new HoldTimer(7, 3.5);
    t.tick(true, 3.0);
    t.tick(false, 0.05);
    assertEquals(0, t.progress(), 1e-9);
    t.tick(true, 3.6);
    assertEquals(3.6, t.progress(), 1e-9);
    t.tick(false, 0.05);
    assertEquals(3.5, t.progress(), 1e-9);
    assertFalse(t.tick(true, 3.4));
    assertTrue(t.tick(true, 0.1));
  }

  @Test
  public void exactlyAtCheckpointIsKept() {
    HoldTimer t = new HoldTimer(7, 3.5);
    t.tick(true, 3.5);
    t.tick(false, 1);
    assertEquals(3.5, t.progress(), 1e-9);
  }

  @Test
  public void noCheckpointMeansReleaseResets() {
    HoldTimer t = new HoldTimer(4, 0);
    t.tick(true, 3.9);
    t.tick(false, 0.05);
    assertEquals(0, t.progress(), 1e-9);
    assertTrue(t.tick(true, 4));
  }

  @Test
  public void rejectsBadTimes() {
    assertThrows(IllegalArgumentException.class, () -> new HoldTimer(7, 7));
    assertThrows(IllegalArgumentException.class, () -> new HoldTimer(0, 0));
  }
}
