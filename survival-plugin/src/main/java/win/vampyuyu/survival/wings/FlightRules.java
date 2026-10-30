package win.vampyuyu.survival.wings;

/** The decision "may this player fly right now", as a pure function so every rule is unit tested. */
public final class FlightRules {
    private FlightRules() {}

    /** Verdict of {@link #decide}. */
    public enum Verdict {
        OK(null), NOT_WEARING(WingsSettings.Reason.UNEQUIP), GAME_MODE(null), WORLD(WingsSettings.Reason.WORLD),
        ZONE(WingsSettings.Reason.ZONE), COMBAT(WingsSettings.Reason.COMBAT), EXHAUSTED(WingsSettings.Reason.ENERGY),
        DEAD(WingsSettings.Reason.DEATH);

        private final WingsSettings.Reason reason;

        Verdict(WingsSettings.Reason reason) {
            this.reason = reason;
        }

        /** The stop reason this verdict maps to (null when none applies). */
        public WingsSettings.Reason reason() {
            return reason;
        }
    }

    /** Everything the decision needs. */
    public record Input(boolean wearing, boolean dead, boolean gameModeOk, boolean worldAllowed, boolean zoneBlocked,
                        long combatMillisLeft, boolean exhausted) {}

    /** First failing rule wins, in order of how permanent the cause is. */
    public static Verdict decide(Input in) {
        if (in.dead()) {
            return Verdict.DEAD;
        }
        if (!in.wearing()) {
            return Verdict.NOT_WEARING;
        }
        if (!in.gameModeOk()) {
            return Verdict.GAME_MODE;
        }
        if (!in.worldAllowed()) {
            return Verdict.WORLD;
        }
        if (in.zoneBlocked()) {
            return Verdict.ZONE;
        }
        if (in.combatMillisLeft() > 0) {
            return Verdict.COMBAT;
        }
        if (in.exhausted()) {
            return Verdict.EXHAUSTED;
        }
        return Verdict.OK;
    }
}
