package win.vampyuyu.survival.dungeon.gen;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import win.vampyuyu.survival.dungeon.puzzle.BoulderField;
import win.vampyuyu.survival.dungeon.puzzle.PuzzleSpec;
import win.vampyuyu.survival.dungeon.puzzle.SequenceState;
import win.vampyuyu.survival.dungeon.puzzle.Weirdos;

/** Draws the apparatus of the five puzzles and returns the positions the runtime needs. */
final class PuzzleTemplates {
    private PuzzleTemplates() {}

    private static final String[] ROMAN = {"", "I", "II", "III", "IV", "V", "VI"};
    private static final String[] WORDS = {"", "one", "two", "three", "four", "five", "six"};

    static void build(Ctx c) {
        PuzzleKind kind = c.plan.puzzle == null ? PuzzleKind.LEVER_SEQUENCE : c.plan.puzzle;
        Pos chest = rewardCage(c);
        c.out.puzzle = switch (kind) {
            case LEVER_SEQUENCE -> levers(c, chest);
            case PLATE_ORDER -> plates(c, chest);
            case TIC_TAC_TOE -> ticTacToe(c, chest);
            case WEIRDOS -> weirdos(c, chest);
            case BOULDER_PATH -> boulder(c, chest);
        };
    }

    /** The reward chest sits inside an iron bar cage until the puzzle is solved. */
    private static Pos rewardCage(Ctx c) {
        c.put(4, 0, 4, "chest[facing=east]");
        for (int x = 3; x <= 5; x++) {
            for (int z = 3; z <= 5; z++) {
                for (int y = 0; y <= 2; y++) {
                    boolean inner = x == 4 && z == 4 && y < 2;
                    if (!inner) {
                        c.put(x, y, z, "iron_bars");
                    }
                }
            }
        }
        c.put(4, 2, 4, "iron_bars");
        c.occupy(2, 2, 6, 6);
        return c.pos(4, 0, 4);
    }

    private static PuzzleSpec levers(Ctx c, Pos chest) {
        c.fill(8, -1, 10, 23, 4, 11, c.pal.wall());
        c.fill(8, 4, 10, 23, 4, 11, c.pal.pillar());
        List<Pos> levers = new ArrayList<>();
        List<Pos> lamps = new ArrayList<>();
        int[] xs = {9, 12, 15, 18, 21};
        for (int x : xs) {
            c.put(x, 3, 11, "redstone_lamp[lit=false]");
            c.put(x, 1, 12, "lever[face=wall,facing=south,powered=false]");
            c.put(x, 0, 12, c.pal.accent());
            levers.add(c.pos(x, 1, 12));
            lamps.add(c.pos(x, 3, 11));
        }
        c.occupy(7, 9, 24, 13);
        int length = Math.min(6, 3 + c.floor.number() / 2);
        int[] sequence = SequenceState.randomSequence(c.rng, length, xs.length);
        return new PuzzleSpec.Levers(levers, lamps, sequence, chest);
    }

    private static PuzzleSpec plates(Ctx c, Pos chest) {
        int[] xs = {9, 15, 21};
        int[] zs = {12, 18};
        List<Integer> ranks = new ArrayList<>(List.of(1, 2, 3, 4, 5, 6));
        Collections.shuffle(ranks, c.rng);
        List<Pos> plates = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        int i = 0;
        for (int z : zs) {
            for (int x : xs) {
                c.fill(x - 1, -1, z - 1, x + 1, -1, z + 1, c.pal.accent());
                c.put(x, -1, z, "chiseled_stone_bricks");
                c.put(x, 0, z, "polished_blackstone_pressure_plate");
                plates.add(c.pos(x, 0, z));
                int rank = ranks.get(i++);
                int form = c.rng.nextInt(3);
                labels.add(form == 0 ? Integer.toString(rank) : form == 1 ? ROMAN[rank] : WORDS[rank]);
                c.occupy(x - 2, z - 2, x + 2, z + 2);
            }
        }
        // order: plate indexes sorted by rank ascending
        int[] order = new int[6];
        for (int r = 1; r <= 6; r++) {
            order[r - 1] = ranks.indexOf(r);
        }
        return new PuzzleSpec.Plates(plates, labels, order, chest);
    }

