package win.vampyuyu.survival.economy;

import org.bukkit.configuration.file.YamlConfiguration;
import win.vampyuyu.survival.api.EconomyApi;
import win.vampyuyu.survival.core.Core;
import win.vampyuyu.survival.core.Feature;
import win.vampyuyu.survival.core.Scheduler;

/**
 * Skyblock coins: the purse, the bank, NPC merchants, the bazaar and the auction house.
 * See docs/skyblock-notes/economy.md.
 */
public final class EconomyFeature implements Feature {
    private Core core;
    private EconomyService economy;
    private Bank bank;
    private Scheduler.Handle bankTask;
    private Scheduler.Handle pressureTask;
    private ShopService shops;
    private PricePressure pressure;
    private NpcManager npcs;
    private BazaarService bazaar;
    private AuctionService auctions;
    private DurableFile.Live bazaarIo;
    private DurableFile.Live auctionIo;
    private Scheduler.Handle auctionTask;
    private Prompts prompts;
    private ItemKeys keys;

    @Override
    public String id() {
        return "economy";
    }

    @Override
    public void enable(Core core) {
        this.core = core;
        YamlConfiguration config = core.featureConfig("economy");
        economy = new EconomyService(core, EconomySettings.load(config));
        bank = new Bank(core, economy, config);
        BalTop balTop = new BalTop(core, economy);
        EconomyAudit audit = new EconomyAudit(core, economy);

        CoinCommands coins = new CoinCommands(core, economy, balTop);
        coins.register();
        core.listen(new PurseListener(core, economy, coins));
        core.listen(new EconomyGuard(core));
        prompts = new Prompts(core);
        prompts.register();
        BankUi bankUi = new BankUi(core, economy, bank);
        bankUi.prompts(prompts);
        bankUi.register();
        new EconomyAdmin(core, economy, audit, this::reload).register();
        bankTask = core.scheduler().repeating(1200, 1200, bank::tick);

        keys = new ItemKeys(core);
        pressure = new PricePressure();
        pressure.load(core.store().read(PRESSURE_FILE, PricePressure.State.class, PricePressure.State::new));
        shops = new ShopService(core, economy, keys, pressure);
        shops.reload(config);
        ShopUi shopUi = new ShopUi(core, economy, keys, shops);
        shopUi.register();
        npcs = new NpcManager(core, shops, shopUi);
        npcs.enable();
        new NpcCommand(core, npcs, shops).register();
        core.listen(new SessionListener(shops));
        economy.flows().load(core.store().read(FLOWS_FILE, Flows.State.class, Flows.State::new));
        pressureTask = core.scheduler().repeating(12_000, 12_000, () -> saveState(false));

        bazaarIo = new DurableFile.Live(core, "bazaar");
        bazaar = new BazaarService(core, economy, keys, bazaarIo);
        bazaar.enable(config);
        bazaar.npcPrices(shops::catalog);
        BazaarUi bazaarUi = new BazaarUi(core, economy, keys, bazaar);
        bazaarUi.prompts(prompts);
        bazaarUi.register();
        audit.addContributor(bazaar);
        allowTradables();

        auctionIo = new DurableFile.Live(core, "auction");
        auctions = new AuctionService(core, economy, keys, auctionIo);
        auctions.enable(config);
        AuctionUi auctionUi = new AuctionUi(core, keys, auctions);
        auctionUi.prompts(prompts);
        auctionUi.register();
        audit.addContributor(auctions);
        auctionTask = core.scheduler().repeating(40, 40, auctions::tick);

        core.register(EconomyApi.class, economy);
    }

    private void reload() {
        YamlConfiguration config = core.featureConfig("economy");
        economy.settings(EconomySettings.load(config));
        bank.reload(config);
        shops.reload(config);
        bazaar.reload(config);
        allowTradables();
        auctions.reload(config);
        npcs.refreshAll();
    }

    /** Custom ids may trade only when a shop table or a bazaar category lists them. */
    private void allowTradables() {
        java.util.Set<String> ids = new java.util.HashSet<>(shops.catalog().keys());
        ids.addAll(bazaar.productKeys());
        keys.allowCustom(ids);
    }

    private static final String PRESSURE_FILE = "economy/pressure.json";
    private static final String FLOWS_FILE = "economy/flows.json";

    /**
     * Writes the sell pressure and the coin flows. The snapshot is taken on the calling (main) thread, the disk write
     * happens off it, except on disable where it must finish before the server stops.
     */
    private void saveState(boolean wait) {
        PricePressure.State p = pressure.export(System.currentTimeMillis());
        Flows.State f = economy.flows().export();
        Runnable write = () -> {
            try {
                core.store().write(PRESSURE_FILE, p);
                core.store().write(FLOWS_FILE, f);
            } catch (java.io.IOException e) {
                core.getPlugin().getLogger().warning("Could not save the sell pressure and coin flows: " + e.getMessage());
            }
        };
        if (wait) {
            write.run();
        } else {
            core.scheduler().async(write);
        }
    }

    @Override
    public void disable() {
        if (bankTask != null) {
            bankTask.cancel();
        }
        if (pressureTask != null) {
            pressureTask.cancel();
        }
        if (auctionTask != null) {
            auctionTask.cancel();
        }
        if (prompts != null) {
            prompts.clear();
        }
        if (npcs != null) {
            npcs.disable();
        }
        // Markets write their final state (and put unfinished claims back) before the purses are closed.
        if (bazaar != null) {
            bazaar.close(bazaarIo);
        }
        if (auctions != null) {
            auctions.close(auctionIo);
        }
        if (pressure != null && economy != null) {
            saveState(true);
        }
        if (economy != null) {
            economy.close();
        }
    }
}
