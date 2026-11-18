package win.vampyuyu.survival.core;

import static org.junit.Assert.assertEquals;

import java.util.Locale;
import org.junit.Test;

public class PlayerLangTest {
    @Test
    public void theChoiceBeatsTheClientBeatsEnglish() {
        assertEquals("de", PlayerLang.code("de_DE", Locale.FRANCE));
        assertEquals("fr", PlayerLang.code(null, Locale.FRANCE));
        assertEquals("fr", PlayerLang.code("garbage", Locale.CANADA_FRENCH));
        assertEquals("de", PlayerLang.code("", Locale.GERMANY));
        assertEquals("en", PlayerLang.code(null, Locale.JAPAN));
        assertEquals("en", PlayerLang.code(null, null));
        assertEquals("en", PlayerLang.code("en_US", Locale.FRANCE));
    }
}
