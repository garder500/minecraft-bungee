package win.vampyuyu.survival.dungeon.party;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

/**
 * Parties of the dungeon feature (the "Party API" the rest of the feature uses). Pure state machine: it never
 * touches Bukkit, messages go through a {@link Notifier} and time through a clock, so it is fully testable.
 * <p>
 * Rules: one party per player; a party has 1 to {@code maxSize} members; the leader invites (or every member when
 * allowed); invites expire; when the last member leaves the party disappears; when the leader leaves the next
 * member leads; nobody can leave, be kicked or be disbanded while the party is in a run.
 */
public final class PartyService {
    /** Outcome of an action. {@code message} is shown to the actor either way. */
    public record Result(boolean ok, String message) {
        static Result ok(String m) { return new Result(true, m); }

        static Result fail(String m) { return new Result(false, m); }
    }

    /** Sends a plain text message to players (the feature maps it to chat). */
    public interface Notifier {
        void send(Collection<UUID> to, String text);
    }

    /** Result of a ready check. */
    public enum ReadyOutcome { ALL_READY, DECLINED, EXPIRED, CANCELLED }

    private record Invite(UUID party, UUID inviter, String inviterName, long expires) {}

    private static final class ReadyCheck {
        final Map<UUID, Boolean> answers = new LinkedHashMap<>();
        final long deadline;
        final Consumer<ReadyOutcome> callback;

        ReadyCheck(long deadline, Consumer<ReadyOutcome> callback) {
            this.deadline = deadline;
            this.callback = callback;
        }
    }

    private final Map<UUID, Party> byPlayer = new HashMap<>();
    private final Map<UUID, Party> byId = new LinkedHashMap<>();
    private final Map<UUID, List<Invite>> invites = new HashMap<>();
    private final Map<UUID, ReadyCheck> checks = new HashMap<>();
    private final Map<UUID, Long> warpCooldown = new HashMap<>();
    private final Map<UUID, Boolean> partyChat = new HashMap<>();
    private final Notifier notifier;
    private final LongSupplier clock;
    private int maxSize;
    private int inviteExpireMillis;
    private int readyCheckMillis;
    private int warpCooldownMillis;
    private boolean membersCanInvite;

    public PartyService(Notifier notifier, LongSupplier clock, int maxSize, int inviteExpireSeconds, int readyCheckSeconds,
            int warpCooldownSeconds, boolean membersCanInvite) {
        this.notifier = notifier;
        this.clock = clock;
        configure(maxSize, inviteExpireSeconds, readyCheckSeconds, warpCooldownSeconds, membersCanInvite);
    }

    public synchronized void configure(int maxSize, int inviteExpireSeconds, int readyCheckSeconds, int warpCooldownSeconds,
            boolean membersCanInvite) {
        this.maxSize = Math.max(1, Math.min(5, maxSize));
        this.inviteExpireMillis = inviteExpireSeconds * 1000;
        this.readyCheckMillis = readyCheckSeconds * 1000;
        this.warpCooldownMillis = warpCooldownSeconds * 1000;
        this.membersCanInvite = membersCanInvite;
    }

    public int maxSize() { return maxSize; }

    // ------------------------------------------------------------------ queries

    public synchronized Party partyOf(UUID player) { return byPlayer.get(player); }

    public synchronized Party byId(UUID partyId) { return byId.get(partyId); }

    public synchronized List<Party> all() { return new ArrayList<>(byId.values()); }

    public synchronized int count() { return byId.size(); }

    /** Pending, unexpired invites of a player: inviter names. */
    public synchronized List<String> pendingInvites(UUID target) {
        long now = clock.getAsLong();
        List<String> out = new ArrayList<>();
        for (Invite i : invites.getOrDefault(target, List.of())) {
            if (i.expires > now && byId.containsKey(i.party)) {
                out.add(i.inviterName);
            }
        }
        return out;
    }

    // ------------------------------------------------------------------ actions

    public synchronized Result create(UUID player, String name) {
        if (byPlayer.containsKey(player)) {
            return Result.fail("You are already in a party. Leave it first with /party leave.");
        }
        Party party = new Party(player, name);
        byPlayer.put(player, party);
        byId.put(party.id, party);
        return Result.ok("Party created. Invite friends with /party invite <player>.");
    }

    /** Creates a one-player party when the player has none (used by /dungeon start). */
    public synchronized Party ensure(UUID player, String name) {
        Party p = byPlayer.get(player);
        if (p == null) {
            create(player, name);
            p = byPlayer.get(player);
        }
        return p;
    }

