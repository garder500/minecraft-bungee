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
        new BankUi(core, economy, bank).register();
        new EconomyAdmin(core, economy, audit, this::reload).register();
        bankTask = core.scheduler().repeating(1200, 1200, bank::tick);

        ItemKeys keys = new ItemKeys(core);
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
        pressureTask = core.scheduler().repeating(12_000, 12_000, this::savePressure);

        bazaarIo = new DurableFile.Live(core, "bazaar");
        bazaar = new BazaarService(core, economy, keys, bazaarIo);
        bazaar.enable(config);
        new BazaarUi(core, economy, keys, bazaar).register();
        audit.addContributor(bazaar);

        auctionIo = new DurableFile.Live(core, "auction");
        auctions = new AuctionService(core, economy, keys, auctionIo);
        auctions.enable(config);
        new AuctionUi(core, keys, auctions).register();
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
        auctions.reload(config);
        npcs.refreshAll();
    }

    private static final String PRESSURE_FILE = "economy/pressure.json";

    private void savePressure() {
        try {
            core.store().write(PRESSURE_FILE, pressure.export(System.currentTimeMillis()));
        } catch (java.io.IOException e) {
            core.getPlugin().getLogger().warning("Could not save the sell pressure: " + e.getMessage());
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
        if (pressure != null) {
            savePressure();
        }
        if (economy != null) {
            economy.close();
        }
    }
}
