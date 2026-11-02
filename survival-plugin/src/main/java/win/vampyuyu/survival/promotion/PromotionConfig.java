package win.vampyuyu.survival.promotion;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import win.vampyuyu.survival.api.PlayerClass;
import win.vampyuyu.survival.api.PromotionApi;
import win.vampyuyu.survival.data.Db;
import win.vampyuyu.survival.data.Settings;

/**
 * The promotion tables and the {@code promotion.*} settings, validated. A bad row is skipped and listed in
 * {@link #problems()}.
 */
public final class PromotionConfig {
    /** One Adept specialization. */
    public record Spec(String id, PlayerClass vocation, String name, String summary, PromotionApi.Mods mods) {}

    /** One step of a chain. */
    public record Step(StepKind kind, String target, int amount, String text) {}

    /** One promotion chain: 3 to 5 steps. */
    public record Chain(String id, String rank, Profile profile, String title, String intro, List<Step> steps) {}

    private final Map<String, Spec> specs = new LinkedHashMap<>();
    private final Map<PlayerClass, PromotionApi.Ability> abilities = new EnumMap<>(PlayerClass.class);
    private final Map<String, Chain> chains = new LinkedHashMap<>();
    private final List<String> problems = new ArrayList<>();
    public boolean enabled = true;
    public String echoBoss = "voidwyrm";
    public int adeptBastionClears = 2;
    public int masterGuardians = 2;

    public static PromotionConfig load(Db db) {
        PromotionConfig c = new PromotionConfig();
        Settings s = Settings.load(db, "promotion");
        c.enabled = s.getBoolean("enabled", true);
        c.echoBoss = s.getString("adept.echo-boss", "voidwyrm");
        c.adeptBastionClears = Math.max(0, s.getInt("adept.bastion-clears", 2));
        c.masterGuardians = Math.max(0, s.getInt("master.guardians", 2));
        db.read(conn -> {
            for (Object[] r : Db.query(conn, "SELECT id, class, name, summary, vitality, might, dexterity, attunement, cooldown_pct, "
                + "cost_pct, power_pct FROM promotion_spec ORDER BY class, position, id",
                rs -> new Object[] {rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getDouble(5),
                    rs.getDouble(6), rs.getDouble(7), rs.getDouble(8), rs.getInt(9), rs.getInt(10), rs.getInt(11)})) {
                c.addSpec(r);
            }
            for (Object[] r : Db.query(conn, "SELECT class, name, summary, aether_cost, cooldown_seconds, radius, power, seconds "
                + "FROM promotion_ability",
                rs -> new Object[] {rs.getString(1), rs.getString(2), rs.getString(3), rs.getDouble(4), rs.getInt(5),
                    rs.getDouble(6), rs.getDouble(7), rs.getDouble(8)})) {
                c.addAbility(r);
            }
            Map<String, List<Step>> steps = new LinkedHashMap<>();
            for (Object[] r : Db.query(conn, "SELECT chain_id, kind, target, amount, text FROM promotion_step ORDER BY chain_id, position",
                rs -> new Object[] {rs.getString(1), rs.getString(2), rs.getString(3), rs.getInt(4), rs.getString(5)})) {
                StepKind kind = StepKind.parse(String.valueOf(r[1]));
                if (kind == null) {
                    c.problems.add("step of " + r[0] + " has the unknown kind " + r[1] + ", skipped");
                    continue;
                }
                steps.computeIfAbsent(String.valueOf(r[0]), k -> new ArrayList<>())
                    .add(new Step(kind, String.valueOf(r[2]).toLowerCase(Locale.ROOT), (Integer) r[3], String.valueOf(r[4])));
            }
            for (Object[] r : Db.query(conn, "SELECT id, rank, profile, title, intro FROM promotion_chain ORDER BY rank, id",
                rs -> new Object[] {rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5)})) {
                c.addChain(r, steps.getOrDefault(String.valueOf(r[0]), List.of()));
            }
            return null;
        });
        c.check();
        return c;
    }

    private void addSpec(Object[] r) {
        PlayerClass vocation = PlayerClass.parse(String.valueOf(r[1]));
        if (vocation == null) {
            problems.add("promotion_spec " + r[0] + " has the unknown class " + r[1] + ", skipped");
            return;
        }
        specs.put(String.valueOf(r[0]), new Spec(String.valueOf(r[0]), vocation, String.valueOf(r[2]), String.valueOf(r[3]),
            new PromotionApi.Mods((Double) r[4], (Double) r[5], (Double) r[6], (Double) r[7], (Integer) r[8], (Integer) r[9], (Integer) r[10])));
    }

    private void addAbility(Object[] r) {
        PlayerClass vocation = PlayerClass.parse(String.valueOf(r[0]));
        if (vocation == null) {
            problems.add("promotion_ability has the unknown class " + r[0] + ", skipped");
            return;
        }
        abilities.put(vocation, new PromotionApi.Ability(String.valueOf(r[1]), String.valueOf(r[2]), (Double) r[3], (Integer) r[4],
            (Double) r[5], (Double) r[6], (Double) r[7]));
    }

    private void addChain(Object[] r, List<Step> steps) {
        String id = String.valueOf(r[0]);
        Profile profile = Profile.parse(String.valueOf(r[2]));
        if (profile == null) {
            problems.add("chain " + id + " has the unknown profile " + r[2] + ", skipped");
            return;
        }
        if (steps.size() < 3 || steps.size() > 5) {
            problems.add("chain " + id + " has " + steps.size() + " steps (3 to 5 wanted), skipped");
            return;
        }
        chains.put(id, new Chain(id, String.valueOf(r[1]), profile, String.valueOf(r[3]), String.valueOf(r[4]), List.copyOf(steps)));
    }

    /** Reports what the data cannot do (GDD 11.5: two specializations per class, a chain for every rank). */
    private void check() {
        for (PlayerClass v : PlayerClass.values()) {
            long n = specs.values().stream().filter(s -> s.vocation() == v).count();
            if (n != 2) {
                problems.add(v + " has " + n + " specializations (2 wanted)");
            }
            if (!abilities.containsKey(v)) {
                problems.add(v + " has no Master ability");
            }
        }
        for (String rank : List.of("ADEPT", "MASTER")) {
            if (chains.values().stream().noneMatch(ch -> ch.rank().equals(rank))) {
                problems.add("no chain leads to " + rank);
            }
        }
    }

    public List<String> problems() { return problems; }

    public Optional<Spec> spec(String id) {
        return Optional.ofNullable(id == null ? null : specs.get(id));
    }

    /** The specializations of a class, in menu order. */
    public List<Spec> specsOf(PlayerClass vocation) {
        return specs.values().stream().filter(s -> s.vocation() == vocation).toList();
    }

    public Optional<PromotionApi.Ability> ability(PlayerClass vocation) {
        return Optional.ofNullable(abilities.get(vocation));
    }

    public Optional<Chain> chain(String id) {
        return Optional.ofNullable(id == null ? null : chains.get(id));
    }

    /** The chain to {@code rank} for this profile. */
    public Optional<Chain> chainFor(String rank, Profile profile) {
        return chains.values().stream().filter(c -> c.rank().equals(rank) && c.profile() == profile).findFirst();
    }

    public Map<String, Chain> chains() { return chains; }
}
