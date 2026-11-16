package win.vampyuyu.valoblock;

import java.util.ArrayList;
import java.util.List;

/**
 * Round history of a match and everything derived from it: score, sides, pistol rounds, overtime.
 * Squads are 0 and 1; squad 0 starts as Seedbearers.
 *
 * <p>Sequence: rounds 1-6 as started, sides swap, rounds 7-12; first to {@code winScore} wins. At
 * 6-6 overtime: round 13 with the starting sides, round 14 swapped, a squad winning both takes the
 * match; at 1-1 round 15 (sudden death, starting sides again) decides.
 */
public final class VbScore {
  private final int winScore, swapAfter;
  private final List<Integer> winners = new ArrayList<>();

  public VbScore(VbRules rules) {
    this.winScore = rules.winScore();
    this.swapAfter = rules.swapAfter();
  }

  public void record(int squad) {
    if (squad != 0 && squad != 1) throw new IllegalArgumentException("squad");
    if (matchWinner() >= 0) throw new IllegalStateException("Match already decided");
    winners.add(squad);
  }

  public int roundsPlayed() {
    return winners.size();
  }

  /** The round about to be played (1-based). */
  public int nextRound() {
    return winners.size() + 1;
  }

  public int wins(int squad) {
    int n = 0;
    for (int w : winners) if (w == squad) n++;
    return n;
  }

  public boolean overtime(int round) {
    return round > swapAfter * 2;
  }

  public boolean suddenDeath(int round) {
    return round > swapAfter * 2 + 2;
  }

  /** Whether the squads' sides are swapped in this round. */
  public boolean swapped(int round) {
    if (round <= swapAfter) return false;
    if (round <= swapAfter * 2) return true;
    return (round - swapAfter * 2 - 1) % 2 == 1;
  }

  public VbSide sideOf(int squad, int round) {
    boolean first = (squad == 0) != swapped(round);
    return first ? VbSide.SEEDBEARERS : VbSide.SEALERS;
  }

  /** The squad playing the given side in this round. */
  public int squadOf(VbSide side, int round) {
    return sideOf(0, round) == side ? 0 : 1;
  }

  /** First round of a half: everyone restarts with the pistol credits. */
  public boolean pistolRound(int round) {
    return round == 1 || round == swapAfter + 1;
  }

  /** The squad that won the match, or -1 while it is undecided. */
  public int matchWinner() {
    int a = wins(0), b = wins(1), n = winners.size();
    if (n <= swapAfter * 2) {
      if (a >= winScore) return 0;
      if (b >= winScore) return 1;
      return -1;
    }
    // Overtime: nothing is decided before round 14 is played.
    if (n < swapAfter * 2 + 2) return -1;
    if (a == b) return -1;
    return a > b ? 0 : 1;
  }

  public List<Integer> history() {
    return List.copyOf(winners);
  }
}
