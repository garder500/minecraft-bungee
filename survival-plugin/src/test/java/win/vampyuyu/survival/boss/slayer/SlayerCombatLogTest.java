package win.vampyuyu.survival.boss.slayer;

import static org.junit.Assert.*;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;
import win.vampyuyu.survival.SurvivalPlugin;
import win.vampyuyu.survival.boss.BossConfig;
import win.vampyuyu.survival.boss.BossData;
import win.vampyuyu.survival.boss.BossManager;
import win.vampyuyu.survival.boss.BossSession;

/** Quitting, running away or losing the boss after hurting it must cost the quest; only server faults restore it. */
public class SlayerCombatLogTest {
    private ServerMock server;
    private SurvivalPlugin plugin;
    private SlayerManager slayer;
    private BossManager bosses;
    private WorldMock world;
    private PlayerMock player;

    @Before
    public void setUp() throws Exception {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(SurvivalPlugin.class);
        BossConfig config;
        try (var reader = new InputStreamReader(getClass().getResourceAsStream("/boss.yml"), StandardCharsets.UTF_8)) {
            config = BossConfig.load(YamlConfiguration.loadConfiguration(reader));
        }
        bosses = new BossManager(plugin.core(), config);
        slayer = new SlayerManager(plugin.core(), bosses);
        world = server.addSimpleWorld("sb_world");
        world.getChunkAt(world.getSpawnLocation()).load();
        player = server.addPlayer();
        player.teleport(world.getSpawnLocation());
    }

    @After
    public void tearDown() {
        MockBukkit.unmock();
    }

    private BossData data() {
        return plugin.core().profiles().section(player.getUniqueId(), "boss", BossData.class, BossData::new);
    }

    /** A quest in the middle of a fight. */
    private BossSession fight() {
        SlayerDefinition def = slayer.slayer("zombie").orElseThrow();
        BossData d = data();
        d.quest = new BossData.Quest("zombie", 1, 10, 1000, 0);
        d.quest.state = BossData.Quest.READY;
        assertTrue(SlayerQuests.beginFight(d));
        slayer.launch(player.getUniqueId(), def, def.tier(1), player.getLocation());
        return bosses.sessionOfOwner(player.getUniqueId()).orElseThrow();
    }

    private void hurt(BossSession session) {
        session.ledger().record(player.getUniqueId(), 5, session.hp());
    }

    @Test
    public void quittingMidFightLosesTheQuestEvenWithoutDamage() {
        fight();
        bosses.playerQuit(player);
        assertNull("no free retry at full boss health", data().quest);
    }

    @Test
    public void runningAwayAfterDamageLosesTheQuest() {
        BossSession session = fight();
        hurt(session);
        session.end(BossSession.End.DESPAWN, null);
        assertNull(data().quest);
    }

    @Test
    public void losingTheEntityAfterDamageLosesTheQuest() {
        BossSession session = fight();
        hurt(session);
        session.end(BossSession.End.ENTITY_LOST, null);
        assertNull(data().quest);
    }

    @Test
    public void despawnBeforeAnyDamageRestoresTheQuest() {
        fight().end(BossSession.End.DESPAWN, null);
        assertEquals(BossData.Quest.READY, data().quest.state);
    }

    @Test
    public void serverFaultsRestoreTheQuestEvenAfterDamage() {
        for (BossSession.End reason : new BossSession.End[] {BossSession.End.SHUTDOWN, BossSession.End.WORLD_UNLOAD,
                BossSession.End.ERROR}) {
            BossSession session = fight();
            hurt(session);
            session.end(reason, null);
            assertNotNull(reason.name(), data().quest);
            assertEquals(reason.name(), BossData.Quest.READY, data().quest.state);
        }
    }

    @Test
    public void failureRulesTable() {
        assertTrue(SlayerQuests.failsQuest(BossSession.End.OWNER_LEFT, false));
        assertTrue(SlayerQuests.failsQuest(BossSession.End.TIMEOUT, false));
        assertTrue(SlayerQuests.failsQuest(BossSession.End.OWNER_DIED, false));
        assertTrue(SlayerQuests.failsQuest(BossSession.End.DESPAWN, true));
        assertFalse(SlayerQuests.failsQuest(BossSession.End.DESPAWN, false));
        assertFalse(SlayerQuests.failsQuest(BossSession.End.SHUTDOWN, true));
        assertFalse(SlayerQuests.failsQuest(BossSession.End.ADMIN, true));
    }
}
