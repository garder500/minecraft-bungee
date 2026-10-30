package win.vampyuyu.survival.skills;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.junit.Test;
import win.vampyuyu.survival.api.Skill;
import win.vampyuyu.survival.api.SkillsApi;
import win.vampyuyu.survival.api.Stat;
import win.vampyuyu.survival.api.event.SkillLevelUpEvent;
import win.vampyuyu.survival.api.event.SkillXpEvent;

public class SkillsServiceTest extends SkillsBase {
    private static final class Recorder implements Listener {
        final List<SkillXpEvent> xp = new ArrayList<>();
        final List<SkillLevelUpEvent> levels = new ArrayList<>();
        double multiplier = 1;
        boolean cancel;

        @EventHandler
        public void onXp(SkillXpEvent event) {
            xp.add(event);
            event.setAmount(event.getAmount() * multiplier);
            event.setCancelled(cancel);
        }

        @EventHandler
        public void onLevel(SkillLevelUpEvent event) {
            levels.add(event);
        }
    }

    private Recorder record() {
        Recorder recorder = new Recorder();
        server.getPluginManager().registerEvents(recorder, plugin);
        return recorder;
    }

    private double xpForLevel(Skill skill, int level) {
        return service.config().levels.xpFor(level);
    }

    @Test
    public void apiIsPublishedAndReportsLevels() {
        SkillsApi api = core.require(SkillsApi.class);
        assertEquals(60, api.maxLevel(Skill.MINING));
        assertEquals(50, api.maxLevel(Skill.CARPENTRY));
        assertEquals(0, api.level(player.getUniqueId(), Skill.MINING));
        api.addXp(player, Skill.MINING, 50, "test");
        assertEquals(1, api.level(player.getUniqueId(), Skill.MINING));
        assertEquals(50, api.xp(player.getUniqueId(), Skill.MINING), 1e-9);
    }

    @Test
    public void everySkillCanLevelUpAndFiresEventsPerLevel() {
        Recorder rec = record();
        double need = xpForLevel(Skill.COMBAT, 3);
        service.grant(player, Skill.COMBAT, need, "test");
        assertEquals(1, rec.xp.size());
        assertEquals("test", rec.xp.get(0).getSource());
        assertEquals(3, rec.levels.size());
        assertEquals(0, rec.levels.get(0).getOldLevel());
        assertEquals(1, rec.levels.get(0).getNewLevel());
        assertEquals(3, rec.levels.get(2).getNewLevel());
        assertEquals(3, service.level(player.getUniqueId(), Skill.COMBAT));
    }

    @Test
    public void listenersCanScaleOrCancelExperience() {
        Recorder rec = record();
        rec.multiplier = 2;
        assertEquals(20, service.grant(player, Skill.FARMING, 10, "test"), 1e-9);
        rec.cancel = true;
        assertEquals(0, service.grant(player, Skill.FARMING, 10, "test"), 1e-9);
        assertEquals(20, service.xp(player.getUniqueId(), Skill.FARMING), 1e-9);
    }

    @Test
    public void invalidGrantsAreIgnored() {
        assertEquals(0, service.grant(player, Skill.MINING, -5, "t"), 0);
        assertEquals(0, service.grant(player, Skill.MINING, 0, "t"), 0);
        assertEquals(0, service.grant(player, Skill.MINING, Double.NaN, "t"), 0);
        assertEquals(0, service.grant(player, Skill.MINING, Double.POSITIVE_INFINITY, "t"), 0);
        assertEquals(0, service.grant(null, Skill.MINING, 5, "t"), 0);
        assertEquals(0, service.grant(player, null, 5, "t"), 0);
        assertEquals(0, service.grant(player, Skill.MINING, 5, null), 0);
        assertEquals(0, service.xp(player.getUniqueId(), Skill.MINING), 0);
    }

