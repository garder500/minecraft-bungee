package win.vampyuyu.survival.hellgao;

import java.util.LinkedHashSet;
import java.util.Set;

/** Profile section {@code "hellgao"}. Only prestige: titles, an aura, lore. Nothing here is a stat (GDD 9.2, 19.4). */
public final class HellGaoData {
    public int clears;
    /** Highest Ascension rank cleared, 0 = only the plain fight. */
    public int bestAscension;
    /** The Ascension rank asked for the next fights. */
    public int selectedAscension;
    public Set<String> titles = new LinkedHashSet<>();
    /** Shown title id, empty = none. */
    public String shownTitle = "";
    public Set<String> lore = new LinkedHashSet<>();
    public boolean aura;
}
