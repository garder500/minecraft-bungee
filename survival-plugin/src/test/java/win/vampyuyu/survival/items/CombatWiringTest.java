package win.vampyuyu.survival.items;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import org.bukkit.Location;
import org.bukkit.entity.Zombie;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import win.vampyuyu.survival.SurvivalPlugin;
import win.vampyuyu.survival.api.Element;
import win.vampyuyu.survival.api.Stat;
import win.vampyuyu.survival.api.StatsApi;
import win.vampyuyu.survival.core.Core;
import win.vampyuyu.survival.items.combat.ElementTraits;

/**
 * The damage formula through real events: the weapon's element and the victim's weakness and resistance, Attunement, the
 * Surge on every fifth charged hit, no random crits, and the Ward of the player taking a hit.
 */
public class CombatWiringTest {
    private ServerMock server;
    private SurvivalPlugin plugin;
    private Core core;
    private org.bukkit.World world;
    private PlayerMock player;

    @Before
    public void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(SurvivalPlugin.class);
        win.vampyuyu.survival.LegacyFeatureFixture.enable(plugin);
        core = plugin.core();
        world = server.addSimpleWorld("sb_world");
        player = server.addPlayer();
        player.teleport(new Location(world, 0, 64, 0));
        player.getInventory().setItemInMainHand(core.items().create("rimeblade")); // Frost, 70 damage, no stats
        stats().recompute(player);
    }

    @After
    public void tearDown() {
        win.vampyuyu.survival.LegacyFeatureFixture.disable(plugin);
        MockBukkit.unmock();
    }

    private StatsApi stats() {
        return core.service(StatsApi.class).orElseThrow();
    }

    private Zombie zombie(Element weak, Element resists) {
        Zombie z = world.spawn(new Location(world, 3, 64, 3), Zombie.class);
        ElementTraits.write(z, weak, resists);
        return z;
    }

    private double hit(Zombie target) {
        EntityDamageByEntityEvent event = new EntityDamageByEntityEvent(player, target, EntityDamageEvent.DamageCause.ENTITY_ATTACK, 1.0);
        server.getPluginManager().callEvent(event);
        return event.getDamage();
    }

    @Test
    public void theWeaponComesFromTheLadderWithItsElement() {
        var def = core.items().def("rimeblade").orElseThrow();
        assertEquals(Element.FROST, def.element());
        assertEquals(70.0, def.pdcDoubles().get("damage"), 0);
        assertEquals(0.0, stats().stat(player, Stat.MIGHT), 0);
    }

    @Test
    public void aWeaknessHitsOneAndAHalfTimesHarderAndAResistanceSixtyPercent() {
        assertEquals(70.0 * 1.5, hit(zombie(Element.FROST, Element.EMBER)), 1e-6);
        assertEquals(70.0 * 0.6, hit(zombie(Element.EMBER, Element.FROST)), 1e-6);
        assertEquals(70.0, hit(zombie(Element.EMBER, Element.VOID)), 1e-6);
        assertEquals(70.0, hit(zombie(null, null)), 1e-6);
    }

    @Test
    public void attunementAndMightAddUp() {
        stats().addModifier(player.getUniqueId(), "test", Stat.ATTUNEMENT, 20);
        stats().addModifier(player.getUniqueId(), "test", Stat.MIGHT, 50);
        stats().recompute(player);
        // weapon x (1 + might/100) x (1.5 + attunement/100)
        assertEquals(70.0 * 1.5 * 1.7, hit(zombie(Element.FROST, null)), 1e-6);
        // attunement does nothing against a neutral target or a resisted one
        assertEquals(70.0 * 1.5, hit(zombie(null, null)), 1e-6);
        assertEquals(70.0 * 1.5 * 0.6, hit(zombie(null, Element.FROST)), 1e-6);
    }

    @Test
    public void everyFifthHitOnTheSameTargetIsASurgeAndNothingIsRandom() {
        Zombie target = zombie(Element.FROST, null);
        for (int run = 0; run < 3; run++) {
            for (int i = 1; i <= 4; i++) {
                assertEquals("run " + run + " hit " + i, 105.0, hit(target), 1e-6);
            }
            assertEquals("run " + run + " fifth hit", 210.0, hit(target), 1e-6);
        }
        // another target starts a new count, the old one forgets its progress
        Zombie other = zombie(Element.FROST, null);
        for (int i = 1; i <= 4; i++) {
            assertEquals(105.0, hit(other), 1e-6);
        }
        assertEquals(210.0, hit(other), 1e-6);
    }

    @Test
    public void wardReducesWhatAPlayerTakes() {
        // incoming: vanilla damage x5 (mob scale) x 100 / (100 + ward)
        Zombie z = zombie(null, null);
        stats().recompute(player);
        double base = takeFrom(z);
        stats().addModifier(player.getUniqueId(), "test", Stat.WARD, 100);
        stats().recompute(player);
        assertEquals(base / 2, takeFrom(z), 1e-6);
        stats().addModifier(player.getUniqueId(), "test", Stat.WARD, 300);
        stats().recompute(player);
        assertEquals(base / 4, takeFrom(z), 1e-6);
        assertNotNull(base);
    }

    private double takeFrom(Zombie attacker) {
        EntityDamageByEntityEvent event = new EntityDamageByEntityEvent(attacker, player, EntityDamageEvent.DamageCause.ENTITY_ATTACK, 3.0);
        server.getPluginManager().callEvent(event);
        return event.getDamage();
    }
}
