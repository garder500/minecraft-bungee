package win.vampyuyu.survival.boss;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

public class DefinitionParserTest {
    @Before
    public void server() {
        MockBukkit.mock();
    }

    @After
    public void stop() {
        MockBukkit.unmock();
    }

    private static YamlConfiguration yaml(String text) {
        YamlConfiguration y = new YamlConfiguration();
        try {
            y.loadFromString(text);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
        return y;
    }

    private static BossConfig load(String text) {
        return BossConfig.load(yaml(text));
    }

    private static final String MINIMAL = """
        bosses:
          tester:
            name: "<red>Tester"
            entity: ZOMBIE
            health: 1000
        """;

    @Test
    public void minimalBossGetsDefaults() {
        BossConfig c = load(MINIMAL);
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
        BossConfig c = load("bosses:\n  a:\n    entity: NOT_A_MOB\n  b:\n    entity: ARROW\n  c:\n    entity: PLAYER\n");
        assertTrue(c.bosses().isEmpty());
        assertEquals(3, c.errors().size());
    }

    @Test
    public void rejectsBadIdAndRanges() {
        BossConfig c = load("""
            bosses:
              Bad-Id: { entity: ZOMBIE }
              neg: { entity: ZOMBIE, health: -5 }
              huge: { entity: ZOMBIE, scale: 99 }
              fast: { entity: ZOMBIE, speed: 50 }
              kb: { entity: ZOMBIE, knockback-resistance: 2 }
              ok: { entity: ZOMBIE }
            """);
        assertEquals(List.of("ok"), new ArrayList<>(c.bosses().keySet()));
        assertEquals(5, c.errors().size());
    }

    @Test
    public void phaseThresholdsMustDecrease() {
        BossConfig c = load("""
            bosses:
              p:
                entity: ZOMBIE
                phases:
                  - { name: A, at: 1.0 }
                  - { name: B, at: 0.5 }
                  - { name: C, at: 0.7 }
            """);
        assertFalse(c.errors().isEmpty());
        assertNull("a definition with a bad phase is dropped", c.bosses().get("p"));
    }

    @Test
    public void rejectsUnknownAbilityAndBadValues() {
        BossConfig c = load("""
            bosses:
              p:
                entity: ZOMBIE
                phases:
                  - name: A
                    at: 1.0
                    abilities:
                      - { type: fly_to_the_moon }
                      - { type: ground_slam, cooldown: 0.1 }
                      - { type: ground_slam, target: everyone }
                      - { type: ground_slam, telegraph: 99 }
            """);
        assertNull(c.bosses().get("p"));
        assertTrue(c.errors().size() >= 4);
        assertTrue(c.errors().get(0).contains("unknown ability"));
    }

    @Test
    public void abilityParametersAndAliasesParse() {
        BossConfig c = load("""
            bosses:
              p:
                entity: ZOMBIE
                abilities:
                  - { type: ground-slam, cooldown: 10, telegraph: 1.5, radius: 12, flag: true, label: x }
            """);
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
    }

    @Test
    public void lootEntriesAreValidated() {
        BossConfig c = load("""
            bosses:
              a:
                entity: ZOMBIE
                loot:
                  drops:
                    - { item: "custom:good_one", chance: 0.5 }
                    - { item: "vanilla:DIAMOND", chance: 1, min: 2, max: 4 }
                    - { item: "weird", chance: 1 }
                    - { item: "custom:x", chance: 3 }
                    - { item: "vanilla:NOT_AN_ITEM" }
                    - { item: "custom:y", min: 5, max: 2 }
            """);
        assertNull("a boss with a broken drop is dropped, never half loaded", c.bosses().get("a"));
        assertEquals(4, c.errors().size());
        BossConfig ok = load("""
            bosses:
              a:
                entity: ZOMBIE
                loot:
                  drops:
                    - { item: "custom:good_one", chance: 0.5 }
                    - { item: "vanilla:DIAMOND", chance: 1, min: 2, max: 4 }
            """);
        assertEquals(2, ok.bosses().get("a").loot().entries().size());
    }

    @Test
    public void equipmentColoursAndSlots() {
        BossConfig c = load("""
            bosses:
              a:
                entity: ZOMBIE
                equipment:
                  head: "LEATHER_HELMET:#102030"
                  chest: "LEATHER_CHESTPLATE:red"
                  tail: "STICK"
                  feet: "NOPE"
            """);
        // the three bad lines produce errors and the whole definition is dropped
        assertNull(c.bosses().get("a"));
        assertEquals(3, c.errors().size());
        BossConfig ok = load("bosses:\n  a:\n    entity: ZOMBIE\n    equipment:\n      head: \"LEATHER_HELMET:#102030\"\n");
        assertEquals(0x102030, (int) ok.bosses().get("a").equipment().get("head").color());
    }

    @Test
    public void altarModeNeedsACoordinate() {
        BossConfig c = load("""
            bosses:
              a:
                entity: ZOMBIE
                summon: { item: some_item, mode: altar }
              b:
                entity: ZOMBIE
                summon: { item: some_item, mode: altar, altar: { world: sb_end, x: 1, y: 2, z: 3, radius: 9 } }
              c:
                entity: ZOMBIE
                summon: { item: some_item, mode: teleport }
            """);
        assertNull(c.bosses().get("a"));
        assertNull(c.bosses().get("c"));
        assertEquals(9, c.bosses().get("b").summon().altar().radius(), 0);
    }

    @Test
    public void arenaRangesAreChecked() {
        BossConfig c = load("""
            bosses:
              a: { entity: ZOMBIE, arena: { radius: 0 } }
              b: { entity: ZOMBIE, arena: { radius: 30, leash: 10 } }
              d: { entity: ZOMBIE, arena: { max-minions: 500 } }
              e: { entity: ZOMBIE, arena: { radius: 30, leash: 40 } }
            """);
        assertEquals(List.of("e"), new ArrayList<>(c.bosses().keySet()));
    }

    @Test
    public void worldBossPoolMustReferenceKnownBosses() {
        BossConfig c = load(MINIMAL + """
            world-bosses:
              enabled: true
              locations: [ { world: sb_hub, x: 1, y: 2, z: 3 } ]
              pool: [ { boss: tester, weight: 2 }, { boss: ghost, weight: 1 }, { boss: tester, weight: -1 } ]
            """);
        assertTrue(c.worldBoss().enabled());
        assertEquals(1, c.worldBoss().pool().size());
        assertEquals(2, c.errors().size());
        BossConfig empty = load(MINIMAL + "world-bosses:\n  enabled: true\n");
        assertFalse(empty.worldBoss().enabled());
        assertFalse(empty.errors().isEmpty());
    }

    @Test
    public void slayerNeedsFiveTiersAndNineLevels() {
        BossConfig c = load("""
            slayers:
              bad:
                entity: ZOMBIE
                targets: [ZOMBIE]
                level-xp: [1, 2, 3]
                costs: [1, 2, 3]
                required-xp: 10
                health: 100
                damage: 5
                slayer-xp: 5
                time-limit: 300
            """);
        assertTrue(c.slayers().isEmpty());
        assertFalse(c.errors().isEmpty());
    }

    @Test
    public void slayerSingleValueRepeatsAndGeneratesFiveBosses() {
        BossConfig c = load("""
            slayers:
              z:
                entity: ZOMBIE
                targets: [ZOMBIE]
                kill-xp: { default: 2, HUSK: 4 }
                level-xp: [1, 2, 3, 4, 5, 6, 7, 8, 9]
                costs: [10, 20, 30, 40, 50]
                required-xp: 10
                health: [1, 2, 3, 4, 5]
                damage: 5
                slayer-xp: 5
                time-limit: 300
            """);
        assertEquals(List.of(), c.errors());
        var z = c.slayers().get("z");
        assertEquals(5, z.tiers().size());
        assertEquals(10, z.tier(5).requiredXp(), 0);
        assertEquals(5, z.tier(5).boss().health(), 0);
        assertEquals(BossDefinition.Kind.SLAYER, z.tier(1).boss().kind());
        assertEquals(2, z.progressFor(org.bukkit.entity.EntityType.ZOMBIE), 0);
        assertEquals(0, z.progressFor(org.bukkit.entity.EntityType.HUSK), 0); // not a target
        assertTrue(c.definition("slayer_z_4").isPresent());
    }

    @Test
    public void settingsAreClamped() {
        BossSettings s = BossSettings.parse(yaml("tick-period: 99\nhp-scale: -1\nvisual-health: 5\n"));
        assertEquals(10, s.tickPeriod());
        assertTrue(s.hpScale() > 0);
        assertEquals(20, s.visualHealth(), 0);
        assertEquals(1, BossSettings.defaults().hpScale(), 0);
    }
}