    @Test
    public void listenerSettingNegativeOrNanAmountGivesNothing() {
        Recorder rec = record();
        rec.multiplier = -1;
        assertEquals(0, service.grant(player, Skill.MINING, 10, "t"), 0);
        rec.multiplier = Double.NaN;
        assertEquals(0, service.grant(player, Skill.MINING, 10, "t"), 0);
        assertEquals(0, service.xp(player.getUniqueId(), Skill.MINING), 0);
    }

    @Test
    public void multipliersAndBoostsApplyBeforeTheEvent() {
        Recorder rec = record();
        service.boosts().add("weekend", null, 2.0, 0);
        double gained = service.grant(player, Skill.ALCHEMY, 10, "t");
        assertEquals(20, gained, 1e-9);
        assertEquals(20, rec.xp.get(0).getAmount(), 1e-9);
    }

    @Test
    public void levelCapStopsLevelsButKeepsExperience() {
        service.grant(player, Skill.CARPENTRY, 1e9, "t");
        assertEquals(50, service.level(player.getUniqueId(), Skill.CARPENTRY));
        assertEquals(1e9, service.xp(player.getUniqueId(), Skill.CARPENTRY), 1);
    }

    @Test
    public void statModifiersFollowTheLevel() {
        UUID id = player.getUniqueId();
        service.grant(player, Skill.MINING, xpForLevel(Skill.MINING, 10), "t");
        // mining: 2 fortune per level up to 20, defense 1 per level up to 14
        assertEquals(20, stats.modifier(id, "skill:mining", Stat.MINING_FORTUNE), 1e-9);
        assertEquals(10, stats.modifier(id, "skill:mining", Stat.DEFENSE), 1e-9);
        assertTrue(stats.recomputes > 0);
        service.grant(player, Skill.MINING, xpForLevel(Skill.MINING, 30) - xpForLevel(Skill.MINING, 10), "t");
        // 20 levels at 2 + 10 at 3 = 70 fortune; defense 14 + 16 * 2 = 46
        assertEquals(70, stats.modifier(id, "skill:mining", Stat.MINING_FORTUNE), 1e-9);
        assertEquals(46, stats.modifier(id, "skill:mining", Stat.DEFENSE), 1e-9);
        // other skills are untouched
        assertEquals(0, stats.modifier(id, "skill:combat", Stat.STRENGTH), 0);
    }

    @Test
    public void everySkillMapsToItsDocumentedStats() {
        UUID id = player.getUniqueId();
        for (Skill skill : Skill.values()) {
            service.adminSetXp(id, skill, service.config().levels.xpFor(10));
        }
        service.applyAllStats(player);
        assertTrue(stats.modifier(id, "skill:combat", Stat.CRIT_CHANCE) > 0);
        assertTrue(stats.modifier(id, "skill:combat", Stat.STRENGTH) > 0);
        assertTrue(stats.modifier(id, "skill:mining", Stat.MINING_FORTUNE) > 0);
        assertTrue(stats.modifier(id, "skill:mining", Stat.DEFENSE) > 0);
        assertTrue(stats.modifier(id, "skill:farming", Stat.FARMING_FORTUNE) > 0);
        assertTrue(stats.modifier(id, "skill:farming", Stat.HEALTH) > 0);
        assertTrue(stats.modifier(id, "skill:foraging", Stat.FORAGING_FORTUNE) > 0);
        assertTrue(stats.modifier(id, "skill:foraging", Stat.STRENGTH) > 0);
        assertTrue(stats.modifier(id, "skill:fishing", Stat.HEALTH) > 0);
        assertTrue(stats.modifier(id, "skill:enchanting", Stat.INTELLIGENCE) > 0);
        assertTrue(stats.modifier(id, "skill:alchemy", Stat.INTELLIGENCE) > 0);
        assertTrue(stats.modifier(id, "skill:carpentry", Stat.HEALTH) > 0);
    }

