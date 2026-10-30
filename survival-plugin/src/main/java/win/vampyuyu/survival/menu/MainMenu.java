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
import win.vampyuyu.survival.api.Stat;
import win.vampyuyu.survival.core.Gui;
import win.vampyuyu.survival.core.Guis;
import win.vampyuyu.survival.core.Msg;

/** The Skyblock Menu: profile summary and a door to every other feature. */
final class MainMenu {
    /** Stats listed on the profile icon, in this order, when not zero. */
    private static final List<Stat> PROFILE_STATS = List.of(Stat.HEALTH, Stat.DEFENSE, Stat.STRENGTH,
        Stat.CRIT_CHANCE, Stat.CRIT_DAMAGE, Stat.ATTACK_SPEED, Stat.INTELLIGENCE, Stat.SPEED, Stat.MINING_SPEED,
        Stat.MINING_FORTUNE, Stat.FARMING_FORTUNE, Stat.FORAGING_FORTUNE, Stat.FISHING_SPEED, Stat.MAGIC_FIND,
        Stat.PET_LUCK);

    /** One door to another feature: icon, name, description and the config key of its command. */
    private record Door(Material icon, String name, String entry, List<String> about) {}

    /** Entries behind the Leaderboards door: the real leaderboard command, or the submenu of the two feature boards. */
    private static final List<String> BOARD_ENTRIES = List.of("leaderboards", "baltop", "skilltop");

    private static final List<Door> DOORS = List.of(
        new Door(Material.DIAMOND_SWORD, "Skills", "skills", List.of("Level up eight skills", "and earn permanent stats.")),
        new Door(Material.PAINTING, "Collections", "collections", List.of("Gather resources to", "unlock recipes and rewards.")),
        new Door(Material.BONE, "Pets", "pets", List.of("Manage your companions", "and their abilities.")),
        new Door(Material.TOTEM_OF_UNDYING, "Accessories", "accessories", List.of("Talismans and rings that", "boost your stats.")),
        new Door(Material.EMERALD, "Bazaar", "bazaar", List.of("Trade resources instantly", "at market prices.")),
        new Door(Material.GOLD_BLOCK, "Auction House", "auction", List.of("Sell rare items to", "other players.")),
        new Door(Material.GOLD_INGOT, "Bank", "bank", List.of("Keep your coins safe", "and earn interest.")),
        new Door(Material.ARMOR_STAND, "Minions", "minions", List.of("Workers that gather", "resources for you.")),
        new Door(Material.GRASS_BLOCK, "Island", "island", List.of("Manage your island,", "co-op and visitors.")),
        new Door(Material.WITHER_SKELETON_SKULL, "Bosses", "boss", List.of("Fight powerful bosses", "for great loot.")),
        new Door(Material.IRON_SWORD, "Slayer", "slayer", List.of("Hunt a personal boss", "and earn perks.")),
        new Door(Material.IRON_DOOR, "Dungeons", "dungeon", List.of("Enter the Catacombs", "with a party.")),
        new Door(Material.GOLDEN_HELMET, "Leaderboards", "leaderboards", List.of("See who is on top.")),
        new Door(Material.CRAFTING_TABLE, "Recipe Book", "recipes", List.of("Browse every recipe", "you have unlocked.")));

