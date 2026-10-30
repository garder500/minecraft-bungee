package win.vampyuyu.survival.dungeon.run;

import org.bukkit.Material;
import org.bukkit.entity.EntityType;

/**
 * One kind of dungeon monster. Health and damage are base values, multiplied by the floor and the party size when
 * spawned. {@code tier} picks the armour material by floor (see {@link MobFactory}).
 */
public record MobSpec(String id, String name, EntityType type, double health, double damage, double speed,
        Material weapon, boolean armored, boolean invisible, int minFloor, int weight, String color) {}
