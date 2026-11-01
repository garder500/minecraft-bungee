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
    /** Total health of a monster whose wanted health is above the vanilla attribute cap (its virtual pool). */
    public final NamespacedKey hpMax;
    /** Health left in the virtual pool. */
    public final NamespacedKey hp;

    public Keys(Plugin plugin) {
        run = new NamespacedKey(plugin, "dungeon_run");
        room = new NamespacedKey(plugin, "dungeon_room");
        mob = new NamespacedKey(plugin, "dungeon_mob");
        kind = new NamespacedKey(plugin, "dungeon_kind");
        damage = new NamespacedKey(plugin, "dungeon_damage");
        npc = new NamespacedKey(plugin, "dungeon_npc");
        level = new NamespacedKey(plugin, "dungeon_level");
        hpMax = new NamespacedKey(plugin, "dungeon_hp_max");
        hp = new NamespacedKey(plugin, "dungeon_hp");
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

    /** Total of the virtual health pool, or null for a monster with plain vanilla health. */
    public Double hpMaxOf(Entity e) {
        return e.getPersistentDataContainer().get(hpMax, PersistentDataType.DOUBLE);
    }

    /** Health left in the virtual pool (the total when untouched), or null for a monster with plain vanilla health. */
    public Double hpOf(Entity e) {
        Double max = hpMaxOf(e);
        if (max == null) {
            return null;
        }
        Double left = e.getPersistentDataContainer().get(hp, PersistentDataType.DOUBLE);
        return left == null ? max : left;
    }

    public void setHp(Entity e, double left) {
        e.getPersistentDataContainer().set(hp, PersistentDataType.DOUBLE, left);
    }

    public String npcOf(Entity e) {
        return e.getPersistentDataContainer().get(npc, PersistentDataType.STRING);
    }
}
