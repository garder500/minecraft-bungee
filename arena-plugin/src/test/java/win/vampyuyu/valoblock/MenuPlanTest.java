package win.vampyuyu.valoblock;

import static org.junit.jupiter.api.Assertions.*;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import win.vampyuyu.valoblock.MenuPlan.Menu;
import win.vampyuyu.valoblock.MenuPlan.View;

class MenuPlanTest {
  private final UUID p = UUID.randomUUID();
  private final MenuPlan plan = new MenuPlan();

  private static View v(VbPhase ph, int round, boolean locked, boolean alive, boolean pack, Menu open, boolean busy) {
    return new View(ph, round, locked, alive, pack, open, busy, 0);
  }

  private static View sel(boolean locked, boolean pack, Menu open, boolean busy) {
    return v(VbPhase.AGENT_SELECT, 0, locked, true, pack, open, busy);
  }

  private static View buy(int round, boolean alive, Menu open) {
    return v(VbPhase.BUY, round, true, alive, true, open, false);
  }

  @Test
  void selectOpensForWhoHasNotLockedIn() {
    assertEquals(Menu.SELECT, plan.decide(p, sel(false, true, Menu.NONE, false)));
    assertEquals(Menu.NONE, plan.decide(p, sel(true, true, Menu.NONE, false)));
  }

  @Test
  void selectCannotBeDismissed() {
    plan.opened(p, sel(false, true, Menu.NONE, false));
    assertEquals(Menu.SELECT, plan.decide(p, sel(false, true, Menu.NONE, false)), "closed: back on the next look");
    assertEquals(Menu.SELECT, plan.decide(p, sel(false, true, Menu.NONE, false)));
    assertEquals(Menu.NONE, plan.decide(p, sel(false, true, Menu.SELECT, false)), "already open");
  }

  @Test
  void selectWaitsForThePackAndForOtherWindows() {
    assertEquals(Menu.NONE, plan.decide(p, sel(false, false, Menu.NONE, false)));
    assertEquals(Menu.NONE, plan.decide(p, sel(false, true, Menu.NONE, true)), "another plugin's menu: no loop");
    assertEquals(Menu.NONE, plan.decide(p, sel(false, true, Menu.ARMORY, false)), "never over another ValoBlock menu");
    assertEquals(Menu.SELECT, plan.decide(p, sel(false, true, Menu.NONE, false)));
  }

  @Test
  void selectClosesOnLockInAndWhenThePhaseEnds() {
    assertTrue(MenuPlan.mustClose(sel(true, true, Menu.SELECT, false)));
    assertTrue(MenuPlan.mustClose(buy(1, true, Menu.SELECT)));
    assertFalse(MenuPlan.mustClose(sel(false, true, Menu.SELECT, false)));
  }

  @Test
  void rejoinDuringSelectOpensAgain() {
    plan.opened(p, sel(false, true, Menu.NONE, false));
    plan.forget(p);
    assertEquals(Menu.SELECT, plan.decide(p, sel(false, true, Menu.NONE, false)));
  }

  @Test
  void armoryOpensAtEveryBuyPhaseForTheLiving() {
    assertEquals(Menu.ARMORY, plan.decide(p, buy(1, true, Menu.NONE)));
    plan.opened(p, buy(1, true, Menu.NONE));
    assertEquals(Menu.ARMORY, plan.decide(p, buy(2, true, Menu.NONE)), "next round");
    assertEquals(Menu.NONE, plan.decide(p, buy(3, false, Menu.NONE)), "dead");
  }

  @Test
  void armoryStaysClosedAfterAVoluntaryClose() {
    plan.opened(p, buy(1, true, Menu.NONE));
    assertEquals(Menu.NONE, plan.decide(p, buy(1, true, Menu.NONE)));
    plan.forget(p); // rejoin
    assertEquals(Menu.ARMORY, plan.decide(p, buy(1, true, Menu.NONE)));
  }

  @Test
  void armoryOpenedByHandCountsAsOffered() {
    assertEquals(Menu.NONE, plan.decide(p, buy(1, true, Menu.ARMORY)));
    assertEquals(Menu.NONE, plan.decide(p, buy(1, true, Menu.NONE)));
  }

