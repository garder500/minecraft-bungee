package win.vampyuyu.survival.menu;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import win.vampyuyu.survival.api.IslandInfo;
import win.vampyuyu.survival.api.Skill;
import win.vampyuyu.survival.core.Gui;
import win.vampyuyu.survival.core.Guis;
import win.vampyuyu.survival.core.MenuArt;
import win.vampyuyu.survival.core.Msg;

/** The Isles menu (the Skyblock menu): a constellation of buttons on the painted background, one per feature. */
final class MainMenu {
    /** Where the 17 buttons sit on the painted constellation (mirror of {@code layout.SB_MAIN}, same order as {@link #DOORS}). */
    static final int[] SB_MAIN = {11, 13, 15, 19, 22, 25, 29, 31, 33, 37, 40, 43, 1, 7, 45, 49, 53};

    /** Which menu or command a button leads to. */
    private enum Target { QUESTS, COMMAND, WISPS, BOARDS, PROFILE, WARPS, SETTINGS }

    /** One button: icon model of the pack, fallback material, name, description, action word and where it leads. */
    private record Door(Target target, Material icon, String model, String name, String entry, String about, String action) {}

    /** Entries behind the Leaderboards door: the real leaderboard command, or the submenu of the two feature boards. */
    private static final List<String> BOARD_ENTRIES = List.of("leaderboards", "baltop", "skilltop");

    /** The buttons in {@link #SB_MAIN} order: quests, skills, collections, forge, island, minions, exchange, market, bank,
     * bosses, crypt, hunts, wisps, leaderboards, profile, warps, settings. */
    private static final List<Door> DOORS = List.of(
        new Door(Target.QUESTS, Material.WRITABLE_BOOK, "quests", "Quests", null, "Tutorial and daily quests.", "Open"),
        new Door(Target.COMMAND, Material.DIAMOND_SWORD, "skills", "Skills", "skills", "Eight skills with permanent stats.", "Open"),
        new Door(Target.COMMAND, Material.PAINTING, "collections", "Collections", "collections", "Gather resources to unlock recipes.", "Open"),
        new Door(Target.COMMAND, Material.BLAST_FURNACE, "forge", "Forge", "forge", "Smelt and craft the ore line.", "Open"),
        new Door(Target.COMMAND, Material.GRASS_BLOCK, "island", "Island", "island", "Your island, co-op and visitors.", "Open"),
        new Door(Target.COMMAND, Material.ARMOR_STAND, "minions", "Minions", "minions", "Workers that gather for you.", "Open"),
        new Door(Target.COMMAND, Material.EMERALD, "exchange", "Sky Exchange", "bazaar", "Trade resources at market prices.", "Open"),
        new Door(Target.COMMAND, Material.GOLD_BLOCK, "market", "Drift Market", "auction", "Sell rare items to other players.", "Open"),
        new Door(Target.COMMAND, Material.GOLD_INGOT, "bank", "Bank", "bank", "Keep your coins safe and earn interest.", "Open"),
        new Door(Target.COMMAND, Material.WITHER_SKELETON_SKULL, "bosses", "Bosses", "boss", "Fight powerful bosses for loot.", "Open"),
        new Door(Target.COMMAND, Material.IRON_DOOR, "crypt", "Crypt Depths", "dungeon", "Enter the depths with a party.", "Open"),
        new Door(Target.COMMAND, Material.IRON_SWORD, "hunts", "Hunts", "slayer", "Hunt a personal boss for perks.", "Open"),
        new Door(Target.WISPS, Material.PINK_DYE, "wisps", "Wisps", null, null, "Open"),
        new Door(Target.BOARDS, Material.GOLDEN_HELMET, "leaderboards", "Leaderboards", "leaderboards", "See who is on top.", "Open"),
        new Door(Target.PROFILE, Material.PLAYER_HEAD, null, "Profile", null, null, "Open"),
        new Door(Target.WARPS, Material.COMPASS, "warps", "Warps", null, "Travel to the hub, your island and more.", "Open"),
        new Door(Target.SETTINGS, Material.COMPARATOR, "settings", "Settings", null, "Sidebar, quest tracker and sounds.", "Open"));

