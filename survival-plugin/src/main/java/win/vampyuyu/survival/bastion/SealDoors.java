package win.vampyuyu.survival.bastion;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import win.vampyuyu.survival.api.BastionApi;
import win.vampyuyu.survival.api.Realm;
import win.vampyuyu.survival.core.SkyblockWorlds;
import win.vampyuyu.survival.depths.DepthsLayout;
import win.vampyuyu.survival.depths.DepthsPlan;
import win.vampyuyu.survival.island.CitadelPlan;
import win.vampyuyu.survival.island.FrostPlan;
import win.vampyuyu.survival.island.FrostRealm;
import win.vampyuyu.survival.island.CitadelRealm;
import win.vampyuyu.survival.island.GrovePlan;
import win.vampyuyu.survival.island.GroveRealm;
import win.vampyuyu.survival.island.NecropolisPlan;
import win.vampyuyu.survival.island.NecropolisRealm;
import win.vampyuyu.survival.marsh.MarshPlan;
import win.vampyuyu.survival.realm.AshesPlan;
import win.vampyuyu.survival.realm.StormPlan;

/**
 * The seals: a Realm's exploration ends at its Guardian, behind a sealed door. Right-clicking the seal block with the main
 * hand opens the Realm's Bastion exactly like {@code /bastion enter} ({@link BastionApi#enter}: the key, the party and every
 * refusal are the Bastion's own). Which Bastion a Realm's seal opens is data ({@code bastion.seal.<realm id>}); where the
 * seal stands comes from the Realm's plan ({@link #sites()}), so no coordinate is written twice.
 */
public final class SealDoors implements Listener {
    /**
     * One seal: the Realm, its world, and the block to click in world coordinates. {@code faceX}/{@code faceZ} point from
     * the seal to where players stand (the side the sign faces), and {@code above} is the seal's height over the standing
     * level there, for tools and screenshots.
     */
    public record Site(Realm realm, String world, int x, int y, int z, int faceX, int faceZ, int above) {
        public boolean is(Block block) {
            return block.getX() == x && block.getY() == y && block.getZ() == z && block.getWorld().getName().equals(world);
        }
    }

    private final Supplier<BastionConfig> config;
    private final Supplier<BastionApi> api;
    private final BiConsumer<Player, String> warn;
    private final List<Site> sites;

    /** The listener of the plugin: {@code config} is read at each click, an {@code /bastion admin reload} swaps it. */
    public SealDoors(Supplier<BastionConfig> config, Supplier<BastionApi> api, BiConsumer<Player, String> warn) {
        this(config, api, warn, sites());
    }

    SealDoors(Supplier<BastionConfig> config, Supplier<BastionApi> api, BiConsumer<Player, String> warn, List<Site> sites) {
        this.config = config;
        this.api = api;
        this.warn = warn;
        this.sites = List.copyOf(sites);
    }

    /** Every seal of the eight Realms, from their plans. */
    public static List<Site> sites() {
        return List.of(
            site(Realm.ASHES, SkyblockWorlds.ASHES, AshesPlan.ORIGIN_Y, AshesPlan.sealBlock(), 0, 1, 1),
            site(Realm.GROVE, SkyblockWorlds.GROVE, GroveRealm.SURFACE_Y, GrovePlan.SEAL, 0, 1, 1),
            site(Realm.HEIGHTS, SkyblockWorlds.HEIGHTS, FrostRealm.SURFACE_Y, FrostPlan.SEAL, 0, 1, 1),
            site(Realm.CITADEL, SkyblockWorlds.CITADEL, CitadelRealm.SURFACE_Y, CitadelPlan.SEAL, 0, 1, 1),
            site(Realm.MARSHES, SkyblockWorlds.MARSH, MarshPlan.ORIGIN_Y, MarshPlan.sealBlock(), 0, 1, 1),
            site(Realm.ARCHIPELAGO, SkyblockWorlds.STORM, StormPlan.ORIGIN_Y, StormPlan.sealBlock(), 0, 1, 0),
            site(Realm.DEPTHS, SkyblockWorlds.DEPTHS, DepthsLayout.ORIGIN_Y, DepthsPlan.sealBlock(), -1, 0, 2),
            site(Realm.NECROPOLIS, SkyblockWorlds.NECROPOLIS, NecropolisRealm.SURFACE_Y, NecropolisPlan.SEAL, 0, 1, 1));
    }

    private static Site site(Realm realm, String world, int originY, int[] cell, int faceX, int faceZ, int above) {
        return new Site(realm, world, cell[0], originY + cell[1], cell[2], faceX, faceZ, above);
    }

    /** The rune lock in front of each seal (display models), by Realm. */
    private final java.util.Map<Realm, win.vampyuyu.survival.island.StructureModels> sigils = new java.util.EnumMap<>(Realm.class);

    /** The rune lock of one seal: spawned while players are near, turning slowly ({@code SealDoor.sigil}). */
    private void sigil(Site site, org.bukkit.World world) {
        win.vampyuyu.survival.island.StructureModels models = sigils.get(site.realm());
        if (models == null) {
            try {
                models = new win.vampyuyu.survival.island.StructureModels(
                    org.bukkit.plugin.java.JavaPlugin.getProvidingPlugin(SealDoors.class), "seal:" + site.realm().id(), world, 0, 0, 0,
                    win.vampyuyu.survival.island.SealDoor.sigil(site.realm(), site.x(), site.y(), site.z(), site.faceX(), site.faceZ()));
            } catch (IllegalArgumentException | IllegalStateException e) {
                return; // no providing plugin (tests)
            }
            sigils.put(site.realm(), models);
        }
        models.tick(20);
    }

    /** A slow shimmer on each seal for the players near it, so the block to click is the one that is alive. */
    public void glow() {
        for (Site site : sites) {
            org.bukkit.World world = org.bukkit.Bukkit.getWorld(site.world());
            if (world == null) {
                continue;
            }
            sigil(site, world);
            if (world.getPlayers().isEmpty()) {
                continue;
            }
            org.bukkit.Location at = new org.bukkit.Location(world, site.x() + 0.5 + site.faceX() * 0.6, site.y() + 0.5,
                site.z() + 0.5 + site.faceZ() * 0.6);
            for (Player player : world.getPlayers()) {
                if (player.getLocation().distanceSquared(at) < 20 * 20) {
                    player.spawnParticle(org.bukkit.Particle.END_ROD, at, 3, 0.3, 0.3, 0.3, 0.005);
                }
            }
        }
    }

    /** The seal at this block, or null. */
    public Site siteAt(Block block) {
        if (block == null) {
            return null;
        }
        for (Site site : sites) {
            if (site.is(block)) {
                return site;
            }
        }
        return null;
    }

    /** The Bastion a Realm's seal opens (the {@code bastion.seal.<realm>} setting), or null when none. */
    public String bastionOf(Realm realm) {
        return config.get().seals.get(realm.id());
    }

    @EventHandler(ignoreCancelled = false)
    public void onInteract(PlayerInteractEvent event) {
        Block block = event.getClickedBlock();
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getHand() != EquipmentSlot.HAND || block == null) {
            return;
        }
        Site site = siteAt(block);
        if (site == null) {
            return;
        }
        event.setCancelled(true);
        Player player = event.getPlayer();
        BastionApi bastions = api.get();
        String bastion = bastionOf(site.realm());
        if (bastions == null || bastion == null) {
            warn.accept(player, "The seal does not answer: Bastions are not available.");
            return;
        }
        String refusal = bastions.enter(player, bastion);
        if (refusal != null) {
            warn.accept(player, refusal);
        }
    }
}
