package win.vampyuyu.survival.items;

import java.util.Optional;
import java.util.function.Supplier;
import org.bukkit.entity.Player;
import win.vampyuyu.survival.api.EconomyApi;
import win.vampyuyu.survival.core.Core;
import win.vampyuyu.survival.core.Msg;

/**
 * Coin payments through the optional {@link EconomyApi}. Without the economy feature everything is free unless
 * {@code costs.require-economy} is true (then nothing that costs coins can be done). Always charge BEFORE changing an
 * item and {@link #refund} if the change then fails.
 */
public final class Costs {
    private final Core core;
    private final Supplier<ItemsSettings> settings;

    public Costs(Core core, Supplier<ItemsSettings> settings) {
        this.core = core;
        this.settings = settings;
    }

    private Optional<EconomyApi> economy() {
        return core.service(EconomyApi.class);
    }

    /** True when {@code amount} coins can be paid right now (always true for free amounts). */
    public boolean canAfford(Player player, double amount) {
        if (amount <= 0) {
            return true;
        }
        Optional<EconomyApi> api = economy();
        if (api.isEmpty()) {
            return !settings.get().requireEconomy;
        }
        return api.get().has(player.getUniqueId(), amount);
    }

    /** Takes the coins. Returns false (nothing taken, player told why) when it cannot be paid. */
    public boolean charge(Player player, double amount, String reason) {
        if (amount <= 0) {
            return true;
        }
        Optional<EconomyApi> api = economy();
        if (api.isEmpty()) {
            if (settings.get().requireEconomy) {
                core.msg().error(player, "The economy is not available right now.");
                return false;
            }
            return true;
        }
        if (!api.get().take(player.getUniqueId(), amount, reason)) {
            core.msg().error(player, "You need " + Msg.coins(amount) + " for this.");
            return false;
        }
        return true;
    }

    /** Gives coins back after a failed operation. */
    public void refund(Player player, double amount, String reason) {
        if (amount > 0) {
            economy().ifPresent(api -> api.add(player.getUniqueId(), amount, reason + ":refund"));
        }
    }

    /** "1,500 coins" or "Free". */
    public static String price(double amount) {
        return amount <= 0 ? "Free" : Msg.coins(amount);
    }
}
