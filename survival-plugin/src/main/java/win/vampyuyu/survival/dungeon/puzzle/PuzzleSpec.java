package win.vampyuyu.survival.dungeon.puzzle;

import java.util.List;
import win.vampyuyu.survival.dungeon.gen.Pos;

/** World positions of one built puzzle, handed to its runtime. */
public sealed interface PuzzleSpec {
    /** Where the reward chest appears when solved. */
    Pos chest();

    /** Levers on the wall, lamps above them, and the sequence to replay (indexes into levers). */
    record Levers(List<Pos> levers, List<Pos> lamps, int[] sequence, Pos chest) implements PuzzleSpec {}

    /** Plates with a label each; {@code order} is the plate indexes in the order to step on them. */
    record Plates(List<Pos> plates, List<String> labels, int[] order, Pos chest) implements PuzzleSpec {}

    /** Nine panels (row major), the crypt marks in red, the player in lime. */
    record TicTacToe(List<Pos> panels, Pos chest) implements PuzzleSpec {}

    /** Weirdo standing spots, chest blocks, their statements and the treasure chest index. */
    record Weirdo(List<Pos> spots, List<Pos> chests, List<String> statements, int treasure, Pos chest)
            implements PuzzleSpec {}

    /** Boulder field: world position of cell (0,0), the field model, the reset button. */
    record Boulder(Pos origin, BoulderField field, Pos resetButton, Pos chest) implements PuzzleSpec {}
}
