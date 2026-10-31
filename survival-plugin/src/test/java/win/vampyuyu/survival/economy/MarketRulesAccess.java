package win.vampyuyu.survival.economy;

import org.bukkit.inventory.ItemStack;

/** Test-only door to the package-private {@link MarketRules}. */
public final class MarketRulesAccess {
    private MarketRulesAccess() {}

    public static boolean bound(ItemStack stack) {
        return MarketRules.bound(stack);
    }
}
