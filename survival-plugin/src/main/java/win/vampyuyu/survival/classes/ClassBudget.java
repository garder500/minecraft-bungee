package win.vampyuyu.survival.classes;

import win.vampyuyu.survival.api.PlayerClass;

/**
 * The class bonuses live INSIDE the meta cap of GDD 8 (at most +100 PV, +25 Might, +25 Dexterity, all sources together),
 * they never come on top of it.
 */
public final class ClassBudget {
    public static final double META_VITALITY = 100;
    public static final double META_MIGHT = 25;
    public static final double META_DEXTERITY = 25;

    /** Bonuses after the cap. */
    public record Bonus(double vitality, double might, double dexterity) {}

    private ClassBudget() {}

    /** The class bonus given {@code usedVitality/Might/Dexterity} already granted by other meta sources. */
    public static Bonus of(PlayerClass chosen, double usedVitality, double usedMight, double usedDexterity) {
        if (chosen == null) {
            return new Bonus(0, 0, 0);
        }
        return new Bonus(room(chosen.vitality(), META_VITALITY, usedVitality), room(chosen.might(), META_MIGHT, usedMight),
            room(chosen.dexterity(), META_DEXTERITY, usedDexterity));
    }

    /** The class bonus plus what a specialization adds, all of it inside the meta cap. */
    public static Bonus with(PlayerClass chosen, double extraVitality, double extraMight, double extraDexterity) {
        if (chosen == null) {
            return new Bonus(0, 0, 0);
        }
        return new Bonus(room(chosen.vitality() + extraVitality, META_VITALITY, 0), room(chosen.might() + extraMight, META_MIGHT, 0),
            room(chosen.dexterity() + extraDexterity, META_DEXTERITY, 0));
    }

    public static Bonus of(PlayerClass chosen) {
        return of(chosen, 0, 0, 0);
    }

    private static double room(double wanted, double cap, double used) {
        return Math.max(0, Math.min(wanted, cap - Math.max(0, used)));
    }
}
