package win.vampyuyu.survival.core;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Drives the feature lifecycle: enable in order, each guarded by try/catch (a failure is logged, what the feature
 * registered through the {@link Core} is rolled back, and the others still load), disable in reverse order. A feature is skipped when {@code enabledCheck}
 * says no (config key {@code features.<id>.enabled}).
 */
public final class FeatureManager {
    private final Logger log;
    private final Predicate<String> enabledCheck;
    private final List<Feature> features;
    private final List<Feature> running = new ArrayList<>();
    private final List<String> failed = new ArrayList<>();

    public FeatureManager(Logger log, Predicate<String> enabledCheck, List<Feature> features) {
        this.log = log;
        this.enabledCheck = enabledCheck;
        this.features = List.copyOf(features);
    }

    public void enableAll(Core core) {
        for (Feature feature : features) {
            String id = feature.id();
            if (!enabledCheck.test(id)) {
                log.info("Feature '" + id + "' is disabled in config.yml");
                continue;
            }
            Core.Mark mark = core.mark();
            try {
                feature.enable(core);
                running.add(feature);
                log.info("Feature '" + id + "' enabled");
            } catch (Throwable t) {
                failed.add(id);
                try {
                    core.rollback(mark);
                } catch (RuntimeException e) {
                    log.log(Level.SEVERE, "Could not roll back the registrations of feature '" + id + "'", e);
                }
                log.log(Level.SEVERE, "Feature '" + id + "' failed to enable and was skipped", t);
            }
        }
    }

    public void disableAll() {
        for (int i = running.size() - 1; i >= 0; i--) {
            Feature feature = running.get(i);
            try {
                feature.disable();
            } catch (Throwable t) {
                log.log(Level.SEVERE, "Feature '" + feature.id() + "' failed to disable cleanly", t);
            }
        }
        running.clear();
    }

    /** Ids of the features currently running, in enable order. */
    public List<String> enabledIds() {
        return running.stream().map(Feature::id).toList();
    }

    /** Ids of the features whose enable() threw. */
    public List<String> failedIds() {
        return List.copyOf(failed);
    }
}
