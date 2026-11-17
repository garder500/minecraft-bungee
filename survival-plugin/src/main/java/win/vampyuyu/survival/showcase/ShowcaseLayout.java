package win.vampyuyu.survival.showcase;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import win.vampyuyu.survival.island.BlockPlan;

/**
 * The gallery as data, relative to its origin (x east, y up from the floor block, z south): a hall {@link #WIDTH}
 * wide whose two long walls carry one glowing item frame per registered item (grouped by owner, one sign per group),
 * a row of placed custom blocks on pedestals down the north side of the aisle and a row of big rotating 3D items down
 * the south side. Pure: no world access, tested without a server.
 */
public final class ShowcaseLayout {
    /** Hall interior: z 0..WIDTH-1. */
    public static final int WIDTH = 15;
    /** Frame rows per wall. */
    public static final int ROWS = 4;
    /** Height of the walls above the floor block. */
    public static final int WALL_HEIGHT = 9;
    /** First column used by the groups, blocks and displays (the entrance is before it). */
    public static final int FIRST_X = 8;
    /** Empty columns between two groups on a wall. */
    public static final int GAP = 2;
    /** A group longer than this many columns is split into sections, each with its own sign. */
    public static final int SECTION_COLS = 30;
    /** Rotating displays: at most this many per group, and in total. */
    public static final int SPIN_PER_GROUP = 3;
    public static final int SPIN_CAP = 24;
    public static final int BLOCK_Z = 4;
    public static final int BLOCK_STEP = 3;
    public static final int SPIN_Z = 10;
    public static final int SPIN_STEP = 5;
    public static final float SPIN_SCALE = 2f;
    public static final int FRAME_TOP = 5;
    public static final int SIGN_Y = 7;

    /** One registered item. {@code spin} marks a weapon, tool, core or helmet worth showing big and rotating. */
    public record Item(String id, String name, String group, boolean spin) {}

    /** A placed custom block: {@code itemId} gives the model, {@code modelKey} an optional item-model override. */
    public record BlockEntry(String itemId, String title, Material hitbox, String modelKey) {}

    public record Frame(String id, String group, int x, int y, int z, BlockFace face) {}

    public record Sign(List<String> lines, int x, int y, int z, BlockFace facing) {}

    /** A placed block: the hitbox block at (x,y,z) with its model drawn over it. */
    public record BlockSlot(BlockEntry entry, int x, int y, int z) {}

    /** A big rotating item hovering at (x+0.5, y, z+0.5) above its pedestal. */
    public record SpinSlot(Item item, int x, double y, int z) {}

    /** A section of a group on one wall: columns {@code [x0, x1]}. */
    public record Section(String group, int part, int parts, boolean north, int x0, int x1, int items) {}

    private final List<Frame> frames = new ArrayList<>();
    private final List<Sign> signs = new ArrayList<>();
    private final List<BlockSlot> blocks = new ArrayList<>();
    private final List<SpinSlot> spins = new ArrayList<>();
    private final List<Section> sections = new ArrayList<>();
    private int length;

    private ShowcaseLayout() {
    }

    public List<Frame> frames() { return frames; }

    public List<Sign> signs() { return signs; }

    public List<BlockSlot> blocks() { return blocks; }

    public List<SpinSlot> spins() { return spins; }

    public List<Section> sections() { return sections; }

    /** Last interior column. */
    public int length() { return length; }

    /** Where a visitor arrives, relative to the origin (a standing position on the floor, facing east). */
    public static double[] entrance() {
        return new double[] {2.5, 1, WIDTH / 2 + 0.5};
    }

    public static final float ENTRANCE_YAW = -90f;

    /** Items sorted for display: the "core" group first, then groups by name, then ids. */
    public static List<Item> ordered(List<Item> items) {
        List<Item> sorted = new ArrayList<>(items);
        sorted.sort(Comparator.comparing((Item i) -> !"core".equals(i.group())).thenComparing(Item::group).thenComparing(Item::id));
        return sorted;
    }

