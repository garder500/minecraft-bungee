package win.vampyuyu.survival.resonance;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import win.vampyuyu.survival.api.Realm;
import win.vampyuyu.survival.data.Db;
import win.vampyuyu.survival.data.Settings;

/**
 * The {@code resonance.*} setting rows. {@code resonance.bastions} lists the Bastions as
 * {@code bossId|realmId|componentItem|baseShards} (a Bastion with no Realm yet leaves the realm id empty).
 */
public record ResonanceConfig(Map<String, Bastion> bastions, double rankBonus, double firstClearMultiplier,
        double defeatFraction, double minShare, long forgeCost, int weeklyForgeLimit, int pityRuns, int pityRunsHigh,
        int pityHighFrom, List<String> problems) {

    /** One boss with a Bastion. {@code realm} is null for the Echo (dungeon 0), which belongs to no Realm. */
    public record Bastion(String bossId, Realm realm, String component, long baseShards) {}

    public static ResonanceConfig load(Db db) {
        return parse(Settings.load(db, "resonance"));
    }

    public static ResonanceConfig parse(Settings s) {
        List<String> problems = new ArrayList<>(s.problems());
        Map<String, Bastion> bastions = new LinkedHashMap<>();
        for (String row : s.getList("bastions")) {
            String[] p = row.split("\\|", -1);
            if (p.length != 4 || p[0].isBlank() || p[2].isBlank()) {
                problems.add("resonance.bastions: bad row '" + row + "' (bossId|realmId|component|baseShards)");
                continue;
            }
            Realm realm = p[1].isBlank() ? null : Realm.byId(p[1].trim());
            if (!p[1].isBlank() && realm == null) {
                problems.add("resonance.bastions: unknown realm '" + p[1] + "'");
                continue;
            }
            long base;
            try {
                base = Long.parseLong(p[3].trim());
            } catch (NumberFormatException e) {
                problems.add("resonance.bastions: bad shard amount in '" + row + "'");
                continue;
            }
            bastions.put(p[0].trim(), new Bastion(p[0].trim(), realm, p[2].trim(), Math.max(0, base)));
        }
        return new ResonanceConfig(Map.copyOf(bastions), Math.max(0, s.getDouble("shards.rank-bonus", 0.25)),
            Math.max(1, s.getDouble("shards.first-clear-multiplier", 2)),
            Math.max(0, Math.min(1, s.getDouble("shards.defeat-fraction", 0.2))),
            Math.max(0, Math.min(1, s.getDouble("shards.min-share", 0.05))),
            Math.max(1, s.getLong("forge.cost", 100)), Math.max(0, s.getInt("forge.weekly-limit", 5)),
            Math.max(1, s.getInt("pity.runs", 4)), Math.max(1, s.getInt("pity.runs-high-rank", 3)),
            Math.max(1, Math.min(5, s.getInt("pity.high-rank-from", 3))), List.copyOf(problems));
    }

    /** Bastions in the order they were declared (Map.copyOf loses it, so callers sort by Realm). */
    public List<Bastion> ordered() {
        List<Bastion> list = new ArrayList<>(bastions.values());
        list.sort(java.util.Comparator.comparingInt(b -> b.realm() == null ? -1 : b.realm().ordinal()));
        return list;
    }
}
