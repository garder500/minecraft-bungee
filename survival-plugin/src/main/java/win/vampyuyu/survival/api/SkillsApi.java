package win.vampyuyu.survival.api;

import java.util.UUID;
import org.bukkit.entity.Player;

/** Skill levels and experience. Implemented by the {@code skills} feature, main thread only for {@link #addXp}. */
public interface SkillsApi {
    /**
     * Grants experience. Fires a cancellable {@code SkillXpEvent} first (listeners may change the amount),
     * then a {@code SkillLevelUpEvent} per level gained.
     *
     * @param source short cause, e.g. "mining:stone", "boss:grave_king"
     */
    void addXp(Player player, Skill skill, double amount, String source);

    /** Current level (0 when the player has no experience yet). */
    int level(UUID player, Skill skill);

    /** Total experience accumulated in the skill. */
    double xp(UUID player, Skill skill);

    /** Highest reachable level of the skill. */
    int maxLevel(Skill skill);
}
