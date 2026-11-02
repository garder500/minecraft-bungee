package win.vampyuyu.survival.station;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import win.vampyuyu.survival.core.Core;
import win.vampyuyu.survival.core.Gui;
import win.vampyuyu.survival.core.Guis;
import win.vampyuyu.survival.core.Msg;
import win.vampyuyu.survival.station.StationConfig.Brew;
import win.vampyuyu.survival.station.StationConfig.Contract;
import win.vampyuyu.survival.station.StationConfig.Product;

/** The four menus: Alembic, Crucible, Ranch and the Contract Hunter's board. Each re-renders itself after a click. */
final class StationGui {
    private static final int FIRST = 9;
    private static final int LAST = 35;

    private final Core core;
    private final StationService service;

    StationGui(Core core, StationService service) {
        this.core = core;
        this.service = service;
    }

    // ---------------------------------------------------------------- Alembic

    void alembic(Player player) {
        Gui gui = core.guis().create(6, "<dark_gray>Alembic");
        renderAlembic(gui, player);
        gui.open(player);
    }

    private void renderAlembic(Gui gui, Player player) {
        service.advance(player);
        StationConfig cfg = service.config();
        StationData d = service.data(player.getUniqueId());
        int tier = service.tier(player.getUniqueId());
        for (int s = 0; s < 54; s++) {
            gui.clear(s);
        }
        int slot = FIRST;
        for (Brew brew : cfg.brews().values()) {
            if (slot > LAST) {
                break;
            }
            List<String> lore = new ArrayList<>();
            lore.add("<gray>" + family(brew.family()) + " - takes " + Msg.duration(brew.seconds() * 1000L));
            lore.add("<gray>" + service.describe(brew));
            lore.add("");
            for (StationConfig.Ingredient i : brew.cost()) {
                boolean ok = service.held(player, i.item()) >= i.amount();
                lore.add((ok ? "<green>" : "<red>") + "- " + i.amount() + " x " + service.label(i.item()));
            }
            lore.add("");
            if (tier < brew.minTier()) {
                lore.add("<red>Needs generator tier " + brew.minTier());
            } else {
                lore.add(Guis.ACCENT + "▸ Brew");
            }
            gui.set(slot++, Guis.icon(icon(brew.family()), "<white>" + brew.name(), lore.toArray(new String[0])), click -> {
                String problem = service.enqueue(player, brew.id());
                if (problem != null) {
                    core.msg().warn(player, problem);
                } else {
                    core.msg().ok(player, brew.name() + " is brewing.");
                }
                renderAlembic(gui, player);
            });
        }
        // the queue on the last row, the ready brews on the one above
        long now = System.currentTimeMillis();
        int q = 45;
        for (StationData.Job job : d.jobs) {
            if (q > 52) {
                break;
            }
            Brew brew = cfg.brews().get(job.recipe);
            String name = brew == null ? job.recipe : brew.name();
            gui.set(q++, Guis.icon(Material.CLOCK, "<yellow>" + name,
                job.endAt == 0 ? "<gray>Waiting for a free slot" : "<gray>Ready in " + Msg.duration(Math.max(0, job.endAt - now))));
        }
        int ready = d.ready.size();
        gui.set(53, Guis.icon(ready > 0 ? Material.HONEY_BOTTLE : Material.GLASS_BOTTLE,
            ready > 0 ? "<green>Collect " + ready + " flask" + (ready == 1 ? "" : "s") : "<gray>Nothing to collect",
            "<gray>" + cfg.alembicSlots + " brews run at once, " + cfg.alembicQueue + " fit in the queue."), click -> {
            int n = service.collect(player);
            if (n > 0) {
                core.msg().ok(player, "You collected " + n + " flask" + (n == 1 ? "" : "s") + ".");
            }
            renderAlembic(gui, player);
        });
    }

