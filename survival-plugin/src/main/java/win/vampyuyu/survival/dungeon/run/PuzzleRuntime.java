package win.vampyuyu.survival.dungeon.run;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import win.vampyuyu.survival.dungeon.gen.Pos;
import win.vampyuyu.survival.dungeon.puzzle.BoulderField;
import win.vampyuyu.survival.dungeon.puzzle.PuzzleSpec;
import win.vampyuyu.survival.dungeon.puzzle.SequenceState;
import win.vampyuyu.survival.dungeon.puzzle.TicTacToe;

/** Behaviour of one puzzle room. Every method runs on the main thread. */
abstract class PuzzleRuntime {
    protected final DungeonRun run;
    protected final RoomRt room;
    boolean solved;

    PuzzleRuntime(DungeonRun run, RoomRt room) {
        this.run = run;
        this.room = room;
    }

    /** First time a player walks into the room. */
    void onFirstEnter() {}

    /** Right click on a block. Returns true when the block belongs to the puzzle (the click is consumed). */
    boolean click(Player p, Block b) { return false; }

    /** Stepping on a pressure plate. */
    boolean step(Player p, Block b) { return false; }

    /** Right click on an entity. */
    boolean clickEntity(Player p, Entity e) { return false; }

    abstract String instructions();

    static boolean at(Pos p, Block b) {
        return p.x() == b.getX() && p.y() == b.getY() && p.z() == b.getZ();
    }

    static int indexOf(List<Pos> list, Block b) {
        for (int i = 0; i < list.size(); i++) {
            if (at(list.get(i), b)) {
                return i;
            }
        }
        return -1;
    }

    static PuzzleRuntime create(DungeonRun run, RoomRt room) {
        PuzzleSpec spec = room.build.puzzle;
        if (spec instanceof PuzzleSpec.Levers l) {
            return new Levers(run, room, l);
        }
        if (spec instanceof PuzzleSpec.Plates p) {
            return new Plates(run, room, p);
        }
        if (spec instanceof PuzzleSpec.TicTacToe t) {
            return new Ttt(run, room, t);
        }
        if (spec instanceof PuzzleSpec.Weirdo w) {
            return new WeirdoPuzzle(run, room, w);
        }
        if (spec instanceof PuzzleSpec.Boulder b) {
            return new BoulderPuzzle(run, room, b);
        }
        return null;
    }

    // ------------------------------------------------------------------ echo levers

    static final class Levers extends PuzzleRuntime {
        private final PuzzleSpec.Levers spec;
        private final SequenceState state;
        private boolean showing;

        Levers(DungeonRun run, RoomRt room, PuzzleSpec.Levers spec) {
            super(run, room);
            this.spec = spec;
            this.state = new SequenceState(spec.sequence());
        }

        @Override
        String instructions() {
            return "<gray>The lamps will flash in a pattern. Pull the matching <white>levers</white> in the same order.";
        }

        @Override
        void onFirstEnter() {
            play(40);
        }

        private void flash(int lamp, boolean on) {
            run.setBlock(spec.lamps().get(lamp), "redstone_lamp[lit=" + on + "]");
        }

        private void play(int delay) {
            showing = true;
            int[] seq = spec.sequence();
            for (int i = 0; i < seq.length; i++) {
                final int lamp = seq[i];
                run.later(delay + i * 14L, () -> {
                    flash(lamp, true);
                    run.sound(spec.lamps().get(lamp), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 0.6f + 0.15f * lamp);
                });
                run.later(delay + i * 14L + 8, () -> flash(lamp, false));
            }
            run.later(delay + seq.length * 14L + 4, () -> showing = false);
        }

        @Override
        boolean click(Player p, Block b) {
            int k = indexOf(spec.levers(), b);
            if (k < 0) {
                return false;
            }
            if (solved) {
                return true;
            }
            if (showing) {
                run.say(p, "<gray>Watch the lamps first.");
                return true;
            }
            switch (state.input(k)) {
                case CORRECT -> {
                    flash(k, true);
                    run.later(6, () -> flash(k, false));
                    run.sound(spec.lamps().get(k), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 0.8f + 0.12f * state.progress());
                }
                case WRONG -> {
                    run.sound(spec.lamps().get(k), Sound.BLOCK_ANVIL_LAND, 0.6f, 0.8f);
                    run.say(p, "<red>Wrong lever! The pattern starts again.");
                    run.hurt(p, 1.5 + run.floorNumber());
                    play(24);
                }
                case SOLVED -> {
                    solved = true;
                    flash(k, true);
                    run.puzzleSolved(room, p);
                }
                default -> { }
            }
            return true;
        }
    }

    // ------------------------------------------------------------------ pressure plates

