package win.vampyuyu.survival.boss;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import win.vampyuyu.survival.data.Db.DataException;
import win.vampyuyu.survival.data.Db;
import win.vampyuyu.survival.data.Settings;

/** The checks run on boss rows: what the schema refuses at insert time and what the reader reports and drops. */
public class DefinitionParserTest {
    private Db db;

    @Before
    public void server() {
        MockBukkit.mock();
        db = Db.memory();
    }

    @After
    public void stop() {
        db.close();
        MockBukkit.unmock();
    }

    private void exec(String... statements) {
        for (String sql : statements) {
            db.write(c -> {
                try (var st = c.createStatement()) {
                    st.execute(sql);
                }
                return null;
            });
        }
    }

    private BossConfig load(String... statements) {
        exec(statements);
        return BossConfig.load(db);
    }

    private static String boss(String id, String entity, String extraColumns, String extraValues) {
        return "INSERT INTO boss (id, name, entity, health" + extraColumns + ") VALUES ('" + id + "', '<red>" + id + "', '"
            + entity + "', 1000" + extraValues + ")";
    }

    private static String boss(String id, String entity) {
        return boss(id, entity, "", "");
    }

    private static String phase(String boss, int position, String name, double at) {
        return "INSERT INTO boss_phase (boss_id, position, name, at) VALUES ('" + boss + "', " + position + ", '" + name + "', " + at + ")";
    }

    private static String ability(String boss, int phase, String role, int position, String type, String extraColumns, String extraValues) {
        return "INSERT INTO boss_ability (boss_id, phase, role, position, type" + extraColumns + ") VALUES ('" + boss + "', " + phase
            + ", '" + role + "', " + position + ", '" + type + "'" + extraValues + ")";
    }

    private static String drop(String boss, int position, String item, String extraColumns, String extraValues) {
        return "INSERT INTO boss_drop (boss_id, position, item" + extraColumns + ") VALUES ('" + boss + "', " + position + ", '"
            + item + "'" + extraValues + ")";
    }

    private void refused(String sql) {
        try {
            exec(sql);
            fail("the schema should refuse: " + sql);
        } catch (DataException expected) {
            // the CHECK constraint did its job
        }
    }

    @Test
    public void minimalBossGetsDefaults() {
        BossConfig c = load(boss("tester", "ZOMBIE"));
        assertEquals(List.of(), c.errors());
        BossDefinition d = c.bosses().get("tester");
        assertEquals(1, d.phases().size());
        assertEquals(1.0, d.phases().get(0).at(), 0);
        assertEquals(1.0, d.knockbackResistance(), 0);
        assertTrue(d.arena().noBuild());
        assertNull(d.summon());
        assertSame(d, c.definition("tester").orElseThrow());
        assertTrue(c.definition("ghost").isEmpty());
    }

    @Test
    public void rejectsUnknownEntityAndNonLiving() {
        BossConfig c = load(boss("a", "NOT_A_MOB"), boss("b", "ARROW"), boss("c", "PLAYER"));
        assertTrue(c.bosses().isEmpty());
        assertEquals(3, c.errors().size());
    }

    @Test
    public void schemaRefusesBadIdAndNonsenseNumbers() {
        refused(boss("Bad-Id", "ZOMBIE"));
        refused("INSERT INTO boss (id, name, entity, health) VALUES ('neg', 'x', 'ZOMBIE', -5)");
        refused(boss("t", "ZOMBIE", ", tier", ", 9"));
        refused(boss("k", "ZOMBIE", ", kind", ", 'slayer'"));
    }

    @Test
    public void rejectsRangesTheSchemaCannotExpress() {
        BossConfig c = load(boss("huge", "ZOMBIE", ", scale", ", 99"), boss("fast", "ZOMBIE", ", speed", ", 50"),
            boss("kb", "ZOMBIE", ", knockback_resistance", ", 2"), boss("ok", "ZOMBIE"));
        assertEquals(List.of("ok"), new ArrayList<>(c.bosses().keySet()));
        assertEquals(3, c.errors().size());
    }

    @Test
    public void phaseThresholdsMustDecrease() {
        BossConfig c = load(boss("p", "ZOMBIE"), phase("p", 0, "A", 1.0), phase("p", 1, "B", 0.5), phase("p", 2, "C", 0.7));
        assertFalse(c.errors().isEmpty());
        assertNull("a definition with a bad phase is dropped", c.bosses().get("p"));
    }

    @Test
    public void rejectsUnknownAbilityAndBadValues() {
        BossConfig c = load(boss("p", "ZOMBIE"), phase("p", 0, "A", 1.0),
            ability("p", 0, "cast", 0, "fly_to_the_moon", "", ""),
            ability("p", 0, "cast", 1, "ground_slam", ", cooldown", ", 0.1"),
            ability("p", 0, "cast", 2, "ground_slam", ", telegraph", ", 99"));
        assertNull(c.bosses().get("p"));
        assertTrue(c.errors().size() >= 3);
        assertTrue(c.errors().get(0).contains("unknown ability"));
        refused(ability("p", 0, "cast", 3, "ground_slam", ", target", ", 'everyone'"));
    }

