package win.vampyuyu.survival.dungeon.puzzle;

import java.util.Random;

/**
 * Three weirdos, three chests, one treasure. Exactly one weirdo tells the truth. The statements are generated until
 * exactly one (treasure chest, truthful weirdo) pair is consistent, so the riddle always has one answer.
 */
public final class Weirdos {
    public static final String[] NAMES = {"Grim", "Mordo", "Vex"};
    private static final String[] CHEST_NAMES = {"red", "green", "blue"};

    /** Statement kinds. */
    public static final int AT = 0;
    public static final int NOT_AT = 1;
    public static final int TRUTHFUL = 2;
    public static final int LYING = 3;

    private final int[] kind = new int[3];
    private final int[] arg = new int[3];
    private int treasure;

    private Weirdos() {}

    public int treasure() { return treasure; }

    public String statement(int weirdo) {
        return switch (kind[weirdo]) {
            case AT -> "The treasure is in the " + CHEST_NAMES[arg[weirdo]] + " chest.";
            case NOT_AT -> "The treasure is not in the " + CHEST_NAMES[arg[weirdo]] + " chest.";
            case TRUTHFUL -> NAMES[arg[weirdo]] + " is telling the truth.";
            default -> NAMES[arg[weirdo]] + " is lying.";
        };
    }

    private boolean holds(int weirdo, int chest, int truthful) {
        return switch (kind[weirdo]) {
            case AT -> chest == arg[weirdo];
            case NOT_AT -> chest != arg[weirdo];
            case TRUTHFUL -> arg[weirdo] == truthful;
            default -> arg[weirdo] != truthful;
        };
    }

    /** Number of (chest, truthful weirdo) worlds in which every statement has the truth value its speaker claims. */
    public int consistentWorlds() {
        int count = 0;
        for (int chest = 0; chest < 3; chest++) {
            for (int t = 0; t < 3; t++) {
                boolean ok = true;
                for (int w = 0; w < 3 && ok; w++) {
                    ok = holds(w, chest, t) == (w == t);
                }
                if (ok) {
                    count++;
                }
            }
        }
        return count;
    }

    private int solve() {
        for (int chest = 0; chest < 3; chest++) {
            for (int t = 0; t < 3; t++) {
                boolean ok = true;
                for (int w = 0; w < 3 && ok; w++) {
                    ok = holds(w, chest, t) == (w == t);
                }
                if (ok) {
                    return chest;
                }
            }
        }
        return -1;
    }

    public static Weirdos generate(Random rng) {
        for (int attempt = 0; attempt < 20000; attempt++) {
            Weirdos w = new Weirdos();
            for (int i = 0; i < 3; i++) {
                w.kind[i] = rng.nextInt(4);
                if (w.kind[i] >= TRUTHFUL) {
                    int other;
                    do {
                        other = rng.nextInt(3);
                    } while (other == i);
                    w.arg[i] = other;
                } else {
                    w.arg[i] = rng.nextInt(3);
                }
            }
            if (w.consistentWorlds() == 1) {
                w.treasure = w.solve();
                return w;
            }
        }
        // cannot happen in practice; a fixed valid riddle keeps the room playable
        Weirdos w = new Weirdos();
        w.kind[0] = AT;
        w.arg[0] = 0;
        w.kind[1] = LYING;
        w.arg[1] = 0;
        w.kind[2] = NOT_AT;
        w.arg[2] = 0;
        w.treasure = w.consistentWorlds() == 1 ? w.solve() : 0;
        return w;
    }
}
