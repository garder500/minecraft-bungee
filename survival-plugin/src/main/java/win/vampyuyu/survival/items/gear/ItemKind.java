package win.vampyuyu.survival.items.gear;

import java.util.Locale;
import org.jspecify.annotations.Nullable;

/** What an item is, read from the type label of its {@code CustomItemDef} ("SWORD", "HELMET"...). */
public enum ItemKind {
    SWORD(Group.MELEE), AXE(Group.MELEE), PICKAXE(Group.TOOL), SHOVEL(Group.TOOL), HOE(Group.TOOL), DRILL(Group.TOOL),
    BOW(Group.RANGED), CROSSBOW(Group.RANGED), ROD(Group.TOOL), STAFF(Group.MELEE), WAND(Group.MELEE),
    HOOK(Group.TOOL), TRIDENT(Group.MELEE),
    HELMET(Group.ARMOR), CHESTPLATE(Group.ARMOR), LEGGINGS(Group.ARMOR), BOOTS(Group.ARMOR),
    ACCESSORY(Group.ACCESSORY), BOOK(Group.OTHER), PET(Group.OTHER), OTHER(Group.OTHER);

    /** Coarse grouping used by enchant applicability and the engine. */
    public enum Group { MELEE, RANGED, TOOL, ARMOR, ACCESSORY, OTHER }

    private final Group group;

    ItemKind(Group group) {
        this.group = group;
    }

    public Group group() { return group; }

    public boolean armor() { return group == Group.ARMOR; }

    public boolean weapon() { return group == Group.MELEE || group == Group.RANGED; }

    public boolean usesBlocks() { return this == PICKAXE || this == AXE || this == SHOVEL || this == HOE || this == DRILL; }

    /** Parses a type label (case insensitive), {@code OTHER} for null or unknown labels. */
    public static ItemKind of(@Nullable String label) {
        if (label == null) {
            return OTHER;
        }
        String normal = label.trim().toUpperCase(Locale.ROOT).replace(' ', '_');
        for (ItemKind kind : values()) {
            if (kind.name().equals(normal)) {
                return kind;
            }
        }
        if (normal.endsWith("TALISMAN") || normal.endsWith("RING") || normal.endsWith("ARTIFACT")) {
            return ACCESSORY;
        }
        return OTHER;
    }
}
