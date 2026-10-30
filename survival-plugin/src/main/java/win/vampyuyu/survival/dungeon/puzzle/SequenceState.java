package win.vampyuyu.survival.dungeon.puzzle;

/** "Press these in this order": shared by the lever and pressure plate puzzles. */
public final class SequenceState {
    /** What an input did. */
    public enum Result { CORRECT, WRONG, SOLVED, IGNORED }

    private final int[] sequence;
    private int progress;
    private boolean solved;
    private int mistakes;

    public SequenceState(int[] sequence) {
        if (sequence.length == 0) {
            throw new IllegalArgumentException("empty sequence");
        }
        this.sequence = sequence.clone();
    }

    public Result input(int element) {
        if (solved) {
            return Result.IGNORED;
        }
        if (sequence[progress] == element) {
            progress++;
            if (progress == sequence.length) {
                solved = true;
                return Result.SOLVED;
            }
            return Result.CORRECT;
        }
        progress = 0;
        mistakes++;
        return Result.WRONG;
    }

    public void reset() { progress = 0; }

    public int progress() { return progress; }

    public int length() { return sequence.length; }

    public boolean solved() { return solved; }

    public int mistakes() { return mistakes; }

    public int[] sequence() { return sequence.clone(); }

    /** A random sequence of {@code length} elements out of {@code elements}, no element twice in a row. */
    public static int[] randomSequence(java.util.Random rng, int length, int elements) {
        int[] seq = new int[length];
        int prev = -1;
        for (int i = 0; i < length; i++) {
            int v;
            do {
                v = rng.nextInt(elements);
            } while (v == prev && elements > 1);
            seq[i] = v;
            prev = v;
        }
        return seq;
    }
}
