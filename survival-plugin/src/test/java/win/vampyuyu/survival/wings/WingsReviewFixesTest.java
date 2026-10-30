package win.vampyuyu.survival.wings;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffectType;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import win.vampyuyu.survival.SurvivalPlugin;
import win.vampyuyu.survival.api.Stat;
import win.vampyuyu.survival.api.WingsApi;

/** Regression tests for the review of the wings feature (energy persistence, glide, void, containers, abilities). */
public class WingsReviewFixesTest {
    private ServerMock server;
    private SurvivalPlugin plugin;
    private WingsService service;
    private FlightManager flight;
    private PlayerMock alice;

    @Before
    public void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(SurvivalPlugin.class);
        service = (WingsService) plugin.core().service(WingsApi.class).orElseThrow();
        flight = service.flight();
        alice = server.addPlayer("Alice");
    }

    @After
    public void tearDown() {
        MockBukkit.unmock();
    }

    private WingsItems items() {
        return service.wingsItems();
    }

    private ItemStack wear(PlayerMock p, int level) {
        service.adminGive(p, level, "t");
        for (ItemStack s : p.getInventory().getContents()) {
            if (items().isWings(s)) {
                p.getInventory().setChestplate(s.clone());
                p.getInventory().remove(s);
                return s.clone();
            }
        }
        fail("no wings");
        return null;
    }

    private void tick(long now) {
        flight.tickPlayer(alice, now);
    }

    // ---------------------------------------------------------------- 1. energy survives the chest slot

    @Test
    public void theBarAndTheLockoutSurviveTakingTheWingsOff() {
        ItemStack wings = wear(alice, 1);
        long now = System.currentTimeMillis();
        tick(now);
        FlightManager.State st = flight.state(alice.getUniqueId());
        st.energy.drain(10_000);
        st.energy.regen(3, 30);
        assertTrue(st.energy.exhausted());
        alice.getInventory().setChestplate(null);
        tick(now + 100);
        assertFalse(flight.hasState(alice.getUniqueId()));
        WingsData d = service.data(alice.getUniqueId());
        assertEquals(3, d.energy, 1e-6);
        assertTrue(d.exhausted);

        alice.getInventory().setChestplate(wings.clone());
        tick(now + 200);
        FlightManager.State again = flight.state(alice.getUniqueId());
        assertTrue("still locked out", again.energy.exhausted());
        assertTrue("about 3 plus 0.1 s of recharge: " + again.energy.value(), again.energy.value() < 6);
        assertFalse("cannot take off", alice.getAllowFlight());
        assertEquals("consumed: the live bar is the truth", -1, d.energy, 1e-9);
        assertFalse(d.exhausted);
    }

    @Test
    public void timeAwayRechargesTheSavedBar() {
        ItemStack wings = wear(alice, 1);
        long now = System.currentTimeMillis();
        tick(now);
        flight.state(alice.getUniqueId()).energy.drain(10_000);
        alice.getInventory().setChestplate(null);
        tick(now + 100);
        alice.getInventory().setChestplate(wings.clone());
        tick(now + 100 + 120_000);
        FlightManager.State st = flight.state(alice.getUniqueId());
        assertTrue(st.energy.full());
        assertFalse(st.energy.exhausted());
        assertTrue(alice.getAllowFlight());
    }

    @Test
    public void energyRestoreKeepsTheFlag() {
        Energy e = new Energy(100);
        e.restore(5, true);
        assertTrue(e.exhausted());
        e.restore(5, false);
        assertFalse(e.exhausted());
        e.restore(0, false);
        assertTrue("empty is always exhausted", e.exhausted());
    }

    @Test
    public void deathResetsTheBarAndTheLockout() {
        wear(alice, 1);
        long now = System.currentTimeMillis();
        tick(now);
        flight.state(alice.getUniqueId()).energy.drain(10_000);
        var death = new org.bukkit.event.entity.PlayerDeathEvent(alice,
            org.bukkit.damage.DamageSource.builder(org.bukkit.damage.DamageType.GENERIC).build(), new ArrayList<>(), 0,
            net.kyori.adventure.text.Component.text("died"), true);
        server.getPluginManager().callEvent(death);
        WingsData d = service.data(alice.getUniqueId());
        assertEquals(-1, d.energy, 1e-9);
        assertFalse(d.exhausted);
    }

    // ---------------------------------------------------------------- 2/5. gliding

    private void airborne() {
        alice.teleport(alice.getLocation().add(0, 50, 0));
        alice.setOnGround(false);
    }

    @Test
    public void aRunningGlideEndsWhereFlyingIsRefusedWithASlowFall() {
        wear(alice, 1);
        long now = System.currentTimeMillis();
        tick(now);
        airborne();
        alice.setGliding(true);
        assertTrue(alice.isGliding());
        flight.tagCombat(alice.getUniqueId());
        tick(now + 100);
        assertFalse("the glide is over", alice.isGliding());
        assertNotNull("combat is an emergency reason now", alice.getPotionEffect(PotionEffectType.SLOW_FALLING));
    }

    @Test
    public void glidingEndsInANoFlyZone() {
        wear(alice, 1);
        long now = System.currentTimeMillis();
        tick(now);
        airborne();
        alice.setGliding(true);
        alice.addScoreboardTag("sb_no_fly");
        tick(now + 100);
        assertFalse(alice.isGliding());
    }

    @Test
    public void glidingCostsEnergyAndTheLastDropEndsIt() {
        wear(alice, 1);
        long now = System.currentTimeMillis();
        tick(now);
        airborne();
        alice.setGliding(true);
        FlightManager.State st = flight.state(alice.getUniqueId());
        double before = st.energy.value();
        for (int i = 1; i <= 10; i++) {
            tick(now + i * 100L);
        }
        assertTrue("about 1 per second", before - st.energy.value() > 0.5 && before - st.energy.value() < 2);
        st.energy.drain(st.energy.value() - 0.05);
        tick(now + 1200);
        tick(now + 1300);
        assertFalse(alice.isGliding());
        assertFalse("the flight allowance is gone too", alice.getAllowFlight());
        assertTrue(st.energy.exhausted());
        assertNotNull(alice.getPotionEffect(PotionEffectType.SLOW_FALLING));
    }

    @Test
    public void startingAGlideIsRefusedWhenExhausted() {
        wear(alice, 1);
        long now = System.currentTimeMillis();
        tick(now);
        flight.state(alice.getUniqueId()).energy.drain(10_000);
        var event = new org.bukkit.event.entity.EntityToggleGlideEvent(alice, true);
        server.getPluginManager().callEvent(event);
        assertTrue(event.isCancelled());
    }

    // ---------------------------------------------------------------- 3. void rescue

    @Test
    public void voidDamageDuringASlowFallBecomesARescueToTheLastGround() {
        wear(alice, 1);
        long now = System.currentTimeMillis();
        alice.setOnGround(true);
        Location ground = alice.getLocation().clone();
        for (int i = 0; i < 6; i++) {  // the safe ground is recorded every fifth tick of the manager
            flight.tickPlayer(alice, now + i * 100L);
        }
        FlightManager.State st = flight.state(alice.getUniqueId());
        st.safeWorld = ground.getWorld().getName();
        st.safeX = ground.getX();
        st.safeY = ground.getY();
        st.safeZ = ground.getZ();
        tick(now + 700);
        alice.setFlying(true);
        st.energy.drain(10_000);
        alice.teleport(ground.clone().add(500, 40, 500));
        alice.setOnGround(false);
        alice.setFlying(true);
        tick(now + 800);
        assertTrue(st.emergency);
        EntityDamageEvent hit = new EntityDamageEvent(alice, EntityDamageEvent.DamageCause.VOID, 4.0);
        server.getPluginManager().callEvent(hit);
        assertTrue(hit.isCancelled());
        assertEquals(ground.getX(), alice.getLocation().getX(), 1e-6);
        assertEquals(ground.getZ(), alice.getLocation().getZ(), 1e-6);
        assertFalse(st.emergency);
        assertNull("our slow fall is gone", alice.getPotionEffect(PotionEffectType.SLOW_FALLING));
    }

    @Test
    public void aBelowTheWorldEmergencyRescuesBeforeTheVoidDamage() {
        wear(alice, 1);
        long now = System.currentTimeMillis();
        tick(now);
        FlightManager.State st = flight.state(alice.getUniqueId());
        Location start = alice.getLocation().clone();
        st.safeWorld = start.getWorld().getName();
        st.safeX = start.getX();
        st.safeY = start.getY();
        st.safeZ = start.getZ();
        alice.setFlying(true);
        alice.teleport(start.clone().add(0, 30, 0));
        alice.setOnGround(false);
        st.energy.drain(10_000);
        tick(now + 100);
        assertTrue(st.emergency);
        alice.teleport(new Location(start.getWorld(), start.getX() + 9, start.getWorld().getMinHeight() - 1, start.getZ()));
        tick(now + 200);
        assertFalse(st.emergency);
        assertEquals(start.getX(), alice.getLocation().getX(), 1e-6);
    }

    @Test
    public void voidDamageIsNeverCancelledForAnOrdinaryPlayer() {
        EntityDamageEvent hit = new EntityDamageEvent(alice, EntityDamageEvent.DamageCause.VOID, 4.0);
        server.getPluginManager().callEvent(hit);
        assertFalse(hit.isCancelled());
        wear(alice, 1);
        tick(System.currentTimeMillis());
        EntityDamageEvent again = new EntityDamageEvent(alice, EntityDamageEvent.DamageCause.VOID, 4.0);
        server.getPluginManager().callEvent(again);
        assertFalse("wearing the Wings alone does not protect from the void", again.isCancelled());
    }

    // ---------------------------------------------------------------- 4. combat emergency

    @Test
    public void defaultsGiveASlowFallAfterACombatStop() {
        assertTrue(service.settings().emergencyReasons.contains(WingsSettings.Reason.COMBAT));
        wear(alice, 1);
        long now = System.currentTimeMillis();
        tick(now);
        alice.setFlying(true);
        alice.teleport(alice.getLocation().add(0, 40, 0));
        alice.setOnGround(false);
        flight.tagCombat(alice.getUniqueId());
        tick(now + 100);
        assertNotNull(alice.getPotionEffect(PotionEffectType.SLOW_FALLING));
        EntityDamageEvent fall = new EntityDamageEvent(alice, EntityDamageEvent.DamageCause.FALL, 12.0);
        server.getPluginManager().callEvent(fall);
        assertTrue(fall.isCancelled());
    }

    // ---------------------------------------------------------------- 5. rocket boosts

    @Test
    public void rocketBoostsFollowTheRulesAndCostEnergy() {
        wear(alice, 1);
        long now = System.currentTimeMillis();
        tick(now);
        FlightManager.State st = flight.state(alice.getUniqueId());
        var rocket = alice.getWorld().spawn(alice.getLocation(), org.bukkit.entity.Firework.class);
        double before = st.energy.value();
        var boost = new com.destroystokyo.paper.event.player.PlayerElytraBoostEvent(alice, new ItemStack(Material.FIREWORK_ROCKET), rocket, org.bukkit.inventory.EquipmentSlot.HAND);
        server.getPluginManager().callEvent(boost);
        assertFalse(boost.isCancelled());
        assertEquals(12, before - st.energy.value(), 1e-6);
        flight.tagCombat(alice.getUniqueId());
        var refused = new com.destroystokyo.paper.event.player.PlayerElytraBoostEvent(alice, new ItemStack(Material.FIREWORK_ROCKET), rocket, org.bukkit.inventory.EquipmentSlot.HAND);
        server.getPluginManager().callEvent(refused);
        assertTrue(refused.isCancelled());
    }

    // ---------------------------------------------------------------- 6. containers

    private InventoryClickEvent click(org.bukkit.inventory.InventoryView view, int rawSlot, ClickType type, InventoryAction action) {
        InventoryClickEvent e = new InventoryClickEvent(view, org.bukkit.event.inventory.InventoryType.SlotType.CONTAINER, rawSlot, type, action);
        server.getPluginManager().callEvent(e);
        return e;
    }

    @Test
    public void wingsCannotBePlacedInAShulkerBoxChestOrBundle() {
        service.grantOnce(alice, "t");
        ItemStack mine = alice.getInventory().getContents()[service.findValidSlot(alice)].clone();
        var shulker = server.createInventory(null, InventoryType.SHULKER_BOX);
        var view = alice.openInventory(shulker);
        alice.setItemOnCursor(mine);
        assertTrue("cursor into the box", click(view, 3, ClickType.LEFT, InventoryAction.PLACE_ALL).isCancelled());
        alice.setItemOnCursor(null);
        // shift click from the player's own inventory
        int bottom = shulker.getSize() + 2;
        alice.getInventory().setItem(2, mine.clone());
        assertTrue("shift click", click(view, bottom, ClickType.SHIFT_LEFT, InventoryAction.MOVE_TO_OTHER_INVENTORY).isCancelled());
        // a harmless item still moves
        alice.getInventory().setItem(3, new ItemStack(Material.DIRT));
        assertFalse(click(view, shulker.getSize() + 3, ClickType.SHIFT_LEFT, InventoryAction.MOVE_TO_OTHER_INVENTORY).isCancelled());
    }

    @Test
    public void wingsCannotGoIntoABundleAndAHopperNeverMovesThem() {
        service.grantOnce(alice, "t");
        ItemStack mine = alice.getInventory().getContents()[service.findValidSlot(alice)].clone();
        ItemStack bundle = new ItemStack(Material.BUNDLE);
        // MockBukkit's player view maps raw slots differently: assert on the rule itself
        assertTrue(WingsGuard.isBundle(bundle));
        assertFalse(WingsGuard.isBundle(new ItemStack(Material.DIRT)));
        assertTrue(WingsGuard.isShulker(new ItemStack(Material.RED_SHULKER_BOX)));
        var move = new org.bukkit.event.inventory.InventoryMoveItemEvent(server.createInventory(null, InventoryType.CHEST),
            mine, server.createInventory(null, InventoryType.HOPPER), true);
        server.getPluginManager().callEvent(move);
        assertTrue(move.isCancelled());
    }

    @Test
    public void aBundleClickWithWingsOnTheCursorIsRefusedAndOtherItemsAreNot() {
        service.grantOnce(alice, "t");
        ItemStack mine = alice.getInventory().getContents()[service.findValidSlot(alice)].clone();
        var chest = server.createInventory(null, InventoryType.CHEST);
        chest.setItem(0, new ItemStack(Material.BUNDLE));
        var view = alice.openInventory(chest);
        alice.setItemOnCursor(mine);
        assertTrue(click(view, 0, ClickType.RIGHT, InventoryAction.PLACE_ONE).isCancelled());
        alice.setItemOnCursor(new ItemStack(Material.DIRT));
        assertFalse(click(view, 0, ClickType.RIGHT, InventoryAction.PLACE_ONE).isCancelled());
        // the other way round: a bundle on the cursor over the wings
        chest.setItem(1, mine.clone());
        alice.setItemOnCursor(new ItemStack(Material.BUNDLE));
        assertTrue(click(view, 1, ClickType.RIGHT, InventoryAction.PLACE_ONE).isCancelled());
    }

    @Test
    public void onlyPlayerInventoriesAndEnderChestsMayStoreWings() {
        assertTrue(WingsGuard.storesWings(alice.getInventory()));
        assertTrue(WingsGuard.storesWings(alice.getEnderChest()));
        assertFalse(WingsGuard.storesWings(server.createInventory(null, InventoryType.CHEST)));
        assertFalse(WingsGuard.storesWings(server.createInventory(null, InventoryType.SHULKER_BOX)));
        assertFalse(WingsGuard.storesWings(server.createInventory(null, InventoryType.HOPPER)));
    }

    @Test
    public void wingsHiddenInABundleAreFoundAndSentBack() {
        service.grantOnce(alice, "t");
        ItemStack mine = alice.getInventory().getContents()[service.findValidSlot(alice)].clone();
        alice.getInventory().clear();
        ItemStack bundle = new ItemStack(Material.BUNDLE);
        var meta = (org.bukkit.inventory.meta.BundleMeta) bundle.getItemMeta();
        meta.setItems(List.of(mine, new ItemStack(Material.DIRT)));
        bundle.setItemMeta(meta);
        alice.getInventory().addItem(bundle);
        service.scan(alice);
        long wings = java.util.Arrays.stream(alice.getInventory().getContents()).filter(items()::isWings).count();
        assertEquals("the wings came back as a plain item", 1, wings);
        for (ItemStack s : alice.getInventory().getContents()) {
            if (s != null && s.getType() == Material.BUNDLE) {
                var m = (org.bukkit.inventory.meta.BundleMeta) s.getItemMeta();
                assertEquals(1, m.getItems().size());
                assertEquals(Material.DIRT, m.getItems().get(0).getType());
            }
        }
    }

    @Test
    public void coresStayOutOfEveryRecipe() {
        ItemStack core = items().createCore(1, 1);
        var grid = (org.bukkit.inventory.CraftingInventory) server.createInventory(null, InventoryType.WORKBENCH);
        var view = alice.openInventory(grid);
        grid.setMatrix(new ItemStack[] {new ItemStack(Material.COPPER_INGOT), core, null, null, null, null, null, null, null});
        grid.setResult(new ItemStack(Material.SPYGLASS));
        server.getPluginManager().callEvent(new org.bukkit.event.inventory.PrepareItemCraftEvent(grid, view, false));
        assertNull(grid.getResult());
    }

    // ---------------------------------------------------------------- 7. boss self defence

    @Test
    public void aNearbyBossGroundsTheWingsWithoutAnyoneSettingAFlag() {
        var boss = alice.getWorld().spawn(alice.getLocation(), org.bukkit.entity.Zombie.class);
        boss.addScoreboardTag("sb_boss");
        wear(alice, 1);
        long now = System.currentTimeMillis();
        tick(now);
        assertTrue(flight.state(alice.getUniqueId()).bossNear);
        assertFalse(alice.getAllowFlight());
        boss.remove();
        flight.state(alice.getUniqueId()).bossNear = flight.nearBoss(alice);
        tick(now + 100);
        assertTrue(alice.getAllowFlight());
    }

    @Test
    public void aStrongPlainMobDoesNotCountAsAZoneBoss() {
        var zombie = alice.getWorld().spawn(alice.getLocation(), org.bukkit.entity.Zombie.class);
        assertFalse(flight.nearBoss(alice));
        zombie.addScoreboardTag("sb_boss");
        assertTrue(flight.nearBoss(alice));
    }

    // ---------------------------------------------------------------- 8. abilities and stats

    @Test
    public void theDashNeedsLevelFourEnergyAndHasACooldown() {
        wear(alice, 3);
        long now = System.currentTimeMillis();
        tick(now);
        airborne();
        var low = new org.bukkit.event.player.PlayerSwapHandItemsEvent(alice, new ItemStack(Material.AIR), new ItemStack(Material.AIR));
        server.getPluginManager().callEvent(low);
        assertFalse("level 3 has no dash: the key works normally", low.isCancelled());

        alice.getInventory().setChestplate(null);
        tick(now + 100);
        wear(alice, 5);
        tick(now + 200);
        airborne();
        FlightManager.State st = flight.state(alice.getUniqueId());
        double before = st.energy.value();
        var dash = new org.bukkit.event.player.PlayerSwapHandItemsEvent(alice, new ItemStack(Material.AIR), new ItemStack(Material.AIR));
        server.getPluginManager().callEvent(dash);
        assertTrue(dash.isCancelled());
        assertEquals(25, before - st.energy.value(), 1e-6);
        var second = new org.bukkit.event.player.PlayerSwapHandItemsEvent(alice, new ItemStack(Material.AIR), new ItemStack(Material.AIR));
        server.getPluginManager().callEvent(second);
        assertTrue("cooldown eats the key", second.isCancelled());
        assertEquals("but costs nothing", 25, before - st.energy.value(), 1e-6);
    }

    @Test
    public void theLandingBurstAbsorbsAFallOnceUntilTheCooldownEnds() {
        // the real island feature makes sb_hub a safe zone (all damage cancelled): fight the fall elsewhere
        alice.teleport((Bukkit.getWorld("world") != null ? Bukkit.getWorld("world") : server.addSimpleWorld("world")).getSpawnLocation());
        wear(alice, 7);
        long now = System.currentTimeMillis();
        tick(now);
        EntityDamageEvent plain = new EntityDamageEvent(alice, EntityDamageEvent.DamageCause.FALL, 8.0);
        server.getPluginManager().callEvent(plain);
        assertFalse("level 7 has no burst", plain.isCancelled());

        service.adminGive(alice, 8, "t");
        for (ItemStack s : alice.getInventory().getContents()) {
            if (items().isWings(s)) {
                alice.getInventory().setChestplate(s.clone());
                alice.getInventory().remove(s);
                break;
            }
        }
        tick(now + 100);
        EntityDamageEvent burst = new EntityDamageEvent(alice, EntityDamageEvent.DamageCause.FALL, 8.0);
        server.getPluginManager().callEvent(burst);
        assertTrue(burst.isCancelled());
        EntityDamageEvent again = new EntityDamageEvent(alice, EntityDamageEvent.DamageCause.FALL, 8.0);
        server.getPluginManager().callEvent(again);
        assertFalse("cooldown", again.isCancelled());
    }

    @Test
    public void statBonusesFollowTheLevelTables() {
        WingsSettings s = service.settings();
        assertEquals(2.0, s.statBonuses(1).get(Stat.DEFENSE), 1e-9);
        assertNull(s.statBonuses(1).get(Stat.HEALTH));
        assertEquals(30.0, s.statBonuses(10).get(Stat.DEFENSE), 1e-9);
        assertEquals(15.0, s.statBonuses(10).get(Stat.SPEED), 1e-9);
        var yaml = new org.bukkit.configuration.file.YamlConfiguration();
        yaml.set("levels.stats.STRENGTH", List.of(1, 2));
        yaml.set("levels.stats.NOT_A_STAT", List.of(9));
        WingsSettings custom = WingsSettings.from(yaml);
        assertEquals(1.0, custom.statBonuses(1).get(Stat.STRENGTH), 1e-9);
        assertEquals("short lists repeat their last value", 2.0, custom.statBonuses(9).get(Stat.STRENGTH), 1e-9);
        assertEquals(1, custom.statBonuses(9).size());
    }

    @Test
    public void newSettingsHaveSaneDefaults() {
        WingsSettings s = WingsSettings.from(null);
        assertEquals(40, s.noFlyBossRadius);
        assertEquals(1.0, s.drainGlide, 1e-9);
        assertEquals(4, s.dashLevel);
        assertEquals(6, s.diveLevel);
        assertEquals(8, s.landingLevel);
        assertTrue(s.voidRescue);
        assertTrue(s.emergencyReasons.contains(WingsSettings.Reason.COMBAT));
    }

    @Test
    public void theBarTitleShowsTheTimeLeft() {
        wear(alice, 1);
        long now = System.currentTimeMillis();
        tick(now);
        alice.setFlying(true);
        airborne();
        for (int i = 1; i <= 3; i++) {
            alice.teleport(alice.getLocation().add(1, 0, 0));
            tick(now + i * 100L);
        }
        assertTrue(flight.state(alice.getUniqueId()).barTitle, flight.state(alice.getUniqueId()).barTitle.contains("s left)"));
    }

    @Test
    public void abilityLinesListUnlockLevels() {
        List<String> low = WingsItems.abilityLines(service.settings(), 1);
        assertTrue(low.stream().anyMatch(l -> l.contains("unlocks at level 4")));
        List<String> high = WingsItems.abilityLines(service.settings(), 10);
        assertTrue(high.stream().noneMatch(l -> l.contains("unlocks")));
    }
}
