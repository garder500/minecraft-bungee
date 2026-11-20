package win.vampyuyu.survival.runes;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Map;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import win.vampyuyu.survival.SurvivalPlugin;
import win.vampyuyu.survival.api.RuneApi;
import win.vampyuyu.survival.api.Stat;
import win.vampyuyu.survival.core.Core;
import win.vampyuyu.survival.core.CustomItemDef;
import win.vampyuyu.survival.core.Rarity;

/** Engraving, scraping and crafting on real stacks. */
public class RuneServiceTest {
    private ServerMock server;
    private Core core;
    private RuneService service;

    @Before
    public void setUp() {
        server = MockBukkit.mock();
        SurvivalPlugin plugin = MockBukkit.load(SurvivalPlugin.class);
        core = plugin.core();
        service = core.service(RuneService.class).orElseThrow();
        core.items().register(CustomItemDef.builder("test_plate", "Test Plate", Rarity.COMMON, Material.IRON_CHESTPLATE)
            .type("CHESTPLATE").tier(2).stat(Stat.WARD, 10).build());
        core.items().register(CustomItemDef.builder("test_blade", "Test Blade", Rarity.COMMON, Material.IRON_SWORD)
            .type("SWORD").tier(1).pdc("damage", 20.0).build());
    }

    @After
    public void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    public void theFeatureIsWiredAndTheItemsExist() {
        assertTrue(core.service(RuneApi.class).isPresent());
        for (String id : new String[] {"rune_powder", "rune_vitality_1", "rune_vitality_3", "rune_scorch", "rune_verdant", "rune_stasis"}) {
            assertTrue(id, core.items().def(id).isPresent());
        }
        assertFalse(core.items().def("rune_scorch_1").isPresent());
    }

    @Test
    public void engravingAddsTheBonusAndFillsASlot() {
        ItemStack plate = core.items().create("test_plate");
        Map.Entry<Rune, Integer> vit = service.runeItem(core.items().create("rune_vitality_3")).orElseThrow();
        assertEquals(3, (int) vit.getValue());
        assertTrue(service.engrave(plate, core.items().create("rune_vitality_3")).ok());
        assertEquals(6.0, core.items().statsOf(plate).get(Stat.VITALITY), 0);
        assertEquals(10.0, core.items().statsOf(plate).get(Stat.WARD), 0);
        assertEquals(Map.of("vitality", 3), service.runesOf(plate));
        assertTrue(service.engrave(plate, core.items().create("rune_ward_2")).ok());
        assertEquals(14.0, core.items().statsOf(plate).get(Stat.WARD), 0);
        assertFalse("a tier 2 item has two slots", service.engrave(plate, core.items().create("rune_aether_1")).ok());
        assertFalse("same kind twice", service.engrave(plate, core.items().create("rune_ward_1")).ok());
        assertTrue("the lore lists the runes", plate.lore().stream()
            .anyMatch(l -> net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(l)
                .contains("Runes (2/2)")));
    }

    @Test
    public void weaponsTakeWeaponRunesAndRealmRunesGiveNoPoints() {
        ItemStack blade = core.items().create("test_blade");
        assertFalse(service.engrave(blade, core.items().create("rune_vitality_1")).ok());
        assertFalse(service.engrave(blade, core.items().create("rune_verdant")).ok());
        assertTrue(service.engrave(blade, core.items().create("rune_scorch")).ok());
        assertEquals(Map.of("scorch", 0), service.runesOf(blade));
        assertFalse(core.items().statsOf(blade).containsKey(Stat.MIGHT));
    }

    @Test
    public void scrapingRemovesTheRunesAndTheirPoints() {
        ItemStack plate = core.items().create("test_plate");
        service.engrave(plate, core.items().create("rune_vitality_2"));
        assertTrue(service.scrape(plate).ok());
        assertTrue(service.runesOf(plate).isEmpty());
        assertFalse(core.items().statsOf(plate).containsKey(Stat.VITALITY));
        assertEquals(10.0, core.items().statsOf(plate).get(Stat.WARD), 0);
        assertFalse(service.scrape(plate).ok());
    }

    @Test
    public void craftingSpendsExactlyTheIngredients() {
        PlayerMock player = server.addPlayer();
        Rune vitality = service.config().rune("vitality").orElseThrow();
        assertFalse(service.craft(player, vitality, 1).ok());
        player.getInventory().addItem(core.items().create("rune_powder", 8));
        assertTrue(service.craft(player, vitality, 1).ok());
        assertEquals(0, RuneService.count(player, service.costs(vitality, 1).get(0)));
        assertEquals(1, RuneService.count(player, service.costs(vitality, 2).get(0)));
        // rank II: 3 runes I + a bronzite
        player.getInventory().addItem(core.items().create("rune_vitality_1", 2));
        player.getInventory().addItem(core.items().create("bronzite"));
        assertTrue(service.craft(player, vitality, 2).ok());
        assertEquals(0, RuneService.count(player, service.costs(vitality, 2).get(0)));
        assertEquals(1, RuneService.count(player, service.costs(vitality, 3).get(0)));
    }

