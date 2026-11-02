package win.vampyuyu.survival.station;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import win.vampyuyu.survival.data.Db;
import win.vampyuyu.survival.data.Settings;

/** The station tables and the {@code station.*} settings, validated. A bad row is skipped and listed in {@link #problems()}. */
public final class StationConfig {
    /** One ingredient: a Material name or {@code custom:<id>}. */
    public record Ingredient(String item, int amount) {}

    /** One Alembic recipe. */
    public record Brew(String id, String family, String name, int seconds, String effect, int amplifier,
            int durationSeconds, String element, int minTier, List<Ingredient> cost) {}

    /** One Ranch product. */
    public record Product(String id, String item, int minTier, int seconds) {}

    /** One contract. {@code kind} is kill, deliver or boss. */
    public record Contract(String id, String kind, String title, String target, int amount, int minTier, int coins,
            int shards, String rewardItem, int rewardAmount) {}

    /** The effect keys an Alembic recipe may use (besides {@code oil}); StationService maps each one to a potion effect. */
    static final Set<String> EFFECTS = Set.of("resistance", "fire_resistance", "water_breathing", "regeneration", "haste",
        "night_vision", "speed", "absorption", "strength", "jump_boost", "slow_falling");

    private final Map<String, Brew> brews = new LinkedHashMap<>();
    private final Map<String, Product> products = new LinkedHashMap<>();
    private final Map<String, Contract> contracts = new LinkedHashMap<>();
    /** What the Forge made an item from: result item id to its ingredients (the Crucible gives them back). */
    private final Map<String, Map<String, Integer>> forgeCosts = new LinkedHashMap<>();
    private final List<String> problems = new ArrayList<>();
    private Set<String> components = Set.of();
    public boolean enabled = true;
    public int alembicSlots = 2;
    public int alembicQueue = 6;
    public double returnRate = 0.6;
    public int duplicateShards = 25;
    public int storageHours = 12;
    public double tierBonus = 0.5;
    public int contractsPerDay = 3;
    public int dailyCoinCap = 3000;

