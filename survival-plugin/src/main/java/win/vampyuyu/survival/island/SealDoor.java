package win.vampyuyu.survival.island;

import java.util.List;
import org.bukkit.Material;
import win.vampyuyu.survival.api.Realm;

/**
 * The sealed door where a Realm's exploration ends: a small framed door of the Realm's palette, one seal block in the
 * middle and a wall sign naming the Guardian and the key. Right-clicking the seal opens the Realm's Bastion
 * ({@code bastion.SealDoors}). Pure geometry, no server needed, shared by every Realm that has no bespoke Sarcophagus.
 *
 * <p>The door stands in the plane {@code z = cz}, facing south (+z): the seal is at {@code (cx, y + 1, cz)}, the frame is
 * five wide and four high, its fill recessed one block (z = cz - 1) so the seal stands out, the sign hangs on the right pillar (seen from the front), and the box in front of it is cleared.
 */
public final class SealDoor {
    /** The frame stands {@code +-REACH} around the centre. */
    public static final int REACH = 2;
    /** Height of the opening (the seal is the middle block of its middle column). */
    public static final int OPENING = 3;

    /** The blocks of one Realm's door. */
    public record Palette(Material frame, Material cap, Material fill, Material seal, String sealProps, Material light,
            Material wallSign) {}

    private SealDoor() {}

    public static Palette palette(Realm realm) {
        return switch (realm) {
            case ASHES -> new Palette(Material.POLISHED_BLACKSTONE_BRICKS, Material.CHISELED_POLISHED_BLACKSTONE, Material.MAGMA_BLOCK,
                Material.RESPAWN_ANCHOR, "charges=4", Material.SHROOMLIGHT, Material.OAK_WALL_SIGN);
            case GROVE -> new Palette(Material.MANGROVE_LOG, Material.STRIPPED_MANGROVE_WOOD, Material.MUSHROOM_STEM,
                Material.SHROOMLIGHT, null, Material.OCHRE_FROGLIGHT, Material.BIRCH_WALL_SIGN);
            case HEIGHTS -> new Palette(Material.PACKED_ICE, Material.CHISELED_STONE_BRICKS, Material.BLUE_ICE,
                Material.PEARLESCENT_FROGLIGHT, null, Material.SEA_LANTERN, Material.BIRCH_WALL_SIGN);
            case CITADEL -> new Palette(Material.CUT_SANDSTONE, Material.CHISELED_SANDSTONE, Material.CUT_RED_SANDSTONE,
                Material.GOLD_BLOCK, null, Material.GLOWSTONE, Material.BIRCH_WALL_SIGN);
            case MARSHES -> new Palette(Material.MUD_BRICKS, Material.PACKED_MUD, Material.SCULK,
                Material.OCHRE_FROGLIGHT, null, Material.SHROOMLIGHT, Material.BIRCH_WALL_SIGN);
            case ARCHIPELAGO -> new Palette(Material.CRYING_OBSIDIAN, Material.CRYING_OBSIDIAN, Material.WAXED_OXIDIZED_CUT_COPPER,
                Material.LODESTONE, null, Material.LIGHTNING_ROD, Material.OAK_WALL_SIGN);
            case DEPTHS -> new Palette(Material.DARK_PRISMARINE, Material.DARK_PRISMARINE, Material.DARK_PRISMARINE,
                Material.SEA_LANTERN, null, Material.SEA_LANTERN, Material.OAK_WALL_SIGN);
            case NECROPOLIS -> new Palette(Material.DEEPSLATE_TILES, Material.CHISELED_DEEPSLATE,
                Material.POLISHED_BLACKSTONE, Material.VERDANT_FROGLIGHT, null, Material.SEA_LANTERN, Material.BIRCH_WALL_SIGN);
        };
    }

    /** The Guardian as it fits on a sign line. */
    public static String guardianLine(Realm realm) {
        return switch (realm) {
            case ASHES -> "Lava Colossus";
            case GROVE -> "Mycelial Queen";
            case HEIGHTS -> "Boreal Watcher";
            case CITADEL -> "The Pharaoh";
            case MARSHES -> "The Abomination";
            case ARCHIPELAGO -> "Thunder Dragon";
            case DEPTHS -> "The Leviathan";
            case NECROPOLIS -> "The Judge";
        };
    }

    /** The first word of the Bastion key's display name ("<word> Bastion Key"). */
    public static String keyWord(Realm realm) {
        return switch (realm) {
            case ASHES -> "Ashes";
            case GROVE -> "Grove";
            case HEIGHTS -> "Heights";
            case CITADEL -> "Citadel";
            case MARSHES -> "Marsh";
            case ARCHIPELAGO -> "Tempest";
            case DEPTHS -> "Abyss";
            case NECROPOLIS -> "Necropolis";
        };
    }

    /** The sign: what is sealed, which key opens it, what to do. Each line fits one sign line. */
    public static List<String> signLines(Realm realm) {
        return List.of("Sealed", guardianLine(realm), keyWord(realm) + " Key", "Right-click");
    }

    /** The block a player right-clicks, for a door built with {@code (cx, y, cz)}. */
    public static int[] sealCell(int cx, int y, int cz) {
        return new int[] {cx, y + 1, cz};
    }

    /** Adds the door of a Realm to the plan. Call it after the rest of the Realm: it clears the box it stands in. */
    public static void build(BlockPlan plan, Realm realm, int cx, int y, int cz) {
        Palette p = palette(realm);
        // free the frame's box and the strip in front of it (decor or an older door must not stand in the way)
        for (int x = cx - REACH; x <= cx + REACH; x++) {
            for (int yy = y; yy <= y + OPENING + 2; yy++) {
                plan.air(x, yy, cz + 1);
                plan.air(x, yy, cz + 2);
            }
        }
        for (int x = cx - REACH; x <= cx + REACH; x++) {
            plan.set(x, y + OPENING, cz, p.cap());
        }
        for (int yy = y; yy < y + OPENING; yy++) {
            plan.set(cx - REACH, yy, cz, p.frame());
            plan.set(cx + REACH, yy, cz, p.frame());
            for (int x = cx - 1; x <= cx + 1; x++) {
                plan.air(x, yy, cz);
                plan.set(x, yy, cz - 1, p.fill()); // recessed: the seal stands out of the door like a boss
            }
        }
        plan.set(cx - REACH, y + OPENING + 1, cz, p.light());
        plan.set(cx + REACH, y + OPENING + 1, cz, p.light());
        int[] seal = sealCell(cx, y, cz);
        plan.set(seal[0], seal[1], seal[2], p.seal(), p.sealProps());
        List<String> lines = signLines(realm);
        plan.sign(cx + REACH, y + 1, cz + 1, p.wallSign(), "facing=south", lines.get(0), lines.get(1), lines.get(2), lines.get(3));
        plan.anchor("seal", seal[0], seal[1], seal[2]);
    }
}
