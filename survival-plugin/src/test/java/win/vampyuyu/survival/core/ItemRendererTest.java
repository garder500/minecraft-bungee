package win.vampyuyu.survival.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.List;
import java.util.Map;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.junit.Test;
import win.vampyuyu.survival.api.Element;
import win.vampyuyu.survival.api.Stat;

/** The closing lore line: a small element and tier line instead of the bold rarity line. */
public class ItemRendererTest {
    private static String text(Component c) {
        return PlainTextComponentSerializer.plainText().serialize(c);
    }

    @Test
    public void anElementItemEndsWithTheElementAndTheTier() {
        CustomItemDef def = CustomItemDef.builder("t1", "T1", Rarity.UNCOMMON, Material.IRON_SWORD).type("SWORD")
            .element(Element.EARTH).tier(1).stat(Stat.MIGHT, 5).build();
        List<Component> lore = ItemRenderer.lore(def, Map.of());
        Component last = lore.get(lore.size() - 1);
        assertEquals("⛰ Earth Element · Tier 1", text(last));
        assertFalse("never bold", last.hasDecoration(TextDecoration.BOLD) && last.decoration(TextDecoration.BOLD) == TextDecoration.State.TRUE);
        assertEquals(Element.EARTH.color(), last.color());
    }

    @Test
    public void anItemWithoutElementEndsWithTheSmallRarityAndType() {
        CustomItemDef def = CustomItemDef.builder("t2", "T2", Rarity.EPIC, Material.IRON_CHESTPLATE).type("CHESTPLATE").build();
        List<Component> lore = ItemRenderer.lore(def, Map.of());
        Component last = lore.get(lore.size() - 1);
        assertEquals("Epic Chestplate", text(last));
        assertTrue(last.decoration(TextDecoration.BOLD) != TextDecoration.State.TRUE);
        CustomItemDef tiered = CustomItemDef.builder("t3", "T3", Rarity.RARE, Material.IRON_HELMET).tier(3).build();
        assertEquals("Tier 3", text(ItemRenderer.lastLine(tiered, Rarity.RARE, false)));
    }
}