    @Test
    public void abilityParametersAndAliasesParse() {
        BossConfig c = load(boss("p", "ZOMBIE"), phase("p", 0, "Fight", 1.0),
            ability("p", 0, "cast", 0, "ground-slam", ", cooldown, telegraph", ", 10, 1.5"),
            "INSERT INTO boss_ability_param (boss_id, phase, role, ability, name, num) VALUES ('p', 0, 'cast', 0, 'radius', 12)",
            "INSERT INTO boss_ability_param (boss_id, phase, role, ability, name, text) VALUES ('p', 0, 'cast', 0, 'flag', 'true')",
            "INSERT INTO boss_ability_param (boss_id, phase, role, ability, name, text) VALUES ('p', 0, 'cast', 0, 'label', 'x')");
        assertEquals(List.of(), c.errors());
        AbilitySpec spec = c.bosses().get("p").phases().get(0).abilities().get(0);
        assertEquals(AbilityType.GROUND_SLAM, spec.type());
        assertEquals(200, spec.cooldownTicks());
        assertEquals(30, spec.telegraphTicks());
        assertEquals(12, spec.num("radius", 0), 0);
        assertEquals(7, spec.integer("missing", 7));
        assertTrue(spec.bool("flag", false));
        assertEquals("x", spec.str("label", ""));
        assertEquals(60, spec.seconds("missing", 3));
        refused("INSERT INTO boss_ability_param (boss_id, phase, role, ability, name) VALUES ('p', 0, 'cast', 0, 'empty')");
    }

    @Test
    public void lootEntriesAreValidated() {
        BossConfig c = load(boss("a", "ZOMBIE"), drop("a", 0, "custom:good_one", ", chance", ", 0.5"),
            drop("a", 1, "vanilla:DIAMOND", ", chance, min_amount, max_amount", ", 1, 2, 4"),
            drop("a", 2, "weird", ", chance", ", 1"), drop("a", 3, "vanilla:NOT_AN_ITEM", ", chance", ", 1"));
        assertNull("a boss with a broken drop is dropped, never half loaded", c.bosses().get("a"));
        assertEquals(2, c.errors().size());
        refused(drop("a", 4, "custom:x", ", chance", ", 3"));
        refused(drop("a", 5, "custom:y", ", chance, min_amount, max_amount", ", 1, 5, 2"));
        exec("DELETE FROM boss_drop WHERE position >= 2");
        BossConfig ok = BossConfig.load(db);
        assertEquals(2, ok.bosses().get("a").loot().entries().size());
    }

    @Test
    public void lootPityDefaultsToNoneAndAGentleCap() {
        BossConfig c = load(boss("a", "ZOMBIE"), drop("a", 0, "custom:x", ", chance", ", 0.05"));
        var entry = c.bosses().get("a").loot().entries().get(0);
        assertEquals(0, entry.pity(), 0);
        assertEquals(0.015, entry.pityCap(), 1e-12);
    }

    @Test
    public void equipmentColoursAndMaterials() {
        BossConfig c = load(boss("a", "ZOMBIE"),
            "INSERT INTO boss_equipment (boss_id, slot, material, color) VALUES ('a', 'head', 'LEATHER_HELMET', 1056816)",
            "INSERT INTO boss_equipment (boss_id, slot, material, color) VALUES ('a', 'chest', 'LEATHER_CHESTPLATE', 99999999)",
            "INSERT INTO boss_equipment (boss_id, slot, material) VALUES ('a', 'feet', 'NOPE')");
        // the two bad lines produce errors and the whole definition is dropped
        assertNull(c.bosses().get("a"));
        assertEquals(2, c.errors().size());
        refused("INSERT INTO boss_equipment (boss_id, slot, material) VALUES ('a', 'tail', 'STICK')");
        exec("DELETE FROM boss_equipment WHERE slot <> 'head'");
        assertEquals(0x102030, (int) BossConfig.load(db).bosses().get("a").equipment().get("head").color());
    }

    @Test
    public void altarModeNeedsACoordinate() {
        BossConfig c = load(boss("a", "ZOMBIE"), boss("b", "ZOMBIE"),
            "INSERT INTO boss_summon (boss_id, item, mode) VALUES ('a', 'some_item', 'altar')",
            "INSERT INTO boss_summon (boss_id, item, mode, altar_world, altar_x, altar_y, altar_z, altar_radius) "
                + "VALUES ('b', 'some_item', 'altar', 'sb_end', 1, 2, 3, 9)");
        assertNull(c.bosses().get("a"));
        assertEquals(9, c.bosses().get("b").summon().altar().radius(), 0);
        refused("INSERT INTO boss_summon (boss_id, item, mode) VALUES ('b', 'some_item', 'teleport')");
    }

    @Test
    public void arenaRangesAreChecked() {
        BossConfig c = load(boss("a", "ZOMBIE", ", arena_radius", ", 0"), boss("b", "ZOMBIE", ", arena_radius, arena_leash", ", 30, 10"),
            boss("d", "ZOMBIE", ", arena_max_minions", ", 500"), boss("e", "ZOMBIE", ", arena_radius, arena_leash", ", 30, 40"));
        assertEquals(List.of("e"), new ArrayList<>(c.bosses().keySet()));
    }

