package win.vampyuyu.survival.minions;

import static org.junit.Assert.assertEquals;

import org.junit.Test;
import win.vampyuyu.survival.data.Db;

/** The converted minions seed keeps what the former minions.yml held (counts measured on that file). */
public class MinionsSeedTest {
    private static int count(Db db, String table) {
        return db.read(c -> Db.query(c, "SELECT COUNT(*) FROM " + table, rs -> rs.getInt(1)).get(0));
    }

    @Test
    public void everyRowOfTheOldYamlIsInTheSeed() {
        try (Db db = TestConfig.seeded()) {
            assertEquals(22, count(db, "setting WHERE id GLOB 'minions.*'"));
            assertEquals(11, count(db, "minion_tier"));
            assertEquals(26, count(db, "minion_type"));
            assertEquals(39, count(db, "minion_drop"));
            assertEquals(9, count(db, "minion_fuel"));
            assertEquals(6, count(db, "minion_fuel_kind"));
            assertEquals(7, count(db, "minion_upgrade"));
            assertEquals(9, count(db, "minion_upgrade_lore"));
            assertEquals(10, count(db, "minion_skin"));
            assertEquals(13, count(db, "minion_recipe"));
            assertEquals(39, count(db, "minion_recipe_row"));
            assertEquals(25, count(db, "minion_recipe_ingredient"));
            assertEquals(13, count(db, "minion_smelt"));
        }
    }

    @Test
    public void theLoadedConfigurationHasTheOldValues() {
        MinionConfig c = TestConfig.load();
        assertEquals(26, c.types.size());
        assertEquals(9, c.fuels.size());
        assertEquals(7, c.upgrades.size());
        assertEquals(10, c.skins.size());
        assertEquals(13, c.itemRecipes.size());
        assertEquals(13, c.smelt.size());
        assertEquals(java.util.List.of(5, 15, 30, 50, 75, 100, 125, 150, 175, 200, 225, 250), c.slotThresholds);
        assertEquals(0.7, c.scale, 0);
        assertEquals(0.857, c.actionFactor[2], 0);
        assertEquals(org.bukkit.Material.NETHERITE_CHESTPLATE, c.chestplate[10]);
        assertEquals(org.bukkit.Material.GOLDEN_HELMET.name(), c.skins.get("golden_helmet").head().name());
        assertEquals(2, c.tierUp(8).amount());
        assertEquals(true, c.tierUp(8).superCompacted());
        assertEquals(8, c.tierUp(7).amount());
        assertEquals(false, c.tierUp(7).superCompacted());
        assertEquals(0.03, c.types.get("zombie").drops().get(1).chance(), 0);
        assertEquals(55, c.types.get("fishing").drops().get(0).chance(), 0);
    }
}
