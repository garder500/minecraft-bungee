package win.vampyuyu.survival.data;

import static org.junit.Assert.assertEquals;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;

/** The converted boss and dungeon seeds keep the counts of the former boss.yml (762 lines) and dungeon.yml (162 lines). */
public class BossSeedTest {
    private static long count(Db db, String table, String where) {
        return db.read(c -> Db.query(c, "SELECT COUNT(*) FROM " + table + (where.isEmpty() ? "" : " WHERE " + where),
            rs -> rs.getLong(1)).get(0));
    }

    @Test
    public void bossSeedKeepsTheCountsOfTheYaml() {
        Db db = Db.memory();
        Seeds.sync(db, List.of(ContentDomains.BOSS), null);
        Map<String, Integer> expected = new LinkedHashMap<>();
        expected.put("boss", 6);
        expected.put("boss_equipment", 10);
        expected.put("boss_phase", 18);
        expected.put("boss_ability", 58);
        expected.put("boss_ability_param", 127);
        expected.put("boss_rank_bonus", 18);
        expected.put("boss_drop", 22);
        expected.put("boss_summon", 5);
        expected.put("boss_summon_world", 5);
        expected.put("slayer", 5);
        expected.put("slayer_target", 9);
        expected.put("slayer_world", 9);
        expected.put("slayer_level", 45);
        expected.put("slayer_perk", 45);
        expected.put("slayer_tier", 25);
        expected.put("slayer_equipment", 3);
        expected.put("slayer_ability", 15);
        expected.put("slayer_ability_param", 26);
        expected.put("slayer_rank_bonus", 15);
        expected.put("slayer_drop", 15);
        expected.put("boss_recipe", 5);
        expected.put("boss_recipe_row", 15);
        expected.put("boss_recipe_ingredient", 15);
        expected.put("boss_mob_drop", 0);
        expected.put("boss_item_text", 0);
        expected.put("boss_world_spot", 2);
        expected.put("boss_world_pool", 0);
        expected.forEach((table, rows) -> assertEquals(table, rows.longValue(), count(db, table, "")));
        // 16 settings (two of them the target seconds of the power curve) + 5 slayer settings + 9 world settings (one of them a list of 3)
        assertEquals(30, count(db, "setting", "id GLOB 'boss.*'"));
        assertEquals(3, count(db, "setting_item", "setting_id = 'boss.world.announce-before-seconds'"));
    }

    @Test
    public void dungeonSeedKeepsTheCountsOfTheYaml() {
        Db db = Db.memory();
        Seeds.sync(db, List.of(ContentDomains.DUNGEON), null);
        assertEquals(7, count(db, "dungeon_floor", ""));
        assertEquals(6, count(db, "dungeon_grade", ""));
        assertEquals(11, count(db, "dungeon_reward", ""));
        assertEquals(7, count(db, "dungeon_trade", ""));
        assertEquals(11, count(db, "dungeon_trade_cost", ""));
        assertEquals(52, count(db, "setting", "id GLOB 'dungeon.*'"));
        assertEquals(11, count(db, "setting_item", "setting_id = 'dungeon.rules.allowed-commands'"));
    }

    @Test
    public void everyPityInTheSeedIsTheGentleOne() {
        Db db = Db.memory();
        Seeds.sync(db, List.of(ContentDomains.BOSS), null);
        for (String table : List.of("boss_drop", "slayer_drop")) {
            assertEquals(table, 0, count(db, table, "pity_cap <> 0.015"));
            assertEquals(table, 0, count(db, table, "pity <> 0 AND pity <> 0.001"));
        }
        assertEquals(6, count(db, "boss_drop", "pity = 0.001"));
        assertEquals(5, count(db, "slayer_drop", "pity = 0.001"));
    }

    @Test
    public void bossTiersAreFilled() {
        Db db = Db.memory();
        Seeds.sync(db, List.of(ContentDomains.BOSS), null);
        assertEquals(0, count(db, "boss", "tier IS NULL"));
        assertEquals(1, count(db, "boss", "id = 'crypt_lord' AND tier = 1"));
        assertEquals(1, count(db, "boss", "id = 'voidwyrm' AND tier = 5"));
    }
}
