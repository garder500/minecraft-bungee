package win.vampyuyu.survival.dungeon;

import java.util.Collection;
import java.util.UUID;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import win.vampyuyu.survival.core.Core;
import win.vampyuyu.survival.core.Msg;
import win.vampyuyu.survival.dungeon.party.PartyService;
import win.vampyuyu.survival.dungeon.run.Keys;
import win.vampyuyu.survival.dungeon.run.RunContext;
import win.vampyuyu.survival.dungeon.run.RunManager;

/** The wired pieces of the dungeon feature: configuration, parties, runs and the menus. */
public final class DungeonService {
    public final Core core;
    public final RunContext ctx;
    public final RunManager runs;
    public final PartyService parties;
    public final Leaderboard leaderboard;
    public final DungeonGuis guis;
    public final Forge forge;
    /** Set by the feature once the commands exist (the menus start runs through it). */
    public DungeonCommand command;

    public DungeonService(Core core, DungeonConfig cfg, RunManager.WorldFactory worlds) {
        this.core = core;
        this.leaderboard = new Leaderboard(core.store());
        this.parties = new PartyService(this::notifyParty, System::currentTimeMillis, cfg.partyMaxSize,
            cfg.inviteExpireSeconds, cfg.readyCheckSeconds, cfg.warpCooldownSeconds, cfg.membersCanInvite);
        this.ctx = new RunContext(core, cfg, new Keys(core.getPlugin()), leaderboard, parties);
        this.runs = new RunManager(ctx, worlds);
        this.guis = new DungeonGuis(this);
        this.forge = new Forge(this);
    }

    public DungeonConfig cfg() { return ctx.cfg; }

    /** Party notifications are plain text (player names and chat never parse as markup). */
    private void notifyParty(Collection<UUID> to, String text) {
        String line = text.startsWith("[Party]") ? text : "[Party] " + text;
        for (UUID id : to) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) {
                p.sendMessage(Msg.plain(line, NamedTextColor.LIGHT_PURPLE));
            }
        }
    }

    /** Re-reads dungeon.yml. Running dungeons keep the floor they were built with; limits apply at once. */
    public java.util.List<String> reload() {
        DungeonConfig fresh = DungeonConfig.parse(core.featureConfig("dungeon"));
        ctx.cfg = fresh;
        parties.configure(fresh.partyMaxSize, fresh.inviteExpireSeconds, fresh.readyCheckSeconds, fresh.warpCooldownSeconds,
            fresh.membersCanInvite);
        return fresh.problems();
    }
}
