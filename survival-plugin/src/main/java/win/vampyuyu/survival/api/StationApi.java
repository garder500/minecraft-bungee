package win.vampyuyu.survival.api;

import java.util.Optional;
import java.util.UUID;

/** The stations (Alembic, Crucible, Ranch, contracts). Implemented by the {@code stations} feature. */
public interface StationApi {
    /** The element an Alembic oil gives the player's weapons right now, empty when no oil is active. */
    Optional<Element> oilElement(UUID player);
}
