package win.vampyuyu.survival.dungeon.puzzle;

import java.util.Random;

/** 3x3 noughts and crosses against the crypt. The crypt is beatable: it sometimes misses a block. */
public final class TicTacToe {
    public static final int EMPTY = 0;
    public static final int PLAYER = 1;
    public static final int CRYPT = 2;
    private static final int[][] LINES = {{0, 1, 2}, {3, 4, 5}, {6, 7, 8}, {0, 3, 6}, {1, 4, 7}, {2, 5, 8}, {0, 4, 8}, {2, 4, 6}};

    private final int[] board = new int[9];
    /** Chance (0-1) that the crypt blocks an immediate player win. */
    private final double blockChance;

    public TicTacToe(double blockChance) {
        this.blockChance = blockChance;
    }

    public int cell(int i) { return board[i]; }

    public boolean play(int cell, int who) {
        if (cell < 0 || cell > 8 || board[cell] != EMPTY || winner() != EMPTY) {
            return false;
        }
        board[cell] = who;
        return true;
    }

    public int winner() {
        for (int[] l : LINES) {
            if (board[l[0]] != EMPTY && board[l[0]] == board[l[1]] && board[l[1]] == board[l[2]]) {
                return board[l[0]];
            }
        }
        return EMPTY;
    }

    public boolean full() {
        for (int c : board) {
            if (c == EMPTY) {
                return false;
            }
        }
        return true;
    }

    public boolean over() { return winner() != EMPTY || full(); }

    public void reset() { java.util.Arrays.fill(board, EMPTY); }

    private int completing(int who) {
        for (int[] l : LINES) {
            int count = 0;
            int empty = -1;
            for (int c : l) {
                if (board[c] == who) {
                    count++;
                } else if (board[c] == EMPTY) {
                    empty = c;
                }
            }
            if (count == 2 && empty >= 0) {
                return empty;
            }
        }
        return -1;
    }

    /** Picks the crypt's cell (win, block with {@code blockChance}, centre, random) and plays it; -1 when none. */
    public int cryptMove(Random rng) {
        if (over()) {
            return -1;
        }
        int move = completing(CRYPT);
        if (move < 0 && rng.nextDouble() < blockChance) {
            move = completing(PLAYER);
        }
        if (move < 0 && board[4] == EMPTY && rng.nextDouble() < 0.7) {
            move = 4;
        }
        if (move < 0) {
            int free = 0;
            for (int c : board) {
                if (c == EMPTY) {
                    free++;
                }
            }
            int pick = rng.nextInt(free);
            for (int i = 0; i < 9; i++) {
                if (board[i] == EMPTY && pick-- == 0) {
                    move = i;
                    break;
                }
            }
        }
        board[move] = CRYPT;
        return move;
    }
}
