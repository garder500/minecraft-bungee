package win.vampyuyu.survival.boss;

import static org.junit.Assert.*;

import org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason;
import org.junit.Test;

public class SpawnSourcesTest {
    @Test
    public void farmableSourcesAreArtificial() {
        for (SpawnReason r : new SpawnReason[] {SpawnReason.SPAWNER, SpawnReason.TRIAL_SPAWNER, SpawnReason.SPAWNER_EGG,
                SpawnReason.BREEDING, SpawnReason.BUILD_SNOWMAN, SpawnReason.BUILD_IRONGOLEM, SpawnReason.BUILD_WITHER,
                SpawnReason.INFECTION, SpawnReason.CURED, SpawnReason.EGG}) {
            assertTrue(r.name(), SpawnSources.isArtificial(r));
        }
    }

    @Test
    public void naturalSourcesAreNot() {
        for (SpawnReason r : new SpawnReason[] {SpawnReason.NATURAL, SpawnReason.CHUNK_GEN, SpawnReason.PATROL,
                SpawnReason.RAID, SpawnReason.REINFORCEMENTS}) {
            assertFalse(r.name(), SpawnSources.isArtificial(r));
        }
        assertFalse(SpawnSources.isArtificial((SpawnReason) null));
    }
}