    @Test
    public void everyRealmHasARuneMadeFromItsResource() {
        java.util.Set<String> resources = new java.util.HashSet<>();
        for (Rune rune : service.config().runes()) {
            if (rune.realm()) {
                assertTrue(rune.id(), core.items().def(rune.itemId(0)).isPresent());
                assertTrue(rune.component() + " is a real item", core.items().def(rune.component()).isPresent());
                assertFalse(rune.id() + " says what it does", RuneService.effectText(rune, service.config()).equals("a Realm effect"));
                resources.add(rune.component());
            }
        }
        assertEquals(java.util.Set.of("cinderheart", "heartwood", "stasis_crystal", "chrono_sand", "rot_resin", "thunder_quartz",
            "pressure_pearl", "soul_ash"), resources);
        assertTrue(service.config().problems().toString(), service.config().problems().isEmpty());
    }

    @Test
    public void blightRotsAndTempestChainsAShareOfTheSurge() {
        PlayerMock player = server.addPlayer();
        ItemStack blade = core.items().create("test_blade");
        service.engrave(blade, core.items().create("rune_blight"));
        var world = player.getWorld();
        org.bukkit.entity.Zombie target = world.spawn(player.getLocation().add(2, 0, 0), org.bukkit.entity.Zombie.class);
        service.onSurge(player, target, blade, 20);
        assertTrue(target.hasPotionEffect(org.bukkit.potion.PotionEffectType.WITHER));

        ItemStack storm = core.items().create("test_blade");
        service.engrave(storm, core.items().create("rune_tempest"));
        org.bukkit.entity.Zombie near = world.spawn(target.getLocation().add(1, 0, 0), org.bukkit.entity.Zombie.class);
        double before = near.getHealth();
        service.onSurge(player, target, storm, 20);
        assertEquals("35 % of the Surge hit", before - 7.0, near.getHealth(), 0.01);
    }

    @Test
    public void soulbindHealsOnAKill() {
        PlayerMock player = server.addPlayer();
        ItemStack blade = core.items().create("test_blade");
        service.engrave(blade, core.items().create("rune_soulbind"));
        player.getInventory().setItemInMainHand(blade);
        player.setHealth(10);
        org.bukkit.entity.Zombie victim = player.getWorld().spawn(player.getLocation(), org.bukkit.entity.Zombie.class);
        service.onKill(player, victim);
        assertEquals(10 + 20 * 0.08, player.getHealth(), 0.01);
        org.bukkit.entity.Cow cow = player.getWorld().spawn(player.getLocation(), org.bukkit.entity.Cow.class);
        service.onKill(player, cow);
        assertEquals("only monsters feed it", 10 + 20 * 0.08, player.getHealth(), 0.01);
    }

    @Test
    public void rewindMendsALowPlayerOncePerCooldown() {
        core.items().register(CustomItemDef.builder("test_helm", "Test Helm", Rarity.COMMON, Material.IRON_HELMET)
            .type("HELMET").tier(2).build());
        PlayerMock player = server.addPlayer();
        ItemStack helm = core.items().create("test_helm");
        assertTrue(service.engrave(helm, core.items().create("rune_rewind")).ok());
        player.getInventory().setHelmet(helm);
        player.setHealth(4);
        var hit = new org.bukkit.event.entity.EntityDamageEvent(player, org.bukkit.event.entity.EntityDamageEvent.DamageCause.FALL,
            org.bukkit.damage.DamageSource.builder(org.bukkit.damage.DamageType.FALL).build(), 1.0);
        service.onHurt(player, hit);
        server.getScheduler().performTicks(2);
        assertEquals(4 + 20 * 0.3, player.getHealth(), 0.01);
        player.setHealth(4);
        service.onHurt(player, hit);
        server.getScheduler().performTicks(2);
        assertEquals("cooling down", 4, player.getHealth(), 0.01);
    }

    @Test
    public void grindingTurnsAnAmethystShardIntoPowder() {
        PlayerMock player = server.addPlayer();
        assertFalse(service.grind(player).ok());
        player.getInventory().addItem(new ItemStack(Material.AMETHYST_SHARD, 1));
        assertTrue(service.grind(player).ok());
        Rune vitality = service.config().rune("vitality").orElseThrow();
        assertEquals(4, RuneService.count(player, service.costs(vitality, 1).get(0)));
    }
}
