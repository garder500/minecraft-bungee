package win.vampyuyu.survival.dungeon;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Before;
import org.junit.Test;
import win.vampyuyu.survival.dungeon.party.Party;
import win.vampyuyu.survival.dungeon.party.PartyService;

public class PartyServiceTest {
    private final AtomicLong now = new AtomicLong(1_000_000);
    private final List<String> sent = new ArrayList<>();
    private final List<UUID> sentTo = new ArrayList<>();
    private PartyService svc;
    private final UUID a = UUID.randomUUID();
    private final UUID b = UUID.randomUUID();
    private final UUID c = UUID.randomUUID();
    private final UUID d = UUID.randomUUID();

    @Before
    public void setUp() {
        svc = new PartyService((Collection<UUID> to, String text) -> {
            sent.add(text);
            sentTo.addAll(to);
        }, now::get, 3, 60, 30, 15, false);
    }

    private void form(UUID leader, String ln, UUID member, String mn) {
        assertTrue(svc.create(leader, ln).ok());
        assertTrue(svc.invite(leader, ln, member, mn).ok());
        assertTrue(svc.accept(member, mn, ln).ok());
    }

    @Test
    public void createInviteAccept() {
        assertTrue(svc.create(a, "Alice").ok());
        assertFalse("already in one", svc.create(a, "Alice").ok());
        assertTrue(svc.invite(a, "Alice", b, "Bob").ok());
        assertEquals(List.of("Alice"), svc.pendingInvites(b));
        assertTrue(sentTo.contains(b));
        assertTrue(svc.accept(b, "Bob", "alice").ok());
        Party p = svc.partyOf(b);
        assertSame(svc.partyOf(a), p);
        assertEquals(2, p.size());
        assertTrue(p.isLeader(a));
        assertTrue(svc.pendingInvites(b).isEmpty());
    }

    @Test
    public void inviteCreatesAPartyAndRulesAreEnforced() {
        assertTrue(svc.invite(a, "Alice", b, "Bob").ok());
        assertNotNull(svc.partyOf(a));
        assertFalse(svc.invite(a, "Alice", a, "Alice").ok());
        assertFalse("duplicate invite", svc.invite(a, "Alice", b, "Bob").ok());
        svc.accept(b, "Bob", null);
        assertFalse("already a member", svc.invite(a, "Alice", b, "Bob").ok());
        // members cannot invite by default
        assertFalse(svc.invite(b, "Bob", c, "Cara").ok());
        // a player in another party cannot be invited
        svc.create(d, "Dan");
        assertFalse(svc.invite(a, "Alice", d, "Dan").ok());
    }

    @Test
    public void partyHasASizeLimit() {
        form(a, "Alice", b, "Bob");
        assertTrue(svc.invite(a, "Alice", c, "Cara").ok());
        assertTrue(svc.accept(c, "Cara", "Alice").ok());
        assertEquals(3, svc.partyOf(a).size());
        assertFalse(svc.invite(a, "Alice", d, "Dan").ok());
    }

    @Test
    public void sizeLimitAppliesAtAcceptTimeToo() {
        svc.configure(2, 60, 30, 15, false);
        svc.create(a, "Alice");
        svc.invite(a, "Alice", b, "Bob");
        svc.invite(a, "Alice", c, "Cara");
        assertTrue(svc.accept(b, "Bob", null).ok());
        assertFalse(svc.accept(c, "Cara", null).ok());
        assertNull(svc.partyOf(c));
    }

    @Test
    public void invitesExpire() {
        svc.create(a, "Alice");
        svc.invite(a, "Alice", b, "Bob");
        now.addAndGet(61_000);
        assertFalse(svc.accept(b, "Bob", null).ok());
        assertTrue(svc.pendingInvites(b).isEmpty());
        svc.tick();
        // a new invite works again
        assertTrue(svc.invite(a, "Alice", b, "Bob").ok());
    }

    @Test
    public void denyRemovesTheInvite() {
        svc.create(a, "Alice");
        svc.invite(a, "Alice", b, "Bob");
        assertTrue(svc.deny(b, "Alice").ok());
        assertFalse(svc.accept(b, "Bob", null).ok());
        assertFalse(svc.deny(b, null).ok());
    }

    @Test
    public void leaderLeavingPassesTheLeadAndEmptyPartiesVanish() {
        form(a, "Alice", b, "Bob");
        assertTrue(svc.leave(a).ok());
        assertTrue(svc.partyOf(b).isLeader(b));
        assertNull(svc.partyOf(a));
        assertTrue(svc.leave(b).ok());
        assertNull(svc.partyOf(b));
        assertEquals("auto disband when empty", 0, svc.count());
        assertFalse(svc.leave(b).ok());
    }

    @Test
    public void kickPromoteDisbandNeedTheLeader() {
        form(a, "Alice", b, "Bob");
        assertFalse(svc.kick(b, a).ok());
        assertFalse(svc.promote(b, a).ok());
        assertFalse(svc.disband(b).ok());
        assertFalse("not yourself", svc.kick(a, a).ok());
        assertTrue(svc.promote(a, b).ok());
        assertTrue(svc.partyOf(a).isLeader(b));
        assertTrue(svc.kick(b, a).ok());
        assertNull(svc.partyOf(a));
        assertTrue(svc.disband(b).ok());
        assertNull(svc.partyOf(b));
        assertEquals(0, svc.count());
    }

