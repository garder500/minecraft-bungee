package win.vampyuyu.survival.items.pet;

import java.util.ArrayList;
import java.util.List;

/** Profile section {@code pets}. */
public final class PetData {
    /** Id of the summoned pet, empty for none. */
    public String active = "";
    /** Summon the active pet again on join. */
    public boolean autoPet = true;
    public List<Entry> pets = new ArrayList<>();

    /** One owned pet. */
    public static final class Entry {
        /** Stable id (uuid string). */
        public String id = "";
        public String type = "";
        /** {@code Rarity} name. */
        public String rarity = "COMMON";
        public double xp = 0;
        /** Id of the held item definition, empty for none. */
        public String held = "";
    }
}
