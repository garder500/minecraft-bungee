package win.vampyuyu.survival.economy;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

/**
 * The merchant definitions of economy.yml. Item keys are kept as written; whether a custom item exists is only
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

    static ShopCatalog parse(ConfigurationSection c, java.util.logging.Logger log) {
        Map<String, ShopDef> out = new LinkedHashMap<>();
        ConfigurationSection section = c.getConfigurationSection("shops");
        if (section == null) {
            return new ShopCatalog(out);
        }
        for (String id : section.getKeys(false)) {
            ConfigurationSection s = section.getConfigurationSection(id);
            if (s == null) {
                continue;
            }
            Material icon = Material.matchMaterial(s.getString("icon", "CHEST"));
            if (icon == null || !icon.isItem()) {
                icon = Material.CHEST;
            }
            List<ShopItem> items = new ArrayList<>();
            ConfigurationSection list = s.getConfigurationSection("items");
            if (list != null) {
                for (String key : list.getKeys(false)) {
                    ConfigurationSection price = list.getConfigurationSection(key);
                    if (price == null) {
                        log.warning("Shop " + id + ": item " + key + " needs {buy: x, sell: y}");
                        continue;
                    }
                    long buy = Money.toCents(price.getDouble("buy", 0)).orElse(0);
                    long sell = Money.toCents(price.getDouble("sell", 0)).orElse(0);
                    if (buy <= 0 && sell <= 0) {
                        continue;
                    }
                    items.add(new ShopItem(key, buy, sell));
                }
            }
            String name = s.getString("name", ItemKeys.pretty(id));
            out.put(id.toLowerCase(java.util.Locale.ROOT), new ShopDef(id.toLowerCase(java.util.Locale.ROOT), name,
                s.getString("npc-name", "<yellow>" + name), icon, s.getString("profession", "NONE"),
                s.getString("npc-type", "VILLAGER"), s.getString("description", ""), List.copyOf(items)));
        }
        return new ShopCatalog(out);
    }
}
