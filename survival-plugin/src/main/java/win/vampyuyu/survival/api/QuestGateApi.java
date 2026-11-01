package win.vampyuyu.survival.api;

import java.util.Optional;
import java.util.UUID;

/** What the quest plan holds back. Published by the {@code menu} feature; when nobody publishes it, nothing is held. */
public interface QuestGateApi {
    /** The name of the quest a Forge recipe waits for, empty when the player may forge {@code item}. */
    Optional<String> forgeLock(UUID player, String item);
}
