package win.vampyuyu.survival.minions;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Remembers the uid of every minion item that was ever placed. A minion item carries a uid; once placed, the uid is
 * spent, so a copy of the item (a dupe glitch, a restored backup of an inventory) cannot be placed a second time.
 * Picking a minion up returns an item with a NEW uid. Bounded: the oldest uids are forgotten past {@code max}.
 */
public final class UidLedger {
    private final Set<String> spent = new LinkedHashSet<>();
    private final int max;

    public UidLedger(int max) {
        this.max = Math.max(16, max);
    }

    public boolean isSpent(String uid) {
        return spent.contains(uid);
    }

    /** Marks the uid spent; false when it already was. */
    public boolean spend(String uid) {
        if (!spent.add(uid)) {
            return false;
        }
        Iterator<String> it = spent.iterator();
        while (spent.size() > max && it.hasNext()) {
            it.next();
            it.remove();
        }
        return true;
    }

    public int size() {
        return spent.size();
    }

    public List<String> snapshot() {
        return new ArrayList<>(spent);
    }

    public void load(Collection<String> uids) {
        spent.clear();
        if (uids != null) {
            uids.forEach(this::spend);
        }
    }
}
