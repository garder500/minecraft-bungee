package win.vampyuyu.valoblock;

import static org.junit.Assert.*;

import java.util.*;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.Test;

public class VbHudTest {
  private static String plain(net.kyori.adventure.text.Component c) {
    return PlainTextComponentSerializer.plainText().serialize(c);
  }

  private VbMatch match() {
    List<UUID> humans = new ArrayList<>();
    for (int i = 0; i < 10; i++) humans.add(UUID.randomUUID());
    return new VbMatch(VbData.rules(), humans, new Random(3));
  }

  @Test
  public void clockIsMinutesAndSeconds() {
    assertEquals("1:12", VbHudService.clock(72));
    assertEquals("0:05", VbHudService.clock(5));
  }

  @Test
  public void topLineShowsPipsScoreClockAndRound() {
    VbMatch m = match();
    m.advance();
    m.advance(); // combat, round 1
    String line = plain(VbHudService.topLine(m, 0, VbPhase.COMBAT, 72));
    assertTrue(line, line.contains("■■■■■"));
    assertTrue(line, line.contains("1:12"));
    assertTrue(line, line.contains("ROUND 1"));
    assertTrue(plain(VbHudService.topLine(m, 1, VbPhase.PLANTED, 30)).contains("SEED PLANTED"));
    assertTrue(plain(VbHudService.topLine(m, 0, VbPhase.BUY, 20)).contains("BUY PHASE"));
  }

  @Test
  public void aDeadPlayerTurnsGreyInThePips() {
    VbMatch m = match();
    m.advance();
    m.advance();
    UUID victim = m.squad(0).get(0);
    var killer = m.squad(1).get(0);
    m.damage(victim, killer, 1000);
    var comp = VbHudService.topLine(m, 0, VbPhase.COMBAT, 50);
    String json = net.kyori.adventure.text.serializer.json.JSONComponentSerializer.json().serialize(comp);
    assertTrue("one grey pip for the dead player", json.contains("dark_gray"));
  }

  @Test
  public void aClearedMatchIsNeverDrawnAgain() {
    VbHudService hud = new VbHudService(null);
    VbMatch m = match();
    assertFalse(hud.isCleared(m));
    hud.markCleared(m);
    assertTrue(hud.isCleared(m));
    hud.render(m, 0, 0); // returns at the guard: nothing is queued (there is no server here, so any queueing would throw)
  }
}
