package win.vampyuyu.survival.items.hud;

import java.util.Optional;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import win.vampyuyu.survival.api.SkillsApi;
import win.vampyuyu.survival.api.Stat;
import win.vampyuyu.survival.api.event.SkillXpEvent;
import win.vampyuyu.survival.core.Core;
import win.vampyuyu.survival.core.Scheduler;
import win.vampyuyu.survival.items.ItemsSettings;
import win.vampyuyu.survival.items.stats.PlayerState;
import win.vampyuyu.survival.items.stats.StatsEngine;

/**
 * The ONLY writer of the action bar. Redraws four times a second per Skyblock player but only sends the line when
 * its text changed (plus a keep-alive resend, because the client fades an action bar after about three seconds).
 */
public final class HudService implements Listener {
    private final Core core;
    private final StatsEngine engine;
    private Scheduler.Handle task;
    private long lastTick = System.currentTimeMillis();

    public HudService(Core core, StatsEngine engine) {
        this.core = core;
        this.engine = engine;
    }

    public void start() {
        task = core.scheduler().repeating(5, engine.settings().hudPeriodTicks, this::tick);
    }

    private XpProgress progress;
    private ItemsSettings progressSource;

    public void stop() {
        if (task != null) {
            task.cancel();
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            engine.existing(player.getUniqueId()).ifPresent(st -> st.lastHud = "");
        }
    }

    private void tick() {
        long now = System.currentTimeMillis();
        double seconds = Math.max(0.05, Math.min(2.0, (now - lastTick) / 1000.0));
        lastTick = now;
        ItemsSettings s = engine.settings();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!engine.active(player) || player.isDead()) {
                continue;
            }
            PlayerState st = engine.state(player);
            engine.tickMana(player, seconds);
            Component line = render(player, st, now);
            String plain = HudFormat.plain(line);
            if (!plain.equals(st.lastHud) || now - st.lastHudAt >= s.hudKeepAliveTicks * 50L) {
                st.lastHud = plain;
                st.lastHudAt = now;
                player.sendActionBar(line);
            }
        }
    }

    /** The line for a player right now. */
    public Component render(Player player, PlayerState st, long now) {
        var attribute = player.getAttribute(Attribute.MAX_HEALTH);
        double max = attribute == null ? 20 : attribute.getValue();
        Component centre = null;
        if (st.noticeUntil > now && !st.notice.isEmpty()) {
            centre = HudFormat.legacy(st.notice);
        } else if (st.xpPopupUntil > now && !st.xpPopup.isEmpty()) {
            centre = Component.text(st.xpPopup, NamedTextColor.DARK_AQUA);
        }
        return HudFormat.line(player.getHealth(), max, player.getAbsorptionAmount(), st.stat(Stat.DEFENSE), centre,
            st.mana.mana(), st.stat(Stat.INTELLIGENCE), st.mana.overflow());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSkillXp(SkillXpEvent event) {
        Player player = event.getPlayer();
        if (!engine.active(player) || event.getAmount() <= 0) {
            return;
        }
        double gained = event.getAmount();
        String skill = event.getSkill().displayName();
        var skills = core.service(SkillsApi.class);
        // The event fires before the grant: read level and total on the next tick.
        core.scheduler().nextTick(() -> {
            if (!player.isOnline()) {
                return;
            }
            PlayerState st = engine.state(player);
            Optional<SkillsApi> api = skills;
            String text = api.map(a -> xpText(a, player, event.getSkill(), skill, gained))
                .orElse(HudFormat.gain(gained) + " " + skill);
            st.xpPopup = text;
            st.xpPopupUntil = System.currentTimeMillis() + engine.settings().xpPopupSeconds * 1000L;
        });
    }

    private String xpText(SkillsApi api, Player player, win.vampyuyu.survival.api.Skill skill, String name,
            double gained) {
        var progress = progress().of(api, player.getUniqueId(), skill);
        if (progress.isPresent()) {
            return HudFormat.xpProgressPopup(name, gained, progress.get().into(), progress.get().needed());
        }
        return HudFormat.xpPopup(name, gained, api.level(player.getUniqueId(), skill), api.xp(player.getUniqueId(), skill));
    }

    private XpProgress progress() {
        ItemsSettings s = engine.settings();
        if (progress == null || progressSource != s) {
            progress = XpProgress.from(s.root());
            progressSource = s;
        }
        return progress;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        engine.existing(event.getPlayer().getUniqueId()).ifPresent(st -> st.lastHud = "");
    }
}
