package win.vampyuyu.survival.quarry;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
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
import win.vampyuyu.survival.api.StatsApi;
import win.vampyuyu.survival.items.combat.ElementTraits;

/**
 * The ore ladder hits for its own damage on the simple Skyblock (no items feature): the swords used to deal vanilla
 * damage (about 6) against bosses tuned for thousands of hits.
 */
public class LadderCombatTest {
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

    private double hit(PlayerMock player, LivingEntity target) {
        EntityDamageByEntityEvent event = new EntityDamageByEntityEvent(player, target,
            EntityDamageEvent.DamageCause.ENTITY_ATTACK, org.bukkit.damage.DamageSource.builder(org.bukkit.damage.DamageType.PLAYER_ATTACK).build(), 6);
        server.getPluginManager().callEvent(event);
        return event.getDamage();
    }

    @Test
    public void theProductionFeatureSetHasNoStatEngineSoTheLadderListenerWorks() {
        assertFalse("the simple Skyblock does not load the items feature", plugin.core().service(StatsApi.class).isPresent());
    }

    @Test
    public void swordEffectsWorkInADungeonWorld() {
        org.bukkit.World crypt = server.addSimpleWorld(win.vampyuyu.survival.core.SkyblockWorlds.dungeon("77"));
        PlayerMock player = server.addPlayer("Delver");
        player.teleport(crypt.getSpawnLocation());
        LivingEntity zombie = (LivingEntity) crypt.spawnEntity(crypt.getSpawnLocation(), EntityType.ZOMBIE);

        player.getInventory().setItemInMainHand(plugin.core().items().create("rimeblade"));
        hit(player, zombie);
        assertTrue("Frostbite slows in a dungeon", zombie.hasPotionEffect(org.bukkit.potion.PotionEffectType.SLOWNESS));

    }

    @Test
    public void aLadderBowsArrowDealsItsDamageScaledByTheDrawAndItsPunchDoesNot() {
        PlayerMock player = server.addPlayer("Archer");
        LivingEntity zombie = (LivingEntity) player.getWorld().spawnEntity(player.getLocation(), EntityType.ZOMBIE);
        org.bukkit.inventory.ItemStack bow = plugin.core().items().create("void_bow");
        player.getInventory().setItemInMainHand(bow);
        double punch = hit(player, zombie);
        assertTrue("a punch with the bow is no 180 damage hit: " + punch, punch < 50);
        double full = arrow(player, zombie, bow, 1f);
        assertTrue("a full draw of the Void Bow (180): " + full, full >= 180);
        double half = arrow(player, zombie, bow, 0.5f);
        assertTrue("half a draw is half the damage: " + half + " of " + full, half < full * 0.75);
    }

    private double arrow(PlayerMock player, LivingEntity target, org.bukkit.inventory.ItemStack bow, float force) {
        org.bukkit.entity.Arrow arrow = player.getWorld().spawn(player.getLocation(), org.bukkit.entity.Arrow.class);
        arrow.setShooter(player);
        server.getPluginManager().callEvent(new org.bukkit.event.entity.EntityShootBowEvent(player, bow,
            new org.bukkit.inventory.ItemStack(org.bukkit.Material.ARROW), arrow, org.bukkit.inventory.EquipmentSlot.HAND, force, true));
        EntityDamageByEntityEvent event = new EntityDamageByEntityEvent(arrow, target, EntityDamageEvent.DamageCause.PROJECTILE,
            org.bukkit.damage.DamageSource.builder(org.bukkit.damage.DamageType.ARROW).build(), 3);
        server.getPluginManager().callEvent(event);
        return event.getDamage();
    }

    @Test
    public void ladderSwordsHitForTheirDamageWithArmorMightElementAndSurge() {
        PlayerMock player = server.addPlayer("Knight");
        LivingEntity zombie = (LivingEntity) player.getWorld().spawnEntity(player.getLocation(), EntityType.ZOMBIE);
        player.getInventory().setItemInMainHand(plugin.core().items().create("bronze_cleaver"));
        double bare = hit(player, zombie);
        assertTrue("the Bronze Cleaver hits for at least its 40 damage, far above vanilla: " + bare, bare >= 40);

        for (String slot : new String[] {"helmet", "chestplate", "leggings", "boots"}) {
            org.bukkit.inventory.ItemStack piece = plugin.core().items().create("bronze_" + slot);
            switch (slot) {
                case "helmet" -> player.getInventory().setHelmet(piece);
                case "chestplate" -> player.getInventory().setChestplate(piece);
                case "leggings" -> player.getInventory().setLeggings(piece);
                default -> player.getInventory().setBoots(piece);
            }
        }
        LivingEntity other = (LivingEntity) player.getWorld().spawnEntity(player.getLocation(), EntityType.ZOMBIE);
        double armored = hit(player, other);
        assertTrue("bronze armor Might adds damage: " + bare + " -> " + armored, armored > bare);

        LivingEntity weak = (LivingEntity) player.getWorld().spawnEntity(player.getLocation(), EntityType.ZOMBIE);
        ElementTraits.write(weak, Element.EARTH, null);
        assertEquals("an Earth weakness is x1.5", armored * 1.5, hit(player, weak), 1e-6);
    }

    @Test
    public void aVanillaSwordIsLeftAlone() {
        PlayerMock player = server.addPlayer("Peasant");
        LivingEntity zombie = (LivingEntity) player.getWorld().spawnEntity(player.getLocation(), EntityType.ZOMBIE);
        player.getInventory().setItemInMainHand(new org.bukkit.inventory.ItemStack(org.bukkit.Material.IRON_SWORD));
        assertEquals(6, hit(player, zombie), 1e-9);
    }
}