    static final class Plates extends PuzzleRuntime {
        private final PuzzleSpec.Plates spec;
        private final SequenceState state;
        private final Map<UUID, Long> debounce = new HashMap<>();
        private final List<UUID> labels = new ArrayList<>();

        Plates(DungeonRun run, RoomRt room, PuzzleSpec.Plates spec) {
            super(run, room);
            this.spec = spec;
            this.state = new SequenceState(spec.order());
        }

        @Override
        String instructions() {
            return "<gray>Step on the six plates from the <white>smallest</white> number to the <white>largest</white>."
                + " The numbers are written in different ways.";
        }

        @Override
        void onFirstEnter() {
            for (int i = 0; i < spec.plates().size(); i++) {
                Pos p = spec.plates().get(i);
                UUID id = run.spawnLabel(new Location(run.world(), p.x() + 0.5, p.y() + 1.6, p.z() + 0.5),
                    "<gold><bold>" + spec.labels().get(i));
                if (id != null) {
                    labels.add(id);
                }
            }
        }

        @Override
        boolean step(Player p, Block b) {
            int k = indexOf(spec.plates(), b);
            if (k < 0) {
                return false;
            }
            long now = System.currentTimeMillis();
            Long last = debounce.get(p.getUniqueId());
            if (last != null && now - last < 700 || solved) {
                return true;
            }
            debounce.put(p.getUniqueId(), now);
            switch (state.input(k)) {
                case CORRECT -> run.sound(spec.plates().get(k), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 0.7f + 0.15f * state.progress());
                case WRONG -> {
                    run.sound(spec.plates().get(k), Sound.BLOCK_ANVIL_LAND, 0.6f, 0.8f);
                    run.say(p, "<red>Wrong plate! Start again from the smallest number.");
                    run.hurt(p, 1.5 + run.floorNumber());
                }
                case SOLVED -> {
                    solved = true;
                    run.puzzleSolved(room, p);
                }
                default -> { }
            }
            return true;
        }
    }

    // ------------------------------------------------------------------ tic tac toe

    static final class Ttt extends PuzzleRuntime {
        private final PuzzleSpec.TicTacToe spec;
        private final TicTacToe game;
        private boolean resetting;

        Ttt(DungeonRun run, RoomRt room, PuzzleSpec.TicTacToe spec) {
            super(run, room);
            this.spec = spec;
            this.game = new TicTacToe(Math.min(0.85, 0.35 + 0.08 * run.floorNumber()));
        }

        @Override
        String instructions() {
            return "<gray>Beat the crypt at <white>tic-tac-toe</white>: right-click a white panel to mark it <green>green</green>."
                + " Three in a row wins.";
        }

        private void paint(int cell, String block) {
            run.setBlock(spec.panels().get(cell), block);
        }

        private void reset() {
            game.reset();
            for (int i = 0; i < 9; i++) {
                paint(i, "white_concrete");
            }
            resetting = false;
        }

        @Override
        boolean click(Player p, Block b) {
            int k = indexOf(spec.panels(), b);
            if (k < 0) {
                return false;
            }
            if (solved || resetting) {
                return true;
            }
            if (!game.play(k, TicTacToe.PLAYER)) {
                return true;
            }
            paint(k, "lime_concrete");
            run.sound(spec.panels().get(k), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1.4f);
            if (game.winner() == TicTacToe.PLAYER) {
                solved = true;
                run.puzzleSolved(room, p);
                return true;
            }
            int move = game.cryptMove(run.rng());
            if (move >= 0) {
                paint(move, "red_concrete");
                run.sound(spec.panels().get(move), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 0.5f);
            }
            if (game.winner() == TicTacToe.CRYPT || (game.full() && game.winner() == TicTacToe.EMPTY)) {
                run.say(p, game.winner() == TicTacToe.CRYPT ? "<red>The crypt wins this round. The board resets."
                    : "<yellow>A draw. The board resets.");
                resetting = true;
                run.later(30, this::reset);
            }
            return true;
        }
    }

    // ------------------------------------------------------------------ three weirdos

    static final class WeirdoPuzzle extends PuzzleRuntime {
        private static final String[] NAMES = {"Grim", "Mordo", "Vex"};
        private static final String[] COLORS = {"<red>", "<green>", "<blue>"};
        private final PuzzleSpec.Weirdo spec;
        private final boolean[] opened = new boolean[3];
        private final List<UUID> npcs = new ArrayList<>();

        WeirdoPuzzle(DungeonRun run, RoomRt room, PuzzleSpec.Weirdo spec) {
            super(run, room);
            this.spec = spec;
        }