    public synchronized Result invite(UUID by, String byName, UUID target, String targetName) {
        if (by.equals(target)) {
            return Result.fail("You cannot invite yourself.");
        }
        Party party = byPlayer.get(by);
        if (party == null) {
            create(by, byName);
            party = byPlayer.get(by);
        }
        if (!party.isLeader(by) && !membersCanInvite) {
            return Result.fail("Only the party leader can invite players.");
        }
        if (party.inRun()) {
            return Result.fail("Your party is in a dungeon run right now.");
        }
        if (party.has(target)) {
            return Result.fail(targetName + " is already in your party.");
        }
        if (byPlayer.containsKey(target)) {
            return Result.fail(targetName + " is already in another party.");
        }
        if (party.size() >= maxSize) {
            return Result.fail("Your party is full (" + maxSize + " players).");
        }
        long now = clock.getAsLong();
        List<Invite> list = invites.computeIfAbsent(target, k -> new ArrayList<>());
        list.removeIf(i -> i.expires <= now);
        for (Invite i : list) {
            if (i.party.equals(party.id)) {
                return Result.fail(targetName + " already has a pending invite from your party.");
            }
        }
        list.add(new Invite(party.id, by, byName, now + inviteExpireMillis));
        notifier.send(List.of(target), byName + " invited you to their dungeon party. Type /party accept "
            + byName + " within " + (inviteExpireMillis / 1000) + " seconds.");
        notifier.send(party.members(), byName + " invited " + targetName + " to the party.");
        return Result.ok("Invite sent to " + targetName + ".");
    }

    public synchronized Result accept(UUID player, String name, String inviterName) {
        if (byPlayer.containsKey(player)) {
            return Result.fail("You are already in a party. Leave it first with /party leave.");
        }
        long now = clock.getAsLong();
        List<Invite> list = invites.getOrDefault(player, List.of());
        Invite chosen = null;
        for (int i = list.size() - 1; i >= 0; i--) {
            Invite inv = list.get(i);
            if (inv.expires <= now || !byId.containsKey(inv.party)) {
                continue;
            }
            if (inviterName == null || inviterName.isBlank() || inv.inviterName.equalsIgnoreCase(inviterName)) {
                chosen = inv;
                break;
            }
        }
        if (chosen == null) {
            return Result.fail(inviterName == null || inviterName.isBlank()
                ? "You have no pending party invite." : "You have no pending invite from " + inviterName + ".");
        }
        Party party = byId.get(chosen.party);
        if (party.size() >= maxSize) {
            return Result.fail("That party is full.");
        }
        if (party.inRun()) {
            return Result.fail("That party is in a dungeon run right now.");
        }
        invites.remove(player);
        party.add(player, name);
        byPlayer.put(player, party);
        cancelCheck(party, ReadyOutcome.CANCELLED);
        notifier.send(party.members(), name + " joined the party. (" + party.size() + "/" + maxSize + ")");
        return Result.ok("You joined " + party.nameOf(party.leader()) + "'s party.");
    }

    public synchronized Result deny(UUID player, String inviterName) {
        List<Invite> list = invites.getOrDefault(player, new ArrayList<>());
        long now = clock.getAsLong();
        Invite found = null;
        for (Invite i : list) {
            if (i.expires > now && (inviterName == null || inviterName.isBlank() || i.inviterName.equalsIgnoreCase(inviterName))) {
                found = i;
                break;
            }
        }
        if (found == null) {
            return Result.fail("You have no pending party invite" + (inviterName == null || inviterName.isBlank() ? "." : " from " + inviterName + "."));
        }
        list.remove(found);
        Party party = byId.get(found.party);
        if (party != null) {
            notifier.send(List.of(found.inviter), "A player declined your party invite.");
        }
        return Result.ok("Invite declined.");
    }

    public synchronized Result leave(UUID player) {
        Party party = byPlayer.get(player);
        if (party == null) {
            return Result.fail("You are not in a party.");
        }
        if (party.inRun()) {
            return Result.fail("Your party is in a dungeon run. Use /dungeon leave first.");
        }
        removeMember(party, player, " left the party.");
        return Result.ok("You left the party.");
    }

    /** Removes a member without the in-run check (quit while idle, or run teardown). Safe to call for non-members. */
    public synchronized void forget(UUID player) {
        Party party = byPlayer.get(player);
        invites.remove(player);
        partyChat.remove(player);
        if (party != null) {
            removeMember(party, player, " left the party.");
        }
    }

