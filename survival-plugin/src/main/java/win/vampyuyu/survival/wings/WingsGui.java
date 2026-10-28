package win.vampyuyu.survival.wings;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import win.vampyuyu.survival.api.EconomyApi;
import win.vampyuyu.survival.api.Stat;
import win.vampyuyu.survival.core.Core;
import win.vampyuyu.survival.core.Gui;
import win.vampyuyu.survival.core.Guis;
import win.vampyuyu.survival.core.Msg;

/** The {@code /wings} menu: status, upgrade with Wing Cores, recovery, personal toggles. Tooltip text only. */
public final class WingsGui {
    private static final int SLOT_WINGS = 4;
    private static final int SLOT_UPGRADE = 11;
    private static final int SLOT_STATS = 13;
    private static final int SLOT_CLAIM = 15;
    private static final int[] TOGGLE_SLOTS = {28, 30, 32, 34};
    private static final long CONFIRM_MS = 10_000;

    private final Core core;
    private final WingsService service;
    private final FlightManager flight;
    private final Map<UUID, Long> claimConfirm = new HashMap<>();

    public WingsGui(Core core, WingsService service, FlightManager flight) {
        this.core = core;
        this.service = service;
        this.flight = flight;
    }

    public void open(Player player) {
        Gui gui = core.guis().create(4, "<gradient:#ffe27a:#ffffff>The Wings</gradient>");
        render(gui, player);
        gui.onClose(p -> claimConfirm.remove(p.getUniqueId()));
        gui.open(player);
    }

