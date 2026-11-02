package win.vampyuyu.survival.realm;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;
import java.util.logging.Logger;
import win.vampyuyu.survival.api.Realm;
import win.vampyuyu.survival.data.Db;

/** Reads {@code realm_world} and {@code realm_resource*}. A broken row is logged and skipped. */
public final class RealmRepo {
    public record Loaded(Map<String, Realm> worlds, Map<String, RealmResource> resources) {}

    private RealmRepo() {}

    public static Loaded load(Db db, Logger log) {
        return db.read(c -> {
            Map<String, Realm> worlds = new HashMap<>();
            for (String[] r : Db.query(c, "SELECT world, realm FROM realm_world", rs -> new String[] {rs.getString(1), rs.getString(2)})) {
                Realm realm = Realm.byId(r[1]);
                if (realm == null) {
                    log.warning("realm: world " + r[0] + ": unknown realm " + r[1] + "; skipped");
                } else {
                    worlds.put(r[0], realm);
                }
            }
            Map<String, java.util.Set<String>> blocks = new HashMap<>();
            Map<String, java.util.Set<String>> mobs = new HashMap<>();
            for (String[] r : Db.query(c, "SELECT resource_id, kind, key FROM realm_resource_source",
                    rs -> new String[] {rs.getString(1), rs.getString(2), rs.getString(3)})) {
                (r[1].equals("block") ? blocks : mobs).computeIfAbsent(r[0], k -> new TreeSet<>()).add(r[2].toUpperCase(Locale.ROOT));
            }
            Map<String, RealmResource> out = new LinkedHashMap<>();
            for (Object[] r : Db.query(c, "SELECT id, realm, item, p0, soft, dur, amount_min, amount_max FROM realm_resource ORDER BY id",
                    rs -> new Object[] {rs.getString(1), rs.getString(2), rs.getString(3), rs.getDouble(4), rs.getInt(5),
                        rs.getInt(6), rs.getInt(7), rs.getInt(8)})) {
                String id = (String) r[0];
                Realm realm = Realm.byId((String) r[1]);
                if (realm == null) {
                    log.warning("realm: resource " + id + ": unknown realm " + r[1] + "; skipped");
                    continue;
                }
                out.put(id, new RealmResource(id, realm, (String) r[2], (Double) r[3], (Integer) r[4], (Integer) r[5],
                    (Integer) r[6], (Integer) r[7], blocks.getOrDefault(id, java.util.Set.of()), mobs.getOrDefault(id, java.util.Set.of())));
            }
            return new Loaded(worlds, out);
        });
    }
}
