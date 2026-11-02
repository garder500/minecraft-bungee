package win.vampyuyu.survival.trades;

import java.util.UUID;
import win.vampyuyu.survival.api.Trade;
import win.vampyuyu.survival.api.TradeApi;
import win.vampyuyu.survival.core.Core;

/** Profile access around {@link TradeRules}; also the published {@link TradeApi}. */
public final class TradeService implements TradeApi {
    private final Core core;
    private volatile TradeConfig config;

    public TradeService(Core core, TradeConfig config) {
        this.core = core;
        this.config = config;
    }

    public TradeConfig config() {
        return config;
    }

    public void reload(TradeConfig fresh) {
        config = fresh;
    }

    /** The live section of an online player (main thread). */
    public TradeData data(UUID id) {
        return core.profiles().section(id, "trades", TradeData.class, TradeData::new);
    }

    @Override
    public int level(UUID player, Trade trade) {
        if (!core.profiles().isLoaded(player)) {
            return 0; // an offline owner: the machines run without the bonus rather than reading a profile from the tick
        }
        return TradeRules.level(data(player), trade, config);
    }

    @Override
    public double forgeDurationFactor(UUID player) {
        return level(player, Trade.ARTISAN) > 0 ? config.forgeFactor() : 1;
    }

    @Override
    public double minerEnergyFactor(UUID player) {
        return level(player, Trade.ARTISAN) > 0 ? config.minerEnergyFactor() : 1;
    }

    @Override
    public double yieldFactor(UUID player) {
        return level(player, Trade.WARDEN) > 0 ? config.yieldFactor() : 1;
    }

    @Override
    public void addXp(UUID player, Trade trade, int amount) {
        if (amount <= 0 || !core.profiles().isLoaded(player)) {
            return;
        }
        TradeData d = data(player);
        if (!d.trades.contains(trade.id())) {
            return; // only a trade the player took earns experience
        }
        d.xp.merge(trade.id(), (long) amount, Long::sum);
        core.profiles().markDirty(player);
    }
}
