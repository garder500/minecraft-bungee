package win.vampyuyu.survival.boss;

import java.util.Optional;
import java.util.UUID;
import java.util.function.LongSupplier;
import win.vampyuyu.survival.core.Cooldowns;
import win.vampyuyu.survival.core.Msg;

/** Per player and global summon cooldowns. Thread safe. */
public final class SummonGate {
    private final Cooldowns perPlayer;
    private final Cooldowns global;

    public SummonGate() {
        this(System::currentTimeMillis);
    }

    public SummonGate(LongSupplier clock) {
        this.perPlayer = new Cooldowns(clock);
        this.global = new Cooldowns(clock);
    }

    private static final UUID GLOBAL = new UUID(0, 0);

    /** Starts both cooldowns when ready; otherwise returns the English reason and changes nothing. */
    public synchronized Optional<String> acquire(UUID player, String bossId, BossDefinition.Summon rules) {
        long gl = global.remaining(GLOBAL, bossId);
        if (gl > 0) {
            return Optional.of("This altar is still recovering. Try again in " + Msg.duration(gl) + ".");
        }
        long pl = perPlayer.remaining(player, bossId);
        if (pl > 0) {
            return Optional.of("You must wait " + Msg.duration(pl) + " before summoning it again.");
        }
        if (rules.cooldownSeconds() > 0) {
            perPlayer.set(player, bossId, rules.cooldownSeconds() * 1000L);
        }
        if (rules.globalCooldownSeconds() > 0) {
            global.set(GLOBAL, bossId, rules.globalCooldownSeconds() * 1000L);
        }
        return Optional.empty();
    }

    /** Gives the cooldowns back (the spawn was refused after they were taken). */
    public synchronized void release(UUID player, String bossId) {
        perPlayer.clear(player, bossId);
        global.clear(GLOBAL, bossId);
    }
}
