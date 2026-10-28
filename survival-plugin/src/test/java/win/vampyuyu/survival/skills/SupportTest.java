package win.vampyuyu.survival.skills;

import static org.junit.Assert.*;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.Test;

/** Pure helpers: boosts, anti farm, placed blocks, reward text. */
public class SupportTest {
    @Test
    public void boostsAddUpAndExpire() {
        long[] now = {1000};
        XpBoosts boosts = new XpBoosts(() -> now[0]);
        assertEquals(1.0, boosts.multiplier(win.vampyuyu.survival.api.Skill.MINING, 10), 0);
        boosts.add("weekend", null, 2.0, 60_000);
        boosts.add("mining", win.vampyuyu.survival.api.Skill.MINING, 1.5, 0);
        assertEquals(2.5, boosts.multiplier(win.vampyuyu.survival.api.Skill.MINING, 10), 1e-9);
        assertEquals(2.0, boosts.multiplier(win.vampyuyu.survival.api.Skill.COMBAT, 10), 1e-9);
        assertEquals(2.2, boosts.multiplier(win.vampyuyu.survival.api.Skill.MINING, 2.2), 1e-9); // capped
        now[0] += 61_000;
        assertEquals(1.5, boosts.multiplier(win.vampyuyu.survival.api.Skill.MINING, 10), 1e-9);
        assertEquals(1, boosts.active().size());
        assertTrue(boosts.remove("mining"));
        assertFalse(boosts.remove("mining"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void boostMustExceedOne() {
        new XpBoosts().add("x", null, 1.0, 0);
    }

    @Test
    public void antiFarmDiminishesThenRecovers() {
        long[] now = {0};
        AntiFarm farm = new AntiFarm(new SkillsConfig.AntiFarmCfg(4, 10_000, 3, 0.25, 0.2), () -> now[0]);
        java.util.UUID p = java.util.UUID.randomUUID();
        for (int i = 0; i < 3; i++) {
            assertEquals(1.0, farm.next(p, "w", 1, 64, 1), 1e-9);
        }
        assertEquals(0.75, farm.next(p, "w", 2, 65, 3), 1e-9); // same 4x4x4 cell
        assertEquals(0.5, farm.next(p, "w", 1, 64, 1), 1e-9);
        assertEquals(1.0, farm.next(p, "w", 40, 64, 40), 1e-9); // other place is untouched
        assertEquals(1.0, farm.next(java.util.UUID.randomUUID(), "w", 1, 64, 1), 1e-9); // other player
        for (int i = 0; i < 10; i++) {
            farm.next(p, "w", 1, 64, 1);
        }
        assertEquals(0.2, farm.next(p, "w", 1, 64, 1), 1e-9); // floor
        now[0] += 20_000;
        assertEquals(1.0, farm.next(p, "w", 1, 64, 1), 1e-9); // window passed
        farm.forget(p);
    }

    @Test
    public void antiFarmCellsAreBounded() {
        AntiFarm farm = new AntiFarm(new SkillsConfig.AntiFarmCfg(1, 60_000, 1, 0.1, 0.1), () -> 0L);
        java.util.UUID p = java.util.UUID.randomUUID();
        for (int i = 0; i < 1000; i++) {
            farm.next(p, "w", i, 64, 0);
        }
        // first cell was evicted: counts as fresh again
        assertEquals(1.0, farm.next(p, "w", 0, 64, 0), 1e-9);
    }

    private static final class MemoryBackend implements PlacedBlocks.Backend {
        final java.util.Map<String, long[]> saved = new java.util.HashMap<>();
        int loads;

        @Override
        public long[] load(java.util.UUID world, int cx, int cz) {
            loads++;
            return saved.getOrDefault(world + ":" + cx + ":" + cz, new long[0]);
        }

        @Override
        public void save(java.util.UUID world, int cx, int cz, long[] bits) {
            saved.put(world + ":" + cx + ":" + cz, bits);
        }
    }

    @Test
    public void placedBlocksMarkUnmarkAndPersist() {
        MemoryBackend backend = new MemoryBackend();
        PlacedBlocks placed = new PlacedBlocks(backend);
        java.util.UUID world = java.util.UUID.randomUUID();
        assertFalse(placed.isPlaced(world, -64, 5, 70, 5));
        placed.mark(world, -64, 5, 70, 5);
        placed.mark(world, -64, -1, -64, -1); // negative coordinates and the lowest layer
        assertTrue(placed.isPlaced(world, -64, 5, 70, 5));
        assertTrue(placed.isPlaced(world, -64, -1, -64, -1));
        assertFalse(placed.isPlaced(world, -64, 6, 70, 5));
        assertFalse(placed.isPlaced(world, -64, 5, 71, 5));
        assertTrue(placed.unmark(world, -64, -1, -64, -1));
        assertFalse(placed.unmark(world, -64, -1, -64, -1));
        assertEquals(2, placed.flush()); // two chunks changed
        assertEquals(0, placed.flush());
        // a fresh store over the same backend sees the saved bits
        PlacedBlocks again = new PlacedBlocks(backend);
        assertTrue(again.isPlaced(world, -64, 5, 70, 5));
        assertFalse(again.isPlaced(world, -64, -1, -64, -1));
        // unload writes pending changes and forgets the chunk
        again.mark(world, -64, 5, 71, 5);
        int cached = again.cachedChunks();
        again.unload(world, 0, 0);
        assertEquals(cached - 1, again.cachedChunks());
        assertTrue(new PlacedBlocks(backend).isPlaced(world, -64, 5, 71, 5));
    }

    @Test
    public void placedBlocksAreCompact() {
        PlacedBlocks placed = new PlacedBlocks(new MemoryBackend());
        java.util.UUID world = java.util.UUID.randomUUID();
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                placed.mark(world, -64, x, 100, z);
            }
        }
        assertEquals(1, placed.dirtyChunks().size());
        assertEquals(1, placed.flush());
    }

    @Test
    public void rewardTextFormats() {
        assertEquals("<green>■■<gray>■■", RewardText.bar(0.5, 4));
        assertEquals("<green><gray>■■", RewardText.bar(-3, 2));
        assertEquals("<green>\u25A0\u25A0<gray>", RewardText.bar(7, 2));
        assertEquals("<green><gray>\u25A0\u25A0", RewardText.bar(Double.NaN, 2));
        assertEquals("33.3%", RewardText.percent(1.0 / 3));
        assertEquals("100%", RewardText.percent(5));
        assertEquals("IV", RewardText.roman(4));
        assertEquals("XLIX", RewardText.roman(49));
        assertEquals("0", RewardText.roman(0));
        assertEquals("Nether Wart", SkillsConfig.prettify("NETHER_WART"));
    }

    @Test
    public void fortuneExtraMath() {
        assertEquals(0, BlockSources.fortuneExtra(3, 0, 0.0));
        assertEquals(3, BlockSources.fortuneExtra(3, 100, 0.99));
        assertEquals(3, BlockSources.fortuneExtra(2, 150, 0.0)); // exactly 3.0 expected
        assertEquals(1, BlockSources.fortuneExtra(1, 50, 0.4));
        assertEquals(0, BlockSources.fortuneExtra(1, 50, 0.6));
        assertEquals(64, BlockSources.fortuneExtra(64, 10_000, 0.0)); // capped per drop
    }

    @Test
    public void configParsingSkipsBadEntries() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("xp.mining.blocks.STONE", 2);
        yaml.set("xp.mining.blocks.NOT_A_BLOCK", 2);
        yaml.set("skills.combat.stats.CRIT_CHANCE", java.util.List.of(java.util.Map.of("from", 1, "to", 10, "amount", 0.5)));
        yaml.set("skills.combat.stats.NOPE", java.util.List.of(java.util.Map.of("from", 1, "to", 10, "amount", 0.5)));
        SkillsConfig cfg = new SkillsConfig(yaml);
        assertEquals(2.0, cfg.miningBlocks.get(org.bukkit.Material.STONE), 0);
        assertEquals(2, cfg.warnings.size());
        SkillsConfig.StatReward reward = cfg.skill(win.vampyuyu.survival.api.Skill.COMBAT).stats().get(0);
        assertEquals(5.0, reward.total(10), 1e-9);
        assertEquals(5.0, reward.total(60), 1e-9);
        assertEquals(1.5, reward.total(3), 1e-9);
        assertEquals(0.5, reward.at(4), 0);
        assertEquals(0, reward.at(11), 0);
    }
}
