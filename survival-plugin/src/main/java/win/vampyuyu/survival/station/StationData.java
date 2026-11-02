package win.vampyuyu.survival.station;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Profile section {@code "stations"}: Alembic queue, Ranch clock and today's contracts. Plain Gson class. */
public final class StationData {
    /** One Alembic brew. {@code endAt} is 0 while it waits for a free slot. */
    public static final class Job {
        public String recipe;
        public long endAt;

        public Job() {}

        public Job(String recipe) {
            this.recipe = recipe;
        }
    }

    /** The brews in the queue, running first. */
    public List<Job> jobs = new ArrayList<>();
    /** Finished brews waiting to be collected (recipe ids). */
    public List<String> ready = new ArrayList<>();
    /** Millis of the last Ranch collection, 0 until the player puts a Ranch down. */
    public long ranchSince;
    /** UTC day ({@code yyyy-MM-dd}) of the contracts below. */
    public String contractDay = "";
    /** The contracts offered today. */
    public List<String> offered = new ArrayList<>();
    /** Kills and boss kills counted today, by contract id. */
    public Map<String, Integer> progress = new HashMap<>();
    /** Contracts turned in today. */
    public List<String> done = new ArrayList<>();
    /** Coins the contracts paid today. */
    public int coinsToday;
}
