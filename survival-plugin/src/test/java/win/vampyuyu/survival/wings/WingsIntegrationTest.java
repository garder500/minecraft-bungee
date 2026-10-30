package win.vampyuyu.survival.wings;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffectType;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import win.vampyuyu.survival.SurvivalPlugin;
import win.vampyuyu.survival.api.EconomyApi;
import win.vampyuyu.survival.api.WingsApi;

public class WingsIntegrationTest {
    private ServerMock server;
    private SurvivalPlugin plugin;
    private WingsService service;
    private FlightManager flight;
    private PlayerMock alice;
    private PlayerMock bob;

    /** Minimal coin store for the upgrade and claim costs. */
    static final class FakeEconomy implements EconomyApi {
        final ConcurrentMap<UUID, Double> balance = new ConcurrentHashMap<>();

        public double coins(UUID p) { return balance.getOrDefault(p, 0.0); }

        public boolean has(UUID p, double amount) { return coins(p) >= amount; }

        public boolean take(UUID p, double amount, String reason) {
            if (amount <= 0 || !has(p, amount)) {
                return false;
            }
            balance.merge(p, -amount, Double::sum);
            return true;
        }

        public void add(UUID p, double amount, String reason) { balance.merge(p, amount, Double::sum); }

        public void addTransactionListener(java.util.function.Consumer<Transaction> listener) {}
    }

