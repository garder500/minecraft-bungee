package win.vampyuyu.survival.economy;

import static org.junit.Assert.*;

import java.io.InputStreamReader;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import win.vampyuyu.survival.SurvivalPlugin;
import win.vampyuyu.survival.core.SkyblockWorlds;

/** The market rules of the game loop: no ultra rare at the merchants or the Bazaar, nothing bound anywhere. */
public class MarketRulesTest {
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

    private YamlConfiguration shippedConfig() {
        return YamlConfiguration.loadConfiguration(new InputStreamReader(getClass().getResourceAsStream("/economy.yml")));
    }

    @Test
    public void merchantsNeverTradeUltraRareItems() {
        YamlConfiguration config = shippedConfig();
        ShopCatalog catalog = ShopCatalog.parse(config, plugin.getLogger());
        assertFalse("there are merchants", catalog.shops().isEmpty());
        var never = MarketRules.neverTraded(config);
        for (String forbidden : List.of("DIAMOND", "DIAMOND_BLOCK", "DIAMOND_SWORD", "ANCIENT_DEBRIS", "NETHERITE_INGOT",
                "NETHER_STAR", "TOTEM_OF_UNDYING", "ELYTRA", "BEACON", "sunstone")) {
            assertTrue(forbidden, never.test(forbidden));
            assertFalse(forbidden + " must not be in a shop", catalog.keys().contains(forbidden));
        }
        for (String key : catalog.keys()) {
            assertFalse(key, never.test(key));
        }
        assertTrue("everyday items are still sold", catalog.keys().contains("COAL"));
        assertTrue(catalog.keys().contains("IRON_INGOT"));
    }

    @Test
    public void theBazaarNeverListsUltraRareItems() {
        YamlConfiguration config = shippedConfig();
        var never = MarketRules.neverTraded(config);
        var categories = config.getConfigurationSection("bazaar.categories");
        for (String category : categories.getKeys(false)) {
            for (String key : categories.getStringList(category)) {
                if (never.test(key)) {
                    // present in the file, but BazaarService.reload skips it: check the rule, not the file
                    assertTrue(key, never.test(key));
                }
            }
        }
        assertTrue("the rule is not empty", never.test("NETHER_STAR"));
        assertFalse(never.test("COAL"));
        assertFalse(never.test("iron_nugget"));
    }

    @Test
    public void aBoundItemHasNoKeyAndIsNeverClean() {
        ItemStack bound = new ItemStack(Material.IRON_SWORD);
        var meta = bound.getItemMeta();
        meta.getPersistentDataContainer().set(MarketRules.UNTRADABLE, PersistentDataType.LONG, 1L);
        bound.setItemMeta(meta);
        assertTrue(MarketRules.bound(bound));
        assertFalse(MarketRules.bound(new ItemStack(Material.IRON_SWORD)));
        ItemKeys keys = new ItemKeys(plugin.core());
        assertNull(keys.keyOf(bound));
        assertFalse(keys.clean(bound));
        assertEquals("IRON_SWORD", keys.keyOf(new ItemStack(Material.IRON_SWORD)));
    }

    @Test
    public void marketCommandsOnlyWorkInSkyblock() {
        PlayerMock player = server.addPlayer("Trader");
        assertTrue(plugin.core().modes().enter(player, new org.bukkit.Location(server.addSimpleWorld("world"), 0, 70, 0)));
        assertEquals("world", player.getWorld().getName());
        for (String label : List.of("/ah browse", "/market", "/bal", "/bz", "/vampyuyusurvival:pay x 5", "/bank")) {
            var event = new PlayerCommandPreprocessEvent(player, label);
            server.getPluginManager().callEvent(event);
            assertTrue(label, event.isCancelled());
        }
        var free = new PlayerCommandPreprocessEvent(player, "/spawn");
        server.getPluginManager().callEvent(free);
        assertFalse("other commands are not touched", free.isCancelled());
        assertTrue(plugin.core().modes().enter(player, new org.bukkit.Location(Bukkit.getWorld(SkyblockWorlds.WORLD), 0, 70, 0)));
        var inside = new PlayerCommandPreprocessEvent(player, "/ah browse");
        server.getPluginManager().callEvent(inside);
        assertFalse(inside.isCancelled());
        assertNotNull(server.getCommandMap().getCommand("market"));
    }
}
