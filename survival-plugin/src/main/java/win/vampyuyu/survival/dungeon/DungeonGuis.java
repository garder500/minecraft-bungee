package win.vampyuyu.survival.dungeon;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import win.vampyuyu.survival.api.Skill;
import win.vampyuyu.survival.core.Gui;
import win.vampyuyu.survival.core.Guis;
import win.vampyuyu.survival.core.Msg;
import win.vampyuyu.survival.dungeon.party.Party;
import win.vampyuyu.survival.dungeon.run.DungeonRun;

/** The menus of the dungeon: floors, party and leaderboard. Text is tooltip text only. */
public final class DungeonGuis {
    private final DungeonService svc;

    public DungeonGuis(DungeonService svc) {
        this.svc = svc;
    }

    private static Material material(String palette) {
        return switch (palette) {
            case "mossy" -> Material.MOSSY_STONE_BRICKS;
            case "deepslate" -> Material.DEEPSLATE_BRICKS;
            case "blackstone" -> Material.POLISHED_BLACKSTONE_BRICKS;
            case "nether" -> Material.NETHER_BRICKS;
            case "end" -> Material.PURPUR_BLOCK;
            case "crimson" -> Material.CRIMSON_PLANKS;
            default -> Material.STONE_BRICKS;
        };
    }

    private static String clock(long millis) {
        long s = millis / 1000;
        return String.format("%d:%02d", s / 60, s % 60);
    }

    // ------------------------------------------------------------------ floors

    /** Why the player cannot start the floor, or null. */
    String lockReason(Player p, DungeonConfig.Floor f) {
        DungeonData d = svc.core.profiles().section(p.getUniqueId(), "dungeon", DungeonData.class, DungeonData::new);
        if (svc.cfg().requirePreviousFloor && f.number() > 1 && !d.cleared(f.number() - 1)) {
            return "Clear floor " + (f.number() - 1) + " first.";
        }
        if (f.minCombatLevel() > 0) {
            var skills = svc.core.service(win.vampyuyu.survival.api.SkillsApi.class);
            if (skills.isPresent() && skills.get().level(p.getUniqueId(), Skill.COMBAT) < f.minCombatLevel()) {
                return "Needs Combat level " + f.minCombatLevel() + ".";
            }
        }
        return null;
    }

    public void floors(Player p) {
        DungeonConfig cfg = svc.cfg();
        DungeonData data = svc.core.profiles().section(p.getUniqueId(), "dungeon", DungeonData.class, DungeonData::new);
        Gui gui = svc.core.guis().create(5, "<dark_red><bold>The Catacombs");
        gui.border(Guis.icon(Material.BLACK_STAINED_GLASS_PANE, " "));
        List<DungeonConfig.Floor> floors = new ArrayList<>(cfg.floors.values());
        gui.paginate(floors, gui.innerSlots(), f -> floorIcon(p, f, data), (click, f) -> {
            click.close();
            if (svc.command != null) {
                svc.command.start(click.player(), f.number(), null);
            }
        }, 39, 41);
        gui.set(37, Guis.icon(Material.PLAYER_HEAD, "<light_purple>Party", partyLine(p), "<yellow>Click to manage"),
            c -> party(c.player()));
        gui.set(38, Guis.icon(Material.GOLDEN_HELMET, "<gold>Leaderboard", "<gray>Fastest clears per floor"),
            c -> leaderboard(c.player(), 1));
        int waiting = data.pendingCount();
        gui.set(42, Guis.icon(waiting > 0 ? Material.CHEST_MINECART : Material.MINECART, "<green>Claim rewards",
            waiting > 0 ? "<gray>" + waiting + " item(s) waiting" : "<gray>Nothing waiting",
            waiting > 0 ? "<yellow>Click to claim" : ""), c -> {
                c.close();
                svc.core.getPlugin().getServer().dispatchCommand(c.player(), "dungeon claim");
            });
        gui.set(43, Guis.icon(Material.BOOK, "<aqua>Your record", "<gray>Runs: <white>" + data.totalRuns,
            "<gray>Clears: <white>" + data.totalClears, "<gray>Highest floor: <white>" + data.highestFloorCleared),
            c -> {
                c.close();
                svc.core.getPlugin().getServer().dispatchCommand(c.player(), "dungeon stats");
            });
        int level = Catacombs.level(cfg, data.catacombsXp);
        gui.set(36, Guis.icon(Material.EXPERIENCE_BOTTLE, "<aqua>Catacombs level " + level,
            "<gray>Reward bonus: <white>+" + String.format("%.0f", Catacombs.bonusPercent(cfg, level)) + "%",
            level >= cfg.catacombsMaxLevel ? "<gray>Max level" : "<gray>Next level at <white>"
                + Catacombs.xpForLevel(cfg, level + 1) + " <gray>XP (you have <white>" + data.catacombsXp + "<gray>)"));
        gui.set(44, Guis.icon(Material.ANVIL, "<gold>Crypt Forge", "<gray>Trade dungeon materials for", "<gray>consumables, caches and coins",
            "<yellow>Click to open"), c -> {
                if (svc.runs.runOf(c.player().getUniqueId()) == null) {
                    svc.forge.open(c.player());
                }
            });
        DungeonRun run = svc.runs.runOf(p.getUniqueId());
        if (run != null) {
            gui.set(40, Guis.icon(Material.RED_BED, "<red>Leave the dungeon", "<gray>Abandon the current run"), c -> {
                c.close();
                svc.runs.leave(c.player());
            });
        } else {
            gui.set(40, Guis.icon(Material.BARRIER, "<red>Close"), c -> c.close());
        }
        gui.open(p);
    }

