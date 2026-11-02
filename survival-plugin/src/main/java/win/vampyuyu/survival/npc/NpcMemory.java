package win.vampyuyu.survival.npc;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import win.vampyuyu.survival.api.NpcMemoryApi;
import win.vampyuyu.survival.data.Db;

/** The persisted memory (facts, relations, local state) and the {@link NpcMemoryApi} on top of it. Thread safe through {@link Db}. */
public final class NpcMemory implements NpcMemoryApi {
    private final Db db;
    private final java.util.function.LongSupplier clock;
    private volatile NpcConfig config;

    public NpcMemory(Db db, NpcConfig config, java.util.function.LongSupplier clock) {
        this.db = db;
        this.config = config;
        this.clock = clock;
    }

    public void reload(NpcConfig next) {
        this.config = next;
    }

    public NpcConfig config() { return config; }

    @Override
    public void recordFact(UUID player, String key, int value) {
        db.write(c -> Db.update(c, "INSERT INTO npc_fact (player, key, value, at) VALUES (?, ?, ?, ?) "
            + "ON CONFLICT (player, key) DO UPDATE SET value = MAX(value, excluded.value)",
            player.toString(), key, value, clock.getAsLong()));
    }

    @Override
    public int fact(UUID player, String key) {
        return db.read(c -> Db.query(c, "SELECT value FROM npc_fact WHERE player = ? AND key = ?", rs -> rs.getInt(1),
            player.toString(), key).stream().findFirst().orElse(0));
    }

    /** Every fact of a player. */
    public Map<String, Integer> facts(UUID player) {
        return db.read(c -> {
            Map<String, Integer> out = new HashMap<>();
            Db.query(c, "SELECT key, value FROM npc_fact WHERE player = ?", rs -> {
                out.put(rs.getString(1), rs.getInt(2));
                return null;
            }, player.toString());
            return out;
        });
    }

    @Override
    public void addRelation(UUID player, String npc, int delta) {
        int max = config.repMax;
        db.write(c -> Db.update(c, "INSERT INTO npc_relation (player, npc, rep) VALUES (?, ?, MAX(?, MIN(?, ?))) "
            + "ON CONFLICT (player, npc) DO UPDATE SET rep = MAX(?, MIN(?, rep + ?))",
            player.toString(), npc, -max, max, delta, -max, max, delta));
    }

    @Override
    public int relation(UUID player, String npc) {
        return db.read(c -> Db.query(c, "SELECT rep FROM npc_relation WHERE player = ? AND npc = ?", rs -> rs.getInt(1),
            player.toString(), npc).stream().findFirst().orElse(0));
    }

    @Override
    public Optional<String> greet(UUID player, String playerName, String npc) {
        NpcConfig cfg = config;
        if (!cfg.enabled) {
            return Optional.empty();
        }
        visit(player, npc, cfg);
        return cfg.rules().greeting(npc, playerName, facts(player), relation(player, npc));
    }

    /** A visit counts for the relation at most once per cooldown. */
    private void visit(UUID player, String npc, NpcConfig cfg) {
        long now = clock.getAsLong();
        long last = db.read(c -> Db.query(c, "SELECT last_seen FROM npc_relation WHERE player = ? AND npc = ?",
            rs -> rs.getLong(1), player.toString(), npc).stream().findFirst().orElse(-1L));
        boolean counts = last < 0 || now - last >= cfg.visitCooldownMillis;
        db.write(c -> Db.update(c, "INSERT INTO npc_relation (player, npc, rep, visits, last_seen) VALUES (?, ?, 0, 1, ?) "
            + "ON CONFLICT (player, npc) DO UPDATE SET visits = visits + 1, last_seen = ?",
            player.toString(), npc, now, now));
        if (counts && cfg.visitGain != 0) {
            addRelation(player, npc, cfg.visitGain);
        }
    }

    @Override
    public int priceDeltaPct(UUID player, String npc) {
        NpcConfig cfg = config;
        return cfg.enabled ? cfg.rules().priceDeltaPct(npc, facts(player), relation(player, npc)) : 0;
    }

    @Override
    public String state(String npc, String key) {
        return db.read(c -> Db.query(c, "SELECT value FROM npc_state WHERE npc = ? AND key = ?", rs -> rs.getString(1), npc, key)
            .stream().findFirst().orElse(""));
    }

    @Override
    public void setState(String npc, String key, String value) {
        db.write(c -> Db.update(c, "INSERT INTO npc_state (npc, key, value) VALUES (?, ?, ?) "
            + "ON CONFLICT (npc, key) DO UPDATE SET value = excluded.value", npc, key, value));
    }
}
