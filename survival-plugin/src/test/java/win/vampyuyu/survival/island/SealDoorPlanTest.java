package win.vampyuyu.survival.island;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;
import win.vampyuyu.survival.api.Realm;
import win.vampyuyu.survival.depths.DepthsPlan;
import win.vampyuyu.survival.marsh.MarshPlan;
import win.vampyuyu.survival.realm.AshesPlan;

/** Every Realm's plan, built with the production block cap, holds its seal block where the plan says it is. */
class SealDoorPlanTest {
    private static final int CAP = 45000;

    private static void check(String label, BlockPlan plan, Realm realm, int[] seal) {
        assertEquals(SealDoor.palette(realm).seal(), plan.get(seal[0], seal[1], seal[2]), label + " seal block");
        assertArrayEquals(seal, plan.anchor("seal"), label + " anchor");
        // players stand in front of it (south, or west for the Depths): head room and a floor
        assertTrue(plan.get(seal[0], seal[1] + 1, seal[2]) != null, label + " has something above the seal");
        int floor = seal[1] - 2; // the seal is one above the standing level, the floor one under that
        for (int dx = -SealDoor.REACH; dx <= SealDoor.REACH; dx++) {
            assertTrue(plan.solidAt(seal[0] + dx, floor, seal[2]), label + " floor under the frame at " + dx);
        }
        for (int dz = 1; dz <= 2; dz++) {
            assertTrue(plan.solidAt(seal[0], floor, seal[2] + dz), label + " floor " + dz + " in front of the door");
        }
    }

    @Test
    void sealsAreInThePlans() {
        check("ashes", AshesPlan.build(), Realm.ASHES, AshesPlan.sealBlock());
        check("grove", GrovePlan.build(CAP), Realm.GROVE, GrovePlan.SEAL);
        check("heights", FrostPlan.build(CAP), Realm.HEIGHTS, FrostPlan.SEAL);
        check("citadel", CitadelPlan.build(CAP), Realm.CITADEL, CitadelPlan.SEAL);
        check("necropolis", NecropolisPlan.build(CAP), Realm.NECROPOLIS, NecropolisPlan.SEAL);
        check("marsh", MarshPlan.build(CAP), Realm.MARSHES, MarshPlan.sealBlock());
        BlockPlan depths = DepthsPlan.build();
        assertEquals(Material.SEA_LANTERN, depths.get(DepthsPlan.sealBlock()[0], DepthsPlan.sealBlock()[1], DepthsPlan.sealBlock()[2]));
    }

    @Test
    void theSignHangsOnThePillarAndNothingStandsInFrontOfTheSeal() {
        Map<Realm, BlockPlan> plans = Map.of(Realm.GROVE, GrovePlan.build(CAP), Realm.HEIGHTS, FrostPlan.build(CAP),
            Realm.CITADEL, CitadelPlan.build(CAP), Realm.NECROPOLIS, NecropolisPlan.build(CAP));
        int[][] seals = {GrovePlan.SEAL, FrostPlan.SEAL, CitadelPlan.SEAL, NecropolisPlan.SEAL};
        Realm[] realms = {Realm.GROVE, Realm.HEIGHTS, Realm.CITADEL, Realm.NECROPOLIS};
        for (int i = 0; i < realms.length; i++) {
            BlockPlan plan = plans.get(realms[i]);
            int[] s = seals[i];
            for (int dz = 1; dz <= 2; dz++) {
                for (int dy = -1; dy <= 3; dy++) {
                    Material m = plan.get(s[0], s[1] + dy, s[2] + dz);
                    assertTrue(m == null || m == Material.AIR, realms[i] + " front " + dz + "," + dy + " is " + m);
                }
            }
            assertNotNull(plan.get(s[0] + SealDoor.REACH, s[1], s[2] + 1), realms[i] + " sign");
            assertTrue(plan.signs().stream().anyMatch(t -> t.lines().get(2).endsWith(" Key")), realms[i] + " sign text");
        }
    }
}
