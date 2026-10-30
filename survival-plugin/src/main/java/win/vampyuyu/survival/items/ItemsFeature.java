package win.vampyuyu.survival.items;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import win.vampyuyu.survival.api.StatsApi;
import win.vampyuyu.survival.core.Core;
import win.vampyuyu.survival.core.Feature;
import win.vampyuyu.survival.core.Scheduler;
import win.vampyuyu.survival.items.ability.AbilityEnv;
import win.vampyuyu.survival.items.ability.AbilityListener;
import win.vampyuyu.survival.items.ability.AbilityRegistry;
import win.vampyuyu.survival.items.ability.Abilities;
import win.vampyuyu.survival.items.ability.BreakListener;
import win.vampyuyu.survival.items.accessory.AccessoryMenu;
import win.vampyuyu.survival.items.accessory.AccessoryService;
import win.vampyuyu.survival.items.catalog.ItemCatalog;
import win.vampyuyu.survival.items.catalog.ItemLoreExtras;
import win.vampyuyu.survival.items.catalog.SetBonuses;
import win.vampyuyu.survival.items.catalog.SetEffects;
import win.vampyuyu.survival.items.combat.CombatListener;
import win.vampyuyu.survival.items.combat.CombatService;
import win.vampyuyu.survival.items.combat.DamageIndicators;
import win.vampyuyu.survival.items.combat.MobScaling;
import win.vampyuyu.survival.items.enchant.EnchantRegistry;
import win.vampyuyu.survival.items.gear.Gear;
import win.vampyuyu.survival.items.gear.ItemApplyListener;
import win.vampyuyu.survival.items.gear.ItemRender;
import win.vampyuyu.survival.items.gear.RenderUpgrader;
import win.vampyuyu.survival.items.gear.UtilityListener;
import win.vampyuyu.survival.items.hud.HudService;
import win.vampyuyu.survival.items.menu.AnvilMenu;
import win.vampyuyu.survival.items.menu.EnchantTableMenu;
import win.vampyuyu.survival.items.menu.ItemMenuListener;
import win.vampyuyu.survival.items.menu.ReforgeMenu;
import win.vampyuyu.survival.items.menu.TableListener;
import win.vampyuyu.survival.items.pet.PetListener;
import win.vampyuyu.survival.items.pet.PetMenu;
import win.vampyuyu.survival.items.pet.PetService;
import win.vampyuyu.survival.items.pet.PetVisuals;
import win.vampyuyu.survival.items.reforge.ReforgeRegistry;
import win.vampyuyu.survival.items.stats.StatsEngine;

/**
 * Entry point of the {@code items} feature: the stats engine ({@link StatsApi}), the damage formula, the HUD, the
 * item catalog, enchants, reforges, accessories and pets. See {@code docs/skyblock-notes/items.md}.
 */
public final class ItemsFeature implements Feature {
    private Core core;
    private StatsEngine engine;
    private HudService hud;
    private PetListener petListener;
    private DamageIndicators indicators;
    private MobScaling mobs;
    private Scheduler.Handle setTask;
    private Gear gear;
    private AccessoryService accessories;
    private SetBonuses sets;
    private ItemLoreExtras lore;
    private PetService pets;

    @Override
    public String id() {
        return "items";
    }

