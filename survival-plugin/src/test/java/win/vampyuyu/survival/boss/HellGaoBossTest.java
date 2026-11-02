package win.vampyuyu.survival.boss;

import static org.junit.Assert.*;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.bukkit.entity.EntityType;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

/** Hell Gao (GDD 19.4): 5 to 7 phases, each with a rule borrowed from a Guardian or the Devourer, no stats, no coins. */
public class HellGaoBossTest {
    @Before
    public void server() {
        MockBukkit.mock();
    }

    @After
    public void stop() {
        MockBukkit.unmock();
    }

    private static BossDefinition hellGao() {
        BossDefinition def = BossTestData.shipped().bosses().get("hell_gao");
        assertNotNull(def);
        return def;
    }

    @Test
    public void hasSixPhasesInDecreasingOrder() {
        BossDefinition def = hellGao();
        assertEquals(EntityType.ENDERMAN, def.entity());
        assertEquals(6, def.phases().size());
        assertTrue("5 to 7 phases", def.phases().size() >= 5 && def.phases().size() <= 7);
        for (int i = 1; i < def.phases().size(); i++) {
            assertTrue(def.phases().get(i).at() < def.phases().get(i - 1).at());
        }
    }

    @Test
    public void everyPhaseBringsARuleNoPhaseBeforeUsed() {
        BossDefinition def = hellGao();
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < def.phases().size(); i++) {
            Set<String> own = new HashSet<>();
            def.phases().get(i).abilities().forEach(a -> own.add(a.type().id()));
            def.phases().get(i).enter().forEach(a -> own.add(a.type().id()));
            assertFalse("phase " + i + " has no ability", own.isEmpty());
            Set<String> fresh = new HashSet<>(own);
            fresh.removeAll(seen);
            if (i > 0 && i < def.phases().size() - 1) {
                assertFalse("phase " + i + " reuses only known rules", fresh.isEmpty());
            }
            seen.addAll(own);
        }
    }

    @Test
    public void paysNoCoinsNoXpAndNoDrops() {
        BossDefinition def = hellGao();
        assertEquals(0.0, def.loot().coins(), 1e-9);
        assertEquals(0.0, def.loot().combatXp(), 1e-9);
        assertTrue(def.loot().entries().isEmpty());
        assertNull("opened only through its Bastion", def.summon());
    }
}
