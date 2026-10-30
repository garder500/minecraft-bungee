package win.vampyuyu.survival.economy;

import static org.junit.Assert.*;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import win.vampyuyu.survival.SurvivalPlugin;
import win.vampyuyu.survival.api.EconomyApi;
import win.vampyuyu.survival.core.Gui;

/** The registered /bz and /ah commands, run through the real feature (not a hand built service). */
public class MarketCommandsTest {
    private ServerMock server;
    private SurvivalPlugin plugin;
    private EconomyService eco;
    private PlayerMock alice;

    @Before
    public void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(SurvivalPlugin.class);
        win.vampyuyu.survival.LegacyFeatureFixture.enable(plugin);
        eco = (EconomyService) plugin.core().service(EconomyApi.class).orElseThrow();
        alice = server.addPlayer("Alice");
    }

    @After
    public void tearDown() {
        win.vampyuyu.survival.LegacyFeatureFixture.disable(plugin);
        MockBukkit.unmock();
    }

    private boolean menuOpen() {
        return alice.getOpenInventory().getTopInventory().getHolder() instanceof Gui;
    }

    @Test
    public void commandsAreRegisteredWithAliasesAndPermissions() {
        for (String name : new String[] {"bazaar", "bz", "auctionhouse", "ah", "auction"}) {
            assertNotNull(name, server.getCommandMap().getCommand(name));
        }
        assertNotNull(plugin.getServer().getPluginManager().getPermission("vampyuyu.sb.economy.bazaar"));
        assertNotNull(plugin.getServer().getPluginManager().getPermission("vampyuyu.sb.economy.auction"));
    }

    @Test
    public void bareCommandsOpenTheMenus() {
        alice.performCommand("bz");
        assertTrue(menuOpen());
        alice.closeInventory();
        alice.performCommand("ah");
        assertTrue(menuOpen());
        alice.closeInventory();
        alice.performCommand("ah history");
        assertTrue(menuOpen());
    }

    @Test
    public void ahSellListsTheHeldItemAndBadInputIsRefusedWithoutLoss() {
        eco.addCents(alice.getUniqueId(), 100_000, "test");
        alice.getInventory().setItemInMainHand(new ItemStack(Material.DIAMOND_PICKAXE));
        alice.performCommand("ah sell notaprice");
        assertEquals(Material.DIAMOND_PICKAXE, alice.getInventory().getItemInMainHand().getType());
        alice.performCommand("ah sell 500 bin 12");
        assertTrue(alice.getInventory().getItemInMainHand().getType().isAir());
        assertEquals(100_000 - 2_500, eco.balanceCents(alice.getUniqueId()));
    }

    @Test
    public void bzOrderRejectsPricesOffTheTickGrid() {
        eco.addCents(alice.getUniqueId(), 100_000, "test");
        alice.performCommand("bz order buy wheat 10 1.05");
        assertEquals(100_000, eco.balanceCents(alice.getUniqueId()));
        alice.performCommand("bz order buy wheat 10 1.1");
        assertEquals(100_000 - 1_100, eco.balanceCents(alice.getUniqueId()));
    }
}
