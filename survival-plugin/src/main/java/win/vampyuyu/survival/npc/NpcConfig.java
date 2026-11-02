package win.vampyuyu.survival.npc;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import win.vampyuyu.survival.data.Db;
import win.vampyuyu.survival.data.Settings;

/** The {@code npc_reaction} rows and the {@code npc.*} settings. A bad row is skipped and listed in {@link #problems()}. */
public final class NpcConfig {
    public boolean enabled = true;
    public int repMax = 100;
    public int visitGain = 1;
    public long visitCooldownMillis = 600_000;
    public int minPricePct = -10;
    public int maxPricePct = 10;
    private final List<NpcReaction> reactions = new ArrayList<>();
    private final List<String> problems = new ArrayList<>();

    public static NpcConfig load(Db db) {
        NpcConfig c = new NpcConfig();
        Settings s = Settings.load(db, "npc");
        c.enabled = s.getBoolean("enabled", true);
        c.repMax = Math.max(1, s.getInt("rep.max", 100));
        c.visitGain = s.getInt("rep.visit-gain", 1);
        c.visitCooldownMillis = Math.max(0, s.getLong("rep.visit-cooldown-seconds", 600)) * 1000;
        c.minPricePct = s.getInt("price.min-pct", -10);
        c.maxPricePct = s.getInt("price.max-pct", 10);
        db.read(conn -> {
            for (Object[] r : Db.query(conn, "SELECT id, npc, kind, fact, min_value, min_rep, max_rep, priority, price_pct, line "
                + "FROM npc_reaction ORDER BY id",
                rs -> new Object[] {rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getInt(5),
                    rs.getInt(6), rs.getInt(7), rs.getInt(8), rs.getInt(9), rs.getString(10)})) {
                try {
                    c.reactions.add(new NpcReaction((String) r[0], (String) r[1],
                        NpcReaction.Kind.valueOf(((String) r[2]).toUpperCase(Locale.ROOT)), (String) r[3], (int) r[4],
                        (int) r[5], (int) r[6], (int) r[7], (int) r[8], (String) r[9]));
                } catch (IllegalArgumentException e) {
                    c.problems.add("reaction " + r[0] + " has the unknown kind " + r[2] + ", skipped");
                }
            }
            return null;
        });
        c.problems.addAll(s.problems());
        return c;
    }

    public NpcRules rules() {
        return new NpcRules(reactions, minPricePct, maxPricePct);
    }

    public List<NpcReaction> reactions() { return List.copyOf(reactions); }

    public List<String> problems() { return List.copyOf(problems); }
}
