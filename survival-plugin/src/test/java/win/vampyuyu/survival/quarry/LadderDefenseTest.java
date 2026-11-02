package win.vampyuyu.survival.quarry;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.bukkit.attribute.Attribute;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.meta.Damageable;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import win.vampyuyu.survival.SurvivalPlugin;
import win.vampyuyu.survival.items.combat.PowerCurve;

/**
 * The defence and the durability of the ore ladder on the simple Skyblock (no stat engine): the Crypt Lord killed a
 * bronze-armoured player in one hit, and the gear wore out like iron.
 */
public class LadderDefenseTest {
    private ServerMock server;
    private SurvivalPlugin plugin;

    @Before
    public void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(SurvivalPlugin.class);
    }

    @After
    public void tearDown() {
        MockBukkit.unmock();
    }

    private void wearBronze(PlayerMock player) {
        player.getInventory().setHelmet(plugin.core().items().create("bronze_helmet"));
        player.getInventory().setChestplate(plugin.core().items().create("bronze_chestplate"));
        player.getInventory().setLeggings(plugin.core().items().create("bronze_leggings"));
        player.getInventory().setBoots(plugin.core().items().create("bronze_boots"));
    }

    @Test
    public void bronzeWardCutsHitsAndVitalityAddsHealth() {
        PlayerMock player = server.addPlayer("Tank");
        player.teleport(org.bukkit.Bukkit.getWorld(win.vampyuyu.survival.core.SkyblockWorlds.WORLD).getSpawnLocation()); // the hub is a safe zone
        wearBronze(player);
        LivingEntity zombie = (LivingEntity) player.getWorld().spawnEntity(player.getLocation(), EntityType.ZOMBIE);
        EntityDamageByEntityEvent hit = new EntityDamageByEntityEvent(zombie, player, EntityDamageEvent.DamageCause.ENTITY_ATTACK,
            org.bukkit.damage.DamageSource.builder(org.bukkit.damage.DamageType.MOB_ATTACK).build(), 10);
        server.getPluginManager().callEvent(hit);
        assertEquals("bronze Ward 41: x100/141", 10 * 100 / 141.0, hit.getDamage(), 1e-6);
        new LadderDefense(plugin.core()).refresh(player);
        assertEquals("bronze Vitality 50 adds 10 health", 30, player.getAttribute(Attribute.MAX_HEALTH).getValue(), 1e-6);
    }

    private void wear(PlayerMock player, String tier) {
        player.getInventory().setHelmet(plugin.core().items().create(tier + "_helmet"));
        player.getInventory().setChestplate(plugin.core().items().create(tier + "_chestplate"));
        player.getInventory().setLeggings(plugin.core().items().create(tier + "_leggings"));
        player.getInventory().setBoots(plugin.core().items().create(tier + "_boots"));
    }

    private double rimebladeHitOn(String armorTier) {
        PlayerMock attacker = server.addPlayer();
        PlayerMock victim = server.addPlayer();
        attacker.getInventory().setItemInMainHand(plugin.core().items().create("rimeblade"));
        if (armorTier != null) {
            wear(victim, armorTier);
        }
        EntityDamageByEntityEvent hit = new EntityDamageByEntityEvent(attacker, victim, EntityDamageEvent.DamageCause.ENTITY_ATTACK,
            org.bukkit.damage.DamageSource.builder(org.bukkit.damage.DamageType.PLAYER_ATTACK).build(), 6);
        // the island PvP flag is off outside an island here, so the two listeners run as the server orders them
        new LadderCombat(plugin.core()).onHit(hit);
        new LadderDefense(plugin.core()).onHurt(hit);
        return hit.getDamage();
    }

    @Test
    public void starcoreArmorProtectsTheMostAgainstARimeblade() {
        double bare = rimebladeHitOn(null);
        double bronze = rimebladeHitOn("bronze");
        double starcore = rimebladeHitOn("starcore");
        assertEquals("a Rimeblade hit on a bare player is its 70 damage", 70, bare, 1e-6);
        assertTrue("bronze protects: " + bronze, bronze < bare);
        assertTrue("starcore protects more than any other set: " + starcore + " vs " + bronze, starcore < bronze);
        assertEquals("starcore Ward 320: x100/420", 70 * 100 / 420.0, starcore, 1e-6);
        assertTrue("with its +76 health a starcore player takes 5 or more Rimeblade hits",
            Math.ceil(96 / starcore) >= 5);
    }

    @Test
    public void theCryptLordTakesEightHitsToDownATierOnePlayer() {
        PowerCurve curve = PowerCurve.load(plugin.core().data());
        double hit = win.vampyuyu.survival.boss.BossConfig.load(plugin.core().data()).bosses().get("crypt_lord").damage();
        assertEquals(curve.bossHitDamage(1), hit, 1e-9);
        double afterWard = hit * 100 / 141.0;
        int hits = (int) Math.ceil(curve.playerHealth(1) / afterWard);
        assertTrue("a bronze player survives several Crypt Lord hits, not one: " + hits, hits >= 7 && hits <= 9);
    }

    @Test
    public void helmetsHaveNoWornTextureSoTheir3dModelShows() {
        for (String tier : new String[] {"bronze", "rime", "ember", "void", "starcore"}) {
            var helmet = plugin.core().items().create(tier + "_helmet").getItemMeta();
            assertTrue(tier, helmet.hasEquippable());
            assertEquals(tier, org.bukkit.inventory.EquipmentSlot.HEAD, helmet.getEquippable().getSlot());
            assertEquals(tier + " has no equipment texture", null, helmet.getEquippable().getModel());
            var chest = plugin.core().items().create(tier + "_chestplate").getItemMeta();
            assertEquals(new org.bukkit.NamespacedKey("vampyuyu", tier), chest.getEquippable().getModel());
        }
    }

    @Test
    public void ladderGearLastsFarLongerThanIron() {
        assertEquals(1500, ((Damageable) plugin.core().items().create("bronze_cleaver").getItemMeta()).getMaxDamage());
        assertEquals(12000, ((Damageable) plugin.core().items().create("starcore_sword").getItemMeta()).getMaxDamage());
        assertEquals(2800, ((Damageable) plugin.core().items().create("void_helmet").getItemMeta()).getMaxDamage());
        assertEquals(5000, ((Damageable) plugin.core().items().create("ember_pick").getItemMeta()).getMaxDamage());
        // a stack made before the rework gets its durability back
        var old = plugin.core().items().create("rimeblade");
        var meta = (Damageable) old.getItemMeta();
        meta.setMaxDamage(250); // what an iron sword had
        old.setItemMeta(meta);
        assertTrue(plugin.core().items().upgradeDurability(old));
        assertEquals(3000, ((Damageable) old.getItemMeta()).getMaxDamage());
    }
}
