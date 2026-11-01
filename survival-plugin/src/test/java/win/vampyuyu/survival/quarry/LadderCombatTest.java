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
