package win.vampyuyu.survival.economy;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import win.vampyuyu.survival.data.Db;
import win.vampyuyu.survival.data.Settings;

/**
 * Everything the economy reads from the content database, as plain rows: the {@code economy.*} settings and the tables
 * of merchants, offers, bank tiers, Bazaar products, pressure prefixes and trade bans (see {@code V003__economy.sql}).
 * The services turn these rows into their own typed objects and keep their own validation and fallbacks.
 */
public record EconomyConfig(Settings settings, List<Merchant> merchants, List<Offer> offers, List<TierRow> tiers,
        Map<String, Double> pressurePrefixes, List<Product> products, List<String> neverTraded,
        List<String> blockedMaterials, List<String> blockedItems) {

    /** A row of {@code merchant}. */
    public record Merchant(String id, String name, String npcName, String icon, String profession, String npcType,
            String description) {}

    /** A row of {@code merchant_offer} (prices in coins). */
    public record Offer(String merchant, String key, double buy, double sell) {}

    /** A row of {@code bank_tier} (amounts in coins, rate in percent). */
    public record TierRow(String name, double capacity, double rate, double maxInterest, double cost) {}

    /** A row of {@code bazaar_product}. */
    public record Product(String category, String key) {}

    public EconomyConfig {
        merchants = List.copyOf(merchants);
        offers = List.copyOf(offers);
        tiers = List.copyOf(tiers);
        pressurePrefixes = Map.copyOf(pressurePrefixes);
        products = List.copyOf(products);
        neverTraded = List.copyOf(neverTraded);
        blockedMaterials = List.copyOf(blockedMaterials);
        blockedItems = List.copyOf(blockedItems);
    }

    /** Reads the whole economy from the database. */
    public static EconomyConfig load(Db db) {
        Settings settings = Settings.load(db, "economy");
        return db.read(c -> {
            List<Merchant> merchants = Db.query(c, "SELECT id, name, npc_name, icon, profession, npc_type, description "
                + "FROM merchant ORDER BY position, id", rs -> new Merchant(rs.getString(1), rs.getString(2),
                rs.getString(3), rs.getString(4), rs.getString(5), rs.getString(6), rs.getString(7)));
            List<Offer> offers = Db.query(c, "SELECT merchant, item_key, buy, sell FROM merchant_offer "
                + "ORDER BY merchant, position, item_key", rs -> new Offer(rs.getString(1), rs.getString(2),
                rs.getDouble(3), rs.getDouble(4)));
            List<TierRow> tiers = Db.query(c, "SELECT name, capacity, rate, max_interest, cost FROM bank_tier "
                + "ORDER BY position, id", rs -> new TierRow(rs.getString(1), rs.getDouble(2), rs.getDouble(3),
                rs.getDouble(4), rs.getDouble(5)));
            Map<String, Double> prefixes = new LinkedHashMap<>();
            for (Object[] row : Db.query(c, "SELECT prefix, ratio FROM price_pressure_prefix ORDER BY prefix",
                    rs -> new Object[] {rs.getString(1), rs.getDouble(2)})) {
                prefixes.put((String) row[0], (Double) row[1]);
            }
            List<Product> products = Db.query(c, "SELECT category, item_key FROM bazaar_product ORDER BY category, position, item_key",
                rs -> new Product(rs.getString(1), rs.getString(2)));
            List<String> never = Db.query(c, "SELECT pattern FROM market_never_traded ORDER BY pattern", rs -> rs.getString(1));
            List<String> materials = Db.query(c, "SELECT value FROM auction_blocked_item WHERE kind = 'material' ORDER BY value",
                rs -> rs.getString(1));
            List<String> items = Db.query(c, "SELECT value FROM auction_blocked_item WHERE kind = 'item' ORDER BY value",
                rs -> rs.getString(1));
            return new EconomyConfig(settings, merchants, offers, tiers, prefixes, products, never, materials, items);
        });
    }

    /** Settings only (no table rows): every getter of the services falls back to its default. */
    public static EconomyConfig of(Settings settings) {
        return new EconomyConfig(settings, List.of(), List.of(), List.of(), Map.of(), List.of(), List.of(), List.of(),
            List.of());
    }

    /** The offers of one merchant, in menu order. */
    List<Offer> offersOf(String merchant) {
        List<Offer> out = new ArrayList<>();
        for (Offer o : offers) {
            if (o.merchant().equals(merchant)) {
                out.add(o);
            }
        }
        return out;
    }
}