    @Override
    public void enable(Core core) {
        this.core = core;
        YamlConfiguration config = core.featureConfig("items");
        ItemsSettings settings = new ItemsSettings(config);

        EnchantRegistry enchants = new EnchantRegistry(config);
        ReforgeRegistry reforges = new ReforgeRegistry(config);
        ItemRender render = new ItemRender();
        gear = new Gear(core.items(), enchants, reforges, settings, render);
        new ItemCatalog(core.items(), settings).registerAll();

        engine = new StatsEngine(core, settings, gear);
        sets = new SetBonuses(gear, settings);
        accessories = new AccessoryService(core, gear, engine, settings);
        PetVisuals visuals = new PetVisuals(core, settings.petHideFromOwner);
        pets = new PetService(core, gear, engine, visuals, settings);
        engine.addProvider(sets);
        engine.addProvider(accessories);
        engine.addProvider(pets);

        AbilityRegistry abilities = new AbilityRegistry();
        Abilities.registerAll(abilities);
        CombatService combat = new CombatService(engine);
        AbilityEnv env = new AbilityEnv(core, engine, gear, combat);
        abilities.env(env);
        indicators = new DamageIndicators(core, settings);
        CombatListener combatListener = new CombatListener(core, engine, gear, combat, indicators, abilities);
        env.tagger(combatListener);

        SetEffects setEffects = new SetEffects(engine, sets);
        combat.addModifiers(setEffects);
        petListener = new PetListener(core, pets, engine);
        combat.addModifiers(petListener);

        lore = new ItemLoreExtras(abilities, sets, gear, enchants, settings);
        lore.env(env);
        lore.petLore(pets::eggLore);
        render.extra(lore);
        render.nameHook((info, stack) -> pets.eggName(info, stack));

        Costs costs = new Costs(core, engine::settings);
        hud = new HudService(core, engine);

        for (World world : Bukkit.getWorlds()) {
            DamageIndicators.purge(world);
        }

        core.listen(engine);
        core.listen(hud);
        mobs = new MobScaling(core, engine);
        core.listen(mobs);
        core.listen(combatListener);
        core.listen(setEffects);
        core.listen(new AbilityListener(abilities, engine, gear));
        core.listen(new BreakListener(core, engine, gear, abilities));
        core.listen(new ItemApplyListener(core, gear, engine));
        core.listen(new UtilityListener(gear, engine));
        core.listen(new RenderUpgrader(core, gear));
        core.listen(new ItemMenuListener(core.getPlugin().getLogger()));
        core.listen(petListener);
        core.listen(new Listener() {
            @EventHandler
            public void onQuit(PlayerQuitEvent event) {
                abilities.clearCooldowns(event.getPlayer());
                accessories.forget(event.getPlayer().getUniqueId());
            }
        });

        ItemsCommands.Menus menus = (player, menu) -> {
            switch (menu) {
                case ACCESSORIES -> new AccessoryMenu(core, player, accessories, costs).open();
                case PETS -> new PetMenu(core, player, pets).open();
                case REFORGE -> new ReforgeMenu(core, player, gear, costs, engine::settings).open();
                case ENCHANT -> new EnchantTableMenu(core, player, gear, costs, lore, engine::settings).open();
                case ANVIL -> new AnvilMenu(core, player, gear, costs, engine::settings).open();
            }
        };
        core.listen(new TableListener(engine::settings, (player, kind) ->
            ItemsCommands.later(core, player, () -> menus.open(player, ItemsCommands.menuOf(kind)))));
        new ItemsCommands(core, gear, engine, pets, menus, this::reload).register();

        engine.start();
        hud.start();
        petListener.start();
        setTask = core.scheduler().repeating(20, 20, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (engine.active(player) && !player.isDead()) {
                    setEffects.tick(player);
                }
            }
        });
        // Last step: other features may call the API from now on.
        core.register(StatsApi.class, engine);
    }

    /** Re-reads items.yml for the numeric settings (enchant and reforge definitions need a restart). */
    private void reload() {
        ItemsSettings fresh = new ItemsSettings(core.featureConfig("items"));
        engine.settings(fresh);
        gear.settings(fresh);
        accessories.settings(fresh);
        pets.settings(fresh);
        sets.settings(fresh);
        lore.settings(fresh);
        indicators.settings(fresh);
        for (Player player : Bukkit.getOnlinePlayers()) {
            engine.markDirty(player.getUniqueId(), true);
        }
    }

    @Override
    public void disable() {
        ItemMenuListener.closeAll();
        if (setTask != null) {
            setTask.cancel();
        }
        if (petListener != null) {
            petListener.stop();
        }
        if (hud != null) {
            hud.stop();
        }
        if (engine != null) {
            engine.stop();
        }
        if (indicators != null) {
            indicators.clear();
        }
        if (mobs != null) {
            mobs.clear();
        }
    }
}
