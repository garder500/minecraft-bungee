package win.vampyuyu.survival.menu;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import win.vampyuyu.survival.data.ContentDomains;
import win.vampyuyu.survival.data.Db;
import win.vampyuyu.survival.data.Seeds;

/** The shipped quests read in French and German; English is the fallback. */
public class QuestI18nTest {
    private Db db;
    private QuestCatalog catalog;

    @Before
    public void setUp() {
        db = Db.memory();
        Seeds.sync(db, ContentDomains.ALL, null);
        catalog = QuestRepo.load(db);
    }

    @After
    public void tearDown() {
        db.close();
    }

    private List<QuestDef> everyQuest() {
        List<QuestDef> all = new ArrayList<>(catalog.tutorial());
        all.addAll(catalog.dailyPool().values());
        return all;
    }

    @Test
    public void everyQuestAndCategoryIsTranslated() {
        assertTrue(everyQuest().size() > 300);
        for (String lang : List.of("fr", "de")) {
            for (QuestDef def : everyQuest()) {
                assertTrue(lang + " name of " + def.id(), !catalog.name(def, lang).isBlank());
                assertTrue(lang + " description of " + def.id(), !catalog.description(def, lang).isBlank());
            }
            for (QuestCatalog.Category category : catalog.categories()) {
                assertTrue(lang + " name of category " + category.id(), !catalog.name(category, lang).isBlank());
            }
        }
        QuestDef pebbles = catalog.tutorialQuest("q006").orElseThrow();
        assertNotEquals(pebbles.description(), catalog.description(pebbles, "fr"));
        assertNotEquals(pebbles.description(), catalog.description(pebbles, "de"));
        assertNotEquals(catalog.description(pebbles, "fr"), catalog.description(pebbles, "de"));
    }

    @Test
    public void aTextWithoutTranslationReadsEnglish() {
        QuestDef def = catalog.tutorialQuest("q006").orElseThrow();
        assertEquals(def.name(), catalog.name(def, "en"));
        assertEquals(def.description(), catalog.description(def, "en"));
        assertEquals(def.name(), catalog.name(def, "es"));
        QuestCatalog.Category first = catalog.categories().get(0);
        assertEquals(first.name(), catalog.name(first, "en"));
    }

    @Test
    public void aTranslationKeepsTheNumbersAndCommandsOfTheEnglishText() {
        Pattern token = Pattern.compile("/[a-z]+|\\d[\\d,. ]*\\d|\\d");
        for (String lang : List.of("fr", "de")) {
            for (QuestDef def : everyQuest()) {
                assertEquals(lang + " " + def.id(), tokens(token, def.description()), tokens(token, catalog.description(def, lang)));
            }
        }
    }

    private static List<String> tokens(Pattern token, String text) {
        List<String> out = new ArrayList<>();
        Matcher m = token.matcher(text);
        while (m.find()) {
            out.add(m.group().replaceAll("[ ,.]", ""));
        }
        out.sort(null);
        return out;
    }
}
