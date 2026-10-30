package win.vampyuyu.survival.economy;

import static org.junit.Assert.*;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.BundleMeta;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import win.vampyuyu.survival.SurvivalPlugin;
import win.vampyuyu.survival.api.EconomyApi;
import win.vampyuyu.survival.api.WingsApi;
import win.vampyuyu.survival.core.CustomItemDef;
import win.vampyuyu.survival.core.Rarity;

/** Regression tests for the review fixes: pressure pooling, audit, flows, nested items, prompts, price seeds. */
public class ReviewFixesTest {
    private ServerMock server;
    private SurvivalPlugin plugin;
    private EconomyService eco;
    private PlayerMock alice;

    @Before
    public void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(SurvivalPlugin.class);
        eco = (EconomyService) plugin.core().service(EconomyApi.class).orElseThrow();
        alice = server.addPlayer("Alice");
    }

    @After
    public void tearDown() {
        MockBukkit.unmock();
    }

    private static YamlConfiguration shipped() {
        return YamlConfiguration.loadConfiguration(new InputStreamReader(
            ReviewFixesTest.class.getResourceAsStream("/economy.yml"), StandardCharsets.UTF_8));
    }

    // ---------------------------------------------------------------- pressure

    @Test
    public void compactedItemsPoolTheirPressureWithTheRawItem() {
        PricePressure p = new PricePressure();
        YamlConfiguration cfg = shipped();
        p.reload(cfg);
        long now = System.currentTimeMillis();
        // 20,000 raw wheat halve the price ...
        PricePressure raw = new PricePressure();
        raw.reload(cfg);
        raw.record("WHEAT", 20_000, now);
        double rawMult = raw.multiplier("WHEAT", 0, now);
        // ... and so do the 133.3 enchanted wheat that stand for the same value
        p.record("minion_enchanted_wheat", 133, now);
        p.record("minion_enchanted_wheat", 1, now);
        double enchantedMult = p.multiplier("minion_enchanted_wheat", 0, now);
        assertEquals(rawMult, enchantedMult, 0.01);
        assertEquals("the raw item feels it too", enchantedMult, p.multiplier("WHEAT", 0, now), 1e-9);
        assertTrue(enchantedMult < 0.55);
    }

    @Test
    public void superItemsWeighTheirFullRawValue() {
        PricePressure p = new PricePressure();
        p.reload(shipped());
        long now = System.currentTimeMillis();
        p.record("minion_super_coal", 1, now);
        // one super item is worth 22,500 raw coal: more pressure than the soft cap
        assertTrue(p.multiplier("COAL", 0, now) < 0.5);
    }

    @Test
    public void rawSalesLowerTheCompactedPriceToo() {
        PricePressure p = new PricePressure();
        p.reload(shipped());
        long now = System.currentTimeMillis();
        p.record("WHEAT", 20_000, now);
        assertTrue("no dodging the raw price through compacted goods",
            p.multiplier("minion_enchanted_wheat", 0, now) < 0.55);
    }

    @Test
    public void buybackReturnsPooledPressure() {
        PricePressure p = new PricePressure();
        p.reload(shipped());
        long now = System.currentTimeMillis();
        p.record("minion_enchanted_wheat", 100, now);
        p.record("minion_enchanted_wheat", -100, now);
        assertEquals(1.0, p.multiplier("WHEAT", 0, now), 1e-9);
    }

    @Test
    public void everyShippedCompactedKeyPoolsIntoARealMaterial() {
        YamlConfiguration cfg = shipped();
        PricePressure p = new PricePressure();
        p.reload(cfg);
        ShopCatalog catalog = ShopCatalog.parse(cfg, java.util.logging.Logger.getAnonymousLogger());
        int seen = 0;
        for (var shop : catalog.shops().values()) {
            for (var item : shop.items()) {
                String key = item.key();
                if (key.startsWith("minion_enchanted_") || key.startsWith("minion_super_")) {
                    PricePressure.Pool pool = p.pool(key);
                    assertNotEquals(key + " does not pool into a material", key, pool.key());
                    assertNotNull(key, Material.matchMaterial(pool.key()));
                    assertTrue(pool.weight() > 1);
                    seen++;
                }
            }
        }
        assertTrue(seen > 20);
    }

    // ---------------------------------------------------------------- audit and flows

    @Test
    public void auditCatchesDriftOlderThanTheLedger() {
        UUID id = alice.getUniqueId();
        eco.addCents(id, 10_000, "seed");
        // the purse silently grew by 500 without a lifetime entry (a past bug): the ring chain is still unbroken
        eco.update(id, (d, txs) -> {
            d.cents += 500;
            d.ledger.get(d.ledger.size() - 1).after += 500;
            return null;
        });
        List<String> problems = new ArrayList<>();
        EconomyAudit.check(id, eco.data(id), problems);
        assertEquals(problems.toString(), 1, problems.size());
        assertTrue(problems.get(0), problems.get(0).contains("lifetime"));
    }

    @Test
    public void aHealthyPurseHasNoLifetimeProblem() {
        UUID id = alice.getUniqueId();
        eco.addCents(id, 10_000, "seed");
        eco.takeCents(id, 2_500, "spend");
        List<String> problems = new ArrayList<>();
        EconomyAudit.check(id, eco.data(id), problems);
        assertTrue(problems.toString(), problems.isEmpty());
    }

    @Test
    public void flowsGroupReasonsAndSeparateTransfers() {
        Flows f = new Flows();
        assertEquals("shop:sell", Flows.group("shop:sell:farmer"));
        assertEquals("bank", Flows.group("bank"));
        assertEquals("unknown", Flows.group(""));
        f.record("shop:sell:farmer", 5_000);
        f.record("shop:buy:farmer", -1_000);
        f.record("bazaar:claim", 700);
        f.record("bank:interest", 300);
        f.record("pay:from:Bob", 100);
        assertEquals(5_300, f.minted());
        assertEquals(1_000, f.burned());
        assertTrue(f.rows().stream().anyMatch(r -> r.group().equals("bazaar:claim") && r.transfer()));
        assertFalse(f.rows().stream().anyMatch(r -> r.group().equals("shop:sell") && r.transfer()));
    }

    @Test
    public void flowsSurviveAPersistenceRoundTrip() {
        Flows f = new Flows();
        f.record("shop:sell:x", 900);
        f.record("death:pvp", -400);
        String json = win.vampyuyu.survival.core.JsonStore.GSON.toJson(f.export());
        Flows g = new Flows();
        g.load(win.vampyuyu.survival.core.JsonStore.GSON.fromJson(json, Flows.State.class));
        assertEquals(900, g.minted());
        assertEquals(400, g.burned());
        assertEquals(f.since(), g.since());
    }

    @Test
    public void everyPurseChangeAndBankInterestFeedTheFlowTable() {
        UUID id = alice.getUniqueId();
        long before = eco.flows().minted();
        eco.addCents(id, 1_000, "shop:sell:test");
        assertEquals(before + 1_000, eco.flows().minted());
        long burned = eco.flows().burned();
        eco.takeCents(id, 400, "shop:buy:test");
        assertEquals(burned + 400, eco.flows().burned());
        Bank bank = new Bank(plugin.core(), eco, plugin.core().featureConfig("economy"));
        eco.addCents(id, 10_000_000, "seed");
        long now = System.currentTimeMillis();
        eco.update(id, (d, txs) -> {
            d.bankCents = 1_000_000;
            d.bankInterestAt = now - bank.periodMs() - 10;
            return null;
        });
        long mintedBefore = eco.flows().minted();
        long interest = bank.settle(id);
        assertTrue(interest > 0);
        assertEquals(mintedBefore + interest, eco.flows().minted());
    }

    @Test
    public void listingFeesAreCountedAsDestroyedExactlyOnce() {
        AuctionService ah = auction();
        PlayerMock seller = server.addPlayer("Seller");
        eco.addCents(seller.getUniqueId(), 100_000_00, "test");
        seller.getInventory().setItemInMainHand(new ItemStack(Material.STONE, 5));
        long burnedBefore = eco.flows().burned();
        assertTrue(ah.list(seller, 100_000, false, 12).ok());
        long fee = ah.book().state().feesBurnedCents;
        assertTrue(fee > 0);
        assertEquals("the purse debit already feeds the flow table", burnedBefore + fee, eco.flows().burned());
        EconomyAudit.Report report = new EconomyAudit.Report();
        ah.audit(report);
        assertFalse("fees must not be added on top of the flow table",
            report.burns.keySet().stream().anyMatch(k -> k.toLowerCase().contains("fee")));
        assertEquals(fee, report.info.values().stream().mapToLong(Long::longValue).sum());
        assertTrue(report.burns.containsKey("Auction sale tax burned"));
    }

    @Test
    public void customBooksThatStoreTheirEnchantmentInItemDataAreNotBazaarProducts() {
        YamlConfiguration cfg = shipped();
        for (String cat : cfg.getConfigurationSection("bazaar.categories").getKeys(false)) {
            assertFalse(cat, cfg.getStringList("bazaar.categories." + cat).contains("enchanted_book"));
        }
    }

    @Test
    public void marketsReportTheirBurnedCoinsToTheAudit() {
        BazaarService bz = new BazaarService(plugin.core(), eco, new ItemKeys(plugin.core()), new DurableFile.Inline());
        bz.enable(plugin.core().featureConfig("economy"));
        EconomyAudit.Report report = new EconomyAudit.Report();
        bz.audit(report);
        assertTrue(report.burns.containsKey("Bazaar tax burned"));
    }

    // ---------------------------------------------------------------- bank tuning

    @Test
    public void shippedBankPaysAtMostAFractionOfAPercentPerDay() {
        Bank bank = new Bank(plugin.core(), eco, shipped());
        assertEquals(24 * 3_600_000L, bank.periodMs());
        for (Bank.Tier tier : bank.tiers()) {
            assertTrue(tier.name() + " rate", tier.rateBp() <= 100);
            assertTrue(tier.name() + " cap", tier.maxInterestCents() <= tier.capacityCents() / 100);
        }
        Bank.Accrual offline = Bank.accrue(500_000_000, 1, 1 + 40 * 24 * 3_600_000L, bank.periodMs(), 3,
            bank.tiers().get(0).rateBp(), bank.tiers().get(0).maxInterestCents(), bank.tiers().get(0).capacityCents());
        assertEquals(3, offline.periods());
    }

    @Test
    public void bankAmountsAcceptAllAndHalf() {
        assertEquals(1000, BankUi.parseAmount("all", 1000).getAsLong());
        assertEquals(500, BankUi.parseAmount("half", 1000).getAsLong());
        assertEquals(150_000, BankUi.parseAmount("1.5k", 1000).getAsLong());
        assertTrue(BankUi.parseAmount("lots", 1000).isEmpty());
    }

    // ---------------------------------------------------------------- nested items in the auction house

    private AuctionService auction() {
        AuctionService ah = new AuctionService(plugin.core(), eco, new ItemKeys(plugin.core()), new DurableFile.Inline());
        ah.enable(plugin.core().featureConfig("economy"));
        return ah;
    }

    private void wingsAreElytra() {
        plugin.core().register(WingsApi.class, new WingsApi() {
            public ItemStack create() { return new ItemStack(Material.ELYTRA); }
            public boolean isWings(ItemStack stack) { return stack.getType() == Material.ELYTRA; }
            public boolean isWearing(org.bukkit.entity.Player player) { return false; }
            public boolean grantOnce(org.bukkit.entity.Player player, String source) { return false; }
            public boolean hasReceived(UUID player) { return false; }
        });
    }

    @Test
    public void wingsInsideAShulkerBoxCannotBeAuctioned() {
        wingsAreElytra();
        AuctionService ah = auction();
        ItemStack box = new ItemStack(Material.SHULKER_BOX);
        ah.inspector(stack -> stack.getType() == Material.SHULKER_BOX
            ? List.of(new ItemStack(Material.DIRT), new ItemStack(Material.ELYTRA)) : List.of());
        String why = ah.refuseItem(box);
        assertNotNull(why);
        assertTrue(why, why.contains("Wings"));
    }

    @Test
    public void blockedItemsInsideNestedContainersAreRefused() {
        AuctionService ah = auction();
        ItemStack outer = new ItemStack(Material.SHULKER_BOX);
        ItemStack inner = new ItemStack(Material.BUNDLE);
        ah.inspector(stack -> switch (stack.getType()) {
            case SHULKER_BOX -> List.of(inner);
            case BUNDLE -> List.of(new ItemStack(Material.BEDROCK));
            default -> List.of();
        });
        String why = ah.refuseItem(outer);
        assertNotNull("bedrock hidden two levels deep", why);
        assertNull(ah.refuseItem(new ItemStack(Material.DIRT)));
    }

    @Test
    public void absurdNestingIsRefusedNotWalkedForever() {
        AuctionService ah = auction();
        ah.inspector(stack -> List.of(new ItemStack(Material.SHULKER_BOX)));
        String why = ah.refuseItem(new ItemStack(Material.SHULKER_BOX));
        assertNotNull(why);
        assertTrue(why, why.contains("nested"));
    }

    @Test
    public void aContainerThatCannotBeInspectedIsRefused() {
        AuctionService ah = auction();
        ah.inspector(stack -> { throw new IllegalStateException("boom"); });
        assertNotNull(ah.refuseItem(new ItemStack(Material.CHEST)));
    }

    @Test
    public void realBundlesAndShulkerBoxesAreInspected() {
        wingsAreElytra();
        AuctionService ah = auction();
        ItemStack bundle = new ItemStack(Material.BUNDLE);
        BundleMeta meta = (BundleMeta) bundle.getItemMeta();
        meta.addItem(new ItemStack(Material.ELYTRA));
        bundle.setItemMeta(meta);
        assertEquals(1, AuctionService.contents(bundle).size());
        assertNotNull("wings in a bundle", ah.refuseItem(bundle));
        ItemStack box = new ItemStack(Material.SHULKER_BOX);
        BlockStateMeta boxMeta = (BlockStateMeta) box.getItemMeta();
        org.bukkit.block.ShulkerBox state = (org.bukkit.block.ShulkerBox) boxMeta.getBlockState();
        state.getInventory().addItem(new ItemStack(Material.ELYTRA));
        boxMeta.setBlockState(state);
        box.setItemMeta(boxMeta);
        assertNotNull("wings in a shulker box", ah.refuseItem(box));
    }

    @Test
    public void browseCanShowOnlyBuyItNowOrOnlyAuctions() {
        AuctionService ah = auction();
        PlayerMock seller = server.addPlayer("Seller");
        eco.addCents(seller.getUniqueId(), 100_000_00, "test");
        seller.getInventory().setItemInMainHand(new ItemStack(Material.STONE, 5));
        assertTrue(ah.list(seller, 1_000, true, 12).ok());
        seller.getInventory().setItemInMainHand(new ItemStack(Material.DIRT, 5));
        assertTrue(ah.list(seller, 1_000, false, 12).ok());
        long now = System.currentTimeMillis();
        assertEquals(2, ah.browse(null, null, AuctionService.Sort.NEWEST, AuctionService.Kind.ALL, now).size());
        var bin = ah.browse(null, null, AuctionService.Sort.NEWEST, AuctionService.Kind.BIN, now);
        assertEquals(1, bin.size());
        assertTrue(bin.get(0).bin);
        var auctions = ah.browse(null, null, AuctionService.Sort.NEWEST, AuctionService.Kind.AUCTION, now);
        assertEquals(1, auctions.size());
        assertFalse(auctions.get(0).bin);
        assertEquals(AuctionService.Kind.ALL, AuctionService.Kind.AUCTION.next());
    }

    // ---------------------------------------------------------------- custom items must be listed to trade

    @Test
    public void customItemsTradeOnlyWhenListedAsProducts() {
        plugin.core().items().register(CustomItemDef.builder("odd_gem", "Odd Gem", Rarity.RARE, Material.EMERALD).build());
        ItemKeys keys = new ItemKeys(plugin.core());
        ItemStack gem = plugin.core().items().create("odd_gem", 1);
        assertFalse("unlisted custom ids never count as clean", keys.clean(gem));
        keys.allowCustom(List.of("odd_gem"));
        assertTrue(keys.clean(gem));
        keys.allowCustom(List.of());
        assertFalse(keys.clean(gem));
    }

    @Test
    public void shippedTablesPriceBossAndDungeonMaterials() {
        YamlConfiguration cfg = shipped();
        ShopCatalog catalog = ShopCatalog.parse(cfg, java.util.logging.Logger.getAnonymousLogger());
        for (String id : List.of("dungeon_bone_fragment", "dungeon_crypt_shard", "dungeon_wither_essence",
                "dungeon_spirit_dust", "dungeon_relic", "dungeon_dark_scroll", "voidwyrm_scale", "warden_heart",
                "frost_shard", "matriarch_silk", "leviathan_tooth", "lord_bone", "colossus_core", "roc_feather",
                "revenant_flesh", "tarantula_web", "ravager_tooth", "null_sphere", "demon_ember", "revenant_catalyst")) {
            assertTrue(id + " has a sell price", catalog.bestSell(id).isPresent());
            assertTrue(id + " is never for sale", catalog.lowestBuy(id).isEmpty());
            boolean listed = false;
            for (String cat : cfg.getConfigurationSection("bazaar.categories").getKeys(false)) {
                listed |= cfg.getStringList("bazaar.categories." + cat).contains(id);
            }
            assertTrue(id + " is a bazaar product", listed);
        }
        // summon items, weapons and the wing core are not priced
        for (String id : List.of("void_sigil", "magma_core", "void_fang", "ember_blade", "wing_core", "tidal_trident",
                "dungeon_spirit_leaf", "dungeon_healing_flask")) {
            assertTrue(id, catalog.bestSell(id).isEmpty());
        }
    }

    // ---------------------------------------------------------------- price band seed

    @Test
    public void aProductWithoutHistoryIsAnchoredToTheNpcPrices() {
        ItemKeys keys = new ItemKeys(plugin.core());
        BazaarService bz = new BazaarService(plugin.core(), eco, keys, new DurableFile.Inline());
        bz.enable(plugin.core().featureConfig("economy"));
        YamlConfiguration cfg = new YamlConfiguration();
        cfg.set("shops.t.items.WHEAT.buy", 8);
        cfg.set("shops.t.items.WHEAT.sell", 2);
        cfg.set("shops.t.items.DIAMOND.sell", 100);
        ShopCatalog catalog = ShopCatalog.parse(cfg, java.util.logging.Logger.getAnonymousLogger());
        bz.npcPrices(() -> catalog);
        assertEquals("floor 2 coins", 20, bz.npcFloorTicks("WHEAT"));
        assertEquals("ceiling 8 coins", 80, bz.npcCeilingTicks("WHEAT"));
        assertEquals("geometric mean of 20 and 80", 40, bz.seedTicks("WHEAT"));
        assertEquals("sell-only: the floor", 1000, bz.seedTicks("DIAMOND"));
        assertEquals(0, bz.seedTicks("COBBLESTONE"));

        long now = System.currentTimeMillis();
        // the first order cannot be 100M for a diamond (reference 100 coins, band 5)
        assertFalse(bz.market().checkSellOrder("x", "DIAMOND", 1_000_000_000L, 1, now).ok());
        assertTrue(bz.market().checkSellOrder("x", "DIAMOND", 1_200, 1, now).ok());
        assertFalse(bz.market().checkBuyOrder("x", "DIAMOND", 10, 1, now).ok());
        // once it trades, the real history takes over
        assertEquals(1000, bz.reference("DIAMOND", now));
    }

    @Test
    public void partialSellQuotesCoverOnlyWhatTheBidsCanTake() {
        BazaarMarket m = new BazaarMarket(new BazaarMarket.State(),
            new BazaarMarket.Rules(125, 14, 71_680, 1_000_000_000, 5.0, 72));
        m.placeBuy("B", "B", "WHEAT", 20, 6, 1_000L);
        m.placeBuy("A", "A", "WHEAT", 10, 50, 1_000L);
        assertEquals("A's own orders are skipped", 6, m.sellableDepth("A", "WHEAT"));
        assertFalse(m.quoteSell("A", "WHEAT", 20).check().ok());
        var partial = m.quoteSell("A", "WHEAT", 20, true);
        assertTrue(partial.check().ok());
        assertEquals(6, partial.items());
        assertFalse(m.quoteSell("Z", "COAL", 5, true).check().ok());
    }

    // ---------------------------------------------------------------- chat prompts

    @Test
    public void aPromptTakesTheNextLineThenForgetsIt() {
        Prompts prompts = new Prompts(plugin.core());
        List<String> answers = new ArrayList<>();
        prompts.ask(alice, "How much?", answers::add, () -> answers.add("cancelled"));
        assertTrue(prompts.waiting(alice));
        assertTrue(prompts.handle(alice.getUniqueId(), "  1.5k "));
        assertEquals(List.of("1.5k"), answers);
        assertFalse(prompts.waiting(alice));
        assertFalse("normal chat afterwards", prompts.handle(alice.getUniqueId(), "hello"));
    }

    @Test
    public void typingCancelDropsThePrompt() {
        Prompts prompts = new Prompts(plugin.core());
        List<String> answers = new ArrayList<>();
        prompts.ask(alice, "How much?", answers::add, () -> answers.add("cancelled"));
        assertTrue(prompts.handle(alice.getUniqueId(), "CANCEL"));
        assertEquals(List.of("cancelled"), answers);
    }

    @Test
    public void aTimeoutOrAReplacementNeverReopensAMenu() {
        Prompts prompts = new Prompts(plugin.core());
        List<String> answers = new ArrayList<>();
        prompts.ask(alice, "One?", answers::add, () -> answers.add("first cancelled"));
        prompts.ask(alice, "Two?", answers::add, () -> answers.add("second cancelled"));
        assertEquals("the replaced prompt is dropped silently", List.of(), answers);
        server.getScheduler().performTicks(Prompts.TIMEOUT_TICKS + 1);
        assertEquals("a timeout only sends a message", List.of(), answers);
        assertFalse(prompts.waiting(alice));
        assertFalse(prompts.handle(alice.getUniqueId(), "late"));
    }

    @Test
    public void aStaleTimeoutDoesNotKillANewerPrompt() {
        Prompts prompts = new Prompts(plugin.core());
        List<String> answers = new ArrayList<>();
        prompts.ask(alice, "One?", answers::add, null);
        server.getScheduler().performTicks(Prompts.TIMEOUT_TICKS - 100);
        prompts.ask(alice, "Two?", answers::add, null);
        server.getScheduler().performTicks(150); // the first prompt's timer fires now
        assertTrue("the second prompt is still waiting", prompts.waiting(alice));
        assertTrue(prompts.handle(alice.getUniqueId(), "42"));
        assertEquals(List.of("42"), answers);
    }
}