    private String partyLine(Player p) {
        Party party = svc.parties.partyOf(p.getUniqueId());
        return party == null ? "<gray>You play alone" : "<gray>Members: <white>" + party.size() + "/" + svc.parties.maxSize();
    }

    private ItemStack floorIcon(Player p, DungeonConfig.Floor f, DungeonData data) {
        String lock = lockReason(p, f);
        DungeonData.FloorStats s = data.floors.get(Integer.toString(f.number()));
        List<String> lore = new ArrayList<>();
        lore.add("<gray>" + f.rooms() + " rooms, " + f.puzzles() + " puzzles, " + f.secretRooms() + " secret rooms");
        lore.add("<gray>" + f.miniBosses() + " mini-bosses, " + f.witherDoors() + " wither doors");
        lore.add("<gray>Monsters: level <white>" + f.mobLevel() + "<gray>, health x<white>" + f.hp() + "<gray>, damage x<white>" + f.damage());
        lore.add("<gray>Par time: <white>" + clock(f.parSeconds() * 1000L) + "<gray>, rewards x<white>" + f.reward());
        if (s != null && s.runs > 0) {
            lore.add("");
            lore.add("<gray>Runs: <white>" + s.runs + "<gray>, cleared: <white>" + s.clears);
            if (s.bestMillis > 0) {
                lore.add("<gray>Best: <white>" + clock(s.bestMillis) + " <gray>(" + s.bestGrade + ", " + s.bestScore + ")");
            }
        }
        lore.add("");
        lore.add(lock == null ? "<yellow>Click to start" : "<red>" + lock);
        Material m = lock == null ? material(f.palette()) : Material.RED_STAINED_GLASS_PANE;
        return Guis.icon(m, (lock == null ? "<gold><bold>" : "<red><bold>") + "Floor " + f.number() + " <gray>- <white>" + f.name(),
            lore.toArray(new String[0]));
    }

    // ------------------------------------------------------------------ party