    private void removeMember(Party party, UUID player, String suffix) {
        String name = party.nameOf(player);
        party.remove(player);
        byPlayer.remove(player);
        partyChat.remove(player);
        cancelCheck(party, ReadyOutcome.CANCELLED);
        if (party.size() == 0) {
            byId.remove(party.id);
            return;
        }
        if (party.isLeader(player)) {
            party.setLeader(party.members().get(0));
            notifier.send(party.members(), name + suffix + " " + party.nameOf(party.leader()) + " is the new party leader.");
        } else {
            notifier.send(party.members(), name + suffix);
        }
    }

    public synchronized Result kick(UUID by, UUID target) {
        Party party = byPlayer.get(by);
        if (party == null) {
            return Result.fail("You are not in a party.");
        }
        if (!party.isLeader(by)) {
            return Result.fail("Only the party leader can kick players.");
        }
        if (by.equals(target)) {
            return Result.fail("You cannot kick yourself. Use /party leave or /party disband.");
        }
        if (!party.has(target)) {
            return Result.fail("That player is not in your party.");
        }
        if (party.inRun()) {
            return Result.fail("Your party is in a dungeon run right now.");
        }
        String name = party.nameOf(target);
        party.remove(target);
        byPlayer.remove(target);
        partyChat.remove(target);
        cancelCheck(party, ReadyOutcome.CANCELLED);
        notifier.send(List.of(target), "You were removed from the party.");
        notifier.send(party.members(), name + " was removed from the party.");
        return Result.ok("Removed " + name + " from the party.");
    }

    public synchronized Result promote(UUID by, UUID target) {
        Party party = byPlayer.get(by);
        if (party == null) {
            return Result.fail("You are not in a party.");
        }
        if (!party.isLeader(by)) {
            return Result.fail("Only the party leader can promote players.");
        }
        if (!party.has(target) || by.equals(target)) {
            return Result.fail("Pick another member of your party.");
        }
        party.setLeader(target);
        notifier.send(party.members(), party.nameOf(target) + " is the new party leader.");
        return Result.ok("You passed the lead to " + party.nameOf(target) + ".");
    }

    public synchronized Result disband(UUID by) {
        Party party = byPlayer.get(by);
        if (party == null) {
            return Result.fail("You are not in a party.");
        }
        if (!party.isLeader(by)) {
            return Result.fail("Only the party leader can disband the party.");
        }
        if (party.inRun()) {
            return Result.fail("Your party is in a dungeon run right now.");
        }
        List<UUID> members = party.members();
        notifier.send(members, "The party was disbanded.");
        dissolve(party);
        return Result.ok("Party disbanded.");
    }

    /** Dissolves a party whatever its state (feature disable). */
    public synchronized void dissolve(Party party) {
        for (UUID m : party.members()) {
            byPlayer.remove(m);
            partyChat.remove(m);
        }
        cancelCheck(party, ReadyOutcome.CANCELLED);
        byId.remove(party.id);
    }

    public synchronized void clear() {
        byPlayer.clear();
        byId.clear();
        invites.clear();
        checks.clear();
        warpCooldown.clear();
        partyChat.clear();
    }

    // ------------------------------------------------------------------ chat, warp, run

    public synchronized boolean partyChat(UUID player) { return partyChat.getOrDefault(player, false); }

    /** Toggles the sticky party chat; returns the new state. */
    public synchronized boolean togglePartyChat(UUID player) {
        boolean now = !partyChat.getOrDefault(player, false);
        if (now) {
            partyChat.put(player, true);
        } else {
            partyChat.remove(player);
        }
        return now;
    }

    /** Sends a chat line to the party of the sender. Returns false when the sender has no party. */
    public synchronized boolean chat(UUID sender, String text) {
        Party party = byPlayer.get(sender);
        if (party == null) {
            return false;
        }
        notifier.send(party.members(), "[Party] " + party.nameOf(sender) + ": " + text);
        return true;
    }

    /** Leader asks to pull the members to them: returns the members to teleport (without the leader) or a failure. */
    public synchronized Result checkWarp(UUID leader) {
        Party party = byPlayer.get(leader);
        if (party == null) {
            return Result.fail("You are not in a party.");
        }
        if (!party.isLeader(leader)) {
            return Result.fail("Only the party leader can warp the party.");
        }
        if (party.inRun()) {
            return Result.fail("You cannot warp the party during a run.");
        }
        if (party.size() < 2) {
            return Result.fail("Nobody to warp: you are alone in the party.");
        }
        long now = clock.getAsLong();
        long until = warpCooldown.getOrDefault(leader, 0L);
        if (until > now) {
            return Result.fail("Party warp is on cooldown for " + ((until - now + 999) / 1000) + "s.");
        }
        warpCooldown.put(leader, now + warpCooldownMillis);
        return Result.ok("Warping the party to you...");
    }

