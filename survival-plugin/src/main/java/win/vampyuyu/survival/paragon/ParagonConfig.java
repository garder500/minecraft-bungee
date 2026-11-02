package win.vampyuyu.survival.paragon;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import win.vampyuyu.survival.api.PlayerClass;
import win.vampyuyu.survival.api.PromotionApi;
import win.vampyuyu.survival.data.Db;
import win.vampyuyu.survival.data.Settings;

/** The Paragon tables and {@code paragon.*} settings, validated. A bad row is skipped and listed in {@link #problems()}. */
public final class ParagonConfig {
    public enum Kind {
        TITLE, AURA, UTILITY, LORE;

        static Kind parse(String s) {
            try {
                return valueOf(s.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                return null;
            }
        }
    }

    /** One Transcendence rank and its reward. */
    public record Rank(int number, String name, int points, Kind kind, String key, int value, String text) {}

    /** The aura ids the game knows how to draw. */
    public static final Set<String> AURAS = Set.of("ether", "starlight", "interpulse");

    private final List<Rank> ranks = new ArrayList<>();
    private final Map<PlayerClass, PromotionApi.Ability> ultimates = new EnumMap<>(PlayerClass.class);
    private final List<String> problems = new ArrayList<>();
    public boolean enabled = true;
    public int astralClearPoints = 1;
    public int auraSeconds = 2;

    public static ParagonConfig load(Db db) {
        ParagonConfig c = new ParagonConfig();
        Settings s = Settings.load(db, "paragon");
        c.enabled = s.getBoolean("enabled", true);
        c.astralClearPoints = Math.max(0, s.getInt("astral-clear-points", 1));
        c.auraSeconds = Math.max(1, s.getInt("aura-seconds", 2));
        db.read(conn -> {
            for (Object[] r : Db.query(conn, "SELECT rank, name, points, reward_kind, reward_key, reward_value, reward_text "
                + "FROM paragon_rank ORDER BY rank",
                rs -> new Object[] {rs.getInt(1), rs.getString(2), rs.getInt(3), rs.getString(4), rs.getString(5), rs.getInt(6), rs.getString(7)})) {
                c.addRank(r);
            }
            for (Object[] r : Db.query(conn, "SELECT class, name, summary, aether_cost, cooldown_seconds, radius, power, seconds "
                + "FROM paragon_ultimate",
                rs -> new Object[] {rs.getString(1), rs.getString(2), rs.getString(3), rs.getDouble(4), rs.getInt(5),
                    rs.getDouble(6), rs.getDouble(7), rs.getDouble(8)})) {
                c.addUltimate(r);
            }
            return null;
        });
        c.check();
        return c;
    }

    private void addRank(Object[] r) {
        int number = (Integer) r[0];
        Kind kind = Kind.parse(String.valueOf(r[3]));
        if (kind == null) {
            problems.add("rank " + number + " has the unknown reward " + r[3] + ", skipped");
            return;
        }
        String key = String.valueOf(r[4]);
        if (kind == Kind.AURA && !AURAS.contains(key.toLowerCase(Locale.ROOT))) {
            problems.add("rank " + number + " has the unknown aura " + key + ", skipped");
            return;
        }
        if (kind == Kind.UTILITY && (Integer) r[5] < 1) {
            problems.add("rank " + number + " is a utility that adds nothing, skipped");
            return;
        }
        int points = (Integer) r[2];
        if (!ranks.isEmpty() && points <= ranks.get(ranks.size() - 1).points()) {
            problems.add("rank " + number + " needs " + points + " points, not more than the rank before, skipped");
            return;
        }
        ranks.add(new Rank(number, String.valueOf(r[1]), points, kind, kind == Kind.AURA ? key.toLowerCase(Locale.ROOT) : key,
            (Integer) r[5], String.valueOf(r[6])));
    }

    private void addUltimate(Object[] r) {
        PlayerClass vocation = PlayerClass.parse(String.valueOf(r[0]));
        if (vocation == null) {
            problems.add("paragon_ultimate has the unknown class " + r[0] + ", skipped");
            return;
        }
        ultimates.put(vocation, new PromotionApi.Ability(String.valueOf(r[1]), String.valueOf(r[2]), (Double) r[3], (Integer) r[4],
            (Double) r[5], (Double) r[6], (Double) r[7]));
    }

    private void check() {
        for (PlayerClass v : PlayerClass.values()) {
            if (!ultimates.containsKey(v)) {
                problems.add(v + " has no ultimate");
            }
        }
        if (ranks.isEmpty()) {
            problems.add("no Transcendence rank");
        } else if (ranks.get(0).points() > astralClearPoints) {
            problems.add("the first rank needs " + ranks.get(0).points() + " points but the Astral Interior pays " + astralClearPoints);
        }
    }

    public List<String> problems() { return problems; }

    /** The ranks in order. */
    public List<Rank> ranks() { return ranks; }

    public Optional<PromotionApi.Ability> ultimate(PlayerClass vocation) {
        return Optional.ofNullable(ultimates.get(vocation));
    }
}
