package win.vampyuyu.survival.economy;

import static org.junit.Assert.*;

import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
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

/**
 * Opens every economy menu with realistic data. A bad icon (a wrong argument, an invalid item model key, a null
 * lore) throws while the menu is built, which this catches; real rendering still needs the in-game check.
 */
public class MenuSmokeTest {
    private ServerMock server;
    private SurvivalPlugin plugin;
    private EconomyService eco;
    private ItemKeys keys;
    private PlayerMock alice;
    private PlayerMock bob;

    @Before
    public void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(SurvivalPlugin.class);
        eco = (EconomyService) plugin.core().service(EconomyApi.class).orElseThrow();
        keys = new ItemKeys(plugin.core());
        alice = server.addPlayer("Alice");
        bob = server.addPlayer("Bob");
        eco.addCents(alice.getUniqueId(), 100_000_000, "test");
        eco.addCents(bob.getUniqueId(), 100_000_000, "test");
    }

    @After
    public void tearDown() {
        MockBukkit.unmock();
    }

    private void assertMenu(String what) {
        assertTrue(what, alice.getOpenInventory().getTopInventory().getHolder() instanceof Gui);
        alice.closeInventory();
    }

    @Test
    public void bankMenus() {
        YamlConfiguration cfg = plugin.core().featureConfig("economy");
        Bank bank = new Bank(plugin.core(), eco, cfg);
        BankUi ui = new BankUi(plugin.core(), eco, bank);
        ui.open(alice);
        assertMenu("bank");
        bank.deposit(alice.getUniqueId(), 500_000);
        bank.withdraw(alice.getUniqueId(), 100_000);
        ui.history(alice);
        assertMenu("bank history");
    }

    @Test
    public void shopMenus() {
        ShopService shops = new ShopService(plugin.core(), eco, keys, new PricePressure());
        shops.reload(plugin.core().featureConfig("economy"));
        ShopUi ui = new ShopUi(plugin.core(), eco, keys, shops);
        ui.openHub(alice);
        assertMenu("shop hub");
        for (var shop : shops.catalog().shops().values()) {
            ui.open(alice, shop);
            assertMenu("shop " + shop.id());
        }
        alice.getInventory().addItem(new ItemStack(Material.WHEAT, 20));
        var shop = shops.catalog().shops().values().iterator().next();
        ui.sellEverything(alice, shop);
        ui.openBuyback(alice, shop);
        assertMenu("buyback");
    }

    @Test
    public void bazaarMenus() {
        BazaarService bz = new BazaarService(plugin.core(), eco, keys, new DurableFile.Inline());
        bz.enable(plugin.core().featureConfig("economy"));
        BazaarUi ui = new BazaarUi(plugin.core(), eco, keys, bz);
        bob.getInventory().addItem(new ItemStack(Material.WHEAT, 64));
        assertTrue(bz.placeSell(bob, "WHEAT", 20, 30).ok());
        assertTrue(bz.placeBuy(alice, "WHEAT", 15, 10).ok());
        assertTrue(bz.instantBuy(alice, "WHEAT", 5).ok());
        ui.openMain(alice);
        assertMenu("bazaar main");
        for (BazaarService.Category c : BazaarService.Category.values()) {
            ui.openList(alice, "<gold>" + c.label, bz.products(c), c);
            assertMenu("bazaar " + c);
        }
        ui.openProduct(alice, "WHEAT", 3, BazaarService.Category.FARMING);
        assertMenu("bazaar product");
        ui.openProduct(alice, "COBBLESTONE", 1, null);
        assertMenu("bazaar empty product");
        ui.openOrders(alice);
        assertMenu("bazaar orders");
        ui.openOrders(bob);
    }

    @Test
    public void auctionMenus() {
        AuctionService ah = new AuctionService(plugin.core(), eco, keys, new DurableFile.Inline());
        ah.enable(plugin.core().featureConfig("economy"));
        AuctionUi ui = new AuctionUi(plugin.core(), keys, ah);
        ItemStack sword = new ItemStack(Material.DIAMOND_SWORD);
        sword.addUnsafeEnchantment(Enchantment.SHARPNESS, 5);
        bob.getInventory().setItemInMainHand(sword);
        assertTrue(ah.list(bob, 100_000, false, 12).ok());
        bob.getInventory().setItemInMainHand(new ItemStack(Material.STONE, 16));
        assertTrue(ah.list(bob, 50_000, true, 6).ok());
        assertTrue(ah.bid(alice, 1, 100_000).ok());
        ui.openMain(alice);
        assertMenu("auction main");
        ui.openBrowse(alice, null, null, AuctionService.Sort.ENDING);
        assertMenu("browse all");
        for (AuctionService.Sort sort : AuctionService.Sort.values()) {
            ui.openBrowse(alice, AuctionService.Category.WEAPONS, null, sort);
            assertMenu("browse " + sort);
        }
        ui.openBrowse(alice, null, "sword", AuctionService.Sort.CHEAPEST);
        assertMenu("search");
        ui.openDetail(alice, 1, () -> { });
        assertMenu("detail auction");
        ui.openDetail(alice, 2, () -> { });
        assertMenu("detail bin");
        ui.openMine(alice);
        assertMenu("mine");
        ui.openMine(bob);
        ui.openHistory(alice);
        assertMenu("history");
        alice.getInventory().setItemInMainHand(new ItemStack(Material.IRON_PICKAXE));
        ui.openCreate(alice, null);
        assertMenu("create");
    }
}
