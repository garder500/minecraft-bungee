package win.vampyuyu.survival.boss;

import java.util.UUID;
import java.util.function.BiConsumer;
import org.bukkit.entity.Entity;

/**
 * Animation cues of a boss fight: "attack", "hurt", "phase", "death", or an ability id ("ground_slam", ...). The fight code
 * sends them here, the skin service plays the matching clip of the boss's rig (BossAnimator). Nothing listens when the
 * skins are off, then a cue does nothing.
 */
final class BossCues {
    private static volatile BiConsumer<UUID, String> sink;
    private static volatile java.util.function.ToIntFunction<String> player;

    private BossCues() {
    }

    static void listen(BiConsumer<UUID, String> listener, java.util.function.ToIntFunction<String> playAll) {
        sink = listener;
        player = playAll;
    }

    /** Plays a clip on every active rigged boss (/boss anim); how many started it, 0 when the skins are off. */
    static int play(String name) {
        java.util.function.ToIntFunction<String> p = player;
        return p == null ? 0 : p.applyAsInt(name);
    }

    static void cue(Entity boss, String name) {
        BiConsumer<UUID, String> s = sink;
        if (s != null && boss != null) {
            s.accept(boss.getUniqueId(), name);
        }
    }
}
