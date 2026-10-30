package win.vampyuyu.survival.dungeon;

import static org.junit.Assert.*;

import java.util.Random;
import org.junit.Test;
import win.vampyuyu.survival.dungeon.puzzle.BoulderField;
import win.vampyuyu.survival.dungeon.puzzle.SequenceState;
import win.vampyuyu.survival.dungeon.puzzle.TicTacToe;
import win.vampyuyu.survival.dungeon.puzzle.Weirdos;

public class PuzzleLogicTest {
    @Test
    public void weirdosHaveExactlyOneConsistentWorld() {
        for (long seed = 0; seed < 300; seed++) {
            Weirdos w = Weirdos.generate(new Random(seed));
            assertEquals("seed " + seed, 1, w.consistentWorlds());
            assertTrue(w.treasure() >= 0 && w.treasure() <= 2);
            for (int i = 0; i < 3; i++) {
                assertFalse(w.statement(i).isBlank());
            }
        }
    }

    @Test
    public void weirdosStatementsAreStablePerSeed() {
        Weirdos a = Weirdos.generate(new Random(9));
        Weirdos b = Weirdos.generate(new Random(9));
        for (int i = 0; i < 3; i++) {
            assertEquals(a.statement(i), b.statement(i));
        }
        assertEquals(a.treasure(), b.treasure());
    }

    @Test
    public void boulderFieldsAreSolvableAndNotTrivial() {
        for (long seed = 0; seed < 60; seed++) {
            BoulderField f = BoulderField.generate(new Random(seed), 9, 9, 10, 4);
            int pushes = f.solve();
            assertTrue("seed " + seed + " pushes " + pushes, pushes >= 4);
            assertFalse(f.isWall(f.startX, f.startZ));
            assertFalse(f.isWall(f.targetX, f.targetZ));
            assertFalse(f.isWall(f.entryX, f.entryZ));
            assertFalse(f.solved());
        }
    }

    @Test
    public void boulderPushRulesAndReset() {
        BoulderField f = BoulderField.generate(new Random(3), 9, 9, 0, 3);
        int bx = f.boulderX();
        int bz = f.boulderZ();
        boolean moved = f.push(1, 0);
        if (bx + 1 < f.w && !f.isWall(bx + 1, bz)) {
            assertTrue(moved);
            assertEquals(bx + 1, f.boulderX());
            assertEquals(1, f.pushes());
        }
        assertFalse("diagonal pushes are refused", f.push(1, 1));
        f.reset();
        assertEquals(f.startX, f.boulderX());
        assertEquals(f.startZ, f.boulderZ());
        // push into the border is refused
        BoulderField g = BoulderField.generate(new Random(5), 9, 9, 0, 3);
        for (int i = 0; i < 20; i++) {
            g.push(-1, 0);
        }
        assertEquals(0, g.boulderX());
        assertFalse(g.push(-1, 0));
    }

    @Test
    public void solvedFieldStopsMoving() {
        BoulderField f = BoulderField.generate(new Random(11), 9, 9, 0, 1);
        // open field: walk the boulder to the target by hand
        int guard = 0;
        while (!f.solved() && guard++ < 50) {
            if (f.boulderX() < f.targetX) {
                f.push(1, 0);
            } else if (f.boulderX() > f.targetX) {
                f.push(-1, 0);
            } else if (f.boulderZ() < f.targetZ) {
                f.push(0, 1);
            } else {
                f.push(0, -1);
            }
        }
        assertTrue(f.solved());
        assertFalse(f.push(1, 0));
    }

    @Test
    public void sequenceStateMachine() {
        SequenceState s = new SequenceState(new int[] {2, 0, 3});
        assertEquals(SequenceState.Result.CORRECT, s.input(2));
        assertEquals(SequenceState.Result.WRONG, s.input(1));
        assertEquals(0, s.progress());
        assertEquals(1, s.mistakes());
        assertEquals(SequenceState.Result.CORRECT, s.input(2));
        assertEquals(SequenceState.Result.CORRECT, s.input(0));
        assertEquals(SequenceState.Result.SOLVED, s.input(3));
        assertTrue(s.solved());
        assertEquals(SequenceState.Result.IGNORED, s.input(3));
    }

    @Test
    public void randomSequencesNeverRepeatNeighbours() {
        for (long seed = 0; seed < 50; seed++) {
            int[] seq = SequenceState.randomSequence(new Random(seed), 6, 5);
            assertEquals(6, seq.length);
            for (int i = 1; i < seq.length; i++) {
                assertNotEquals(seq[i - 1], seq[i]);
            }
        }
    }

    @Test
    public void ticTacToeRules() {
        TicTacToe t = new TicTacToe(1.0);
        assertTrue(t.play(0, TicTacToe.PLAYER));
        assertFalse("cell taken", t.play(0, TicTacToe.CRYPT));
        assertFalse("out of range", t.play(9, TicTacToe.PLAYER));
        t.play(1, TicTacToe.PLAYER);
        t.play(2, TicTacToe.PLAYER);
        assertEquals(TicTacToe.PLAYER, t.winner());
        assertTrue(t.over());
        assertFalse("no move after a win", t.play(5, TicTacToe.CRYPT));
        assertEquals(-1, t.cryptMove(new Random(1)));
        t.reset();
        assertEquals(TicTacToe.EMPTY, t.winner());
        assertFalse(t.over());
    }

    @Test
    public void cryptWinsWhenItCanAndBlocksWhenItCan() {
        TicTacToe t = new TicTacToe(1.0);
        t.play(0, TicTacToe.CRYPT);
        t.play(1, TicTacToe.CRYPT);
        t.play(4, TicTacToe.PLAYER);
        t.play(8, TicTacToe.PLAYER);
        assertEquals(2, t.cryptMove(new Random(1)));
        assertEquals(TicTacToe.CRYPT, t.winner());

        TicTacToe b = new TicTacToe(1.0);
        b.play(0, TicTacToe.PLAYER);
        b.play(1, TicTacToe.PLAYER);
        assertEquals(2, b.cryptMove(new Random(4)));
    }

    @Test
    public void thePlayerCanBeatTheCrypt() {
        // with blockChance 0 the crypt never defends: the player wins the top row
        int wins = 0;
        for (long seed = 0; seed < 40; seed++) {
            TicTacToe t = new TicTacToe(0.0);
            Random rng = new Random(seed);
            for (int cell : new int[] {0, 1, 2}) {
                if (!t.play(cell, TicTacToe.PLAYER)) {
                    // the crypt took the cell earlier: pick any free one
                    for (int c = 0; c < 9; c++) {
                        if (t.play(c, TicTacToe.PLAYER)) {
                            break;
                        }
                    }
                }
                if (t.winner() == TicTacToe.EMPTY) {
                    t.cryptMove(rng);
                }
            }
            if (t.winner() == TicTacToe.PLAYER) {
                wins++;
            }
        }
        assertTrue("a naive crypt is beatable", wins > 5);
    }
}
