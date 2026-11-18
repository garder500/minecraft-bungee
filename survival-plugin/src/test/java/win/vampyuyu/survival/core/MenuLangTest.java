package win.vampyuyu.survival.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.Set;
import java.util.TreeSet;
import org.junit.Test;

/** Fixed menu text: both language files carry the same keys and placeholders; long text is wrapped for tooltips. */
public class MenuLangTest {
    private static final Pattern ARG = Pattern.compile("\\{\\d+}");

    @Test
    public void frenchAndGermanHaveTheSameKeysAndPlaceholders() {
        Set<String> fr = MenuLang.keys("fr");
        assertFalse(fr.isEmpty());
        assertEquals(fr, MenuLang.keys("de"));
        for (String key : fr) {
            assertEquals(key, args(MenuLang.tr("fr", key, "")), args(MenuLang.tr("de", key, "")));
        }
    }

    @Test
    public void aMissingLanguageOrKeyReadsEnglishWithItsArguments() {
        assertEquals("step 3 of the main path", MenuLang.tr("en", "quests.step", "step {0} of the main path", 3));
        assertEquals("étape 3 du chemin principal", MenuLang.tr("fr", "quests.step", "step {0} of the main path", 3));
        assertEquals("Fallback", MenuLang.tr("fr", "no.such.key", "Fallback"));
        assertEquals("Fallback", MenuLang.tr(null, "quests.step", "Fallback"));
    }

    @Test
    public void wrapKeepsEveryLineShortAndEveryWord() {
        String text = "Des dunes pour le sable et le gravier, du givre pour la glace et le Frostium. D'autres survivants"
            + " répondent à l'Appel : le marché ouvre.";
        List<String> lines = Guis.wrap("<gray>", text, Guis.LINE_WIDTH);
        assertTrue(lines.size() > 1);
        StringBuilder joined = new StringBuilder();
        for (String line : lines) {
            assertTrue(line, line.startsWith("<gray>"));
            assertTrue(line, line.length() - "<gray>".length() <= Guis.LINE_WIDTH + 2); // + a glued " :"
            joined.append(joined.length() == 0 ? "" : " ").append(line.substring("<gray>".length()));
        }
        assertEquals(text, joined.toString());
        for (String line : lines) {
            assertFalse("a French colon starts a line: " + line, line.startsWith("<gray>:"));
        }
        assertEquals(List.of("<gray>short"), Guis.wrap("<gray>", "short", Guis.LINE_WIDTH));
    }

    private static Set<String> args(String text) {
        Set<String> out = new TreeSet<>();
        Matcher m = ARG.matcher(text);
        while (m.find()) {
            out.add(m.group());
        }
        return out;
    }
}
