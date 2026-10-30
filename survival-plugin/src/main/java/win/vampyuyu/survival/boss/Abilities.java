package win.vampyuyu.survival.boss;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

/**
 * The ability library. Every ability telegraphs first (particles and a sound), then lands, and always rewards
 * dodging: moving, jumping, hiding or breaking something. No ability edits blocks. Each method returns the number
 * of ticks the boss stays busy, so abilities never stack.
 */
final class Abilities {
    private Abilities() {}

    static int cast(BossSession s, AbilitySpec spec) {
        return switch (spec.type()) {
            case FIREBALL_BARRAGE -> fireballs(s, spec);
            case GROUND_SLAM -> groundSlam(s, spec);
            case SUMMON_MINIONS -> summon(s, spec);
            case LIGHTNING_STRIKES -> lightning(s, spec);
            case VACUUM -> pull(s, spec, false);
            case WHIRLPOOL -> pull(s, spec, true);
            case POISON_CLOUD, WITHER_CLOUD, BREATH_CLOUD, LAVA_POOLS, VOID_ZONES, ICE_SPIKES, WEB_TRAPS ->
                zones(s, spec, Zone.of(spec.type()));
            case TELEPORT_BEHIND -> teleportBehind(s, spec);
            case SHIELD_CRYSTALS -> shield(s, spec);
            case LASER_BEAM -> laser(s, spec);
            case CHARGE_DASH -> charge(s, spec);
            case DIVE_BOMB -> dive(s, spec);
            case TIDAL_WAVE -> tidalWave(s, spec);
            case FIRE_RING -> fireRing(s, spec);
            case BONE_CAGE -> boneCage(s, spec);
            case SOUL_DRAIN -> soulDrain(s, spec);
            case BLIZZARD -> blizzard(s, spec);
        };
    }

    private static int tele(AbilitySpec spec, int fallback) {
        return spec.telegraphTicks() > 0 ? spec.telegraphTicks() : fallback;
    }

    // ---------------------------------------------------------------- geometry helpers

    /** Top of the first solid block below {@code from} (up to 40 blocks), or {@code from} itself. */
    static Location ground(World world, Location from) {
        Location probe = from.clone();
        int minY = world.getMinHeight();
        for (int i = 0; i < 40 && probe.getBlockY() > minY; i++) {
            Block block = probe.getBlock();
            if (!block.isPassable() || block.isLiquid()) {
                return new Location(world, from.getX(), block.getY() + 1.05, from.getZ());
            }
            probe.subtract(0, 1, 0);
        }
        return from.clone();
    }

    static Location randomSpot(BossSession s, double minR, double maxR) {
        double angle = s.random.nextDouble() * Math.PI * 2;
        double r = minR + s.random.nextDouble() * Math.max(0.1, maxR - minR);
        Location top = s.anchor().add(Math.cos(angle) * r, 6, Math.sin(angle) * r);
        return ground(s.world, top);
    }

    /** Distance from a point to a segment. */
    static double segmentDistance(Vector p, Vector a, Vector b) {
        Vector ab = b.clone().subtract(a);
        double len2 = ab.lengthSquared();
        if (len2 < 1e-9) {
            return p.distance(a);
        }
        double t = Math.max(0, Math.min(1, p.clone().subtract(a).dot(ab) / len2));
        return p.distance(a.clone().add(ab.multiply(t)));
    }

    private static Location bossCenter(BossSession s) {
        Location at = s.location();
        return at.add(0, s.entity().getHeight() / 2, 0);
    }

    private static boolean onGround(Player p, double clearance) {
        if (p.isOnGround()) {
            return true;
        }
        Location below = ground(p.getWorld(), p.getLocation());
        return p.getLocation().getY() - below.getY() < clearance;
    }

    // ---------------------------------------------------------------- fireball barrage

    private static int fireballs(BossSession s, AbilitySpec spec) {
        int count = Math.min(12, Math.max(1, spec.integer("count", 5)));
        double speed = spec.num("speed", 0.9);
        double radius = spec.num("radius", 2.6);
        double homing = spec.num("homing", 0.07);
        int tele = tele(spec, 20);
        Fx.sound(s.world, s.location(), Sound.ENTITY_BLAZE_SHOOT, 3f, 0.5f);
        Fx.particle(s.world, Particle.FLAME, bossCenter(s), 40, 1.2, 1.2, 1.2, 0.05);
        for (int i = 0; i < count; i++) {
            s.later(tele + i * 5L, () -> {
                List<Player> targets = s.targets(spec.target(), 1);
                if (targets.isEmpty()) {
                    return;
                }
                launchMissile(s, spec, targets.get(0), speed, radius, homing);
            });
        }
        return tele + count * 5 + 30;
    }

    private static void launchMissile(BossSession s, AbilitySpec spec, Player target, double speed, double radius,
            double homing) {
        Location start = bossCenter(s);
        Vector pos = start.toVector();
        Vector dir = target.getEyeLocation().toVector().subtract(pos).normalize();
        dir.add(new Vector((s.random.nextDouble() - 0.5) * 0.3, (s.random.nextDouble() - 0.5) * 0.2,
            (s.random.nextDouble() - 0.5) * 0.3)).normalize();
        Fx.sound(s.world, start, Sound.ENTITY_GHAST_SHOOT, 2f, 0.9f);
        s.everyWhile(1, 1, 80, step -> {
            if (target.isOnline() && target.getWorld().equals(s.world)) {
                Vector want = target.getEyeLocation().toVector().subtract(pos).normalize();
                dir.add(want.subtract(dir).multiply(homing)).normalize();
            }
            pos.add(dir.clone().multiply(speed));
            Location at = pos.toLocation(s.world);
            Fx.particle(s.world, Particle.FLAME, at, 4, 0.1, 0.1, 0.1, 0.01);
            Fx.particle(s.world, Particle.SMOKE, at, 2, 0.1, 0.1, 0.1, 0.01);
            boolean blocked = !at.getBlock().isPassable();
            boolean touched = false;
            for (Player p : s.players()) {
                if (p.getEyeLocation().distanceSquared(at) < 1.8 * 1.8 || p.getLocation().distanceSquared(at) < 1.6 * 1.6) {
                    touched = true;
                    break;
                }
            }
            if (blocked || touched || step >= 79) {
                explode(s, spec, at, radius);
                return false;
            }
            return true;
        });
    }

