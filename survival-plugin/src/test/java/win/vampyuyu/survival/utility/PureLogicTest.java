package win.vampyuyu.survival.utility;

import static org.junit.Assert.*;

import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.util.BoundingBox;
import org.junit.Test;
import win.vampyuyu.survival.core.Rarity;

/** Formulas and state machines that need no server. */
public class PureLogicTest {
    // ---------------------------------------------------------------- homes

    @Test
    public void homeNamesAreNormalisedAndValidated() {
        assertEquals("base", HomeBook.normalise("  BaSe "));
        assertNull(HomeBook.normalise("   "));
        assertNull(HomeBook.normalise(null));
        assertNull(HomeBook.validate("farm-2_x", 16));
        assertNotNull(HomeBook.validate("", 16));
        assertNotNull(HomeBook.validate(null, 16));
        assertNotNull(HomeBook.validate("a".repeat(17), 16));
        assertNotNull(HomeBook.validate("bad name", 16));
        assertNotNull(HomeBook.validate("<red>", 16));
        assertNotNull(HomeBook.validate("é", 16));
    }

    @Test
    public void homeLimitUsesTheBiggestPermissionAndTheCap() {
        assertEquals(3, HomeBook.limit(List.of(), 3, 100));
        assertEquals(10, HomeBook.limit(List.of("vampyuyu.sb.utility.homes.5", "vampyuyu.sb.utility.homes.10"), 3, 100));
        // a node smaller than the default never lowers it
        assertEquals(3, HomeBook.limit(List.of("vampyuyu.sb.utility.homes.1"), 3, 100));
        // capped by max-limit
        assertEquals(20, HomeBook.limit(List.of("vampyuyu.sb.utility.homes.500"), 3, 20));
        assertEquals(20, HomeBook.limit(List.of("vampyuyu.sb.utility.homes.unlimited"), 3, 20));
        // junk nodes and unrelated permissions are ignored
        assertEquals(3, HomeBook.limit(List.of("vampyuyu.sb.utility.homes.lots", "vampyuyu.sb.utility.homes.-4",
            "something.else.9"), 3, 100));
        assertEquals(2, HomeBook.limit(List.of(), 5, 2));
    }

    @Test
    public void homesSurviveAJsonRoundTrip() {
        UtilityData data = new UtilityData();
        data.homes.put("base", new UtilityData.Home("world", 1.5, 64, -2.5, 90f, 10f, 123L));
        data.homes.put("farm", new UtilityData.Home("sb_world", 100, 70, 100, 0f, 0f, 456L));
        data.tpaDisabled = true;
        String json = win.vampyuyu.survival.core.JsonStore.GSON.toJson(data);
        UtilityData back = win.vampyuyu.survival.core.JsonStore.GSON.fromJson(json, UtilityData.class);
        assertEquals(List.of("base", "farm"), List.copyOf(back.homes.keySet()));
        assertEquals(-2.5, back.homes.get("base").z, 0.0);
        assertEquals("sb_world", back.homes.get("farm").world);
        assertTrue(back.tpaDisabled);
        // an old document without the new fields still loads
        UtilityData old = win.vampyuyu.survival.core.JsonStore.GSON.fromJson("{}", UtilityData.class);
        assertTrue(old.homes.isEmpty());
        assertFalse(old.tpaDisabled);
    }

    // ---------------------------------------------------------------- combat tag

    @Test
    public void combatTagExpiresAndNeverShortens() {
        AtomicLong now = new AtomicLong(1000);
        CombatTag tag = new CombatTag(now::get);
        UUID id = UUID.randomUUID();
        assertFalse(tag.tagged(id));
        tag.tag(id, 10_000);
        assertTrue(tag.tagged(id));
        assertEquals(10_000, tag.remaining(id));
        now.addAndGet(4000);
        tag.tag(id, 1000); // shorter tag must not reduce the running one
        assertEquals(6000, tag.remaining(id));
        tag.tag(id, 10_000);
        assertEquals(10_000, tag.remaining(id));
        now.addAndGet(10_000);
        assertFalse(tag.tagged(id));
        tag.tag(id, 5000);
        tag.clear(id);
        assertFalse(tag.tagged(id));
    }


