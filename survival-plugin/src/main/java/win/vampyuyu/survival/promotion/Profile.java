package win.vampyuyu.survival.promotion;

/** How a player plays, as the Master reads it from their history (GDD 11.5). Each profile has one chain per rank. */
public enum Profile {
    COMBAT("Combat"), CRAFT("Craft"), EXPLORE("Exploration"), COOP("Teamwork"), BOSS("Boss hunting");

    private final String label;

    Profile(String label) {
        this.label = label;
    }

    public String label() { return label; }

    public static Profile parse(String name) {
        if (name == null) {
            return null;
        }
        for (Profile p : values()) {
            if (p.name().equalsIgnoreCase(name.trim())) {
                return p;
            }
        }
        return null;
    }
}
