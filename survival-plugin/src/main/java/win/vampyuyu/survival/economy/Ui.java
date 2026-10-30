package win.vampyuyu.survival.economy;

import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import win.vampyuyu.survival.core.Guis;

/** Small helpers shared by the economy menus. Text is tooltip text only. */
final class Ui {
    private static final MiniMessage MINI = MiniMessage.miniMessage();

    private Ui() {}

    static String esc(String text) {
        return MINI.escapeTags(text == null ? "" : text);
    }

    static Component line(String mini) {
        return MINI.deserialize(mini).decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }

    /** An icon using the pack item model {@code vampyuyu:economy/<model>} (falls back to the material without the pack). */
    static ItemStack icon(Material material, String model, String name, String... lore) {
        ItemStack item = Guis.icon(material, name, lore);
        if (model != null) {
            ItemMeta meta = item.getItemMeta();
            meta.setItemModel(new NamespacedKey("vampyuyu", "economy/" + model));
            item.setItemMeta(meta);
        }
        return item;
    }

    static ItemStack plain(Material material, String name, String... lore) {
        return Guis.icon(material, name, lore);
    }

    static ItemStack glass() {
        return Guis.icon(Material.BLACK_STAINED_GLASS_PANE, " ");
    }

    /** Copy of {@code base} (keeps its name and lore) with {@code extra} lore lines appended. */
    static ItemStack withLore(ItemStack base, List<String> extra) {
        ItemStack copy = base.clone();
        copy.setAmount(1);
        ItemMeta meta = copy.getItemMeta();
        List<Component> lore = meta.hasLore() && meta.lore() != null ? new ArrayList<>(meta.lore()) : new ArrayList<>();
        for (String s : extra) {
            lore.add(line(s));
        }
        meta.lore(lore);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        copy.setItemMeta(meta);
        return copy;
    }

    /** Sparkline of values using block characters. */
    static String spark(List<Long> values) {
        if (values.isEmpty()) {
            return "";
        }
        long lo = Long.MAX_VALUE;
        long hi = Long.MIN_VALUE;
        for (long v : values) {
            lo = Math.min(lo, v);
            hi = Math.max(hi, v);
        }
        String bars = "▁▂▃▄▅▆▇█";
        StringBuilder out = new StringBuilder();
        for (long v : values) {
            int i = hi == lo ? 3 : (int) ((v - lo) * 7 / (hi - lo));
            out.append(bars.charAt(Math.max(0, Math.min(7, i))));
        }
        return out.toString();
    }
}
