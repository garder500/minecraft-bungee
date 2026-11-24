package win.vampyuyu.survival.island;

import java.util.ArrayList;
import java.util.List;
import win.vampyuyu.survival.api.Realm;
import win.vampyuyu.survival.core.MenuLang;

/**
 * The beat that tells a player a Guardian's defeat woke Sky Gates (pure, no Bukkit). Two parts: the words of the title and
 * chat line shown at once ({@link #announce}), and the pacing of the reveal on the Quai on the player's next visit, where
 * the new Gates light one by one ({@link Pacer}). Which Gates are new is {@link SkyGateState#unseen}.
 */
public final class GateReveal {
    private GateReveal() {}

    /** What the player reads when Gates wake: a title, its subtitle and the chat line (the click is added by the caller). */
    public record Announce(String title, String subtitle, String chat, String click, String hover) {}

    /**
     * The words for the Gates that just woke, in the viewer's language ({@code lang} "fr", "de", anything else English).
     * The title is kept short (it is drawn at four times the size of chat and clips in a small window). One or two Gates are
     * named in the subtitle; more than two would not fit, so the subtitle sends the player to the Quai and the chat line
     * ("... wake on the Quai: names") carries the names.
     */
    public static Announce announce(List<Realm> woke, String lang) {
        List<String> names = new ArrayList<>();
        for (Realm realm : woke) {
            names.add(realm.title());
        }
        String joined = String.join(", ", names);
        String click = MenuLang.tr(lang, "quai.wake.click", "[Go to the Quai]");
        String hover = MenuLang.tr(lang, "quai.wake.hover", "/quai");
        if (woke.size() == 1) {
            return new Announce(MenuLang.tr(lang, "quai.wake.title_one", "A Gate wakes"), names.get(0),
                MenuLang.tr(lang, "quai.wake.chat_one", "A Gate wakes on the Quai: {0}.", joined), click, hover);
        }
        String title = MenuLang.tr(lang, "quai.wake.title_many", "{0} Gates wake", woke.size());
        String subtitle = woke.size() == 2 ? names.get(0) + " · " + names.get(1)
            : MenuLang.tr(lang, "quai.wake.sub_many", "The Quai is calling you");
        return new Announce(title, subtitle,
            MenuLang.tr(lang, "quai.wake.chat_many", "{0} Gates wake on the Quai: {1}.", woke.size(), joined), click, hover);
    }

    /**
     * When the next Gate of a player's reveal lights. One per player: {@link #poll} is called each step while the player is
     * near the Quai. The first Gate waits {@code startDelay} steps after they arrive (a travel fade has to finish), then one
     * Gate every {@code gap} steps, in the order of the list it is given. A step is one view tick of {@link SkyGates}.
     */
    public static final class Pacer {
        private int waited;
        private long nextAt;
        private int fired;

        /** The Gate to light now, or null. {@code unseen} is {@link SkyGateState#unseen}; empty resets the wait. */
        public Realm poll(List<Realm> unseen, long step, int startDelay, int gap) {
            if (unseen.isEmpty()) {
                waited = 0;
                fired = 0;
                return null;
            }
            waited++;
            if (waited <= startDelay || step < nextAt) {
                return null;
            }
            nextAt = step + Math.max(1, gap);
            fired++;
            return unseen.get(0);
        }

        /** How many Gates of the current sequence have lit (the chime rises with it). */
        public int fired() {
            return fired;
        }

        /** The player left the Quai: the next visit waits again. */
        public void leave() {
            waited = 0;
        }
    }

    /** The view steps between two Gates: {@code ticks} server ticks, rounded up, at one step per {@code period} ticks. */
    public static int steps(int ticks, int period) {
        return Math.max(1, (ticks + period - 1) / period);
    }
}
