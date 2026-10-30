package win.vampyuyu.survival.boss.slayer;

import java.util.Optional;
import org.bukkit.entity.EntityType;
import win.vampyuyu.survival.boss.BossData;
import win.vampyuyu.survival.boss.BossSession;

/** Pure state machine of a slayer quest: start rules, progress, win, fail, restart recovery. */
public final class SlayerQuests {
    private SlayerQuests() {}

    /** Outcome of a win. */
    public record Win(int levelBefore, int levelAfter, long xpAfter) {
        public boolean leveledUp() { return levelAfter > levelBefore; }
    }

    public static int level(BossData data, SlayerDefinition def) {
        return SlayerMath.level(data.slayerXp.getOrDefault(def.id(), 0L), def.levelXp());
    }

    /** English refusal for starting a tier, empty when allowed. */
    public static Optional<String> denial(BossData data, SlayerDefinition def, int tier, double coins) {
        if (tier < 1 || tier > def.tiers().size()) {
            return Optional.of("Tier must be between I and " + SlayerDefinition.roman(def.tiers().size()) + ".");
        }
        if (data.quest != null) {
            return Optional.of("You already have a slayer quest running. Finish it or cancel it first.");
        }
        SlayerDefinition.Tier t = def.tier(tier);
        int level = level(data, def);
        if (level < t.unlockLevel()) {
            return Optional.of("Reach " + def.name() + " level " + t.unlockLevel() + " to unlock tier "
                + SlayerDefinition.roman(tier) + ".");
        }
        if (coins < t.cost()) {
            return Optional.of("You need " + (long) t.cost() + " coins for this quest.");
        }
        return Optional.empty();
    }

    /** Creates the quest after the coins were paid. */
    public static BossData.Quest begin(BossData data, SlayerDefinition def, int tier, long now) {
        SlayerDefinition.Tier t = def.tier(tier);
        data.quest = new BossData.Quest(def.id(), tier, t.requiredXp(), t.cost(), now);
        return data.quest;
    }

    /**
     * Adds progress for killing {@code type}. Returns true when this kill completed the quest (state turns READY).
     * Only a COLLECTING quest of the matching slayer progresses.
     */
    public static boolean addKill(BossData data, SlayerDefinition def, EntityType type) {
        BossData.Quest q = data.quest;
        if (q == null || !q.slayer.equals(def.id()) || !BossData.Quest.COLLECTING.equals(q.state)) {
            return false;
        }
        double amount = def.progressFor(type);
        if (amount <= 0) {
            return false;
        }
        q.progress = SlayerMath.addProgress(q.progress, q.required, amount);
        if (q.progress >= q.required) {
            q.state = BossData.Quest.READY;
            return true;
        }
        return false;
    }

    /** READY to FIGHTING; false when the quest is not ready. */
    public static boolean beginFight(BossData data) {
        if (data.quest == null || !BossData.Quest.READY.equals(data.quest.state)) {
            return false;
        }
        data.quest.state = BossData.Quest.FIGHTING;
        return true;
    }

    /** FIGHTING back to READY (fight interrupted by the server, not the player's fault). */
    public static void interrupted(BossData data) {
        if (data.quest != null && BossData.Quest.FIGHTING.equals(data.quest.state)) {
            data.quest.state = BossData.Quest.READY;
        }
    }

    /**
     * Whether a fight that ended for {@code reason} costs the player the quest. Leaving, dying, running out of time, or
     * losing the boss after hurting it is a lost fight: otherwise quitting at low health would be a free retry at full
     * boss health. Only server faults and an admin removal keep the quest ready.
     *
     * @param damaged the boss took damage before it ended
     */
    public static boolean failsQuest(BossSession.End reason, boolean damaged) {
        return switch (reason) {
            case TIMEOUT, OWNER_DIED, OWNER_LEFT -> true;
            case DESPAWN, ENTITY_LOST -> damaged;
            default -> false;
        };
    }

    /** Join or restart recovery: no fight survives a restart. */
    public static void recover(BossData data) {
        interrupted(data);
    }

    /** The boss died: slayer xp, kill counter, quest cleared. */
    public static Win win(BossData data, SlayerDefinition def, int tier) {
        long before = data.slayerXp.getOrDefault(def.id(), 0L);
        long after = before + def.tier(tier).slayerXp();
        data.slayerXp.put(def.id(), after);
        data.slayerKills.merge(def.id() + ":" + tier, 1, Integer::sum);
        data.quest = null;
        return new Win(SlayerMath.level(before, def.levelXp()), SlayerMath.level(after, def.levelXp()), after);
    }

    /** Failure (death, time out): the quest and its coins are gone. */
    public static void fail(BossData data) {
        data.quest = null;
    }
}
