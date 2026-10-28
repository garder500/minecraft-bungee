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

    /** One door to another feature: slot, icon, name, description and the config key of its command. */
    private record Door(int slot, Material icon, String name, String entry, List<String> about) {}

    private static final List<Door> DOORS = List.of(
        new Door(19, Material.DIAMOND_SWORD, "Skills", "skills", List.of("Level up eight skills", "and earn permanent stats.")),
        new Door(20, Material.PAINTING, "Collections", "collections", List.of("Gather resources to", "unlock recipes and rewards.")),
        new Door(21, Material.BONE, "Pets", "pets", List.of("Manage your companions", "and their abilities.")),
        new Door(22, Material.TOTEM_OF_UNDYING, "Accessories", "accessories", List.of("Talismans and rings that", "boost your stats.")),
        new Door(23, Material.EMERALD, "Bazaar", "bazaar", List.of("Trade resources instantly", "at market prices.")),
        new Door(24, Material.GOLD_BLOCK, "Auction House", "auction", List.of("Sell rare items to", "other players.")),
        new Door(25, Material.CRAFTING_TABLE, "Recipe Book", "recipes", List.of("Browse every recipe", "you have unlocked.")),
        new Door(28, Material.GRASS_BLOCK, "Island", "island", List.of("Manage your island,", "co-op and visitors.")),
        new Door(32, Material.GOLDEN_HELMET, "Leaderboards", "leaderboards", List.of("See who is on top.")));

    private final MenuRuntime rt;
    private final QuestService quests;
    private final QuestsGui questsGui;
    private final WarpsGui warpsGui;
    private final SettingsGui settingsGui;
    private final FairySouls fairy;

    MainMenu(MenuRuntime rt, QuestService quests, QuestsGui questsGui, WarpsGui warpsGui, SettingsGui settingsGui,
            FairySouls fairy) {
        this.rt = rt;
        this.quests = quests;
        this.questsGui = questsGui;
        this.warpsGui = warpsGui;
        this.settingsGui = settingsGui;
        this.fairy = fairy;
    }

    void open(Player player) {
        quests.sync(player);
        Gui gui = rt.core.guis().create(6, "<dark_gray>Skyblock Menu");
        gui.border(Guis.icon(Material.GRAY_STAINED_GLASS_PANE, " "));
        gui.set(4, profile(player));
        for (Door door : DOORS) {
            gui.set(door.slot(), doorIcon(player, door), click -> {
                Optional<String> command = rt.commandFor(door.entry(), player);
                if (command.isPresent()) {
                    run(player, command.get());
                    click.close();
                }
            });
        }
        QuestData questData = rt.quests(player.getUniqueId());
        int doneTutorial = questData.completed.size();
        int totalTutorial = rt.config().catalog.tutorial().size();
        gui.set(29, Guis.icon(Material.WRITABLE_BOOK, "<yellow>Quests",
            "<gray>Tutorial quests and daily", "<gray>quests with streak rewards.", "",
            "<gray>Tutorial: <green>" + doneTutorial + "<gray>/" + totalTutorial,
            "<gray>Daily streak: <yellow>" + rt.engine().liveStreak(questData, quests.today()), "",
            "<yellow>Click to open!"), click -> questsGui.open(player));
        FairyData fairyData = rt.fairy(player.getUniqueId());
        gui.set(30, Guis.icon(Material.PINK_DYE, "<light_purple>Fairy Souls",
            "<gray>Hidden souls give you", "<gray>permanent stats.", "",
            "<gray>Found: <light_purple>" + fairyData.found.size() + "<gray>/" + fairy.total(), "",
            "<yellow>Click to find the nearest!"), click -> {
                click.close();
                rt.core.scheduler().nextTick(() -> fairy.hint(player));
            });
        gui.set(31, Guis.icon(Material.COMPASS, "<aqua>Warps",
            "<gray>Travel to the hub, your", "<gray>island and other places.", "", "<yellow>Click to open!"),
            click -> warpsGui.open(player));
        gui.set(33, Guis.icon(Material.COMPARATOR, "<gray>Settings",
            "<gray>Sidebar, damage numbers,", "<gray>sounds and more.", "", "<yellow>Click to open!"),
            click -> settingsGui.open(player));
        gui.set(49, Guis.icon(Material.BARRIER, "<red>Close"), Gui.Click::close);
        gui.open(player);
        rt.play(player, "click");
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
        if (rt.commandFor(door.entry(), player).isPresent()) {
            List<String> lore = new ArrayList<>();
            door.about().forEach(line -> lore.add("<gray>" + line));
            lore.add("");
            lore.add("<yellow>Click to open!");
            return Guis.icon(door.icon(), "<green>" + door.name(), lore.toArray(String[]::new));
        }
        String why = rt.commandExists(door.entry()) ? "You do not have permission." : "Not available right now.";
        return Guis.icon(Material.GRAY_DYE, "<gray>" + door.name() + " <dark_gray>(unavailable)",
            "<red>" + why);
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