    private void render(Gui gui, Player player) {
        WingsSettings s = service.settings();
        WingsData d = service.data(player.getUniqueId());
        gui.fill(Guis.icon(Material.BLACK_STAINED_GLASS_PANE, " "));
        gui.border(Guis.icon(Material.YELLOW_STAINED_GLASS_PANE, " "));
        int level = s.clampLevel(d.level);
        int slot = d.received && !d.activeId.isEmpty() ? service.findValidSlot(player) : -1;

        // ---- the wings themselves
        if (slot >= 0) {
            ItemStack shown = player.getInventory().getItem(slot).clone();
            gui.set(SLOT_WINGS, shown);
        } else if (d.received && !d.activeId.isEmpty()) {
            gui.set(SLOT_WINGS, Guis.icon(Material.BARRIER, "<red>Your Wings are not on you",
                "<gray>They are not in your inventory.", "<gray>Check your ender chest, or use <white>Recover</white>."));
        } else {
            gui.set(SLOT_WINGS, Guis.icon(Material.ELYTRA, "<gray>No Wings yet",
                "<dark_gray>Defeat the Voidwyrm to earn The Wings.", "<dark_gray>Each player can earn them once."));
        }

        // ---- stats of the current level
        double capacity = Stat.FLIGHT_ENERGY.base() + s.capacityBonus(level);
        gui.set(SLOT_STATS, Guis.icon(Material.AMETHYST_CLUSTER, "<light_purple>Level " + level + " <dark_gray>/ " + s.maxLevel,
            "<gray>Flight Energy: <light_purple>" + Math.round(capacity),
            "<gray>Flight time: <white>~" + Math.round(s.flightSeconds(capacity, level)) + "s",
            "<gray>Sprint drain: <red>" + Msg.number(s.drainPerSecond(level, true, false, false)) + "/s",
            "<gray>Recharge: <green>" + Msg.number(s.regenPerSecond(level, false)) + "/s <dark_gray>(x"
                + Msg.number(s.regenIslandMultiplier) + " on islands)",
            "<gray>Flight speed: <aqua>" + Math.round(s.flySpeed(level) / 0.1f * 100) + "%",
            "<gray>Trail: <white>" + s.trail(level).particle().name().toLowerCase().replace('_', ' ')));

        // ---- upgrade
        if (slot < 0) {
            gui.set(SLOT_UPGRADE, Guis.icon(Material.GRAY_DYE, "<gray>Upgrade unavailable",
                "<dark_gray>Carry your Wings to upgrade them."));
        } else if (level >= s.maxLevel) {
            gui.set(SLOT_UPGRADE, Guis.icon(Material.NETHER_STAR, "<gold>Maximum level",
                "<gray>Your Wings are fully awakened."));
        } else {
            WingsSettings.Upgrade cost = s.upgradeFrom(level);
            int have = service.countCores(player, cost.coreTier());
            boolean enough = have >= cost.amount();
            EconomyApi eco = core.service(EconomyApi.class).orElse(null);
            boolean pay = eco != null && cost.coins() > 0;
            boolean rich = !pay || eco.has(player.getUniqueId(), cost.coins());
            double nextCapacity = Stat.FLIGHT_ENERGY.base() + s.capacityBonus(level + 1);
            java.util.List<String> lore = new java.util.ArrayList<>();
            lore.add("<gray>Level <white>" + level + " <gray>-> <gold>" + (level + 1));
            lore.add("");
            lore.add("<gray>Cost:");
            lore.add((enough ? "<green>" : "<red>") + " " + cost.amount() + "x " + s.coreNames.get(cost.coreTier() - 1)
                + " <dark_gray>(you have " + have + ")");
            if (pay) {
                lore.add((rich ? "<green>" : "<red>") + " " + Msg.coins(cost.coins()));
            }
            lore.add("");
            lore.add("<gray>Flight Energy: <light_purple>" + Math.round(capacity) + " <dark_gray>-> <light_purple>"
                + Math.round(nextCapacity));
            lore.add("<gray>Recharge: <green>" + Msg.number(s.regenPerSecond(level, false)) + " <dark_gray>-> <green>"
                + Msg.number(s.regenPerSecond(level + 1, false)) + "/s");
            lore.add("<gray>Speed: <aqua>" + Math.round(s.flySpeed(level) / 0.1f * 100) + "% <dark_gray>-> <aqua>"
                + Math.round(s.flySpeed(level + 1) / 0.1f * 100) + "%");
            lore.add("");
            lore.add(enough && rich ? "<yellow>Click to upgrade" : "<red>You cannot afford this yet");
            gui.set(SLOT_UPGRADE, Guis.icon(enough && rich ? Material.ANVIL : Material.GRAY_DYE,
                "<gold>Upgrade the Wings", lore.toArray(String[]::new)), click -> {
                    WingsService.UpgradeOutcome out = service.upgrade(click.player());
                    switch (out.result()) {
                        case OK -> core.msg().ok(click.player(), "Your Wings reached level " + out.level() + "!");
                        case NO_WINGS -> core.msg().error(click.player(), "Carry your Wings to upgrade them.");
                        case MAX_LEVEL -> core.msg().info(click.player(), "Your Wings are already at the maximum level.");
                        case MISSING_CORES -> core.msg().error(click.player(), "You need more Wing Cores.");
                        case MISSING_COINS -> core.msg().error(click.player(), "You cannot afford the coin cost.");
                    }
                    render(gui, click.player());
                });
        }

        // ---- recovery
        if (d.received) {
            boolean has = service.hasValidWings(player);
            long remaining = service.claimRemaining(player);
            java.util.List<String> lore = new java.util.ArrayList<>();
            lore.add("<gray>Lost your Wings? A new pair is forged;");
            lore.add("<gray>the old ones (if any) turn to dust.");
            lore.add("");
            lore.add("<gray>Cost: <gold>" + Msg.coins(s.claimCoins));
            if (remaining > 0) {
                lore.add("<red>Available in " + Msg.duration(remaining));
            } else if (has) {
                lore.add("<green>You still have your Wings.");
            } else {
                lore.add("<yellow>Click twice to confirm");
            }
            gui.set(SLOT_CLAIM, Guis.icon(has || remaining > 0 ? Material.GRAY_DYE : Material.ENDER_EYE,
                "<aqua>Recover lost Wings", lore.toArray(String[]::new)), click -> {
                    Player p = click.player();
                    long now = System.currentTimeMillis();
                    Long armed = claimConfirm.get(p.getUniqueId());
                    if (armed == null || now - armed > CONFIRM_MS) {
                        claimConfirm.put(p.getUniqueId(), now);
                        core.msg().warn(p, "Click again within 10 seconds to recover your Wings.");
                        return;
                    }
                    claimConfirm.remove(p.getUniqueId());
                    reportClaim(p, service.claim(p));
                    render(gui, p);
                });
        } else {
            gui.set(SLOT_CLAIM, Guis.icon(Material.GRAY_DYE, "<gray>Recovery", "<dark_gray>Nothing to recover yet."));
        }

        // ---- toggles
        toggle(gui, player, TOGGLE_SLOTS[0], Material.FIREWORK_STAR, "Particle trail", d.trail,
            "Shows a trail behind you while you fly.", v -> d.trail = v);
        toggle(gui, player, TOGGLE_SLOTS[1], Material.EXPERIENCE_BOTTLE, "Energy bar", d.bar,
            "Shows the Flight Energy bar at the top.", v -> d.bar = v);
        toggle(gui, player, TOGGLE_SLOTS[2], Material.FEATHER, "Double jump to fly", d.doubleJump,
            "Off: start flying with /wings fly only.", v -> d.doubleJump = v);
        toggle(gui, player, TOGGLE_SLOTS[3], Material.NOTE_BLOCK, "Wing beat sounds", d.sounds,
            "Plays the flapping sound while you fly.", v -> d.sounds = v);
    }

    private void toggle(Gui gui, Player player, int slot, Material icon, String name, boolean on, String what,
                        java.util.function.Consumer<Boolean> setter) {
        gui.set(slot, Guis.icon(on ? icon : Material.GRAY_DYE, (on ? "<green>" : "<red>") + name + ": " + (on ? "ON" : "OFF"),
            "<gray>" + what, "", "<yellow>Click to toggle"), click -> {
                setter.accept(!on);
                core.profiles().markDirty(click.player().getUniqueId());
                render(gui, click.player());
            });
    }

    /** Chat feedback of a claim attempt (shared with the command). */
    public void reportClaim(Player p, WingsService.ClaimResult result) {
        switch (result) {
            case OK -> core.msg().ok(p, "A new pair of Wings was forged for you.");
            case RESTORED -> core.msg().ok(p, "Your Wings found their way back to you.");
            case NEVER_RECEIVED -> core.msg().error(p, "You have never received The Wings.");
            case HAS_WINGS -> core.msg().info(p, "You still have your Wings (check your inventory and ender chest).");
            case COOLDOWN -> core.msg().error(p, "You can recover your Wings again in "
                + Msg.duration(service.claimRemaining(p)) + ".");
            case MISSING_COINS -> core.msg().error(p, "Recovering the Wings costs " + Msg.coins(service.settings().claimCoins) + ".");
        }
    }

    FlightManager flight() {
        return flight;
    }
}
