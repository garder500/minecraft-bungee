package win.vampyuyu.survival.dungeon;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import win.vampyuyu.survival.core.Cooldowns;
import win.vampyuyu.survival.core.Msg;

/**
 * The entrance "pad" in the hub: standing near the configured point (dungeon.yml, section entrance) shows a clickable
 * hint. The hub belongs to the island feature, so this only reads a position; signposts there should point at it.
 */
public final class EntranceHint {
    private final DungeonService svc;
    private final Cooldowns cooldowns = new Cooldowns();

    public EntranceHint(DungeonService svc) {
        this.svc = svc;
    }

    /** Runs once a second on the main thread. */
    public void tick() {
        DungeonConfig cfg = svc.cfg();
        if (!cfg.entranceEnabled) {
            return;
        }
        World w = Bukkit.getWorld(cfg.entranceWorld);
        if (w == null) {
            return;
        }
        Location pad = new Location(w, cfg.entranceX, cfg.entranceY, cfg.entranceZ);
        double r2 = cfg.entranceRadius * cfg.entranceRadius;
        for (Player p : w.getPlayers()) {
            if (p.getLocation().distanceSquared(pad) <= r2 && svc.runs.runOf(p.getUniqueId()) == null
                    && cooldowns.tryUse(p.getUniqueId(), "hint", cfg.entranceHintCooldownSeconds * 1000L)) {
                p.sendMessage(Msg.component("<dark_red>The Catacombs <gray>lie below. ")
                    .append(Msg.component("<yellow><bold>[Click to enter]")
                        .clickEvent(ClickEvent.runCommand("/dungeon"))
                        .hoverEvent(HoverEvent.showText(Component.text("Open the floor menu")))));
            }
        }
    }
}