    @Test
    public void combatTagReportsFreshTagsAndHandsOutExpiredOnce() {
        AtomicLong now = new AtomicLong(1000);
        CombatTag tag = new CombatTag(now::get);
        UUID id = UUID.randomUUID();
        assertTrue(tag.tag(id, 10_000));
        assertFalse(tag.tag(id, 10_000));
        assertTrue(tag.expired().isEmpty());
        now.addAndGet(10_000);
        assertFalse(tag.tagged(id));
        assertEquals(List.of(id), tag.expired());
        assertTrue(tag.expired().isEmpty());
        assertTrue(tag.tag(id, 5000));
        now.addAndGet(5000);
        assertTrue(tag.tag(id, 5000)); // the old tag had ended: a fresh one
    }

    // ---------------------------------------------------------------- safe spots (collision boxes)

    private static final BoundingBox BOTTOM_SLAB = new BoundingBox(0, 0, 0, 1, 0.5, 1);
    private static final BoundingBox FULL = new BoundingBox(0, 0, 0, 1, 1, 1);

    @Test
    public void standingOnASlabStairCarpetOrChestDoesNotObstructTheFeet() {
        // feet on top of the block: the block's own box ends at the feet
        assertFalse(SafeSpot.boxStopsBody(BOTTOM_SLAB, 64, 0.5, 0.5, 64.5));
        assertTrue(SafeSpot.boxHoldsFeet(BOTTOM_SLAB, 64, 0.5, 0.5, 64.5));
        BoundingBox carpet = new BoundingBox(0, 0, 0, 1, 0.0625, 1);
        assertFalse(SafeSpot.boxStopsBody(carpet, 64, 0.5, 0.5, 64.0625));
        assertTrue(SafeSpot.boxHoldsFeet(carpet, 64, 0.5, 0.5, 64.0625));
        BoundingBox chest = new BoundingBox(0.0625, 0, 0.0625, 0.9375, 0.875, 0.9375);
        assertFalse(SafeSpot.boxStopsBody(chest, 64, 0.5, 0.5, 64.875));
        assertTrue(SafeSpot.boxHoldsFeet(chest, 64, 0.5, 0.5, 64.875));
        // stair: the low step holds the feet, the raised back is beside the body
        BoundingBox back = new BoundingBox(0, 0.5, 0.5, 1, 1, 1);
        assertFalse(SafeSpot.boxStopsBody(back, 64, 0.5, 0.2, 64.5));
        assertFalse(SafeSpot.boxStopsBody(BOTTOM_SLAB, 64, 0.5, 0.2, 64.5));
        // but standing against the raised back it does obstruct
        assertTrue(SafeSpot.boxStopsBody(back, 64, 0.5, 0.6, 64.5));
    }

    @Test
    public void bodiesInsideBlocksOrUnderCeilingsAreObstructed() {
        assertTrue(SafeSpot.boxStopsBody(FULL, 64, 0.5, 0.5, 64.0));
        assertTrue(SafeSpot.boxStopsBody(BOTTOM_SLAB, 64, 0.5, 0.5, 64.25)); // feet sunk in the slab
        assertTrue(SafeSpot.boxStopsBody(FULL, 65, 0.5, 0.5, 64.0)); // head cell
        assertFalse(SafeSpot.boxStopsBody(FULL, 66, 0.5, 0.5, 64.0)); // above a 1.8 tall body
        BoundingBox topSlab = new BoundingBox(0, 0.5, 0, 1, 1, 1);
        assertTrue(SafeSpot.boxStopsBody(topSlab, 65, 0.5, 0.5, 64.0)); // the body reaches 65.8, into the upper half of the head cell
        assertFalse(SafeSpot.boxStopsBody(topSlab, 65, 0.5, 0.5, 63.0)); // 63 + 1.8 = 64.8 < 65.5
        assertFalse(SafeSpot.boxHoldsFeet(FULL, 63, 0.5, 0.5, 63.5)); // top is above the feet
        assertTrue(SafeSpot.boxHoldsFeet(FULL, 63, 0.5, 0.5, 64.0));
    }

    // ---------------------------------------------------------------- tpa

    private static final UUID A = UUID.randomUUID();
    private static final UUID B = UUID.randomUUID();
    private static final UUID C = UUID.randomUUID();

