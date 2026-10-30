package win.vampyuyu.survival.boss;

import static org.junit.Assert.*;

import java.util.List;
import org.junit.Test;
import win.vampyuyu.survival.boss.world.WorldBossState;
import win.vampyuyu.survival.core.JsonStore;

/** What the feature stores must survive Gson, and old documents must still load. */
public class PersistenceRoundTripTest {
    @Test
    public void bossDataRoundTrip() {
        BossData data = new BossData();
        data.slayerXp.put("zombie", 1234L);
        data.slayerKills.put("zombie:3", 2);
        data.kills.put("voidwyrm", 1);
        data.dry.put("voidwyrm|void_fang", 7);
        data.seenLevel.put("zombie", 3);
        data.quest = new BossData.Quest("zombie", 4, 2400, 50000, 99L);
        data.quest.progress = 12.5;
        data.quest.state = BossData.Quest.READY;
        data.pending.add("QUJD");
        data.pendingWings.add("boss:voidwyrm");
        data.pendingXp.put("boss:voidwyrm", 1500.0);
        data.pendingKills.add("voidwyrm|123");
        data.pendingDry.add("+voidwyrm|void_fang");
        data.recentKills.put("voidwyrm", new java.util.ArrayList<>(java.util.List.of(5L, 6L)));
        BossData back = JsonStore.GSON.fromJson(JsonStore.GSON.toJson(data), BossData.class);
        assertEquals(1234L, back.slayerXp.get("zombie").longValue());
        assertEquals(2, (int) back.slayerKills.get("zombie:3"));
        assertEquals(7, (int) back.dry.get("voidwyrm|void_fang"));
        assertEquals(List.of("boss:voidwyrm"), back.pendingWings);
        assertEquals(1500.0, back.pendingXp.get("boss:voidwyrm"), 0);
        assertEquals(List.of("voidwyrm|123"), back.pendingKills);
        assertEquals(List.of("+voidwyrm|void_fang"), back.pendingDry);
        assertEquals(List.of(5L, 6L), back.recentKills.get("voidwyrm"));
        assertEquals("zombie", back.quest.slayer);
        assertEquals(4, back.quest.tier);
        assertEquals(12.5, back.quest.progress, 0);
        assertEquals(BossData.Quest.READY, back.quest.state);
        assertEquals(50000, back.quest.paid, 0);
        assertEquals(java.util.List.of("QUJD"), back.pending);
    }

    @Test
    public void oldOrEmptyDocumentsLoadWithDefaults() {
        BossData empty = JsonStore.GSON.fromJson("{}", BossData.class);
        assertNotNull(empty.slayerXp);
        assertNotNull(empty.pending);
        assertNull(empty.quest);
        BossData partial = JsonStore.GSON.fromJson("{\"slayerXp\":{\"wolf\":5}}", BossData.class);
        assertEquals(5L, partial.slayerXp.get("wolf").longValue());
        assertNotNull(partial.dry);
    }

    @Test
    public void worldBossStateRoundTrip() {
        WorldBossState state = new WorldBossState();
        state.nextSpawnAt = 123456789L;
        state.lastBoss = "storm_roc";
        WorldBossState.Entry entry = new WorldBossState.Entry();
        entry.at = 5;
        entry.boss = "storm_roc";
        entry.killed = true;
        entry.top.add(new WorldBossState.Row("Alex", 1.5e6));
        state.history.add(entry);
        WorldBossState back = JsonStore.GSON.fromJson(JsonStore.GSON.toJson(state), WorldBossState.class);
        assertEquals(123456789L, back.nextSpawnAt);
        assertEquals("storm_roc", back.lastBoss);
        assertEquals(1, back.history.size());
        assertTrue(back.history.get(0).killed);
        assertEquals("Alex", back.history.get(0).top.get(0).name);
        assertEquals(1.5e6, back.history.get(0).top.get(0).damage, 0);
    }
}
