package win.vampyuyu.survival.realm;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import win.vampyuyu.survival.data.Db;
import win.vampyuyu.survival.data.Seeds;
import win.vampyuyu.survival.data.ContentDomains;

public class RealmPityTest {
    @Test
    public void curveFollowsGdd151() {
        assertEquals(0.06, RealmPity.chance(0.06, 12, 36, 0), 1e-9);
        assertEquals(0.06, RealmPity.chance(0.06, 12, 36, 11), 1e-9);
        assertEquals(0.06, RealmPity.chance(0.06, 12, 36, 12), 1e-9);
        assertEquals(0.53, RealmPity.chance(0.06, 12, 36, 24), 1e-9);
        assertEquals(1.0, RealmPity.chance(0.06, 12, 36, 36), 1e-9);
        assertEquals(1.0, RealmPity.chance(0.06, 12, 36, 99), 1e-9);
    }

    @Test
    public void seedLoadsBothResources() {
        try (Db db = Db.memory()) {
            Seeds.sync(db, ContentDomains.ALL, null);
            RealmRepo.Loaded loaded = RealmRepo.load(db, java.util.logging.Logger.getAnonymousLogger());
            assertNotNull(loaded.resources().get("cinderheart"));
            assertNotNull(loaded.resources().get("heartwood"));
            assertTrue(loaded.resources().get("cinderheart").blocks().contains("MAGMA_BLOCK"));
            assertTrue(loaded.resources().get("heartwood").mobs().contains("BOGGED"));
            assertEquals(win.vampyuyu.survival.api.Realm.ASHES, loaded.worlds().get("sb_ashes"));
        }
    }
}
