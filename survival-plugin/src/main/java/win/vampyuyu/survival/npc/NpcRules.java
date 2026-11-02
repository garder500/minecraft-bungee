package win.vampyuyu.survival.npc;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The deterministic rules of the living NPCs (GDD 18): the same facts and the same relation always give the same greeting
 * and the same price. No clock, no random, no Bukkit: unit tested alone.
 */
public final class NpcRules {
    private final List<NpcReaction> reactions;
    private final int minPricePct;
    private final int maxPricePct;

    public NpcRules(List<NpcReaction> reactions, int minPricePct, int maxPricePct) {
        this.reactions = List.copyOf(reactions);
        this.minPricePct = Math.min(minPricePct, maxPricePct);
        this.maxPricePct = Math.max(minPricePct, maxPricePct);
    }

    /** Whether the reaction's conditions hold for these facts and this relation. */
    public static boolean matches(NpcReaction r, String npc, Map<String, Integer> facts, int rep) {
        if (!r.appliesTo(npc) || rep < r.minRep() || rep > r.maxRep()) {
            return false;
        }
        return r.fact().isEmpty() || facts.getOrDefault(r.fact(), 0) >= r.minValue();
    }

    /** Highest priority first, ties by id; an NPC-specific reaction beats a generic one of the same priority. */
    public Optional<String> greeting(String npc, String playerName, Map<String, Integer> facts, int rep) {
        return reactions.stream()
            .filter(r -> r.kind() == NpcReaction.Kind.GREETING && !r.line().isEmpty() && matches(r, npc, facts, rep))
            .min(Comparator.comparingInt(NpcReaction::priority).reversed()
                .thenComparing((NpcReaction r) -> r.npc().equals("*") ? 1 : 0)
                .thenComparing(NpcReaction::id))
            .map(r -> r.line().replace("{player}", playerName));
    }

    /** Sum of the matching PRICE reactions, clamped to the settings window. */
    public int priceDeltaPct(String npc, Map<String, Integer> facts, int rep) {
        int sum = 0;
        for (NpcReaction r : reactions) {
            if (r.kind() == NpcReaction.Kind.PRICE && matches(r, npc, facts, rep)) {
                sum += r.pricePct();
            }
        }
        return Math.max(minPricePct, Math.min(maxPricePct, sum));
    }

    /** Price in cents after the percent (rounded down, never below 1 for a positive price). */
    public static long apply(long cents, int pct) {
        if (cents <= 0) {
            return cents;
        }
        return Math.max(1, cents * (100 + pct) / 100);
    }
}
