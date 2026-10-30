package win.vampyuyu.survival.dungeon.run;

import java.util.List;
import io.papermc.paper.scoreboard.numbers.NumberFormat;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;

/** A per player sidebar (no numbers) that is updated in place. Not the action bar: that belongs to the items HUD. */
final class Sidebar {
    private static final int MAX = 14;
    private final Scoreboard board;
    private final Objective objective;
    private int shown;

    Sidebar(Component title) {
        this.board = Bukkit.getScoreboardManager().getNewScoreboard();
        this.objective = board.registerNewObjective("dungeon", Criteria.DUMMY, title);
        this.objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        this.objective.numberFormat(NumberFormat.blank());
    }

    private static String entry(int i) {
        return "§" + Integer.toHexString(i) + "§r";
    }

    void show(Player player) {
        player.setScoreboard(board);
    }

    void update(List<Component> lines) {
        int n = Math.min(MAX, lines.size());
        for (int i = 0; i < n; i++) {
            var score = objective.getScore(entry(i));
            score.setScore(n - i);
            score.customName(lines.get(i));
        }
        for (int i = n; i < shown; i++) {
            board.resetScores(entry(i));
        }
        shown = n;
    }

    void hide(Player player, Scoreboard restore) {
        try {
            player.setScoreboard(restore != null ? restore : Bukkit.getScoreboardManager().getMainScoreboard());
        } catch (RuntimeException ignored) {
            // player offline
        }
    }
}
