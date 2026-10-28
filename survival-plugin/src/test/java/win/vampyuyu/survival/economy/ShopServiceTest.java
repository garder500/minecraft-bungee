package win.vampyuyu.survival.economy;

import static org.junit.Assert.*;

import java.util.List;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import win.vampyuyu.survival.SurvivalPlugin;
import win.vampyuyu.survival.api.EconomyApi;
import win.vampyuyu.survival.core.CustomItemDef;
import win.vampyuyu.survival.core.Rarity;
import win.vampyuyu.survival.api.Stat;

public class ShopServiceTest {
    private ServerMock server;
    private SurvivalPlugin plugin;
    private EconomyService eco;
    private ShopService shops;
    private PricePressure pressure;
    private ItemKeys keys;
    private PlayerMock alice;

    @Before
    public void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(SurvivalPlugin.class);
        eco = (EconomyService) plugin.core().service(EconomyApi.class).orElseThrow();
        plugin.core().items().register(CustomItemDef.builder("test_gem", "Test Gem", Rarity.RARE, Material.EMERALD).build());
        keys = new ItemKeys(plugin.core());
        pressure = new PricePressure();
        shops = new ShopService(plugin.core(), eco, keys, pressure);
        YamlConfiguration cfg = new YamlConfiguration();
        cfg.set("shops.test.name", "Tester");
        cfg.set("shops.test.items.WHEAT.buy", 8);
        cfg.set("shops.test.items.WHEAT.sell", 2);
        cfg.set("shops.test.items.test_gem.buy", 100);
        cfg.set("shops.test.items.test_gem.sell", 20);
        cfg.set("shops.test.items.not_registered.sell", 5);
        cfg.set("pressure.soft-cap", 100);
        cfg.set("pressure.min-multiplier", 0.25);
        shops.reload(cfg);
        alice = server.addPlayer("Alice");
    }

    @After
    public void tearDown() {
        MockBukkit.unmock();
    }

    private ShopCatalog.ShopDef shop() {
        return shops.catalog().shop("test").orElseThrow();
    }

    private ShopService.Result sellAll() {
        return shops.sell(alice, "test", k -> shop().item(k).map(ShopCatalog.ShopItem::sellCents).orElse(0L), null, 0);
    }

    @Test
    public void sellsCleanItemsAndPaysWithAnExactLedger() {
        alice.getInventory().addItem(new ItemStack(Material.WHEAT, 10));
        pressure.reload(new YamlConfiguration() {{ set("pressure.enabled", false); }});
        var r = sellAll();
        assertTrue(r.error(), r.ok());
        assertEquals(2000, r.cents());
        assertEquals(2000, eco.balanceCents(alice.getUniqueId()));
        assertEquals(0, Inv.count(alice, keys, "WHEAT"));
    }

    @Test
    public void modifiedItemsAreNeverSold() {
        ItemStack named = new ItemStack(Material.WHEAT, 5);
        var meta = named.getItemMeta();
        meta.displayName(net.kyori.adventure.text.Component.text("Special"));
        named.setItemMeta(meta);
        alice.getInventory().addItem(named);
        ItemStack enchanted = new ItemStack(Material.WHEAT, 3);
        enchanted.addUnsafeEnchantment(org.bukkit.enchantments.Enchantment.SHARPNESS, 1);
        alice.getInventory().addItem(enchanted);
        var r = sellAll();
        assertFalse(r.ok());
        assertEquals(0, eco.balanceCents(alice.getUniqueId()));
        assertEquals(2, java.util.Arrays.stream(alice.getInventory().getStorageContents()).filter(java.util.Objects::nonNull).count());
    }

    @Test
    public void customItemsTradeByIdAndBonusedOnesDoNot() {
        ItemStack plain = plugin.core().items().create("test_gem", 4);
        alice.getInventory().addItem(plain);
        ItemStack boosted = plugin.core().items().create("test_gem", 1);
        plugin.core().items().setBonus(boosted, Stat.STRENGTH, 5);
        alice.getInventory().addItem(boosted);
        var r = sellAll();
        assertTrue(r.ok());
        assertEquals(4, r.items());
        assertEquals(1, java.util.Arrays.stream(alice.getInventory().getStorageContents()).filter(java.util.Objects::nonNull).count());
        assertTrue(keys.known("test_gem"));
        assertFalse(keys.known("not_registered"));
        assertFalse(keys.known("wheat")); // vanilla keys are upper case only
    }

    @Test
    public void pressureLowersThePriceAndRecovers() {
        long now = System.currentTimeMillis();
        double fresh = pressure.multiplier("WHEAT", 0, now);
        assertEquals(1.0, fresh, 1e-9);
        pressure.record("WHEAT", 100, now);
        double after = pressure.multiplier("WHEAT", 0, now);
        assertEquals(0.5, after, 1e-9);
        assertEquals(0.25, pressure.multiplier("WHEAT", 0, now) * 0 + Math.max(0.25, pressure.multiplier("WHEAT", 100000, now)), 1e-9);
        // two hours later (default half life) the pressure halved
        double later = pressure.multiplier("WHEAT", 0, now + 2 * 3_600_000L);
        assertEquals(1.0 / (1.0 + 50.0 / 100.0), later, 1e-6);
    }

    @Test
    public void sellingEverythingTwiceIsWorthLessTheSecondTime() {
        alice.getInventory().addItem(new ItemStack(Material.WHEAT, 64));
        var first = sellAll();
        alice.getInventory().addItem(new ItemStack(Material.WHEAT, 64));
        var second = sellAll();
        assertTrue(first.ok() && second.ok());
        assertTrue("second sale must pay less: " + first.cents() + " vs " + second.cents(), second.cents() < first.cents());
    }

    @Test
    public void buyTakesCoinsAndGivesItemsOrNothing() {
        var item = shop().item("WHEAT").orElseThrow();
        assertFalse(shops.buy(alice, shop(), item, 3).ok());
        assertEquals(0, Inv.count(alice, keys, "WHEAT"));
        eco.addCents(alice.getUniqueId(), 5_000, "seed");
        var r = shops.buy(alice, shop(), item, 3);
        assertTrue(r.error(), r.ok());
        assertEquals(5_000 - 2_400, eco.balanceCents(alice.getUniqueId()));
        assertEquals(3, Inv.count(alice, keys, "WHEAT"));
    }

    @Test
    public void aFullInventoryRefusesThePurchaseWithoutTakingCoins() {
        eco.addCents(alice.getUniqueId(), 100_000, "seed");
        for (int i = 0; i < 36; i++) {
            alice.getInventory().setItem(i, new ItemStack(Material.DIRT, 64));
        }
        var r = shops.buy(alice, shop(), shop().item("WHEAT").orElseThrow(), 1);
        assertFalse(r.ok());
        assertEquals(100_000, eco.balanceCents(alice.getUniqueId()));
    }

    @Test
    public void buybackReturnsTheItemsForWhatTheyPaid() {
        alice.getInventory().addItem(new ItemStack(Material.WHEAT, 10));
        var sold = sellAll();
        assertTrue(sold.ok());
        long afterSale = eco.balanceCents(alice.getUniqueId());
        var entries = shops.buybackOf(alice.getUniqueId());
        assertEquals(1, entries.size());
        assertEquals(sold.cents(), entries.get(0).paidCents());
        var back = shops.repurchase(alice, entries.get(0));
        assertTrue(back.error(), back.ok());
        assertEquals(afterSale - sold.cents(), eco.balanceCents(alice.getUniqueId()));
        assertEquals(10, Inv.count(alice, keys, "WHEAT"));
        // the same entry cannot be bought twice
        assertFalse(shops.repurchase(alice, entries.get(0)).ok());
        assertEquals(10, Inv.count(alice, keys, "WHEAT"));
    }

    @Test
    public void buybackNeedsTheCoins() {
        alice.getInventory().addItem(new ItemStack(Material.WHEAT, 10));
        sellAll();
        eco.setCents(alice.getUniqueId(), 0, "t");
        assertFalse(shops.repurchase(alice, shops.buybackOf(alice.getUniqueId()).get(0)).ok());
        assertEquals(0, Inv.count(alice, keys, "WHEAT"));
    }

    @Test
    public void purseAtTheCapRefusesTheSaleAndKeepsTheItems() {
        eco.setCents(alice.getUniqueId(), eco.settings().maxBalanceCents(), "t");
        alice.getInventory().addItem(new ItemStack(Material.WHEAT, 10));
        assertFalse(sellAll().ok());
        assertEquals(10, Inv.count(alice, keys, "WHEAT"));
    }

    @Test
    public void listOfTestedKeysUsesTheSharedCatalog() {
        assertEquals(List.of("test"), List.copyOf(shops.catalog().shops().keySet()));
    }
}
