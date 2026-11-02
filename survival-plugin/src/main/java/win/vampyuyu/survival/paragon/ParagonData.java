package win.vampyuyu.survival.paragon;

/** Profile section {@code "paragon"}. Plain Gson class. */
public final class ParagonData {
    /** True once the player survived the Astral Interior (or an admin said so). */
    public boolean astralCleared;
    /** Paragon points earned in all. */
    public int points;
    /** Chosen title, null = the highest one reached. */
    public String title;
    /** Chosen aura id, null = the highest one reached. */
    public String aura;
    /** False hides the aura. */
    public boolean showAura = true;
}
