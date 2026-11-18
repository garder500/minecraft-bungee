package win.vampyuyu.survival.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TranslatableComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.junit.After;
import org.junit.Test;
import win.vampyuyu.survival.data.ContentDomains;
import win.vampyuyu.survival.data.Db;
import win.vampyuyu.survival.data.Seeds;

/** Content text in the player's language: menus by lookup, item stacks by pack keys with the English fallback. */
public class LangTest {
    private static final Logger QUIET = Logger.getAnonymousLogger();

    static {
        QUIET.setLevel(java.util.logging.Level.OFF);
    }

    @After
    public void forget() {
        Lang.clear();
    }

    private static List<TranslatableComponent> translatables(Component c, List<TranslatableComponent> out) {
        if (c instanceof TranslatableComponent t) {
            out.add(t);
        }
        c.children().forEach(child -> translatables(child, out));
        return out;
    }

    @Test
    public void withoutTranslationsTheTextIsThePlainEnglishOfBefore() {
        assertEquals(Component.text("Void Sword"), Lang.name("item.void_sword.name", "Void Sword"));
        assertEquals("Void Sword", Lang.tr("fr", "item.void_sword.name", "Void Sword"));
        assertTrue(translatables(Lang.line("item.x.lore.0", "<gray>Hits <white>hard."), new ArrayList<>()).isEmpty());
    }

    @Test
    public void aMenuReadsTheLanguageOfThePlayerAndEnglishOtherwise() {
        Lang.publish(Map.of("fr", Map.of("item.bronzite.name", "Bronzite brute")));
        assertEquals("Bronzite brute", Lang.tr("fr", "item.bronzite.name", "Bronzite"));
        assertEquals("Bronzite", Lang.tr("de", "item.bronzite.name", "Bronzite"));
        assertEquals("Bronzite", Lang.tr((String) null, "item.bronzite.name", "Bronzite"));
    }

    @Test
    public void aStackNameIsAPackKeyThatEndsWithTheHashOfTheEnglishAndFallsBackToIt() {
        Lang.publish(Map.of("fr", Map.of("item.void_sword.name", "Épée du Vide")));
        TranslatableComponent name = (TranslatableComponent) Lang.name("item.void_sword.name", "Void Sword");
        assertEquals("vy.item.void_sword.name.06625ed6", name.key()); // the value scripts/i18n_seed.py writes in the pack
        assertEquals("Void Sword", name.fallback());
        assertEquals("the fallback is what a plain reader (logs, the auction house) sees", "Void Sword",
            PlainTextComponentSerializer.plainText().serialize(name));
        TranslatableComponent edited = (TranslatableComponent) Lang.name("item.void_sword.name", "Void Blade");
        assertFalse("an edited English name asks for another key: the pack's stale translation is not shown",
            name.key().equals(edited.key()));
    }

    @Test
    public void aStackLineKeepsItsColoursAndTranslatesEveryPieceOfText() {
        Lang.publish(Map.of("de", Map.of("item.x.lore.0", "<gray>Trifft: <white>das Ziel's Herz")));
        Component line = Lang.line("item.x.lore.0", "<gray>Rend: <white>the target's heart");
        List<TranslatableComponent> pieces = translatables(line, new ArrayList<>());
        assertEquals(2, pieces.size());
        String base = Lang.packKey("item.x.lore.0", "<gray>Rend: <white>the target's heart");
        assertEquals(base + ".1", pieces.get(0).key()); // piece 0 is the empty text before <gray>
        assertEquals("Rend: ", pieces.get(0).fallback());
        assertEquals(base + ".2", pieces.get(1).key());
        assertEquals("the target's heart", pieces.get(1).fallback());
        assertEquals("Rend: the target's heart", PlainTextComponentSerializer.plainText().serialize(line));
        assertEquals(NamedTextColor.GRAY, pieces.get(0).color() != null ? pieces.get(0).color() : line.color());
    }

    @Test
    public void theRendererWritesTranslatedNamesAndLines() {
        Lang.publish(Map.of("fr", Map.of("item.t9.name", "Lame", "item.t9.lore.0", "<gray>Tranche.")));
        CustomItemDef def = CustomItemDef.builder("t9", "Blade", Rarity.RARE, Material.IRON_SWORD).lore("<gray>Cuts.").build();
        assertEquals(1, translatables(ItemRenderer.name(def), new ArrayList<>()).size());
        assertEquals(Rarity.RARE.color(), ItemRenderer.name(def).color());
        assertEquals(1, translatables(ItemRenderer.description(def).get(0), new ArrayList<>()).size());
    }

    @Test
    public void theSeedTranslatesEveryItemTextInFrenchAndGerman() {
        try (Db db = Db.memory()) {
            Seeds.sync(db, ContentDomains.ALL, QUIET);
            Lang.load(db);
            Map<String, String> english = Lang.english(db);
            assertTrue(english.size() > 200);
            List<String> missing = new ArrayList<>();
            for (Map.Entry<String, String> row : english.entrySet()) {
                if (row.getValue().isEmpty()) {
                    continue;
                }
                for (String language : List.of("fr", "de")) {
                    String text = Lang.tr(language, row.getKey(), null);
                    if (text == null || text.isBlank()) {
                        missing.add(language + ":" + row.getKey());
                    }
                }
            }
            assertTrue("not translated (run scripts/i18n_extract.py, translate, then scripts/i18n_seed.py): " + missing,
                missing.isEmpty());
            assertEquals("Épée du Vide", Lang.tr("fr", "item.void_sword.name", "Void Sword"));
        }
    }

    @Test
    public void anEnglishRowEditedInThePanelDropsItsStaleTranslation() {
        try (Db db = Db.memory()) {
            Seeds.sync(db, ContentDomains.ALL, QUIET);
            db.write(c -> Db.update(c, "UPDATE item_def SET name = 'Void Blade', origin = 'panel' WHERE id = 'void_sword'"));
            Lang.load(db);
            assertEquals("Void Blade", Lang.tr("fr", "item.void_sword.name", "Void Blade"));
            assertEquals(Component.text("Void Blade"), Lang.name("item.void_sword.name", "Void Blade"));
        }
    }
}
