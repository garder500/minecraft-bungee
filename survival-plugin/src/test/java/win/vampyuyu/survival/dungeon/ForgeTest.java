package win.vampyuyu.survival.dungeon;

import static org.junit.Assert.*;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
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

public class ForgeTest {
    private ServerMock server;
    private SurvivalPlugin plugin;
    private DungeonService svc;
    private PlayerMock alice;

    @Before
    public void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(SurvivalPlugin.class);
        win.vampyuyu.survival.LegacyFeatureFixture.enable(plugin);
        svc = plugin.core().require(DungeonService.class);
        server.addSimpleWorld("main_world");
        alice = server.addPlayer("Alice");
    }

    @After
    public void tearDown() {
        win.vampyuyu.survival.LegacyFeatureFixture.disable(plugin);
        MockBukkit.unmock();
    }

    private void give(String id, int amount) {
        for (int left = amount; left > 0; ) {
            ItemStack s = plugin.core().items().create(id);
            int n = Math.min(left, s.getMaxStackSize());
            s.setAmount(n);
            alice.getInventory().addItem(s);
            left -= n;
        }
    }

    private int have(String id) {
        return svc.forge.count(alice, id);
    }

    private int pending() {
        return plugin.core().profiles().section(alice.getUniqueId(), "dungeon", DungeonData.class, DungeonData::new).pendingCount();
    }

    @Test
    public void aTradeTakesExactlyTheCostAndGivesTheResult() {
        give(DungeonItems.BONE_FRAGMENT, 40);
        give(DungeonItems.CRYPT_SHARD, 5);
        var r = svc.forge.buy(alice, svc.forge.trade("spirit_leaf"));
        assertTrue(r.message(), r.ok());
        assertEquals(24, have(DungeonItems.BONE_FRAGMENT));
        assertEquals(1, have(DungeonItems.CRYPT_SHARD));
        assertEquals(1, have(DungeonItems.SPIRIT_LEAF));
    }

    @Test
    public void nothingIsTakenOrGivenWhenTheMaterialsAreShort() {
        give(DungeonItems.BONE_FRAGMENT, 16);
        give(DungeonItems.CRYPT_SHARD, 3);
        var r = svc.forge.buy(alice, svc.forge.trade("spirit_leaf"));
        assertFalse(r.ok());
        assertTrue(r.message(), r.message().contains("1 more crypt shard"));
        assertEquals(16, have(DungeonItems.BONE_FRAGMENT));
        assertEquals(3, have(DungeonItems.CRYPT_SHARD));
        assertEquals(0, have(DungeonItems.SPIRIT_LEAF));
    }

    @Test
    public void repeatedClicksCannotBuyMoreThanThePaymentCovers() {
        give(DungeonItems.BONE_FRAGMENT, 30);
        int bought = 0;
        for (int i = 0; i < 10; i++) {
            if (svc.forge.buy(alice, svc.forge.trade("healing_flasks")).ok()) {
                bought++;
            }
        }
        assertEquals(2, bought);
        assertEquals(6, have(DungeonItems.HEALING_FLASK));
        assertEquals(6, have(DungeonItems.BONE_FRAGMENT));
    }

    @Test
    public void costIsTakenAcrossSeveralStacks() {
        ItemStack a = plugin.core().items().create(DungeonItems.BONE_FRAGMENT);
        a.setAmount(5);
        ItemStack b = plugin.core().items().create(DungeonItems.BONE_FRAGMENT);
        b.setAmount(9);
        alice.getInventory().setItem(0, a);
        alice.getInventory().setItem(20, b);
        var r = svc.forge.buy(alice, svc.forge.trade("healing_flasks")); // costs 12
        assertTrue(r.message(), r.ok());
        assertEquals(2, have(DungeonItems.BONE_FRAGMENT));
        assertEquals(3, have(DungeonItems.HEALING_FLASK));
    }

    @Test
    public void coinTradesAreRefusedWithoutAnEconomyAndNothingIsTaken() {
        svc.ctx.override(EconomyApi.class, null); // explicitly absent, whatever the economy feature registered
        give(DungeonItems.BONE_FRAGMENT, 64);
        var r = svc.forge.buy(alice, svc.forge.trade("sell_bones"));
        assertFalse(r.ok());
        assertEquals(64, have(DungeonItems.BONE_FRAGMENT));
    }

    @Test
    public void coinTradesPayThroughTheEconomy() {
        AtomicReference<Double> paid = new AtomicReference<>(0.0);
        EconomyApi fake = (EconomyApi) java.lang.reflect.Proxy.newProxyInstance(getClass().getClassLoader(),
            new Class<?>[] {EconomyApi.class}, (proxy, method, args) -> {
                if (method.getName().equals("add")) {
                    paid.set((Double) args[1]);
                }
                return method.getReturnType() == boolean.class ? Boolean.TRUE : method.getReturnType() == double.class ? (Object) 0.0 : null;
            });
        svc.ctx.override(EconomyApi.class, fake);
        give(DungeonItems.BONE_FRAGMENT, 64);
        var r = svc.forge.buy(alice, svc.forge.trade("sell_bones"));
        assertTrue(r.message(), r.ok());
        assertEquals(6.0, paid.get(), 0.0);
        assertEquals(0, have(DungeonItems.BONE_FRAGMENT));
    }

    @Test
    public void aCacheRollsFromTheRewardTableAndDeliversEverythingRolled() {
        give(DungeonItems.CRYPT_SHARD, 12);
        give(DungeonItems.WITHER_ESSENCE, 3);
        var data = plugin.core().profiles().section(alice.getUniqueId(), "dungeon", DungeonData.class, DungeonData::new);
        data.highestFloorCleared = 3;
        var r = svc.forge.buy(alice, svc.forge.trade("crypt_cache"));
        assertTrue(r.message(), r.ok());
        assertTrue("the message lists what was rolled: " + r.message(), r.message().contains("x "));
        assertEquals("a bare inventory holds everything", 0, pending());
        int total = 0;
        for (ItemStack s : alice.getInventory().getStorageContents()) {
            total += s == null ? 0 : s.getAmount();
        }
        assertTrue("the cache gave something", total > 0);
    }

    @Test
    public void whatDoesNotFitGoesToThePendingListNotToTheFloor() {
        give(DungeonItems.BONE_FRAGMENT, 30); // one stack that is only half spent
        for (int i = 0; i < alice.getInventory().getSize(); i++) {
            if (alice.getInventory().getItem(i) == null) {
                alice.getInventory().setItem(i, new ItemStack(Material.COBBLESTONE, 64));
            }
        }
        var r = svc.forge.buy(alice, svc.forge.trade("healing_flasks"));
        assertTrue(r.ok());
        assertEquals(18, have(DungeonItems.BONE_FRAGMENT));
        assertEquals("no room: nothing in the inventory", 0, have(DungeonItems.HEALING_FLASK));
        assertEquals("three flasks are waiting for /dungeon claim", 3, pending());
    }

    @Test
    public void tradesAreRefusedInsideARunAndForUnknownIds() {
        assertNull(svc.forge.trade("nope"));
        assertFalse(svc.forge.buy(null, svc.forge.trade("spirit_leaf")).ok());
        assertFalse(svc.forge.buy(alice, null).ok());
    }

    @Test
    public void similarLookingItemsDoNotCountAsPayment() {
        alice.getInventory().addItem(new ItemStack(Material.BONE, 64)); // a vanilla bone is not a bone fragment
        assertEquals(0, have(DungeonItems.BONE_FRAGMENT));
        var r = svc.forge.buy(alice, svc.forge.trade("healing_flasks"));
        assertFalse(r.ok());
        assertEquals(64, alice.getInventory().all(Material.BONE).values().stream().mapToInt(ItemStack::getAmount).sum());
    }

    @Test
    public void theCommandBuysAndTabCompletes() {
        give(DungeonItems.BONE_FRAGMENT, 12);
        server.dispatchCommand(alice, "dungeon forge buy healing_flasks");
        assertEquals(3, have(DungeonItems.HEALING_FLASK));
        assertEquals(0, have(DungeonItems.BONE_FRAGMENT));
        var cmd = server.getCommandMap().getCommand("dungeon");
        var tab = cmd.tabComplete(alice, "dungeon", new String[] {"forge", "buy", "spi"});
        assertTrue(tab.contains("spirit_leaf") && tab.contains("spirit_cache"));
        server.dispatchCommand(alice, "dungeon forge buy nothing");
        server.dispatchCommand(alice, "dungeon forge");
        assertNotNull("the menu opened", alice.getOpenInventory());
        win.vampyuyu.survival.core.MenuRules.check(alice.getOpenInventory().getTopInventory(), "crypt forge");
    }

    @Test
    public void statsShowTheCatacombsLevel() {
        var data = plugin.core().profiles().section(alice.getUniqueId(), "dungeon", DungeonData.class, DungeonData::new);
        data.catacombsXp = 1700;
        server.dispatchCommand(alice, "dungeon stats");
        boolean found = false;
        String m;
        while ((m = next()) != null) {
            found |= m.contains("Crypt Depths level: 2");
        }
        assertTrue(found);
    }

    private String next() {
        var c = alice.nextComponentMessage();
        return c == null ? null : net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(c);
    }

    @Test
    public void shippedForgeEqualsTheBuiltInTradesAndIsValid() throws Exception {
        DungeonConfig c;
        try (var db = DungeonTestData.seeded()) {
            c = DungeonConfig.load(db);
        }
        assertEquals(c.problems().toString(), 0, c.problems().size());
        assertEquals(DungeonConfig.DEFAULT_TRADES, c.forgeTrades);
        for (var t : c.forgeTrades) {
            for (String id : t.cost().keySet()) {
                assertTrue(id, plugin.core().items().def(id).isPresent());
            }
            if (!t.give().isEmpty()) {
                assertTrue(t.give(), plugin.core().items().def(t.give()).isPresent());
            }
        }
    }

    @Test
    public void aTradeWithoutACostIsSkippedWithAProblemAndTheRestSurvive() {
        try (var db = DungeonTestData.empty()) {
            DungeonTestData.exec(db, "INSERT INTO dungeon_trade (id, position, name, give, amount) VALUES ('ok', 0, 'ok', 'dungeon_spirit_leaf', 1)",
                "INSERT INTO dungeon_trade_cost (trade_id, item, amount) VALUES ('ok', 'dungeon_crypt_shard', 2)",
                "INSERT INTO dungeon_trade (id, position, name, give, amount) VALUES ('free', 1, 'free', 'dungeon_spirit_leaf', 1)");
            DungeonConfig c = DungeonConfig.load(db);
            assertEquals(1, c.forgeTrades.size());
            assertEquals("ok", c.forgeTrades.get(0).id());
            assertEquals(1, c.problems().size());
        }
    }

    @Test
    public void theSchemaRefusesTradesWithTwoOutcomesOrABadId() {
        try (var db = DungeonTestData.empty()) {
            for (String sql : new String[] {
                "INSERT INTO dungeon_trade (id, position, name, give, coins) VALUES ('two', 0, 'two', 'dungeon_spirit_leaf', 5)",
                "INSERT INTO dungeon_trade (id, position, name) VALUES ('none', 0, 'none')",
                "INSERT INTO dungeon_trade (id, position, name, coins) VALUES ('Bad Id!', 0, 'bad', 5)"}) {
                try {
                    DungeonTestData.exec(db, sql);
                    fail("the schema should refuse: " + sql);
                } catch (win.vampyuyu.survival.data.Db.DataException expected) {
                    // the CHECK constraint did its job
                }
            }
        }
    }

    @Test
    public void aTradeNamingAnUnknownItemIsRefusedBeforeAnythingIsTaken() {
        try (var db = DungeonTestData.empty()) {
            DungeonTestData.exec(db, "INSERT INTO dungeon_trade (id, position, name, give, amount) VALUES ('ghost', 0, 'ghost', 'no_such_item', 1)",
                "INSERT INTO dungeon_trade_cost (trade_id, item, amount) VALUES ('ghost', 'dungeon_crypt_shard', 1)");
            svc.ctx.cfg = DungeonConfig.load(db);
        }
        give(DungeonItems.CRYPT_SHARD, 3);
        var r = svc.forge.buy(alice, svc.forge.trade("ghost"));
        assertFalse(r.ok());
        assertEquals(3, have(DungeonItems.CRYPT_SHARD));
        assertEquals(Map.of("dungeon_crypt_shard", 1), svc.forge.trade("ghost").cost());
    }
}
