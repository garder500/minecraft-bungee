package win.vampyuyu.survival;

import static org.junit.Assert.*;

import java.util.List;
import org.bukkit.Bukkit;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import win.vampyuyu.survival.core.Feature;

/** Boots the whole plugin from plugin.yml: every stub feature loads, /back and /sbgive exist. */
public class SurvivalPluginTest {
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
    public void everyFeatureEnablesInOrder() {
        assertEquals(List.of("utility", "island", "wings", "menu"), plugin.features().enabledIds());
        assertTrue(plugin.features().failedIds().isEmpty());
        assertEquals(plugin.features().enabledIds(),
            SurvivalPlugin.featureList().stream().map(Feature::id).toList());
    }

    @Test
    public void backAndSbgiveAreRegistered() {
        assertNotNull(plugin.getCommand("back").getExecutor());
        assertNotNull(Bukkit.getCommandMap().getCommand("sbgive"));
    }

    @Test
    public void sbgiveGivesACustomItem() {
        plugin.core().items().register(win.vampyuyu.survival.core.CustomItemDef.builder("give_test", "Give Test",
            win.vampyuyu.survival.core.Rarity.RARE, org.bukkit.Material.STICK).build());
        var admin = server.addPlayer();
        admin.setOp(true);
        admin.performCommand("sbgive give_test 3");
        assertEquals(3, admin.getInventory().getItem(0).getAmount());
        assertTrue(plugin.core().items().is(admin.getInventory().getItem(0), "give_test"));
    }

    @Test
    public void disablingCleansUp() {
        assertNotNull(Bukkit.getCommandMap().getKnownCommands().get("sbgive"));
        server.getPluginManager().disablePlugin(plugin);
        assertNull(Bukkit.getCommandMap().getKnownCommands().get("sbgive"));
    }
}
