package win.vampyuyu.survival.promotion;

/** What a quest step counts. The target of a step is {@code any} or an id (a boss for BOSS_*, a Realm for REALM). */
public enum StepKind {
    /** Hostile creatures killed. */
    KILL,
    /** Items crafted. */
    CRAFT,
    /** Uses of the class active. */
    CAST,
    /** Hostile creatures killed with another player within {@link PromotionRules#COOP_RADIUS} blocks. */
    COOP,
    /** Entries into a Realm world. */
    REALM,
    /** Bosses defeated as the only damage dealer. */
    BOSS_SOLO,
    /** Bosses defeated with other players. */
    BOSS_GROUP,
    /** Bosses defeated, solo or not. */
    BOSS_ANY,
    /** Bastions cleared. */
    BASTION;

    public static StepKind parse(String name) {
        for (StepKind k : values()) {
            if (k.name().equalsIgnoreCase(name == null ? "" : name.trim())) {
                return k;
            }
        }
        return null;
    }
}
