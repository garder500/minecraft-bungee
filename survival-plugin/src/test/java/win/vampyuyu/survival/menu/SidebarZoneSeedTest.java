package win.vampyuyu.survival.menu;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import org.junit.Test;
import win.vampyuyu.survival.core.SkyblockWorlds;
import win.vampyuyu.survival.data.ContentDomains;
import win.vampyuyu.survival.data.Db;
import win.vampyuyu.survival.data.Seeds;

/** The sidebar names every Realm, the Astral Interiority and a Bastion instead of showing the raw world name. */
public class SidebarZoneSeedTest {
    @Test
    public void everyRealmWorldHasASidebarName() {
        MenuConfig cfg;
        try (Db db = Db.memory()) {
            Seeds.sync(db, ContentDomains.ALL, null);
            cfg = MenuRepo.load(db);
        }
        for (String world : new String[] {SkyblockWorlds.ASHES, SkyblockWorlds.GROVE, SkyblockWorlds.HEIGHTS,
                SkyblockWorlds.CITADEL, SkyblockWorlds.MARSH, SkyblockWorlds.STORM, SkyblockWorlds.DEPTHS,
                SkyblockWorlds.NECROPOLIS, SkyblockWorlds.ASTRAL, SkyblockWorlds.BASTION_PREFIX + "7"}) {
            assertNotNull(world + " has no sidebar name", cfg.defaultZones.get(MenuConfig.worldToken(world)));
        }
        assertEquals("Bastion", cfg.defaultZones.get(MenuConfig.worldToken(SkyblockWorlds.BASTION_PREFIX + "1")));
    }
}