    @Test
    public void levelCoinsArePaidOnceAndTrackedAsClaimed() {
        UUID id = player.getUniqueId();
        service.grant(player, Skill.FISHING, xpForLevel(Skill.FISHING, 2), "t");
        double expected = service.config().levelCoins.at(1) + service.config().levelCoins.at(2);
        assertEquals(expected, economy.coins(id), 1e-9);
        assertTrue(service.data(id).claimed.contains("lvl:fishing:1"));
        // re-running the settle step never pays again
        service.settle(player);
        service.settle(player);
        assertEquals(expected, economy.coins(id), 1e-9);
        assertEquals(0, service.pendingCoins(id), 0);
    }

    @Test
    public void coinsWaitForTheEconomyAndAreSettledLater() {
        // no economy service: take the fake out of the registry by using a second boot-less path
        UUID id = player.getUniqueId();
        SkillsData data = service.data(id);
        data.xp.put("combat", service.config().levels.xpFor(2)); // levels reached without any payment
        assertTrue(service.pendingCoins(id) > 0);
        double pending = service.pendingCoins(id);
        service.settle(player);
        assertEquals(pending, economy.coins(id), 1e-9);
        assertEquals(0, service.pendingCoins(id), 0);
    }

    @Test
    public void adminSetXpDoesNotPayAndUnclaimsHigherLevels() {
        UUID id = player.getUniqueId();
        service.adminSetXp(id, Skill.MINING, xpForLevel(Skill.MINING, 5));
        assertEquals(5, service.level(id, Skill.MINING));
        assertEquals(0, economy.coins(id), 0);
        assertTrue(service.data(id).claimed.contains("lvl:mining:5"));
        service.adminSetXp(id, Skill.MINING, xpForLevel(Skill.MINING, 2));
        assertEquals(2, service.level(id, Skill.MINING));
        assertFalse(service.data(id).claimed.contains("lvl:mining:3"));
        // earning level 3 normally now pays it
        service.grant(player, Skill.MINING, service.config().levels.cost(3), "t");
        assertEquals(service.config().levelCoins.at(3), economy.coins(id), 1e-9);
        service.adminReset(id, Skill.MINING);
        assertEquals(0, service.xp(id, Skill.MINING), 0);
        assertTrue(service.data(id).claimed.stream().noneMatch(c -> c.startsWith("lvl:mining:")));
    }

    @Test
    public void resetAllClearsEverySkill() {
        UUID id = player.getUniqueId();
        service.grant(player, Skill.MINING, 500, "t");
        service.grant(player, Skill.COMBAT, 500, "t");
        service.adminReset(id, null);
        assertEquals(0, service.xp(id, Skill.MINING), 0);
        assertEquals(0, service.xp(id, Skill.COMBAT), 0);
    }

    @Test
    public void averageUsesFractionalLevelsAndSkipsCarpentry() {
        UUID id = player.getUniqueId();
        assertEquals(0, service.average(id), 0);
        for (Skill skill : Skill.values()) {
            service.adminSetXp(id, skill, service.config().levels.xpFor(10));
        }
        assertEquals(10, service.average(id), 1e-9);
        service.adminSetXp(id, Skill.CARPENTRY, 1e12); // not in the average
        assertEquals(10, service.average(id), 1e-9);
        service.adminSetXp(id, Skill.MINING, service.config().levels.xpFor(17));
        assertEquals((6 * 10 + 17) / 7.0, service.average(id), 1e-9);
    }

    @Test
    public void dataSurvivesAJsonRoundTrip() {
        UUID id = player.getUniqueId();
        service.grant(player, Skill.FORAGING, 1234.5, "t");
        service.addCollection(player, Material.OAK_LOG, 77);
        SkillsData data = service.data(id);
        String json = win.vampyuyu.survival.core.JsonStore.GSON.toJson(data);
        SkillsData back = win.vampyuyu.survival.core.JsonStore.GSON.fromJson(json, SkillsData.class);
        assertEquals(1234.5, back.xpOf("foraging"), 1e-9);
        assertEquals(77, back.collected("OAK_LOG"));
        assertEquals(data.claimed, back.claimed);
        // an old document without the newer fields still loads
        SkillsData old = win.vampyuyu.survival.core.JsonStore.GSON.fromJson("{\"xp\":{\"mining\":5.0}}", SkillsData.class);
        assertEquals(5.0, old.xpOf("mining"), 0);
        assertNotNull(old.claimed);
        assertNotNull(old.seenRecipes);
    }

