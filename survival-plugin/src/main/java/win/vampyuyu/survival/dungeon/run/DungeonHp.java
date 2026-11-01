package win.vampyuyu.survival.dungeon.run;

import win.vampyuyu.survival.items.combat.PowerCurve;

/**
 * Health of a dungeon monster, from the power curve: a basic monster (base health {@code dungeon.mobs.base-health}) of a
 * floor of tier N dies in {@code dungeon.mobs.target-seconds} to one player with the tier N sword and armor (no
 * weakness assumed); tougher monsters scale from their base health. The floor's own {@code hp} multiplier and the party
 * scale come on top.
 */
public final class DungeonHp {
    private DungeonHp() {}

    /**
     * @param curve the power curve (null or without that tier: the old rule, base health times the multipliers)
     * @param specHealth the monster kind's base health
     * @param floorHp the floor's health multiplier
     * @param party the party health scale
     */
    public static double health(PowerCurve curve, int tier, double seconds, double baseUnit, double specHealth, double floorHp,
            double party) {
        double mult = floorHp * party;
        if (curve == null || !curve.has(tier) || seconds <= 0 || baseUnit <= 0) {
            return Math.max(1, specHealth * mult);
        }
        return Math.max(1, Math.round(curve.neutralHealth(tier, seconds) * (specHealth / baseUnit) * mult));
    }
}
