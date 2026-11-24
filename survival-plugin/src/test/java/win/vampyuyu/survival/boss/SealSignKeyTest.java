package win.vampyuyu.survival.boss;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import win.vampyuyu.survival.api.Realm;
import win.vampyuyu.survival.bastion.BastionConfig;
import win.vampyuyu.survival.data.ContentDomains;
import win.vampyuyu.survival.data.Db;
import win.vampyuyu.survival.data.Seeds;
import win.vampyuyu.survival.island.SealDoor;

/** The sign of a seal names the key the way the key item is named (the refusal quotes the item's own name). */
class SealSignKeyTest {
    @Test
    void theSignNamesTheKeyAndTheGuardianOfItsRealm() {
        Db db = Db.memory();
        Seeds.sync(db, List.of(ContentDomains.BASTION), null);
        BastionConfig cfg = BastionConfig.load(db);
        for (Realm realm : Realm.values()) {
            BastionConfig.Bastion bastion = cfg.get(cfg.seals.get(realm.id())).orElseThrow();
            String key = BossItems.byId().get(bastion.keyItem()).name();
            assertEquals(SealDoor.keyWord(realm) + " Bastion Key", key, realm + " key");
            assertTrue(SealDoor.signLines(realm).contains(SealDoor.keyWord(realm) + " Key"), realm + " sign names the key");
            for (String line : SealDoor.signLines(realm)) {
                assertTrue(line.length() <= 16, realm + " sign line fits: " + line);
            }
        }
    }
}