    public void party(Player p) {
        UUID id = p.getUniqueId();
        Party party = svc.parties.partyOf(id);
        Gui gui = svc.core.guis().create(3, "<light_purple><bold>Dungeon party");
        gui.fill(Guis.icon(Material.BLACK_STAINED_GLASS_PANE, " "));
        if (party == null) {
            gui.set(13, Guis.icon(Material.EMERALD, "<green>Create a party", "<gray>Then invite friends with /party invite"),
                c -> {
                    var r = svc.parties.create(id, p.getName());
                    svc.core.msg().ok(c.player(), r.message());
                    party(c.player());
                });
            gui.set(22, Guis.icon(Material.ARROW, "<gray>Back"), c -> floors(c.player()));
            gui.open(p);
            return;
        }
        boolean leader = party.isLeader(id);
        int slot = 9;
        for (UUID m : party.members()) {
            if (slot > 13) {
                break;
            }
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            if (head.getItemMeta() instanceof SkullMeta meta) {
                meta.setOwningPlayer(Bukkit.getOfflinePlayer(m));
                meta.displayName(Msg.component((party.isLeader(m) ? "<gold>" : "<white>") + party.nameOf(m)));
                List<Component> lore = new ArrayList<>();
                lore.add(Msg.component(party.isLeader(m) ? "<gray>Party leader" : "<gray>Member"));
                if (leader && !m.equals(id)) {
                    lore.add(Msg.component("<yellow>Left-click: make leader"));
                    lore.add(Msg.component("<red>Shift-click: remove"));
                }
                meta.lore(lore);
                head.setItemMeta(meta);
            }
            gui.set(slot++, head, click -> {
                if (!leader || m.equals(id)) {
                    return;
                }
                var r = click.shift() ? svc.parties.kick(id, m) : svc.parties.promote(id, m);
                if (r.ok()) {
                    svc.core.msg().ok(click.player(), r.message());
                } else {
                    svc.core.msg().error(click.player(), r.message());
                }
                party(click.player());
            });
        }
        gui.set(15, Guis.icon(Material.NAME_TAG, "<aqua>Invite a player", "<gray>Click, then type the name",
            "<gray>Up to " + svc.parties.maxSize() + " players"), c -> {
                c.close();
                c.player().sendMessage(Msg.component("<aqua>Click to invite: ").append(Msg.component("<yellow>[/party invite ]")
                    .clickEvent(ClickEvent.suggestCommand("/party invite "))));
            });
        gui.set(16, Guis.icon(Material.LIME_DYE, "<green>Ready check", "<gray>Ask everyone to confirm"), c -> {
            var r = svc.parties.startReadyCheck(id, o -> { });
            c.close();
            if (r.ok()) {
                svc.core.msg().ok(c.player(), r.message());
            } else {
                svc.core.msg().error(c.player(), r.message());
            }
        });
        gui.set(20, Guis.icon(Material.ENDER_PEARL, "<light_purple>Warp party", "<gray>Pull everyone to you"),
            c -> {
                c.close();
                svc.core.getPlugin().getServer().dispatchCommand(c.player(), "party warp");
            });
        gui.set(22, Guis.icon(Material.OAK_SIGN, svc.parties.partyChat(id) ? "<green>Party chat: ON" : "<gray>Party chat: OFF",
            "<gray>Click to toggle"), c -> {
                svc.parties.togglePartyChat(id);
                party(c.player());
            });
        gui.set(24, Guis.icon(Material.RED_DYE, leader ? "<red>Disband party" : "<red>Leave party"), c -> {
            c.close();
            var r = leader ? svc.parties.disband(id) : svc.parties.leave(id);
            if (r.ok()) {
                svc.core.msg().ok(c.player(), r.message());
            } else {
                svc.core.msg().error(c.player(), r.message());
            }
        });
        gui.set(26, Guis.icon(Material.ARROW, "<gray>Back to the floors"), c -> floors(c.player()));
        gui.open(p);
    }

    // ------------------------------------------------------------------ leaderboard

    public void leaderboard(Player p, int floor) {
        DungeonConfig cfg = svc.cfg();
        int f = Math.max(1, Math.min(cfg.floorCount(), floor));
        Gui gui = svc.core.guis().create(4, "<gold><bold>Leaderboard - Floor " + f);
        gui.fill(Guis.icon(Material.BLACK_STAINED_GLASS_PANE, " "));
        DungeonConfig.Floor fl = cfg.floor(f);
        gui.set(4, Guis.icon(material(fl.palette()), "<gold>Floor " + f + " <white>" + fl.name(), "<gray>Fastest clears"));
        if (f > 1) {
            gui.set(3, Guis.icon(Material.ARROW, "<yellow>Floor " + (f - 1)), c -> leaderboard(c.player(), f - 1));
        }
        if (f < cfg.floorCount()) {
            gui.set(5, Guis.icon(Material.ARROW, "<yellow>Floor " + (f + 1)), c -> leaderboard(c.player(), f + 1));
        }
        List<Leaderboard.Entry> top = svc.leaderboard.top(f);
        int[] slots = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21};
        if (top.isEmpty()) {
            gui.set(13, Guis.icon(Material.COBWEB, "<gray>Nobody has cleared this floor yet"));
        }
        for (int i = 0; i < top.size() && i < slots.length; i++) {
            Leaderboard.Entry e = top.get(i);
            Material m = i == 0 ? Material.GOLD_INGOT : i == 1 ? Material.IRON_INGOT : i == 2 ? Material.COPPER_INGOT : Material.PAPER;
            List<String> lore = new ArrayList<>();
            lore.add("<gray>Score: <white>" + e.score + " <gray>(" + e.grade + ")");
            lore.add("<gray>Party:");
            for (String n : e.names) {
                lore.add("<dark_gray> - <white>" + n.replace("<", ""));
            }
            gui.set(slots[i], Guis.icon(m, "<gold>#" + (i + 1) + " <white>" + clock(e.millis), lore.toArray(new String[0])));
        }
        gui.set(31, Guis.icon(Material.ARROW, "<gray>Back"), c -> floors(c.player()));
        gui.open(p);
    }
}
