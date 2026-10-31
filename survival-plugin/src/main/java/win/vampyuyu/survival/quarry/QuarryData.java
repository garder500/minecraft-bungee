package win.vampyuyu.survival.quarry;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Profile section {@code quarry}: generator progress and Forge jobs of one player. Mutated on the main thread. */
public final class QuarryData {
    /** Generator tier of the player's island (1 = Stone, 2 = Vein...). */
    public int generatorTier = 1;
    /** Blocks since the last drop, per ore id (the pity counters). */
    public Map<String, Integer> counters = new HashMap<>();
    public long blocks;
    public long geodes;
    public List<Job> jobs = new ArrayList<>();
    /** The Void Lens was used: /island end works. */
    public boolean endUnlocked;
    /** Sword skins unlocked by the account (ids of {@link Skin}). */
    public List<String> skins = new ArrayList<>();

    /** Pebbles picked up on {@link #pebbleDay} (UTC epoch day), for the daily cap. */
    public long pebbleDay;
    public int pebbles;
    /** The player made a Fountain: pebbles stop. */
    public boolean fountainMade;

    /** A Forge job: finished when {@code now >= finishAt}. */
    public static final class Job {
        public String recipe;
        public long finishAt;

        public Job() {}

        public Job(String recipe, long finishAt) {
            this.recipe = recipe;
            this.finishAt = finishAt;
        }
    }
}
