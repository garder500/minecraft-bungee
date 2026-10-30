package win.vampyuyu.survival.menu;

import static org.junit.Assert.*;

import java.util.Optional;
import java.util.UUID;
import org.junit.Test;
import win.vampyuyu.survival.menu.JoinRouter.Action;
import win.vampyuyu.survival.menu.JoinRouter.Context;
import win.vampyuyu.survival.menu.JoinRouter.NoKey;

public class HandoffAndRouterTest {
    private static final long NOW = 1_800_000_000_000L;
    private static final long MAX = 30_000;

    private static Optional<HandoffPayload> p(String raw) {
        return HandoffPayload.parse(raw);
    }

    private static Context ctx(String raw, boolean island, boolean dungeon, boolean inSb, NoKey noKey) {
        return new Context(p(raw), NOW, MAX, island, dungeon, inSb, noKey);
    }

    // ------------------------------------------------------------ payload

    @Test
    public void networkWriterFormatWithoutTimestampIsAccepted() {
        HandoffPayload payload = p("{\"dest\":\"skyblock\"}").orElseThrow();
        assertEquals(HandoffPayload.Dest.SKYBLOCK, payload.dest());
        assertFalse(payload.isStale(NOW, MAX));
    }

    @Test
    public void fullPayloadAndBareWordAndAliases() {
        HandoffPayload full = p("{\"dest\":\"Skyblock\",\"arg\":\"HUB\",\"ts\":" + NOW + "}").orElseThrow();
        assertEquals("hub", full.arg());
        assertEquals(NOW, full.timestamp());
        assertEquals(HandoffPayload.Dest.SKYBLOCK, p("skyblock").orElseThrow().dest());
        assertEquals(HandoffPayload.Dest.DUNGEON, p(" dungeon ").orElseThrow().dest());
        assertEquals(HandoffPayload.Dest.ISLAND, p("{\"dest\":\"home\"}").orElseThrow().dest());
        assertEquals(HandoffPayload.Dest.HUB, p("{\"dest\":\"hub\"}").orElseThrow().dest());
    }

    @Test
    public void garbageNeverThrowsAndMeansSkyblock() {
        assertTrue(p(null).isEmpty());
        assertTrue(p("   ").isEmpty());
        for (String junk : new String[] {"{", "{\"dest\":", "[1,2]", "{\"dest\":{\"a\":1}}", "{\"dest\":\"mars\"}",
                "{\"ts\":\"abc\",\"dest\":\"skyblock\"}", "\"quoted\"", "{}"}) {
            HandoffPayload payload = p(junk).orElseThrow();
            assertEquals(junk, HandoffPayload.Dest.SKYBLOCK, payload.dest());
        }
    }

    @Test
    public void staleOnlyWhenTimestampIsPresentAndOld() {
        assertTrue(p("{\"dest\":\"skyblock\",\"ts\":" + (NOW - 31_000) + "}").orElseThrow().isStale(NOW, MAX));
        assertFalse(p("{\"dest\":\"skyblock\",\"ts\":" + (NOW - 29_000) + "}").orElseThrow().isStale(NOW, MAX));
        assertFalse(p("{\"dest\":\"skyblock\",\"ts\":" + (NOW + 5_000) + "}").orElseThrow().isStale(NOW, MAX));
    }

    // ------------------------------------------------------------ router

    @Test
    public void noKeyStayModeDoesNotMoveThePlayer() {
        assertEquals(Action.STAY, JoinRouter.decide(ctx(null, true, false, false, NoKey.STAY)).action());
        assertEquals(Action.STAY, JoinRouter.decide(ctx(null, false, false, false, NoKey.STAY)).action());
    }

    @Test
    public void noKeyHubModeSendsToHubUnlessAlreadyInSkyblock() {
        assertEquals(Action.HUB, JoinRouter.decide(ctx(null, true, false, false, NoKey.HUB)).action());
        assertEquals(Action.HUB_CREATE_PROMPT, JoinRouter.decide(ctx(null, false, false, false, NoKey.HUB)).action());
        assertEquals(Action.STAY, JoinRouter.decide(ctx(null, true, false, true, NoKey.HUB)).action());
    }

    @Test
    public void staleKeyIsTreatedAsNoKey() {
        String stale = "{\"dest\":\"skyblock\",\"ts\":" + (NOW - 120_000) + "}";
        assertEquals(Action.STAY, JoinRouter.decide(ctx(stale, true, false, false, NoKey.STAY)).action());
    }

    @Test
    public void skyblockKeyGoesToIslandOrHubWithPrompt() {
        assertEquals(Action.ISLAND, JoinRouter.decide(ctx("{\"dest\":\"skyblock\"}", true, false, false, NoKey.STAY)).action());
        assertEquals(Action.HUB_CREATE_PROMPT,
            JoinRouter.decide(ctx("{\"dest\":\"skyblock\"}", false, false, false, NoKey.STAY)).action());
        assertEquals(Action.HUB,
            JoinRouter.decide(ctx("{\"dest\":\"skyblock\",\"arg\":\"hub\"}", true, false, false, NoKey.STAY)).action());
        assertEquals(Action.ISLAND, JoinRouter.decide(ctx("island", true, false, false, NoKey.STAY)).action());
        assertEquals(Action.HUB_CREATE_PROMPT, JoinRouter.decide(ctx("island", false, false, false, NoKey.STAY)).action());
    }

    @Test
    public void dungeonKeyNeverMovesAndPlayersInRunsAreNeverMoved() {
        assertEquals(Action.STAY, JoinRouter.decide(ctx("dungeon", true, false, false, NoKey.STAY)).action());
        assertEquals(Action.STAY, JoinRouter.decide(ctx("dungeon", true, true, true, NoKey.STAY)).action());
        assertEquals(Action.STAY, JoinRouter.decide(ctx("skyblock", true, true, true, NoKey.HUB)).action());
        assertEquals(Action.STAY, JoinRouter.decide(ctx(null, false, true, true, NoKey.HUB)).action());
    }

    // ------------------------------------------------------------ source contract

    @Test
    public void fakeSourceConsumesOnce() throws Exception {
        UUID id = UUID.randomUUID();
        java.util.Map<UUID, String> store = new java.util.concurrent.ConcurrentHashMap<>();
        store.put(id, "skyblock");
        HandoffSource source = store::remove;
        assertEquals("skyblock", source.take(id));
        assertNull(source.take(id));
    }
}
