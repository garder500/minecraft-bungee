package win.vampyuyu.valoblock;

import static org.junit.Assert.*;

import org.junit.Test;

public class PositionHistoryTest {
  private PositionHistory walking() {
    PositionHistory h = new PositionHistory();
    for (int i = 0; i <= 10; i++) h.record(1000 + i * 50L, i, 0, 0, false); // 1 block per tick, 50 ms ticks
    return h;
  }

  @Test
  public void noRewindReturnsTheLatest() {
    assertEquals(10, walking().at(1500, 0).x(), 1e-9);
  }

  @Test
  public void rewindsAndInterpolates() {
    PositionHistory h = walking();
    assertEquals(8, h.at(1500, 100).x(), 1e-9); // two ticks back
    assertEquals(8.5, h.at(1500, 75).x(), 1e-9); // between samples
  }

  @Test
  public void rewindIsCappedAt200Ms() {
    PositionHistory h = walking();
    assertEquals(6, h.at(1500, 5000).x(), 1e-9); // 200 ms = 4 ticks, not more
    assertEquals(PositionHistory.MAX_REWIND_MS, 200);
  }

  @Test
  public void oldSamplesFallOutOfTheRing() {
    PositionHistory h = new PositionHistory(4);
    for (int i = 0; i < 10; i++) h.record(i * 50L, i, 0, 0, false);
    assertEquals(6, h.at(450, 200).x(), 1e-9); // 250 ms back is gone; 200 ms is kept
    assertEquals(6, h.at(450, 9999).x(), 1e-9);
  }

  @Test
  public void emptyHistoryAnswersNull() {
    assertNull(new PositionHistory().at(10, 0));
  }

  @Test
  public void timeNeverGoesBackwards() {
    PositionHistory h = new PositionHistory();
    h.record(100, 1, 0, 0, false);
    h.record(50, 9, 0, 0, false); // ignored
    assertEquals(1, h.at(100, 0).x(), 0);
  }
}
