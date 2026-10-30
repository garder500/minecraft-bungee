package win.vampyuyu.survival.core;

import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;

/** Item rarity, from the most common to the most exclusive. Order matters ({@code ordinal()} = tier). */
public enum Rarity {
    COMMON("COMMON", NamedTextColor.WHITE),
    UNCOMMON("UNCOMMON", NamedTextColor.GREEN),
    RARE("RARE", NamedTextColor.BLUE),
    EPIC("EPIC", NamedTextColor.DARK_PURPLE),
    LEGENDARY("LEGENDARY", NamedTextColor.GOLD),
    MYTHIC("MYTHIC", NamedTextColor.LIGHT_PURPLE),
    DIVINE("DIVINE", NamedTextColor.AQUA),
    /** Admin, event and quest items outside the normal ladder. */
    SPECIAL("SPECIAL", NamedTextColor.RED);

    private final String label;
    private final TextColor color;

    Rarity(String label, TextColor color) {
        this.label = label;
        this.color = color;
    }

    /** Upper case English label shown on the last lore line. */
    public String label() {
        return label;
    }

    public TextColor color() {
        return color;
    }
}
