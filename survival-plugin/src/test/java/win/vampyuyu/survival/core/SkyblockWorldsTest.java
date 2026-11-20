package win.vampyuyu.survival.core;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Every live Skyblock world must be enterable: a retired world refuses teleports (Sky Gates, Bastions, /astral). */
public class SkyblockWorldsTest {
    private static final String[] LIVE = {
        SkyblockWorlds.HUB, SkyblockWorlds.WORLD, SkyblockWorlds.NETHER, SkyblockWorlds.END,
        SkyblockWorlds.ASHES, SkyblockWorlds.GROVE, SkyblockWorlds.HEIGHTS, SkyblockWorlds.CITADEL, SkyblockWorlds.MARSH,
        SkyblockWorlds.STORM, SkyblockWorlds.DEPTHS, SkyblockWorlds.NECROPOLIS, SkyblockWorlds.ASTRAL,
        SkyblockWorlds.dungeon("run1"), SkyblockWorlds.BASTION_PREFIX + "3",
    };

    @Test
    public void liveWorldsAreSkyblockAndNotRetired() {
        for (String world : LIVE) {
            assertTrue(world + " should be a Skyblock world", SkyblockWorlds.isSkyblock(world));
            assertFalse(world + " must not be retired", SkyblockWorlds.isRetired(world));
        }
    }

    @Test
    public void retiredWorldsAreNamedOnes() {
        for (String world : SkyblockWorlds.RETIRED) {
            assertTrue(world + " is retired but not a Skyblock name", SkyblockWorlds.isSkyblock(world));
        }
        assertFalse(SkyblockWorlds.isRetired(null));
    }
}