    /** The profile page: stats, pets, accessories and the recipe book, which have no slot on the constellation. */
    private record Extra(Material icon, String name, String entry, String about) {}

    private static final List<Extra> EXTRAS = List.of(
        new Extra(Material.NETHER_STAR, "Stats", "stats", "Your stats and where they come from."),
        new Extra(Material.TOTEM_OF_UNDYING, "Accessories", "accessories", "Talismans and rings that boost stats."),
        new Extra(Material.BONE, "Pets", "pets", "Your companions and their abilities."),
        new Extra(Material.CRAFTING_TABLE, "Recipe Book", "recipes", "Every recipe you have unlocked."));
    private static final int[] EXTRA_SLOTS = {10, 12, 14, 16};

    private final MenuRuntime rt;
    private final QuestService quests;
    private final QuestsGui questsGui;
    private final WarpsGui warpsGui;
    private final SettingsGui settingsGui;
    private final FairyGui fairyGui;
    private final LeaderboardsGui boardsGui;

    MainMenu(MenuRuntime rt, QuestService quests, QuestsGui questsGui, WarpsGui warpsGui, SettingsGui settingsGui,
            FairyGui fairyGui, LeaderboardsGui boardsGui) {
        this.rt = rt;
        this.quests = quests;
        this.questsGui = questsGui;
        this.warpsGui = warpsGui;
        this.settingsGui = settingsGui;
        this.fairyGui = fairyGui;
        this.boardsGui = boardsGui;
    }

    void open(Player player) {
        quests.sync(player);
        Gui gui = rt.core.guis().create(MenuArt.Kind.MAIN, "<dark_gray>Isles");
        for (int i = 0; i < DOORS.size(); i++) {
            Door door = DOORS.get(i);
            if (!visible(door)) {
                continue;
            }
            gui.set(SB_MAIN[i], doorIcon(player, door), click -> clickDoor(player, door, click));
        }
        gui.open(player);
        rt.play(player, "click");
    }

    /** The profile page: a small menu with the entries that have no slot on the constellation. */
    void openProfile(Player player) {
        Gui gui = rt.core.guis().create(3, "<dark_gray>Profile");
        int at = 0;
        for (Extra extra : EXTRAS) {
            if (!rt.commandExists(extra.entry())) {
                continue;
            }
            boolean usable = rt.commandFor(extra.entry(), player).isPresent();
            gui.set(EXTRA_SLOTS[at++], usable
                ? Guis.button(extra.icon(), "<green>" + extra.name(), extra.about(), "Open")
                : Guis.icon(Material.GRAY_DYE, "<gray>" + extra.name() + " <dark_gray>(unavailable)",
                    "<red>You do not have permission."),
                click -> {
                    if (usable) {
                        click.close();
                        run(player, rt.commandFor(extra.entry(), player).orElseThrow());
                    }
                });
        }
        gui.back("To the Isles", click -> open(click.player()));
        gui.open(player);
    }

    /** A door is hidden when none of its commands exists at all (the feature is not installed). */
    private boolean visible(Door door) {
        if (door.entry() == null) {
            return true;
        }
        if (door.entry().equals("leaderboards")) {
            return BOARD_ENTRIES.stream().anyMatch(rt::commandExists);
        }
        return rt.commandExists(door.entry());
    }

    private boolean usable(Door door, Player player) {
        if (door.entry() == null) {
            return true;
        }
        if (door.entry().equals("leaderboards")) {
            return BOARD_ENTRIES.stream().anyMatch(e -> rt.commandFor(e, player).isPresent());
        }
        return rt.commandFor(door.entry(), player).isPresent();
    }

    /** True for the Island door of a player who has no island: it creates one instead. */
    private boolean createsIsland(Door door, Player player) {
        return "island".equals(door.entry()) && rt.islands().isPresent() && rt.createIslandCommand(player).isPresent()
            && rt.islands().get().islandOf(player.getUniqueId()).isEmpty();
    }

