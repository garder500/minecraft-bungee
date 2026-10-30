package win.vampyuyu.survival.boss.world;

import java.util.ArrayList;
import java.util.List;

/** Persisted world boss state ({@code boss/worldboss.json}). Plain Gson class. */
public final class WorldBossState {
    /** Epoch millis of the next event, 0 = not scheduled yet. */
    public long nextSpawnAt;
    public long lastSpawnAt;
    public String lastBoss = "";
    public List<Entry> history = new ArrayList<>();

    /** A finished event. */
    public static final class Entry {
        public long at;
        public String boss = "";
        public boolean killed;
        public List<Row> top = new ArrayList<>();
    }

    /** A leaderboard row. */
    public static final class Row {
        public String name = "";
        public double damage;

        public Row() {}

        public Row(String name, double damage) {
            this.name = name;
            this.damage = damage;
        }
    }
}