    private static void explode(BossSession s, AbilitySpec spec, Location at, double radius) {
        Fx.particle(s.world, Particle.EXPLOSION, at, 2, 0.3, 0.3, 0.3, 0);
        Fx.particle(s.world, Particle.FLAME, at, 40, radius / 2, radius / 3, radius / 2, 0.1);
        Fx.sound(s.world, at, Sound.ENTITY_GENERIC_EXPLODE, 1.5f, 1.2f);
        for (Player p : s.players()) {
            if (p.getLocation().add(0, 1, 0).distanceSquared(at) <= radius * radius) {
                s.hit(p, spec.damage());
                p.setFireTicks(Math.max(p.getFireTicks(), 40));
            }
        }
    }

    // ---------------------------------------------------------------- ground slam

    private static int groundSlam(BossSession s, AbilitySpec spec) {
        double radius = spec.num("radius", 14);
        double speed = Math.max(0.5, spec.num("speed", 1.1));
        double clearance = spec.num("jump-clearance", 1.0);
        int tele = tele(spec, 30);
        Location center = ground(s.world, s.location());
        s.entity().setVelocity(s.entity().getVelocity().setY(0.85));
        Fx.sound(s.world, center, Sound.ENTITY_IRON_GOLEM_ATTACK, 3f, 0.5f);
        s.every(0, 5, Math.max(1, tele / 5), step -> {
            Fx.dustCircle(s.world, center, radius, Color.RED, 1.4f, 48);
            Fx.dustCircle(s.world, center, radius * (step + 1) / Math.max(1, tele / 5.0), Color.ORANGE, 1.0f, 32);
        }, () -> {
            Fx.sound(s.world, center, Sound.BLOCK_ANVIL_LAND, 3f, 0.5f);
            Fx.particle(s.world, Particle.EXPLOSION_EMITTER, center, 1, 0, 0, 0, 0);
            Set<UUID> hit = new HashSet<>();
            int steps = (int) Math.ceil(radius / speed);
            s.every(1, 2, steps, i -> {
                double r = (i + 1) * speed;
                Fx.circle(s.world, center.clone().add(0, 0.2, 0), r, Particle.CLOUD, (int) Math.min(120, r * 8));
                Fx.circle(s.world, center.clone().add(0, 0.3, 0), r, Particle.CRIT, (int) Math.min(80, r * 5));
                for (Player p : s.players()) {
                    if (hit.contains(p.getUniqueId())) {
                        continue;
                    }
                    double d = Fx.flat(p.getLocation(), center);
                    if (Math.abs(d - r) <= 1.4 && onGround(p, clearance)) {
                        hit.add(p.getUniqueId());
                        s.hit(p, spec.damage());
                        Vector out = p.getLocation().toVector().subtract(center.toVector()).setY(0);
                        if (out.lengthSquared() > 0.01) {
                            Fx.push(p, out.normalize().multiply(0.5).setY(0.55), 0.8, 0.6);
                        }
                    }
                }
            }, null);
        });
        return tele + (int) (radius / speed) * 2 + 20;
    }

    // ---------------------------------------------------------------- minions

