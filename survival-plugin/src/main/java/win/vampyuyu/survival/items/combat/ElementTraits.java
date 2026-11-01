package win.vampyuyu.survival.items.combat;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.jspecify.annotations.Nullable;
import win.vampyuyu.survival.api.Element;

/**
 * The element a monster is weak to and the one it resists, stored on the entity when it spawns (natural mobs, bosses,
 * slayers, dungeon monsters) so the damage formula needs no registry.
 */
public final class ElementTraits {
    private static final NamespacedKey WEAK = new NamespacedKey("vampyuyu", "element_weak");
    private static final NamespacedKey RESIST = new NamespacedKey("vampyuyu", "element_resist");

    /** What an entity is weak to and resists (either may be null). */
    public record Traits(@Nullable Element weakTo, @Nullable Element resists) {
        public static final Traits NONE = new Traits(null, null);
    }

    private ElementTraits() {}

    /** Writes the traits (a null element removes the tag). */
    public static void write(Entity entity, @Nullable Element weakTo, @Nullable Element resists) {
        PersistentDataContainer pdc = entity.getPersistentDataContainer();
        set(pdc, WEAK, weakTo);
        set(pdc, RESIST, resists);
    }

    /** Same, from the element names of a data row (unknown or empty names mean none). */
    public static void write(Entity entity, @Nullable String weakTo, @Nullable String resists) {
        write(entity, Element.parse(weakTo), Element.parse(resists));
    }

    public static Traits read(Entity entity) {
        if (entity instanceof org.bukkit.entity.ComplexEntityPart part) {
            entity = part.getParent(); // a hit on a dragon part lands on the dragon that carries the traits
        }
        PersistentDataContainer pdc = entity.getPersistentDataContainer();
        return new Traits(Element.parse(pdc.get(WEAK, PersistentDataType.STRING)),
            Element.parse(pdc.get(RESIST, PersistentDataType.STRING)));
    }

    private static void set(PersistentDataContainer pdc, NamespacedKey key, @Nullable Element element) {
        if (element == null) {
            pdc.remove(key);
        } else {
            pdc.set(key, PersistentDataType.STRING, element.key());
        }
    }
}