    private static PuzzleSpec ticTacToe(Ctx c, Pos chest) {
        c.fill(10, -1, 10, 20, 8, 10, c.pal.wall());
        c.fill(10, 8, 10, 20, 8, 10, c.pal.pillar());
        List<Pos> panels = new ArrayList<>();
        int[] rows = {5, 3, 1};
        int[] cols = {13, 15, 17};
        for (int y : rows) {
            for (int x : cols) {
                c.put(x, y, 11, "white_concrete");
                panels.add(c.pos(x, y, 11));
            }
        }
        for (int x = 12; x <= 18; x++) {
            c.put(x, 6, 11, c.pal.accent());
            c.put(x, 0, 11, c.pal.accent());
        }
        for (int y = 1; y <= 5; y++) {
            c.put(12, y, 11, c.pal.accent());
            c.put(18, y, 11, c.pal.accent());
        }
        c.put(14, 2, 11, c.pal.accent());
        c.put(14, 4, 11, c.pal.accent());
        c.put(16, 2, 11, c.pal.accent());
        c.put(16, 4, 11, c.pal.accent());
        c.occupy(9, 9, 21, 13);
        return new PuzzleSpec.TicTacToe(panels, chest);
    }

    private static PuzzleSpec weirdos(Ctx c, Pos chest) {
        Weirdos w = Weirdos.generate(c.rng);
        int[] xs = {8, 15, 22};
        String[] wool = {"red_wool", "lime_wool", "blue_wool"};
        List<Pos> spots = new ArrayList<>();
        List<Pos> chests = new ArrayList<>();
        List<String> statements = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            int x = xs[i];
            c.fill(x - 1, -1, 7, x + 1, -1, 9, c.pal.accent());
            c.put(x, -1, 8, c.pal.pillar());
            c.put(x - 1, 0, 7, "candle[candles=2,lit=true]");
            c.put(x + 1, 0, 9, "candle[candles=3,lit=true]");
            spots.add(c.pos(x, 0, 8));
            statements.add(w.statement(i));
            c.fill(x - 1, -1, 20, x + 1, -1, 22, wool[i]);
            c.put(x, 0, 21, "chest[facing=north]");
            chests.add(c.pos(x, 0, 21));
            c.occupy(x - 2, 6, x + 2, 10);
            c.occupy(x - 2, 19, x + 2, 23);
        }
        return new PuzzleSpec.Weirdo(spots, chests, statements, w.treasure(), chest);
    }

    private static PuzzleSpec boulder(Ctx c, Pos chest) {
        BoulderField f = BoulderField.generate(c.rng, 9, 9, 8 + c.floor.number(), 3 + c.floor.number() / 2);
        c.fill(10, -1, 10, 20, -1, 20, "smooth_stone");
        for (int i = 10; i <= 20; i++) {
            c.put(i, 0, 10, c.pal.slab() + "[type=bottom]");
            c.put(i, 0, 20, c.pal.slab() + "[type=bottom]");
            c.put(10, 0, i, c.pal.slab() + "[type=bottom]");
            c.put(20, 0, i, c.pal.slab() + "[type=bottom]");
        }
        for (int x = 0; x < f.w; x++) {
            for (int z = 0; z < f.h; z++) {
                if (f.isWall(x, z)) {
                    c.fill(11 + x, 0, 11 + z, 11 + x, 1, 11 + z, c.pal.pillar());
                }
            }
        }
        c.put(11 + f.targetX, -1, 11 + f.targetZ, "gold_block");
        c.put(11 + f.startX, 0, 11 + f.startZ, "mossy_cobblestone");
        c.put(15, 0, 8, c.pal.accent());
        c.put(15, 1, 8, "stone_button[face=floor,facing=north,powered=false]");
        c.occupy(9, 7, 21, 21);
        return new PuzzleSpec.Boulder(c.pos(11, 0, 11), f, c.pos(15, 1, 8), chest);
    }
}