    /** Marks the party as being in a run (or idle with null). */
    public synchronized void setRun(Party party, String runId) {
        party.setRun(runId);
    }

    public synchronized void rename(UUID player, String name) {
        Party p = byPlayer.get(player);
        if (p != null) {
            p.rename(player, name);
        }
    }

    // ------------------------------------------------------------------ ready check

    private void cancelCheck(Party party, ReadyOutcome outcome) {
        ReadyCheck check = checks.remove(party.id);
        if (check != null) {
            check.callback.accept(outcome);
        }
    }

    /** Leader starts a ready check; {@code callback} gets the outcome (once). */
    public synchronized Result startReadyCheck(UUID leader, Consumer<ReadyOutcome> callback) {
        Party party = byPlayer.get(leader);
        if (party == null) {
            return Result.fail("You are not in a party.");
        }
        if (!party.isLeader(leader)) {
            return Result.fail("Only the party leader can start a ready check.");
        }
        if (checks.containsKey(party.id)) {
            return Result.fail("A ready check is already running.");
        }
        ReadyCheck check = new ReadyCheck(clock.getAsLong() + readyCheckMillis, callback);
        for (UUID m : party.members()) {
            check.answers.put(m, m.equals(leader));
        }
        checks.put(party.id, check);
        if (party.size() == 1) {
            checks.remove(party.id);
            callback.accept(ReadyOutcome.ALL_READY);
            return Result.ok("Ready.");
        }
        List<UUID> others = new ArrayList<>(party.members());
        others.remove(leader);
        notifier.send(others, party.nameOf(leader) + " started a ready check. Type /party ready or /party notready within "
            + (readyCheckMillis / 1000) + " seconds.");
        return Result.ok("Ready check started.");
    }

    /** A member answers the ready check. */
    public synchronized Result answerReady(UUID player, boolean ready) {
        Party party = byPlayer.get(player);
        if (party == null) {
            return Result.fail("You are not in a party.");
        }
        ReadyCheck check = checks.get(party.id);
        if (check == null) {
            return Result.fail("There is no ready check running.");
        }
        check.answers.put(player, ready);
        notifier.send(party.members(), party.nameOf(player) + (ready ? " is ready." : " is NOT ready."));
        if (!ready) {
            checks.remove(party.id);
            check.callback.accept(ReadyOutcome.DECLINED);
        } else if (!check.answers.containsValue(false)) {
            checks.remove(party.id);
            notifier.send(party.members(), "Everyone is ready!");
            check.callback.accept(ReadyOutcome.ALL_READY);
        }
        return Result.ok(ready ? "You are ready." : "You are not ready.");
    }

    public synchronized boolean readyCheckRunning(UUID player) {
        Party party = byPlayer.get(player);
        return party != null && checks.containsKey(party.id);
    }

    /** Names that have not answered yet (empty without a ready check). */
    public synchronized List<String> waitingFor(UUID player) {
        Party party = byPlayer.get(player);
        List<String> out = new ArrayList<>();
        if (party == null) {
            return out;
        }
        ReadyCheck check = checks.get(party.id);
        if (check != null) {
            check.answers.forEach((id, ok) -> {
                if (!ok) {
                    out.add(party.nameOf(id));
                }
            });
        }
        return out;
    }

    // ------------------------------------------------------------------ time

    /** Expires invites and ready checks. Call about once a second. */
    public synchronized void tick() {
        long now = clock.getAsLong();
        invites.values().forEach(list -> list.removeIf(i -> i.expires <= now));
        invites.values().removeIf(List::isEmpty);
        warpCooldown.values().removeIf(t -> t <= now);
        List<UUID> expired = new ArrayList<>();
        for (Map.Entry<UUID, ReadyCheck> e : checks.entrySet()) {
            if (e.getValue().deadline <= now) {
                expired.add(e.getKey());
            }
        }
        for (UUID id : expired) {
            ReadyCheck check = checks.remove(id);
            Party party = byId.get(id);
            if (party != null) {
                notifier.send(party.members(), "The ready check timed out.");
            }
            check.callback.accept(ReadyOutcome.EXPIRED);
        }
    }
}