    @Test
    public void nothingChangesWhileInARun() {
        form(a, "Alice", b, "Bob");
        Party p = svc.partyOf(a);
        svc.setRun(p, "7");
        assertTrue(p.inRun());
        assertFalse(svc.leave(b).ok());
        assertFalse(svc.kick(a, b).ok());
        assertFalse(svc.disband(a).ok());
        assertFalse(svc.invite(a, "Alice", c, "Cara").ok());
        assertFalse(svc.checkWarp(a).ok());
        svc.setRun(p, null);
        assertTrue(svc.leave(b).ok());
    }

    @Test
    public void forgetWorksEvenInARun() {
        form(a, "Alice", b, "Bob");
        svc.setRun(svc.partyOf(a), "7");
        svc.forget(b);
        assertNull(svc.partyOf(b));
        assertEquals(1, svc.partyOf(a).size());
        svc.forget(a);
        assertEquals(0, svc.count());
        svc.forget(UUID.randomUUID()); // unknown player: no effect
    }

    @Test
    public void partyChatGoesToEveryMember() {
        form(a, "Alice", b, "Bob");
        sent.clear();
        sentTo.clear();
        assertTrue(svc.chat(a, "hello"));
        assertEquals(List.of("[Party] Alice: hello"), sent);
        assertTrue(sentTo.containsAll(List.of(a, b)));
        assertFalse(svc.chat(c, "x"));
        assertTrue(svc.togglePartyChat(a));
        assertTrue(svc.partyChat(a));
        assertFalse(svc.togglePartyChat(a));
    }

    @Test
    public void warpChecksLeaderSizeAndCooldown() {
        svc.create(a, "Alice");
        assertFalse("alone", svc.checkWarp(a).ok());
        form(d, "Dan", b, "Bob");
        assertFalse("members cannot warp", svc.checkWarp(b).ok());
        assertTrue(svc.checkWarp(d).ok());
        assertFalse("cooldown", svc.checkWarp(d).ok());
        now.addAndGet(16_000);
        assertTrue(svc.checkWarp(d).ok());
    }

    @Test
    public void readyCheckAllReady() {
        form(a, "Alice", b, "Bob");
        AtomicReference<PartyService.ReadyOutcome> out = new AtomicReference<>();
        assertFalse(svc.startReadyCheck(b, out::set).ok());
        assertTrue(svc.startReadyCheck(a, out::set).ok());
        assertTrue(svc.readyCheckRunning(a));
        assertEquals(List.of("Bob"), svc.waitingFor(a));
        assertFalse("one at a time", svc.startReadyCheck(a, x -> { }).ok());
        assertNull(out.get());
        assertTrue(svc.answerReady(b, true).ok());
        assertEquals(PartyService.ReadyOutcome.ALL_READY, out.get());
        assertFalse(svc.readyCheckRunning(a));
    }

    @Test
    public void readyCheckDeclinedExpiredAndCancelled() {
        form(a, "Alice", b, "Bob");
        AtomicReference<PartyService.ReadyOutcome> out = new AtomicReference<>();
        svc.startReadyCheck(a, out::set);
        svc.answerReady(b, false);
        assertEquals(PartyService.ReadyOutcome.DECLINED, out.get());

        out.set(null);
        svc.startReadyCheck(a, out::set);
        now.addAndGet(31_000);
        svc.tick();
        assertEquals(PartyService.ReadyOutcome.EXPIRED, out.get());

        out.set(null);
        svc.startReadyCheck(a, out::set);
        assertTrue(svc.leave(b).ok());
        assertEquals(PartyService.ReadyOutcome.CANCELLED, out.get());
        assertFalse(svc.answerReady(a, true).ok());
    }

    @Test
    public void soloPartyReadyCheckPassesAtOnce() {
        svc.create(a, "Alice");
        AtomicReference<PartyService.ReadyOutcome> out = new AtomicReference<>();
        assertTrue(svc.startReadyCheck(a, out::set).ok());
        assertEquals(PartyService.ReadyOutcome.ALL_READY, out.get());
    }

    @Test
    public void ensureCreatesASoloParty() {
        Party p = svc.ensure(a, "Alice");
        assertEquals(1, p.size());
        assertSame(p, svc.ensure(a, "Alice"));
        assertEquals(1, svc.count());
    }

    @Test
    public void maxSizeIsClampedToFive() {
        svc.configure(99, 60, 30, 15, true);
        assertEquals(5, svc.maxSize());
        svc.configure(0, 60, 30, 15, true);
        assertEquals(1, svc.maxSize());
        svc.create(a, "Alice");
        assertFalse("a party of one is full", svc.invite(a, "Alice", b, "Bob").ok());
    }

    @Test
    public void membersCanInviteWhenAllowed() {
        svc.configure(5, 60, 30, 15, true);
        form(a, "Alice", b, "Bob");
        assertTrue(svc.invite(b, "Bob", c, "Cara").ok());
    }
}
