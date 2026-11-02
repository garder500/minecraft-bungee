package win.vampyuyu.survival.breath;

import java.util.List;
import java.util.Locale;
import win.vampyuyu.survival.api.Realm;

/** One event of the Celestial Breath: a title, a weight in the draw, a duration and what it does to the world. */
public record BreathEvent(String id, String title, int weight, int minutes, List<Effect> effects) {

    public enum Type { YIELD, MACHINE, MONSTER, ROUTE, FRACTURE, INVADE }

    /**
     * One effect. {@code target}: a Realm resource id or {@code *} (YIELD), a Realm id (ROUTE, FRACTURE) or {@code hub}
     * or a Realm id (INVADE). {@code value}: the change in percent (YIELD, MACHINE, MONSTER). {@code floor}: the
     * dungeon floor of a FRACTURE.
     */
    public record Effect(Type type, String target, int value, int floor) {
        /** True when the effect helps the players (a bonus, a lower cost, a door to loot). */
        public boolean gain() {
            return switch (type) {
                case YIELD -> value > 0;
                case MACHINE, MONSTER -> value < 0;
                case FRACTURE -> true;
                case ROUTE, INVADE -> false;
            };
        }

        /** True when the effect costs the players something (a malus, a closed route, an enemy). */
        public boolean cost() {
            return switch (type) {
                case YIELD -> value < 0;
                case MACHINE, MONSTER -> value > 0;
                case FRACTURE -> false;
                case ROUTE, INVADE -> true;
            };
        }

        /** The Realm a ROUTE, FRACTURE or INVADE effect names, null for the Hub or another type. */
        public Realm realm() {
            return Realm.byId(target);
        }
    }

    /** Every event must offer something and cost something (GDD 19.3: an event produces a choice). */
    public boolean isChoice() {
        return effects.stream().anyMatch(Effect::gain) && effects.stream().anyMatch(Effect::cost);
    }

    public boolean has(Type type) {
        return effects.stream().anyMatch(e -> e.type() == type);
    }

    /** The English label of a resource id: {@code stasis_crystal} gives {@code Stasis Crystal}. */
    public static String label(String id) {
        StringBuilder out = new StringBuilder();
        for (String word : id.split("_")) {
            if (word.isEmpty()) {
                continue;
            }
            if (out.length() > 0) {
                out.append(' ');
            }
            out.append(word.substring(0, 1).toUpperCase(Locale.ROOT)).append(word.substring(1));
        }
        return out.toString();
    }
}
