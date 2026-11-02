package win.vampyuyu.survival.realm;

import java.util.Set;
import win.vampyuyu.survival.api.Realm;

/** One row of {@code realm_resource} with its sources. */
public record RealmResource(String id, Realm realm, String item, double p0, int soft, int dur, int amountMin, int amountMax,
                            Set<String> blocks, Set<String> mobs) {}
