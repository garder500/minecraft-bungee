package win.vampyuyu.survival.economy;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import org.bukkit.Material;

/**
 * The merchant definitions of the economy tables. Item keys are kept as written; whether a custom item exists is only
 * checked when a menu is built (the feature that registers it may enable after the economy).
 */
final class ShopCatalog {
    /** One line of a shop. A price of 0 means "not for sale" / "not bought". */
    record ShopItem(String key, long buyCents, long sellCents) {}

    /** A merchant. {@code npcName} is MiniMessage. */
    record ShopDef(String id, String name, String npcName, Material icon, String profession, String npcModel,
            String description, List<ShopItem> items) {
        Optional<ShopItem> item(String key) {
            return items.stream().filter(i -> i.key().equals(key)).findFirst();
        }
    }

    private final Map<String, ShopDef> shops;

    private ShopCatalog(Map<String, ShopDef> shops) {
        this.shops = shops;
    }

    static ShopCatalog empty() {
        return new ShopCatalog(Map.of());
    }

    Map<String, ShopDef> shops() {
        return shops;
    }

    Optional<ShopDef> shop(String id) {
        return Optional.ofNullable(id == null ? null : shops.get(id.toLowerCase(java.util.Locale.ROOT)));
    }

    /** Every item key any shop trades. */
    Set<String> keys() {
        Set<String> out = new java.util.HashSet<>();
        for (ShopDef shop : shops.values()) {
            for (ShopItem i : shop.items()) {
                out.add(i.key());
            }
        }
        return out;
    }

    /** Highest sell price any shop pays for the key (used by sell-all). */
    OptionalLong bestSell(String key) {
        long best = 0;
        for (ShopDef shop : shops.values()) {
            for (ShopItem i : shop.items()) {
                if (i.key().equals(key)) {
                    best = Math.max(best, i.sellCents());
                }
            }
        }
        return best > 0 ? OptionalLong.of(best) : OptionalLong.empty();
    }

    /** Lowest price any shop asks for the key (the cheapest the NPCs sell it at). */
    OptionalLong lowestBuy(String key) {
        long best = 0;
        for (ShopDef shop : shops.values()) {
            for (ShopItem i : shop.items()) {
                if (i.key().equals(key) && i.buyCents() > 0 && (best == 0 || i.buyCents() < best)) {
                    best = i.buyCents();
                }
            }
        }
        return best > 0 ? OptionalLong.of(best) : OptionalLong.empty();
    }

    /** Builds the catalog from the merchant and offer rows; a bad row is skipped (and logged when it matters). */
    static ShopCatalog of(EconomyConfig c, java.util.logging.Logger log) {
        Map<String, ShopDef> out = new LinkedHashMap<>();
        java.util.function.Predicate<String> never = MarketRules.neverTraded(c.neverTraded());
        int refused = 0;
        for (EconomyConfig.Merchant m : c.merchants()) {
            String id = m.id().toLowerCase(java.util.Locale.ROOT);
            Material icon = Material.matchMaterial(m.icon());
            if (icon == null || !icon.isItem()) {
                icon = Material.CHEST;
            }
            List<ShopItem> items = new ArrayList<>();
            for (EconomyConfig.Offer offer : c.offersOf(m.id())) {
                if (never.test(offer.key())) {
                    refused++;
                    continue;
                }
                long buy = Money.toCents(offer.buy()).orElse(0);
                long sell = Money.toCents(offer.sell()).orElse(0);
                if (buy <= 0 && sell <= 0) {
                    continue;
                }
                items.add(new ShopItem(offer.key(), buy, sell));
            }
            String name = m.name().isBlank() ? ItemKeys.pretty(m.id()) : m.name();
            out.put(id, new ShopDef(id, name, m.npcName().isBlank() ? "<yellow>" + name : m.npcName(), icon,
                m.profession(), m.npcType(), m.description(), List.copyOf(items)));
        }
        Set<String> known = new java.util.HashSet<>();
        for (EconomyConfig.Merchant m : c.merchants()) {
            known.add(m.id());
        }
        for (EconomyConfig.Offer offer : c.offers()) {
            if (!known.contains(offer.merchant())) {
                log.warning("Offer " + offer.key() + ": unknown merchant " + offer.merchant());
            }
        }
        if (refused > 0) {
            log.info("Shops: " + refused + " ultra rare entries are never traded by merchants (market_never_traded)");
        }
        return new ShopCatalog(out);
    }
}