  @Test
  void armoryNeverInCombatAndClosesThere() {
    for (VbPhase ph : new VbPhase[] {VbPhase.COMBAT, VbPhase.PLANTED, VbPhase.ROUND_END, VbPhase.END})
      assertEquals(Menu.NONE, plan.decide(p, v(ph, 1, true, true, true, Menu.NONE, false)));
    assertTrue(MenuPlan.mustClose(v(VbPhase.COMBAT, 1, true, true, true, Menu.ARMORY, false)));
    assertFalse(MenuPlan.mustClose(buy(1, true, Menu.ARMORY)));
  }

  @Test
  void armoryWaitsForThePackThenOpensOnce() {
    View noPack = v(VbPhase.BUY, 1, true, true, false, Menu.NONE, false);
    assertEquals(Menu.NONE, plan.decide(p, noPack));
    assertEquals(Menu.ARMORY, plan.decide(p, buy(1, true, Menu.NONE)));
  }

  @Test
  void doubleTapWithinHalfASecond() {
    DoubleTap t = new DoubleTap();
    assertFalse(t.press(p, 1000));
    assertTrue(t.press(p, 1400));
    assertFalse(t.press(p, 1500), "a third press starts over");
    assertFalse(t.press(p, 2100));
    assertTrue(t.press(p, 2600));
  }

  private static View lobby(int ballot, boolean pack, Menu open, boolean busy) {
    return new View(VbPhase.QUEUE, 0, false, true, pack, open, busy, ballot);
  }

  @Test
  void voteOpensForAHumanInAWaitingLobbyOncePerBallot() {
    assertEquals(Menu.NONE, plan.decide(p, lobby(0, true, Menu.NONE, false)), "no ballot open");
    assertEquals(Menu.VOTE, plan.decide(p, lobby(1, true, Menu.NONE, false)));
    plan.opened(p, lobby(1, true, Menu.NONE, false));
    assertEquals(Menu.NONE, plan.decide(p, lobby(1, true, Menu.NONE, false)), "closed by hand: it stays closed (/vb vote reopens it)");
    assertEquals(Menu.VOTE, plan.decide(p, lobby(2, true, Menu.NONE, false)), "the next match has a new ballot");
  }

  @Test
  void voteWaitsForThePackAndNeverOpensOverAnotherWindow() {
    assertEquals(Menu.NONE, plan.decide(p, lobby(1, false, Menu.NONE, false)), "pack not loaded yet");
    assertEquals(Menu.NONE, plan.decide(p, lobby(1, true, Menu.NONE, true)), "a foreign window is open");
    assertEquals(Menu.NONE, plan.decide(p, lobby(1, true, Menu.ARMORY, false)), "never over another ValoBlock menu");
    assertEquals(Menu.NONE, plan.decide(p, lobby(1, true, Menu.SELECT, false)));
    assertEquals(Menu.VOTE, plan.decide(p, lobby(1, true, Menu.NONE, false)));
  }

  @Test
  void voteOpenedByHandCountsAsOfferedAndRejoinOffersItAgain() {
    assertEquals(Menu.NONE, plan.decide(p, lobby(1, true, Menu.VOTE, false)));
    assertEquals(Menu.NONE, plan.decide(p, lobby(1, true, Menu.NONE, false)));
    plan.forget(p);
    assertEquals(Menu.VOTE, plan.decide(p, lobby(1, true, Menu.NONE, false)));
  }

  @Test
  void voteClosesWhenTheBallotClosesAndWhenTheMatchStarts() {
    assertFalse(MenuPlan.mustClose(lobby(1, true, Menu.VOTE, false)));
    assertTrue(MenuPlan.mustClose(lobby(0, true, Menu.VOTE, false)), "the ballot closed");
    assertTrue(MenuPlan.mustClose(sel(false, true, Menu.VOTE, false)), "the match began: the agent select takes over");
    assertEquals(Menu.SELECT, plan.decide(p, sel(false, true, Menu.NONE, false)));
  }
}
