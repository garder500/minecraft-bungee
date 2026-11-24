package win.vampyuyu.survival.runes;

import java.util.Set;
import org.bukkit.Material;
import win.vampyuyu.survival.core.CustomItemDef;
import win.vampyuyu.survival.core.Items;
import win.vampyuyu.survival.core.Rarity;

/** The custom items of the rune system: Rune Powder and every rune. Each has its own pack model ({@code vampyuyu:rune/...},
 * drawn by {@code resource-pack/art/rune_items.py}); the vanilla material is only the fallback of a client without the pack.
 * A rune id the pack has no sprite for (the list is editable in the panel) keeps the vanilla look. */
final class RuneItems {
    static final String POWDER = "rune_powder";
    /** PDC: the rune id (string) and its rank (long, 0 for a Realm rune) on a rune item. */
    static final String RUNE = "rune";
    static final String RANK = "rune_rank";
    /** PDC on equipment: the engraved runes ({@link RuneRules#parse}). */
    static final String ENGRAVED = "runes";

    /** Ids that have a sprite in the pack: keep in step with {@code STAT_IDS} and {@code REALM_IDS} of rune_items.py. */
    private static final Set<String> STAT_SPRITES = Set.of("vitality", "ward", "might", "aether");
    private static final Set<String> REALM_SPRITES = Set.of("scorch", "verdant", "stasis", "rewind", "blight", "tempest",
        "undertow", "soulbind");

    private RuneItems() {}

    /** The pack model of a rune item, or null when the pack has no sprite for that rune. */
    static String model(Rune rune, int rank) {
        if (rune.realm()) {
            return REALM_SPRITES.contains(rune.id()) ? "vampyuyu:rune/" + rune.id() : null;
        }
        return STAT_SPRITES.contains(rune.id()) ? "vampyuyu:rune/" + rune.id() + "_" + rank : null;
    }

    static void register(Items items, RuneConfig config) {
        items.register(CustomItemDef.builder(POWDER, "Rune Powder", Rarity.UNCOMMON, Material.GLOWSTONE_DUST).type("RUNE")
            .itemModel("vampyuyu:rune/powder")
            .lore("<gray>Ground amethyst, still humming.", "<gray>Eight of it make a <aqua>rank I<gray> rune at the",
                "<gray>Runecraft table.").build());
        for (Rune rune : config.runes()) {
            if (rune.realm()) {
                items.register(CustomItemDef.builder(rune.itemId(0), rune.name() + " Rune", Rarity.EPIC, Material.ECHO_SHARD)
                    .type("RUNE").itemModel(model(rune, 0)).glint(true).pdc(RUNE, rune.id()).pdc(RANK, 0L)
                    .lore("<gold>" + RuneService.effectText(rune, config) + ".", "<gray>A Realm rune: a new effect instead of points.",
                        "<gray>Worth a rank III rune.", "",
                        "<dark_gray>Click it onto a " + rune.target().name().toLowerCase(java.util.Locale.ROOT)
                            + " in your inventory.").build());
                continue;
            }
            for (int rank = 1; rank <= 3; rank++) {
                Rarity rarity = rank == 1 ? Rarity.UNCOMMON : rank == 2 ? Rarity.RARE : Rarity.EPIC;
                Material material = rank == 1 ? Material.AMETHYST_SHARD : rank == 2 ? Material.ECHO_SHARD : Material.PRISMARINE_CRYSTALS;
                items.register(CustomItemDef.builder(rune.itemId(rank), rune.name() + " Rune " + Rune.roman(rank), rarity, material)
                    .type("RUNE").itemModel(model(rune, rank)).pdc(RUNE, rune.id()).pdc(RANK, (long) rank)
                    .lore("<gray>+" + config.value(rank) + " " + rune.stat().displayName() + " when engraved.", "",
                        "<dark_gray>Click it onto " + target(rune) + " in your inventory.").build());
            }
        }
    }

    static void unregister(Items items, RuneConfig config) {
        items.unregister(POWDER);
        for (Rune rune : config.runes()) {
            for (int rank = 0; rank <= 3; rank++) {
                items.unregister(rune.itemId(rank));
            }
        }
    }

    private static String target(Rune rune) {
        return switch (rune.target()) {
            case ARMOR -> "an armor piece";
            case WEAPON -> "a weapon";
            case ANY -> "a weapon or armor piece";
        };
    }
}
