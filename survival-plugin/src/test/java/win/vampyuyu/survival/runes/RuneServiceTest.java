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
    public void grindingTurnsAnAmethystShardIntoPowder() {
        PlayerMock player = server.addPlayer();
        assertFalse(service.grind(player).ok());
        player.getInventory().addItem(new ItemStack(Material.AMETHYST_SHARD, 1));
        assertTrue(service.grind(player).ok());
        Rune vitality = service.config().rune("vitality").orElseThrow();
        assertEquals(4, RuneService.count(player, service.costs(vitality, 1).get(0)));
    }
}
