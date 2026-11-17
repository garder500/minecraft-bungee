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
        assertEquals(List.of("utility", "island", "wings", "economy", "quarry", "classes", "trades", "gadgets", "boss", "resonance", "dungeon", "bastion", "realm", "ashes", "storm", "marsh", "depths", "promotion", "npc", "runes", "stations", "astral", "paragon", "breath", "hellgao", "menu", "movement", "showcase"), plugin.features().enabledIds());
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
    public void sbshowcaseIsRegistered() {
        assertNotNull(Bukkit.getCommandMap().getCommand("sbshowcase"));
    }

    @Test
    public void showcaseLayoutHasOneFramePerRegisteredItem() {
        var layout = win.vampyuyu.survival.showcase.ShowcaseFeature.layoutOf(plugin.core());
        assertEquals(plugin.core().items().ids().size(), layout.frames().size());
        assertEquals(plugin.core().items().ids().size(),
            layout.frames().stream().map(f -> f.id()).distinct().count());
        assertTrue(layout.blocks().size() >= 14);
        System.out.println("SHOWCASE items=" + layout.frames().size() + " blocks=" + layout.blocks().size()
            + " spins=" + layout.spins().size() + " sections=" + layout.sections().size() + " length=" + layout.length());
        layout.sections().forEach(sec -> System.out.println("SHOWCASE section " + sec));
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

    @Test
    public void aSaveDuringTheShutdownStillRuns() {
        server.getPluginManager().disablePlugin(plugin);
        boolean[] saved = {false};
        plugin.core().scheduler().async(() -> saved[0] = true);
        assertTrue(saved[0]);
    }
}
