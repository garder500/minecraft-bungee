package win.vampyuyu.survival.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import win.vampyuyu.survival.api.Stat;

/**
 * Stable lore layout of every custom item (pure, no Bukkit state):
 * <pre>
 * Strength: +50 (+5)        one line per stat, enum order; "(+5)" = bonus from reforges/enchants
 * Defense: +10
 *                           blank, only when stats and description both exist
 * description lines...
 *                           blank
 * LEGENDARY CHESTPLATE      bold, rarity colour
 * </pre>
 */
public final class ItemRenderer {
    private static final MiniMessage MINI = MiniMessage.miniMessage();

    private ItemRenderer() {}

    private static Component line(Component component) {
        return component.decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }

    /** Display name coloured with the rarity. */
    public static Component name(CustomItemDef def) {
        return line(Component.text(def.name(), def.rarity().color()));
    }

    /** Full lore. {@code bonus} are per-item additions on top of {@code def.stats()}. */
    public static List<Component> lore(CustomItemDef def, Map<Stat, Double> bonus) {
        List<Component> out = new ArrayList<>();
        for (Stat stat : Stat.values()) {
            double base = def.stats().getOrDefault(stat, 0.0);
            double extra = bonus.getOrDefault(stat, 0.0);
            if (base == 0 && extra == 0) {
                continue;
            }
            Component text = Component.text(stat.displayName() + ": ", NamedTextColor.GRAY)
                .append(Component.text(signed(base, stat), stat.color()));
            if (extra != 0) {
                text = text.append(Component.text(" (" + signed(extra, stat) + ")", NamedTextColor.BLUE));
            }
            out.add(line(text));
        }
        if (!out.isEmpty() && !def.lore().isEmpty()) {
            out.add(Component.empty());
        }
        for (String text : def.lore()) {
            out.add(line(MINI.deserialize(text)));
        }
        if (!out.isEmpty()) {
            out.add(Component.empty());
        }
        String label = def.rarity().label() + (def.typeLabel() == null ? "" : " " + def.typeLabel());
        out.add(line(Component.text(label, def.rarity().color(), TextDecoration.BOLD)));
        return out;
    }

    private static String signed(double value, Stat stat) {
        String number = value == Math.rint(value) ? Long.toString((long) value) : Double.toString(Math.round(value * 10) / 10.0);
        return (value >= 0 ? "+" : "") + number + (stat.percent() ? "%" : "");
    }
}
