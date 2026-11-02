package win.vampyuyu.survival.api;

import java.util.UUID;

/**
 * The Astral Interiority (GDD 4.5), Act III. Implemented by the {@code astral} feature. The Paragon (GDD 19.1) is open to the
 * players who survived it: the features that hand out Paragon ranks ask {@link #survived}.
 */
public interface AstralApi {
    /** True when the player took the Throne of the Astral Interiority: the Paragon trials are open to them. */
    boolean survived(UUID player);

    /** True when the player defeated the Guardian of every Realm, so the Devourer's mouth opens for them. */
    boolean canEnter(UUID player);
}
