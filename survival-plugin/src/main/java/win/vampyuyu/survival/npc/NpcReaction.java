package win.vampyuyu.survival.npc;

/** One row of {@code npc_reaction}. {@code npc} is an NPC id or {@code *}. */
public record NpcReaction(String id, String npc, Kind kind, String fact, int minValue, int minRep, int maxRep, int priority,
        int pricePct, String line) {
    public enum Kind { GREETING, PRICE }

    boolean appliesTo(String npcId) {
        return npc.equals("*") || npc.equals(npcId);
    }
}
