package win.vampyuyu.survival.menu;

import static org.junit.Assert.*;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import win.vampyuyu.survival.SurvivalPlugin;

/** The guide commands and the quests that can really be completed in the Skyblock as it is now. */
public class GuideTest {
    private ServerMock server;
    private SurvivalPlugin plugin;

    @Before
    public void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(SurvivalPlugin.class);
    }

    @After
    public void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    public void theGuideCommandsExist() {
        for (String name : new String[] {"guide", "quests", "track", "hud"}) {
            assertNotNull(name, server.getCommandMap().getCommand(name));
        }
    }

    @Test
    public void deadQuestsAreDroppedWithoutWarnings() {
        var catalog = QuestRepo.load(plugin.core().data());
        assertTrue(catalog.problems().toString(), catalog.problems().isEmpty());
        assertFalse(catalog.tutorial().isEmpty());
        for (String dead : new String[] {"mining_3", "combat_3", "first_soul", "visit_hub", "craft_minion", "first_boss"}) {
            assertTrue(dead + " cannot be completed", catalog.tutorialQuest(dead).isEmpty());
        }
    }

    @Test
    public void everyForgeQuestSaysWhichCommand() {
        var catalog = QuestRepo.load(plugin.core().data());
        int forge = 0;
        for (QuestDef def : catalog.tutorial()) {
            if (def.type() == QuestType.FORGE_ITEM) {
                forge++;
                assertTrue(def.id() + " names /forge", GuideCommands.hint(def).contains("/forge"));
            }
        }
        assertTrue(forge > 10);
    }

    @Test
    public void aPlayerGetsTheTutorialAndTheGuideReadsIt() {
        PlayerMock player = server.addPlayer("Newbie");
        player.performCommand("guide");
        player.performCommand("hud off");
        player.performCommand("hud on");
        player.performCommand("track q031"); // not unlocked yet: refused, never an exception
        assertNotNull(server.getCommandMap().getCommand("guide"));
    }
}
