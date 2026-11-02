package win.vampyuyu.survival.marsh;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.UUID;
import org.bukkit.Material;
import org.junit.Test;
import win.vampyuyu.survival.api.Realm;
import win.vampyuyu.survival.data.ContentDomains;
import win.vampyuyu.survival.data.Db;
import win.vampyuyu.survival.data.Seeds;
import win.vampyuyu.survival.island.BlockPlan;
import win.vampyuyu.survival.realm.RealmRepo;

public class MarshTest {
    @Test
    public void salvagePointsAndRolls() {
        assertEquals(1, Salvage.points(Material.ROTTEN_FLESH));
        assertEquals(3, Salvage.points(Material.SUSPICIOUS_STEW));
        assertEquals(0, Salvage.points(Material.DIAMOND));
        assertEquals(8, Salvage.rollCost(0));
        assertEquals(6, Salvage.rollCost(2));
        assertEquals(3, Salvage.rolls(25, 0));
        assertEquals(1, Salvage.carry(25, 0));
        assertEquals(4, Salvage.rolls(25, 2));
        assertEquals(1, Salvage.carry(25, 2));
        assertEquals(0, Salvage.rolls(-4, 0));
    }

    @Test
    public void purificationTiersAndPersistence() {
        MarshProgress p = new MarshProgress();
        UUID id = UUID.randomUUID();
        assertEquals(0, p.tier(id));
        assertEquals(-1, p.add(id, 39));
        assertEquals(1, p.add(id, 1));
        assertEquals(-1, p.add(id, 100));
        assertEquals(2, p.add(id, 60));
        p.setCarry(id, 5);
        MarshProgress back = MarshProgress.fromDoc(p.toDoc());
        assertEquals(200, back.points(id));
        assertEquals(5, back.carry(id));
        assertEquals(2, back.tier(id));
    }

    @Test
    public void thePlanHasLandingVatsAndAnchor() {
        BlockPlan plan = MarshPlan.build(60000);
        assertTrue(plan.size() > 2000);
        assertNotNull(plan.anchor("spawn"));
        for (MarshPlan.Spot vat : MarshPlan.VATS) {
            assertEquals(Material.WATER_CAULDRON, plan.get(vat.x(), vat.y(), vat.z()));
        }
        assertNotNull(plan.get(MarshPlan.SPAWN[0], 0, MarshPlan.SPAWN[2] - 1));
        assertFalse(plan.solidAt(MarshPlan.SPAWN[0], 1, MarshPlan.SPAWN[2]));
    }

    @Test
    public void rotResinIsARealmResourceOfTheMarshes() {
        try (Db db = Db.memory()) {
            Seeds.sync(db, ContentDomains.ALL, null);
            RealmRepo.Loaded loaded = RealmRepo.load(db, java.util.logging.Logger.getAnonymousLogger());
            assertEquals(Realm.MARSHES, loaded.worlds().get("sb_marsh"));
            assertEquals(Realm.MARSHES, loaded.resources().get("rot_resin").realm());
            assertTrue(loaded.resources().get("rot_resin").blocks().contains("MUDDY_MANGROVE_ROOTS"));
            assertTrue(loaded.resources().get("rot_resin").mobs().contains("SLIME"));
        }
    }
}
