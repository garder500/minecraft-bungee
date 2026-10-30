package win.vampyuyu.survival.items.ability;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * An item ability. Numbers (mana, cooldown and every named parameter) have defaults here and can be overridden in
 * {@code items.yml} under {@code abilities.<id>.<param>}.
 *
 * @param description MiniMessage lines; {@code {param}} is replaced by the resolved value
 * @param params default numeric parameters used by the description and the executor
 */
public record Ability(String id, String name, Trigger trigger, double mana, double cooldownSeconds,
        List<String> description, Map<String, Double> params, @Nullable Executor executor) {

    /** What starts the ability. */
    public enum Trigger {
        RIGHT_CLICK("RIGHT CLICK"), LEFT_CLICK("LEFT CLICK"), SNEAK_RIGHT_CLICK("SNEAK + RIGHT CLICK"),
        SHOOT("SHOOT"), BREAK_BLOCK("BREAK BLOCK"), FISH("REEL IN"), PASSIVE("PASSIVE");

        private final String label;

        Trigger(String label) {
            this.label = label;
        }

        public String label() { return label; }

        /** True for triggers started by a click on the item. */
        public boolean click() { return this == RIGHT_CLICK || this == LEFT_CLICK || this == SNEAK_RIGHT_CLICK; }
    }

    /** Runs the ability. Return true when it was used (mana and cooldown are then charged), false to cancel quietly. */
    @FunctionalInterface
    public interface Executor {
        boolean run(AbilityContext context);
    }

    public Ability {
        description = List.copyOf(description);
        params = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(params));
    }

    public static Builder builder(String id, String name, Trigger trigger) {
        return new Builder(id, name, trigger);
    }

    /** Fluent builder. */
    public static final class Builder {
        private final String id;
        private final String name;
        private final Trigger trigger;
        private double mana;
        private double cooldown;
        private final java.util.ArrayList<String> lines = new java.util.ArrayList<>();
        private final Map<String, Double> params = new LinkedHashMap<>();
        private Executor executor;

        private Builder(String id, String name, Trigger trigger) {
            this.id = id;
            this.name = name;
            this.trigger = trigger;
        }

        public Builder mana(double mana) { this.mana = mana; return this; }

        public Builder cooldown(double seconds) { this.cooldown = seconds; return this; }

        public Builder describe(String... text) { lines.addAll(List.of(text)); return this; }

        public Builder param(String key, double value) { params.put(key, value); return this; }

        public Builder exec(Executor executor) { this.executor = executor; return this; }

        public Ability build() {
            return new Ability(id, name, trigger, mana, cooldown, lines, params, executor);
        }
    }
}
