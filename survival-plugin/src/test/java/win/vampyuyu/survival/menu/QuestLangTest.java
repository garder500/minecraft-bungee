package win.vampyuyu.survival.menu;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import win.vampyuyu.survival.api.EconomyApi;
import win.vampyuyu.survival.api.IslandApi;
import win.vampyuyu.survival.core.Lang;

/** Quest names and descriptions are shown in the player's language, English when there is no translation. */
public class QuestLangTest extends MenuTestBase {
    private PlayerMock player;
    private QuestService quests;
    private QuestsGui gui;
    private SidebarService sidebar;

    @Before
    public void setUp() {
        core.replace(EconomyApi.class, new FakeEconomy());
        core.replace(IslandApi.class, new FakeIslands());
        quests = new QuestService(rt);
        gui = new QuestsGui(rt, quests);
        sidebar = new SidebarService(rt, quests);
        player = playerIn("sb_hub");
        Lang.publish(Map.of("fr", Map.of("quest.create_island.name", "Une île", "quest.create_island.desc", "Crée ton île.")));
    }

    @After
    public void clearLang() {
        Lang.clear();
    }

    private QuestDef first() {
        return rt.engine().catalog().tutorialQuest("create_island").orElseThrow();
    }

    private String iconName(PlayerMock p) {
        var meta = gui.icon(p, rt.quests(p.getUniqueId()), first(), false).getItemMeta();
        return PlainTextComponentSerializer.plainText().serialize(meta.displayName());
    }

    private String iconLore(PlayerMock p) {
        var meta = gui.icon(p, rt.quests(p.getUniqueId()), first(), false).getItemMeta();
        return meta.lore().stream().map(l -> PlainTextComponentSerializer.plainText().serialize(l))
            .reduce("", (a, b) -> a + "\n" + b);
    }

    @Test
    public void englishWithoutTranslationOrLocale() {
        assertTrue(iconName(player), iconName(player).contains("A Place to Call Home"));
        assertTrue(iconLore(player), iconLore(player).contains("Create your island."));
    }

    @Test
    public void frenchPlayerSeesTheTranslation() {
        player.setLocale(Locale.FRENCH);
        assertTrue(iconName(player), iconName(player).contains("Une île"));
        assertTrue(iconLore(player), iconLore(player).contains("Crée ton île."));
        assertEquals("Une île", Lang.tr(player, "quest.create_island.name", "A Place to Call Home"));
    }

    @Test
    public void sidebarTracksTheTranslatedName() {
        rt.prefs(player.getUniqueId()).questTracker = true;
        rt.quests(player.getUniqueId()).tracked = "create_island";
        List<String> english = sidebar.build(player);
        assertTrue(english.toString(), english.toString().contains("A Place to Call Home"));
        player.setLocale(Locale.FRENCH);
        List<String> french = sidebar.build(player);
        assertTrue(french.toString(), french.toString().contains("Une île"));
    }
}
