package win.vampyuyu.survival.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import win.vampyuyu.survival.api.Element;
import win.vampyuyu.survival.api.Stat;
import win.vampyuyu.survival.core.CustomItemDef;
import win.vampyuyu.survival.items.ItemsSettings;
import win.vampyuyu.survival.items.catalog.ArmorSet;
import win.vampyuyu.survival.items.catalog.ArmorSets;
import win.vampyuyu.survival.items.catalog.ItemDefs;

/** The items domain: what the former items.yml and the hard-coded catalog became (counts, stats, elements, ladder). */
public class ItemsSeedTest {
    private static Db db;

    @BeforeClass
    public static void load() {
        MockBukkit.mock(); // item rows name vanilla materials
        db = TestDb.seeded();
    }

    @AfterClass
    public static void unload() {
        MockBukkit.unmock();
    }

    private static long count(String table) {
        return TestDb.count(db, "SELECT COUNT(*) FROM " + table);
    }

    @Test
    public void onlyTheOreLadderIsLeftTheInactiveCatalogIsGone() {
        assertEquals(40, count("item_def"));
        assertEquals(0, TestDb.count(db, "SELECT COUNT(*) FROM item_def WHERE owner = 'items'"));
        assertEquals(40, TestDb.count(db, "SELECT COUNT(*) FROM item_def WHERE owner = 'quarry'"));
        assertEquals(0, count("armor_set"));
        assertEquals(13, count("item_mob"));
        assertEquals(0, count("item_recipe"));
        assertEquals(0, count("item_loot"));
        assertTrue(TestDb.count(db, "SELECT COUNT(*) FROM setting WHERE id GLOB 'items.*'") > 90);
    }

    @Test
    public void everyItemRowBuildsADefinition() {
        List<String> problems = new ArrayList<>();
        List<CustomItemDef> items = ItemDefs.load(db, "items", problems);
        List<CustomItemDef> ladder = ItemDefs.load(db, "quarry", problems);
        assertEquals(problems.toString(), 0, problems.size());
        assertEquals(0, items.size());
        assertEquals(40, ladder.size());
    }

    @Test
    public void theLadderHasTheDamageElementTierAndArmorStats() {
        List<CustomItemDef> ladder = ItemDefs.load(db, "quarry", new ArrayList<>());
        Map<String, double[]> swords = Map.of("bronze_cleaver", new double[] {40, 1}, "rimeblade", new double[] {70, 2},
            "ember_sword", new double[] {120, 3}, "void_sword", new double[] {200, 4}, "starcore_sword", new double[] {320, 5});
        Map<String, Element> elements = Map.of("bronze_cleaver", Element.EARTH, "rimeblade", Element.FROST,
            "ember_sword", Element.EMBER, "void_sword", Element.VOID, "starcore_sword", Element.ASTRAL);
        swords.forEach((id, row) -> {
            CustomItemDef def = ladder.stream().filter(d -> d.id().equals(id)).findFirst().orElseThrow();
            assertEquals(id, row[0], def.pdcDoubles().get("damage"), 0);
            assertEquals(id, (int) row[1], def.tier().intValue());
            assertEquals(id, elements.get(id), def.element());
        });
        double last = 0;
        for (String tier : List.of("bronze", "rime", "ember", "void", "starcore")) {
            double might = 0;
            double ward = 0;
            double vitality = 0;
            for (String slot : List.of("helmet", "chestplate", "leggings", "boots")) {
                CustomItemDef piece = ladder.stream().filter(d -> d.id().equals(tier + "_" + slot)).findFirst().orElseThrow();
                assertNotNull(piece.equippable());
                might += piece.stats().getOrDefault(Stat.MIGHT, 0.0);
                ward += piece.stats().getOrDefault(Stat.WARD, 0.0);
                vitality += piece.stats().getOrDefault(Stat.VITALITY, 0.0);
            }
            assertTrue(tier + " armor grows with the tier", might > last);
            assertTrue(ward > 0 && vitality > 0);
            last = might;
        }
    }

    @Test
    public void noItemKeepsAnOldStatName() {
        for (String stat : TestDb.strings(db, "SELECT DISTINCT stat FROM item_stat UNION SELECT DISTINCT stat FROM armor_set_stat")) {
            assertNotNull(stat, Stat.parse(stat));
        }
    }

    @Test
    public void weaponsOfTheCatalogStayInsideTheLadder() {
        List<CustomItemDef> items = ItemDefs.load(db, "items", new ArrayList<>());
        for (CustomItemDef def : items) {
            Double damage = def.pdcDoubles().get("damage");
            if (damage == null || def.typeLabel() == null || !def.typeLabel().equals("SWORD")) {
                continue;
            }
            double effective = damage * (1 + def.stats().getOrDefault(Stat.MIGHT, 0.0) / 100.0);
            assertTrue(def.id() + " hits for " + effective + ", more than the tier 4 sword with its armor",
                effective < 200 * 1.56);
        }
    }

    @Test
    public void settingsReadBackWithTheirTypes() {
        ItemsSettings s = ItemsSettings.load(db);
        assertEquals(0.0, s.damageBonus, 0);
        assertEquals(5.0, s.incomingScale, 0);
        assertEquals(5, (int) s.surgeEvery);
        assertEquals(2.0, s.surgeMultiplier, 0);
        assertEquals(1.5, s.elementWeakness, 0);
        assertEquals(0.6, s.elementResistance, 0);
        assertEquals(12.5, s.weaponCooldownTicks, 0);
        assertEquals(0.6, s.perMagicalPower.get(Stat.VITALITY), 0);
        assertTrue(s.heldTypes.contains("SWORD"));
        assertEquals(13, s.mobTable.size());
        assertEquals("EMBER", s.mobTable.get("ZOMBIE").weakness());
        assertEquals(2, s.d("hot-potato.weapon.might", 0), 0);
        assertEquals(0, s.lootEntries.size());
        assertEquals(0, s.recipeDefs.size());
    }

    @Test
    public void aPanelEditedRowSurvivesAReseed() {
        Db own = Db.memory();
        Seeds.sync(own, List.of(ContentDomains.ITEMS), null);
        TestDb.exec(own, "UPDATE item_def SET damage = 999, origin = 'panel' WHERE id = 'bronze_cleaver'");
        TestDb.exec(own, "DELETE FROM seed_state WHERE domain = 'items'");
        Seeds.sync(own, List.of(ContentDomains.ITEMS), null);
        assertEquals(999.0, own.read(c -> Db.query(c, "SELECT damage FROM item_def WHERE id = 'bronze_cleaver'",
            rs -> rs.getDouble(1)).get(0)), 0);
        assertEquals(70.0, own.read(c -> Db.query(c, "SELECT damage FROM item_def WHERE id = 'rimeblade'",
            rs -> rs.getDouble(1)).get(0)), 0);
        own.close();
    }
}
