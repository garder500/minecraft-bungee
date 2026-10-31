package win.vampyuyu.survival.quarry;

import java.util.Optional;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;

/** The sword skins. Each is unlocked once for the account with its token (item {@code skin_<id>}). */
enum Skin {
    KITSUNE("kitsune", "Kitsune", "Common", NamedTextColor.WHITE, "Quest reward: First Blade"),
    TITAN_EDGE("titan_edge", "Titan Edge", "Rare", NamedTextColor.BLUE, "Dungeon floors 4 and 5, rank S"),
    CRYSTAL_SABER("crystal_saber", "Crystal Saber", "Rare", NamedTextColor.BLUE, "Forge: 12 Sunstone and 6 diamonds"),
    FLAME_BRAND("flame_brand", "Flame Brand", "Epic", NamedTextColor.DARK_PURPLE, "Rare drop of the Magma Warden"),
    FROST_FANG("frost_fang", "Frost Fang", "Epic", NamedTextColor.DARK_PURPLE, "Rare drop of the Frost Revenant"),
    WYRMFANG("wyrmfang", "Wyrmfang", "Mythic", NamedTextColor.LIGHT_PURPLE, "First victory over the Voidwyrm");

    final String id;
    final String title;
    final String rarity;
    final TextColor color;
    final String source;

    Skin(String id, String title, String rarity, TextColor color, String source) {
        this.id = id;
        this.title = title;
        this.rarity = rarity;
        this.color = color;
        this.source = source;
    }

    String tokenId() {
        return "skin_" + id;
    }

    String model() {
        return "vampyuyu:skin/" + id;
    }

    static Optional<Skin> byId(String id) {
        for (Skin skin : values()) {
            if (skin.id.equals(id)) {
                return Optional.of(skin);
            }
        }
        return Optional.empty();
    }

    static Optional<Skin> ofToken(String itemId) {
        for (Skin skin : values()) {
            if (skin.tokenId().equals(itemId)) {
                return Optional.of(skin);
            }
        }
        return Optional.empty();
    }
}
