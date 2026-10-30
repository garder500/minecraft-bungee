package win.vampyuyu.survival.items.hud;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.jspecify.annotations.Nullable;
import win.vampyuyu.survival.core.Msg;

/**
 * Pure builders of the action bar line. Colours stay on the named palette: the resource pack's HUD shader owns
 * the red channel value 78, so no custom hex colour with that value may appear here.
 */
public final class HudFormat {
    private HudFormat() {}

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

    /** Parses a "§" coded notice. */
    public static Component legacy(String text) {
        return LEGACY.deserialize(text);
    }

    /** "+3.2", "+12", "+0.5". */
    public static String gain(double amount) {
        double rounded = Math.round(amount * 10.0) / 10.0;
        String number = rounded == Math.rint(rounded) ? Long.toString((long) rounded) : Double.toString(rounded);
        return "+" + number;
    }

    /** The text of a skill XP popup: {@code +3.2 Mining (Lv 7, 1,234 XP)}. */
    public static String xpPopup(String skill, double gained, int level, double totalXp) {
        return gain(gained) + " " + skill + " (Lv " + level + ", " + Msg.grouped(totalXp) + " XP)";
    }

    /**
     * Builds the bar: health on the left, the centre message (notice, XP popup or Defense) and the mana on the right.
     *
     * @param centre a plain centre text already coloured by the caller, or null to show Defense
     */
    public static Component line(double health, double maxHealth, double absorption, double defense,
            @Nullable Component centre, double mana, double maxMana, double overflow) {
        Component left = Component.text(Msg.grouped(Math.ceil(health)) + "/" + Msg.grouped(Math.ceil(maxHealth)) + "❤",
            NamedTextColor.RED);
        if (absorption > 0.5) {
            left = left.append(Component.text(" +" + Msg.grouped(Math.ceil(absorption)), NamedTextColor.GOLD));
        }
        Component middle = centre != null ? centre
            : Component.text(Msg.grouped(Math.floor(defense)) + "❈ Defense", NamedTextColor.GREEN);
        Component right = Component.text(Msg.grouped(Math.floor(mana)) + "/" + Msg.grouped(Math.floor(maxMana)) + "✎ Mana",
            NamedTextColor.AQUA);
        if (overflow >= 1) {
            right = right.append(Component.text(" " + Msg.grouped(Math.floor(overflow)) + "ʬ", NamedTextColor.DARK_AQUA));
        }
        Component gap = Component.text("    ");
        return left.append(gap).append(middle).append(gap).append(right);
    }

    /** Plain text of a component (tests, change detection). */
    public static String plain(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }
}
