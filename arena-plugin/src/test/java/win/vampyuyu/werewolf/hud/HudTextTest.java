package win.vampyuyu.werewolf.hud;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import org.junit.jupiter.api.Test;
import win.vampyuyu.werewolf.WerewolfGame;
import win.vampyuyu.werewolf.roles.RoleInfo;

/** The HUD's words: language files, the layout of a line, the glyphs it becomes. */
class HudTextTest {
  private static final CustomHud HUD = new CustomHud();

  /** Every glyph of a rendered HUD as {green - 100 (the element), blue (the parameter)}, in order. */
  private static List<int[]> glyphs(Component c) {
    List<int[]> out = new ArrayList<>();
    if (c instanceof TextComponent t && !t.content().isEmpty() && t.color() != null)
      out.add(new int[] {t.color().green() - 100, t.color().blue()});
    for (Component child : c.children()) out.addAll(glyphs(child));
    return out;
  }

  private static CustomHud.State state(String label, String roleWord, String teamWord, int against) {
    return new CustomHud.State(
        "vote", "vote", 2, label, 87, 1, true, true, "seer", roleWord, teamWord, "seer", 10, 12,
        List.of("alive", "me"), List.of(), against);
  }

  @Test
  void theThreeLanguagesHaveTheSameKeys() {
    assertFalse(HudLang.keys("en_US").isEmpty());
    for (String tag : HudLang.TAGS) assertEquals(HudLang.keys("en_US"), HudLang.keys(tag), tag);
  }

  @Test
  void everyWordOfTheGameHasAKey() {
    Set<String> keys = HudLang.keys("en_US");
    for (var role : WerewolfGame.Role.values()) {
      RoleInfo info = RoleInfo.of(role.name());
      assertTrue(keys.contains("hud.role." + CustomHud.word(info.word())), role + " word");
      assertTrue(keys.contains("hud.team." + CustomHud.word(info.teamWord())), role + " team word");
      if (info.call() != null)
        assertTrue(keys.contains("hud.phase." + CustomHud.word(info.call().label())), role + " call label");
    }
    // the words CustomHud.of writes itself
    for (String label :
        List.of("game over", "sleeping", "last shot", "vote", "election", "defense", "trial", "debate", "cupid awake",
            "guard awake", "seer awake", "wolves hunt", "witch awake", "amulet", "waiting", "away"))
      assertTrue(keys.contains("hud.phase." + CustomHud.word(label)), label);
    assertTrue(keys.containsAll(List.of("hud.role.fallen", "hud.team.out", "hud.team.wolf_side", "hud.votes.line1", "hud.votes.line2")));
  }

  @Test
  void translationFallsBackToEnglishThenToTheWord() {
    assertEquals("VOYANTE", HudLang.tr("fr_FR", "hud.role.seer", "x"));
    assertEquals("SEHERIN", HudLang.tr("de_DE", "hud.role.seer", "x"));
    assertEquals("SEER", HudLang.tr("xx_XX", "hud.role.seer", "x"));
    assertEquals("NEW ROLE", HudLang.tr("fr_FR", "hud.role.new_role", "NEW ROLE"));
  }

  @Test
  void theLanguageOfAPlayer() {
    assertEquals("fr_FR", HudLang.tag("fr_FR", Locale.GERMANY));
    assertEquals("de_DE", HudLang.tag(null, Locale.GERMANY));
    assertEquals("fr_FR", HudLang.tag("", Locale.CANADA_FRENCH));
    assertEquals("en_US", HudLang.tag("garbage", new Locale("es", "ES")));
    assertEquals("en_US", HudLang.tag(null, null));
  }

  @Test
  void aLanguageFileIsOneQuotedTextPerLine() {
    assertEquals(Map.of("a.b", "X Y"), HudLang.parse("t", "# note\n\na.b: \"X Y\"\n"));
    assertThrows(IllegalStateException.class, () -> HudLang.parse("t", "a.b: X\n"));
    assertThrows(IllegalStateException.class, () -> HudLang.parse("t", "a:\n  b: \"X\"\n"));
  }

