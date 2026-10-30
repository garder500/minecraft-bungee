package win.vampyuyu.survival.dungeon.gen;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import win.vampyuyu.survival.dungeon.DungeonConfig;

/** Turns a {@link Layout} into blocks: rooms from the code templates, then the doorways. Pure and deterministic. */
public final class DungeonBuilder {
    private DungeonBuilder() {}

    public static BuiltDungeon build(Layout layout, DungeonConfig.Floor floor, int baseY) {
        BlockBuffer buffer = new BlockBuffer();
        Palette palette = Palette.byName(floor.palette());
        Map<Integer, RoomBuild> rooms = new LinkedHashMap<>();
        for (RoomPlan plan : layout.rooms()) {
            Random rng = new Random(layout.seed * 1_000_003L + plan.index * 7919L + 13);
            RoomBuild out = new RoomBuild(plan, baseY);
            Ctx ctx = new Ctx(buffer, layout, plan, out, palette, rng, floor, baseY);
            RoomTemplates.build(ctx);
            rooms.put(plan.index, out);
        }
        List<BuiltDungeon.DoorBuild> doors = new ArrayList<>();
        for (int i = 0; i < layout.doors().size(); i++) {
            doors.add(door(layout, buffer, palette, layout.doors().get(i), i, baseY));
        }
        // the player side of the blood door, the boss room spawn
        BuiltDungeon built = new BuiltDungeon(layout, buffer, rooms, doors, baseY);
        for (BuiltDungeon.DoorBuild d : doors) {
            if (d.plan().kind == DoorKind.BLOOD) {
                RoomBuild blood = rooms.get(layout.blood().index);
                RoomBuild boss = rooms.get(layout.boss().index);
                boolean bloodIsA = d.plan().a == layout.blood().index;
                blood.bloodSign = bloodIsA ? d.sideA().add(0, 3, 0) : d.sideB().add(0, 3, 0);
                boss.spawn = bloodIsA ? d.sideB() : d.sideA();
            }
        }
        return built;
    }

    private static BuiltDungeon.DoorBuild door(Layout layout, BlockBuffer buffer, Palette pal, DoorPlan d, int index,
            int baseY) {
        List<Pos> blocks = Geo.doorBlocks(layout, d, baseY);
        RoomPlan a = layout.room(d.a);
        RoomPlan b = layout.room(d.b);
        String fill;
        String frame;
        switch (d.kind) {
            case WITHER -> {
                fill = "coal_block";
                frame = "crying_obsidian";
            }
            case BLOOD -> {
                fill = "red_concrete";
                frame = "crying_obsidian";
            }
            case SECRET -> {
                fill = pal.wall();
                frame = pal.wall();
            }
            default -> {
                fill = "air";
                frame = pal.pillar();
            }
        }
        for (Pos p : blocks) {
            buffer.set(p.x(), p.y(), p.z(), fill);
        }
        // frame: two columns and a lintel around the 4x4 opening, on both wall layers
        int minX = blocks.stream().mapToInt(Pos::x).min().orElse(0);
        int maxX = blocks.stream().mapToInt(Pos::x).max().orElse(0);
        int minZ = blocks.stream().mapToInt(Pos::z).min().orElse(0);
        int maxZ = blocks.stream().mapToInt(Pos::z).max().orElse(0);
        boolean alongX = d.dir == Dir.SOUTH; // opening spans x when the wall runs along x
        for (int layer = 0; layer < 2; layer++) {
            for (int h = 0; h <= Geo.DOOR_H; h++) {
                if (alongX) {
                    int z = minZ + layer;
                    buffer.set(minX - 1, baseY + h, z, frame);
                    buffer.set(maxX + 1, baseY + h, z, frame);
                    if (h == Geo.DOOR_H) {
                        for (int x = minX; x <= maxX; x++) {
                            buffer.set(x, baseY + h, z, frame);
                        }
                    }
                } else {
                    int x = minX + layer;
                    buffer.set(x, baseY + h, minZ - 1, frame);
                    buffer.set(x, baseY + h, maxZ + 1, frame);
                    if (h == Geo.DOOR_H) {
                        for (int z = minZ; z <= maxZ; z++) {
                            buffer.set(x, baseY + h, z, frame);
                        }
                    }
                }
            }
        }
        // light at the doorway on both sides
        if (d.kind != DoorKind.SECRET) {
            String torch = pal.wallTorch();
            if (alongX) {
                buffer.set(minX - 1, baseY + 3, minZ - 1, torch + "[facing=north]");
                buffer.set(maxX + 1, baseY + 3, minZ - 1, torch + "[facing=north]");
                buffer.set(minX - 1, baseY + 3, maxZ + 1, torch + "[facing=south]");
                buffer.set(maxX + 1, baseY + 3, maxZ + 1, torch + "[facing=south]");
            } else {
                buffer.set(minX - 1, baseY + 3, minZ - 1, torch + "[facing=west]");
                buffer.set(minX - 1, baseY + 3, maxZ + 1, torch + "[facing=west]");
                buffer.set(maxX + 1, baseY + 3, minZ - 1, torch + "[facing=east]");
                buffer.set(maxX + 1, baseY + 3, maxZ + 1, torch + "[facing=east]");
            }
        }
        Dir fromA = d.dir;
        Pos sideA = Geo.doorLanding(layout, a, fromA, baseY);
        Pos sideB = Geo.doorLanding(layout, b, fromA.opposite(), baseY);
        return new BuiltDungeon.DoorBuild(index, d, blocks, fill, sideA, sideB);
    }
}
