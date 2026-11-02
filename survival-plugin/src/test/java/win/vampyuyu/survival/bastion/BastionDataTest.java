package win.vampyuyu.survival.bastion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import win.vampyuyu.survival.core.SkyblockWorlds;
import win.vampyuyu.survival.dungeon.run.WorldFiles;

class BastionDataTest {
    @Test
    void clearsAndDefeatsAreCountedWithTheBestTime() {
        BastionData d = new BastionData();
        d.recordClear("ashes_bastion", 90_000);
        d.recordClear("ashes_bastion", 80_000);
        d.recordClear("ashes_bastion", 100_000);
        d.recordDefeat("ashes_bastion");
        assertEquals(80_000, d.of("ashes_bastion").bestMillis);
        assertEquals(3, d.of("ashes_bastion").clears);
        assertEquals(4, d.of("ashes_bastion").runs);
        assertEquals(1, d.of("ashes_bastion").defeats);
        assertTrue(d.cleared("ashes_bastion"));
        assertFalse(d.cleared("grove_bastion"));
    }

    @Test
    void bastionWorldsAreRecognisedAndOnlyThose() {
        assertTrue(SkyblockWorlds.isBastion("sb_bastion_3"));
        assertTrue(SkyblockWorlds.isSkyblock("sb_bastion_3"));
        assertFalse(SkyblockWorlds.isDungeon("sb_bastion_3"));
        assertTrue(WorldFiles.isBastionWorldName("sb_bastion_12"));
        assertFalse(WorldFiles.isBastionWorldName("sb_bastion_"));
        assertFalse(WorldFiles.isBastionWorldName("sb_bastion_1/../x"));
        assertFalse(WorldFiles.isBastionWorldName("sb_world"));
    }

    @Test
    void arenaPalettesCoverEveryPaletteOfTheSchema() {
        for (String p : new String[] {"cinder", "grove"}) {
            assertEquals(4, BastionArena.floor(p).length);
        }
    }
}
