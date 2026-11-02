package win.vampyuyu.survival.runes;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import win.vampyuyu.survival.api.Stat;
import win.vampyuyu.survival.data.ContentDomains;
import win.vampyuyu.survival.data.Db;
import win.vampyuyu.survival.data.Seeds;

/** Rune values, slots, engraving rules and the shipped seed (GDD 10, 3.2). */
public class RuneRulesTest {
    private final RuneConfig config = seeded();

    private static RuneConfig seeded() {
        try (Db db = Db.memory()) {
            Seeds.sync(db, ContentDomains.ALL, null);
            return RuneConfig.load(db);
        }
    }

    private Rune rune(String id) {
        return config.rune(id).orElseThrow();
    }

    @Test
    public void theShippedSeedHasNoProblemAndTheGddNumbers() {
        assertEquals(List.of(), config.problems());
        assertEquals(List.of(2, 4, 6), config.values());
        assertEquals(List.of(1, 2, 3, 4, 4), config.slots());
        assertEquals(8, config.powder());
        assertEquals(3, config.fuse());
        assertEquals(6, config.runes().size());
        assertEquals(2, config.runes().stream().filter(Rune::realm).count());
    }

    @Test
    public void textRoundTripsAndDamagedEntriesAreDropped() {
        Map<String, Integer> runes = new LinkedHashMap<>();
        runes.put("vitality", 3);
        runes.put("scorch", 0);
        assertEquals("vitality:3,scorch:0", RuneRules.format(runes));
        assertEquals(runes, RuneRules.parse("vitality:3,scorch:0"));
        assertEquals(Map.of("ward", 1), RuneRules.parse("ward:1,broken,x:y,:4"));
        assertTrue(RuneRules.parse(null).isEmpty());
        assertTrue(RuneRules.parse("  ").isEmpty());
    }

    @Test
    public void statRunesAddTheirRankValueAndRealmRunesAddNoPoints() {
        Map<Stat, Double> bonus = RuneRules.bonus(config, Map.of("vitality", 3, "ward", 1, "scorch", 0, "ghost", 2));
        assertEquals(6.0, bonus.get(Stat.VITALITY), 0);
        assertEquals(2.0, bonus.get(Stat.WARD), 0);
        assertEquals(2, bonus.size());
    }

    @Test
    public void slotsFollowTheTier() {
        assertEquals(1, config.slotsFor(1));
        assertEquals(2, config.slotsFor(2));
        assertEquals(3, config.slotsFor(3));
        assertEquals(4, config.slotsFor(4));
        assertEquals(4, config.slotsFor(5));
        assertEquals(1, config.slotsFor(0));
        assertEquals(4, config.slotsFor(9));
    }

    @Test
    public void engravingChecksTargetSlotsAndDuplicates() {
        Rune vitality = rune("vitality");
        assertNull(RuneRules.problem(config, vitality, 1, Map.of(), false, true, 1));
        assertNotNull("armor rune on a sword", RuneRules.problem(config, vitality, 1, Map.of(), true, false, 5));
        assertNotNull("not equipment", RuneRules.problem(config, vitality, 1, Map.of(), false, false, 5));
        assertNotNull("one slot only at tier 1", RuneRules.problem(config, vitality, 1, Map.of("ward", 1), false, true, 1));
        assertNull(RuneRules.problem(config, vitality, 1, Map.of("ward", 1), false, true, 2));
        assertNotNull("same kind twice", RuneRules.problem(config, vitality, 2, Map.of("vitality", 1), false, true, 5));
        assertNotNull("rank out of range", RuneRules.problem(config, vitality, 4, Map.of(), false, true, 5));
        assertNull(RuneRules.problem(config, rune("aether"), 1, Map.of(), true, false, 1));
        assertNull(RuneRules.problem(config, rune("aether"), 1, Map.of(), false, true, 1));
        assertNull("Realm runes have no rank", RuneRules.problem(config, rune("scorch"), 0, Map.of(), true, false, 3));
        assertNotNull(RuneRules.problem(config, rune("scorch"), 0, Map.of(), false, true, 3));
        assertNotNull(RuneRules.problem(config, rune("verdant"), 0, Map.of(), true, false, 3));
    }

    @Test
    public void aFullKitStaysInsideTheRuneBudgetOfTheGdd() {
        // GDD 8.5: about +100 from runes in a finished kit. Four armor pieces carry one rune of each kind, a weapon one.
        for (Rune rune : config.runes()) {
            if (rune.realm()) {
                continue;
            }
            int carriers = rune.target() == Rune.Target.WEAPON ? 1 : 4;
            assertTrue(rune.id() + " stays under 100 points", carriers * config.value(3) <= 100);
        }
        // a Realm rune is worth a rank III: one slot, no extra points
        assertEquals(0, RuneRules.bonus(config, Map.of("scorch", 0, "verdant", 0)).size());
    }

    @Test
    public void realmRunesAreMadeFromTheRealmResources() {
        assertEquals("cinderheart", rune("scorch").component());
        assertEquals("heartwood", rune("verdant").component());
        assertEquals("rune_scorch", rune("scorch").itemId(0));
        assertEquals("rune_vitality_2", rune("vitality").itemId(2));
    }

    @Test
    public void emptySettingsGiveNoRunesAndTheDefaults() {
        win.vampyuyu.survival.data.Settings bad = win.vampyuyu.survival.data.Settings.empty("runes");
        RuneConfig empty = RuneConfig.parse(bad);
        assertTrue(empty.runes().isEmpty());
        assertFalse(empty.stationBlock().isEmpty());
    }
}