    private void clickDoor(Player player, Door door, Gui.Click click) {
        switch (door.target()) {
            case QUESTS -> questsGui.open(player);
            case WISPS -> fairyGui.open(player);
            case WARPS -> warpsGui.open(player);
            case SETTINGS -> settingsGui.open(player);
            case PROFILE -> openProfile(player);
            case BOARDS, COMMAND -> {
                if (!usable(door, player)) {
                    return;
                }
                click.close();
                if (createsIsland(door, player)) {
                    run(player, rt.createIslandCommand(player).orElseThrow());
                } else if (door.target() == Target.BOARDS && rt.commandFor("leaderboards", player).isEmpty()) {
                    rt.core.scheduler().nextTick(() -> boardsGui.open(player));
                } else {
                    run(player, rt.commandFor(door.entry(), player).orElseThrow());
                }
            }
        }
    }

    /** Runs a linked feature's command as the player on the next tick (after the menu closed). */
    void run(Player player, String command) {
        rt.core.scheduler().nextTick(() -> {
            if (!player.isOnline()) {
                return;
            }
            player.performCommand(command);
            quests.command(player, command.split(" ")[0]);
        });
    }

    private ItemStack doorIcon(Player player, Door door) {
        switch (door.target()) {
            case QUESTS -> {
                QuestData data = rt.quests(player.getUniqueId());
                int done = rt.engine().completedCount(data);
                int total = rt.config().catalog.tutorial().size();
                return Guis.menuButton(door.icon(), door.model(), "<gold>Quests",
                    "Tutorial " + Guis.progress(done, total) + " · streak "
                        + rt.engine().liveStreak(data, quests.today()), "Open");
            }
            case WISPS -> {
                FairyData data = rt.fairy(player.getUniqueId());
                return Guis.menuButton(door.icon(), door.model(), "<light_purple>Wisps",
                    "Found " + Guis.progress(data.found.size(), fairyGui.total()), "Open");
            }
            case WARPS -> {
                return Guis.menuButton(door.icon(), door.model(), "<aqua>Warps", door.about(), door.action());
            }
            case SETTINGS -> {
                return Guis.menuButton(door.icon(), door.model(), "<gray>Settings", door.about(), door.action());
            }
            case PROFILE -> {
                return profile(player);
            }
            default -> { }
        }
        if (usable(door, player)) {
            if (createsIsland(door, player)) {
                return Guis.menuButton(Material.OAK_SAPLING, door.model(), "<green>Create Island",
                    "Start your own island and pick a style.", "Create");
            }
            return Guis.menuButton(door.icon(), door.model(), "<green>" + door.name(), door.about(), door.action());
        }
        return Guis.icon(Material.GRAY_DYE, "<gray>" + door.name() + " <dark_gray>(unavailable)",
            "<red>You do not have permission.");
    }

    // ---------------------------------------------------------------- profile

    private ItemStack profile(Player player) {
        String skills = rt.skills().isPresent()
            ? "Skill average " + String.format(java.util.Locale.ROOT, "%.1f", skillAverage(player)) : "";
        String coins = rt.economy().map(e -> Msg.grouped(e.coins(player.getUniqueId())) + " coins").orElse("");
        String about = (skills + (skills.isEmpty() || coins.isEmpty() ? "" : " · ") + coins).trim();
        ItemStack head = Guis.button(Material.PLAYER_HEAD, "<green>Profile", about.isEmpty() ? "Stats, pets and recipes." : about,
            "Open");
        if (head.getItemMeta() instanceof SkullMeta meta) {
            meta.setOwningPlayer(player);
            head.setItemMeta(meta);
        }
        return head;
    }

    /** Mean level of the eight skills. */
    double skillAverage(Player player) {
        return rt.skills().map(api -> {
            double sum = 0;
            for (Skill skill : Skill.values()) {
                sum += api.level(player.getUniqueId(), skill);
            }
            return sum / Skill.values().length;
        }).orElse(0.0);
    }

    Consumer<Player> opener() {
        return this::open;
    }
}
