package win.vampyuyu.survival.boss;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import win.vampyuyu.survival.api.Realm;
import win.vampyuyu.survival.items.combat.PowerCurve;

/** The Guardians (Lava Colossus, Mycelial Queen, Judge of Lost Souls): data, hard pity (GDD 15.2), readability (GDD 8.7). */
public class GuardianTest {
    private static final List<String> IDS = List.of("magma_warden", "sylvan_matriarch", "crypt_lord", "frost_revenant", "temporal_pharaoh", "abyssal_leviathan", "mire_abomination", "thunder_dragon");

    @Before
    public void server() {
        MockBukkit.mock();
    }

    @After
    public void stop() {
        MockBukkit.unmock();
    }

    private static BossDefinition.Loot loot(BossDefinition def) {
        return def.loot();
    }

    @Test
    public void theGuardiansAreDeclaredWithARealmAResourceAndLines() {
        Guardians guardians = BossTestData.shipped().guardians();
        assertEquals(java.util.Set.copyOf(IDS), guardians.byBoss().keySet());
        assertEquals(Realm.ASHES, Realm.byId(guardians.get("magma_warden").realm()));
        assertEquals(Realm.GROVE, Realm.byId(guardians.get("sylvan_matriarch").realm()));
        assertEquals("cinderheart", guardians.get("magma_warden").resource());
        assertEquals("heartwood", guardians.get("sylvan_matriarch").resource());
        assertEquals(Realm.NECROPOLIS, Realm.byId(guardians.get("crypt_lord").realm()));
        assertEquals("soul_ash", guardians.get("crypt_lord").resource());
        assertEquals(Realm.MARSHES, Realm.byId(guardians.get("mire_abomination").realm()));
        assertEquals("rot_resin", guardians.get("mire_abomination").resource());
        assertEquals(Realm.HEIGHTS, Realm.byId(guardians.get("frost_revenant").realm()));
        assertEquals("stasis_crystal", guardians.get("frost_revenant").resource());
        assertEquals(Realm.DEPTHS, Realm.byId(guardians.get("abyssal_leviathan").realm()));
        assertEquals("pressure_pearl", guardians.get("abyssal_leviathan").resource());
        assertEquals(Realm.ARCHIPELAGO, Realm.byId(guardians.get("thunder_dragon").realm()));
        assertEquals("thunder_quartz", guardians.get("thunder_dragon").resource());
        for (Guardians.Guardian g : guardians.byBoss().values()) {
            for (String event : List.of(Guardians.INTRO, Guardians.phaseEvent(1), Guardians.phaseEvent(2), Guardians.DEATH,
                    Guardians.FIRST_CLEAR)) {
                assertFalse(g.bossId() + " " + event, g.line(event).isEmpty());
            }
            assertTrue(g.bossId(), g.resourceMax() >= g.resourceMin() && g.resourceMin() > 0);
            assertTrue(g.bossId(), g.firstClearResource() > g.resourceMax());
            assertNotNull(Guardians.say(g, Guardians.INTRO, new Random(1)));
        }
    }

    @Test
    public void theVisibleNamesUseTheCanon() {
        BossConfig config = BossTestData.shipped();
        assertTrue(config.bosses().get("magma_warden").name().contains("Lava Colossus"));
        assertTrue(config.bosses().get("sylvan_matriarch").name().contains("Mycelial Queen"));
        assertTrue(config.bosses().get("crypt_lord").name().contains("Judge of Lost Souls"));
        assertTrue(config.bosses().get("frost_revenant").name().contains("Boreal Watcher"));
        for (String id : IDS) {
            BossDefinition def = config.bosses().get(id);
            assertFalse(id, def.name().contains("Warden") || def.name().contains("Matriarch"));
            for (var phase : def.phases()) {
                assertFalse(id + " " + phase.title(), phase.title().contains("MATRIARCH") || phase.title().contains("FORGE"));
            }
        }
    }

    @Test
    public void theComponentIsADropOfTheGuardianAndAnItemOfTheTable() {
        BossConfig config = BossTestData.shipped();
        for (Guardians.Guardian g : config.guardians().byBoss().values()) {
            assertTrue(g.component(), BossItems.tableIds().contains(g.component()));
            assertTrue(g.bossId(), loot(config.bosses().get(g.bossId())).entries().stream()
                .anyMatch(e -> e.item().equals("custom:" + g.component()) && e.pity() > 0));
        }
    }

    @Test
    public void hardPityIsTheFourthRunAndTheThirdAtResonanceThree() {
        Guardians guardians = BossTestData.shipped().guardians();
        assertEquals(4, guardians.hardPityFor(0));
        assertEquals(4, guardians.hardPityFor(2));
        assertEquals(3, guardians.hardPityFor(3));
        assertEquals(3, guardians.hardPityFor(5));
        assertEquals(Map.of("custom:lava_core", 4), guardians.hardPityMap("magma_warden", 0));
        assertEquals(Map.of("custom:mycelial_core", 3), guardians.hardPityMap("sylvan_matriarch", 3));
        assertEquals(Map.of("custom:frost_core", 4), guardians.hardPityMap("frost_revenant", 0));
        assertEquals(Map.of(), guardians.hardPityMap("voidwyrm", 3));
    }

