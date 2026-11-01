package win.vampyuyu.survival.core;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/** The house rules every Skyblock menu follows (celestial identity): checked by the menu tests on each menu they open. */
public final class MenuRules {
    private MenuRules() {}

    /** No glass pane filler, no barrier in slot 49, no head in slot 4, no "Click to" line, no block progress bar. */
    public static void check(Inventory top, String what) {
        for (int slot = 0; slot < top.getSize(); slot++) {
            ItemStack item = top.getItem(slot);
            if (item == null) {
                continue;
            }
            String where = what + " slot " + slot;
            // a pane for sale in a shop keeps its name; the old grey filler had a blank one
            boolean blank = item.getItemMeta() != null && item.getItemMeta().displayName() != null
                && PlainTextComponentSerializer.plainText().serialize(item.getItemMeta().displayName()).isBlank();
            assertFalse(where + " is a glass pane filler", item.getType().name().endsWith("GLASS_PANE") && blank);
            if (slot == 49 && top.getSize() == 54) {
                assertNotEquals(where + " is a Close barrier", Material.BARRIER, item.getType());
            }
            if (slot == 4) {
                assertNotEquals(where + " is a player head", Material.PLAYER_HEAD, item.getType());
            }
            var lore = item.getItemMeta() == null ? null : item.getItemMeta().lore();
            if (lore != null) {
                for (var line : lore) {
                    String text = PlainTextComponentSerializer.plainText().serialize(line);
                    assertFalse(where + " has a Hypixel click line: " + text, text.contains("Click to"));
                    assertFalse(where + " has a block progress bar: " + text, text.contains("▮"));
                }
            }
        }
    }
}
