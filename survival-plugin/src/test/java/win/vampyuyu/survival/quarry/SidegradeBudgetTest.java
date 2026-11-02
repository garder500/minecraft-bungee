package win.vampyuyu.survival.quarry;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import win.vampyuyu.survival.api.Realm;
import win.vampyuyu.survival.api.Stat;
import win.vampyuyu.survival.core.CustomItemDef;
import win.vampyuyu.survival.data.Db;
import win.vampyuyu.survival.data.TestDb;
import win.vampyuyu.survival.items.catalog.ItemDefs;

/**
 * The coherence test of GDD 9.2: "if I replace the old item with the new one, is the player stronger or only
 * different?" The answer must be "different": the same points as the Starcore piece, redistributed within 20 % per stat.
 */
public class SidegradeBudgetTest {
    private static Db db;
    private static List<CustomItemDef> ladder;

    @BeforeClass
    public static void load() {
        MockBukkit.mock(); // item rows name vanilla materials
        db = TestDb.seeded();
        ladder = ItemDefs.load(db, "quarry", new ArrayList<>());
    }

    @AfterClass
    public static void unload() {
        MockBukkit.unmock();
    }

    private static CustomItemDef def(String id) {
        return ladder.stream().filter(d -> d.id().equals(id)).findFirst().orElseThrow(() -> new AssertionError("missing " + id));
    }

    private static double stat(CustomItemDef def, Stat stat) {
        return def.stats().getOrDefault(stat, 0.0);
    }

    private static double points(CustomItemDef def) {
        return def.stats().values().stream().mapToDouble(Double::doubleValue).sum();
    }

    @Test
    public void everySidegradePieceHasExactlyTheStarcorePointsAndNoStatMovesMoreThanTwentyPercent() {
        for (String set : Sidegrades.SETS) {
            for (String slot : Sidegrades.SLOTS) {
                CustomItemDef base = def("starcore_" + slot);
                CustomItemDef side = def(set + "_" + slot);
                assertEquals(set + " " + slot + " total points", points(base), points(side), 0.0001);
                for (Stat stat : Stat.values()) {
                    double was = stat(base, stat);
                    double now = stat(side, stat);
                    assertTrue(set + " " + slot + " " + stat + " " + was + " -> " + now, Math.abs(now - was) <= was * 0.20 + 1e-9);
                }
                assertEquals(5, side.tier().intValue());
                assertEquals(base.rarity(), side.rarity());
            }
        }
    }

    @Test
    public void aSidegradeSetIsNotStrongerOnAnyCapOfTheMetaBudget() {
        // GDD 8: the sets never raise Vitality (1 500 total), Ward (300) or Might (+150 %); a variant must not exceed Starcore
        for (String set : Sidegrades.SETS) {
            double might = 0;
            double ward = 0;
            double vitality = 0;
            double baseWard = 0;
            for (String slot : Sidegrades.SLOTS) {
                might += stat(def(set + "_" + slot), Stat.MIGHT);
                ward += stat(def(set + "_" + slot), Stat.WARD);
                vitality += stat(def(set + "_" + slot), Stat.VITALITY);
                baseWard += stat(def("starcore_" + slot), Stat.WARD);
            }
            assertTrue(set + " Ward " + ward, ward <= baseWard);
            assertTrue(set + " Might " + might, might <= 150);
            assertTrue(set + " Vitality " + vitality, vitality <= 1500 - 100);
            assertEquals(set + " is not a second set of the same piece", 4, Sidegrades.SLOTS.stream()
                .filter(slot -> def(set + "_" + slot).equippable() != null).count());
        }
    }

    @Test
    public void theAffinitiesAreFunctionalAndSmall() {
        double heat = 0;
        for (String slot : Sidegrades.SLOTS) {
            heat += def("cinder_starcore_" + slot).pdcDoubles().getOrDefault("heat_resist", 0.0);
            assertFalse(def("grove_starcore_" + slot).pdcDoubles().containsKey("heat_resist"));
        }
        // the Ashes ask 75 for the caldera and a netherite set gives 48: the set closes the gap, it does not make it moot
        assertEquals(30.0, heat, 0);
        assertTrue(def("grove_starcore_helmet").pdcDoubles().containsKey("respiration"));
        assertFalse(def("grove_starcore_boots").pdcDoubles().containsKey("respiration"));
    }

    @Test
    public void everyPieceNeedsItsStarcorePieceAndTheLocalResourceOfItsRealm() {
        for (String set : Sidegrades.SETS) {
            String resource = set.equals(Sidegrades.CINDER) ? "custom:cinderheart" : "custom:heartwood";
            for (String slot : Sidegrades.SLOTS) {
                String id = set + "_" + slot;
                assertEquals(id + " needs its Starcore piece", 1, TestDb.count(db,
                    "SELECT COUNT(*) FROM forge_recipe_ingredient WHERE forge_recipe_id = ? AND item = ?", id, "custom:starcore_" + slot));
                assertEquals(id + " needs the local resource", 1, TestDb.count(db,
                    "SELECT COUNT(*) FROM forge_recipe_ingredient WHERE forge_recipe_id = ? AND item = ?", id, resource));
                assertEquals(id + " result", 1, TestDb.count(db, "SELECT COUNT(*) FROM forge_recipe WHERE id = ? AND result = ?", id, id));
            }
        }
    }

    @Test
    public void setsAreRecognisedByTheirFourDifferentPieces() {
        assertEquals(Sidegrades.CINDER, Sidegrades.fullSet(Arrays.asList("cinder_starcore_boots", "cinder_starcore_leggings",
            "cinder_starcore_chestplate", "cinder_starcore_helmet")));
        assertNull(Sidegrades.fullSet(Arrays.asList("cinder_starcore_boots", "cinder_starcore_leggings",
            "cinder_starcore_chestplate", "grove_starcore_helmet")));
        assertNull(Sidegrades.fullSet(Arrays.asList("cinder_starcore_boots", "cinder_starcore_leggings",
            "cinder_starcore_chestplate", null)));
        assertNull(Sidegrades.fullSet(Arrays.asList("starcore_boots", "starcore_leggings", "starcore_chestplate", "starcore_helmet")));
        assertEquals(Realm.ASHES, Sidegrades.realmOf("cinder_starcore_helmet"));
        assertEquals(Realm.GROVE, Sidegrades.realmOf("grove_starcore_boots"));
        assertNull(Sidegrades.realmOf("starcore_helmet"));
    }

    @Test
    public void ashenMantleHalvesFireOnlyAndVerdantMendingWaitsForCalm() {
        assertEquals(0.5, Sidegrades.fireFactor(Sidegrades.CINDER, DamageCause.LAVA), 0);
        assertEquals(0.5, Sidegrades.fireFactor(Sidegrades.CINDER, DamageCause.HOT_FLOOR), 0);
        assertEquals(1.0, Sidegrades.fireFactor(Sidegrades.CINDER, DamageCause.ENTITY_ATTACK), 0);
        assertEquals(1.0, Sidegrades.fireFactor(Sidegrades.GROVE, DamageCause.FIRE), 0);
        assertEquals(1.0, Sidegrades.fireFactor(null, DamageCause.FIRE), 0);
        assertTrue(Sidegrades.mends(Sidegrades.GROVE, 0, 100_000));
        assertFalse(Sidegrades.mends(Sidegrades.GROVE, 95_000, 100_000));
        assertFalse(Sidegrades.mends(Sidegrades.CINDER, 0, 100_000));
    }
}
