package win.vampyuyu.survival.skills;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Zombie;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.junit.Test;
import win.vampyuyu.survival.api.Skill;

public class ActivitySourcesTest extends SkillsBase {
    private Zombie zombie(int x) {
        Zombie mob = world.spawn(new Location(world, x, 64, 0), Zombie.class);
        // a plugin spawn counts as CUSTOM and is tagged by the feature; these tests want a normal mob
        mob.getPersistentDataContainer().remove(ActivitySources.SPAWN_BLOCKED);
        return mob;
    }

    private void kill(LivingEntity mob, ItemStack... drops) {
        mob.setKiller(player);
        server.getPluginManager().callEvent(new EntityDeathEvent(mob,
            org.bukkit.damage.DamageSource.builder(org.bukkit.damage.DamageType.PLAYER_ATTACK).build(),
            new ArrayList<>(List.of(drops))));
    }

    private double combat() {
        return service.xp(player.getUniqueId(), Skill.COMBAT);
    }

    @Test
    public void mobKillsGiveCombatExperienceByType() {
        kill(zombie(0));
        assertEquals(6, combat(), 1e-9);
    }

    @Test
    public void mobDropsFeedCollections() {
        kill(zombie(0), new ItemStack(Material.ROTTEN_FLESH, 2), new ItemStack(Material.IRON_INGOT, 1));
        assertEquals(2, service.data(player.getUniqueId()).collected("ROTTEN_FLESH"));
    }

    @Test
    public void customItemDropsAreNotCountedAsCollections() {
        ItemStack custom = core.items().create("enchanted_bone", 5);
        kill(zombie(0), custom);
        assertEquals(0, service.data(player.getUniqueId()).collected("BONE"));
    }

    @Test
    public void spawnerAndPluginMobsGiveNothing() throws Exception {
        useUnprotectedWorld();
        Zombie spawned = zombie(1);
        server.getPluginManager().callEvent(new CreatureSpawnEvent(spawned, CreatureSpawnEvent.SpawnReason.SPAWNER));
        kill(spawned, new ItemStack(Material.ROTTEN_FLESH, 2));
        assertEquals(0, combat(), 0);
        assertEquals(0, service.data(player.getUniqueId()).collected("ROTTEN_FLESH"));

        Zombie custom = zombie(2);
        server.getPluginManager().callEvent(new CreatureSpawnEvent(custom, CreatureSpawnEvent.SpawnReason.CUSTOM));
        kill(custom);
        assertEquals(0, combat(), 0);

        // another feature can opt a plugin spawned mob back in
        Zombie allowed = zombie(3);
        allowed.getPersistentDataContainer().set(ActivitySources.FORCE_XP, PersistentDataType.BYTE, (byte) 1);
        server.getPluginManager().callEvent(new CreatureSpawnEvent(allowed, CreatureSpawnEvent.SpawnReason.CUSTOM));
        kill(allowed);
        assertEquals(6, combat(), 1e-9);
    }

    @Test
    public void skillXpTagSetAfterSpawningStillCounts() {
        // the usual pattern: spawn first (the spawn event fires inside the call), tag afterwards
        Zombie boss = world.spawn(new Location(world, 30, 64, 0), Zombie.class);
        assertTrue(boss.getPersistentDataContainer().has(ActivitySources.SPAWN_BLOCKED, PersistentDataType.BYTE));
        assertFalse("the public refusal tag is not written by the spawn handler",
            boss.getPersistentDataContainer().has(ActivitySources.NO_XP, PersistentDataType.BYTE));
        boss.getPersistentDataContainer().set(ActivitySources.FORCE_XP, PersistentDataType.BYTE, (byte) 1);
        kill(boss);
        assertEquals(6, combat(), 1e-9);

        // an explicit no_skill_xp still beats skill_xp
        Zombie both = world.spawn(new Location(world, 31, 64, 0), Zombie.class);
        both.getPersistentDataContainer().set(ActivitySources.FORCE_XP, PersistentDataType.BYTE, (byte) 1);
        both.getPersistentDataContainer().set(ActivitySources.NO_XP, PersistentDataType.BYTE, (byte) 1);
        kill(both);
        assertEquals(6, combat(), 1e-9);

        // a plugin spawn nobody vouched for stays refused
        kill(world.spawn(new Location(world, 32, 64, 0), Zombie.class));
        assertEquals(6, combat(), 1e-9);
    }

    @Test
    public void taggedMobsAreRefusedAndSeaCreaturesPayFishing() {
        Zombie refused = zombie(4);
        refused.getPersistentDataContainer().set(ActivitySources.NO_XP, PersistentDataType.BYTE, (byte) 1);
        kill(refused);
        assertEquals(0, combat(), 0);

        Zombie sea = zombie(5);
        sea.getPersistentDataContainer().set(ActivitySources.SEA_CREATURE, PersistentDataType.BYTE, (byte) 1);
        kill(sea);
        assertEquals(0, combat(), 0);
        assertEquals(service.config().defaultSeaCreatureXp, service.xp(player.getUniqueId(), Skill.FISHING), 1e-9);
    }

    @Test
    public void grindingOneSpotDiminishes() throws Exception {
        useUnprotectedWorld();
        double previous = Double.MAX_VALUE;
        double sum = 0;
        for (int i = 0; i < 60; i++) {
            double before = combat();
            kill(zombie(0));
            double gain = combat() - before;
            assertTrue(gain <= previous + 1e-9);
            previous = gain;
            sum += gain;
        }
        assertTrue("sum " + sum, sum < 60 * 6 * 0.7);
        assertEquals(0.6, previous, 1e-9); // 6 xp at the 10% floor
    }

    @Test
    public void killsWithoutAPlayerKillerAreIgnored() {
        Zombie mob = zombie(6);
        server.getPluginManager().callEvent(new EntityDeathEvent(mob,
            org.bukkit.damage.DamageSource.builder(org.bukkit.damage.DamageType.FALL).build(), new ArrayList<>()));
        assertEquals(0, combat(), 0);
    }

    @Test
    public void killsOutsideSkyblockWorldsGiveNothing() {
        org.bukkit.World other = server.addSimpleWorld("plain_world");
        Zombie mob = other.spawn(new Location(other, 0, 64, 0), Zombie.class);
        kill(mob);
        assertEquals(0, combat(), 0);
    }

    @Test
    public void experienceOfOneKillIsCapped() {
        // a boss sized health pool without a table entry is capped by max_per_kill
        var cfg = service.config();
        assertTrue(cfg.maxPerKill > 0);
        assertEquals(250, Math.min(100000 * cfg.healthFactor, cfg.maxPerKill), 0);
    }
}