    private static String family(String family) {
        return switch (family) {
            case "tonic" -> "Tonic";
            case "oil" -> "Oil";
            default -> "Draught";
        };
    }

    private static Material icon(String family) {
        return switch (family) {
            case "tonic" -> Material.POTION;
            case "oil" -> Material.GLOW_INK_SAC;
            default -> Material.HONEY_BOTTLE;
        };
    }

    // ---------------------------------------------------------------- Crucible

    void crucible(Player player) {
        Gui gui = core.guis().create(3, "<dark_gray>Crucible");
        renderCrucible(gui, player);
        gui.open(player);
    }

    private void renderCrucible(Gui gui, Player player) {
        for (int s = 0; s < gui.size(); s++) {
            gui.clear(s);
        }
        ItemStack hand = player.getInventory().getItemInMainHand();
        Optional<String> id = core.items().idOf(hand);
        Map<String, Integer> back = id.map(i -> service.meltDown(i, player)).orElse(Map.of());
        List<String> lore = new ArrayList<>();
        if (back.isEmpty()) {
            lore.add("<gray>Hold an item the Forge can make,");
            lore.add("<gray>then come back here.");
        } else {
            lore.add("<gray>You get back:");
            for (Map.Entry<String, Integer> e : back.entrySet()) {
                lore.add("<green>- " + e.getValue() + " x " + service.label(e.getKey()));
            }
            lore.add("");
            lore.add(Guis.ACCENT + "▸ Melt one");
        }
        gui.set(11, Guis.icon(back.isEmpty() ? Material.CAULDRON : Material.LAVA_BUCKET,
            "<white>Melt down the held item",
            lore.toArray(new String[0])), click -> {
            String problem = service.melt(player);
            if (problem != null) {
                core.msg().warn(player, problem);
            } else {
                core.msg().ok(player, "The Crucible melted the item.");
            }
            renderCrucible(gui, player);
        });
        Map<String, Integer> dup = service.duplicates(player);
        List<String> dupLore = new ArrayList<>();
        long shards = 0;
        if (dup.isEmpty()) {
            dupLore.add("<gray>Hold two copies of a boss component you");
            dupLore.add("<gray>already found: the extra ones become Shards.");
        } else {
            for (Map.Entry<String, Integer> e : dup.entrySet()) {
                dupLore.add("<aqua>- " + e.getValue() + " x " + service.label("custom:" + e.getKey()));
                shards += (long) e.getValue() * service.config().duplicateShards;
            }
            dupLore.add("");
            dupLore.add(Guis.ACCENT + "▸ Convert for " + shards + " Memory Shards");
        }
        gui.set(15, Guis.icon(dup.isEmpty() ? Material.AMETHYST_SHARD : Material.ECHO_SHARD, "<aqua>Duplicate components",
            dupLore.toArray(new String[0])), click -> {
            long paid = service.convertDuplicates(player);
            if (paid > 0) {
                core.msg().ok(player, "You turned duplicates into " + paid + " Memory Shards.");
            } else {
                core.msg().warn(player, "You hold no duplicate component.");
            }
            renderCrucible(gui, player);
        });
    }

    // ---------------------------------------------------------------- Ranch

    void ranch(Player player) {
        Gui gui = core.guis().create(3, "<dark_gray>Ranch");
        renderRanch(gui, player);
        gui.open(player);
    }

