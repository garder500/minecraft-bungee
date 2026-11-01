package win.vampyuyu.survival.core;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

/**
 * Painted window backgrounds of the Skyblock menus: font tiles of the pack (font {@code vampyuyu:menus}, cut by
 * {@code resource-pack/layout.py} BACKGROUNDS) drawn in the title, followed by the title text. Same drawing logic as
 * the lobby's {@code MenuPack.title}. The pack is assumed to be loaded: there is no reliable "pack not loaded" signal
 * on the survival server, and without it the title only shows a few unknown-glyph boxes.
 */
public final class MenuArt {
    /** Font that carries the full-window backgrounds. */
    public static final Key FONT = Key.key("vampyuyu", "menus");
    static final char SHIFT_BACK = '';
    static final char SHIFT_FORWARD = '';
    static final char ROW_BACK = '';
    static final char SEAM = '';
    static final int COLS = 4;

    /** The four Skyblock backgrounds: first tile, tile rows of the picture and chest rows of the window. */
    public enum Kind {
        /** The Skyblock menu: a constellation, buttons only on the constellation slots. */
        MAIN('', 6, 6),
        /** Any 6-row menu: every slot has a cloud-glass frame. */
        PANEL('', 6, 6),
        /** Any 3-row menu (the window is 168 px, so 4 tile rows). */
        SMALL('', 4, 3),
        /** The Forge: info slot 4, recipes in rows 1 to 3, job slots 46 to 52. */
        FORGE('', 6, 6);

        private final char base;
        private final int tileRows;
        private final int chestRows;

        Kind(char base, int tileRows, int chestRows) {
            this.base = base;
            this.tileRows = tileRows;
            this.chestRows = chestRows;
        }

        public char base() { return base; }

        public int tileRows() { return tileRows; }

        /** Rows of the chest this background fits. */
        public int chestRows() { return chestRows; }
    }

    private MenuArt() {}

    /** The background for a menu of {@code rows} chest rows: 3 or fewer is SMALL, anything bigger is PANEL (6 rows). */
    public static Kind forRows(int rows) {
        return rows <= 3 ? Kind.SMALL : Kind.PANEL;
    }

    /** The background tiles then {@code after} (the title text, at the usual title position). */
    public static Component title(Kind kind, Component after) {
        StringBuilder tiles = new StringBuilder().append(SHIFT_BACK);
        for (int r = 0; r < kind.tileRows; r++) {
            for (int c = 0; c < COLS; c++) {
                tiles.append((char) (kind.base + r * COLS + c));
                if (c < COLS - 1) {
                    tiles.append(SEAM);
                }
            }
            if (r < kind.tileRows - 1) {
                tiles.append(ROW_BACK);
            }
        }
        tiles.append(SHIFT_FORWARD);
        return Component.text()
            .append(Component.text(tiles.toString(), NamedTextColor.WHITE).font(FONT))
            .append(readable(after))
            .build();
    }

    /** Title text over the sky art: its own colours (dark grey, dark red...) read badly on blue, so it is drawn light. */
    static Component readable(Component title) {
        String text = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(title);
        return Component.text(text, net.kyori.adventure.text.format.TextColor.color(0xFFF6E0));
    }
}
