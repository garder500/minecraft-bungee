package win.vampyuyu.survival.items.ability;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;
import win.vampyuyu.survival.api.Stat;
import win.vampyuyu.survival.items.ability.Ability.Trigger;

/** The built-in abilities. Every number is a default that {@code items.yml} may override under {@code abilities.<id>}. */
public final class Abilities {
    private Abilities() {}

    /** Registers every ability into {@code registry}. */
    public static void registerAll(AbilityRegistry registry) {
        registry.register(Ability.builder("instant_transmission", "Instant Transmission", Trigger.RIGHT_CLICK)
            .mana(50).describe("<gray>Teleport <green>{range} blocks</green> ahead of you.")
            .param("range", 8).exec(Abilities::instantTransmission).build());
        registry.register(Ability.builder("wither_impact", "Wither Impact", Trigger.RIGHT_CLICK)
            .mana(150).cooldown(3)
            .describe("<gray>Teleport <green>{range} blocks</green> ahead and unleash a blast",
                "<gray>dealing <red>{damage}</red> damage to enemies within <green>{radius} blocks</green>.",
                "<gray>You gain a <green>shield</green> for <green>{shield}s</green> that blocks most damage.")
            .param("range", 10).param("radius", 6).param("damage", 800).param("shield", 5)
            .exec(Abilities::witherImpact).build());
        registry.register(Ability.builder("triple_shot", "Triple Shot", Trigger.SHOOT)
            .mana(10).describe("<gray>Shoots <green>3 arrows</green> at once.",
                "<gray>The extra arrows deal <green>{percent}%</green> of the normal damage.")
            .param("percent", 70).param("spread", 10).exec(Abilities::tripleShot).build());
        registry.register(Ability.builder("grappling_hook", "Grapple", Trigger.FISH)
            .cooldown(2).describe("<gray>Reel the hook in once it lands on a block",
                "<gray>to <green>pull yourself</green> towards it.")
            .param("speed", 1.1).exec(Abilities::grapple).build());
        registry.register(Ability.builder("rod_pull", "Legendary Pull", Trigger.FISH)
            .mana(30).cooldown(4).describe("<gray>Hook an enemy to <green>drag it</green> to you",
                "<gray>and deal <red>{damage}</red> damage.")
            .param("damage", 300).exec(Abilities::rodPull).build());
        registry.register(Ability.builder("volcano_pillar", "Eruption", Trigger.RIGHT_CLICK)
            .mana(200).cooldown(8).describe("<gray>Summon a <gold>pillar of fire</gold> where you aim",
                "<gray>(up to <green>{range} blocks</green>) for <green>{duration}s</green>.",
                "<gray>It deals <red>{damage}</red> damage every half second within <green>{radius} blocks</green>.")
            .param("range", 24).param("duration", 4).param("damage", 140).param("radius", 3.5)
            .exec(Abilities::volcano).build());
        registry.register(Ability.builder("mining_speed_boost", "Mining Speed Boost", Trigger.RIGHT_CLICK)
            .cooldown(90).describe("<gray>Grants <gold>+{amount} Mining Speed</gold> for <green>{duration}s</green>.")
            .param("amount", 250).param("duration", 12).exec(Abilities::miningBoost).build());
        registry.register(Ability.builder("heal", "Heal", Trigger.RIGHT_CLICK)
            .mana(80).cooldown(4).describe("<gray>Heal <green>{percent}%</green> of the max health of you",
                "<gray>and players within <green>{radius} blocks</green>.")
            .param("percent", 15).param("radius", 6).exec(Abilities::heal).build());
        registry.register(Ability.builder("leap", "Leap", Trigger.RIGHT_CLICK)
            .mana(30).cooldown(3).describe("<gray>Dash <green>forward</green>.")
            .param("power", 1.5).exec(Abilities::leap).build());
        registry.register(Ability.builder("frost_nova", "Frost Nova", Trigger.RIGHT_CLICK)
            .mana(100).cooldown(6).describe("<gray>Freeze enemies within <green>{radius} blocks</green>,",
                "<gray>dealing <red>{damage}</red> damage and slowing them for <green>{slow}s</green>.")
            .param("radius", 6).param("damage", 220).param("slow", 5).exec(Abilities::frostNova).build());
        registry.register(Ability.builder("fire_beam", "Fire Beam", Trigger.RIGHT_CLICK)
            .mana(60).cooldown(1).describe("<gray>Shoot a beam of fire up to <green>{range} blocks</green>",
                "<gray>dealing <red>{damage}</red> damage and igniting the target.")
            .param("range", 20).param("damage", 260).exec(Abilities::fireBeam).build());
        registry.register(Ability.builder("overflux", "Overflux", Trigger.RIGHT_CLICK)
            .cooldown(30).describe("<gray>Gain <aqua>overflow mana</aqua> equal to <green>{percent}%</green>",
                "<gray>of your maximum mana.")
            .param("percent", 25).exec(Abilities::overflux).build());
        registry.register(Ability.builder("treecapitator", "Treecapitator", Trigger.BREAK_BLOCK)
            .cooldown(2).describe("<gray>Breaks up to <green>{blocks} logs</green> of a tree at once.")
            .param("blocks", 35).exec(ctx -> chop(ctx, false)).build());
        registry.register(Ability.builder("jungle_chop", "Jungle Might", Trigger.BREAK_BLOCK)
            .cooldown(0.5).describe("<gray>Breaks up to <green>{blocks} logs</green> and the leaves around them,",
                "<gray>with almost no cooldown.")
            .param("blocks", 20).exec(ctx -> chop(ctx, true)).build());
        registry.register(Ability.builder("drill_fuel", "Fuel Tank", Trigger.PASSIVE)
            .describe("<gray>Every block mined uses <green>1 fuel</green>.",
                "<gray>An empty drill gives no stats. Refuel by clicking a fuel item onto it.").build());
        registry.register(Ability.builder("replant", "Replant", Trigger.PASSIVE)
            .describe("<gray>Harvesting a fully grown crop <green>replants it</green>",
                "<gray>using one seed from the drops.").build());
    }

