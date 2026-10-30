package win.vampyuyu.survival.dungeon.run;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.bossbar.BossBar;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.scoreboard.Scoreboard;

/** What a run remembers about one participant. */
final class RunPlayer {
    final UUID id;
    final String name;
    GameMode originalMode = GameMode.SURVIVAL;
    Scoreboard originalBoard;
    Sidebar sidebar;
    /** Left the run (command, quit, world change). */
    boolean left;
    boolean ghost;
    boolean eliminated;
    int ghostDeaths;
    int totalDeaths;
    int kills;
    int secrets;
    int bonusLives;
    long ghostUntil;
    Location body;
    UUID bodyStand;
    long reviveStart;
    BossBar reviveBar;
    int room = -1;
    Location lastSafe;
    final Map<Integer, Long> hintAt = new HashMap<>();
    long invulnerableUntil;
    boolean origAllowFlight;
    boolean origFlying;
    boolean rewarded;
    boolean darkness;
    long lastRoomTitle;

    RunPlayer(UUID id, String name) {
        this.id = id;
        this.name = name;
    }

    boolean alive() { return !left && !ghost && !eliminated; }

    /** Still counts as part of the party (may be revived). */
    boolean standing() { return !left && !eliminated; }
}
