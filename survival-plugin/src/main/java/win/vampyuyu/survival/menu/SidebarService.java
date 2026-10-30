package win.vampyuyu.survival.menu;

import io.papermc.paper.scoreboard.numbers.NumberFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Criteria;
import win.vampyuyu.survival.api.IslandInfo;
import win.vampyuyu.survival.core.Msg;
import win.vampyuyu.survival.core.Scheduler;

/**
 * Per-player Hypixel style sidebar. Each player in a Skyblock world gets a private scoreboard (so nothing shared flickers)
 * whose lines are fixed entries with custom names and no score numbers; a line is rewritten only when its text
 * changes. The main scoreboard is put back as soon as the player leaves Skyblock, switches the sidebar off, or the
 * feature is disabled. The action bar and the TAB list are not touched.
 */
final class SidebarService implements Listener {
    private static final int MAX_LINES = 15;

    private static final class State {
        final Scoreboard board;
        final Objective objective;
        String title = "";
        List<String> lines = List.of();

        State(Scoreboard board, Objective objective) {
            this.board = board;
            this.objective = objective;
        }
    }

    private final MenuRuntime rt;
    private final QuestService quests;
    private final Map<UUID, State> states = new HashMap<>();
    private final Map<UUID, String> ownerNames = new HashMap<>();
    private Scheduler.Handle task;
    private boolean numberFormatBroken;
    private boolean customNamesBroken;

    SidebarService(MenuRuntime rt, QuestService quests) {
        this.rt = rt;
        this.quests = quests;
    }

    void start() {
        task = rt.core.scheduler().repeating(20, rt.config().sidebarUpdateTicks, this::updateAll);
    }

    void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        for (UUID id : new ArrayList<>(states.keySet())) {
            Player player = Bukkit.getPlayer(id);
            if (player != null) {
                clear(player);
            }
        }
        states.clear();
    }

    private void updateAll() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            try {
                refresh(player);
            } catch (RuntimeException e) {
                rt.core.getPlugin().getLogger().warning("Sidebar update failed for " + player.getName() + ": " + e);
            }
        }
    }

    /** Redraws the player's sidebar now (settings changed, world changed). */
    void refresh(Player player) {
        MenuConfig cfg = rt.config();
        boolean want = cfg.sidebarEnabled && player.isOnline() && MenuRuntime.inSkyblock(player.getWorld())
            && rt.prefs(player.getUniqueId()).sidebar;
        if (!want) {
            clear(player);
            return;
        }
        List<String> lines = build(player);
        State state = states.get(player.getUniqueId());
        if (state == null || player.getScoreboard() != state.board) {
            state = create(player);
        }
        if (!cfg.sidebarTitle.equals(state.title)) {
            state.objective.displayName(Msg.component(cfg.sidebarTitle));
            state.title = cfg.sidebarTitle;
        }
        apply(state, lines);
    }

    private State create(Player player) {
        Scoreboard board = Bukkit.getScoreboardManager().getNewScoreboard();
        Objective objective = board.registerNewObjective("sbside", Criteria.DUMMY, Component.text("SKYBLOCK"));
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        if (!numberFormatBroken) {
            try {
                objective.numberFormat(NumberFormat.blank());
            } catch (RuntimeException | LinkageError e) {
                numberFormatBroken = true;
            }
        }
        State state = new State(board, objective);
        states.put(player.getUniqueId(), state);
        player.setScoreboard(board);
        return state;
    }

    private static String entry(int index) {
        return "§" + Integer.toHexString(index) + "§r";
    }

    /** Rewrites only the lines whose text differs from what is on screen. */
    private void apply(State state, List<String> lines) {
        List<String> next = lines.size() > MAX_LINES ? lines.subList(0, MAX_LINES) : lines;
        int count = next.size();
        for (int i = 0; i < count; i++) {
            String text = next.get(i);
            boolean changed = i >= state.lines.size() || !state.lines.get(i).equals(text);
            int score = count - i;
            var line = state.objective.getScore(entry(i));
            if (changed && !customNamesBroken) {
                try {
                    line.customName(text.isEmpty() ? Component.text(" ") : Msg.component(text));
                } catch (RuntimeException e) {
                    // A server without per-score display names: the lines stay blank instead of failing every second.
                    customNamesBroken = true;
                }
            }
            if (i >= state.lines.size() || state.lines.size() != count) {
                line.setScore(score);
            }
        }
        for (int i = count; i < state.lines.size(); i++) {
            state.board.resetScores(entry(i));
        }
        state.lines = List.copyOf(next);
    }

    /** Restores the main scoreboard when the player is showing ours. */
    void clear(Player player) {
        State state = states.remove(player.getUniqueId());
        if (state != null && player.isOnline() && player.getScoreboard() == state.board) {
            player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
        }
    }

    // ---------------------------------------------------------------- content

    List<String> build(Player player) {
        MenuConfig cfg = rt.config();
        World world = player.getWorld();
        MenuData prefs = rt.prefs(player.getUniqueId());
        String coins = rt.economy().map(e -> Msg.grouped(e.coins(player.getUniqueId()))).orElse(null);
        String members = rt.islands().flatMap(api -> api.islandOf(player.getUniqueId())).map(island -> {
            long online = island.members().stream().filter(m -> {
                Player p = Bukkit.getPlayer(m);
                return p != null && p.isOnline();
            }).count();
            return online + "/" + island.members().size();
        }).orElse(null);
        String questName = null;
        String questProgress = null;
        if (prefs.questTracker) {
            Optional<QuestEngine.Tracked> tracked = quests.tracked(player);
            if (tracked.isPresent()) {
                questName = tracked.get().def().name();
                questProgress = tracked.get().def().amount() > 1
                    ? "(" + Msg.grouped(tracked.get().progress()) + "/" + Msg.grouped(tracked.get().def().amount()) + ")"
                    : null;
            }
        }
        return SidebarLines.build(new SidebarLines.View(SkyCalendar.date(world.getFullTime()),
            SkyCalendar.clock(world.getTime()), SkyCalendar.isDay(world.getTime()), zone(player), coins, members,
            questName, questProgress, cfg.sidebarFooter));
    }

    String zone(Player player) {
        MenuConfig cfg = rt.config();
        Location loc = player.getLocation();
        String world = loc.getWorld().getName();
        Optional<String> named = cfg.zones.find(world, loc.getX(), loc.getY(), loc.getZ());
        if (named.isPresent()) {
            return named.get();
        }
        Optional<IslandInfo> island = rt.islands().flatMap(api -> api.islandAt(loc));
        if (island.isPresent()) {
            IslandInfo info = island.get();
            if (info.members().contains(player.getUniqueId())) {
                return "Your Island";
            }
            return ownerName(info.owner()) + "'s Island";
        }
        return cfg.defaultZones.getOrDefault(MenuConfig.worldToken(world), world);
    }

    private String ownerName(UUID owner) {
        return ownerNames.computeIfAbsent(owner, id -> {
            String name = Bukkit.getOfflinePlayer(id).getName();
            return name == null ? "Someone" : name;
        });
    }

    // ---------------------------------------------------------------- events

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        rt.core.scheduler().nextTick(() -> refresh(player));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        states.remove(event.getPlayer().getUniqueId());
    }
}
