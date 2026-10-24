package win.vampyuyu.werewolf;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import org.junit.jupiter.api.Test;
import win.vampyuyu.werewolf.ambience.AmbienceProfile;
import win.vampyuyu.werewolf.hud.HudPreview;

class WwDebugCommandTest {
  @Test
  void movedCommandsKeepTheirOldSpelling() {
    for (String sub : List.of("practice", "force", "act", "click", "peek", "phase")) {
      String hint = WwDebugCommand.deprecation(sub);
      assertNotNull(hint, sub);
      assertTrue(hint.contains("/ww debug " + sub), hint);
      assertEquals(hint, WwDebugCommand.deprecation(sub.toUpperCase(Locale.ROOT)));
    }
    for (String sub : List.of("status", "start", "stop", "cast", "hud", "vote", "debug", "help", ""))
      assertNull(WwDebugCommand.deprecation(sub), sub);
    assertNull(WwDebugCommand.deprecation(null));
    assertArrayEquals(new String[] {"debug", "phase", "skip"}, WwDebugCommand.forwarded(new String[] {"phase", "skip"}));
  }

  @Test
  void everyMovedCommandIsASubcommand() {
    assertTrue(WwDebugCommand.SUBS.containsAll(WwDebugCommand.MOVED));
  }

  @Test
  void subcommandCompletion() {
    assertEquals(WwDebugCommand.SUBS, WwDebugCommand.candidates(new String[] {"debug", ""}));
    assertTrue(WwDebugCommand.SUBS.containsAll(List.of("hud", "ambience", "sky", "help")));
  }

  @Test
  void hudCompletion() {
    var first = WwDebugCommand.candidates(new String[] {"debug", "hud", ""});
    assertTrue(first.containsAll(List.of("list", "cycle", "stop", "size")));
    assertTrue(first.containsAll(HudPreview.ids()));
    assertTrue(first.contains("seer"));
    assertTrue(first.containsAll(HudPreview.roles()));
    assertTrue(first.containsAll(HudPreview.phases()));
    assertEquals(WwDebugCommand.SIZES, WwDebugCommand.candidates(new String[] {"debug", "hud", "size", ""}));
    assertEquals(List.of("2", "4", "8"), WwDebugCommand.candidates(new String[] {"debug", "hud", "cycle", ""}));
  }

  @Test
  void ambienceAndSkyCompletion() {
    var phases = WwDebugCommand.candidates(new String[] {"debug", "ambience", ""});
    assertTrue(phases.containsAll(List.of("lobby", "day", "night", "vote", "trial", "finale", "discussion", "seer")));
    assertTrue(WwDebugCommand.candidates(new String[] {"debug", "sky", ""}).contains("12"));
    assertEquals(List.of("skip"), WwDebugCommand.candidates(new String[] {"debug", "phase", ""}));
  }

  @Test
  void everyAmbiencePhaseResolves() {
    for (String phase : WwDebugCommand.ambiencePhases()) {
      var entry = AmbienceProfile.defaults().forPhase(phase, HudPreview.isNight(phase));
      assertNotNull(entry, phase);
      assertNotNull(entry.sting().sound());
    }
  }

  @Test
  void skySummaryPrintsLayoutNumbers() {
    String text = WwDebugCommand.skySummary(12);
    assertTrue(text.contains("12 players"), text);
    assertTrue(text.contains("ring radius"), text);
    assertThrows(IllegalArgumentException.class, () -> WwDebugCommand.skySummary(2));
    assertThrows(IllegalArgumentException.class, () -> WwDebugCommand.skySummary(46));
  }
}