    // ================================================================= helpers

    /** Furthest safe standing spot along the player's view direction within {@code range}, or null when blocked. */
    public static Location safeForward(Player player, double range) {
        Location eye = player.getEyeLocation();
        Vector dir = eye.getDirection().normalize();
        World world = eye.getWorld();
        RayTraceResult hit = world.rayTraceBlocks(eye, dir, range, FluidCollisionMode.NEVER, true);
        double distance = hit == null ? range : Math.max(0, hit.getHitPosition().distance(eye.toVector()) - 0.6);
        for (double d = distance; d >= 1.0; d -= 0.5) {
            Location feet = eye.clone().add(dir.clone().multiply(d)).subtract(0, 1.62, 0);
            Block foot = feet.getBlock();
            Block head = feet.clone().add(0, 1, 0).getBlock();
            if (foot.isPassable() && head.isPassable() && !foot.isLiquid()) {
                feet.setYaw(eye.getYaw());
                feet.setPitch(eye.getPitch());
                return feet;
            }
        }
        return null;
    }

    private static List<LivingEntity> near(AbilityContext ctx, Location center, double radius) {
        List<LivingEntity> out = new ArrayList<>();
        for (Entity entity : center.getWorld().getNearbyEntities(center, radius, radius, radius)) {
            if (entity instanceof LivingEntity living && ctx.targetable(living)) {
                out.add(living);
            }
        }
        return out;
    }

    private static boolean teleport(Player player, Location to) {
        Location from = player.getLocation();
        player.teleport(to);
        player.setFallDistance(0);
        from.getWorld().spawnParticle(Particle.PORTAL, from.add(0, 1, 0), 24, 0.3, 0.6, 0.3, 0.2);
        to.getWorld().playSound(to, Sound.ENTITY_ENDERMAN_TELEPORT, SoundCategory.PLAYERS, 1f, 1f);
        return true;
    }

    // ================================================================= abilities

    private static boolean instantTransmission(AbilityContext ctx) {
        Location to = safeForward(ctx.player(), ctx.param("range"));
        return to != null && to.distanceSquared(ctx.player().getLocation()) > 1 && teleport(ctx.player(), to);
    }