        @Override
        String instructions() {
            return "<gray>Three weirdos guard three chests (<red>red</red>, <green>green</green>, <blue>blue</blue>)."
                + " Exactly one of them tells the truth. Talk to them (right-click), then open the chest with the treasure."
                + " A wrong chest is a trap.";
        }

        @Override
        void onFirstEnter() {
            for (int i = 0; i < 3; i++) {
                Pos p = spec.spots().get(i);
                UUID id = run.spawnNpc(new Location(run.world(), p.x() + 0.5, p.y(), p.z() + 0.5, 0f, 0f),
                    "weirdo:" + i, "<yellow>" + NAMES[i]);
                if (id != null) {
                    npcs.add(id);
                }
            }
        }

        @Override
        boolean clickEntity(Player p, Entity e) {
            String npc = run.keys().npcOf(e);
            if (npc == null || !npc.startsWith("weirdo:")) {
                return false;
            }
            int i;
            try {
                i = Integer.parseInt(npc.substring(7));
            } catch (NumberFormatException ex) {
                return true;
            }
            if (i < 0 || i > 2 || !npcs.contains(e.getUniqueId())) {
                return false;
            }
            run.say(p, "<yellow>" + NAMES[i] + "<gray>: <white>\"" + spec.statements().get(i) + "\"");
            return true;
        }

        @Override
        boolean click(Player p, Block b) {
            int k = indexOf(spec.chests(), b);
            if (k < 0) {
                return false;
            }
            if (solved || opened[k]) {
                return true;
            }
            opened[k] = true;
            if (k == spec.treasure()) {
                solved = true;
                run.setBlock(spec.chests().get(k), "air");
                run.puzzleSolved(room, p);
            } else {
                run.setBlock(spec.chests().get(k), "air");
                run.say(p, COLORS[k] + "The " + (k == 0 ? "red" : k == 1 ? "green" : "blue") + " chest <red>was a trap!");
                run.sound(spec.chests().get(k), Sound.ENTITY_WITHER_SPAWN, 0.4f, 1.6f);
                run.hurt(p, 3 + 1.5 * run.floorNumber());
            }
            return true;
        }
    }

    // ------------------------------------------------------------------ boulder path

    static final class BoulderPuzzle extends PuzzleRuntime {
        private final PuzzleSpec.Boulder spec;
        private final BoulderField field;

        BoulderPuzzle(DungeonRun run, RoomRt room, PuzzleSpec.Boulder spec) {
            super(run, room);
            this.spec = spec;
            this.field = spec.field();
        }

        @Override
        String instructions() {
            return "<gray>Push the <white>mossy boulder</white> onto the <gold>gold block</gold> by right-clicking it from the"
                + " side you want it to move away from. Stuck? Press the <white>button</white> to reset it.";
        }

        private Pos cell(int x, int z) {
            return new Pos(spec.origin().x() + x, spec.origin().y(), spec.origin().z() + z);
        }

        @Override
        boolean click(Player p, Block b) {
            if (at(spec.resetButton(), b)) {
                if (solved) {
                    return true;
                }
                run.setBlock(cell(field.boulderX(), field.boulderZ()), "air");
                field.reset();
                run.setBlock(cell(field.boulderX(), field.boulderZ()), "mossy_cobblestone");
                run.sound(spec.resetButton(), Sound.BLOCK_PISTON_EXTEND, 0.6f, 1.2f);
                run.say(p, "<gray>The boulder rolls back to its start.");
                return true;
            }
            Pos boulder = cell(field.boulderX(), field.boulderZ());
            if (!at(boulder, b)) {
                return false;
            }
            if (solved) {
                return true;
            }
            double dx = boulder.x() + 0.5 - p.getLocation().getX();
            double dz = boulder.z() + 0.5 - p.getLocation().getZ();
            if (Math.abs(dx) + Math.abs(dz) > 4.2) {
                run.say(p, "<gray>Come closer to push it.");
                return true;
            }
            int sx = 0;
            int sz = 0;
            if (Math.abs(dx) >= Math.abs(dz)) {
                sx = dx > 0 ? 1 : -1;
            } else {
                sz = dz > 0 ? 1 : -1;
            }
            Pos target = cell(field.boulderX() + sx, field.boulderZ() + sz);
            if (run.anyoneAt(target)) {
                run.say(p, "<gray>Something is in the way.");
                return true;
            }
            if (!field.push(sx, sz)) {
                run.sound(boulder, Sound.BLOCK_ANVIL_LAND, 0.4f, 0.6f);
                return true;
            }
            run.setBlock(boulder, "air");
            run.setBlock(target, "mossy_cobblestone");
            run.sound(target, Sound.BLOCK_PISTON_EXTEND, 0.7f, 0.8f);
            if (field.solved()) {
                solved = true;
                run.puzzleSolved(room, p);
            }
            return true;
        }
    }
}