  @Test
  void textIsNormalisedToWhatTheFontsHave() {
    assertEquals("SORCIÈRE, À TOI", HUD.normalise("Sorcière,  à toi "));
    assertEquals("STRASSE", HUD.normalise("Straße"));
    assertEquals("D'OURS", HUD.normalise("d\u2019ours"));
    assertEquals("COEUR", HUD.normalise("c\u0153ur"));
    assertEquals("NANDU", HUD.normalise("ñandú")); // accents the fonts lack are dropped
    assertEquals("A?B", HUD.normalise("a@b"));
  }

  @Test
  void widthsAndTheCut() {
    // bold: V, O, T and E are 6 px of ink and one of spacing each; a space is 5
    assertEquals(4 * 7 - 1, HUD.width("b", "VOTE"));
    assertEquals(2 * 7 + 5 - 1, HUD.width("b", "A B"));
    assertEquals(0, HUD.width("b", ""));
    assertEquals("VOTE", HUD.fit("b", "VOTE", 27));
    String cut = HUD.fit("b", "VOTE VOTE VOTE", 40);
    assertTrue(cut.endsWith(".") && HUD.width("b", cut) <= 40, cut);
    assertEquals("VOTE.", cut); // "VOTE V." would be 41 px
  }

  @Test
  void everyShippedWordFitsItsLineInEveryLanguage() {
    for (String tag : HudLang.TAGS)
      for (String key : HudLang.keys(tag)) {
        String line = HUD.normalise(HudLang.tr(tag, key, ""));
        assertFalse(line.isEmpty() || line.contains("?"), tag + " " + key);
        String font = key.startsWith("hud.team.") || key.equals("hud.votes.line2") ? "m" : "b";
        int limit = key.startsWith("hud.phase.") ? 106 : key.startsWith("hud.votes.") ? 62 : 84;
        assertTrue(HUD.width(font, line) <= limit, tag + " " + key + " is " + HUD.width(font, line) + " px");
        assertEquals(line, HUD.fit(font, line, limit));
      }
  }

  @Test
  void aWordIsOneGlyphPerLetterAtItsOffset() {
    // "VOTE" in the headline font (V, O, T, E: 6 px of ink + 1): cells at 0, 7, 14, 21 of the label element
    var out = glyphs(HUD.render(state("vote", "SEER", "VILLAGE", -1), 78));
    int label = out.get(3)[0];
    assertEquals(List.of(0, 7, 14, 21), out.subList(3, 7).stream().map(g -> g[1]).toList());
    assertTrue(out.subList(3, 7).stream().allMatch(g -> g[0] == label));
    assertNotEquals(label, out.get(7)[0]); // then the time bar
  }

  @Test
  void theHudIsWrittenInThePlayersLanguage() {
    var s = state("vote", "SEER", "VILLAGE", 3);
    int en = glyphs(HUD.render(s, 78, "en_US")).size();
    int fr = glyphs(HUD.render(s, 78, "fr_FR")).size();
    int de = glyphs(HUD.render(s, 78, "de_DE")).size();
    // SEER -> VOYANTE (+3), VILLAGE stays, AGAINST YOU -> CONTRE TOI (-1 letter)
    assertEquals(en + 3 - 1, fr);
    // VOTE -> ABSTIMMUNG (+6), SEER -> SEHERIN (+3), VILLAGE -> DORF (-3), VOTES -> STIMMEN (+2), AGAINST YOU -> GEGEN DICH (-1)
    assertEquals(en + 6 + 3 - 3 + 2 - 1, de);
    assertEquals(en, glyphs(HUD.render(s, 78)).size());
  }

  @Test
  void aWordWithoutAKeyIsWrittenAsItIs() {
    var known = glyphs(HUD.render(state("vote", "SEER", "VILLAGE", -1), 78, "fr_FR")).size();
    var custom = glyphs(HUD.render(state("vote", "ORACLE", "VILLAGE", -1), 78, "fr_FR")).size();
    assertEquals(known - "VOYANTE".length() + "ORACLE".length(), custom);
  }
}