    @Test
    public void worldBossPoolMustReferenceKnownBosses() {
        exec(boss("tester", "ZOMBIE"),
            "INSERT INTO setting (id, type, value) VALUES ('boss.world.enabled', 'bool', 'true')",
            "INSERT INTO boss_world_spot (id, position, world, x, y, z, name) VALUES ('s', 0, 'sb_hub', 1, 2, 3, 'Spot')",
            "INSERT INTO boss_world_pool (boss_id, weight) VALUES ('tester', 2)",
            "INSERT INTO boss_world_pool (boss_id, weight) VALUES ('ghost', 1)");
        BossConfig c = BossConfig.load(db);
        assertTrue(c.worldBoss().enabled());
        assertEquals(1, c.worldBoss().pool().size());
        assertEquals(1, c.errors().size());
        refused("INSERT INTO boss_world_pool (boss_id, weight) VALUES ('neg', -1)");
        exec("DELETE FROM boss_world_spot", "DELETE FROM boss_world_pool");
        BossConfig empty = BossConfig.load(db);
        assertFalse(empty.worldBoss().enabled());
        assertFalse(empty.errors().isEmpty());
    }

    private static String slayer(String id) {
        return "INSERT INTO slayer (id, position, name, entity) VALUES ('" + id + "', 0, 'Zed', 'ZOMBIE')";
    }

    private static String tier(String id, int tier, double health) {
        return "INSERT INTO slayer_tier (slayer_id, tier, cost, required_xp, health, damage, slayer_xp, time_limit) VALUES ('" + id
            + "', " + tier + ", " + tier * 10 + ", 10, " + health + ", 5, 5, 300)";
    }

    @Test
    public void slayerNeedsFiveTiersAndNineLevels() {
        BossConfig c = load(slayer("bad"), "INSERT INTO slayer_target (slayer_id, position, entity) VALUES ('bad', 0, 'ZOMBIE')",
            "INSERT INTO slayer_level (slayer_id, level, xp) VALUES ('bad', 1, 1)",
            "INSERT INTO slayer_level (slayer_id, level, xp) VALUES ('bad', 2, 2)", tier("bad", 1, 100), tier("bad", 2, 100));
        assertTrue(c.slayers().isEmpty());
        assertFalse(c.errors().isEmpty());
    }

    @Test
    public void slayerGeneratesFiveBosses() {
        List<String> sql = new ArrayList<>(List.of(slayer("z"),
            "INSERT INTO slayer_target (slayer_id, position, entity, kill_xp) VALUES ('z', 0, 'ZOMBIE', NULL)",
            "INSERT INTO slayer_target (slayer_id, position, entity, kill_xp) VALUES ('z', 1, 'HUSK', 4)"));
        for (int level = 1; level <= 9; level++) {
            sql.add("INSERT INTO slayer_level (slayer_id, level, xp) VALUES ('z', " + level + ", " + level + ")");
        }
        for (int t = 1; t <= 5; t++) {
            sql.add(tier("z", t, t));
        }
        sql.add("INSERT INTO slayer_drop (slayer_id, position, item, chance, from_tier, pity) VALUES ('z', 0, 'custom:rare', 0.03, 3, 0.001)");
        BossConfig c = load(sql.toArray(String[]::new));
        assertEquals(List.of(), c.errors());
        var z = c.slayers().get("z");
        assertEquals(5, z.tiers().size());
        assertEquals(50, z.tier(5).cost(), 0);
        assertEquals(5, z.tier(5).boss().health(), 0);
        assertEquals(BossDefinition.Kind.SLAYER, z.tier(1).boss().kind());
        assertEquals(5, z.progressFor(org.bukkit.entity.EntityType.ZOMBIE), 0);   // the slayer's default
        assertEquals(4, z.progressFor(org.bukkit.entity.EntityType.HUSK), 0);
        assertEquals(0, z.progressFor(org.bukkit.entity.EntityType.SPIDER), 0);   // not a target
        assertTrue(c.definition("slayer_z_4").isPresent());
        assertTrue("the drop starts at tier 3", z.tier(2).boss().loot().entries().isEmpty());
        assertEquals(1, z.tier(3).boss().loot().entries().size());
    }

    @Test
    public void settingsAreClamped() {
        exec("INSERT INTO setting (id, type, value) VALUES ('boss.settings.tick-period', 'int', '99')",
            "INSERT INTO setting (id, type, value) VALUES ('boss.settings.hp-scale', 'real', '-1')",
            "INSERT INTO setting (id, type, value) VALUES ('boss.settings.visual-health', 'real', '5')");
        BossSettings s = BossSettings.parse(Settings.load(db, "boss"));
        assertEquals(10, s.tickPeriod());
        assertTrue(s.hpScale() > 0);
        assertEquals(20, s.visualHealth(), 0);
        assertEquals(1, BossSettings.defaults().hpScale(), 0);
    }
}