    @Test
    public void tpaRequestsExpireAfterTheDelay() {
        AtomicLong now = new AtomicLong(0);
        TpaManager m = new TpaManager(now::get, 60_000, 0, 5);
        assertTrue(m.send(A, B, TpaManager.Type.TO_TARGET, false) instanceof TpaManager.SendResult.Sent);
        now.set(59_999);
        assertEquals(1, m.pendingFor(B).size());
        now.set(60_000);
        assertTrue(m.pendingFor(B).isEmpty());
        assertTrue(m.take(B, null).isEmpty());
        assertEquals(1, m.purgeExpired().size());
        assertEquals(0, m.size());
    }

    @Test
    public void tpaSendCooldownStopsSpamButBypassWorks() {
        AtomicLong now = new AtomicLong(0);
        TpaManager m = new TpaManager(now::get, 60_000, 10_000, 5);
        m.send(A, B, TpaManager.Type.TO_TARGET, false);
        var second = m.send(A, C, TpaManager.Type.TO_TARGET, false);
        assertTrue(second instanceof TpaManager.SendResult.Cooldown);
        assertEquals(10_000, ((TpaManager.SendResult.Cooldown) second).remainingMs());
        assertTrue(m.send(A, C, TpaManager.Type.TO_TARGET, true) instanceof TpaManager.SendResult.Sent);
        now.set(10_001);
        assertTrue(m.send(A, B, TpaManager.Type.HERE, false) instanceof TpaManager.SendResult.Sent);
    }

    @Test
    public void tpaOneOpenRequestPerSenderAndReplacementIsReported() {
        TpaManager m = new TpaManager(() -> 0, 60_000, 0, 5);
        m.send(A, B, TpaManager.Type.TO_TARGET, false);
        var r = (TpaManager.SendResult.Sent) m.send(A, C, TpaManager.Type.HERE, false);
        assertNotNull(r.replaced());
        assertEquals(B, r.replaced().target());
        assertTrue(m.pendingFor(B).isEmpty());
        assertEquals(1, m.pendingFor(C).size());
        assertTrue(m.send(A, A, TpaManager.Type.HERE, true) instanceof TpaManager.SendResult.Self);
    }

    @Test
    public void tpaRequestCanOnlyBeAnsweredOnce() {
        TpaManager m = new TpaManager(() -> 0, 60_000, 0, 5);
        m.send(A, B, TpaManager.Type.TO_TARGET, false);
        assertTrue(m.take(B, A).isPresent());
        assertTrue(m.take(B, A).isEmpty());
        assertTrue(m.take(B, null).isEmpty());
        assertTrue(m.cancel(A).isEmpty());
    }

    @Test
    public void tpaTakeWithoutNameTakesTheNewestAndWithNameTheRightOne() {
        TpaManager m = new TpaManager(() -> 0, 60_000, 0, 5);
        m.send(A, B, TpaManager.Type.TO_TARGET, false);
        m.send(C, B, TpaManager.Type.HERE, false);
        assertEquals(C, m.take(B, null).get().sender());
        assertEquals(A, m.take(B, A).get().sender());
    }

    @Test
    public void tpaCapsRequestsPerTargetButAllowsReplacingOwn() {
        TpaManager m = new TpaManager(() -> 0, 60_000, 0, 2);
        UUID d = UUID.randomUUID();
        assertTrue(m.send(A, B, TpaManager.Type.TO_TARGET, true) instanceof TpaManager.SendResult.Sent);
        assertTrue(m.send(C, B, TpaManager.Type.TO_TARGET, true) instanceof TpaManager.SendResult.Sent);
        assertTrue(m.send(d, B, TpaManager.Type.TO_TARGET, true) instanceof TpaManager.SendResult.TooMany);
        assertTrue(m.send(A, B, TpaManager.Type.HERE, true) instanceof TpaManager.SendResult.Sent);
    }

    @Test
    public void tpaForgetRemovesBothDirectionsButKeepsTheSpamCooldown() {
        AtomicLong now = new AtomicLong(0);
        TpaManager m = new TpaManager(now::get, 60_000, 10_000, 5);
        m.send(A, B, TpaManager.Type.TO_TARGET, false);
        m.send(C, A, TpaManager.Type.TO_TARGET, false);
        assertEquals(2, m.forget(A).size());
        assertEquals(0, m.size());
        assertTrue(m.send(A, B, TpaManager.Type.TO_TARGET, false) instanceof TpaManager.SendResult.Cooldown);
    }

