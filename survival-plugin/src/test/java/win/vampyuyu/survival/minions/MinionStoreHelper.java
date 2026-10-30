package win.vampyuyu.survival.minions;

/** Test access to package private runtime state. */
final class MinionStoreHelper {
    private MinionStoreHelper() {}

    /** Makes the next tick run the island check for this minion. */
    static void forceIslandCheck(MinionManager manager, MinionData d) {
        manager.runtimeOf(d).lastIslandCheck = 0;
    }
}
