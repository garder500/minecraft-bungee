package win.vampyuyu.survival.core;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.entity.Player;

/**
 * Full-screen veils drawn by the resource pack's text core shader (resource-pack/art/fx_shaders.py): a glyph of font
 * {@code vampyuyu:fx} coloured (78, 92, id) is stretched over the whole screen and painted as effect {@code id}. The Skyblock
 * action bar has a single writer (the items HUD): it appends {@link #of(Player)} to its line, so a feature that wants a veil
 * registers a provider here instead of sending its own action bar (two writers make the bar flicker).
 *
 * <p>Ids 1 to 4 belong to the Werewolf night; 5 void, 6 frost, 7 decay, 8 burn, 9 warped are the boss statuses.
 */
public final class ScreenVeil {
    public static final Key FONT = Key.key("vampyuyu", "fx");
    /** The stretched glyph and its negative-advance twin, so a veil takes no room in the line. */
    public static final String GLYPH = "\uF100\uF101";

    private static final List<Function<Player, List<Integer>>> PROVIDERS = new CopyOnWriteArrayList<>();
    /** Players whose action bar a HUD writes (and so carries the veils itself); null = nobody's. */
    private static volatile java.util.function.Predicate<Player> owner;

    /** The HUD that writes the action bar of these players declares itself here (null when it stops). */
    public static void owner(java.util.function.Predicate<Player> hud) {
        owner = hud;
    }

    /** True when no HUD writes {@code player}'s action bar: the veil's own provider must send it. */
    public static boolean unowned(Player player) {
        java.util.function.Predicate<Player> o = owner;
        return o == null || !o.test(player);
    }

    private ScreenVeil() {}

    public static void register(Function<Player, List<Integer>> provider) {
        PROVIDERS.add(provider);
    }

    public static void unregister(Function<Player, List<Integer>> provider) {
        PROVIDERS.remove(provider);
    }

    /** The glyph of veil {@code id} (1..9). */
    public static Component glyph(int id) {
        return Component.text(GLYPH).color(TextColor.color(78, 92, id)).font(FONT);
    }

    /** Every veil the providers want on {@code player} now, in their order (later ones are drawn on top). */
    public static Component of(Player player) {
        Component out = Component.empty();
        for (Function<Player, List<Integer>> provider : PROVIDERS) {
            List<Integer> ids;
            try {
                ids = provider.apply(player);
            } catch (RuntimeException e) {
                continue;
            }
            for (int id : ids) {
                if (id >= 1 && id <= 9) {
                    out = out.append(glyph(id));
                }
            }
        }
        return out;
    }
}
