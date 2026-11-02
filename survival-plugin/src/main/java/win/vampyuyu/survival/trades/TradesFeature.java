package win.vampyuyu.survival.trades;

import org.bukkit.permissions.PermissionDefault;
import win.vampyuyu.survival.api.TradeApi;
import win.vampyuyu.survival.core.Core;
import win.vampyuyu.survival.core.Feature;

/**
 * The Artisan and Warden trades (GDD 11.3): cumulative with any class, chosen with {@code /trade}, levelled by use.
 * Other features ask {@link TradeApi} for a multiplier and get 1.0 when the player has no trade (rule 11.4).
 */
public final class TradesFeature implements Feature {
    private TradeService service;

    @Override
    public String id() {
        return "trades";
    }

    @Override
    public void enable(Core core) {
        TradeConfig config = TradeConfig.load(core.data());
        config.problems().forEach(p -> core.getPlugin().getLogger().warning("trades data: " + p));
        service = new TradeService(core, config);
        core.permissions().register(TradeCommand.ADMIN, "Trades administration (add experience)", PermissionDefault.OP);
        TradeCommand command = new TradeCommand(core, service);
        core.commands().command("trade").aliases("trades", "job")
            .description("Choose the Artisan or Warden trade and use its ability")
            .usage("/trade [status|choose <artisan|warden>|drop <trade>|overclock|mend]")
            .permission("vampyuyu.sb.trades.use", PermissionDefault.TRUE)
            .executor(command).tab(command).register();
        core.register(TradeApi.class, service);
        core.register(TradeService.class, service);
    }

    @Override
    public void disable() {
        service = null;
    }
}
