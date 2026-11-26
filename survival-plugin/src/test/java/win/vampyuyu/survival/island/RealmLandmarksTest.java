package win.vampyuyu.survival.island;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.EnumMap;
import java.util.Map;
import org.junit.Test;
import win.vampyuyu.survival.api.Realm;
import win.vampyuyu.survival.depths.DepthsPlan;
import win.vampyuyu.survival.marsh.MarshPlan;
import win.vampyuyu.survival.realm.AshesPlan;
import win.vampyuyu.survival.realm.StormPlan;

/** Every Realm plan carries its floating monument and relic, out of the landing's way. */
public class RealmLandmarksTest {
    private static Map<Realm, BlockPlan> plans() {
        Map<Realm, BlockPlan> all = new EnumMap<>(Realm.class);
        all.put(Realm.ASHES, AshesPlan.build());
        all.put(Realm.GROVE, GrovePlan.build(45000));
        all.put(Realm.HEIGHTS, FrostPlan.build(45000));
        all.put(Realm.CITADEL, CitadelPlan.build(45000));
        all.put(Realm.MARSHES, MarshPlan.build(45000));
        all.put(Realm.ARCHIPELAGO, StormPlan.build());
        all.put(Realm.DEPTHS, DepthsPlan.build());
        all.put(Realm.NECROPOLIS, NecropolisPlan.build(45000));
        return all;
    }

    @Test
    public void everyRealmHasItsMonumentAndRelic() {
        for (var e : plans().entrySet()) {
            Realm realm = e.getKey();
            BlockPlan plan = e.getValue();
            RealmLandmarks.Site s = RealmLandmarks.site(realm);
            assertNotNull(realm + " monument top", plan.get(s.x(), s.y(), s.z()));
            long relics = plan.models().stream().filter(m -> m.item().equals(RealmLandmarks.relic(realm).item())).count();
            assertEquals(realm + " relic", 1, relics);
            int[] spawn = plan.anchor("spawn");
            assertNotNull(realm + " spawn", spawn);
            assertTrue(realm + " floats well above the landing", s.y() - 14 > spawn[1] + 8);
        }
    }

    @Test
    public void eachRelicHasItsOwnModel() {
        java.util.Set<String> items = new java.util.HashSet<>();
        for (Realm realm : Realm.values()) {
            assertTrue(items.add(RealmLandmarks.relic(realm).item()));
        }
    }

    @Test
    public void everyArrivalIsAPavedLitPlazaWithItsRayTowardTheArch() {
        for (var e : plans().entrySet()) {
            Realm realm = e.getKey();
            BlockPlan plan = e.getValue();
            int[] sp = plan.anchor("spawn");
            RealmLandmarks.Plaza p = RealmLandmarks.plaza(realm);
            int inlays = 0;
            for (int dx = -RealmLandmarks.PLAZA_RADIUS; dx <= RealmLandmarks.PLAZA_RADIUS; dx++) {
                for (int dz = -RealmLandmarks.PLAZA_RADIUS; dz <= RealmLandmarks.PLAZA_RADIUS; dz++) {
                    if (plan.get(sp[0] + dx, sp[1] - 1, sp[2] + dz) == p.inlay()) {
                        inlays++;
                    }
                }
            }
            assertTrue(realm + " glowing stones in the plaza: " + inlays, inlays >= 4);
            assertTrue(realm + " the spawn still stands on a floor", plan.solidAt(sp[0], sp[1] - 1, sp[2]));
        }
    }
}
