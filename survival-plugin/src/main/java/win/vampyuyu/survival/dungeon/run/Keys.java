package win.vampyuyu.survival.dungeon.run;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

/** Persistent data keys written on dungeon entities (metadata for listeners and for other features). */
public final class Keys {
    public final NamespacedKey run;
    public final NamespacedKey room;
    public final NamespacedKey mob;
    public final NamespacedKey kind;
    public final NamespacedKey damage;
    public final NamespacedKey npc;
    public final NamespacedKey level;
    /** Extra damage divisor of monsters whose wanted health is above the vanilla attribute cap. */
    public final NamespacedKey factor;

    public Keys(Plugin plugin) {
        run = new NamespacedKey(plugin, "dungeon_run");
        room = new NamespacedKey(plugin, "dungeon_room");
        mob = new NamespacedKey(plugin, "dungeon_mob");
        kind = new NamespacedKey(plugin, "dungeon_kind");
        damage = new NamespacedKey(plugin, "dungeon_damage");
        npc = new NamespacedKey(plugin, "dungeon_npc");
        level = new NamespacedKey(plugin, "dungeon_level");
        factor = new NamespacedKey(plugin, "dungeon_hpfactor");
    }

    /** Run id written on the entity, or null for a foreign entity. */
    public String runOf(Entity e) {
        return e.getPersistentDataContainer().get(run, PersistentDataType.STRING);
    }

    public String kindOf(Entity e) {
        return e.getPersistentDataContainer().get(kind, PersistentDataType.STRING);
    }

    public Integer roomOf(Entity e) {
        return e.getPersistentDataContainer().get(room, PersistentDataType.INTEGER);
    }

    public Double damageOf(Entity e) {
        return e.getPersistentDataContainer().get(damage, PersistentDataType.DOUBLE);
    }

    public Double factorOf(Entity e) {
        return e.getPersistentDataContainer().get(factor, PersistentDataType.DOUBLE);
    }

    public String npcOf(Entity e) {
        return e.getPersistentDataContainer().get(npc, PersistentDataType.STRING);
    }
}
