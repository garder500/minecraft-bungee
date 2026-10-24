package win.vampyuyu.werewolf.hud;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;
import win.vampyuyu.werewolf.WerewolfGame;

class HudPreviewTest {
  private static final CustomHud HUD = new CustomHud();

  private static int glyphs(net.kyori.adventure.text.Component c) {
    return PlainTextComponentSerializer.plainText().serialize(c).length();
  }

  @Test
  void everyStateIsValidAndRenders() {
    assertFalse(HudPreview.all().isEmpty());
    Set<String> ids = new HashSet<>();
    for (var e : HudPreview.all()) {
      assertTrue(ids.add(e.id()), "duplicate id " + e.id());
      var s = e.state();
      assertNotNull(s.palette());
      assertNotNull(s.label());
      assertTrue(s.total() >= s.alive() && s.alive() >= 0, e.id());
      assertEquals(s.total(), s.seats().size(), e.id());
      assertTrue(s.badges().size() <= 6, e.id());
      for (int mode : new int[] {78, 79, 80, 81}) {
        var out = assertDoesNotThrow(() -> HUD.render(s, mode), e.id());
        // plate, icon, round and label are two characters each: a missing sprite renders nothing
        assertTrue(glyphs(out) >= 8, e.id() + " lost its top plate");
        if (s.showRole()) assertTrue(glyphs(out) >= 8 + 10, e.id() + " lost its right panel");
      }
    }
  }

  @Test
  void everyRoleHasAPanel() {
    List<String> roles = HudPreview.roles();
    assertEquals(WerewolfGame.Role.values().length, roles.size());
    for (var role : WerewolfGame.Role.values()) {
      var e = HudPreview.find(role.name().toLowerCase(Locale.ROOT)).orElseThrow();
      assertEquals("role-" + role.name().toLowerCase(Locale.ROOT), e.id());
      assertTrue(e.state().showRole());
    }
  }

  @Test
  void theNamedStatesAreThere() {
    for (String id :
        List.of("night-seer", "night-cupid", "day-discussion-0", "day-day_vote-1", "day-day_vote-2", "day-away",
            "dead-day", "badge-lover", "badge-life", "badge-death", "badge-infect", "badge-guard", "badge-mayor",
            "badge-silenced", "danger-4", "danger-vote", "seats-4", "seats-24", "day-vote-hidden"))
      assertTrue(HudPreview.find(id).isPresent(), id);
    assertEquals(0, HudPreview.find("day-day_vote-1").orElseThrow().state().against());
    assertEquals(3, HudPreview.find("day-day_vote-2").orElseThrow().state().against());
    assertEquals(-1, HudPreview.find("day-vote-hidden").orElseThrow().state().against());
    assertEquals(4, HudPreview.find("danger-4").orElseThrow().state().seconds());
    assertEquals("dead", HudPreview.find("dead-day").orElseThrow().state().palette());
    assertEquals("away", HudPreview.find("day-away").orElseThrow().state().label());
    assertEquals(List.of("lover"), HudPreview.find("badge-lover").orElseThrow().state().badges());
    assertEquals(HudPreview.SIZE_STATE, HudPreview.find(HudPreview.SIZE_STATE).orElseThrow().id());
  }

  @Test
  void lookupByPhaseRoleAndSeats() {
    assertEquals("role-seer", HudPreview.find("seer").orElseThrow().id());
    assertEquals("night-night", HudPreview.find("night").orElseThrow().id());
    assertEquals("DAY_VOTE", HudPreview.find("vote").orElseThrow().phase());
    assertEquals(18, HudPreview.find("seats-18").orElseThrow().state().seats().size());
    assertTrue(HudPreview.find("seats-99").isEmpty());
    assertTrue(HudPreview.find("nope").isEmpty());
    assertTrue(HudPreview.find("").isEmpty());
  }

  @Test
  void seatStripsOfEveryTableSize() {
    for (int n = 2; n <= 45; n++) {
      var s = HudPreview.find("seats-" + n).orElseThrow().state();
      assertEquals(n, s.seats().size());
      assertEquals(1, s.seats().stream().filter("me"::equals).count());
      assertDoesNotThrow(() -> HUD.render(s, 78));
    }
  }

  @Test
  void liveCountdownTicksDown() {
    var e = HudPreview.find("danger-countdown").orElseThrow();
    assertTrue(e.live());
    assertEquals(9, e.at(0).seconds());
    assertEquals(6, e.at(3).seconds());
    assertEquals(0, e.at(99).seconds());
    assertSame(HudPreview.find("danger-4").orElseThrow().state(), HudPreview.find("danger-4").orElseThrow().at(5));
  }

  @Test
  void listFilters() {
    assertEquals(HudPreview.ids(), HudPreview.list(""));
    assertTrue(HudPreview.list("badge").stream().allMatch(id -> id.contains("badge")));
    assertTrue(HudPreview.list("badge").size() >= 7);
    assertTrue(HudPreview.list("zzzz").isEmpty());
  }

  @Test
  void nightPhases() {
    assertTrue(HudPreview.isNight("SEER"));
    assertTrue(HudPreview.isNight("night"));
    assertFalse(HudPreview.isNight("DAY_VOTE"));
    assertFalse(HudPreview.isNight("vote"));
    assertFalse(HudPreview.isNight("lobby"));
  }
}
