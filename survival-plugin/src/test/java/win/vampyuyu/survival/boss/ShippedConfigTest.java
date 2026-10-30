package win.vampyuyu.survival.boss;

import static org.junit.Assert.*;

import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import win.vampyuyu.survival.boss.slayer.SlayerDefinition;

/** The boss.yml shipped in the jar must validate: a wrong key silently drops a boss. */
public class ShippedConfigTest {
    @Before
    public void server() {
        MockBukkit.mock(); // Material and EntityType lookups need a server
    }

    @After
    public void stop() {
        MockBukkit.unmock();
    }

    static BossConfig shipped() throws Exception {
        try (Reader reader = new InputStreamReader(ShippedConfigTest.class.getResourceAsStream("/boss.yml"),
                StandardCharsets.UTF_8)) {
            return BossConfig.load(YamlConfiguration.loadConfiguration(reader));
        }
    }

    @Test
    public void noErrors() throws Exception {
        assertEquals(List.of(), shipped().errors());
    }

    @Test
    public void contractBossesExist() throws Exception {
        BossConfig config = shipped();
        for (String id : List.of("voidwyrm", "magma_warden", "frost_revenant", "sylvan_matriarch",
                "abyssal_leviathan", "crypt_lord")) {
            assertTrue(id, config.bosses().containsKey(id));
        }
    }

    @Test
    public void voidwyrmGrantsWingsAtFivePercent() throws Exception {
        BossDefinition def = shipped().bosses().get("voidwyrm");
        assertEquals(org.bukkit.entity.EntityType.ENDER_DRAGON, def.entity());
        assertEquals(0.05, def.loot().wingsMinShare(), 1e-9);
        assertEquals("void_sigil", def.summon().item());
        assertTrue(def.loot().entries().stream().anyMatch(e -> e.item().equals("custom:wing_core")));
    }

    @Test
    public void cryptLordHasNoSummonItem() throws Exception {
        assertNull(shipped().bosses().get("crypt_lord").summon());
    }

    @Test
    public void leviathanNeedsWater() throws Exception {
        assertTrue(shipped().bosses().get("abyssal_leviathan").summon().requireWater());
    }

    @Test
    public void everyBossHasSeveralPhases() throws Exception {
        for (BossDefinition def : shipped().bosses().values()) {
            assertTrue(def.id(), def.phases().size() >= 2);
            assertTrue(def.id(), def.phases().stream().anyMatch(p -> !p.abilities().isEmpty()));
        }
    }

    @Test
    public void fiveSlayersWithFiveTiers() throws Exception {
        BossConfig config = shipped();
        assertEquals(Set.of("zombie", "spider", "wolf", "enderman", "blaze"), config.slayers().keySet());
        for (SlayerDefinition s : config.slayers().values()) {
            assertEquals(5, s.tiers().size());
            assertEquals(9, s.levelXp().size());
            assertTrue(s.tier(5).boss().health() > s.tier(1).boss().health());
            assertTrue(config.definition("slayer_" + s.id() + "_3").isPresent());
        }
    }

    @Test
    public void worldBossPoolIsValid() throws Exception {
        BossConfig config = shipped();
        assertTrue(config.worldBoss().enabled());
        assertFalse(config.worldBoss().locations().isEmpty());
        for (var entry : config.worldBoss().pool()) {
            assertEquals(BossDefinition.Kind.WORLD, config.bosses().get(entry.boss()).kind());
        }
    }

    @Test
    public void everyReferencedCustomItemIsRegistered() throws Exception {
        BossConfig config = shipped();
        Set<String> known = new java.util.HashSet<>(BossItems.tableIds());
        List<String> missing = new ArrayList<>();
        List<BossDefinition> all = new ArrayList<>(config.bosses().values());
        config.slayers().values().forEach(s -> s.tiers().forEach(t -> all.add(t.boss())));
        for (BossDefinition def : all) {
            def.loot().entries().stream().filter(e -> e.item().startsWith("custom:"))
                .map(e -> e.item().substring(7)).filter(id -> !known.contains(id)).forEach(missing::add);
            if (def.summon() != null && !known.contains(def.summon().item())) {
                missing.add(def.summon().item());
            }
        }
        config.recipes().keySet().stream().filter(id -> !known.contains(id)).forEach(missing::add);
        config.mobDrops().stream().map(BossConfig.MobDrop::item).filter(id -> !known.contains(id))
            .forEach(missing::add);
        assertEquals(List.of(), missing);
    }

    @Test
    public void everySummonItemHasABossAndARecipeOrDrop() throws Exception {
        BossConfig config = shipped();
        for (String item : List.of("void_sigil", "magma_core", "frost_heart", "matriarch_egg", "leviathan_scale")) {
            assertTrue(item, config.bosses().values().stream()
                .anyMatch(d -> d.summon() != null && d.summon().item().equals(item)));
            assertTrue(item, config.mobDrops().stream().anyMatch(d -> d.item().equals(item)));
            assertTrue(item, config.recipes().containsKey(item));
        }
    }
}
