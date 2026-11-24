package win.vampyuyu.arena;

import static org.junit.Assert.*;

import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.Test;
import win.vampyuyu.arena.ArenaLang.Lang;

/** The language files load without a server, and French and German match English key for key. */
public class ArenaLangTest {
  private static TreeSet<String> placeholders(String text) {
    TreeSet<String> out = new TreeSet<>();
    Matcher m = Pattern.compile("\\{[^}]*}").matcher(text);
    while (m.find()) out.add(m.group());
    return out;
  }

  @Test
  public void everyLanguageHasEveryKeyWithTheSamePlaceholders() {
    Map<String, String> en = ArenaLang.texts(Lang.EN);
    assertTrue("the English file is read from the jar", en.size() > 100);
    for (Lang lang : Lang.values()) {
      Map<String, String> other = ArenaLang.texts(lang);
      assertEquals(lang + " keys", en.keySet(), other.keySet());
      for (var e : en.entrySet()) {
        assertEquals(
            lang + " " + e.getKey(), placeholders(e.getValue()), placeholders(other.get(e.getKey())));
        assertFalse(lang + " " + e.getKey() + " is empty", other.get(e.getKey()).isBlank());
      }
    }
  }

  @Test
  public void aTextFallsBackToEnglishThenToItsKey() {
    assertEquals("Annuler", ArenaLang.tr(Lang.FR, "menu.confirm.cancel"));
    assertEquals("Abbrechen", ArenaLang.tr(Lang.DE, "menu.confirm.cancel"));
    assertEquals("no.such.key", ArenaLang.tr(Lang.FR, "no.such.key"));
  }

  @Test
  public void placeholdersAreFilledOnceAndValuesAreNotExpandedAgain() {
    assertEquals(
        "Confirm: Delete {action}", ArenaLang.tr(Lang.EN, "menu.confirm.confirm", "action", "Delete {action}"));
    assertEquals(
        "Clic : voter contre $1\\", ArenaLang.tr(Lang.FR, "werewolf.menu.vote.click", "player", "$1\\"));
    assertEquals(
        "an unknown placeholder stays visible",
        "Confirm: {action}",
        ArenaLang.tr(Lang.EN, "menu.confirm.confirm", "other", "x"));
  }

  @Test
  public void thePlayersChoiceWinsOverTheClientThenEnglish() {
    assertEquals(Lang.FR, ArenaLang.resolve("fr_FR", Locale.GERMANY));
    assertEquals(Lang.DE, ArenaLang.resolve(null, Locale.of("de", "AT")));
    assertEquals(Lang.DE, ArenaLang.resolve("garbage", Locale.GERMANY));
    assertEquals(Lang.EN, ArenaLang.resolve(null, Locale.of("es", "ES")));
    assertEquals(Lang.EN, ArenaLang.resolve(null, null));
  }

  /** The title is drawn from GUI x=44 on a plate that ends at x=168: about 20 wide characters. */
  @Test
  public void everyWerewolfMenuTitleFitsItsPlate() {
    for (Lang lang : Lang.values())
      for (var e : ArenaLang.texts(lang).entrySet())
        if (e.getKey().startsWith("werewolf.menu.title."))
          assertTrue(lang + " " + e.getKey() + " is too long", e.getValue().length() <= 20);
  }
}
