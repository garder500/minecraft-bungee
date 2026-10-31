package win.vampyuyu.survival.menu;

import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.jetbrains.annotations.Nullable;

/** Builds the text of the sidebar from plain values (MiniMessage strings). Pure, so the layout is unit tested. */
public final class SidebarLines {
    private SidebarLines() {}

    private static final MiniMessage MINI = MiniMessage.miniMessage();
    /** Longest quest name shown before it is cut with an ellipsis. */
    public static final int QUEST_WIDTH = 26;

    /** What the sidebar shows for one player. {@code null} entries are left out. */
    public record View(String date, String time, boolean day, String zone, @Nullable String coins,
            @Nullable String members, @Nullable String questName, @Nullable String questProgress, String serverName,
            String footer) {}

    /** The lines from top to bottom; a blank line is the empty string. */
    public static List<String> build(View v) {
        List<String> lines = new ArrayList<>();
        lines.add("<gray>" + esc(v.date()));
        lines.add("<gray>" + esc(v.time()) + (v.day() ? " <yellow>☀" : " <blue>☽"));
        lines.add("");
        lines.add("<gray>⏣ <aqua>" + esc(v.zone()));
        lines.add("");
        if (v.coins() != null) {
            lines.add("<white>Purse: <gold>" + esc(v.coins()));
        }
        if (v.members() != null) {
            lines.add("<white>Members: <green>" + esc(v.members()));
        }
        if (v.coins() != null || v.members() != null) {
            lines.add("");
        }
        if (v.questName() != null) {
            lines.add("<aqua>Objective");
            lines.add("<yellow>" + esc(cut(v.questName(), QUEST_WIDTH))
                + (v.questProgress() == null ? "" : " <gray>" + esc(v.questProgress())));
            lines.add("<gray>Type <green>/guide <gray>for help");
            lines.add("");
        }
        if (!v.serverName().isBlank()) {
            lines.add("<white>Server: <green>" + esc(v.serverName()));
        }
        if (!v.footer().isBlank()) {
            lines.add(v.footer());
        }
        // a trailing blank line would only add height
        while (!lines.isEmpty() && lines.get(lines.size() - 1).isEmpty()) {
            lines.remove(lines.size() - 1);
        }
        return lines;
    }

    static String cut(String text, int width) {
        return text.length() <= width ? text : text.substring(0, Math.max(1, width - 1)) + "…";
    }

    private static String esc(String text) {
        return MINI.escapeTags(text);
    }
}
