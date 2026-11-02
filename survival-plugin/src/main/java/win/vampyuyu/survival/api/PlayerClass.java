package win.vampyuyu.survival.api;

import java.util.Locale;

/**
 * The four playable classes (GDD 11.2). Artisan and Warden are trades, not classes. The numbers here are the Novice
 * rank bonuses, in the scale of the meta caps of GDD 8 (Vitality points, Might %, Dexterity): they are clamped by
 * {@code ClassBudget}, never added on top of the cap.
 */
public enum PlayerClass {
    VANGUARD("Vanguard", "Tank", "Bulwark", "-30% damage taken for 6 s", "-5% damage taken near allies", 60, 0, 0, 0, 30, 25),
    DUELIST("Duelist", "Single target, mobility", "Rift Step", "short 6 block dash, your next hit is a Surge",
        "a Surge every 4th hit instead of every 5th", 20, 15, 20, 0, 20, 12),
    CHANNELER("Channeler", "Element and area", "Elemental Burst", "area blast of your weapon's element",
        "+10% elemental weakness bonus", 20, 5, 5, 40, 40, 20),
    RANGER("Ranger", "Distance, mobility", "Gale Volley", "volley of 3 arrows, each 40% of your bow damage",
        "+10% speed in combat, arrows fly further", 20, 10, 20, 0, 30, 18);

    private final String displayName;
    private final String role;
    private final String active;
    private final String activeText;
    private final String passiveText;
    private final double vitality;
    private final double might;
    private final double dexterity;
    private final double aetherPool;
    private final double aetherCost;
    private final int cooldownSeconds;

    PlayerClass(String displayName, String role, String active, String activeText, String passiveText, double vitality,
            double might, double dexterity, double aetherPool, double aetherCost, int cooldownSeconds) {
        this.displayName = displayName;
        this.role = role;
        this.active = active;
        this.activeText = activeText;
        this.passiveText = passiveText;
        this.vitality = vitality;
        this.might = might;
        this.dexterity = dexterity;
        this.aetherPool = aetherPool;
        this.aetherCost = aetherCost;
        this.cooldownSeconds = cooldownSeconds;
    }

    public String displayName() { return displayName; }
    public String role() { return role; }
    /** Name of the active ability. */
    public String active() { return active; }
    public String activeText() { return activeText; }
    public String passiveText() { return passiveText; }
    /** Raw Novice bonus in Vitality points (1 point = 1 PV of the GDD scale). */
    public double vitality() { return vitality; }
    /** Raw Novice bonus in Might (percent damage). */
    public double might() { return might; }
    public double dexterity() { return dexterity; }
    /** Extra size of the Aether reserve (the base reserve is 100). */
    public double aetherPool() { return aetherPool; }
    public double aetherCost() { return aetherCost; }
    public int cooldownSeconds() { return cooldownSeconds; }

    /** The class with this name (any case), or null. */
    public static PlayerClass parse(String name) {
        if (name == null) {
            return null;
        }
        String key = name.trim().toUpperCase(Locale.ROOT);
        for (PlayerClass c : values()) {
            if (c.name().equals(key)) {
                return c;
            }
        }
        return null;
    }
}
