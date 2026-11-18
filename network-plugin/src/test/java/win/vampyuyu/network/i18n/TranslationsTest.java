package win.vampyuyu.network.i18n;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.Test;

public class TranslationsTest {
    private static Translations sample() {
        Translations t = new Translations();
        t.put(Lang.EN, Map.of("a", "A en", "b", "B en <x>"));
        t.put(Lang.FR, Map.of("a", "A fr", "extra", "oops"));
        return t;
    }

    @Test public void ownTextThenEnglishThenKey() {
        Translations t = sample();
        assertEquals("A fr", t.raw(Lang.FR, "a"));
        assertEquals("B en <x>", t.raw(Lang.FR, "b"));
        assertEquals("B en <x>", t.raw(Lang.DE, "b"));
        assertEquals("nope", t.raw(Lang.FR, "nope"));
        assertNull(t.find(Lang.FR, "nope"));
    }

    @Test public void reportsGaps() {
        Translations t = sample();
        assertEquals(List.of("b"), t.missing(Lang.FR));
        assertEquals(List.of("a", "b"), t.missing(Lang.DE));
        assertTrue(t.missing(Lang.EN).isEmpty());
        assertEquals(List.of("extra"), t.unknown(Lang.FR));
    }

    @Test public void fillsPlaceholders() {
        assertEquals("hi Bob, 3", Translations.fill("hi <who>, <n>", "who", "Bob", "n", "3"));
        assertEquals("<unset>", Translations.fill("<unset>"));
    }

    @Test(expected = IllegalArgumentException.class) public void oddPairsRefused() {
        Translations.fill("x", "only");
    }
}