    private static boolean witherImpact(AbilityContext ctx) {
        Player player = ctx.player();
        Location to = safeForward(player, ctx.param("range"));
        if (to != null && to.distanceSquared(player.getLocation()) > 1) {
            teleport(player, to);
        }
        Location center = player.getLocation();
        World world = center.getWorld();
        world.spawnParticle(Particle.EXPLOSION_EMITTER, center.clone().add(0, 1, 0), 3, 1.5, 0.5, 1.5, 0);
        world.spawnParticle(Particle.SMOKE, center.clone().add(0, 1, 0), 60, ctx.param("radius") / 2, 0.8,
            ctx.param("radius") / 2, 0.05);
        world.playSound(center, Sound.ENTITY_GENERIC_EXPLODE, SoundCategory.PLAYERS, 1f, 0.8f);
        double damage = ctx.env().combat().abilityDamage(player, ctx.param("damage"));
        for (LivingEntity target : near(ctx, center, ctx.param("radius"))) {
            ctx.env().combat().strike(player, target, damage, false, true);
        }
        long shield = Math.round(ctx.param("shield") * 1000);
        ctx.state().shieldUntil = System.currentTimeMillis() + shield;
        double max = player.getAttribute(Attribute.MAX_HEALTH) == null ? 20 : player.getAttribute(Attribute.MAX_HEALTH).getValue();
        player.setHealth(Math.min(max, player.getHealth() + max * 0.1));
        return true;
    }

    private static boolean tripleShot(AbilityContext ctx) {
        if (!(ctx.event() instanceof EntityShootBowEvent event) || !(event.getProjectile() instanceof AbstractArrow main)) {
            return false;
        }
        double spread = Math.toRadians(ctx.param("spread"));
        double factor = ctx.param("percent") / 100.0;
        for (int sign : new int[] {-1, 1}) {
            Vector velocity = main.getVelocity().clone().rotateAroundY(sign * spread);
            AbstractArrow extra = main.getWorld().spawnArrow(main.getLocation(), velocity, (float) velocity.length(), 0f);
            extra.setShooter(ctx.player());
            extra.setVelocity(velocity);
            extra.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
            extra.setCritical(main.isCritical());
            extra.setPersistent(false);
            ctx.env().tagger().copy(main, extra, factor);
        }
        return true;
    }

