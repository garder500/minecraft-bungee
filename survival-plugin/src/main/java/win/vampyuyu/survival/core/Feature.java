package win.vampyuyu.survival.core;

/**
 * One self-contained gameplay feature. Implementations live in their own package and are listed once in
 * {@code SurvivalPlugin}. {@link #enable} is where a feature registers its commands, listeners, items and
 * services through the {@link Core}; it must not touch another feature's classes (use {@code api/}).
 */
public interface Feature {
    /** Stable lower case id, also the config key {@code features.<id>.enabled} and the profile/data namespace. */
    String id();

    /**
     * Starts the feature. Features enable in registration order: do not {@code require()} a service of a feature
     * registered later, look it up lazily when needed. Throwing disables only this feature (it is logged).
     */
    void enable(Core core) throws Exception;

    /** Stops the feature (reverse registration order). Core already unregisters commands, permissions and GUIs. */
    default void disable() {}
}
