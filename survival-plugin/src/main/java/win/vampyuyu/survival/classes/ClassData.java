package win.vampyuyu.survival.classes;

/** The "classes" section of a player profile. */
public final class ClassData {
    /** Name of a {@link win.vampyuyu.survival.api.PlayerClass}, null until chosen. */
    public String vocation;
    /** NOVICE, ADEPT or MASTER (Paragon comes with its own feature). Promoted by the promotion feature. */
    public String rank = "NOVICE";
    /** Id of the Adept specialization (a {@code promotion_spec} row), null for a Novice. */
    public String spec;
}
