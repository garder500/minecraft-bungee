package win.vampyuyu.survival.core;

import static org.junit.Assert.*;

import java.util.Map;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import win.vampyuyu.survival.api.Stat;

public class ItemsTest {
    private Items items;

    @Before
    public void setUp() {
        MockBukkit.mock();
        items = new Items();
        items.register(CustomItemDef.builder("test_blade", "Test Blade", Rarity.LEGENDARY, Material.DIAMOND_SWORD)
            .type("SWORD").lore("<gray>Cuts things.")
            .stat(Stat.STRENGTH, 50).stat(Stat.CRIT_CHANCE, 5)
            .pdc("kind", "blade").pdc("tier", 3L).pdc("ratio", 0.5)
            .unbreakable(true).build());
    }

    @After
    public void tearDown() {
        MockBukkit.unmock();
    }

    private static String plain(Component c) {
        return PlainTextComponentSerializer.plainText().serialize(c);
    }

    @Test
    public void createAndIdRoundTrip() {
        ItemStack stack = items.create("test_blade", 1);
        assertEquals(Material.DIAMOND_SWORD, stack.getType());
        assertEquals("test_blade", items.idOf(stack).orElseThrow());
        assertTrue(items.isCustom(stack));
        assertTrue(items.is(stack, "test_blade"));
        assertEquals("test_blade", stack.getItemMeta().getPersistentDataContainer()
            .get(new NamespacedKey("vampyuyu", "item_id"), PersistentDataType.STRING));
    }

    @Test
    public void vanillaStacksAreNotCustom() {
        assertTrue(items.idOf(new ItemStack(Material.DIRT)).isEmpty());
        assertFalse(items.isCustom(null));
        assertTrue(items.statsOf(new ItemStack(Material.DIRT)).isEmpty());
    }

    @Test
    public void extraPdcValuesAreStored() {
        ItemStack stack = items.create("test_blade");
        assertEquals("blade", items.getString(stack, "kind").orElseThrow());
        assertEquals(3L, items.getLong(stack, "tier").orElseThrow().longValue());
        assertEquals(0.5, items.getDouble(stack, "ratio").orElseThrow(), 0);
        items.set(stack, "xp", 120L);
        assertEquals(120L, items.getLong(stack, "xp").orElseThrow().longValue());
    }

    @Test
    public void statsAndBonuses() {
        ItemStack stack = items.create("test_blade");
        Map<Stat, Double> stats = items.statsOf(stack);
        assertEquals(50, stats.get(Stat.STRENGTH), 0);
        assertEquals(5, stats.get(Stat.CRIT_CHANCE), 0);
        items.setBonus(stack, Stat.STRENGTH, 10);
        assertEquals(60, items.statsOf(stack).get(Stat.STRENGTH), 0);
        items.setBonus(stack, Stat.STRENGTH, 0);
        assertEquals(50, items.statsOf(stack).get(Stat.STRENGTH), 0);
    }

    @Test
    public void loreIsRenderedStably() {
        ItemStack stack = items.create("test_blade");
        var lore = stack.getItemMeta().lore().stream().map(ItemsTest::plain).toList();
        assertEquals(java.util.List.of(
            "Strength: +50", "Crit Chance: +5%", "", "Cuts things.", "", "LEGENDARY SWORD"), lore);
        items.setBonus(stack, Stat.STRENGTH, 10);
        items.refresh(stack);
        assertEquals("Strength: +50 (+10)", plain(stack.getItemMeta().lore().get(0)));
    }

    @Test(expected = IllegalArgumentException.class)
    public void unknownIdIsRejected() {
        items.create("nope");
    }

    @Test(expected = IllegalStateException.class)
    public void duplicateIdIsRejected() {
        items.register(CustomItemDef.builder("test_blade", "Again", Rarity.COMMON, Material.STICK).build());
    }

    @Test(expected = IllegalArgumentException.class)
    public void reservedPdcKeyIsRejected() {
        CustomItemDef.builder("x", "X", Rarity.COMMON, Material.STICK).pdc("item_id", "no").build();
    }

    @Test
    public void modelAndEquippableAreApplied() {
        items.register(CustomItemDef.builder("test_wings", "Test Wings", Rarity.MYTHIC, Material.ELYTRA)
            .itemModel("vampyuyu:wings").equippable(EquipmentSlot.CHEST, "vampyuyu:wings").build());
        ItemStack stack = items.create("test_wings");
        assertEquals(new NamespacedKey("vampyuyu", "wings"), stack.getItemMeta().getItemModel());
        assertEquals(EquipmentSlot.CHEST, stack.getItemMeta().getEquippable().getSlot());
        assertEquals(new NamespacedKey("vampyuyu", "wings"), stack.getItemMeta().getEquippable().getModel());
    }
}
