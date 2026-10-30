package win.vampyuyu.survival.minions;

/** How many minions a player may have placed. */
public final class SlotRules {
    private SlotRules() {}

    /** Base slots plus one per threshold reached by the number of different minions crafted, capped. */
    public static int slots(MinionConfig config, int uniqueCrafted) {
        int slots = config.baseSlots;
        for (int threshold : config.slotThresholds) {
            if (uniqueCrafted >= threshold) {
                slots++;
            }
        }
        return Math.min(config.maxSlots, slots);
    }

    /** Different minions still needed for the next slot, or -1 when the player is at the cap. */
    public static int untilNext(MinionConfig config, int uniqueCrafted) {
        if (slots(config, uniqueCrafted) >= config.maxSlots) {
            return -1;
        }
        for (int threshold : config.slotThresholds) {
            if (uniqueCrafted < threshold) {
                return threshold - uniqueCrafted;
            }
        }
        return -1;
    }
}
