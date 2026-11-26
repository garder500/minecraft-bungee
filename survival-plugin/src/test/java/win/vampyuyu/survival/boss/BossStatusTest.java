package win.vampyuyu.survival.boss;

import static org.junit.Assert.*;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import win.vampyuyu.survival.SurvivalPlugin;

/** The potion-free boss afflictions: attribute modifiers that come and go, damage ticks, and no vanilla potion at all. */
public class BossStatusTest {
    private ServerMock server;
    private SurvivalPlugin plugin;
    private PlayerMock player;

    @Before
    public void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(SurvivalPlugin.class);
        win.vampyuyu.survival.LegacyFeatureFixture.enable(plugin);
        player = server.addPlayer();
    }

    @After
    public void tearDown() {
        win.vampyuyu.survival.LegacyFeatureFixture.disable(plugin);
        MockBukkit.unmock();
    }

    private double mod(Attribute attribute, org.bukkit.NamespacedKey key) {
        var inst = player.getAttribute(attribute);
        var m = inst == null ? null : inst.getModifier(key);
        return m == null ? 0 : m.getAmount();
    }

    @Test
    public void strongestSlowWinsAndARootStopsThePlayer() {
        Map<StatusKind, StatusMath.Active> map = new EnumMap<>(StatusKind.class);
        map.put(StatusKind.SLOW, new StatusMath.Active(2, 100, 0));
        map.put(StatusKind.FROST, new StatusMath.Active(1, 100, 0.5));
        assertEquals(-0.3, StatusMath.speed(map, 0), 1e-9);
        map.put(StatusKind.SLOW, new StatusMath.Active(9, 100, 0));
        assertEquals("never more than 90 % without a root", -0.9, StatusMath.speed(map, 0), 1e-9);
        map.put(StatusKind.ROOT, new StatusMath.Active(1, 100, 0));
        assertEquals(-1.0, StatusMath.speed(map, 0), 1e-9);
        assertTrue(StatusMath.noJump(map, 0));
        assertEquals("expired statuses count for nothing", 0, StatusMath.speed(map, 100), 1e-9);
    }

    @Test
    public void liftBeatsFloatAndDecayNeverKills() {
        Map<StatusKind, StatusMath.Active> map = new EnumMap<>(StatusKind.class);
        map.put(StatusKind.FLOAT, new StatusMath.Active(1, 50, 0));
        assertTrue("a float slows the fall but the player still falls", StatusMath.gravity(map, 0) > -0.08);
        map.put(StatusKind.LIFT, new StatusMath.Active(1, 50, 0));
        assertTrue("a lift turns gravity over", StatusMath.gravity(map, 0) < -0.08);
        assertEquals(0, StatusMath.tickDamage(StatusKind.DECAY, 2.0, 1.0), 1e-9);
        assertEquals(0.5, StatusMath.tickDamage(StatusKind.DECAY, 2.0, 1.5), 1e-9);
        assertEquals(2.0, StatusMath.tickDamage(StatusKind.WITHERING, 2.0, 1.5), 1e-9);
    }

    @Test
    public void aRefreshNeverShortensOrWeakens() {
        StatusMath.Active old = new StatusMath.Active(3, 200, 1);
        StatusMath.Active merged = StatusMath.merge(old, new StatusMath.Active(1, 120, 0.5), 100);
        assertEquals(3, merged.level());
        assertEquals(200, merged.until());
        assertEquals(1, merged.dps(), 1e-9);
    }

    @Test
    public void modifiersComeAndGoWithoutAnyPotion() {
        BossStatus.apply(player, StatusKind.SLOW, 40, 2);
        BossStatus.apply(player, StatusKind.FLOAT, 40, 1);
        server.getScheduler().performTicks(2);
        assertEquals(-0.3, mod(Attribute.MOVEMENT_SPEED, BossStatus.SPEED), 1e-9);
        if (player.getAttribute(Attribute.GRAVITY) != null) {   // MockBukkit players have no gravity attribute; Paper's do
            assertEquals(StatusKind.FLOAT.gravity(), mod(Attribute.GRAVITY, BossStatus.GRAVITY), 1e-9);
        }
        assertTrue("no vanilla potion, no potion icon", player.getActivePotionEffects().isEmpty());
        assertTrue(BossStatus.has(player, StatusKind.SLOW));
        server.getScheduler().performTicks(45);
        assertFalse(BossStatus.has(player, StatusKind.SLOW));
        assertEquals("taken back when it runs out", 0, mod(Attribute.MOVEMENT_SPEED, BossStatus.SPEED), 1e-9);
        assertEquals(0, mod(Attribute.GRAVITY, BossStatus.GRAVITY), 1e-9);
    }

    @Test
    public void aRootTakesTheJumpAndMilkOrTheFightEndClearsEverything() {
        BossStatus.apply(player, StatusKind.ROOT, 200, 1);
        server.getScheduler().performTicks(1);
        assertEquals(-1.0, mod(Attribute.MOVEMENT_SPEED, BossStatus.SPEED), 1e-9);
        if (player.getAttribute(Attribute.JUMP_STRENGTH) != null) {   // absent on MockBukkit players
            assertEquals(-1.0, mod(Attribute.JUMP_STRENGTH, BossStatus.JUMP), 1e-9);
        }
        server.getPluginManager().callEvent(new PlayerItemConsumeEvent(player, new ItemStack(Material.MILK_BUCKET), EquipmentSlot.HAND));
        assertEquals(0, mod(Attribute.JUMP_STRENGTH, BossStatus.JUMP), 1e-9);
        assertEquals(0, mod(Attribute.MOVEMENT_SPEED, BossStatus.SPEED), 1e-9);
        BossStatus.apply(player, StatusKind.SLOW, 200, 1);
        BossStatus.clear(List.of(player));
        assertFalse(BossStatus.has(player, StatusKind.SLOW));
        assertEquals(0, mod(Attribute.MOVEMENT_SPEED, BossStatus.SPEED), 1e-9);
    }

    @Test
    public void statusesBecomePackVeilsWithTheVoidOnTop() {
        BossStatus.apply(player, StatusKind.BLIND, 100, 1);
        BossStatus.apply(player, StatusKind.FROST, 100, 1);
        BossStatus.apply(player, StatusKind.SLOW, 100, 1);
        String plain = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
            .serialize(win.vampyuyu.survival.core.ScreenVeil.of(player));
        assertEquals("three veils, each a glyph and its negative twin", 3 * win.vampyuyu.survival.core.ScreenVeil.GLYPH.length(), plain.length());
        assertEquals(5, BossStatus.veil(StatusKind.BLIND));
        BossStatus.clear(List.of(player));
        assertEquals("", net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
            .serialize(win.vampyuyu.survival.core.ScreenVeil.of(player)));
    }

    @Test
    public void decayTicksOnceASecondAndLeavesOneHealth() {
        player.setHealth(3.0);
        BossStatus.apply(player, StatusKind.DECAY, 200, 1, 2.0);
        for (int i = 0; i < 5; i++) {
            server.getScheduler().performTicks(20);
            player.setNoDamageTicks(0);   // MockBukkit does not run the invulnerability countdown between two ticks
        }
        assertTrue("it hurt", player.getHealth() < 3.0);
        assertTrue("never below one health", player.getHealth() >= 1.0 - 1e-6);
        assertFalse(player.isDead());
        assertTrue(player.getActivePotionEffects().isEmpty());
    }
}
