package win.vampyuyu.survival.boss.slayer;

import static org.junit.Assert.*;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import win.vampyuyu.survival.boss.BossConfig;
import win.vampyuyu.survival.boss.BossData;

/** The slayer quest state machine and its exploit guards, on the shipped zombie slayer. */
public class SlayerQuestsTest {
    private SlayerDefinition zombie;

    @Before
    public void setUp() throws Exception {
        MockBukkit.mock();
        try (var reader = new java.io.InputStreamReader(getClass().getResourceAsStream("/boss.yml"),
                java.nio.charset.StandardCharsets.UTF_8)) {
            BossConfig config = BossConfig.load(YamlConfiguration.loadConfiguration(reader));
            zombie = config.slayers().get("zombie");
        }
        assertNotNull(zombie);
    }

    @After
    public void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    public void startRulesRefuseWithEnglishReasons() {
        BossData data = new BossData();
        assertTrue(SlayerQuests.denial(data, zombie, 0, 1e9).isPresent());
        assertTrue(SlayerQuests.denial(data, zombie, 6, 1e9).isPresent());
        assertTrue(SlayerQuests.denial(data, zombie, 1, 10).get().contains("coins"));
        assertTrue("tier III needs level 2", SlayerQuests.denial(data, zombie, 3, 1e9).get().contains("level"));
        assertTrue(SlayerQuests.denial(data, zombie, 1, zombie.tier(1).cost()).isEmpty());
        data.slayerXp.put("zombie", 20L); // level 2
        assertTrue(SlayerQuests.denial(data, zombie, 3, 1e9).isEmpty());
    }

    @Test
    public void onlyOneQuestAtATime() {
        BossData data = new BossData();
        SlayerQuests.begin(data, zombie, 1, 1000);
        assertTrue(SlayerQuests.denial(data, zombie, 1, 1e9).get().contains("already"));
    }

    @Test
    public void progressStatesAdvanceInOrder() {
        BossData data = new BossData();
        SlayerQuests.begin(data, zombie, 1, 1);
        assertFalse("not ready: cannot fight", SlayerQuests.beginFight(data));
        double required = data.quest.required;
        int kills = 0;
        while (!SlayerQuests.addKill(data, zombie, EntityType.ZOMBIE)) {
            kills++;
            assertTrue("quest must complete", kills < 10_000);
        }
        assertEquals(BossData.Quest.READY, data.quest.state);
        assertEquals(required, data.quest.progress, 1e-9);
        assertFalse("progress stops at ready", SlayerQuests.addKill(data, zombie, EntityType.ZOMBIE));
        assertTrue(SlayerQuests.beginFight(data));
        assertFalse("cannot start a second fight", SlayerQuests.beginFight(data));
        assertEquals(BossData.Quest.FIGHTING, data.quest.state);
    }

    @Test
    public void nonTargetsAndOtherSlayersGiveNothing() {
        BossData data = new BossData();
        SlayerQuests.begin(data, zombie, 1, 1);
        assertFalse(SlayerQuests.addKill(data, zombie, EntityType.COW));
        assertEquals(0, data.quest.progress, 0);
        data.quest.slayer = "spider";
        assertFalse(SlayerQuests.addKill(data, zombie, EntityType.ZOMBIE));
        assertEquals(0, data.quest.progress, 0);
        assertFalse(SlayerQuests.addKill(new BossData(), zombie, EntityType.ZOMBIE));
    }

    @Test
    public void restartRecoversAFightToReadyButKeepsOtherStates() {
        BossData data = new BossData();
        SlayerQuests.begin(data, zombie, 1, 1);
        SlayerQuests.recover(data);
        assertEquals(BossData.Quest.COLLECTING, data.quest.state);
        data.quest.state = BossData.Quest.FIGHTING;
        SlayerQuests.recover(data);
        assertEquals(BossData.Quest.READY, data.quest.state);
        SlayerQuests.recover(new BossData()); // no quest: no crash
    }

    @Test
    public void winPaysXpOnceClearsTheQuestAndLevelsUp() {
        BossData data = new BossData();
        SlayerQuests.begin(data, zombie, 1, 1);
        SlayerQuests.Win win = SlayerQuests.win(data, zombie, 1);
        assertNull(data.quest);
        assertEquals(zombie.tier(1).slayerXp(), data.slayerXp.get("zombie").longValue());
        assertEquals(1, (int) data.slayerKills.get("zombie:1"));
        assertEquals(0, win.levelBefore());
        assertTrue(win.levelAfter() >= 1);
        assertTrue(win.leveledUp());
    }

    @Test
    public void failureLosesTheQuestAndGivesNoXp() {
        BossData data = new BossData();
        SlayerQuests.begin(data, zombie, 2, 1);
        SlayerQuests.fail(data);
        assertNull(data.quest);
        assertTrue(data.slayerXp.isEmpty());
    }
}