    @Test
    public void collectionTiersPayOnceAndAnnounceRecipes() {
        UUID id = player.getUniqueId();
        SkillsConfig.CollectionDef cobble = service.config().collections.get("COBBLESTONE");
        long tier4 = cobble.tiers().get(3);
        service.addCollection(player, Material.COBBLESTONE, tier4 - 1);
        assertEquals(3, cobble.tierOf(service.data(id).collected("COBBLESTONE")));
        double paid = cobble.coinsFor(1) + cobble.coinsFor(2) + cobble.coinsFor(3);
        assertEquals(paid, economy.coins(id), 1e-9);
        assertFalse(service.meetsRequirements(id, service.config().recipes.get("enchanted_cobblestone")));
        service.addCollection(player, Material.COBBLESTONE, 1);
        assertEquals(paid + cobble.coinsFor(4), economy.coins(id), 1e-9);
        assertTrue(service.meetsRequirements(id, service.config().recipes.get("enchanted_cobblestone")));
        service.addCollection(player, Material.COBBLESTONE, 0);
        service.addCollection(player, Material.COBBLESTONE, -5);
        service.addCollection(player, Material.DIRT, 10); // not a collection
        assertEquals(tier4, service.data(id).collected("COBBLESTONE"));
        service.settle(player);
        assertEquals(paid + cobble.coinsFor(4), economy.coins(id), 1e-9);
    }

    @Test
    public void multipleMaterialsShareOneCollection() {
        service.addCollection(player, Material.MELON_SLICE, 3);
        service.addCollection(player, Material.MELON, 2);
        assertEquals(5, service.data(player.getUniqueId()).collected("MELON"));
    }

    @Test
    public void adminSetCollectionClaimsReachedTiersSilently() {
        UUID id = player.getUniqueId();
        SkillsConfig.CollectionDef diamond = service.config().collections.get("DIAMOND");
        service.adminSetCollection(id, diamond, diamond.tiers().get(2));
        assertEquals(0, economy.coins(id), 0);
        assertTrue(service.data(id).claimed.contains("col:DIAMOND:3"));
        assertFalse(service.data(id).claimed.contains("col:DIAMOND:4"));
        assertTrue(service.meetsRequirements(id, service.config().recipes.get("enchanted_diamond")));
    }

    @Test
    public void joiningAppliesStatsAndLeavingRemovesThem() {
        UUID id = player.getUniqueId();
        service.adminSetXp(id, Skill.COMBAT, service.config().levels.xpFor(10));
        stats.modifiers.clear();
        PlayerMockHelper.join(server, plugin, "Fighter");
        var joined = server.getPlayer("Fighter");
        assertNotNull(joined);
        // the existing player is not re-applied, the new one has no skills: nothing registered for him
        assertTrue(stats.modifier(joined.getUniqueId(), "skill:combat", Stat.STRENGTH) == 0);
        service.applyAllStats(player);
        assertTrue(stats.modifier(id, "skill:combat", Stat.STRENGTH) > 0);
        player.disconnect();
        assertEquals(0, stats.modifier(id, "skill:combat", Stat.STRENGTH), 0);
    }

    @Test
    public void grantFromAnotherThreadIsMovedToTheMainThread() throws Exception {
        Thread thread = new Thread(() -> service.grant(player, Skill.MINING, 10, "async"));
        thread.start();
        thread.join();
        server.getScheduler().performOneTick();
        assertEquals(10, service.xp(player.getUniqueId(), Skill.MINING), 1e-9);
    }
}
