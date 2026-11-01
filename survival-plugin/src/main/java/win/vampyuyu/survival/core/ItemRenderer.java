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
 * Might: +50 (+5)           one line per stat, enum order; "(+5)" = bonus from reforges/enchants
 * Ward: +10
 *                           blank, only when stats and description both exist
 * description lines...
 *                           blank
 * ⛰ Earth Element · Tier 1  small, element colour (items without element: "Epic Chestplate" in the rarity colour)
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
        out.add(lastLine(def, def.rarity(), false));
        return out;
    }

    /**
     * The small closing line: {@code ⛰ Earth Element · Tier 1} in the element colour, {@code Tier 2} in the rarity
     * colour, or the plain rarity and type ({@code Epic Chestplate}) for items with neither. Never bold.
     */
    public static Component lastLine(CustomItemDef def, win.vampyuyu.survival.core.Rarity rarity, boolean recombobulated) {
        java.util.List<String> parts = new ArrayList<>();
        net.kyori.adventure.text.format.TextColor color = rarity.color();
        if (def.element() != null) {
            parts.add(def.element().symbol() + " " + def.element().displayName() + " Element");
            color = def.element().color();
        }
        if (def.tier() != null) {
            parts.add("Tier " + def.tier());
        }
        String text;
        if (parts.isEmpty()) {
            String type = def.typeLabel() == null ? "" : " " + titleCase(def.typeLabel());
            text = titleCase(rarity.label()) + type;
        } else {
            text = String.join(" · ", parts);
        }
        return line(Component.text((recombobulated ? "\u2726 " : "") + text, color));
    }

    private static String titleCase(String text) {
        StringBuilder out = new StringBuilder();
        for (String word : text.toLowerCase(java.util.Locale.ROOT).split(" ")) {
            if (word.isEmpty()) {
                continue;
            }
            if (out.length() > 0) {
                out.append(' ');
            }
            out.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return out.toString();
    }

    private static String signed(double value, Stat stat) {
        String number = value == Math.rint(value) ? Long.toString((long) value) : Double.toString(Math.round(value * 10) / 10.0);
        return (value >= 0 ? "+" : "") + number + (stat.percent() ? "%" : "");
    }
}
