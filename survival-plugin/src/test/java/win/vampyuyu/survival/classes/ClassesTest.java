package win.vampyuyu.survival.classes;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.List;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.inventory.ItemStack;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.entity.ZombieMock;
import org.mockbukkit.mockbukkit.world.WorldMock;
import win.vampyuyu.survival.SurvivalPlugin;
import win.vampyuyu.survival.api.ClassApi;
import win.vampyuyu.survival.api.PlayerClass;

/** The four Novice classes on the simple Skyblock: choice, bonuses and each active. */
public class ClassesTest {
    private ServerMock server;
    private SurvivalPlugin plugin;
    private ClassService classes;
    /** Players join in the hub, a safe zone that cancels damage: the fights happen here. Nothing blocks a Rift Step (the mock has no ray trace). */
    private WorldMock arena;

    @Before
    public void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(SurvivalPlugin.class);
        classes = plugin.core().require(ClassService.class);
        arena = new WorldMock() {
            @Override
            public org.bukkit.util.RayTraceResult rayTraceBlocks(org.bukkit.Location start, org.bukkit.util.Vector direction, double maxDistance,
                    org.bukkit.FluidCollisionMode fluids, boolean ignorePassable) {
                return null;
            }

            @Override
            public <T extends org.bukkit.entity.Entity> T createEntity(org.bukkit.Location location, Class<T> type) {
                T entity = super.createEntity(location, type); // a launched projectile stays out of the mock world otherwise
                server.registerEntity((org.mockbukkit.mockbukkit.entity.EntityMock) entity);
                return entity;
            }
        };
        arena.setName("class_arena");
        server.addWorld(arena);
    }

    @After
    public void tearDown() {
        MockBukkit.unmock();
    }

    private PlayerMock player(String name, PlayerClass chosen) {
        PlayerMock p = server.addPlayer(name);
        p.setLocation(arena.getSpawnLocation()); // out of the mode check: the arena is not a Skyblock world
        if (chosen != null) {
            assertTrue(classes.choose(p.getUniqueId(), chosen));
        }
        return p;
    }

    private LivingEntity zombie(PlayerMock near) {
        // the mock has no damage with a source, which a class spell uses
        ZombieMock zombie = new ZombieMock(server, java.util.UUID.randomUUID()) {
            @Override
            public void damage(double amount, org.bukkit.damage.DamageSource source) {
                damage(amount);
            }
        };
        zombie.setLocation(near.getLocation());
        server.registerEntity(zombie);
        return zombie;
    }

    private double hit(PlayerMock player, LivingEntity target) {
        EntityDamageByEntityEvent event = new EntityDamageByEntityEvent(player, target, EntityDamageEvent.DamageCause.ENTITY_ATTACK,
            org.bukkit.damage.DamageSource.builder(org.bukkit.damage.DamageType.PLAYER_ATTACK).build(), 6);
        server.getPluginManager().callEvent(event);
        return event.getDamage();
    }

    private double hurt(PlayerMock victim, double damage) {
        LivingEntity mob = zombie(victim);
        EntityDamageByEntityEvent event = new EntityDamageByEntityEvent(mob, victim, EntityDamageEvent.DamageCause.ENTITY_ATTACK,
            org.bukkit.damage.DamageSource.builder(org.bukkit.damage.DamageType.MOB_ATTACK).build(), damage);
        server.getPluginManager().callEvent(event);
        return event.getDamage();
    }

    private void hold(PlayerMock p, String id) {
        p.getInventory().setItemInMainHand(plugin.core().items().create(id));
    }

    @Test
    public void aClassIsChosenOnceAndKeptPerPlayer() {
        PlayerMock p = player("Newbie", null);
        assertTrue(classes.classOf(p.getUniqueId()).isEmpty());
        assertTrue(classes.choose(p.getUniqueId(), PlayerClass.RANGER));
        assertFalse("a second choice is refused: only the class-change quest changes it", classes.choose(p.getUniqueId(), PlayerClass.VANGUARD));
        assertEquals(PlayerClass.RANGER, classes.classOf(p.getUniqueId()).get());
        classes.set(p.getUniqueId(), PlayerClass.VANGUARD);
        assertEquals("set is the entry point of the class-change quest", PlayerClass.VANGUARD, classes.classOf(p.getUniqueId()).get());
        assertTrue(plugin.core().service(ClassApi.class).isPresent());
    }

    @Test
    public void commandChoosesAClass() {
        PlayerMock p = server.addPlayer("Cmd");
        p.performCommand("class choose channeler");
        assertEquals(PlayerClass.CHANNELER, classes.classOf(p.getUniqueId()).get());
    }

    @Test
    public void classBonusesAreInsideTheMetaCapAndNoClassMeansNoBonus() {
        PlayerMock none = player("Plain", null);
        assertEquals(0, classes.mightBonus(none.getUniqueId()), 0);
        for (PlayerClass c : PlayerClass.values()) {
            PlayerMock p = player("P" + c, c);
            assertTrue(classes.mightBonus(p.getUniqueId()) <= 25);
            assertTrue(classes.dexterityBonus(p.getUniqueId()) <= 25);
            classes.refresh(p);
            double bonus = p.getAttribute(Attribute.MAX_HEALTH).getValue() - 20;
            assertTrue(c + " health bonus " + bonus + " is at most 100 PV / 5", bonus <= 100 / 5.0 + 1e-9);
        }
        PlayerMock tank = player("Tank", PlayerClass.VANGUARD);
        classes.refresh(tank);
        assertEquals("Vanguard: +60 PV is +12 health", 32, tank.getAttribute(Attribute.MAX_HEALTH).getValue(), 1e-9);
    }

    @Test
    public void migratingAwayFromAClassRemovesItsHealth() {
        PlayerMock tank = player("Swap", PlayerClass.VANGUARD);
        assertEquals(32, tank.getAttribute(Attribute.MAX_HEALTH).getValue(), 1e-9);
        classes.set(tank.getUniqueId(), null);
        assertEquals(20, tank.getAttribute(Attribute.MAX_HEALTH).getValue(), 1e-9);
    }

    @Test
    public void bulwarkTakesThirtyPercentForSixSecondsAndCostsAether() {
        PlayerMock tank = player("Bulwark", PlayerClass.VANGUARD);
        double before = hurt(tank, 100);
        assertEquals(ClassService.Cast.OK, classes.cast(tank));
        assertEquals("Bulwark: -30%", before * 0.7, hurt(tank, 100), 1e-6);
        assertEquals("Bulwark costs 30 Aether", 70, classes.aether(tank), 0.5);
        assertEquals("recharging", ClassService.Cast.COOLDOWN, classes.cast(tank));
    }

    @Test
    public void vanguardTakesFivePercentLessNearAnAlly() {
        PlayerMock tank = player("Wall", PlayerClass.VANGUARD);
        double alone = hurt(tank, 100);
        server.addPlayer("Friend").setLocation(tank.getLocation());
        assertEquals(alone * 0.95, hurt(tank, 100), 1e-6);
    }

    @Test
    public void duelistSurgesEveryFourthHitAndRiftStepGuaranteesOne() {
        PlayerMock duelist = player("Duelist", PlayerClass.DUELIST);
        hold(duelist, "bronze_cleaver");
        LivingEntity target = zombie(duelist);
        double normal = hit(duelist, target);
        hit(duelist, target);
        hit(duelist, target);
        double fourth = hit(duelist, target);
        assertTrue("4th hit is a Surge: " + normal + " -> " + fourth, fourth > normal * 1.5);

        assertEquals(ClassService.Cast.OK, classes.cast(duelist));
        double afterStep = hit(duelist, target);
        assertTrue("Rift Step: the next hit is a Surge: " + afterStep, afterStep > normal * 1.5);
        assertFalse("only one", hit(duelist, target) > normal * 1.5);
    }

    @Test
    public void otherClassesSurgeOnTheFifthHit() {
        PlayerMock knight = player("Knight", PlayerClass.VANGUARD);
        hold(knight, "bronze_cleaver");
        LivingEntity target = zombie(knight);
        double normal = hit(knight, target);
        hit(knight, target);
        hit(knight, target);
        assertEquals("no Surge on the 4th hit", normal, hit(knight, target), 1e-9);
        assertTrue(hit(knight, target) > normal * 1.5);
    }

    @Test
    public void riftStepMovesTheDuelistForward() {
        PlayerMock duelist = player("Blink", PlayerClass.DUELIST);
        duelist.teleport(duelist.getLocation().setDirection(new org.bukkit.util.Vector(1, 0, 0)));
        double x = duelist.getLocation().getX();
        assertEquals(ClassService.Cast.OK, classes.cast(duelist));
        assertTrue("moved: " + x + " -> " + duelist.getLocation().getX(), duelist.getLocation().getX() - x <= 6.01);
    }

    @Test
    public void elementalBurstHitsNearbyMonstersWithTheWeaponAndSparesPlayers() {
        PlayerMock mage = player("Mage", PlayerClass.CHANNELER);
        assertEquals("needs a weapon", ClassService.Cast.NO_WEAPON, classes.cast(mage));
        hold(mage, "bronze_cleaver");
        LivingEntity a = zombie(mage);
        LivingEntity b = zombie(mage);
        PlayerMock friend = server.addPlayer("Friend");
        friend.setLocation(mage.getLocation());
        double friendHealth = friend.getHealth();
        assertEquals(ClassService.Cast.OK, classes.cast(mage));
        assertTrue("zombie hurt", a.getHealth() < a.getAttribute(Attribute.MAX_HEALTH).getValue());
        assertTrue("both hurt", b.getHealth() < b.getAttribute(Attribute.MAX_HEALTH).getValue());
        assertEquals("players are spared", friendHealth, friend.getHealth(), 0);
    }

    @Test
    public void channelerAddsTenPercentElementalWeakness() {
        PlayerMock mage = player("Elem", PlayerClass.CHANNELER);
        assertEquals(10, classes.attunementBonus(mage.getUniqueId()), 0);
        assertEquals(0, classes.attunementBonus(player("Other", PlayerClass.RANGER).getUniqueId()), 0);
    }

    @Test
    public void galeVolleyShootsThreeArrowsOfFortyPercent() {
        PlayerMock ranger = player("Ranger", PlayerClass.RANGER);
        assertEquals("needs a bow", ClassService.Cast.NO_BOW, classes.cast(ranger));
        hold(ranger, "void_bow");
        long before = ranger.getWorld().getEntitiesByClass(Arrow.class).size();
        assertEquals(ClassService.Cast.OK, classes.cast(ranger));
        List<Arrow> arrows = List.copyOf(ranger.getWorld().getEntitiesByClass(Arrow.class));
        assertEquals(3, arrows.size() - before);
        ItemStack bow = ranger.getInventory().getItemInMainHand();
        LivingEntity target = zombie(ranger);
        Arrow volley = arrows.get(arrows.size() - 1);
        volley.setShooter(ranger); // the mock launches without a shooter
        EntityDamageByEntityEvent event = new EntityDamageByEntityEvent(volley, target, EntityDamageEvent.DamageCause.PROJECTILE,
            org.bukkit.damage.DamageSource.builder(org.bukkit.damage.DamageType.ARROW).build(), 3);
        server.getPluginManager().callEvent(event);
        Arrow full = ranger.getWorld().spawn(ranger.getLocation(), Arrow.class);
        full.setShooter(ranger);
        server.getPluginManager().callEvent(new EntityShootBowEvent(ranger, bow, new ItemStack(org.bukkit.Material.ARROW), full,
            org.bukkit.inventory.EquipmentSlot.HAND, 1f, true));
        EntityDamageByEntityEvent fullHit = new EntityDamageByEntityEvent(full, zombie(ranger), EntityDamageEvent.DamageCause.PROJECTILE,
            org.bukkit.damage.DamageSource.builder(org.bukkit.damage.DamageType.ARROW).build(), 3);
        server.getPluginManager().callEvent(fullHit);
        assertEquals("each volley arrow is 40% of a full draw", fullHit.getDamage() * 0.4, event.getDamage(), 1e-6);
    }

    @Test
    public void rangerGetsSpeedInCombatOnly() {
        PlayerMock ranger = player("Fast", PlayerClass.RANGER);
        double base = ranger.getAttribute(Attribute.MOVEMENT_SPEED).getValue();
        hurt(ranger, 1);
        assertEquals("+10% in combat", base * 1.1, ranger.getAttribute(Attribute.MOVEMENT_SPEED).getValue(), 1e-9);
        assertTrue(classes.inCombat(ranger.getUniqueId()));
    }

    @Test
    public void noContentNeedsAClass() {
        PlayerMock p = player("Classless", null);
        hold(p, "bronze_cleaver");
        double d = hit(p, zombie(p));
        assertTrue("a classless player fights normally: " + d, d >= 40);
        assertEquals(ClassService.Cast.NO_CLASS, classes.cast(p));
    }
}