    private void renderRanch(Gui gui, Player player) {
        for (int s = 0; s < gui.size(); s++) {
            gui.clear(s);
        }
        UUID id = player.getUniqueId();
        int tier = service.tier(id);
        StationConfig cfg = service.config();
        Map<Product, Integer> stock = service.ranchStock(id, System.currentTimeMillis());
        int slot = 10;
        int total = 0;
        for (Map.Entry<Product, Integer> e : stock.entrySet()) {
            Product p = e.getKey();
            List<String> lore = new ArrayList<>();
            if (tier < p.minTier()) {
                lore.add("<red>Needs generator tier " + p.minTier());
            } else {
                double perHour = 3600.0 / p.seconds() * (1 + cfg.tierBonus * (tier - 1));
                lore.add("<gray>About " + Msg.number(Math.round(perHour * 10) / 10.0) + " per hour at your tier");
                lore.add("<green>" + e.getValue() + " waiting");
                total += e.getValue();
            }
            gui.set(slot++, Guis.icon(Optional.ofNullable(service.stack(p.item(), 1)).map(ItemStack::getType).orElse(Material.HAY_BLOCK),
                "<white>" + service.label(p.item()), lore.toArray(new String[0])));
        }
        int waiting = total;
        gui.set(16, Guis.icon(waiting > 0 ? Material.CHEST : Material.BARREL,
            waiting > 0 ? "<green>Collect " + waiting + " items" : "<gray>Nothing to collect",
            "<gray>The Ranch keeps " + cfg.storageHours + " hours of output.",
            "<gray>Higher generator tiers make it produce more."), click -> {
            int n = service.collectRanch(player);
            if (n > 0) {
                core.msg().ok(player, "You collected " + n + " items from the Ranch.");
            }
            renderRanch(gui, player);
        });
    }

    // ---------------------------------------------------------------- Contracts

    void contracts(Player player) {
        Gui gui = core.guis().create(3, "<dark_gray>Contract Hunter");
        renderContracts(gui, player);
        gui.open(player);
    }

    private void renderContracts(Gui gui, Player player) {
        for (int s = 0; s < gui.size(); s++) {
            gui.clear(s);
        }
        StationConfig cfg = service.config();
        StationData d = service.contracts(player);
        int slot = 10;
        for (String id : d.offered) {
            Contract c = cfg.contracts().get(id);
            if (c == null) {
                continue;
            }
            boolean done = d.done.contains(id);
            int have = service.progress(player, d, c);
            List<String> lore = new ArrayList<>();
            lore.add("<gray>" + goal(c));
            lore.add("<gray>Progress: <white>" + have + "/" + c.amount());
            lore.add("");
            lore.add("<gray>Reward: <gold>" + Msg.coins(c.coins())
                + (c.shards() > 0 ? "<gray>, <aqua>" + c.shards() + " Memory Shards" : "")
                + (c.rewardItem() != null && !c.rewardItem().isBlank() ? "<gray>, " + c.rewardAmount() + " x " + service.label(c.rewardItem()) : ""));
            lore.add(done ? "<green>Done for today" : have >= c.amount() ? Guis.ACCENT + "▸ Turn in" : "<dark_gray>Not finished");
            Material material = done ? Material.LIME_DYE : have >= c.amount() ? Material.WRITTEN_BOOK : Material.PAPER;
            gui.set(slot++, Guis.icon(material, "<white>" + c.title(), lore.toArray(new String[0])), click -> {
                String problem = service.turnIn(player, id);
                if (problem != null) {
                    core.msg().warn(player, problem);
                }
                renderContracts(gui, player);
            });
        }
        gui.set(16, Guis.icon(Material.CLOCK, "<yellow>New contracts every day",
            "<gray>The board changes at 00:00 UTC.",
            "<gray>Harder contracts appear as your generator grows.",
            "<gray>Paid today: <gold>" + Msg.coins(d.coinsToday) + "<gray> of " + Msg.coins(cfg.dailyCoinCap)));
    }

    private String goal(Contract c) {
        return switch (c.kind()) {
            case "kill" -> "Kill " + c.amount() + " " + pretty(c.target()) + ".";
            case "boss" -> "Defeat " + pretty(c.target()) + " in a Bastion or the open world.";
            default -> "Bring " + c.amount() + " x " + service.label(c.target()) + ".";
        };
    }

    private static String pretty(String raw) {
        String s = raw.toLowerCase(java.util.Locale.ROOT).replace('_', ' ');
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