    @Before
    public void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(SurvivalPlugin.class);
        service = (WingsService) plugin.core().service(WingsApi.class).orElseThrow();
        flight = service.flight();
        alice = server.addPlayer("Alice");
        bob = server.addPlayer("Bob");
    }

    @After
    public void tearDown() {
        MockBukkit.unmock();
    }

    private WingsItems items() {
        return service.wingsItems();
    }

    private void equip(PlayerMock p) {
        for (ItemStack s : p.getInventory().getContents()) {
            if (items().isWings(s)) {
                p.getInventory().setChestplate(s.clone());
                p.getInventory().remove(s);
                return;
            }
        }
        fail("no wings in the inventory");
    }

    /** The real dungeon feature refuses teleports into a dungeon world that is not its own run: borrow its admin permission. */
    private void enterDungeon(PlayerMock p, World dungeon) {
        var admin = p.addAttachment(plugin, win.vampyuyu.survival.dungeon.run.RunListener.ADMIN, true);
        try {
            p.teleport(dungeon.getSpawnLocation());
        } finally {
            p.removeAttachment(admin);
        }
    }

    /** The real island feature makes sb_hub a safe zone (all damage cancelled), so damage tests step out of it. */
    private void leaveHub(PlayerMock... players) {
        World w = Bukkit.getWorld("world") != null ? Bukkit.getWorld("world") : server.addSimpleWorld("world");
        for (PlayerMock p : players) {
            p.teleport(w.getSpawnLocation());
        }
    }

    private void give(PlayerMock p, int tier, int amount) {
        p.getInventory().addItem(items().createCore(tier, amount));
    }

    // ---------------------------------------------------------------- registration and items

    @Test
    public void featureIsEnabledAndPublishesTheApi() {
        assertTrue(plugin.features().enabledIds().contains("wings"));
        assertNotNull(Bukkit.getCommandMap().getCommand("wings"));
        assertTrue(plugin.core().items().ids().contains("wings_1"));
        assertTrue(plugin.core().items().ids().contains("wings_10"));
        assertTrue(plugin.core().items().ids().contains("wings_core_5"));
    }

    @Test
    public void wingsItemIsAnElytraWithTierAssetsAndTheUntradableFlag() {
        ItemStack stack = items().create(1, alice.getUniqueId(), "Alice", "id-1");
        assertEquals(Material.ELYTRA, stack.getType());
        var meta = stack.getItemMeta();
        assertEquals(new NamespacedKey("vampyuyu", "wings/wings"), meta.getItemModel());
        assertEquals(new NamespacedKey("vampyuyu", "wings_tier1"), meta.getEquippable().getModel());
        assertEquals(org.bukkit.inventory.EquipmentSlot.CHEST, meta.getEquippable().getSlot());
        assertEquals(1L, (long) meta.getPersistentDataContainer().get(new NamespacedKey("vampyuyu", "untradable"),
            PersistentDataType.LONG));
        assertTrue(meta.isUnbreakable());
        ItemStack top = items().create(10, alice.getUniqueId(), "Alice", "id-1");
        assertEquals(new NamespacedKey("vampyuyu", "wings_tier5"), top.getItemMeta().getEquippable().getModel());
        assertEquals(new NamespacedKey("vampyuyu", "wings/wings_tier5"), top.getItemMeta().getItemModel());
    }

    @Test
    public void tagRoundTrip() {
        ItemStack stack = items().create(4, alice.getUniqueId(), "Alice", "abc");
        WingsItems.Tag tag = items().read(stack);
        assertEquals(4, tag.level());
        assertEquals(alice.getUniqueId(), tag.owner());
        assertEquals("abc", tag.uuid());
        assertFalse(tag.unbound());
        assertTrue(items().read(items().createUnbound(2)).unbound());
        assertNull(items().read(new ItemStack(Material.ELYTRA)));
        assertNull(items().read(null));
        assertEquals(3, items().coreTier(items().createCore(3, 1)));
        assertEquals(0, items().coreTier(stack));
    }

    @Test
    public void wingsLoreNamesTheOwnerAndKeepsTheRarityLineLast() {
        ItemStack stack = items().create(1, alice.getUniqueId(), "Alice", "x");
        var lore = stack.getItemMeta().lore();
        String all = lore.stream().map(c -> net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
            .plainText().serialize(c)).reduce("", (a, b) -> a + "\n" + b);
        assertTrue(all, all.contains("Bound to Alice"));
        String last = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
            .serialize(lore.get(lore.size() - 1));
        assertTrue(last, last.startsWith("MYTHIC"));
    }

    // ---------------------------------------------------------------- grant once

    @Test
    public void grantOnceGivesOneItemAndNeverASecond() {
        WingsApi api = service;
        assertFalse(api.hasReceived(alice.getUniqueId()));
        assertTrue(api.grantOnce(alice, "boss:voidwyrm"));
        assertTrue(api.hasReceived(alice.getUniqueId()));
        assertFalse(api.grantOnce(alice, "boss:voidwyrm"));
        long count = java.util.Arrays.stream(alice.getInventory().getContents()).filter(items()::isWings).count();
        assertEquals(1, count);
        WingsData d = service.data(alice.getUniqueId());
        assertEquals("boss:voidwyrm", d.source);
        assertFalse(d.activeId.isEmpty());
        assertEquals(1, d.level);
        assertTrue(service.findValidSlot(alice) >= 0);
    }

    @Test
    public void grantOnceDropsTheItemWhenTheInventoryIsFull() {
        for (int i = 0; i < 36; i++) {
            alice.getInventory().setItem(i, new ItemStack(Material.DIRT, 64));
        }
        assertTrue(service.grantOnce(alice, "test"));
        boolean dropped = alice.getWorld().getEntitiesByClass(org.bukkit.entity.Item.class).stream()
            .anyMatch(i -> items().isWings(i.getItemStack()));
        long carried = java.util.Arrays.stream(alice.getInventory().getContents()).filter(items()::isWings).count();
        assertEquals("exactly one pair exists, carried or on the ground", 1, carried + (dropped ? 1 : 0));
    }

    @Test
    public void grantOnceRemembersAcrossProfileReloads() {
        service.grantOnce(alice, "a");
        String id = service.data(alice.getUniqueId()).activeId;
        plugin.core().profiles().awaitWrites();
        assertEquals(id, plugin.core().profiles().section(alice.getUniqueId(), "wings", WingsData.class,
            WingsData::new).activeId);
        assertTrue(service.hasReceived(alice.getUniqueId()));
    }

    @Test
    public void grantOnceFromAnotherThreadIsMarshalledToTheMainThread() throws Exception {
        java.util.concurrent.CompletableFuture<Boolean> result = new java.util.concurrent.CompletableFuture<>();
        Thread t = new Thread(() -> result.complete(service.grantOnce(alice, "async")));
        t.start();
        // the call waits for the main thread: tick the server until it finishes
        for (int i = 0; i < 200 && !result.isDone(); i++) {
            server.getScheduler().performOneTick();
            Thread.sleep(10);
        }
        assertTrue(result.get());
        assertTrue(service.hasReceived(alice.getUniqueId()));
    }

    @Test
    public void createGivesAnUnboundLevelOneItem() {
        ItemStack stack = service.create();
        assertTrue(service.isWings(stack));
        assertTrue(items().read(stack).unbound());
        assertEquals(1, items().level(stack));
        assertFalse(service.isWings(new ItemStack(Material.ELYTRA)));
    }

    // ---------------------------------------------------------------- ownership

    @Test
    public void anUnboundItemBindsToTheFirstHolderWithoutWings() {
        alice.getInventory().addItem(service.create());
        service.scan(alice);
        assertTrue(service.hasReceived(alice.getUniqueId()));
        assertTrue(service.findValidSlot(alice) >= 0);
        // a second unbound item crumbles: one pair per player
        alice.getInventory().addItem(service.create());
        service.scan(alice);
        long count = java.util.Arrays.stream(alice.getInventory().getContents()).filter(items()::isWings).count();
        assertEquals(1, count);
    }

    @Test
    public void aStrangersWingsFlyBackToTheirOwner() {
        service.grantOnce(alice, "t");
        ItemStack mine = alice.getInventory().getContents()[service.findValidSlot(alice)].clone();
        alice.getInventory().clear();
        bob.getInventory().addItem(mine);
        service.scan(bob);
        assertEquals(0, java.util.Arrays.stream(bob.getInventory().getContents()).filter(items()::isWings).count());
        assertEquals(1, java.util.Arrays.stream(alice.getInventory().getContents()).filter(items()::isWings).count());
        assertTrue(service.findValidSlot(alice) >= 0);
    }

    @Test
    public void aStrangersWingsForAnOfflineOwnerAreHeldForTheirNextJoin() {
        service.grantOnce(alice, "t");
        ItemStack mine = alice.getInventory().getContents()[service.findValidSlot(alice)].clone();
        alice.getInventory().clear();
        UUID aliceId = alice.getUniqueId();
        alice.disconnect();
        bob.getInventory().addItem(mine);
        service.scan(bob);
        server.getScheduler().waitAsyncTasksFinished();
        plugin.core().profiles().awaitWrites();
        assertEquals(0, java.util.Arrays.stream(bob.getInventory().getContents()).filter(items()::isWings).count());
        assertTrue(service.data(aliceId).pendingReturn);
        PlayerMock back = new PlayerMock(server, "Alice", aliceId);
        server.addPlayer(back);
        service.scan(back);
        assertTrue(service.findValidSlot(back) >= 0);
        assertFalse(service.data(aliceId).pendingReturn);
    }

    @Test
    public void staleCopiesTurnToDustAndDuplicatesAreRemoved() {
        service.grantOnce(alice, "t");
        ItemStack valid = alice.getInventory().getContents()[service.findValidSlot(alice)].clone();
        alice.getInventory().addItem(valid.clone());  // a duplicate
        ItemStack stale = items().create(1, alice.getUniqueId(), "Alice", "old-id");
        alice.getInventory().addItem(stale);
        service.scan(alice);
        long count = java.util.Arrays.stream(alice.getInventory().getContents()).filter(items()::isWings).count();
        assertEquals(1, count);
        assertEquals(service.data(alice.getUniqueId()).activeId,
            items().read(alice.getInventory().getContents()[service.findValidSlot(alice)]).uuid());
    }

    @Test
    public void aLevelMismatchIsRepairedFromTheProfile() {
        service.grantOnce(alice, "t");
        service.data(alice.getUniqueId()).level = 6;
        service.scan(alice);
        assertEquals(6, items().read(alice.getInventory().getContents()[service.findValidSlot(alice)]).level());
    }

    @Test
    public void pendingReturnGivesAFreshCopyAndInvalidatesTheOld() {
        service.grantOnce(alice, "t");
        String before = service.data(alice.getUniqueId()).activeId;
        alice.getInventory().clear();
        service.data(alice.getUniqueId()).pendingReturn = true;
        service.scan(alice);
        WingsData d = service.data(alice.getUniqueId());
        assertNotEquals(before, d.activeId);
        assertFalse(d.pendingReturn);
        assertTrue(service.findValidSlot(alice) >= 0);
    }

    @Test
    public void isWearingNeedsTheValidItemInTheChestSlot() {
        service.grantOnce(alice, "t");
        assertFalse(service.isWearing(alice));
        equip(alice);
        assertTrue(service.isWearing(alice));
        alice.getInventory().setChestplate(items().create(1, alice.getUniqueId(), "Alice", "nope"));
        assertFalse(service.isWearing(alice));
        alice.getInventory().setChestplate(items().create(1, bob.getUniqueId(), "Bob", service.data(alice.getUniqueId()).activeId));
        assertFalse("owner must match", service.isWearing(alice));
    }

    // ---------------------------------------------------------------- admin

    @Test
    public void adminGiveReplacesAndSetsTheLevel() {
        service.grantOnce(alice, "t");
        String before = service.data(alice.getUniqueId()).activeId;
        service.adminGive(alice, 7, "admin:x");
        WingsData d = service.data(alice.getUniqueId());
        assertEquals(7, d.level);
        assertNotEquals(before, d.activeId);
        assertEquals(1, java.util.Arrays.stream(alice.getInventory().getContents()).filter(items()::isWings).count());
        assertEquals(7, items().read(alice.getInventory().getContents()[service.findValidSlot(alice)]).level());
        service.adminGive(alice, null, "admin:x");
        assertEquals("a null level keeps the current one", 7, service.data(alice.getUniqueId()).level);
    }

    @Test
    public void revokeMakesEveryCopyInertAndResetAllowsANewGrant() {
        service.grantOnce(alice, "t");
        equip(alice);
        service.revoke(alice.getUniqueId(), false);
        assertEquals(0, java.util.Arrays.stream(alice.getInventory().getContents()).filter(items()::isWings).count());
        assertTrue(service.data(alice.getUniqueId()).activeId.isEmpty());
        assertFalse(service.grantOnce(alice, "again"));
        service.revoke(alice.getUniqueId(), true);
        assertTrue(service.grantOnce(alice, "again"));
        assertEquals(1, java.util.Arrays.stream(alice.getInventory().getContents()).filter(items()::isWings).count());
    }

    @Test
    public void revokeWorksOnAnOfflinePlayer() {
        service.grantOnce(alice, "t");
        UUID id = alice.getUniqueId();
        alice.disconnect();
        service.revoke(id, false);
        server.getScheduler().waitAsyncTasksFinished();
        assertTrue(service.data(id).activeId.isEmpty());
    }

    // ---------------------------------------------------------------- upgrades

    @Test
    public void upgradeConsumesCoresAndRaisesTheLevel() {
        service.grantOnce(alice, "t");
        give(alice, 1, 5);
        WingsService.UpgradeOutcome out = service.upgrade(alice);
        assertEquals(WingsService.UpgradeResult.OK, out.result());
        assertEquals(2, out.level());
        assertEquals(4, service.countCores(alice, 1));  // 1 -> 2 costs one tier 1 core, coins ignored (no economy)
        assertEquals(2, items().read(alice.getInventory().getContents()[service.findValidSlot(alice)]).level());
        assertEquals(1, service.data(alice.getUniqueId()).coresUsed);
    }

    @Test
    public void upgradeNeedsTheRightCoresAndLeavesThemAloneOtherwise() {
        service.grantOnce(alice, "t");
        give(alice, 2, 9);  // the wrong tier
        assertEquals(WingsService.UpgradeResult.MISSING_CORES, service.upgrade(alice).result());
        assertEquals(9, service.countCores(alice, 2));
        assertEquals(1, service.data(alice.getUniqueId()).level);
    }

    @Test
    public void upgradeNeedsTheWingsOnYou() {
        assertEquals(WingsService.UpgradeResult.NO_WINGS, service.upgrade(alice).result());
        service.grantOnce(alice, "t");
        give(alice, 1, 1);
        alice.getInventory().remove(Material.ELYTRA);
        for (int i = 0; i < 41; i++) {
            if (items().isWings(alice.getInventory().getItem(i))) {
                alice.getInventory().setItem(i, null);
            }
        }
        assertEquals(WingsService.UpgradeResult.NO_WINGS, service.upgrade(alice).result());
        assertEquals(1, service.countCores(alice, 1));
    }

    @Test
    public void upgradeStopsAtTheMaxLevel() {
        service.adminGive(alice, 10, "t");
        give(alice, 5, 5);
        assertEquals(WingsService.UpgradeResult.MAX_LEVEL, service.upgrade(alice).result());
        assertEquals(5, service.countCores(alice, 5));
    }

    @Test
    public void upgradeCostsCoinsWhenTheEconomyExists() {
        FakeEconomy eco = new FakeEconomy();
        plugin.core().replace(EconomyApi.class, eco);
        service.adminGive(alice, 2, "t");  // 2 -> 3: 3 tier 1 cores and 5000 coins
        give(alice, 1, 3);
        assertEquals(WingsService.UpgradeResult.MISSING_COINS, service.upgrade(alice).result());
        assertEquals("cores untouched when the coins are missing", 3, service.countCores(alice, 1));
        eco.add(alice.getUniqueId(), 6000, "test");
        assertEquals(WingsService.UpgradeResult.OK, service.upgrade(alice).result());
        assertEquals(1000, eco.coins(alice.getUniqueId()), 1e-9);
        assertEquals(0, service.countCores(alice, 1));
    }

    @Test
    public void coresAreTakenAcrossStacks() {
        service.adminGive(alice, 2, "t");
        give(alice, 1, 2);
        give(alice, 1, 1);
        alice.getInventory().addItem(items().createCore(1, 64));
        assertTrue(service.takeCores(alice, 1, 3));
        assertEquals(64, service.countCores(alice, 1));
        assertFalse(service.takeCores(alice, 1, 65));
        assertEquals(64, service.countCores(alice, 1));
    }

    // ---------------------------------------------------------------- claim

    @Test
    public void claimRulesArePure() {
        WingsData d = new WingsData();
        long now = 10_000_000L;
        assertEquals(WingsService.ClaimResult.NEVER_RECEIVED, WingsService.claimCheck(d, false, now, 1000));
        d.received = true;
        assertEquals(WingsService.ClaimResult.HAS_WINGS, WingsService.claimCheck(d, true, now, 1000));
        assertEquals(WingsService.ClaimResult.OK, WingsService.claimCheck(d, false, now, 1000));
        d.lastClaim = now - 500;
        assertEquals(WingsService.ClaimResult.COOLDOWN, WingsService.claimCheck(d, false, now, 1000));
        d.lastClaim = now - 1500;
        assertEquals(WingsService.ClaimResult.OK, WingsService.claimCheck(d, false, now, 1000));
        d.pendingReturn = true;
        assertEquals(WingsService.ClaimResult.RESTORED, WingsService.claimCheck(d, false, now, 1000));
    }

    @Test
    public void claimForgesNewWingsOnceAndChargesTheCoins() {
        FakeEconomy eco = new FakeEconomy();
        plugin.core().replace(EconomyApi.class, eco);
        service.grantOnce(alice, "t");
        String old = service.data(alice.getUniqueId()).activeId;
        ItemStack lost = alice.getInventory().getContents()[service.findValidSlot(alice)].clone();
        alice.getInventory().clear();
        assertEquals(WingsService.ClaimResult.MISSING_COINS, service.claim(alice));
        eco.add(alice.getUniqueId(), 30000, "t");
        assertEquals(WingsService.ClaimResult.OK, service.claim(alice));
        assertEquals(5000, eco.coins(alice.getUniqueId()), 1e-9);
        assertNotEquals(old, service.data(alice.getUniqueId()).activeId);
        assertEquals(WingsService.ClaimResult.HAS_WINGS, service.claim(alice));
        // the lost copy is dead even if somebody finds it
        alice.getInventory().addItem(lost);
        service.scan(alice);
        assertEquals(1, java.util.Arrays.stream(alice.getInventory().getContents()).filter(items()::isWings).count());
        // and a second claim is on cooldown
        alice.getInventory().clear();
        assertEquals(WingsService.ClaimResult.COOLDOWN, service.claim(alice));
    }

    @Test
    public void claimWorksFromTheEnderChestCheck() {
        service.grantOnce(alice, "t");
        int slot = service.findValidSlot(alice);
        ItemStack mine = alice.getInventory().getContents()[slot].clone();
        alice.getInventory().clear();
        alice.getEnderChest().addItem(mine);
        assertEquals(WingsService.ClaimResult.HAS_WINGS, service.claim(alice));
    }

    // ---------------------------------------------------------------- flight

    private void tick(PlayerMock p, long now) {
        flight.tickPlayer(p, now);
    }

    @Test
    public void wearingTheWingsAllowsFlightInAnAllowedWorld() {
        service.grantOnce(alice, "t");
        equip(alice);
        long now = System.currentTimeMillis();
        tick(alice, now);
        assertTrue(alice.getAllowFlight());
        assertTrue(flight.hasState(alice.getUniqueId()));
        assertEquals(0.1f, alice.getFlySpeed(), 1e-6f);
        // taking them off removes the flight again
        alice.getInventory().setChestplate(null);
        tick(alice, now + 100);
        assertFalse(alice.getAllowFlight());
        assertFalse(flight.hasState(alice.getUniqueId()));
    }

    @Test
    public void withoutWingsThereIsNoFlightAndForeignFlightIsUntouched() {
        tick(alice, System.currentTimeMillis());
        assertFalse(alice.getAllowFlight());
        alice.setAllowFlight(true);  // another plugin's flight
        tick(alice, System.currentTimeMillis());
        assertTrue(alice.getAllowFlight());
    }

    @Test
    public void creativeAndSpectatorAreLeftAlone() {
        service.grantOnce(alice, "t");
        equip(alice);
        alice.setGameMode(GameMode.CREATIVE);
        tick(alice, System.currentTimeMillis());
        assertEquals(FlightRules.Verdict.GAME_MODE, flight.verdict(alice, 1, null, System.currentTimeMillis()));
        FlightManager.State st = flight.state(alice.getUniqueId());
        assertFalse("not ours", st.ownsFlight);
    }

    @Test
    public void flightIsRefusedInADungeonWorld() {
        service.grantOnce(alice, "t");
        equip(alice);
        World dungeon = server.addSimpleWorld("sb_dungeon_test");
        enterDungeon(alice, dungeon);
        tick(alice, System.currentTimeMillis());
        assertFalse(alice.getAllowFlight());
    }

    @Test
    public void leavingForADungeonStopsAnOngoingFlight() {
        service.grantOnce(alice, "t");
        equip(alice);
        tick(alice, System.currentTimeMillis());
        alice.setFlying(true);
        World dungeon = server.addSimpleWorld("sb_dungeon_test2");
        enterDungeon(alice, dungeon);
        tick(alice, System.currentTimeMillis() + 100);
        assertFalse(alice.getAllowFlight());
        assertFalse(alice.isFlying());
    }

    @Test
    public void combatTagGroundsTheFlyerForEightSeconds() {
        service.grantOnce(alice, "t");
        equip(alice);
        long now = System.currentTimeMillis();
        tick(alice, now);
        assertTrue(alice.getAllowFlight());
        flight.tagCombat(alice.getUniqueId());
        tick(alice, now + 100);
        assertFalse(alice.getAllowFlight());
        assertTrue(flight.combatRemaining(alice.getUniqueId()) > 7000);
        tick(alice, now + 7000);
        assertFalse(alice.getAllowFlight());
        tick(alice, now + 8600);
        assertTrue("flight is back after the tag expires", alice.getAllowFlight());
    }

    @Test
    public void damageFromPlayersAndBossesTagsButEnvironmentDoesNot() {
        leaveHub(alice, bob);
        service.grantOnce(alice, "t");
        equip(alice);
        assertEquals(0, flight.combatRemaining(alice.getUniqueId()));
        server.getPluginManager().callEvent(new org.bukkit.event.entity.EntityDamageByEntityEvent(bob, alice,
            EntityDamageEvent.DamageCause.ENTITY_ATTACK, 2.0));
        assertTrue(flight.combatRemaining(alice.getUniqueId()) > 0);
        assertTrue("attacker is tagged too", flight.combatRemaining(bob.getUniqueId()) > 0);

        flight.combatRemainingReset(alice.getUniqueId());
        var zombie = alice.getWorld().spawn(alice.getLocation(), org.bukkit.entity.Zombie.class);
        server.getPluginManager().callEvent(new org.bukkit.event.entity.EntityDamageByEntityEvent(zombie, alice,
            EntityDamageEvent.DamageCause.ENTITY_ATTACK, 2.0));
        assertEquals("plain mobs do not tag by default", 0, flight.combatRemaining(alice.getUniqueId()));
        zombie.addScoreboardTag("sb_boss");
        server.getPluginManager().callEvent(new org.bukkit.event.entity.EntityDamageByEntityEvent(zombie, alice,
            EntityDamageEvent.DamageCause.ENTITY_ATTACK, 2.0));
        assertTrue("tagged bosses do", flight.combatRemaining(alice.getUniqueId()) > 0);
    }

    @Test
    public void runningOutOfEnergyEndsTheFlightWithASlowFall() {
        service.grantOnce(alice, "t");
        equip(alice);
        long now = System.currentTimeMillis();
        tick(alice, now);
        alice.setFlying(true);
        FlightManager.State st = flight.state(alice.getUniqueId());
        assertTrue(st.energy.capacity() >= 140);
        st.energy.drain(st.energy.value() - 0.5);
        alice.teleport(alice.getLocation().add(0, 50, 0));  // in the air
        for (int i = 1; i <= 20 && alice.getAllowFlight(); i++) {
            tick(alice, now + i * 100L);
        }
        assertFalse(alice.getAllowFlight());
        assertTrue(st.energy.exhausted());
        assertNotNull("no fall damage: slow fall instead", alice.getPotionEffect(PotionEffectType.SLOW_FALLING));
        // and fall damage is cancelled while the emergency lasts
        EntityDamageEvent fall = new EntityDamageEvent(alice, EntityDamageEvent.DamageCause.FALL, 10.0);
        server.getPluginManager().callEvent(fall);
        assertTrue(fall.isCancelled());
    }

    @Test
    public void fallDamageIsNotCancelledForNormalPlayers() {
        leaveHub(alice);
        EntityDamageEvent fall = new EntityDamageEvent(alice, EntityDamageEvent.DamageCause.FALL, 10.0);
        server.getPluginManager().callEvent(fall);
        assertFalse(fall.isCancelled());
    }

    @Test
    public void exhaustedPlayersCannotTakeOffUntilTheBarRefills() {
        service.grantOnce(alice, "t");
        equip(alice);
        long now = System.currentTimeMillis();
        tick(alice, now);
        FlightManager.State st = flight.state(alice.getUniqueId());
        st.energy.drain(10_000);
        tick(alice, now + 100);
        assertFalse(alice.getAllowFlight());
        st.energy.regen(1, 30);
        tick(alice, now + 200);
        assertFalse("still under the resume threshold", alice.getAllowFlight());
        st.energy.regen(500, 30);
        tick(alice, now + 300);
        assertTrue(alice.getAllowFlight());
    }

    @Test
    public void noFlyZoneFlagsBlockFlight() {
        service.grantOnce(alice, "t");
        equip(alice);
        long now = System.currentTimeMillis();
        alice.getPersistentDataContainer().set(new NamespacedKey("vampyuyu", FlightManager.NO_FLY_KEY),
            PersistentDataType.LONG, now + 5000);
        tick(alice, now);
        assertFalse("PDC flag with a future time", alice.getAllowFlight());
        alice.getPersistentDataContainer().set(new NamespacedKey("vampyuyu", FlightManager.NO_FLY_KEY),
            PersistentDataType.LONG, now - 1);
        tick(alice, now + 100);
        assertTrue("an expired flag heals by itself", alice.getAllowFlight());
        alice.addScoreboardTag("sb_no_fly");
        tick(alice, now + 200);
        assertFalse("scoreboard tag", alice.getAllowFlight());
        alice.removeScoreboardTag("sb_no_fly");
        tick(alice, now + 300);
        assertTrue(alice.getAllowFlight());
    }

    @Test
    public void levelDecidesTheFlySpeedAndTheCapacity() {
        service.adminGive(alice, 10, "t");
        equip(alice);
        tick(alice, System.currentTimeMillis());
        assertEquals(0.22f, alice.getFlySpeed(), 1e-6f);
        assertEquals(600, flight.state(alice.getUniqueId()).energy.capacity(), 1e-9);
    }

    @Test
    public void quitRestoresTheFlightFlagsAndSavesTheEnergy() {
        service.grantOnce(alice, "t");
        equip(alice);
        long now = System.currentTimeMillis();
        tick(alice, now);
        flight.state(alice.getUniqueId()).energy.drain(40);
        UUID id = alice.getUniqueId();
        double left = flight.state(id).energy.value();
        alice.disconnect();
        assertFalse(flight.hasState(id));
        assertEquals(left, service.data(id).energy, 1e-6);
    }

    @Test
    public void doubleJumpOffRefusesTheClientToggle() {
        service.grantOnce(alice, "t");
        equip(alice);
        tick(alice, System.currentTimeMillis());
        service.data(alice.getUniqueId()).doubleJump = false;
        var event = new org.bukkit.event.player.PlayerToggleFlightEvent(alice, true);
        server.getPluginManager().callEvent(event);
        assertTrue(event.isCancelled());
        service.data(alice.getUniqueId()).doubleJump = true;
        var allowed = new org.bukkit.event.player.PlayerToggleFlightEvent(alice, true);
        server.getPluginManager().callEvent(allowed);
        assertFalse(allowed.isCancelled());
    }

    @Test
    public void toggleFlyRefusesWithoutWingsAndInDungeons() {
        assertNotNull(flight.toggleFly(alice));
        service.grantOnce(alice, "t");
        equip(alice);
        tick(alice, System.currentTimeMillis());
        assertNull(flight.toggleFly(alice));
        assertTrue(alice.isFlying());
        assertNull(flight.toggleFly(alice));
        assertFalse(alice.isFlying());
    }

    @Test
    public void disablingThePluginReleasesEveryFlyer() {
        service.grantOnce(alice, "t");
        equip(alice);
        tick(alice, System.currentTimeMillis());
        assertTrue(alice.getAllowFlight());
        server.getPluginManager().disablePlugin(plugin);
        assertFalse(alice.getAllowFlight());
        assertNull(Bukkit.getCommandMap().getKnownCommands().get("wings"));
        assertFalse(plugin.core().items().ids().contains("wings_1"));
    }

    // ---------------------------------------------------------------- guard

    @Test
    public void aStrangerCannotPickUpDroppedWings() {
        // MockBukkit's ItemMock.getThrower is unimplemented, which island protection calls on pickups: bypass it
        // (this test is about the Wings guard, not about islands)
        for (PlayerMock p : new PlayerMock[] {alice, bob}) {
            p.addAttachment(plugin, win.vampyuyu.survival.island.IslandService.BYPASS, true);
        }
        service.grantOnce(alice, "t");
        ItemStack mine = alice.getInventory().getContents()[service.findValidSlot(alice)].clone();
        alice.getInventory().clear();
        var item = alice.getWorld().dropItem(alice.getLocation(), mine);
        var event = new org.bukkit.event.entity.EntityPickupItemEvent(bob, item, 0);
        server.getPluginManager().callEvent(event);
        assertTrue(event.isCancelled());
        assertTrue(item.isDead() || !item.isValid());
        assertEquals("the item went back to Alice", 1,
            java.util.Arrays.stream(alice.getInventory().getContents()).filter(items()::isWings).count());
        assertEquals(0, java.util.Arrays.stream(bob.getInventory().getContents()).filter(items()::isWings).count());
    }

    @Test
    public void theOwnerCanPickUpTheirDroppedWings() {
        // MockBukkit's ItemMock.getThrower is unimplemented, which island protection calls on pickups: bypass it
        // (this test is about the Wings guard, not about islands)
        for (PlayerMock p : new PlayerMock[] {alice, bob}) {
            p.addAttachment(plugin, win.vampyuyu.survival.island.IslandService.BYPASS, true);
        }
        service.grantOnce(alice, "t");
        ItemStack mine = alice.getInventory().getContents()[service.findValidSlot(alice)].clone();
        alice.getInventory().clear();
        var item = alice.getWorld().dropItem(alice.getLocation(), mine);
        var event = new org.bukkit.event.entity.EntityPickupItemEvent(alice, item, 0);
        server.getPluginManager().callEvent(event);
        assertFalse(event.isCancelled());
    }

    @Test
    public void mobsCannotPickUpWings() {
        service.grantOnce(alice, "t");
        ItemStack mine = alice.getInventory().getContents()[service.findValidSlot(alice)].clone();
        var item = alice.getWorld().dropItem(alice.getLocation(), mine);
        var zombie = alice.getWorld().spawn(alice.getLocation(), org.bukkit.entity.Zombie.class);
        var event = new org.bukkit.event.entity.EntityPickupItemEvent(zombie, item, 0);
        server.getPluginManager().callEvent(event);
        assertTrue(event.isCancelled());
    }

    @Test
    public void wingsStayOutOfTheCraftingGrid() {
        service.grantOnce(alice, "t");
        ItemStack mine = alice.getInventory().getContents()[service.findValidSlot(alice)].clone();
        var grid = (org.bukkit.inventory.CraftingInventory) server.createInventory(null,
            org.bukkit.event.inventory.InventoryType.WORKBENCH);
        var view = alice.openInventory(grid);
        assertNotNull(view);
        grid.setMatrix(new ItemStack[] {mine, new ItemStack(Material.PHANTOM_MEMBRANE), null, null, null, null, null,
            null, null});
        grid.setResult(new ItemStack(Material.ELYTRA));
        server.getPluginManager().callEvent(new org.bukkit.event.inventory.PrepareItemCraftEvent(grid, view, false));
        assertNull("no recipe may use the wings", grid.getResult());
        // a grid without wings keeps its result
        grid.setMatrix(new ItemStack[] {new ItemStack(Material.OAK_LOG), null, null, null, null, null, null, null, null});
        grid.setResult(new ItemStack(Material.OAK_PLANKS, 4));
        server.getPluginManager().callEvent(new org.bukkit.event.inventory.PrepareItemCraftEvent(grid, view, false));
        assertNotNull(grid.getResult());
    }

    @Test
    public void landingEndsTheSlowFallButTheLandingTickStillCannotHurt() {
        service.grantOnce(alice, "t");
        equip(alice);
        long now = System.currentTimeMillis();
        tick(alice, now);
        alice.setFlying(true);
        flight.state(alice.getUniqueId()).energy.drain(10_000);
        tick(alice, now + 100);
        assertNotNull(alice.getPotionEffect(PotionEffectType.SLOW_FALLING));
        alice.setOnGround(true);
        tick(alice, now + 200);
        assertNull("our slow fall is removed on landing", alice.getPotionEffect(PotionEffectType.SLOW_FALLING));
        assertFalse(flight.state(alice.getUniqueId()).emergency);
        EntityDamageEvent fall = new EntityDamageEvent(alice, EntityDamageEvent.DamageCause.FALL, 6.0);
        server.getPluginManager().callEvent(fall);
        assertTrue("grace period right after landing", fall.isCancelled());
    }

    @Test
    public void aSlowFallThePlayerAlreadyHadIsNotRemoved() {
        service.grantOnce(alice, "t");
        equip(alice);
        long now = System.currentTimeMillis();
        tick(alice, now);
        alice.setFlying(true);
        alice.addPotionEffect(new org.bukkit.potion.PotionEffect(PotionEffectType.SLOW_FALLING, 20 * 600, 0));
        flight.state(alice.getUniqueId()).energy.drain(10_000);
        tick(alice, now + 100);
        assertFalse(flight.state(alice.getUniqueId()).slowFallOurs);
        alice.setOnGround(true);
        tick(alice, now + 200);
        assertNotNull(alice.getPotionEffect(PotionEffectType.SLOW_FALLING));
    }

    @Test
    public void flyingDrainsEnergyAndSprintDrainsMore() {
        service.grantOnce(alice, "t");
        equip(alice);
        long now = System.currentTimeMillis();
        tick(alice, now);
        alice.setFlying(true);
        alice.teleport(alice.getLocation().add(0, 30, 0));
        FlightManager.State st = flight.state(alice.getUniqueId());
        double start = st.energy.value();
        for (int i = 1; i <= 10; i++) {  // one second, moving each tick
            alice.teleport(alice.getLocation().add(1, 0, 0));
            tick(alice, now + i * 100L);
        }
        double normal = start - st.energy.value();
        assertEquals("about 4 per second at level 1", 4.0, normal, 0.8);
        alice.setSprinting(true);
        double mid = st.energy.value();
        for (int i = 11; i <= 20; i++) {
            alice.teleport(alice.getLocation().add(1, 0, 0));
            tick(alice, now + i * 100L);
        }
        assertTrue("sprint flying costs more", mid - st.energy.value() > normal * 1.8);
    }

    @Test
    public void restingOnTheGroundRefillsTheBarAfterTheDelay() {
        service.grantOnce(alice, "t");
        equip(alice);
        long now = System.currentTimeMillis();
        tick(alice, now);
        FlightManager.State st = flight.state(alice.getUniqueId());
        st.energy.drain(100);
        double low = st.energy.value();
        alice.setOnGround(true);
        for (int i = 1; i <= 20; i++) {
            tick(alice, now + i * 100L);
        }
        assertTrue(st.energy.value() > low + 15);
        // in the air without flying nothing refills
        alice.setOnGround(false);
        double before = st.energy.value();
        for (int i = 21; i <= 30; i++) {
            tick(alice, now + i * 100L);
        }
        assertEquals(before, st.energy.value(), 1e-9);
    }

    @Test
    public void theEnergyBarIsABossBarNotTheActionBar() {
        service.grantOnce(alice, "t");
        equip(alice);
        long now = System.currentTimeMillis();
        tick(alice, now);
        FlightManager.State st = flight.state(alice.getUniqueId());
        st.energy.drain(50);
        tick(alice, now + 100);
        assertNotNull(st.bar);
        assertTrue(st.barShown);
        assertTrue(st.barTitle, st.barTitle.startsWith("Flight Energy"));
        // the preference hides it
        service.data(alice.getUniqueId()).bar = false;
        tick(alice, now + 200);
        assertFalse(st.barShown);
    }

    @Test
    public void thePlayerStatModifierIsAppliedWhileWearing() {
        var applied = new java.util.concurrent.ConcurrentHashMap<win.vampyuyu.survival.api.Stat, Double>();
        plugin.core().replace(win.vampyuyu.survival.api.StatsApi.class, new win.vampyuyu.survival.api.StatsApi() {
            public double stat(org.bukkit.entity.Player p, win.vampyuyu.survival.api.Stat s) {
                return s.base() + applied.getOrDefault(s, 0.0);
            }

            public void addModifier(UUID p, String source, win.vampyuyu.survival.api.Stat s, double amount) {
                assertEquals("wings", source);
                applied.put(s, amount);
            }

            public void removeModifiers(UUID p, String source) {
                assertEquals("wings", source);
                applied.clear();
            }

            public void recompute(org.bukkit.entity.Player p) {}
        });
        service.adminGive(alice, 3, "t");
        equip(alice);
        long now = System.currentTimeMillis();
        tick(alice, now);
        assertEquals(120.0, applied.get(win.vampyuyu.survival.api.Stat.FLIGHT_ENERGY), 1e-9);
        assertEquals("level 3 defense", 6.0, applied.get(win.vampyuyu.survival.api.Stat.DEFENSE), 1e-9);
        assertEquals("level 3 health", 5.0, applied.get(win.vampyuyu.survival.api.Stat.HEALTH), 1e-9);
        assertNull("a zero bonus is not registered", applied.get(win.vampyuyu.survival.api.Stat.SPEED));
        assertEquals(220, flight.state(alice.getUniqueId()).energy.capacity(), 1e-9);
        alice.getInventory().setChestplate(null);
        tick(alice, now + 100);
        assertTrue(applied.isEmpty());
    }

    @Test
    public void deathKeepsTheWingsOutOfTheDropsAndHandsThemBackOnRespawn() {
        service.grantOnce(alice, "t");
        equip(alice);
        String before = service.data(alice.getUniqueId()).activeId;
        List<ItemStack> drops = new ArrayList<>();
        drops.add(alice.getInventory().getChestplate().clone());
        drops.add(new ItemStack(Material.DIRT));
        var death = new org.bukkit.event.entity.PlayerDeathEvent(alice,
            org.bukkit.damage.DamageSource.builder(org.bukkit.damage.DamageType.GENERIC).build(), drops, 0,
            net.kyori.adventure.text.Component.text("died"), false);
        server.getPluginManager().callEvent(death);
        assertEquals(1, death.getDrops().size());
        assertEquals(Material.DIRT, death.getDrops().get(0).getType());
        assertTrue(service.data(alice.getUniqueId()).pendingReturn);
        alice.getInventory().clear();
        service.scan(alice);
        assertNotEquals(before, service.data(alice.getUniqueId()).activeId);
        assertTrue(service.findValidSlot(alice) >= 0);
    }

    @Test
    public void keepInventoryKeepsTheItemAndNoReturnIsPending() {
        service.grantOnce(alice, "t");
        List<ItemStack> drops = new ArrayList<>();
        var death = new org.bukkit.event.entity.PlayerDeathEvent(alice,
            org.bukkit.damage.DamageSource.builder(org.bukkit.damage.DamageType.GENERIC).build(), drops, 0,
            net.kyori.adventure.text.Component.text("died"), false);
        death.setKeepInventory(true);
        server.getPluginManager().callEvent(death);
        assertFalse(service.data(alice.getUniqueId()).pendingReturn);
    }

    // ---------------------------------------------------------------- command

    @Test
    public void wingsCommandOpensTheMenu() {
        alice.performCommand("wings");
        assertTrue(alice.getOpenInventory().getTopInventory().getHolder() instanceof win.vampyuyu.survival.core.Gui);
    }

    @Test
    public void toggleCommandFlipsThePreference() {
        assertTrue(service.data(alice.getUniqueId()).trail);
        alice.performCommand("wings toggle trail");
        assertFalse(service.data(alice.getUniqueId()).trail);
        alice.performCommand("wings toggle trail");
        assertTrue(service.data(alice.getUniqueId()).trail);
        alice.performCommand("wings toggle doublejump");
        assertFalse(service.data(alice.getUniqueId()).doubleJump);
        alice.performCommand("wings toggle nonsense");
        assertFalse("an unknown toggle changes nothing", service.data(alice.getUniqueId()).doubleJump);
    }

    @Test
    public void adminCommandsNeedThePermission() {
        alice.performCommand("wings give Alice 5");
        assertFalse(service.hasReceived(alice.getUniqueId()));
        alice.setOp(true);
        alice.performCommand("wings give Alice 5");
        assertTrue(service.hasReceived(alice.getUniqueId()));
        assertEquals(5, service.data(alice.getUniqueId()).level);
        alice.performCommand("wings give Bob 99");
        assertFalse("level out of range is refused", service.hasReceived(bob.getUniqueId()));
        alice.performCommand("wings give Bob");
        assertTrue(service.hasReceived(bob.getUniqueId()));
        alice.performCommand("wings revoke Bob reset");
        assertFalse(service.hasReceived(bob.getUniqueId()));
    }

    @Test
    public void reloadRebuildsTheItemsAndInfoWorks() {
        alice.setOp(true);
        service.grantOnce(alice, "t");
        alice.performCommand("wings reload");
        assertTrue(plugin.core().items().ids().contains("wings_10"));
        assertTrue("the held wings survive a reload", service.findValidSlot(alice) >= 0);
        alice.performCommand("wings info");
        alice.performCommand("wings info Bob");
        alice.performCommand("wings help");
    }

    @Test
    public void tabCompletionHidesAdminSubcommandsFromPlayers() {
        var cmd = Bukkit.getCommandMap().getCommand("wings");
        List<String> player = cmd.tabComplete(alice, "wings", new String[] {""});
        assertTrue(player.contains("toggle"));
        assertFalse(player.contains("give"));
        alice.setOp(true);
        List<String> admin = cmd.tabComplete(alice, "wings", new String[] {"g"});
        assertEquals(List.of("give"), admin);
        assertEquals(List.of("trail"), cmd.tabComplete(alice, "wings", new String[] {"toggle", "tr"}));
    }

    // ---------------------------------------------------------------- menu

    private void click(PlayerMock p, int slot) {
        var view = p.getOpenInventory();
        var event = new org.bukkit.event.inventory.InventoryClickEvent(view,
            org.bukkit.event.inventory.InventoryType.SlotType.CONTAINER, slot,
            org.bukkit.event.inventory.ClickType.LEFT, org.bukkit.event.inventory.InventoryAction.PICKUP_ALL);
        server.getPluginManager().callEvent(event);
        assertTrue(event.isCancelled());
    }

    @Test
    public void menuUpgradeButtonSpendsCores() {
        service.grantOnce(alice, "t");
        give(alice, 1, 1);
        alice.performCommand("wings");
        click(alice, 11);
        assertEquals(2, service.data(alice.getUniqueId()).level);
        assertEquals(0, service.countCores(alice, 1));
        click(alice, 11);
        assertEquals("not enough cores: nothing happens", 2, service.data(alice.getUniqueId()).level);
    }

    @Test
    public void menuClaimNeedsADoubleClick() {
        FakeEconomy eco = new FakeEconomy();
        plugin.core().replace(EconomyApi.class, eco);
        eco.add(alice.getUniqueId(), 100000, "t");
        service.grantOnce(alice, "t");
        alice.getInventory().clear();
        alice.performCommand("wings");
        click(alice, 15);
        assertFalse(service.findValidSlot(alice) >= 0);
        click(alice, 15);
        assertTrue(service.findValidSlot(alice) >= 0);
    }

    @Test
    public void menuTogglesChangeThePreferences() {
        alice.performCommand("wings");
        click(alice, 28);
        assertFalse(service.data(alice.getUniqueId()).trail);
        click(alice, 30);
        assertFalse(service.data(alice.getUniqueId()).bar);
    }
}
