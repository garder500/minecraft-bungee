package win.vampyuyu.survival.boss;

import static org.junit.Assert.*;

import java.util.List;
import org.bukkit.Bukkit;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import win.vampyuyu.survival.SurvivalPlugin;
import win.vampyuyu.survival.api.BossApi;

/** The feature must really boot (a feature whose enable() throws is silently skipped by the core). */
public class BossFeatureBootTest {
    private ServerMock server;
    private SurvivalPlugin plugin;

    @Before
    public void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(SurvivalPlugin.class);
        win.vampyuyu.survival.LegacyFeatureFixture.enable(plugin);
    }

    @After
    public void tearDown() {
        win.vampyuyu.survival.LegacyFeatureFixture.disable(plugin);
        MockBukkit.unmock();
    }

    @Test
    public void bossFeatureEnabledAndPublishesTheApi() {
        assertTrue(win.vampyuyu.survival.LegacyFeatureFixture.features(plugin).enabledIds().contains("boss"));
        assertFalse(plugin.features().failedIds().contains("boss"));
        BossApi api = plugin.core().service(BossApi.class).orElseThrow();
        assertTrue(api.bossIds().containsAll(List.of("voidwyrm", "magma_warden", "frost_revenant",
            "sylvan_matriarch", "abyssal_leviathan", "crypt_lord")));
    }

    @Test
    public void summonItemsAreRegistered() {
        for (String id : List.of("void_sigil", "magma_core", "frost_heart", "matriarch_egg", "leviathan_scale",
                "wing_core")) {
            assertTrue(id, plugin.core().items().def(id).isPresent());
        }
    }

    @Test
    public void commandsAreRegistered() {
        for (String name : List.of("boss", "slayer", "worldboss")) {
            assertNotNull(name, Bukkit.getCommandMap().getCommand(name));
        }
    }

    @Test
    public void unknownBossIsRefused() {
        BossApi api = plugin.core().service(BossApi.class).orElseThrow();
        assertFalse(api.spawn("nope", server.addSimpleWorld("sb_world").getSpawnLocation(), null));
        assertFalse(api.spawn(null, null, null));
    }

    @Test
    public void disableUnregistersEverything() {
        win.vampyuyu.survival.LegacyFeatureFixture.disable(plugin);
        server.getPluginManager().disablePlugin(plugin);
        assertNull(Bukkit.getCommandMap().getKnownCommands().get("slayer"));
    }
}