    public static StationConfig load(Db db) {
        StationConfig c = new StationConfig();
        Settings s = Settings.load(db, "station");
        c.enabled = s.getBoolean("enabled", true);
        c.alembicSlots = c.clamp(s.getInt("alembic.slots", 2), 1, 10, "alembic.slots");
        c.alembicQueue = c.clamp(s.getInt("alembic.queue", 6), 1, 54, "alembic.queue");
        c.returnRate = Math.max(0, Math.min(1, s.getDouble("crucible.return-rate", 0.6)));
        c.duplicateShards = c.clamp(s.getInt("crucible.duplicate-shards", 25), 0, 10_000, "crucible.duplicate-shards");
        c.components = Set.copyOf(s.getList("crucible.components"));
        c.storageHours = c.clamp(s.getInt("ranch.storage-hours", 12), 1, 168, "ranch.storage-hours");
        c.tierBonus = Math.max(0, Math.min(5, s.getDouble("ranch.tier-bonus", 0.5)));
        c.contractsPerDay = c.clamp(s.getInt("contracts.per-day", 3), 1, 9, "contracts.per-day");
        c.dailyCoinCap = c.clamp(s.getInt("contracts.daily-coin-cap", 3000), 0, 1_000_000, "contracts.daily-coin-cap");
        db.read(conn -> {
            Map<String, List<Ingredient>> cost = new LinkedHashMap<>();
            for (Object[] r : Db.query(conn, "SELECT recipe_id, item, amount FROM alembic_ingredient ORDER BY recipe_id, position",
                    rs -> new Object[] {rs.getString(1), rs.getString(2), rs.getInt(3)})) {
                cost.computeIfAbsent((String) r[0], k -> new ArrayList<>()).add(new Ingredient((String) r[1], (Integer) r[2]));
            }
            for (Object[] r : Db.query(conn, "SELECT id, family, name, seconds, effect, amplifier, duration_seconds, element, "
                    + "min_tier FROM alembic_recipe ORDER BY position",
                    rs -> new Object[] {rs.getString(1), rs.getString(2), rs.getString(3), rs.getInt(4), rs.getString(5),
                        rs.getInt(6), rs.getInt(7), rs.getString(8), rs.getInt(9)})) {
                c.addBrew(r, cost.getOrDefault((String) r[0], List.of()));
            }
            for (Object[] r : Db.query(conn, "SELECT id, item, min_tier, seconds FROM ranch_product ORDER BY position",
                    rs -> new Object[] {rs.getString(1), rs.getString(2), rs.getInt(3), rs.getInt(4)})) {
                c.products.put((String) r[0], new Product((String) r[0], (String) r[1], (Integer) r[2], (Integer) r[3]));
            }
            for (Object[] r : Db.query(conn, "SELECT id, kind, title, target, amount, min_tier, coins, shards, reward_item, "
                    + "reward_amount FROM contract ORDER BY position",
                    rs -> new Object[] {rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getInt(5),
                        rs.getInt(6), rs.getInt(7), rs.getInt(8), rs.getString(9), rs.getInt(10)})) {
                c.contracts.put((String) r[0], new Contract((String) r[0], (String) r[1], (String) r[2], (String) r[3],
                    (Integer) r[4], (Integer) r[5], (Integer) r[6], (Integer) r[7], (String) r[8], (Integer) r[9]));
            }
            for (Object[] r : Db.query(conn, "SELECT r.result, i.item, i.amount FROM forge_recipe r "
                    + "JOIN forge_recipe_ingredient i ON i.forge_recipe_id = r.id ORDER BY r.position, i.position",
                    rs -> new Object[] {rs.getString(1), rs.getString(2), rs.getInt(3)})) {
                c.forgeCosts.computeIfAbsent((String) r[0], k -> new LinkedHashMap<>()).merge((String) r[1], (Integer) r[2], Integer::sum);
            }
            return null;
        });
        if (c.brews.isEmpty()) {
            c.problems.add("the alembic_recipe table has no valid row");
        }
        return c;
    }

    private void addBrew(Object[] r, List<Ingredient> cost) {
        String id = String.valueOf(r[0]);
        String element = r[7] == null ? null : String.valueOf(r[7]).toLowerCase(Locale.ROOT);
        if (cost.isEmpty()) {
            problems.add("alembic recipe " + id + " has no ingredient, skipped");
            return;
        }
        if ("oil".equals(r[1]) && win.vampyuyu.survival.api.Element.parse(element) == null) {
            problems.add("alembic oil " + id + " needs an element (earth, frost, ember, void, astral), skipped");
            return;
        }
        if (!"oil".equals(r[4]) && !EFFECTS.contains(String.valueOf(r[4]))) {
            problems.add("alembic recipe " + id + " has an unknown effect '" + r[4] + "', skipped");
            return;
        }
        brews.put(id, new Brew(id, (String) r[1], (String) r[2], (Integer) r[3], (String) r[4], (Integer) r[5],
            (Integer) r[6], element, (Integer) r[8], List.copyOf(cost)));
    }

    private int clamp(int v, int lo, int hi, String name) {
        if (v < lo || v > hi) {
            problems.add("station." + name + " = " + v + " is outside " + lo + ".." + hi + ", clamped");
        }
        return Math.max(lo, Math.min(hi, v));
    }

    public Map<String, Brew> brews() { return java.util.Collections.unmodifiableMap(brews); }

    public Map<String, Product> products() { return java.util.Collections.unmodifiableMap(products); }

    public Map<String, Contract> contracts() { return java.util.Collections.unmodifiableMap(contracts); }

    /** Boss component item ids: given back whole by the Crucible. */
    public Set<String> components() { return components; }

    /** The Forge ingredients of an item, empty when the Forge cannot make it. */
    public Map<String, Integer> forgeCost(String itemId) {
        return forgeCosts.getOrDefault(itemId, Map.of());
    }

    public List<String> problems() { return java.util.Collections.unmodifiableList(problems); }
}
