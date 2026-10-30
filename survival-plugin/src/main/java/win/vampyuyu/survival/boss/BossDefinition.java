package win.vampyuyu.survival.boss;

import java.util.List;
import java.util.Map;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.jspecify.annotations.Nullable;

/**
 * Immutable description of a boss, loaded from {@code boss.yml} by {@link DefinitionParser}.
 *
 * @param id contract id ({@code voidwyrm}, {@code crypt_lord}...), {@code [a-z0-9_]+}
 * @param name MiniMessage display name
 * @param description one line shown by {@code /boss info}
 * @param entity vanilla base entity
 * @param health virtual health points (the entity itself only mirrors the percentage)
 * @param damage base hit damage, vanilla hearts scale (2 = one heart) before the global damage scale
 * @param armor armor attribute of the entity
 * @param scale scale attribute (1 = vanilla size)
 * @param speed speed multiplier on the entity's movement speed
 * @param knockbackResistance 0-1, 1 = immune to knockback
 * @param barColor boss bar colour (adventure names)
 * @param barOverlay boss bar overlay (adventure names)
 * @param arena fight rules
 * @param phases at least one, ordered by decreasing threshold
 * @param equipment slot name to item spec
 * @param options entity options (size, baby, glow...)
 * @param loot rewards
 * @param summon how it is summoned, null when it is spawned by code only
 * @param kind regular boss, slayer tier or world boss
 */
