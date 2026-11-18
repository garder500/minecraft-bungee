package win.vampyuyu.network.i18n;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.util.Locale;
import org.junit.Test;

public class LangTest {
    @Test public void parsesCodesAndTags() {
        assertEquals(Lang.FR, Lang.parse("fr"));
        assertEquals(Lang.FR, Lang.parse("fr_FR"));
        assertEquals(Lang.FR, Lang.parse("fr-CA"));
        assertEquals(Lang.DE, Lang.parse("DE_at"));
        assertEquals(Lang.DE, Lang.parse(" de "));
        assertEquals(Lang.EN, Lang.parse("en_GB"));
    }

    @Test public void rejectsTheRest() {
        assertNull(Lang.parse(null));
        assertNull(Lang.parse(""));
        assertNull(Lang.parse("   "));
        assertNull(Lang.parse("es_ES"));
        assertNull(Lang.parse("french"));
        assertNull(Lang.parse("_fr"));
    }

    @Test public void tagsMatchTheFileNames() {
        assertEquals("en_US", Lang.EN.tag());
        assertEquals("fr_FR", Lang.FR.tag());
        assertEquals("de_DE", Lang.DE.tag());
    }

    @Test public void fromLocale() {
        assertEquals(Lang.DE, Lang.of(Locale.GERMANY));
        assertEquals(Lang.FR, Lang.of(Locale.CANADA_FRENCH));
        assertNull(Lang.of(Locale.JAPAN));
        assertNull(Lang.of((Locale) null));
    }

    @Test public void chosenBeatsClientBeatsDefault() {
        assertEquals(Lang.DE, LangChoice.resolve("de_DE", Locale.FRANCE));
        assertEquals(Lang.FR, LangChoice.resolve(null, Locale.FRANCE));
        assertEquals(Lang.FR, LangChoice.resolve("garbage", Locale.FRANCE));
        assertEquals(Lang.EN, LangChoice.resolve(null, Locale.JAPAN));
        assertEquals(Lang.EN, LangChoice.resolve(null, null));
    }
}
