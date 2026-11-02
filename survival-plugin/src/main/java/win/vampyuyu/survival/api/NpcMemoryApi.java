package win.vampyuyu.survival.api;

import java.util.Optional;
import java.util.UUID;

/**
 * What the living NPCs (GDD 18) remember. Implemented by the {@code npc} feature. Facts are named by a dotted key
 * ({@code defeated.magma_warden}, {@code rescued}, {@code breath.survived}); recording is idempotent. The rules engine is
 * the only thing that turns facts into reactions: callers never get a decision from an AI.
 */
public interface NpcMemoryApi {
    /** Remember a fact about a player (kept at the highest value if it already exists). */
    void recordFact(UUID player, String key, int value);

    default void recordFact(UUID player, String key) {
        recordFact(player, key, 1);
    }

    /** Value of a fact, 0 when unknown. */
    int fact(UUID player, String key);

    /** Change the relation between an NPC and a player (clamped by settings). */
    void addRelation(UUID player, String npc, int delta);

    /** Relation, 0 when the NPC never met the player. */
    int relation(UUID player, String npc);

    /** The greeting this NPC gives this player now; counts as a visit. Empty when the feature is off. */
    Optional<String> greet(UUID player, String playerName, String npc);

    /** Price change in percent (negative = cheaper) this NPC applies to this player. */
    int priceDeltaPct(UUID player, String npc);

    /** Local state of an NPC (its next spot, whether it was rescued...). Empty string when unset. */
    String state(String npc, String key);

    void setState(String npc, String key, String value);
}
