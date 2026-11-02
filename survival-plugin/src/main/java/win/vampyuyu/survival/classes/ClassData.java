package win.vampyuyu.survival.classes;

/** The "classes" section of a player profile. */
public final class ClassData {
    /** Name of a {@link win.vampyuyu.survival.api.PlayerClass}, null until chosen. */
    public String vocation;
    /** NOVICE for now; Adept, Master and Paragon come with the class-change quests. */
    public String rank = "NOVICE";
}