public record BossDefinition(String id, String name, String description, EntityType entity, double health,
        double damage, double armor, double scale, double speed, double knockbackResistance, String barColor,
        String barOverlay, Arena arena, List<Phase> phases, Map<String, EquipSpec> equipment,
        Map<String, Object> options, Loot loot, @Nullable Summon summon, Kind kind) {

    /** What a definition is used for. */
    public enum Kind { BOSS, SLAYER, WORLD }

    public BossDefinition {
        phases = List.copyOf(phases);
        equipment = Map.copyOf(equipment);
        options = Map.copyOf(options);
    }

    /**
     * Arena rules.
     *
     * @param radius players farther than this from the spawn point cannot hurt the boss
     * @param leash the boss is pulled back when it strays farther than this
     * @param projectileRange maximum distance of a counted hit (arrows, spells)
     * @param noFly flying or gliding players cannot hurt the boss
     * @param noBuild nobody places or breaks blocks in the arena while the boss lives
     * @param noTeleport ender pearls and chorus fruit are blocked in the arena
     * @param minPlayers participants below this count do not scale health
     * @param scalePerPlayer extra health fraction per participant above minPlayers
     * @param scaleCap maximum health multiplier
     * @param enrageSeconds seconds until the boss enrages, 0 = never
     * @param enrageDamage damage multiplier at enrage start
     * @param enrageGrowth extra damage multiplier per further minute of enrage
     * @param idleDespawnSeconds the boss disappears when nobody was in the arena for this long, 0 = never
     * @param timeLimitSeconds hard time limit, 0 = none
     * @param maxMinions cap of living minions
     */
    public record Arena(double radius, double leash, double projectileRange, boolean noFly, boolean noBuild,
            boolean noTeleport, int minPlayers, double scalePerPlayer, double scaleCap, int enrageSeconds,
            double enrageDamage, double enrageGrowth, int idleDespawnSeconds, int timeLimitSeconds,
            int maxMinions) {}

    /**
     * A phase starts when the health fraction drops to {@code at}.
     *
     * @param enter abilities cast once when the phase starts
     * @param abilities abilities cast on cooldown while the phase lasts
     */
    public record Phase(String name, double at, String title, String subtitle, double damageMult, double speedMult,
            List<AbilitySpec> enter, List<AbilitySpec> abilities) {
        public Phase {
            enter = List.copyOf(enter);
            abilities = List.copyOf(abilities);
        }
    }

    /**
     * Equipment piece.
     *
     * @param color optional RGB for dyed leather
     */
    public record EquipSpec(Material material, @Nullable Integer color) {}

    /**
     * Rewards.
     *
     * @param coins total coins, split by damage share (see {@link LootMath#share}; the pool is never exceeded)
     * @param combatXp total combat xp, split the same way
     * @param minShare damage share below which a participant gets nothing
     * @param shareFloor fraction of coins/xp split equally between the eligible participants, the rest follows the damage share
     * @param rankBonus chance multiplier for rank 1, 2, 3...
     * @param entries item drops
     * @param wingsMinShare share above which WingsApi.grantOnce is called, negative = never
     * @param announceBelow drops with a chance at or below this are announced to the server
     */
    public record Loot(double coins, double combatXp, double minShare, double shareFloor, List<Double> rankBonus,
            List<LootEntry> entries, double wingsMinShare, double announceBelow) {
        public Loot {
            rankBonus = List.copyOf(rankBonus);
            entries = List.copyOf(entries);
        }

        public static Loot none() {
            return new Loot(0, 0, 0.01, 0.25, List.of(), List.of(), -1, 0.01);
        }
    }

    /**
     * One drop.
     *
     * @param item {@code custom:<id>} (registered Items id) or {@code vanilla:<MATERIAL>}
     * @param chance base chance 0-1, 1 = guaranteed
     * @param min minimum amount
     * @param max maximum amount
     * @param minShare damage share needed
     * @param maxRank worst rank allowed, 0 = anyone
     * @param magicFind whether Magic Find boosts the chance
     * @param pity chance added per dry kill
     * @param pityCap maximum pity bonus
     * @param fromTier slayer tier from which the entry exists (1 for everything else)
     */
    public record LootEntry(String item, double chance, int min, int max, double minShare, int maxRank,
            boolean magicFind, double pity, double pityCap, int fromTier) {}

    /**
     * Summon rules (only the summon item path checks them; {@code BossApi.spawn} never does).
     *
     * @param item custom item id that summons it
     * @param mode {@code altar} (must stand near the altar) or {@code free}
     * @param worlds allowed world names, empty = any Skyblock world
     * @param altar altar position, only for the altar mode
     * @param maxSpawnDistance max distance from the world spawn (0 = no limit)
     * @param cooldownSeconds per player cooldown
     * @param globalCooldownSeconds cooldown shared by every player
     * @param requireIslandMember on an island the summoner must be a member
     * @param requireWater the summoner must stand in water
     * @param maxConcurrent how many of this boss may live at once
     * @param refundOnDespawn the item is given back when the boss despawns without a kill
     */
    public record Summon(String item, String mode, List<String> worlds, @Nullable Altar altar,
            double maxSpawnDistance, int cooldownSeconds, int globalCooldownSeconds, boolean requireIslandMember,
            boolean requireWater, int maxConcurrent, boolean refundOnDespawn, int globalMaxConcurrent) {
        public Summon {
            worlds = List.copyOf(worlds);
        }

        public Summon(String item, String mode, List<String> worlds, @Nullable Altar altar, double maxSpawnDistance,
                int cooldownSeconds, int globalCooldownSeconds, boolean requireIslandMember, boolean requireWater,
                int maxConcurrent, boolean refundOnDespawn) {
            this(item, mode, worlds, altar, maxSpawnDistance, cooldownSeconds, globalCooldownSeconds,
                requireIslandMember, requireWater, maxConcurrent, refundOnDespawn, Math.max(maxConcurrent, 6));
        }
    }

    /**
     * Altar position and the radius around it where the item works. With {@code cellSize > 0} the altar repeats on a
     * grid (every island owns one in its own End cell): the centre used is the one nearest to the player.
     */
    public record Altar(String world, double x, double y, double z, double radius, double cellSize) {
        public Altar(String world, double x, double y, double z, double radius) {
            this(world, x, y, z, radius, 0);
        }

        /** The altar centre closest to {@code (px, pz)}; the configured one when the altar does not repeat. */
        public double[] centreNear(double px, double pz) {
            if (!(cellSize > 0)) {
                return new double[] {x, z};
            }
            return new double[] {Math.round((px - x) / cellSize) * cellSize + x,
                Math.round((pz - z) / cellSize) * cellSize + z};
        }
    }

    public boolean isSlayer() { return kind == Kind.SLAYER; }
}
