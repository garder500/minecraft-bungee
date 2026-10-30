package win.vampyuyu.survival.dungeon;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import win.vampyuyu.survival.core.JsonStore;

/** Best runs per floor, kept in {@code dungeon/leaderboard.json} (updated at the end of each cleared run). */
public final class Leaderboard {
    public static final String FILE = "dungeon/leaderboard.json";
    public static final int KEEP = 10;

    /** One run of the board. */
    public static final class Entry {
        public long millis;
        public int score;
        public String grade = "";
        public List<String> names = new ArrayList<>();
        public long date;
    }

    /** File content. */
    public static final class Data {
        public Map<String, List<Entry>> floors = new LinkedHashMap<>();
    }

    private final JsonStore store;
    private Data data;

    public Leaderboard(JsonStore store) {
        this.store = store;
    }

    private synchronized Data data() {
        if (data == null) {
            data = store.read(FILE, Data.class, Data::new);
            if (data.floors == null) {
                data.floors = new LinkedHashMap<>();
            }
        }
        return data;
    }

    /** Adds a cleared run; returns the 1-based rank when it made the board, else 0. Writes the file (call off the main thread). */
    public synchronized int record(int floor, long millis, int score, Grade grade, List<String> names) {
        Data d = data();
        List<Entry> list = d.floors.computeIfAbsent(Integer.toString(floor), k -> new ArrayList<>());
        Entry e = new Entry();
        e.millis = millis;
        e.score = score;
        e.grade = grade.label();
        e.names = new ArrayList<>(names);
        e.date = System.currentTimeMillis();
        list.add(e);
        list.sort((a, b) -> a.millis != b.millis ? Long.compare(a.millis, b.millis) : Integer.compare(b.score, a.score));
        int rank = list.indexOf(e) + 1;
        while (list.size() > KEEP) {
            list.remove(list.size() - 1);
        }
        try {
            store.write(FILE, d);
        } catch (java.io.IOException | RuntimeException ex) {
            // keep the in-memory board; the next record retries the write
        }
        return rank > KEEP ? 0 : rank;
    }

    /** Snapshot of the board of a floor, best first. */
    public synchronized List<Entry> top(int floor) {
        List<Entry> list = data().floors.get(Integer.toString(floor));
        return list == null ? List.of() : List.copyOf(list);
    }
}
