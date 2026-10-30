package win.vampyuyu.survival.api.event;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import win.vampyuyu.survival.api.Skill;

/** Fired on the main thread after a player gained one skill level (once per level gained). */
public final class SkillLevelUpEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final Player player;
    private final Skill skill;
    private final int oldLevel;
    private final int newLevel;

    public SkillLevelUpEvent(Player player, Skill skill, int oldLevel, int newLevel) {
        this.player = player;
        this.skill = skill;
        this.oldLevel = oldLevel;
        this.newLevel = newLevel;
    }

    public Player getPlayer() { return player; }

    public Skill getSkill() { return skill; }

    public int getOldLevel() { return oldLevel; }

    public int getNewLevel() { return newLevel; }

    @Override
    public HandlerList getHandlers() { return HANDLERS; }

    public static HandlerList getHandlerList() { return HANDLERS; }
}
