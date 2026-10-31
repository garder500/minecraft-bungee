package win.vampyuyu.survival.economy;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

/**
 * What the server never buys or sells: ultra rare items stay out of the merchants and the Bazaar (the Auction House is
 * the only place players can trade them), and a bound (soulbound) item is traded nowhere at all.
 */
final class MarketRules {
    /** PDC key (long 1) written on items that can never be sold, traded or auctioned. Same flag as the Wings. */
    static final NamespacedKey UNTRADABLE = new NamespacedKey("vampyuyu", "untradable");

    /** Used when economy.yml has no {@code market.never-traded} list. A trailing {@code *} matches a prefix. */
    private static final List<String> DEFAULTS = List.of("DIAMOND", "DIAMOND_*", "ANCIENT_DEBRIS", "NETHERITE_*",
        "NETHER_STAR", "TOTEM_OF_UNDYING", "ELYTRA", "BEACON", "ENCHANTED_GOLDEN_APPLE", "DRAGON_EGG", "DRAGON_HEAD",
        "minion_enchanted_diamond*", "minion_super_diamond*");

    private MarketRules() {}

    /** A test for keys (vanilla UPPER_CASE or custom lower_case ids) that the merchants and the Bazaar must refuse. */
    static Predicate<String> neverTraded(ConfigurationSection root) {
        List<String> entries = new ArrayList<>(DEFAULTS);
        if (root != null) {
            entries.addAll(root.getStringList("market.never-traded"));
        }
        List<String> exact = new ArrayList<>();
        List<String> prefixes = new ArrayList<>();
        for (String entry : entries) {
            String e = entry.trim().toLowerCase(Locale.ROOT);
            if (e.endsWith("*")) {
                prefixes.add(e.substring(0, e.length() - 1));
            } else if (!e.isEmpty()) {
                exact.add(e);
            }
        }
        return key -> {
            String k = key.toLowerCase(Locale.ROOT);
            return exact.contains(k) || prefixes.stream().anyMatch(k::startsWith);
        };
    }

    /** True for an item bound to its owner: it can be sold nowhere. */
    static boolean bound(ItemStack stack) {
        if (stack == null || stack.getType().isAir() || !stack.hasItemMeta()) {
            return false;
        }
        var pdc = stack.getItemMeta().getPersistentDataContainer();
        Long flag = pdc.get(UNTRADABLE, PersistentDataType.LONG);
        // a sword with a skin on it is bound to its owner
        return (flag != null && flag >= 1) || pdc.has(new NamespacedKey("vampyuyu", "skin"), PersistentDataType.STRING);
    }
}