    @Test
    public void tpaReconfigureAppliesToNewRequests() {
        AtomicLong now = new AtomicLong(0);
        TpaManager m = new TpaManager(now::get, 60_000, 0, 5);
        m.configure(5_000, 0, 5);
        m.send(A, B, TpaManager.Type.TO_TARGET, false);
        now.set(5_000);
        assertTrue(m.pendingFor(B).isEmpty());
    }

    // ---------------------------------------------------------------- rtp / safe spots

    @Test
    public void rtpPointsStayInsideTheRing() {
        Random random = new Random(42);
        for (int i = 0; i < 2000; i++) {
            int[] p = RtpFinder.randomPoint(random, 100, -50, 200, 1000);
            double distance = Math.hypot(p[0] - 100, p[1] + 50);
            assertTrue("too close: " + distance, distance >= 199);
            assertTrue("too far: " + distance, distance <= 1001);
        }
    }

    @Test
    public void rtpGroundRulesRejectLiquidsHazardsAndTrees() {
        assertTrue(RtpFinder.usable(Material.GRASS_BLOCK, true, Material.AIR, true, Material.AIR, true));
        assertFalse(RtpFinder.usable(Material.WATER, false, Material.AIR, true, Material.AIR, true));
        assertFalse(RtpFinder.usable(Material.LAVA, false, Material.AIR, true, Material.AIR, true));
        assertFalse(RtpFinder.usable(Material.MAGMA_BLOCK, true, Material.AIR, true, Material.AIR, true));
        assertFalse(RtpFinder.usable(Material.CACTUS, true, Material.AIR, true, Material.AIR, true));
        assertFalse(RtpFinder.usable(Material.OAK_LEAVES, true, Material.AIR, true, Material.AIR, true));
        assertFalse(RtpFinder.usable(Material.ICE, true, Material.AIR, true, Material.AIR, true));
        assertFalse(RtpFinder.usable(Material.STONE, true, Material.STONE, false, Material.AIR, true));
        assertFalse(RtpFinder.usable(Material.STONE, true, Material.AIR, true, Material.STONE, false));
        assertFalse(RtpFinder.usable(Material.STONE, true, Material.FIRE, true, Material.AIR, true));
        assertFalse(RtpFinder.usable(Material.AIR, false, Material.AIR, true, Material.AIR, true));
    }

    @Test
    public void hazardsAreRecognised() {
        assertTrue(SafeSpot.hazard(Material.LAVA));
        assertTrue(SafeSpot.hazard(Material.CAMPFIRE));
        assertFalse(SafeSpot.hazard(Material.STONE));
        assertFalse(SafeSpot.hazard(Material.WATER));
    }

    // ---------------------------------------------------------------- config

    @Test
    public void configDefaultsMatchTheSpecification() {
        UtilityConfig c = UtilityConfig.defaults();
        assertEquals(3, c.warmupSeconds());
        assertEquals(10, c.combatSeconds());
        assertFalse("only players tag by default", c.combatIncludeMobs());
        assertTrue(c.combatNotify());
        assertEquals(60, c.tpaExpireSeconds());
        assertEquals(600, c.rtpCooldownSeconds());
        assertEquals(3, c.homesDefault());
        assertEquals("home", c.defaultHomeName());
        assertEquals(List.of("wings"), c.trashKeepIds());
        assertEquals(Rarity.LEGENDARY, c.trashKeepRarity());
    }

    @Test
    public void configClampsNonsense() {
        YamlConfiguration y = new YamlConfiguration();
        y.set("teleport.warmup-seconds", -4);
        y.set("rtp.min-radius", 5000);
        y.set("rtp.max-radius", 10);
        y.set("near.default-radius", 9999);
        y.set("near.max-radius", 50);
        y.set("trash.keep-rarity", "nonsense");
        y.set("trash.keep-ids", List.of());
        y.set("homes.default-name", "BAD NAME!");
        UtilityConfig c = UtilityConfig.from(y);
        assertEquals(0, c.warmupSeconds());
        assertTrue(c.rtpMaxRadius() > c.rtpMinRadius());
        assertEquals(50, c.nearDefault());
        assertEquals(Rarity.LEGENDARY, c.trashKeepRarity());
        assertTrue(c.trashKeepIds().isEmpty());
        y.set("trash.keep-rarity", "");
        assertNull(UtilityConfig.from(y).trashKeepRarity());
        assertEquals("home", c.defaultHomeName());
    }
}