    /** Slots the visible doors fill, in order (a feature that is not installed leaves no gap). */
    private static final int[] DOOR_SLOTS = {19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34};

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
        Gui gui = rt.core.guis().create(6, "<dark_gray>Skyblock Menu");
        gui.border(Guis.icon(Material.GRAY_STAINED_GLASS_PANE, " "));
        gui.set(4, profile(player), click -> rt.commandFor("stats", player).ifPresent(command -> {
            click.close();
            run(player, command);
        }));
        int next = 0;
        for (Door door : DOORS) {
            if (!visible(door) || next >= DOOR_SLOTS.length) {
                continue;
            }
            gui.set(DOOR_SLOTS[next++], doorIcon(player, door), click -> clickDoor(player, door, click));
        }
        QuestData questData = rt.quests(player.getUniqueId());
        int doneTutorial = questData.completed.size();
        int totalTutorial = rt.config().catalog.tutorial().size();
        gui.set(38, Guis.icon(Material.WRITABLE_BOOK, "<yellow>Quests",
            "<gray>Tutorial quests and daily", "<gray>quests with streak rewards.", "",
            "<gray>Tutorial: <green>" + doneTutorial + "<gray>/" + totalTutorial,
            "<gray>Daily streak: <yellow>" + rt.engine().liveStreak(questData, quests.today()), "",
            "<yellow>Click to open!"), click -> questsGui.open(player));
        FairyData fairyData = rt.fairy(player.getUniqueId());
        gui.set(39, Guis.icon(Material.PINK_DYE, "<light_purple>Fairy Souls",
            "<gray>Hidden souls give you", "<gray>permanent stats.", "",
            "<gray>Found: <light_purple>" + fairyData.found.size() + "<gray>/" + fairyGui.total(), "",
            "<yellow>Click to open!"), click -> fairyGui.open(player));
        gui.set(41, Guis.icon(Material.COMPASS, "<aqua>Warps",
            "<gray>Travel to the hub, your", "<gray>island and other places.", "", "<yellow>Click to open!"),
            click -> warpsGui.open(player));
        gui.set(42, Guis.icon(Material.COMPARATOR, "<gray>Settings",
            "<gray>Sidebar, quest tracker", "<gray>and sounds.", "", "<yellow>Click to open!"),
            click -> settingsGui.open(player));
        gui.set(49, Guis.icon(Material.BARRIER, "<red>Close"), Gui.Click::close);
        gui.open(player);
        rt.play(player, "click");
    }

    /** A door is hidden when none of its commands exists at all (the feature is not installed). */
    private boolean visible(Door door) {
        if (door.entry().equals("leaderboards")) {
            return BOARD_ENTRIES.stream().anyMatch(rt::commandExists);
        }
        return rt.commandExists(door.entry());
    }

    private boolean usable(Door door, Player player) {
        if (door.entry().equals("leaderboards")) {
            return BOARD_ENTRIES.stream().anyMatch(e -> rt.commandFor(e, player).isPresent());
        }
        return rt.commandFor(door.entry(), player).isPresent();
    }

    /** True for the Island door of a player who has no island: it creates one instead. */
    private boolean createsIsland(Door door, Player player) {
        return door.entry().equals("island") && rt.islands().isPresent() && rt.createIslandCommand(player).isPresent()
            && rt.islands().get().islandOf(player.getUniqueId()).isEmpty();
    }

    private void clickDoor(Player player, Door door, Gui.Click click) {
        if (!usable(door, player)) {
            return;
        }
        click.close();
        if (createsIsland(door, player)) {
            run(player, rt.createIslandCommand(player).orElseThrow());
        } else if (door.entry().equals("leaderboards") && rt.commandFor("leaderboards", player).isEmpty()) {
            rt.core.scheduler().nextTick(() -> boardsGui.open(player));
        } else {
            run(player, rt.commandFor(door.entry(), player).orElseThrow());
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
        if (usable(door, player)) {
            if (createsIsland(door, player)) {
                return Guis.icon(Material.OAK_SAPLING, "<green>Create Island",
                    "<gray>You do not have an island yet.", "<gray>Start yours and pick a style!", "",
                    "<yellow>Click to create!");
            }
            List<String> lore = new ArrayList<>();
            door.about().forEach(line -> lore.add("<gray>" + line));
            lore.add("");
            lore.add("<yellow>Click to open!");
            return Guis.icon(door.icon(), "<green>" + door.name(), lore.toArray(String[]::new));
        }
        return Guis.icon(Material.GRAY_DYE, "<gray>" + door.name() + " <dark_gray>(unavailable)",
            "<red>You do not have permission.");
    }

    // ---------------------------------------------------------------- profile

    private ItemStack profile(Player player) {
        List<String> lore = new ArrayList<>();
        lore.add("<gray>View your Skyblock profile.");
        lore.add("");
        Optional<win.vampyuyu.survival.api.StatsApi> stats = rt.stats();
        if (stats.isPresent()) {
            for (Stat stat : PROFILE_STATS) {
                double value = stats.get().stat(player, stat);
                if (value != 0) {
                    lore.add(statLine(stat, value));
                }
            }
        } else {
            lore.add("<dark_gray>Stats are not available right now.");
        }
        lore.add("");
        Optional<win.vampyuyu.survival.api.SkillsApi> skills = rt.skills();
        lore.add("<gray>Skill average: " + (skills.isPresent()
            ? "<green>" + String.format(java.util.Locale.ROOT, "%.1f", skillAverage(player)) : "<dark_gray>n/a"));
        lore.add("<gray>Coins: " + rt.economy().map(e -> "<gold>" + Msg.grouped(e.coins(player.getUniqueId())))
            .orElse("<dark_gray>n/a"));
        Optional<IslandInfo> island = rt.islands().flatMap(api -> api.islandOf(player.getUniqueId()));
        lore.add(island.map(i -> "<gray>Island: <green>" + i.members().size() + (i.members().size() == 1 ? " member"
            : " members") + "<gray>, radius <green>" + i.radius()).orElse("<gray>Island: <red>none yet"));
        ItemStack head = Guis.icon(Material.PLAYER_HEAD, "<green>" + net.kyori.adventure.text.minimessage.MiniMessage
            .miniMessage().escapeTags(player.getName()) + "<gray>'s Profile", lore.toArray(String[]::new));
        if (head.getItemMeta() instanceof SkullMeta meta) {
            meta.setOwningPlayer(player);
            head.setItemMeta(meta);
        }
        return head;
    }

    private static String statLine(Stat stat, double value) {
        String number = value == Math.rint(value) ? Long.toString((long) value)
            : String.format(java.util.Locale.ROOT, "%.1f", value);
        String color = "<#" + String.format("%06x", stat.color().value()) + ">";
        return "<gray>" + stat.displayName() + ": " + color + stat.symbol() + number + (stat.percent() ? "%" : "");
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
