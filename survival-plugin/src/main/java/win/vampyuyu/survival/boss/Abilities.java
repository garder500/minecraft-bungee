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
            case PERCH -> perch(s, spec);
            case TIDAL_WAVE -> tidalWave(s, spec);
            case FIRE_RING -> fireRing(s, spec);
            case BONE_CAGE -> boneCage(s, spec);
            case SOUL_DRAIN -> soulDrain(s, spec);
            case BLIZZARD -> blizzard(s, spec);
            case CHAIN_ANCHORS -> anchors(s, spec);
            case TIME_ECHO -> Gravity.timeEcho(s, spec);
            case WEIGHTLESS -> Gravity.weightless(s, spec);
        };
    }

    static int tele(AbilitySpec spec, int fallback) {
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
        Fx.sfx(s.world, s.location(), "fire_burst", 3f, 1f);
        BossVfx.burst(bossCenter(s), "burst", BossVfx.EMBER, 3.0, 10);
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
        Fx.sfx(s.world, start, "fireball_launch", 2f, 1f);
        BossVfx.Handle orb = BossVfx.missile(start, "orb", BossVfx.EMBER, 1.3, 90);
        s.everyWhile(1, 1, 80, step -> {
            if (target.isOnline() && target.getWorld().equals(s.world)) {
                Vector want = target.getEyeLocation().toVector().subtract(pos).normalize();
                dir.add(want.subtract(dir).multiply(homing)).normalize();
            }
            pos.add(dir.clone().multiply(speed));
            Location at = pos.toLocation(s.world);
            orb.move(at);
            boolean blocked = !at.getBlock().isPassable();
            boolean touched = false;
            for (Player p : s.players()) {
                if (p.getEyeLocation().distanceSquared(at) < 1.8 * 1.8 || p.getLocation().distanceSquared(at) < 1.6 * 1.6) {
                    touched = true;
                    break;
                }
            }
            if (blocked || touched || step >= 79) {
                orb.end();
                explode(s, spec, at, radius);
                return false;
            }
            return true;
        });
    }

    private static void explode(BossSession s, AbilitySpec spec, Location at, double radius) {
        BossVfx.burst(at, "burst", BossVfx.EMBER, radius * 1.2, 9);
        BossVfx.burst(at, "orb", BossVfx.GOLD, radius * 0.7, 7);
        Fx.sfx(s.world, at, "explosion", 1.5f, 1f);
        for (Player p : s.players()) {
            if (p.getLocation().add(0, 1, 0).distanceSquared(at) <= radius * radius) {
                s.hit(p, spec.damage());
                BossStatus.apply(p, StatusKind.BURN, 40, 1);
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
        Fx.sfx(s.world, center, "slam_windup", 3f, 1f);
        BossVfx.decal(center, "disc", BossVfx.DANGER, radius, tele + 2, 30f);
        BossVfx.wave(center, "ring", BossVfx.GOLD, 0.5, radius, tele, 1f);   // the charge filling the disc
        s.every(0, 5, Math.max(1, tele / 5), step -> { }, () -> {
            Fx.sfx(s.world, center, "ground_slam", 3f, 1f);
            BossVfx.burst(center.clone().add(0, 1, 0), "burst", BossVfx.GOLD, 4.0, 10);
            Set<UUID> hit = new HashSet<>();
            int steps = (int) Math.ceil(radius / speed);
            // the shockwave to jump: a ring that runs out at exactly the damage speed (one step of speed every 2 ticks)
            BossVfx.wave(center, "ring", BossVfx.GOLD, speed, radius, steps * 2, 4f);
            s.every(1, 2, steps, i -> {
                double r = (i + 1) * speed;
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
        Fx.sfx(s.world, center, "summon_call", 2f, 1f);
        BossVfx.decal(ground(s.world, center), "sigil", BossVfx.SOUL, spread, tele + 2, 90f);
        s.every(0, 5, Math.max(1, tele / 5), step -> { }, () -> {
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
                BossVfx.column(at, "pillar", BossVfx.SOUL, 1.4, 3.2, 4, 8);
            }
            Fx.sfx(s.world, center, "summon_rise", 2f, 1f);
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
        Fx.sfx(s.world, s.location(), "thunder_charge", 2f, 1f);
        for (Location mark : marks) {   // the marked spots: a storm sigil that winds up until the strike
            BossVfx.decal(mark, "sigil", BossVfx.STORM, radius, tele + 2, 160f);
            BossVfx.decal(mark, "ring", BossVfx.STORM, radius, tele + 2, -60f);
        }
        s.every(0, 4, Math.max(1, tele / 4), step -> { }, () -> {
            for (Location mark : marks) {
                BossVfx.bolt(mark, BossVfx.STORM, 18, 2.6, 10);
                Fx.sfx(s.world, mark, "thunder_strike", 3f, 1f);
                BossVfx.burst(mark.clone().add(0, 0.6, 0), "burst", BossVfx.STORM, radius * 1.1, 8);
                BossVfx.wave(mark, "ring", BossVfx.STORM, 0.4, radius * 1.3, 7, 1f);
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
        Color look = whirl ? BossVfx.TIDE : BossVfx.VOID;
        Fx.sfx(s.world, center, whirl ? "abyss_call" : "void_stare", 3f, 1f);
        s.later(tele, () -> Fx.sfx(s.world, s.location(), "vortex_pull", 4f, 1f));
        BossVfx.decal(ground(s.world, center), "disc", BossVfx.DANGER, blast, tele + duration + 4, 40f);
        s.later(tele, () -> {
            // the funnel the players are dragged into, turning with the pull, and the reach of the pull on the ground
            BossVfx.spin(ground(s.world, s.location()), "vortex", look, radius * 0.35, 5, duration, Math.max(2, duration / 12), s.entity());
            BossVfx.decal(ground(s.world, s.location()), "ring", look, radius, duration, whirl ? 140f : -140f);
        });
        s.later(tele, () -> s.every(0, 2, Math.max(1, duration / 2), step -> {
            Location core = s.location();
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
            BossVfx.impact(ground(s.world, core), look, blast);
            Fx.sfx(s.world, core, "explosion", 3f, 1f);
            for (Player p : s.players()) {
                if (Fx.flat(p.getLocation(), core) <= blast) {
                    s.hit(p, spec.damage());
                    if (whirl) {
                        BossStatus.apply(p, StatusKind.SLOW, 60, 2);
                    }
                }
            }
        }));
        return tele + duration + 30;
    }

    // ---------------------------------------------------------------- zones

    /** The VFX colour of a zone ability (the tint of the pack primitives). */
    static Color zoneColor(AbilityType type) {
        return switch (type) {
            case POISON_CLOUD -> BossVfx.TOXIC;
            case WITHER_CLOUD -> BossVfx.WITHER;
            case BREATH_CLOUD -> BossVfx.VOID;
            case LAVA_POOLS -> BossVfx.EMBER;
            case VOID_ZONES -> BossVfx.RIFT;
            case ICE_SPIKES -> BossVfx.FROST;
            case WEB_TRAPS -> BossVfx.BONE;
            default -> BossVfx.RIFT;
        };
    }

    /** Preset of the lingering zone and eruption abilities. */
    record Zone(boolean eruption, double radius, int count, double seconds, double teleSeconds, double dmg,
            StatusKind effect, int effectTicks, int amp, boolean fire, int freeze, Particle particle,
            Color color, String sound) {
        static Zone of(AbilityType type) {
            return switch (type) {
                case POISON_CLOUD -> new Zone(false, 4, 3, 9, 1.2, 0.16, StatusKind.DECAY, 60, 1, false, 0,
                    Particle.SNEEZE, Color.fromRGB(80, 200, 60), "toxic_hiss");
                case WITHER_CLOUD -> new Zone(false, 4, 3, 9, 1.2, 0.18, StatusKind.WITHERING, 60, 1, false, 0,
                    Particle.SMOKE, Color.fromRGB(40, 40, 40), "wither_moan");
                case BREATH_CLOUD -> new Zone(false, 5, 3, 10, 1.4, 0.2, StatusKind.WITHERING, 40, 0, false, 0,
                    Particle.DRAGON_BREATH, Color.fromRGB(190, 60, 220), "breath_roar");
                case LAVA_POOLS -> new Zone(false, 3.5, 4, 12, 1.5, 0.22, StatusKind.SLOW, 20, 0, true, 0,
                    Particle.LAVA, Color.fromRGB(255, 90, 0), "lava_bubble");
                case VOID_ZONES -> new Zone(false, 4, 3, 10, 1.5, 0.2, StatusKind.BLIND, 50, 0, false, 0,
                    Particle.REVERSE_PORTAL, Color.fromRGB(110, 0, 170), "void_open");
                case ICE_SPIKES -> new Zone(true, 3.2, 6, 0, 1.6, 1.6, StatusKind.FROST, 80, 2, false, 160,
                    Particle.SNOWFLAKE, Color.fromRGB(150, 230, 255), "ice_shatter");
                case WEB_TRAPS -> new Zone(false, 2.6, 4, 3.5, 1.2, 0.08, StatusKind.ROOT, 30, 6, false, 0,
                    Particle.ITEM_COBWEB, Color.WHITE, "web_snare");
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
        Fx.sfx(s.world, s.location(), z.sound(), 3f, 1f);
        Color look = zoneColor(spec.type());
        for (Location spot : spots) {   // the telegraph: a disc of the zone's colour exactly as wide as the damage
            BossVfx.decal(spot, "disc", look, radius, tele + 2, 45f);
        }
        s.every(0, 5, Math.max(1, tele / 5), step -> { }, () -> {
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
            Fx.sfx(s.world, spot, z.sound(), 2f, 1f);
            Color look = zoneColor(spec.type());
            BossVfx.column(spot, "spike", look, radius * 0.9, 4.5, 3, 18);
            BossVfx.burst(spot.clone().add(0, 0.8, 0), "burst", look, radius * 0.9, 8);
            BossVfx.wave(spot, "ring", look, 0.3, radius, 6, 1f);
        }
        for (Player p : s.players()) {
            for (Location spot : spots) {
                if (Fx.flat(p.getLocation(), spot) <= radius && Math.abs(p.getLocation().getY() - spot.getY()) < 3) {
                    s.hit(p, spec.damage() * z.dmg());
                    p.setVelocity(new Vector(0, 0.75, 0));
                    BossStatus.apply(p, z.effect(), z.effectTicks(), z.amp() + 1);
                    break;
                }
            }
        }
    }

    private static void linger(BossSession s, AbilitySpec spec, Zone z, List<Location> spots, double radius,
            int duration) {
        Color look = zoneColor(spec.type());
        for (Location spot : spots) {
            BossVfx.decal(spot, "ring", look, radius, duration, 30f);
            switch (spec.type()) {
                case WEB_TRAPS -> {
                    for (int i = 0; i < 4; i++) {   // a cage of bone bars round the trap
                        double a = Math.PI / 2 * i + 0.4;
                        BossVfx.column(spot.clone().add(Math.cos(a) * radius * 0.8, 0, Math.sin(a) * radius * 0.8), "cage_bar", look, 0.7, 2.6, 4, duration - 8);
                    }
                }
                case LAVA_POOLS -> BossVfx.column(spot, "pillar", look, radius * 0.9, 1.4, 6, duration - 10);
                default -> {   // poison, wither, breath, void: a low cloud that churns over the zone
                    BossVfx.spin(spot.clone().add(0, 0.6, 0), "cloud", look, radius * 0.55, radius * 0.5, duration, Math.max(1, duration / 60), null);
                }
            }
        }
        s.every(0, 5, Math.max(1, duration / 5), step -> {
            if (step % 2 != 0) {
                return;
            }
            for (Player p : s.players()) {
                for (Location spot : spots) {
                    if (Fx.flat(p.getLocation(), spot) <= radius && Math.abs(p.getLocation().getY() - spot.getY()) < 3) {
                        s.hit(p, spec.damage() * z.dmg());
                        BossStatus.apply(p, z.effect(), z.effectTicks(), z.amp() + 1);
                        if (z.fire()) {
                            BossStatus.apply(p, StatusKind.BURN, 60, 1);
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
        Fx.sfx(s.world, s.location(), "void_stare", 3f, 1f);
        BossVfx.decalOn(target, "sigil", BossVfx.VOID, 1.5, tele + 2, 240f);
        BossVfx.spin(s.location(), "vortex", BossVfx.VOID, 1.6, 3, tele, 2, s.entity());
        s.every(0, 4, Math.max(1, tele / 4), step -> { }, () -> {
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
            BossVfx.burst(s.location().add(0, 1.5, 0), "shard", BossVfx.VOID, 3.0, 8);
            s.entity().teleport(behind);
            BossVfx.burst(behind.clone().add(0, 1.5, 0), "burst", BossVfx.VOID, 3.0, 8);
            Fx.sfx(s.world, behind, "blink", 2f, 1f);
            Location strike = behind.clone();
            s.later(12, () -> {
                BossVfx.burst(strike.clone().add(0, 1.4, 0), "slash", BossVfx.RIFT, radius * 1.1, 7);
                Fx.sfx(s.world, strike, "slash", 2f, 1f);
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

    // ---------------------------------------------------------------- chained pull (GDD 12.5)

    /**
     * The Chained Leviathan's pull: crystal anchors appear around the arena and chain everybody toward the boss with a
     * steady, capped pull until they are all destroyed. Destroying them dazes the boss; leaving them overloads them.
     */
    private static int anchors(BossSession s, AbilitySpec spec) {
        s.startAnchors(Math.min(6, Math.max(1, spec.integer("count", 3))), spec.num("radius", 16), spec.num("height", 2),
            Math.max(1, spec.integer("hits", 3)), spec.integer("seconds", 25), spec.num("strength", 0.12),
            spec.num("nova", 2.0) * spec.damage());
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
        Fx.sfx(s.world, s.location(), "laser_charge", 3f, 1f);
        BossVfx.decalOn(target, "sigil", BossVfx.RIFT, 1.6, tele + 2, 200f);   // the marked player
        BossVfx.decal(s.location(), "ring", BossVfx.RIFT, 3.5, tele + 2, -120f);
        s.every(0, 2, Math.max(1, tele / 2), step -> {
            Location from = bossCenter(s);
            if (target.isOnline() && target.getWorld().equals(s.world)) {
                dir[0] = target.getLocation().toVector().subtract(from.toVector()).setY(0);
                if (dir[0].lengthSquared() < 0.01) {
                    dir[0] = new Vector(1, 0, 0);
                }
                dir[0].normalize();
            }
        }, () -> {
            Fx.sfx(s.world, s.location(), "power_up", 3f, 1f);
            // the whole sweep is known now: one beam display pivots on the boss and the client turns it
            List<Float> yaws = new ArrayList<>();
            for (int i = 0; i <= Math.max(1, duration / 2); i++) {
                Vector d = dir[0].clone().rotateAroundY(sweep * sign[0] * (i + 1));   // the damage loop turns before its first hit
                yaws.add((float) Math.toDegrees(Math.atan2(-d.getX(), d.getZ())));
            }
            Location origin = bossCenter(s);
            Vector lift = origin.toVector().subtract(s.location().toVector());
            BossVfx.sweep(origin, s.entity(), lift, "beam", BossVfx.RIFT, length, width * 1.4, 0, yaws, 2);
            Fx.sfx(s.world, origin, "laser_beam", 4f, 1f);
            BossVfx.burst(origin, "burst", BossVfx.RIFT, 2.4, 10);
            s.every(0, 2, Math.max(1, duration / 2), step -> {
                Location from = bossCenter(s);
                yaw[0] += sweep * sign[0];
                Vector d = dir[0].clone().rotateAroundY(yaw[0]);
                Location end = from.clone().add(d.clone().multiply(length));
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
        Fx.sfx(s.world, s.location(), "charge_roar", 3f, 1f);
        s.every(0, 3, Math.max(1, tele / 3), step -> {
            Location from = bossCenter(s);
            Vector d = target.getLocation().add(0, 1, 0).toVector().subtract(from.toVector());
            if (flat) {
                d.setY(0);
            }
            if (d.lengthSquared() > 0.01) {
                dir[0] = d.normalize();
            }
            // the dash line, re-aimed every 3 ticks: a thin danger beam on the ground
            Location foot = ground(s.world, s.location()).add(0, 0.1, 0);
            BossVfx.beam(foot, foot.clone().add(dir[0].clone().setY(0).multiply(distance)), "beam", BossVfx.DANGER, width * 0.5, 4);
        }, () -> {
            Set<UUID> hit = new HashSet<>();
            int steps = (int) Math.ceil(distance / speed);
            Fx.sfx(s.world, s.location(), "swoop", 3f, 1f);
            s.steer(steps + 2);
            s.every(0, 1, steps, step -> {
                s.entity().setVelocity(dir[0].clone().multiply(speed).setY(flat ? Math.max(0.05, s.entity().getVelocity().getY()) : dir[0].getY() * speed));
                Location at = bossCenter(s);
                if (step % 3 == 0) {
                    BossVfx.burst(at, "slash", BossVfx.DANGER, width * 1.4, 6);
                }
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
        int stun = (int) Math.round(spec.num("stun", 0) * 20);
        Fx.sfx(s.world, s.location(), "roar", 4f, 1f);
        BossVfx.decalOn(target, "sigil", BossVfx.DANGER, radius, Math.max(4, tele - 12), 200f);   // follows until the mark freezes
        s.every(0, 4, Math.max(1, tele / 4), step -> {
            if (step < tele / 4 - 3 && target.isOnline() && target.getWorld().equals(s.world)) {
                mark[0] = ground(s.world, target.getLocation());
            }
            if (step == Math.max(0, tele / 4 - 3)) {   // the mark is fixed now: the decal stays where it will land
                BossVfx.decal(mark[0], "disc", BossVfx.DANGER, radius, 16, 90f);
            }
        }, () -> {
            Location end = mark[0].clone();
            if (s.entity() instanceof EnderDragon dragon) {
                try {
                    dragon.setPhase(EnderDragon.Phase.CHARGE_PLAYER);
                } catch (RuntimeException ignored) {
                    // the dive is still resolved at the mark below
                }
            }
            Fx.sfx(s.world, s.location(), "swoop", 4f, 1f);
            s.steer(30);
            s.every(0, 1, 28, step -> {
                Vector to = end.toVector().add(new Vector(0, 2, 0)).subtract(s.location().toVector());
                if (to.lengthSquared() > 4) {
                    s.entity().setVelocity(to.normalize().multiply(Math.min(2.2, 0.8 + step * 0.06)));
                }
                if (step % 4 == 0) {
                    BossVfx.burst(s.location(), "slash", BossVfx.VOID, 4.0, 6);
                }
            }, () -> {
                BossVfx.impact(end, BossVfx.EMBER, radius);
                BossVfx.column(end, "pillar", BossVfx.EMBER, radius * 0.6, 4, 3, 6);
                Fx.sfx(s.world, end, "explosion", 4f, 1f);
                for (Player p : s.players()) {
                    if (Fx.flat(p.getLocation(), end) <= radius && onGround(p, 1.8)) {
                        s.hit(p, spec.damage());
                        Vector out = p.getLocation().toVector().subtract(end.toVector()).setY(0);
                        if (out.lengthSquared() > 0.01) {
                            Fx.push(p, out.normalize().multiply(0.8).setY(0.5), 1.0, 0.6);
                        }
                    }
                }
                if (stun > 0) {
                    land(s, end, stun, "<yellow><bold>" + BossSession.plain(s.def.name()) + " crashed into the ground!");
                } else if (s.entity() instanceof EnderDragon dragon) {
                    try {
                        dragon.setPhase(EnderDragon.Phase.CIRCLING);
                    } catch (RuntimeException ignored) {
                        // nothing to restore
                    }
                }
            });
        });
        return tele + 28 + 40 + stun;
    }

    // ---------------------------------------------------------------- perch (stun on the ground)

    /**
     * The flyer glides down to a marked spot near the arena centre and stays stunned there for {@code seconds}: the
     * window in which a sword reaches it (a circling dragon is out of melee range the rest of the fight).
     */
    private static int perch(BossSession s, AbilitySpec spec) {
        int hold = (int) Math.round(Math.max(1, spec.num("seconds", 5)) * 20);
        double offset = spec.num("radius", 6);
        int tele = tele(spec, 50);
        double angle = s.random.nextDouble() * Math.PI * 2;
        Location spot = ground(s.world, s.anchor().add(Math.cos(angle) * offset, 8, Math.sin(angle) * offset));
        s.broadcastNearby(BossSession.MINI.deserialize("<yellow><bold>" + BossSession.plain(s.def.name())
            + " is tiring! <gray>It comes down to land: get ready to strike."));
        Fx.sfx(s.world, s.location(), "roar", 4f, 1f);
        s.steer(tele + 2);
        s.every(0, 1, tele, step -> {
            Vector to = spot.toVector().add(new Vector(0, 1, 0)).subtract(s.location().toVector());
            if (to.lengthSquared() > 1) {
                s.entity().setVelocity(to.normalize().multiply(Math.min(1.2, 0.3 + to.length() * 0.05)));
            }
            if (step == 0) {
                BossVfx.decal(spot, "ring", BossVfx.GOLD, 4, tele + 2, 60f);
            }
        }, () -> land(s, spot, hold, null));
        return tele + hold + 20;
    }

    /**
     * Pins the flyer on {@code spot} for {@code hold} ticks: a sitting dragon does not knock players back with its wings,
     * the boss takes the dazed bonus, and it takes off again afterwards.
     */
    static void land(BossSession s, Location spot, int hold, String message) {
        Location at = spot.clone();
        at.setYaw(s.entity().getLocation().getYaw());
        at.setPitch(0);
        s.entity().teleport(at);
        s.entity().setVelocity(new Vector(0, 0, 0));
        s.groundFor(hold);
        sit(s);
        if (message != null) {
            s.broadcastNearby(BossSession.MINI.deserialize(message));
        }
        net.kyori.adventure.title.Title title = net.kyori.adventure.title.Title.title(
            BossSession.MINI.deserialize("<yellow><bold>STUNNED"),
            BossSession.MINI.deserialize("<gray>Strike it now! <white>" + Math.round(hold / 20.0) + "s"),
            net.kyori.adventure.title.Title.Times.times(java.time.Duration.ofMillis(150), java.time.Duration.ofMillis(1500),
                java.time.Duration.ofMillis(300)));
        for (Player p : s.players()) {
            p.showTitle(title);
        }
        Fx.sfx(s.world, at, "explosion", 3f, 1f);
        BossVfx.impact(at, BossVfx.GOLD, 4);
        // the daze: a ring of stars turning over its head for as long as it is pinned
        BossVfx.spin(at.clone().add(0, s.entity().getHeight() + 0.6, 0), "ring", BossVfx.GOLD, 1.6, 0.3, hold, Math.max(1, hold / 20), null);
        s.every(1, 1, hold, step -> {
            if (s.entity().getLocation().distanceSquared(at) > 0.09) {
                s.entity().teleport(at);
            }
            s.entity().setVelocity(new Vector(0, 0, 0));
            sit(s);
        }, () -> {
            s.groundedUntil = 0;
            if (s.entity() instanceof EnderDragon dragon) {
                try {
                    dragon.setPhase(EnderDragon.Phase.CIRCLING);
                } catch (RuntimeException ignored) {
                    // it flies off on its own
                }
            }
            s.steer(20);
            s.entity().setVelocity(new Vector(0, 0.9, 0));
            Fx.sfx(s.world, s.location(), "wing_flap", 3f, 1f);
        });
    }

    /** Keeps a dragon in a sitting phase (no wing knockback, head low); other bosses just stand still. */
    private static void sit(BossSession s) {
        if (s.entity() instanceof EnderDragon dragon && dragon.getPhase() != EnderDragon.Phase.SEARCH_FOR_BREATH_ATTACK_TARGET) {
            try {
                dragon.setPhase(EnderDragon.Phase.SEARCH_FOR_BREATH_ATTACK_TARGET);
            } catch (RuntimeException e) {
                try {
                    dragon.setPhase(EnderDragon.Phase.HOVER);
                } catch (RuntimeException ignored) {
                    // the teleport still pins it
                }
            }
        }
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
        Fx.sfx(s.world, s.location(), "abyss_call", 3f, 1f);
        s.broadcastNearby(net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(
            "<aqua><bold>A tidal wave is coming! <gray>Run for the gap in the wall."));
        // the wall rises where it starts, with the gap marked safe; then it marches across at the damage speed
        List<Double> segments = new ArrayList<>();
        for (double w = -halfWidth + 2; w <= halfWidth - 2 + 1e-6; w += 4) {
            if (Math.abs(w - gapCenter) >= gapWidth / 2 + 1.5) {
                segments.add(w);
            }
        }
        Location wallBase = ground(s.world, start.clone().add(0, 4, 0));
        int wallSteps = (int) Math.ceil(reach * 2 / speed);
        BossVfx.wall(wallBase, dir, segments, 4.1, height, reach * 2, tele, wallSteps, BossVfx.TIDE);
        BossVfx.decal(ground(s.world, start.clone().add(side.clone().multiply(gapCenter)).add(0, 4, 0)), "sigil", BossVfx.SAFE,
            gapWidth / 2, tele + wallSteps, 90f);
        s.every(0, 5, Math.max(1, tele / 5), step -> { }, () -> {
            Fx.sfx(s.world, wallBase, "wave_rush", 5f, 1f);
            Set<UUID> hit = new HashSet<>();
            int steps = (int) Math.ceil(reach * 2 / speed);
            s.every(0, 2, steps / 2 + 1, step -> {
                double travelled = step * 2 * speed;
                Location front = start.clone().add(dir.clone().multiply(travelled));
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
        Fx.sfx(s.world, center, "fire_burst", 3f, 1f);
        Vector gapDir = new Vector(Math.cos(gapCenter), 0, Math.sin(gapCenter));
        int ringSteps = (int) Math.ceil(max / speed);
        BossVfx.beam(center.clone().add(0, 0.15, 0), center.clone().add(0, 0.15, 0).add(gapDir.clone().multiply(max)), "beam",
            BossVfx.SAFE, 1.2, tele + ringSteps * 2);   // the way out, shown until the ring has passed
        BossVfx.decal(center, "ring", BossVfx.EMBER, 3, tele + 2, 120f);
        s.every(0, 5, Math.max(1, tele / 5), step -> { }, () -> {
            Set<UUID> hit = new HashSet<>();
            int steps = (int) Math.ceil(max / speed);
            // a wall of fire segments running out at the damage speed, leaving the gap open
            BossVfx.arcWave(center, "wave", BossVfx.EMBER, speed, max, steps * 2, 1.6, 20, gapCenter, half);
            Fx.sfx(s.world, center, "fire_wall", 4f, 1f);
            s.every(0, 2, steps, i -> {
                double r = (i + 1) * speed;
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
                        BossStatus.apply(p, StatusKind.BURN, 60, 1);
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
        Fx.sfx(s.world, s.location(), "bone_rattle", 3f, 1f);
        centers.forEach(c -> BossVfx.decal(c, "disc", BossVfx.BONE, radius, tele + 2, 60f));
        s.every(0, 5, Math.max(1, tele / 5), step -> { }, () -> {
            for (Location c : centers) {   // the cage: bone bars bursting out of the ground round the mark
                for (int b = 0; b < 10; b++) {
                    double a = 2 * Math.PI * b / 10;
                    BossVfx.column(c.clone().add(Math.cos(a) * radius, 0, Math.sin(a) * radius), "cage_bar", BossVfx.BONE, 0.9, 3.4, 3,
                        duration);
                }
                BossVfx.decal(c.clone().add(0, 3.4, 0), "ring", BossVfx.BONE, radius, duration + 3, 20f);
            }
            Fx.sfx(s.world, centers.get(0), "bone_burst", 3f, 1f);
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
        Fx.sfx(s.world, s.location(), "soul_drain", 2f, 1f);
        BossVfx.decalOn(target, "sigil", BossVfx.SOUL, 1.4, tele + duration, 160f);
        s.every(0, 4, Math.max(1, tele / 4), step ->
            BossVfx.beam(bossCenter(s), target.getLocation().add(0, 1, 0), "beam", BossVfx.SOUL, 0.15, 5), () ->
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
                BossVfx.beam(from, end, "beam", BossVfx.SOUL, 0.6, 5);   // the drain, re-aimed every 4 ticks
                if (step % 2 == 0) {
                    s.hit(victim, spec.damage() * 0.3);
                    s.heal(s.maxHp * heal * 2);
                    BossVfx.burst(end, "orb", BossVfx.SOUL, 1.2, 6);
                    BossVfx.burst(from.clone().add(0, 1.5, 0), "orb", BossVfx.SOUL, 1.6, 8);
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
        Fx.sfx(s.world, s.location(), "storm_rise", 3f, 1f);
        s.broadcastNearby(net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(
            "<aqua><bold>A blizzard is rising! <gray>Stand inside a warm brazier ring or you will freeze."));
        for (Location f : fires) {   // the warm braziers: a fire column inside a ring exactly as wide as the shelter
            BossVfx.column(f, "pillar", BossVfx.EMBER, 1.3, 2.6, 6, tele + duration);
            BossVfx.decal(f, "ring", BossVfx.EMBER, shelterRadius, tele + duration + 4, 45f);
        }
        // the storm: a huge pale funnel over the arena for the whole blizzard
        BossVfx.spin(s.anchor(), "vortex", BossVfx.FROST, Math.max(10, s.def.arena().radius() * 0.55), 9, tele + duration,
            Math.max(2, (tele + duration) / 30), null);
        s.every(0, 5, Math.max(1, tele / 5), step -> { }, () -> s.every(0, 5, Math.max(1, duration / 5), step -> {
            if (step % 16 == 0) {   // the 4 s wind clip, again every 4 s while the storm lasts
                Fx.sfx(s.world, s.anchor(), "blizzard_wind", 4f, 1f);
            }
            for (Player p : s.players()) {
                if (step % 2 == 0) {
                    BossVfx.burst(p.getLocation().add(0, 2.5, 0), "shard", BossVfx.FROST, 1.4, 8);
                }
                boolean sheltered = false;
                for (Location f : fires) {
                    if (Fx.flat(p.getLocation(), f) <= shelterRadius) {
                        sheltered = true;
                        break;
                    }
                }
                if (sheltered) {
                    BossStatus.remove(p, StatusKind.FROST);
                    continue;
                }
                BossStatus.apply(p, StatusKind.FROST, 30, 1);
                if (step % 2 == 0) {
                    s.hit(p, spec.damage() * 0.35);
                }
            }
        }, null));
        return tele + duration + 20;
    }
}
