package win.vampyuyu.survival.dungeon;

/**
 * Score of a finished run, 0 to 325: speed (100) + exploration (100) + skill (100) + bonus (25).
 * Pure maths so it can be tested and tuned from {@code dungeon.yml}.
 */
public final class ScoreCalculator {
    private ScoreCalculator() {}

    /** Everything the score depends on. */
    public record Inputs(int floor, boolean bossKilled, double seconds, int parSeconds, int roomsTotal, int roomsDone,
            int fightRooms, int fightRoomsCleared, int puzzles, int puzzlesSolved, int mobsSpawned, int mobsKilled,
            int secretsTotal, int secretsFound, int secretsNeededPercent, int deaths, int miniBossesKilled) {}

    /** The breakdown shown to the party. */
    public record Score(double speed, double exploration, double skill, double bonus, int total, Grade grade,
            boolean cleared) {}

    public static Score compute(Inputs in, DungeonConfig cfg) {
        // speed
        double speed = in.seconds() <= in.parSeconds() ? 100
            : Math.max(0, 100 - (in.seconds() - in.parSeconds()) / cfg.speedDecaySeconds);

        // exploration: rooms visited and secrets found against the needed share
        double rooms = in.roomsTotal() <= 0 ? 1 : Math.min(1, (double) in.roomsDone() / in.roomsTotal());
        double secretsNeed = Math.max(1, Math.ceil(in.secretsTotal() * in.secretsNeededPercent() / 100.0));
        double secrets = in.secretsTotal() <= 0 ? 1 : Math.min(1, in.secretsFound() / secretsNeed);
        double exploration = rooms * 60 + secrets * 40;

        // skill: fights, puzzles, kills, minus deaths
        double fights = in.fightRooms() <= 0 ? 1 : Math.min(1, (double) in.fightRoomsCleared() / in.fightRooms());
        double puzzles = in.puzzles() <= 0 ? 1 : Math.min(1, (double) in.puzzlesSolved() / in.puzzles());
        double kills = in.mobsSpawned() <= 0 ? 1 : Math.min(1, (double) in.mobsKilled() / in.mobsSpawned());
        double skill = Math.max(0, Math.min(100, fights * 40 + puzzles * 30 + kills * 30 - in.deaths() * cfg.deathPenalty));

        // bonus
        double bonus = 0;
        if (in.deaths() == 0) {
            bonus += 10;
        }
        bonus += Math.min(9, in.miniBossesKilled() * 3);
        if (in.puzzles() > 0 && in.puzzlesSolved() >= in.puzzles()) {
            bonus += 3;
        }
        if (in.secretsTotal() > 0 && in.secretsFound() >= in.secretsTotal()) {
            bonus += 3;
        }
        bonus = Math.min(25, bonus);

        int total = (int) Math.round(speed + exploration + skill + bonus);
        return new Score(speed, exploration, skill, bonus, total, gradeFor(total, cfg), in.bossKilled());
    }

    public static Grade gradeFor(int total, DungeonConfig cfg) {
        Grade best = Grade.D;
        for (Grade g : Grade.values()) {
            if (total >= cfg.thresholds.getOrDefault(g, 0)) {
                best = g;
            }
        }
        return best;
    }
}
