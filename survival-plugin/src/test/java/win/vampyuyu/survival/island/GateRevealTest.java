package win.vampyuyu.survival.island;

import static org.junit.Assert.*;

import java.util.List;
import java.util.UUID;
import org.junit.Test;
import win.vampyuyu.survival.api.Realm;
import win.vampyuyu.survival.core.JsonStore;

/** The Gate reveal: which Gates are new for a player, the order, once only, and the words. Pure, no server. */
public class GateRevealTest {
    private static final UUID A = UUID.randomUUID();

    private static SkyGateState queenKilled() {
        SkyGateState state = new SkyGateState();
        // the order the kill handlers happen to call light in is not the order of the Gates
        for (Realm realm : List.of(Realm.ARCHIPELAGO, Realm.HEIGHTS, Realm.DEPTHS, Realm.CITADEL, Realm.MARSHES)) {
            state.light(A, realm);
        }
        return state;
    }

    @Test
    public void everyNewlyLitGateIsUnseenInRealmOrder() {
        SkyGateState state = queenKilled();
        assertEquals(List.of(Realm.HEIGHTS, Realm.CITADEL, Realm.MARSHES, Realm.ARCHIPELAGO, Realm.DEPTHS), state.unseen(A));
        assertTrue(state.revealed(A).isEmpty());
        assertTrue(state.unseen(UUID.randomUUID()).isEmpty());
    }

    @Test
    public void aGateIsRevealedOnceThenStaysLit() {
        SkyGateState state = queenKilled();
        assertTrue(state.markSeen(A, Realm.HEIGHTS));
        assertFalse("marked twice", state.markSeen(A, Realm.HEIGHTS));
        assertEquals(List.of(Realm.HEIGHTS), state.revealed(A));
        assertEquals(List.of(Realm.CITADEL, Realm.MARSHES, Realm.ARCHIPELAGO, Realm.DEPTHS), state.unseen(A));
        assertTrue(state.isLit(A, Realm.HEIGHTS));
        assertFalse("a dark Gate cannot be seen", state.markSeen(A, Realm.ASHES));
    }

    @Test
    public void lightingAGateAgainDoesNotReplayIt() {
        SkyGateState state = queenKilled();
        state.markSeen(A, Realm.HEIGHTS);
        assertFalse(state.light(A, Realm.HEIGHTS));
        assertFalse(state.unseen(A).contains(Realm.HEIGHTS));
    }

    @Test
    public void aGatePutOutAndLitAgainReplays() {
        SkyGateState state = queenKilled();
        state.markSeen(A, Realm.HEIGHTS);
        assertTrue(state.extinguish(A, Realm.HEIGHTS));
        assertTrue(state.light(A, Realm.HEIGHTS));
        assertTrue(state.unseen(A).contains(Realm.HEIGHTS));
    }

    @Test
    public void seenSurvivesSaveAndLoad() {
        SkyGateState state = queenKilled();
        state.markSeen(A, Realm.HEIGHTS);
        state.markSeen(A, Realm.CITADEL);
        SkyGateState back = SkyGateState.fromDoc(JsonStore.GSON.fromJson(JsonStore.GSON.toJson(state.toDoc()), SkyGateState.Doc.class));
        assertEquals(List.of(Realm.HEIGHTS, Realm.CITADEL), back.revealed(A));
        assertEquals(List.of(Realm.MARSHES, Realm.ARCHIPELAGO, Realm.DEPTHS), back.unseen(A));
    }

    @Test
    public void aFileFromBeforeTheRevealReplaysNothing() {
        SkyGateState.Doc old = JsonStore.GSON.fromJson("{\"version\":1,\"players\":{\"" + A + "\":[\"ashes\",\"grove\"]}}", SkyGateState.Doc.class);
        assertNull(old.seen);
        SkyGateState state = SkyGateState.fromDoc(old);
        assertTrue(state.unseen(A).isEmpty());
        assertEquals(List.of(Realm.ASHES, Realm.GROVE), state.revealed(A));
        state.light(A, Realm.HEIGHTS);
        assertEquals(List.of(Realm.HEIGHTS), state.unseen(A));
    }

    @Test
    public void pacerWaitsThenLightsOneGateEveryGap() {
        GateReveal.Pacer pacer = new GateReveal.Pacer();
        SkyGateState state = queenKilled();
        int start = 4;
        int gap = 2;
        StringBuilder order = new StringBuilder();
        long firstAt = -1;
        long previous = -1;
        for (long step = 0; step < 40; step++) {
            Realm next = pacer.poll(state.unseen(A), step, start, gap);
            if (next != null) {
                if (firstAt < 0) {
                    firstAt = step;
                } else {
                    assertEquals("one Gate every gap", gap, step - previous);
                }
                previous = step;
                order.append(next.id()).append(' ');
                assertTrue(state.markSeen(A, next));
            }
        }
        assertEquals("the first Gate waits for the arrival delay", start, firstAt);
        assertEquals("heights citadel marshes archipelago depths ", order.toString());
        assertEquals("the sequence is over: the count starts again", 0, pacer.fired());
        assertTrue("then nothing replays", state.unseen(A).isEmpty());
    }

    @Test
    public void leavingTheQuaiMakesTheNextVisitWaitAgain() {
        GateReveal.Pacer pacer = new GateReveal.Pacer();
        List<Realm> unseen = List.of(Realm.GROVE);
        for (long step = 0; step < 3; step++) {
            assertNull(pacer.poll(unseen, step, 4, 2));
        }
        pacer.leave();
        for (long step = 3; step < 7; step++) {
            assertNull("waits again after a visit cut short", pacer.poll(unseen, step, 4, 2));
        }
        assertEquals(Realm.GROVE, pacer.poll(unseen, 7, 4, 2));
    }

    @Test
    public void stepsRoundUpAndNeverReachZero() {
        assertEquals(2, GateReveal.steps(20, 10));
        assertEquals(3, GateReveal.steps(21, 10));
        assertEquals(1, GateReveal.steps(0, 10));
    }

    @Test
    public void oneGateIsNamedInTheSubtitle() {
        GateReveal.Announce a = GateReveal.announce(List.of(Realm.GROVE), "en");
        assertEquals("A Gate wakes", a.title());
        assertEquals("Whispering Grove", a.subtitle());
        assertTrue(a.chat().contains("Whispering Grove"));
    }

    @Test
    public void manyGatesGetACountAndTheNamesGoToChat() {
        List<Realm> five = List.of(Realm.HEIGHTS, Realm.CITADEL, Realm.MARSHES, Realm.ARCHIPELAGO, Realm.DEPTHS);
        GateReveal.Announce a = GateReveal.announce(five, "en");
        assertEquals("5 Gates wake", a.title());
        assertFalse("five names would not fit a subtitle", a.subtitle().contains("Heights"));
        for (Realm realm : five) {
            assertTrue(a.chat().contains(realm.title()));
        }
        GateReveal.Announce two = GateReveal.announce(List.of(Realm.ASHES, Realm.GROVE), "en");
        assertEquals("Cracked Ashes · Whispering Grove", two.subtitle());
    }

    @Test
    public void frenchAndGermanWordsExist() {
        assertEquals("Une Porte s'éveille", GateReveal.announce(List.of(Realm.GROVE), "fr").title());
        assertEquals("Ein Tor erwacht", GateReveal.announce(List.of(Realm.GROVE), "de").title());
        assertEquals("3 Portes s'éveillent",
            GateReveal.announce(List.of(Realm.GROVE, Realm.HEIGHTS, Realm.CITADEL), "fr").title());
    }
}
