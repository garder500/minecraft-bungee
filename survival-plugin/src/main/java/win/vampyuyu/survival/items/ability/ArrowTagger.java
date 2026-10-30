package win.vampyuyu.survival.items.ability;

import org.bukkit.entity.AbstractArrow;

/** Copies the Skyblock damage tags of one arrow onto another (multi shot abilities). */
@FunctionalInterface
public interface ArrowTagger {
    void copy(AbstractArrow from, AbstractArrow to, double factor);
}