    private static boolean grapple(AbilityContext ctx) {
        if (!(ctx.event() instanceof PlayerFishEvent fish) || fish.getState() == PlayerFishEvent.State.CAUGHT_ENTITY) {
            return false;
        }
        Location hook = fish.getHook().getLocation();
        Player player = ctx.player();
        if (hook.getWorld() != player.getWorld()) {
            return false;
        }
        Vector pull = hook.toVector().subtract(player.getLocation().toVector());
        double distance = pull.length();
        if (distance < 3 || distance > 60) {
            return false;
        }
        Vector velocity = pull.normalize().multiply(Math.min(3.0, ctx.param("speed") + distance / 12.0));
        velocity.setY(Math.max(velocity.getY(), 0.35) + 0.25);
        player.setVelocity(velocity);
        player.setFallDistance(0);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_FISHING_BOBBER_RETRIEVE, SoundCategory.PLAYERS, 1f, 0.7f);
        return true;
    }

    private static boolean rodPull(AbilityContext ctx) {
        if (!(ctx.event() instanceof PlayerFishEvent fish) || fish.getState() != PlayerFishEvent.State.CAUGHT_ENTITY
                || !(fish.getCaught() instanceof LivingEntity target) || !ctx.targetable(target)) {
            return false;
        }
        Player player = ctx.player();
        Vector pull = player.getLocation().toVector().subtract(target.getLocation().toVector()).normalize().multiply(1.1);
        pull.setY(0.4);
        target.setVelocity(pull);
        ctx.env().combat().strike(player, target, ctx.env().combat().abilityDamage(player, ctx.param("damage")), false, true);
        return true;
    }

    private static boolean volcano(AbilityContext ctx) {
        Player player = ctx.player();
        Location eye = player.getEyeLocation();
        double range = ctx.param("range");
        RayTraceResult hit = eye.getWorld().rayTrace(eye, eye.getDirection(), range, FluidCollisionMode.NEVER, true, 0.3,
            e -> e instanceof LivingEntity living && ctx.targetable(living));
        Location target;
        if (hit == null) {
            target = eye.clone().add(eye.getDirection().multiply(range));
            target = target.getWorld().getHighestBlockAt(target).getLocation().add(0.5, 1, 0.5);
        } else {
            target = hit.getHitPosition().toLocation(eye.getWorld());
        }
        final Location center = target;
        double damage = ctx.env().combat().abilityDamage(player, ctx.param("damage"));
        double radius = ctx.param("radius");
        int pulses = (int) Math.max(1, ctx.param("duration") * 2);
        final int[] left = {pulses};
        final win.vampyuyu.survival.core.Scheduler.Handle[] handle = new win.vampyuyu.survival.core.Scheduler.Handle[1];
        center.getWorld().playSound(center, Sound.ENTITY_BLAZE_SHOOT, SoundCategory.PLAYERS, 1.5f, 0.5f);
        handle[0] = ctx.env().core().scheduler().repeating(0, 10, () -> {
            if (left[0]-- <= 0 || !player.isOnline()) {
                handle[0].cancel();
                return;
            }
            World world = center.getWorld();
            for (double y = 0; y < 4; y += 0.5) {
                world.spawnParticle(Particle.FLAME, center.clone().add(0, y, 0), 6, radius / 3, 0.1, radius / 3, 0.02);
            }
            world.spawnParticle(Particle.LAVA, center.clone().add(0, 0.5, 0), 4, radius / 2, 0.2, radius / 2, 0);
            for (LivingEntity victim : near(ctx, center, radius)) {
                victim.setFireTicks(60);
                ctx.env().combat().strike(player, victim, damage, false, true);
            }
        });
        return true;
    }

    private static boolean miningBoost(AbilityContext ctx) {
        Player player = ctx.player();
        var engine = ctx.env().engine();
        engine.addModifier(player.getUniqueId(), "ability:mining_speed", Stat.MINING_SPEED, ctx.param("amount"));
        engine.recompute(player);
        long ticks = Math.max(20, Math.round(ctx.param("duration") * 20));
        ctx.env().core().scheduler().later(ticks, () -> {
            engine.removeModifiers(player.getUniqueId(), "ability:mining_speed");
            if (player.isOnline()) {
                engine.recompute(player);
            }
        });
        player.getWorld().playSound(player.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, SoundCategory.PLAYERS, 1f, 1.4f);
        player.getWorld().spawnParticle(Particle.ENCHANT, player.getLocation().add(0, 1, 0), 40, 0.5, 0.8, 0.5, 0.5);
        return true;
    }

    private static boolean heal(AbilityContext ctx) {
        Player caster = ctx.player();
        double radius = ctx.param("radius");
        double percent = ctx.param("percent") / 100.0;
        List<Player> healed = new ArrayList<>();
        healed.add(caster);
        for (Entity entity : caster.getNearbyEntities(radius, radius, radius)) {
            if (entity instanceof Player other && !other.equals(caster) && ctx.env().engine().active(other)) {
                healed.add(other);
            }
        }
        for (Player player : healed) {
            var attribute = player.getAttribute(Attribute.MAX_HEALTH);
            double max = attribute == null ? 20 : attribute.getValue();
            player.setHealth(Math.min(max, player.getHealth() + max * percent));
            player.getWorld().spawnParticle(Particle.HEART, player.getLocation().add(0, 2, 0), 5, 0.4, 0.3, 0.4, 0);
        }
        caster.getWorld().playSound(caster.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, SoundCategory.PLAYERS, 0.8f, 1.6f);
        return true;
    }

    private static boolean leap(AbilityContext ctx) {
        Player player = ctx.player();
        Vector velocity = player.getLocation().getDirection().setY(0).normalize().multiply(ctx.param("power"));
        velocity.setY(0.55);
        player.setVelocity(velocity);
        player.setFallDistance(0);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_BAT_TAKEOFF, SoundCategory.PLAYERS, 1f, 1f);
        player.getWorld().spawnParticle(Particle.CLOUD, player.getLocation(), 12, 0.3, 0.1, 0.3, 0.05);
        return true;
    }

    private static boolean frostNova(AbilityContext ctx) {
        Player player = ctx.player();
        Location center = player.getLocation();
        double radius = ctx.param("radius");
        double damage = ctx.env().combat().abilityDamage(player, ctx.param("damage"));
        int slowTicks = (int) (ctx.param("slow") * 20);
        for (LivingEntity target : near(ctx, center, radius)) {
            target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, slowTicks, 2));
            ctx.env().combat().strike(player, target, damage, false, true);
        }
        for (int i = 0; i < 40; i++) {
            double angle = i / 40.0 * Math.PI * 2;
            center.getWorld().spawnParticle(Particle.SNOWFLAKE, center.clone().add(Math.cos(angle) * radius, 0.3,
                Math.sin(angle) * radius), 2, 0.1, 0.1, 0.1, 0.01);
        }
        center.getWorld().playSound(center, Sound.BLOCK_GLASS_BREAK, SoundCategory.PLAYERS, 1f, 0.6f);
        return true;
    }

    private static boolean fireBeam(AbilityContext ctx) {
        Player player = ctx.player();
        Location eye = player.getEyeLocation();
        double range = ctx.param("range");
        RayTraceResult hit = eye.getWorld().rayTrace(eye, eye.getDirection(), range, FluidCollisionMode.NEVER, true, 0.4,
            e -> e instanceof LivingEntity living && ctx.targetable(living));
        double length = hit == null ? range : hit.getHitPosition().distance(eye.toVector());
        for (double d = 1; d < length; d += 0.6) {
            eye.getWorld().spawnParticle(Particle.FLAME, eye.clone().add(eye.getDirection().multiply(d)), 1, 0, 0, 0, 0);
        }
        eye.getWorld().playSound(eye, Sound.ENTITY_BLAZE_SHOOT, SoundCategory.PLAYERS, 1f, 1.2f);
        if (hit != null && hit.getHitEntity() instanceof LivingEntity target) {
            target.setFireTicks(80);
            ctx.env().combat().strike(player, target, ctx.env().combat().abilityDamage(player, ctx.param("damage")), false, true);
        }
        return true;
    }

    private static boolean overflux(AbilityContext ctx) {
        var settings = ctx.env().settings();
        double max = ctx.state().stat(Stat.INTELLIGENCE);
        double added = ctx.state().mana.addOverflow(max * ctx.param("percent") / 100.0, max, settings.overflowMaxPercent);
        if (added <= 0) {
            ctx.env().notice(ctx.player(), "§cOverflow mana is already full", 1200);
            return false;
        }
        Player player = ctx.player();
        player.getWorld().spawnParticle(Particle.END_ROD, player.getLocation().add(0, 1, 0), 25, 0.4, 0.8, 0.4, 0.05);
        player.getWorld().playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.PLAYERS, 1f, 1.2f);
        return true;
    }

    // ----------------------------------------------------------------- tree chopping

    private static final ThreadLocal<Boolean> CHAINING = ThreadLocal.withInitial(() -> false);

    /** True while a chain break (tree capitator) is running, so its nested break events do not chain again. */
    public static boolean chaining() {
        return CHAINING.get();
    }

    private static boolean chop(AbilityContext ctx, boolean leaves) {
        if (chaining()) {
            return false;
        }
        CHAINING.set(true);
        try {
            return chopNow(ctx, leaves);
        } finally {
            CHAINING.set(false);
        }
    }

    private static boolean chopNow(AbilityContext ctx, boolean leaves) {
        if (!(ctx.event() instanceof org.bukkit.event.block.BlockBreakEvent event)) {
            return false;
        }
        Block origin = event.getBlock();
        if (!org.bukkit.Tag.LOGS.isTagged(origin.getType())) {
            return false;
        }
        int limit = (int) ctx.param("blocks");
        List<Block> found = new ArrayList<>();
        java.util.Set<Block> seen = new java.util.HashSet<>();
        java.util.ArrayDeque<Block> queue = new java.util.ArrayDeque<>();
        queue.add(origin);
        seen.add(origin);
        while (!queue.isEmpty() && found.size() < limit + (leaves ? 60 : 0)) {
            Block current = queue.poll();
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        Block next = current.getRelative(dx, dy, dz);
                        if (!seen.add(next)) {
                            continue;
                        }
                        Material type = next.getType();
                        boolean log = org.bukkit.Tag.LOGS.isTagged(type);
                        boolean leaf = leaves && org.bukkit.Tag.LEAVES.isTagged(type);
                        if ((log || leaf) && Math.abs(next.getX() - origin.getX()) <= 8
                                && Math.abs(next.getZ() - origin.getZ()) <= 8 && next.getY() >= origin.getY() - 1) {
                            found.add(next);
                            queue.add(next);
                        }
                    }
                }
            }
        }
        int logs = 0;
        int broken = 0;
        for (Block block : found) {
            boolean isLog = org.bukkit.Tag.LOGS.isTagged(block.getType());
            if (isLog && logs >= limit - 1) {
                continue;
            }
            if (!ctx.env().canBuild(ctx.player(), block.getLocation())) {
                continue;
            }
            if (ctx.player().breakBlock(block)) {
                broken++;
                if (isLog) {
                    logs++;
                }
            }
        }
        return broken > 0;
    }
}
