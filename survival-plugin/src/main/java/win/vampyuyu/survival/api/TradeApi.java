package win.vampyuyu.survival.api;

import java.util.UUID;

/**
 * The Artisan and Warden trades (GDD 11.3). Every multiplier is neutral (1.0) for a player without the trade: nothing
 * in the game asks for a trade (rule 11.4), a trade only makes some things faster.
 */
public interface TradeApi {
    /** Level 1 to 5 of a trade the player practises, 0 when the player does not have it. */
    int level(UUID player, Trade trade);

    /** Multiplier of a Forge job duration (Artisan: 0.85). */
    double forgeDurationFactor(UUID player);

    /** Multiplier of the Flux a Miner draws (Artisan: 0.90). */
    double minerEnergyFactor(UUID player);

    /** Multiplier of the yield of a Ranch or an Alembic (Warden: 1.20). The two stations call it when they exist. */
    double yieldFactor(UUID player);

    /** Adds experience to a trade; does nothing when the player does not practise it (no farming for a trade you lack). */
    void addXp(UUID player, Trade trade, int amount);
}