    /** A random source that never lets a chance roll succeed (always 0.999...), to see what only the pity gives. */
    private static final class Unlucky extends Random {
        @Override
        public double nextDouble() {
            return 0.9999999;
        }
    }

    private static boolean dropped(BossDefinition def, String item, Map<String, Integer> dry, Map<String, Integer> hard) {
        LootRoller.Result result = LootRoller.roll(def.id(), def.loot(), 1.0, 1, 0, dry, hard, new Unlucky());
        return result.drops().stream().anyMatch(d -> d.entry().item().equals(item));
    }

    @Test
    public void theComponentIsCertainOnTheFourthRunWithoutIt() {
        BossConfig config = BossTestData.shipped();
        for (Guardians.Guardian g : config.guardians().byBoss().values()) {
            BossDefinition def = config.bosses().get(g.bossId());
            String item = "custom:" + g.component();
            Map<String, Integer> hard = config.guardians().hardPityMap(g.bossId(), 0);
            String key = g.bossId() + "|" + item;
            Map<String, Integer> dry = new HashMap<>();
            for (int run = 1; run <= 3; run++) {
                assertFalse(g.bossId() + " run " + run, dropped(def, item, dry, hard));
                dry = new HashMap<>(LootRoller.roll(def.id(), def.loot(), 1.0, 1, 0, dry, hard, new Unlucky()).dry());
            }
            assertEquals(3, (int) dry.get(key));
            assertTrue(g.bossId() + " run 4", dropped(def, item, dry, hard));
            // the streak is reset by the drop
            LootRoller.Result win = LootRoller.roll(def.id(), def.loot(), 1.0, 1, 0, dry, hard, new Unlucky());
            assertFalse(win.dry().containsKey(key));
            // at Resonance III it is the third run
            Map<String, Integer> res3 = config.guardians().hardPityMap(g.bossId(), 3);
            assertTrue(g.bossId() + " resonance run 3", dropped(def, item, Map.of(key, 2), res3));
            assertFalse(g.bossId() + " resonance run 2", dropped(def, item, Map.of(key, 1), res3));
        }
    }

    @Test
    public void aFighterWhoDidNotReallyFightDoesNotGetTheGuaranteedComponent() {
        BossDefinition def = BossTestData.shipped().bosses().get("magma_warden");
        Map<String, Integer> hard = Map.of("custom:lava_core", 4);
        Map<String, Integer> dry = Map.of("magma_warden|custom:lava_core", 10);
        LootRoller.Result result = LootRoller.roll(def.id(), def.loot(), 0.01, 3, 0, dry, hard, new Unlucky());
        assertTrue(result.drops().stream().noneMatch(d -> d.entry().item().equals("custom:lava_core")));
    }

    @Test
    public void resourceAmountIsWithinBoundsAndFirstClearAddsTheBonus() {
        Guardians.Guardian g = BossTestData.shipped().guardians().get("magma_warden");
        Random random = new Random(7);
        for (int i = 0; i < 200; i++) {
            int normal = Guardians.resourceAmount(g, false, random);
            assertTrue(normal >= g.resourceMin() && normal <= g.resourceMax());
            int first = Guardians.resourceAmount(g, true, random);
            assertTrue(first >= g.resourceMin() + g.firstClearResource() && first <= g.resourceMax() + g.firstClearResource());
        }
    }

    @Test
    public void healthFollowsTheCurveAndReadabilityRulesHold() {
        var db = win.vampyuyu.survival.data.TestDb.seeded();
        PowerCurve curve = PowerCurve.load(db);
        BossConfig config = BossRepo.load(db);
        double hitsToDown = win.vampyuyu.survival.data.Settings.load(db, "boss").getInt("settings.hits-to-down", 8);
        double damageScale = win.vampyuyu.survival.data.Settings.load(db, "boss").getDouble("settings.damage-scale", 1);
        for (String id : IDS) {
            BossDefinition boss = config.bosses().get(id);
            assertEquals(id, curve.bossHealth(boss.tier(), 180, 0.5), boss.health(), 0);
            assertEquals(id, 540, boss.arena().enrageSeconds());
            assertEquals(id, 3.0, BossMath.hpMultiplier(5, boss.arena().minPlayers(), boss.arena().scalePerPlayer(),
                boss.arena().scaleCap()), 1e-9);
            double previous = 0;
            for (var phase : boss.phases()) {
                assertTrue(id + " phase " + phase.name(), phase.damageMult() <= 1.4 + 1e-9);
                if (previous > 0) {
                    assertTrue(id + " jump " + phase.name(), phase.damageMult() / previous <= 1.4 + 1e-9);
                }
                previous = phase.damageMult();
                for (var ability : java.util.stream.Stream.concat(phase.enter().stream(), phase.abilities().stream()).toList()) {
                    double share = ability.damage() * phase.damageMult() * damageScale / hitsToDown;
                    assertTrue(id + " " + ability.type() + " takes " + share, share <= 0.35);
                }
            }
        }
    }
}
