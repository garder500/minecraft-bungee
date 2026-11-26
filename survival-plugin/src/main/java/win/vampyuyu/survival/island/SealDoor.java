package win.vampyuyu.survival.island;

import java.util.List;
import org.bukkit.Material;
import win.vampyuyu.survival.api.Realm;

/**
 * The sealed door where a Realm's exploration ends: a monumental door of the Realm's palette (a plinth, a back wall,
 * outer columns, buttresses and a pointed pediment with lights), one seal block in the middle of its 3 x 3 opening, a
 * wall sign naming the Guardian and the key, and a rune lock of chains round the seal ({@link #sigil}, a display
 * model). Right-clicking the seal opens the Realm's Bastion
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
        // free the door's box and the strip in front of it (decor or an older door must not stand in the way)
        for (int x = cx - REACH - 2; x <= cx + REACH + 2; x++) {
            for (int yy = y; yy <= y + OPENING + 4; yy++) {
                plan.air(x, yy, cz + 1);
                plan.air(x, yy, cz + 2);
                if (Math.abs(x - cx) <= REACH + 2) {
                    plan.air(x, yy, cz);
                }
            }
        }
        // a plinth the whole width, one step proud of the door
        for (int x = cx - REACH - 2; x <= cx + REACH + 2; x++) {
            for (int z = cz - 1; z <= cz + 1; z++) {
                plan.set(x, y - 1, z, p.frame());
            }
        }
        // the back wall: the door is cut into it
        for (int x = cx - REACH - 1; x <= cx + REACH + 1; x++) {
            for (int yy = y; yy <= y + OPENING + 2; yy++) {
                plan.set(x, yy, cz - 1, p.fill());
            }
        }
        // the inner frame around the 3 x 3 opening, its lintel
        for (int yy = y; yy < y + OPENING; yy++) {
            plan.set(cx - REACH, yy, cz, p.frame());
            plan.set(cx + REACH, yy, cz, p.frame());
            for (int x = cx - 1; x <= cx + 1; x++) {
                plan.air(x, yy, cz);
            }
        }
        for (int x = cx - REACH; x <= cx + REACH; x++) {
            plan.set(x, y + OPENING, cz, p.cap());
        }
        // outer columns six high with their lights, low buttresses with lights
        for (int side : new int[] {-1, 1}) {
            int col = cx + side * (REACH + 1);
            for (int yy = y; yy <= y + OPENING + 2; yy++) {
                plan.set(col, yy, cz, p.frame());
            }
            plan.set(col, y + OPENING + 3, cz, p.light());
            int but = cx + side * (REACH + 2);
            plan.set(but, y, cz, p.frame());
            plan.set(but, y + 1, cz, p.cap());
            plan.set(but, y + 2, cz, p.light());
        }
        // the pointed pediment over the lintel, the keystone light
        for (int x = cx - REACH; x <= cx + REACH; x++) {
            plan.set(x, y + OPENING + 1, cz, p.cap());
        }
        for (int x = cx - 1; x <= cx + 1; x++) {
            plan.set(x, y + OPENING + 2, cz, p.cap());
        }
        plan.set(cx, y + OPENING + 3, cz, p.light());
        plan.set(cx, y + OPENING + 4, cz, p.cap());
        int[] seal = sealCell(cx, y, cz);
        plan.set(seal[0], seal[1], seal[2], p.seal(), p.sealProps());
        List<String> lines = signLines(realm);
        plan.sign(cx + REACH, y + 1, cz + 1, p.wallSign(), "facing=south", lines.get(0), lines.get(1), lines.get(2), lines.get(3));
        plan.anchor("seal", seal[0], seal[1], seal[2]);
    }

    /** Tint of a Realm's seal sigil (the colour of its Gate). */
    public static int sigilColor(Realm realm) {
        return QuaiPlan.haloColor(realm);
    }

    /**
     * The rune lock in front of a seal block (world coordinates of the block, the direction players stand in): a ring
     * round the seal and two chains across the door (a display model), turning slowly, in the Realm's colour.
     */
    public static List<BlockPlan.Model> sigil(Realm realm, int x, int y, int z, int faceX, int faceZ) {
        float yaw = (float) Math.toDegrees(Math.atan2(faceX, faceZ));
        return List.of(BlockPlan.Model.of("structure/seal_sigil", x + 0.5 + faceX * 0.75, y + 0.5, z + 0.5 + faceZ * 0.75, 1.6f)
            .tilt(0, yaw, 0).spin(4, "z").color(sigilColor(realm)));
    }
}
