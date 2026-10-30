package win.vampyuyu.survival.boss;

import net.kyori.adventure.text.minimessage.MiniMessage;

/** Escapes player or config text before it is placed inside MiniMessage markup. */
final class MiniEsc {
    private MiniEsc() {}

    static String esc(String text) {
        return MiniMessage.miniMessage().escapeTags(text);
    }
}
