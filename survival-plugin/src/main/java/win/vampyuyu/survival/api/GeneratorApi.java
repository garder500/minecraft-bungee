package win.vampyuyu.survival.api;

import java.util.UUID;

/** The cobble generator tier of a player (1 to 4). Implemented by the {@code quarry} feature. */
public interface GeneratorApi {
    int tier(UUID player);
}
