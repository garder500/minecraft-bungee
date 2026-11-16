package win.vampyuyu.valoblock;

import static org.junit.Assert.*;

import org.junit.Test;

public class VbScoreTest {
  private VbScore score() {
    return new VbScore(VbData.rules());
  }

  private void win(VbScore s, int squad, int times) {
    for (int i = 0; i < times; i++) s.record(squad);
  }

  @Test
  public void sidesSwapAfterRoundSix() {
    VbScore s = score();
    for (int r = 1; r <= 6; r++) assertEquals(VbSide.SEEDBEARERS, s.sideOf(0, r));
    for (int r = 7; r <= 12; r++) {
      assertEquals(VbSide.SEALERS, s.sideOf(0, r));
      assertEquals(VbSide.SEEDBEARERS, s.sideOf(1, r));
    }
    assertEquals(0, s.squadOf(VbSide.SEEDBEARERS, 3));
    assertEquals(1, s.squadOf(VbSide.SEEDBEARERS, 8));
  }

  @Test
  public void pistolRoundsAreOneAndSeven() {
    VbScore s = score();
    for (int r = 1; r <= 15; r++) assertEquals(r == 1 || r == 7, s.pistolRound(r));
  }

  @Test
  public void firstToSevenWinsBeforeRoundTwelve() {
    VbScore s = score();
    win(s, 0, 6);
    assertEquals(-1, s.matchWinner());
    s.record(0);
    assertEquals(0, s.matchWinner());
    assertThrows(IllegalStateException.class, () -> s.record(1));
  }

  @Test
  public void sevenFiveEndsTheMatchAtRoundTwelve() {
    VbScore s = score();
    for (int i = 0; i < 5; i++) {
      s.record(0);
      s.record(1);
    }
    s.record(1);
    s.record(0); // 6-6 after 12 rounds
    assertEquals(12, s.roundsPlayed());
    assertEquals(-1, s.matchWinner());
    assertTrue(s.overtime(13));
    assertFalse(s.suddenDeath(13));
  }

  private VbScore sixSix() {
    VbScore s = score();
    for (int i = 0; i < 6; i++) {
      s.record(0);
      s.record(1);
    }
    return s;
  }

  @Test
  public void overtimeWonTwoNilEndsAtRoundFourteen() {
    VbScore s = sixSix();
    s.record(1);
    assertEquals(-1, s.matchWinner()); // one round each side must be played
    s.record(1);
    assertEquals(1, s.matchWinner());
  }

  @Test
  public void overtimeSplitGoesToSuddenDeath() {
    VbScore s = sixSix();
    s.record(0);
    s.record(1);
    assertEquals(14, s.roundsPlayed());
    assertEquals(-1, s.matchWinner());
    assertEquals(15, s.nextRound());
    assertTrue(s.suddenDeath(15));
    s.record(0);
    assertEquals(0, s.matchWinner());
  }

  @Test
  public void overtimeSidesAlternate() {
    VbScore s = sixSix();
    assertEquals(VbSide.SEEDBEARERS, s.sideOf(0, 13));
    assertEquals(VbSide.SEALERS, s.sideOf(0, 14));
    assertEquals(VbSide.SEEDBEARERS, s.sideOf(0, 15));
  }
}