    private static int summon(BossSession s, AbilitySpec spec) {
        EntityType type;
        try {
            type = EntityType.valueOf(spec.str("entity", "ZOMBIE").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            type = EntityType.ZOMBIE;
        }
        EntityType minionType = type;
        int count = Math.min(10, Math.max(1, spec.integer("count", 4)));
        double health = spec.num("health", 40);
        double spread = spec.num("radius", 6);
        String name = spec.str("name", "<gray>Minion");
        String weapon = spec.str("weapon", "");
        int tele = tele(spec, 25);
        Location center = s.location();
        Fx.sound(s.world, center, Sound.ENTITY_EVOKER_PREPARE_SUMMON, 2f, 0.8f);
        s.every(0, 5, Math.max(1, tele / 5), step ->
            Fx.circle(s.world, ground(s.world, center), spread, Particle.SOUL_FIRE_FLAME, 40), () -> {
            for (int i = 0; i < count; i++) {
                double angle = 2 * Math.PI * i / count;
                Location at = ground(s.world, center.clone().add(Math.cos(angle) * spread, 2, Math.sin(angle) * spread));
                LivingEntity minion = s.spawnMinion(at, minionType, name, health, spec.damage());
                if (minion == null) {
                    break;
                }
                if (!weapon.isEmpty() && minion.getEquipment() != null) {
                    Material m = Material.matchMaterial(weapon);
                    if (m != null && m.isItem()) {
                        minion.getEquipment().setItemInMainHand(new ItemStack(m));
                    }
                }
                Fx.particle(s.world, Particle.SOUL, at.clone().add(0, 1, 0), 25, 0.3, 0.6, 0.3, 0.05);
            }
            Fx.sound(s.world, center, Sound.ENTITY_EVOKER_CAST_SPELL, 2f, 0.8f);
        });
        return tele + 40;
    }

    // ---------------------------------------------------------------- lightning

    private static int lightning(BossSession s, AbilitySpec spec) {
        int count = Math.min(10, Math.max(1, spec.integer("count", 4)));
        double radius = spec.num("radius", 2.8);
        int tele = tele(spec, 35);
        List<Player> targets = s.targets(spec.target(), count);
        List<Location> marks = new ArrayList<>();
        for (Player p : targets) {
            marks.add(ground(s.world, p.getLocation()));
        }
        while (marks.size() < count && !targets.isEmpty()) {
            marks.add(ground(s.world, targets.get(s.random.nextInt(targets.size())).getLocation().add(
                (s.random.nextDouble() - 0.5) * 10, 0, (s.random.nextDouble() - 0.5) * 10)));
        }
        if (marks.isEmpty()) {
            return 20;
        }
        Fx.sound(s.world, s.location(), Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 2f, 1.5f);
        s.every(0, 4, Math.max(1, tele / 4), step -> {
            for (Location mark : marks) {
                Fx.dustCircle(s.world, mark.clone().add(0, 0.1, 0), radius, Color.YELLOW, 1.2f, 24);
                Fx.particle(s.world, Particle.ELECTRIC_SPARK, mark.clone().add(0, 0.5, 0), 4, radius / 3, 0.4, radius / 3, 0.1);
            }
        }, () -> {
            for (Location mark : marks) {
                s.world.strikeLightningEffect(mark);
                Fx.particle(s.world, Particle.ELECTRIC_SPARK, mark.clone().add(0, 1, 0), 60, radius / 2, 1.5, radius / 2, 0.3);
                for (Player p : s.players()) {
                    if (p.getLocation().distanceSquared(mark) <= radius * radius) {
                        s.hit(p, spec.damage());
                    }
                }
            }
        });
        return tele + 25;
    }

    // ---------------------------------------------------------------- vacuum and whirlpool

    private static int pull(BossSession s, AbilitySpec spec, boolean whirl) {
        double radius = spec.num("radius", whirl ? 22 : 24);
        double strength = spec.num("strength", whirl ? 0.22 : 0.3);
        double spin = whirl ? spec.num("spin", 0.28) : 0;
        double blast = spec.num("blast-radius", whirl ? 7 : 6);
        int duration = spec.seconds("duration", 3.5);
        int tele = tele(spec, 20);
        Location center = s.location();
        Particle swirl = whirl ? Particle.BUBBLE : Particle.PORTAL;
        Fx.sound(s.world, center, whirl ? Sound.ENTITY_ELDER_GUARDIAN_CURSE : Sound.ENTITY_ENDERMAN_STARE, 3f, 0.5f);
        s.later(tele, () -> s.every(0, 2, Math.max(1, duration / 2), step -> {
            Location core = s.location();
            double angle = step * 0.5;
            for (int arm = 0; arm < 3; arm++) {
                for (double r = radius; r > 1; r -= 3.5) {
                    double a = angle + arm * 2.1 + r * 0.25;
                    Fx.particle(s.world, swirl, core.clone().add(Math.cos(a) * r, 0.6 + (radius - r) * 0.05, Math.sin(a) * r),
                        2, 0.1, 0.1, 0.1, 0);
                }
            }
            Fx.circle(s.world, ground(s.world, core), blast, Particle.SMOKE, 24);
            for (Player p : s.players()) {
                Vector to = core.toVector().subtract(p.getLocation().toVector()).setY(0);
                double dist = to.length();
                if (dist > radius || dist < 1.5) {
                    continue;
                }
                Vector push = to.normalize().multiply(strength);
                if (whirl) {
                    push.add(new Vector(-to.getZ(), 0, to.getX()).multiply(spin));
                }
                Fx.push(p, p.getVelocity().multiply(0.6).add(push), 0.9, 0.2);
            }
        }, () -> {
            Location core = s.location();
            Fx.particle(s.world, Particle.EXPLOSION_EMITTER, core, 2, 1, 1, 1, 0);
            Fx.sound(s.world, core, Sound.ENTITY_GENERIC_EXPLODE, 3f, 0.6f);
            Fx.circle(s.world, ground(s.world, core), blast, Particle.FLAME, 60);
            for (Player p : s.players()) {
                if (Fx.flat(p.getLocation(), core) <= blast) {
                    s.hit(p, spec.damage());
                    if (whirl) {
                        p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 1));
                    }
                }
            }
        }));
        return tele + duration + 30;
    }

    // ---------------------------------------------------------------- zones

    /** Preset of the lingering zone and eruption abilities. */
    record Zone(boolean eruption, double radius, int count, double seconds, double teleSeconds, double dmg,
            PotionEffectType effect, int effectTicks, int amp, boolean fire, int freeze, Particle particle,
            Color color, Sound sound) {
        static Zone of(AbilityType type) {
            return switch (type) {
                case POISON_CLOUD -> new Zone(false, 4, 3, 9, 1.2, 0.16, PotionEffectType.POISON, 60, 1, false, 0,
                    Particle.SNEEZE, Color.fromRGB(80, 200, 60), Sound.ENTITY_SPLASH_POTION_BREAK);
                case WITHER_CLOUD -> new Zone(false, 4, 3, 9, 1.2, 0.18, PotionEffectType.WITHER, 60, 1, false, 0,
                    Particle.SMOKE, Color.fromRGB(40, 40, 40), Sound.ENTITY_WITHER_SHOOT);
                case BREATH_CLOUD -> new Zone(false, 5, 3, 10, 1.4, 0.2, PotionEffectType.WITHER, 40, 0, false, 0,
                    Particle.DRAGON_BREATH, Color.fromRGB(190, 60, 220), Sound.ENTITY_ENDER_DRAGON_SHOOT);
                case LAVA_POOLS -> new Zone(false, 3.5, 4, 12, 1.5, 0.22, PotionEffectType.SLOWNESS, 20, 0, true, 0,
                    Particle.LAVA, Color.fromRGB(255, 90, 0), Sound.BLOCK_LAVA_POP);
                case VOID_ZONES -> new Zone(false, 4, 3, 10, 1.5, 0.2, PotionEffectType.BLINDNESS, 50, 0, false, 0,
                    Particle.REVERSE_PORTAL, Color.fromRGB(110, 0, 170), Sound.BLOCK_PORTAL_TRIGGER);
                case ICE_SPIKES -> new Zone(true, 3.2, 6, 0, 1.6, 1.6, PotionEffectType.SLOWNESS, 80, 2, false, 160,
                    Particle.SNOWFLAKE, Color.fromRGB(150, 230, 255), Sound.BLOCK_GLASS_BREAK);
                case WEB_TRAPS -> new Zone(false, 2.6, 4, 3.5, 1.2, 0.08, PotionEffectType.SLOWNESS, 30, 6, false, 0,
                    Particle.ITEM_COBWEB, Color.WHITE, Sound.ENTITY_SPIDER_AMBIENT);
                default -> throw new IllegalArgumentException(type.name());
            };
        }
    }

    private static int zones(BossSession s, AbilitySpec spec, Zone z) {
        double radius = spec.num("radius", z.radius());
        int count = Math.min(10, Math.max(1, spec.integer("count", z.count())));
        int duration = spec.seconds("duration", z.seconds());
        int tele = tele(spec, (int) Math.round(z.teleSeconds() * 20));
        String place = spec.str("place", "target");
        List<Location> spots = new ArrayList<>();
        if (place.equals("target")) {
            List<Player> picked = s.targets(spec.target(), count);
            for (Player p : picked) {
                spots.add(ground(s.world, p.getLocation()));
            }
        }
        while (spots.size() < count) {
            if (place.equals("ring")) {
                spots.add(ground(s.world, s.location().add(Math.cos(spots.size() * 2.4) * 8, 3, Math.sin(spots.size() * 2.4) * 8)));
            } else {
                spots.add(randomSpot(s, 4, Math.max(6, s.def.arena().radius() * 0.7)));
            }
        }
        Fx.sound(s.world, s.location(), z.sound(), 3f, 0.7f);
        s.every(0, 5, Math.max(1, tele / 5), step -> {
            for (Location spot : spots) {
                Fx.dustCircle(s.world, spot.clone().add(0, 0.1, 0), radius, z.color(), 1.5f, 28);
            }
        }, () -> {
            if (z.eruption()) {
                erupt(s, spec, z, spots, radius);
            } else {
                linger(s, spec, z, spots, radius, duration);
            }
        });
        return tele + (z.eruption() ? 30 : Math.min(duration, 100)) + 10;
    }

    private static void erupt(BossSession s, AbilitySpec spec, Zone z, List<Location> spots, double radius) {
        for (Location spot : spots) {
            Fx.sound(s.world, spot, z.sound(), 2f, 0.8f);
            for (double h = 0; h < 3.5; h += 0.5) {
                Fx.dust(s.world, spot.clone().add(0, h, 0), z.color(), 1.6f, 10, radius * 0.4);
                Fx.particle(s.world, z.particle(), spot.clone().add(0, h, 0), 6, radius * 0.4, 0.1, radius * 0.4, 0.02);
            }
            Fx.particle(s.world, Particle.CLOUD, spot, 20, radius / 2, 0.2, radius / 2, 0.1);
        }
        for (Player p : s.players()) {
            for (Location spot : spots) {
                if (Fx.flat(p.getLocation(), spot) <= radius && Math.abs(p.getLocation().getY() - spot.getY()) < 3) {
                    s.hit(p, spec.damage() * z.dmg());
                    p.setVelocity(new Vector(0, 0.75, 0));
                    p.addPotionEffect(new PotionEffect(z.effect(), z.effectTicks(), z.amp()));
                    if (z.freeze() > 0) {
                        p.setFreezeTicks(Math.min(p.getMaxFreezeTicks() + 40, z.freeze()));
                    }
                    break;
                }
            }
        }
    }

    private static void linger(BossSession s, AbilitySpec spec, Zone z, List<Location> spots, double radius,
            int duration) {
        s.every(0, 5, Math.max(1, duration / 5), step -> {
            for (Location spot : spots) {
                Fx.particle(s.world, z.particle(), spot.clone().add(0, 0.4, 0), 8, radius * 0.5, 0.3, radius * 0.5, 0.01);
                Fx.dust(s.world, spot.clone().add(0, 0.5, 0), z.color(), 1.6f, 6, radius * 0.5);
                Fx.circle(s.world, spot.clone().add(0, 0.1, 0), radius, z.particle() == Particle.LAVA ? Particle.FLAME : Particle.CLOUD, 20);
            }
            if (step % 2 != 0) {
                return;
            }
            for (Player p : s.players()) {
                for (Location spot : spots) {
                    if (Fx.flat(p.getLocation(), spot) <= radius && Math.abs(p.getLocation().getY() - spot.getY()) < 3) {
                        s.hit(p, spec.damage() * z.dmg());
                        p.addPotionEffect(new PotionEffect(z.effect(), z.effectTicks(), z.amp(), true, true, true));
                        if (z.fire()) {
                            p.setFireTicks(Math.max(p.getFireTicks(), 60));
                        }
                        break;
                    }
                }
            }
        }, null);
    }

    // ---------------------------------------------------------------- teleport behind

    private static int teleportBehind(BossSession s, AbilitySpec spec) {
        List<Player> targets = s.targets(spec.target(), 1);
        if (targets.isEmpty()) {
            return 20;
        }
        Player target = targets.get(0);
        int tele = tele(spec, 20);
        double radius = spec.num("radius", 4.5);
        Fx.sound(s.world, s.location(), Sound.ENTITY_ENDERMAN_STARE, 3f, 0.7f);
        s.every(0, 4, Math.max(1, tele / 4), step -> {
            Fx.particle(s.world, Particle.PORTAL, s.location().add(0, 1, 0), 30, 0.4, 0.8, 0.4, 0.8);
            Fx.dustLine(s.world, s.location().add(0, 1, 0), target.getLocation().add(0, 1, 0), Color.PURPLE, 1.0f, 0.8);
        }, () -> {
            if (!target.isOnline() || !target.getWorld().equals(s.world)) {
                return;
            }
            Vector facing = target.getLocation().getDirection().setY(0);
            if (facing.lengthSquared() < 0.01) {
                facing = new Vector(0, 0, 1);
            }
            facing.normalize();
            Location behind = target.getLocation().subtract(facing.clone().multiply(2.2));
            if (!behind.getBlock().isPassable() || !behind.clone().add(0, 1, 0).getBlock().isPassable()) {
                behind = target.getLocation().add(facing.clone().multiply(2.2));
            }
            behind.setDirection(facing);
            Fx.particle(s.world, Particle.PORTAL, s.location().add(0, 1, 0), 60, 0.5, 1, 0.5, 1);
            s.entity().teleport(behind);
            Fx.particle(s.world, Particle.PORTAL, behind.clone().add(0, 1, 0), 60, 0.5, 1, 0.5, 1);
            Fx.sound(s.world, behind, Sound.ENTITY_ENDERMAN_TELEPORT, 2f, 0.6f);
            Location strike = behind.clone();
            s.later(12, () -> {
                Fx.particle(s.world, Particle.SWEEP_ATTACK, strike.clone().add(0, 1, 0), 6, 1.5, 0.3, 1.5, 0);
                Fx.sound(s.world, strike, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 2f, 0.6f);
                for (Player p : s.players()) {
                    if (p.getLocation().distanceSquared(s.location()) <= radius * radius) {
                        s.hit(p, spec.damage());
                        Vector away = p.getLocation().toVector().subtract(s.location().toVector()).setY(0);
                        if (away.lengthSquared() > 0.01) {
                            Fx.push(p, away.normalize().multiply(0.7).setY(0.3), 0.9, 0.4);
                        }
                    }
                }
            });
        });
        return tele + 40;
    }

    // ---------------------------------------------------------------- shield

    private static int shield(BossSession s, AbilitySpec spec) {
        s.startShield(Math.min(8, Math.max(1, spec.integer("count", 4))), spec.num("radius", 14),
            spec.num("height", 5), Math.max(1, spec.integer("hits", 1)), spec.integer("seconds", 40),
            spec.num("heal", 0.004), spec.num("nova", 6.0));
        return 40;
    }

    // ---------------------------------------------------------------- laser

    private static int laser(BossSession s, AbilitySpec spec) {
        List<Player> targets = s.targets(spec.target(), 1);
        if (targets.isEmpty()) {
            return 20;
        }
        Player target = targets.get(0);
        double length = spec.num("length", 34);
        double width = spec.num("width", 1.3);
        double sweep = Math.toRadians(spec.num("sweep", 4.0));
        int duration = spec.seconds("duration", 4.0);
        int tele = tele(spec, 30);
        double[] yaw = {0};
        int[] sign = {s.random.nextBoolean() ? 1 : -1};
        Vector[] dir = {target.getEyeLocation().toVector().subtract(bossCenter(s).toVector()).setY(0).normalize()};
        Fx.sound(s.world, s.location(), Sound.BLOCK_BEACON_POWER_SELECT, 3f, 0.5f);
        s.every(0, 2, Math.max(1, tele / 2), step -> {
            Location from = bossCenter(s);
            if (target.isOnline() && target.getWorld().equals(s.world)) {
                dir[0] = target.getLocation().toVector().subtract(from.toVector()).setY(0);
                if (dir[0].lengthSquared() < 0.01) {
                    dir[0] = new Vector(1, 0, 0);
                }
                dir[0].normalize();
            }
            Fx.dustLine(s.world, from, from.clone().add(dir[0].clone().multiply(length)), Color.RED, 0.7f, 1.0);
        }, () -> {
            Fx.sound(s.world, s.location(), Sound.BLOCK_BEACON_ACTIVATE, 3f, 1.8f);
            s.every(0, 2, Math.max(1, duration / 2), step -> {
                Location from = bossCenter(s);
                yaw[0] += sweep * sign[0];
                Vector d = dir[0].clone().rotateAroundY(yaw[0]);
                Location end = from.clone().add(d.clone().multiply(length));
                Fx.line(s.world, from, end, Particle.END_ROD, 0.9);
                Fx.dustLine(s.world, from, end, Color.fromRGB(255, 60, 60), 1.6f, 0.9);
                if (step % 2 == 0) {
                    for (Player p : s.players()) {
                        Vector point = p.getLocation().add(0, 1, 0).toVector();
                        if (segmentDistance(point, from.toVector(), end.toVector()) <= width) {
                            s.hit(p, spec.damage() * 0.4);
                        }
                    }
                }
            }, null);
        });
        return tele + duration + 20;
    }

    // ---------------------------------------------------------------- charge

    private static int charge(BossSession s, AbilitySpec spec) {
        List<Player> targets = s.targets(spec.target(), 1);
        if (targets.isEmpty()) {
            return 20;
        }
        Player target = targets.get(0);
        double distance = spec.num("distance", 26);
        double speed = Math.max(0.6, spec.num("speed", 1.5));
        double width = spec.num("width", 2.4);
        boolean flat = spec.bool("flat", !(s.entity() instanceof EnderDragon));
        int tele = tele(spec, 25);
        Vector[] dir = {new Vector(1, 0, 0)};
        Fx.sound(s.world, s.location(), Sound.ENTITY_RAVAGER_ROAR, 3f, 0.7f);
        s.every(0, 3, Math.max(1, tele / 3), step -> {
            Location from = bossCenter(s);
            Vector d = target.getLocation().add(0, 1, 0).toVector().subtract(from.toVector());
            if (flat) {
                d.setY(0);
            }
            if (d.lengthSquared() > 0.01) {
                dir[0] = d.normalize();
            }
            Fx.dustLine(s.world, from, from.clone().add(dir[0].clone().multiply(distance)), Color.RED, 1.2f, 0.8);
        }, () -> {
            Set<UUID> hit = new HashSet<>();
            int steps = (int) Math.ceil(distance / speed);
            Fx.sound(s.world, s.location(), Sound.ENTITY_PHANTOM_SWOOP, 3f, 0.6f);
            s.every(0, 1, steps, step -> {
                s.entity().setVelocity(dir[0].clone().multiply(speed).setY(flat ? Math.max(0.05, s.entity().getVelocity().getY()) : dir[0].getY() * speed));
                Location at = bossCenter(s);
                Fx.particle(s.world, Particle.CLOUD, at, 4, 0.4, 0.4, 0.4, 0.02);
                for (Player p : s.players()) {
                    if (!hit.contains(p.getUniqueId()) && p.getLocation().add(0, 1, 0).distanceSquared(at) <= width * width) {
                        hit.add(p.getUniqueId());
                        s.hit(p, spec.damage());
                        Fx.push(p, dir[0].clone().multiply(1.0).setY(0.4), 1.2, 0.5);
                    }
                }
            }, null);
        });
        return tele + (int) (distance / speed) + 30;
    }

    // ---------------------------------------------------------------- dive bomb

    private static int dive(BossSession s, AbilitySpec spec) {
        List<Player> targets = s.targets(spec.target(), 1);
        if (targets.isEmpty()) {
            return 20;
        }
        Player target = targets.get(0);
        double radius = spec.num("radius", 6.5);
        int tele = tele(spec, 40);
        Location[] mark = {ground(s.world, target.getLocation())};
        Fx.sound(s.world, s.location(), Sound.ENTITY_ENDER_DRAGON_GROWL, 4f, 1.2f);
        s.every(0, 4, Math.max(1, tele / 4), step -> {
            if (step < tele / 4 - 3 && target.isOnline() && target.getWorld().equals(s.world)) {
                mark[0] = ground(s.world, target.getLocation());
            }
            Fx.dustCircle(s.world, mark[0].clone().add(0, 0.1, 0), radius, Color.RED, 1.6f, 36);
            Fx.dustLine(s.world, s.location(), mark[0], Color.fromRGB(255, 120, 120), 0.8f, 1.2);
        }, () -> {
            Location end = mark[0].clone();
            if (s.entity() instanceof EnderDragon dragon) {
                try {
                    dragon.setPhase(EnderDragon.Phase.CHARGE_PLAYER);
                } catch (RuntimeException ignored) {
                    // the dive is still resolved at the mark below
                }
            }
            Fx.sound(s.world, s.location(), Sound.ENTITY_PHANTOM_SWOOP, 4f, 0.5f);
            s.every(0, 1, 28, step -> {
                Vector to = end.toVector().add(new Vector(0, 2, 0)).subtract(s.location().toVector());
                if (to.lengthSquared() > 4) {
                    s.entity().setVelocity(to.normalize().multiply(Math.min(2.2, 0.8 + step * 0.06)));
                }
                Fx.particle(s.world, Particle.CLOUD, s.location(), 5, 1, 1, 1, 0.05);
            }, () -> {
                Fx.particle(s.world, Particle.EXPLOSION_EMITTER, end, 2, 1, 0.5, 1, 0);
                Fx.circle(s.world, end.clone().add(0, 0.3, 0), radius, Particle.FLAME, 80);
                Fx.sound(s.world, end, Sound.ENTITY_GENERIC_EXPLODE, 4f, 0.5f);
                for (Player p : s.players()) {
                    if (Fx.flat(p.getLocation(), end) <= radius && onGround(p, 1.8)) {
                        s.hit(p, spec.damage());
                        Vector out = p.getLocation().toVector().subtract(end.toVector()).setY(0);
                        if (out.lengthSquared() > 0.01) {
                            Fx.push(p, out.normalize().multiply(0.8).setY(0.5), 1.0, 0.6);
                        }
                    }
                }
                if (s.entity() instanceof EnderDragon dragon) {
                    try {
                        dragon.setPhase(EnderDragon.Phase.CIRCLING);
                    } catch (RuntimeException ignored) {
                        // nothing to restore
                    }
                }
            });
        });
        return tele + 28 + 40;
    }

    // ---------------------------------------------------------------- tidal wave

    private static int tidalWave(BossSession s, AbilitySpec spec) {
        double reach = s.def.arena().radius() * 0.9;
        double halfWidth = spec.num("width", 44) / 2;
        double height = spec.num("height", 3.2);
        double speed = Math.max(0.6, spec.num("speed", 1.1));
        double gapWidth = spec.num("gap", 7);
        int tele = tele(spec, 50);
        double angle = s.random.nextDouble() * Math.PI * 2;
        Vector dir = new Vector(-Math.cos(angle), 0, -Math.sin(angle));
        Vector side = new Vector(-dir.getZ(), 0, dir.getX());
        Location start = s.anchor().add(Math.cos(angle) * reach, 0, Math.sin(angle) * reach);
        double gapCenter = (s.random.nextDouble() - 0.5) * (halfWidth * 1.2);
        Fx.sound(s.world, s.location(), Sound.ENTITY_ELDER_GUARDIAN_CURSE, 3f, 0.5f);
        s.broadcastNearby(net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(
            "<aqua><bold>A tidal wave is coming! <gray>Run for the gap in the wall."));
        s.every(0, 5, Math.max(1, tele / 5), step -> {
            for (double w = -halfWidth; w <= halfWidth; w += 2.5) {
                if (Math.abs(w - gapCenter) < gapWidth / 2) {
                    Fx.dust(s.world, ground(s.world, start.clone().add(side.clone().multiply(w)).add(0, 4, 0)), Color.LIME, 1.4f, 2, 0.1);
                } else {
                    Fx.dust(s.world, ground(s.world, start.clone().add(side.clone().multiply(w)).add(0, 4, 0)), Color.AQUA, 1.2f, 1, 0.1);
                }
            }
        }, () -> {
            Set<UUID> hit = new HashSet<>();
            int steps = (int) Math.ceil(reach * 2 / speed);
            s.every(0, 2, steps / 2 + 1, step -> {
                double travelled = step * 2 * speed;
                Location front = start.clone().add(dir.clone().multiply(travelled));
                for (double w = -halfWidth; w <= halfWidth; w += 1.6) {
                    if (Math.abs(w - gapCenter) < gapWidth / 2) {
                        continue;
                    }
                    Location col = front.clone().add(side.clone().multiply(w));
                    Location base = ground(s.world, col.clone().add(0, 4, 0));
                    for (double h = 0; h <= height; h += 1.0) {
                        Fx.particle(s.world, h >= height ? Particle.SPLASH : Particle.FALLING_WATER, base.clone().add(0, h, 0), 3, 0.3, 0.1, 0.3, 0);
                    }
                }
                for (Player p : s.players()) {
                    if (hit.contains(p.getUniqueId())) {
                        continue;
                    }
                    Vector rel = p.getLocation().toVector().subtract(front.toVector());
                    double along = rel.dot(dir);
                    double across = rel.dot(side);
                    if (Math.abs(along) <= 1.8 && Math.abs(across) <= halfWidth
                        && Math.abs(across - gapCenter) >= gapWidth / 2) {
                        Location base = ground(s.world, p.getLocation());
                        if (p.getLocation().getY() - base.getY() <= height) {
                            hit.add(p.getUniqueId());
                            s.hit(p, spec.damage());
                            Fx.push(p, dir.clone().multiply(0.9).setY(0.35), 1.0, 0.45);
                        }
                    }
                }
            }, null);
        });
        return tele + (int) (reach * 2 / speed) + 20;
    }

    // ---------------------------------------------------------------- fire ring

    private static int fireRing(BossSession s, AbilitySpec spec) {
        double max = spec.num("max-radius", Math.min(28, s.def.arena().radius()));
        double speed = Math.max(0.4, spec.num("speed", 0.9));
        double gapDeg = spec.num("gap", 55);
        int tele = tele(spec, 30);
        double gapCenter = s.random.nextDouble() * Math.PI * 2;
        double half = Math.toRadians(gapDeg) / 2;
        Location center = ground(s.world, s.location());
        Fx.sound(s.world, center, Sound.ENTITY_BLAZE_SHOOT, 3f, 0.5f);
        s.every(0, 5, Math.max(1, tele / 5), step -> {
            Vector g = new Vector(Math.cos(gapCenter), 0, Math.sin(gapCenter));
            Fx.dustLine(s.world, center.clone().add(0, 0.2, 0), center.clone().add(g.multiply(max)), Color.LIME, 1.4f, 0.8);
            Fx.dustCircle(s.world, center.clone().add(0, 0.1, 0), 3, Color.ORANGE, 1.4f, 20);
        }, () -> {
            Set<UUID> hit = new HashSet<>();
            int steps = (int) Math.ceil(max / speed);
            s.every(0, 2, steps, i -> {
                double r = (i + 1) * speed;
                int points = (int) Math.min(160, Math.max(24, r * 8));
                for (int k = 0; k < points; k++) {
                    double a = 2 * Math.PI * k / points;
                    if (angleDiff(a, gapCenter) < half) {
                        continue;
                    }
                    Location pt = center.clone().add(Math.cos(a) * r, 0.2, Math.sin(a) * r);
                    Fx.particle(s.world, Particle.FLAME, pt, 1, 0.05, 0.3, 0.05, 0.01);
                }
                for (Player p : s.players()) {
                    if (hit.contains(p.getUniqueId())) {
                        continue;
                    }
                    Location pl = p.getLocation();
                    double dist = Fx.flat(pl, center);
                    double a = Math.atan2(pl.getZ() - center.getZ(), pl.getX() - center.getX());
                    if (Math.abs(dist - r) <= 1.3 && angleDiff(a, gapCenter) >= half && onGround(p, 1.1)) {
                        hit.add(p.getUniqueId());
                        s.hit(p, spec.damage());
                        p.setFireTicks(Math.max(p.getFireTicks(), 60));
                    }
                }
            }, null);
        });
        return tele + steps(max, speed) * 2 + 20;
    }

    private static int steps(double max, double speed) {
        return (int) Math.ceil(max / speed);
    }

    private static double angleDiff(double a, double b) {
        double d = Math.abs(a - b) % (2 * Math.PI);
        return d > Math.PI ? 2 * Math.PI - d : d;
    }

    // ---------------------------------------------------------------- bone cage

    private static int boneCage(BossSession s, AbilitySpec spec) {
        List<Player> targets = s.targets(spec.target(), Math.max(1, spec.integer("count", 1)));
        if (targets.isEmpty()) {
            return 20;
        }
        double radius = spec.num("radius", 3.4);
        int tele = tele(spec, 30);
        int duration = spec.seconds("duration", 5);
        List<Location> centers = new ArrayList<>();
        targets.forEach(t -> centers.add(ground(s.world, t.getLocation())));
        Fx.sound(s.world, s.location(), Sound.ENTITY_SKELETON_AMBIENT, 3f, 0.4f);
        s.every(0, 5, Math.max(1, tele / 5), step -> centers.forEach(c ->
            Fx.dustCircle(s.world, c.clone().add(0, 0.1, 0), radius, Color.GRAY, 1.6f, 32)), () -> {
            Fx.sound(s.world, centers.get(0), Sound.ENTITY_SKELETON_HORSE_DEATH, 3f, 0.5f);
            Set<UUID> caged = new HashSet<>();
            for (Player p : s.players()) {
                for (Location c : centers) {
                    if (Fx.flat(p.getLocation(), c) <= radius) {
                        caged.add(p.getUniqueId());
                        s.hit(p, spec.damage());
                    }
                }
            }
            s.every(0, 4, Math.max(1, duration / 4), step -> {
                for (Location c : centers) {
                    for (double h = 0; h <= 3; h += 0.75) {
                        for (Location bar : Fx.circlePoints(c.clone().add(0, h, 0), radius, 10)) {
                            Fx.dust(s.world, bar, Color.WHITE, 1.3f, 1, 0);
                        }
                    }
                    Fx.circle(s.world, c.clone().add(0, 3.2, 0), radius, Particle.CRIT, 16);
                }
                for (Player p : s.players()) {
                    if (!caged.contains(p.getUniqueId())) {
                        continue;
                    }
                    for (Location c : centers) {
                        double d = Fx.flat(p.getLocation(), c);
                        if (d > radius - 0.5 && d < radius + 2.5) {
                            Vector in = c.toVector().subtract(p.getLocation().toVector()).setY(0);
                            if (in.lengthSquared() > 0.01) {
                                Fx.push(p, in.normalize().multiply(0.55).setY(0.1), 0.8, 0.3);
                            }
                            if (step % 4 == 0) {
                                s.hit(p, spec.damage() * 0.25);
                            }
                            break;
                        }
                    }
                }
            }, null);
        });
        return tele + duration + 20;
    }

    // ---------------------------------------------------------------- soul drain

    private static int soulDrain(BossSession s, AbilitySpec spec) {
        List<Player> targets = s.targets(spec.target(), 1);
        if (targets.isEmpty()) {
            return 20;
        }
        Player target = targets.get(0);
        double range = spec.num("range", 30);
        double heal = spec.num("heal", 0.0025);
        int duration = spec.seconds("duration", 6);
        int tele = tele(spec, 20);
        Fx.sound(s.world, s.location(), Sound.ENTITY_WARDEN_SONIC_BOOM, 2f, 1.6f);
        s.every(0, 4, Math.max(1, tele / 4), step ->
            Fx.dustLine(s.world, bossCenter(s), target.getLocation().add(0, 1, 0), Color.TEAL, 0.8f, 1.0), () ->
            s.every(0, 4, Math.max(1, duration / 4), step -> {
                Location from = bossCenter(s);
                Player victim = target;
                if (!Fx.targetable(victim) || victim.getLocation().distance(from) > range) {
                    return;
                }
                Vector a = from.toVector();
                Vector b = victim.getLocation().add(0, 1, 0).toVector();
                for (Player other : s.players()) {
                    if (other != target && segmentDistance(other.getLocation().add(0, 1, 0).toVector(), a, b) <= 1.4) {
                        victim = other;
                        b = other.getLocation().add(0, 1, 0).toVector();
                        break;
                    }
                }
                Location end = b.toLocation(s.world);
                Fx.line(s.world, from, end, Particle.SCULK_SOUL, 0.7);
                Fx.line(s.world, from, end, Particle.SOUL, 1.4);
                if (step % 2 == 0) {
                    s.hit(victim, spec.damage() * 0.3);
                    s.heal(s.maxHp * heal * 2);
                    Fx.particle(s.world, Particle.HEART, from.clone().add(0, 1.5, 0), 2, 0.5, 0.3, 0.5, 0);
                }
            }, null));
        return tele + duration + 20;
    }

    // ---------------------------------------------------------------- blizzard

    private static int blizzard(BossSession s, AbilitySpec spec) {
        int shelters = Math.min(6, Math.max(1, spec.integer("shelters", 3)));
        double shelterRadius = spec.num("shelter-radius", 4.2);
        int duration = spec.seconds("duration", 10);
        int tele = tele(spec, 40);
        List<Location> fires = new ArrayList<>();
        for (int i = 0; i < shelters; i++) {
            fires.add(randomSpot(s, 4, Math.max(8, s.def.arena().radius() * 0.6)));
        }
        Fx.sound(s.world, s.location(), Sound.ITEM_TRIDENT_THUNDER, 3f, 0.5f);
        s.broadcastNearby(net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(
            "<aqua><bold>A blizzard is rising! <gray>Stand inside a warm brazier ring or you will freeze."));
        s.every(0, 5, Math.max(1, tele / 5), step -> fires.forEach(f -> {
            Fx.dustCircle(s.world, f.clone().add(0, 0.1, 0), shelterRadius, Color.ORANGE, 1.3f, 28);
            Fx.particle(s.world, Particle.FLAME, f.clone().add(0, 0.5, 0), 6, 0.2, 0.5, 0.2, 0.01);
        }), () -> s.every(0, 5, Math.max(1, duration / 5), step -> {
            for (Location f : fires) {
                Fx.dustCircle(s.world, f.clone().add(0, 0.1, 0), shelterRadius, Color.ORANGE, 1.1f, 20);
                Fx.particle(s.world, Particle.FLAME, f.clone().add(0, 0.6, 0), 8, 0.2, 0.8, 0.2, 0.01);
                Fx.particle(s.world, Particle.SMALL_FLAME, f.clone().add(0, 1.2, 0), 4, 0.3, 0.5, 0.3, 0.01);
            }
            for (Player p : s.players()) {
                Fx.particle(s.world, Particle.SNOWFLAKE, p.getLocation().add(0, 1.5, 0), 25, 5, 2, 5, 0.1);
                boolean sheltered = false;
                for (Location f : fires) {
                    if (Fx.flat(p.getLocation(), f) <= shelterRadius) {
                        sheltered = true;
                        break;
                    }
                }
                if (sheltered) {
                    p.setFreezeTicks(0);
                    continue;
                }
                p.setFreezeTicks(Math.min(p.getMaxFreezeTicks() + 60, p.getFreezeTicks() + 50));
                p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 30, 0, true, true, true));
                if (step % 2 == 0) {
                    s.hit(p, spec.damage() * 0.35);
                }
            }
        }, null));
        return tele + duration + 20;
    }
}