    public static ShowcaseLayout build(List<Item> items, List<BlockEntry> blockEntries) {
        ShowcaseLayout layout = new ShowcaseLayout();
        Map<String, List<Item>> groups = new LinkedHashMap<>();
        for (Item item : ordered(items)) {
            groups.computeIfAbsent(item.group(), g -> new ArrayList<>()).add(item);
        }
        int[] cursor = {FIRST_X, FIRST_X};
        int perSection = ROWS * SECTION_COLS;
        for (Map.Entry<String, List<Item>> group : groups.entrySet()) {
            List<Item> all = group.getValue();
            int parts = (all.size() + perSection - 1) / perSection;
            for (int part = 0; part < parts; part++) {
                List<Item> chunk = all.subList(part * perSection, Math.min(all.size(), (part + 1) * perSection));
                boolean north = cursor[0] <= cursor[1];
                int wall = north ? 0 : 1;
                int x0 = cursor[wall];
                int cols = (chunk.size() + ROWS - 1) / ROWS;
                int z = north ? 0 : WIDTH - 1;
                BlockFace face = north ? BlockFace.SOUTH : BlockFace.NORTH;
                for (int i = 0; i < chunk.size(); i++) {
                    layout.frames.add(new Frame(chunk.get(i).id(), group.getKey(), x0 + i / ROWS, FRAME_TOP - i % ROWS, z, face));
                }
                layout.sections.add(new Section(group.getKey(), part + 1, parts, north, x0, x0 + cols - 1, chunk.size()));
                List<String> lines = new ArrayList<>();
                lines.add(title(group.getKey()));
                lines.add(chunk.size() + (chunk.size() == 1 ? " item" : " items"));
                if (parts > 1) {
                    lines.add("part " + (part + 1) + "/" + parts);
                }
                layout.signs.add(new Sign(lines, x0, SIGN_Y, z, face));
                cursor[wall] = x0 + cols + GAP;
            }
        }
        int end = Math.max(cursor[0], cursor[1]);

        int bx = FIRST_X;
        for (BlockEntry entry : blockEntries) {
            layout.blocks.add(new BlockSlot(entry, bx, 2, BLOCK_Z));
            layout.signs.add(new Sign(wrap(entry.title()), bx, 1, BLOCK_Z + 1, BlockFace.SOUTH));
            bx += BLOCK_STEP;
        }
        int sx = FIRST_X;
        Map<String, Integer> perGroup = new LinkedHashMap<>();
        for (Item item : ordered(items)) {
            if (!item.spin() || layout.spins.size() >= SPIN_CAP || perGroup.merge(item.group(), 1, Integer::sum) > SPIN_PER_GROUP) {
                continue;
            }
            layout.spins.add(new SpinSlot(item, sx, 4.0, SPIN_Z));
            layout.signs.add(new Sign(wrap(item.name()), sx, 1, SPIN_Z - 1, BlockFace.NORTH));
            sx += SPIN_STEP;
        }
        layout.length = Math.max(Math.max(end, bx), sx) + 2;
        return layout;
    }

    /** "coal_generator" or "Coal Generator" as sign text: up to 4 lines of at most 15 characters. */
    static List<String> wrap(String text) {
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.trim().split("\\s+")) {
            if (line.length() > 0 && line.length() + 1 + word.length() > 15) {
                lines.add(line.toString());
                line.setLength(0);
            }
            if (line.length() > 0) {
                line.append(' ');
            }
            line.append(word.length() > 15 ? word.substring(0, 15) : word);
            if (lines.size() == 3 && line.length() == 15) {
                break;
            }
        }
        if (line.length() > 0 && lines.size() < 4) {
            lines.add(line.toString());
        }
        return lines;
    }

    /** "quarry" becomes "Quarry", "hellgao" stays readable. */
    static String title(String group) {
        String clean = group.replace('_', ' ').replace('-', ' ');
        return clean.isEmpty() ? clean : Character.toUpperCase(clean.charAt(0)) + clean.substring(1).toLowerCase(Locale.ROOT);
    }

    /** The static blocks: floor with a sea lantern grid, walls, end wall, roof, pedestals and every sign. */
    public BlockPlan plan() {
        BlockPlan plan = new BlockPlan();
        for (int x = 0; x <= length; x++) {
            for (int z = 0; z < WIDTH; z++) {
                plan.set(x, 0, z, x % 3 == 1 && z % 3 == 1 ? Material.SEA_LANTERN : Material.POLISHED_DEEPSLATE);
            }
        }
        plan.fill(-1, 0, -1, length + 1, WALL_HEIGHT, -1, Material.BLACK_CONCRETE);
        plan.fill(-1, 0, WIDTH, length + 1, WALL_HEIGHT, WIDTH, Material.BLACK_CONCRETE);
        plan.fill(length + 1, 0, 0, length + 1, WALL_HEIGHT, WIDTH - 1, Material.BLACK_CONCRETE);
        // closed roof (a lantern grid like the floor) so the hall is no longer open to the sky
        for (int x = -1; x <= length + 1; x++) {
            for (int z = -1; z <= WIDTH; z++) {
                plan.set(x, WALL_HEIGHT + 1, z, x % 3 == 1 && z % 3 == 1 ? Material.SEA_LANTERN : Material.BLACK_CONCRETE);
            }
        }
        for (BlockSlot slot : blocks) {
            plan.set(slot.x(), 1, slot.z(), Material.POLISHED_BLACKSTONE);
            plan.set(slot.x(), slot.y(), slot.z(), slot.entry().hitbox());
        }
        for (SpinSlot slot : spins) {
            plan.set(slot.x(), 1, slot.z(), Material.POLISHED_BLACKSTONE);
        }
        for (Sign sign : signs) {
            plan.sign(sign.x(), sign.y(), sign.z(), Material.OAK_WALL_SIGN, "facing=" + sign.facing().name().toLowerCase(Locale.ROOT),
                sign.lines().toArray(new String[0]));
        }
        return plan;
    }

    /** Inclusive bounds {minX, minY, minZ, maxX, maxY, maxZ} of everything the gallery occupies, entities included. */
    public int[] bounds() {
        return bounds(length);
    }

    public static int[] bounds(int length) {
        return new int[] {-2, 0, -2, length + 2, WALL_HEIGHT + 3, WIDTH + 1};
    }
}
