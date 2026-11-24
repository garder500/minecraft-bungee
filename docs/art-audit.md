# Art audit (2026-11-24)

An art-director pass (Claude Fable) over every texture, menu and HUD of both packs, ranked worst first, to plan the full
graphics redo the owner asked for ("refait l'intégralité des assets graphiques ... fait des bangers"). Paths: `PACK` =
`resource-pack/pack/assets/vampyuyu/textures`, `VB` = `resource-pack/valoblock/assets/vampyuyu/textures`, `ART` =
`resource-pack/art`. Tags: [2D] repaint, [3D] Blockbench model, [HUD/UI] layout or size. Filenames inside a family were
mapped by sort order: trust the globs over single names.

Core finding: the base pack mixes four styles. The imported 32 px painted sprites are good; the 16 px procedural output of
`ART/*.py` (flat blobs, 1 px outline, two accent pixels) is most of what follows.

Who does what: the 3D models of bosses, armour, Skyblock items and blocks are being redone in Blockbench in a separate
session; the ValoBlock guns are done (`ART/valoblock_bb/`).

## Ranked problems

### ValoBlock
1. [HUD/UI] Oversized, redundant HUD (`VB/font/vbhud_*`, `ART/valoblock_ui.py`): plates 88x36, ability tiles 4 x 30x38
   (duplicating hotbar slots 4-7), parchment minimap overlapping the health plate, notice text across the crosshair.
   Targets: plates about 60x22, tiles 20x24 or folded into the hotbar, minimap about 60 px, notices small above the hotbar.
2. [2D] Agent portraits and faces are 8x8 Steve-style heads (`VB/font/vb_portrait_*`, `vbinline_head_*`,
   `VB/item/valoblock/agent_*`; `valoblock_ui.py`, `valoblock_agents.py`): painted 64 px busts, one signature colour each.
3. [2D] Ability icons illegible at 16 px (`VB/item/valoblock/ab_*`, `VB/font/vbhud_ic_*`; `valoblock_ability_icons.py`):
   32 px single-colour glyphs on an agent-tinted tile, one bold shape each.
4. [HUD/UI] Agent select, Armory and tactical map menus read as debug grids (`VB/font/vb_select_*`, `vb_armory_*`,
   `vb_tacmap_*`): real panels, gradient, accent bars, framed portrait bay.
5. [2D] Role and seat tiles are placeholders (`VB/item/valoblock/role_*`, `lock_in`, `random`, `card_frame*`).
6. [2D] Tactical map, minimap and vote thumbnails are a block spreadsheet (`VB/font/tac/*`, `vb_vote*`;
   `valoblock_tacmap.py`, `valoblock_vote.py`): clean floor plan, no grid.
7. [2D] Flat gun sprites must follow the new 3D guns (Armory, kill feed, tabs; `VB/font/vbinline_gun_*`).
8. [2D] Agent body skins are flat colour blocks (`VB/entity/valoblock/*`, `valoblock_skins.py`).
9. [2D] Ward icons are tiny gems with empty margins (`heavy_ward`, `light_ward`).

### Skyblock 16 px procedural sprites
10. Minions: one blob recoloured about 30 times (`PACK/item/minions/*`, `minions_icons.py`).
11. Collection items are primitives (`PACK/item/skills/enchanted_*`, `skills_icons.py`).
12. Class armour icons: one stamp palette-swapped (`PACK/item/items/{farmer,...}_*_t[1-3]`, `items_icons.py`).
13. Accessories: four shapes x nine colours (`PACK/item/items/acc_*`).
14. Pets and misc (`pet_*`, `reforge_stone_*`, `*_singularity`, `drill_fuel_*`, ...).
15. Procedural 16 px weapons and tools next to the painted 32 px ones (`PACK/item/items/*_sword`, `PACK/item/skin/*`).
16. Boss drops: one sphere recoloured (`PACK/item/boss/*`, `boss_items.py`).
17. Machine window faces are placeholder symbols (`PACK/block/machine/window_*`, `block_models.py window_textures()`).
18. Old 16 px machine item icons (`PACK/item/machine/{astral_lattice,void_reactor,heat_tap,spring_wheel,lightning_rod,cable}`).
19. Wings: a recoloured butterfly bow (`PACK/item/wings/*`, `wings_art.py`).
20. Worn armour layers are stripe banding (`PACK/entity/equipment/humanoid*/*`, `armor_layers.py`, `armor_tiers.py`).
21. Boss skin textures are flat swatches (`PACK/item/boss/skin/*`, `boss_skins.py`).
22. Economy and menu icons at 16 px beside painted `PACK/item/menu/*` (`economy_icons.py`, `menu_icons.py`).
23. Dungeon items and 16 px ore variants (`dungeon_icons.py`, `PACK/item/ore/*_gui`, `*_block`, ...).
24. Rune tiers differ only by rim colour (`rune_items.py`).

### Menus
25. Four menu styles with no shared frame (`PACK/font/menu_*`, `sb_*`, ValoBlock; `render.py`, `sky_menus.py`).
26. Text painted into menu art against the project rule (role menus, `menu_confirm`, `menu_lobby`, `menu_list`,
    `menu_profile`, `menu_setup`, the wordmark in every lobby-style title zone).
27. Skyblock menu background is clip-art (`PACK/font/sb_*`): painted panel, neutral dark slot wells.
28. Role-menu lower half is dead navy space.

### Werewolf
29. Painted portraits for only 7 of about 40 roles; flat, blurry, near-duplicate sticker icons for the rest
    (`PACK/font/card/*`, `PACK/font/icon/*`, `icons.py`).
30. Werewolf HUD large with baked English labels (`PACK/font/hud/label_*`, `hud.py`): shrink about 25 %, real text.
31. Badges are 12 px blobs (`PACK/font/hud/badge_*`).
32. To check: the Vampire panel once showed the villager face (old screenshot).

### Other
33. Vanilla block overrides look like upscaled smears (`resource-pack/pack/assets/minecraft/textures/block/*`, `import_faces.py`).
34. Site images small and dark, untextured boss box (`site/assets/img/sb-*.jpg`); reshoot after the art pass.
35. Hypixel-like names in asset filenames (`hot_potato_book`, `recombobulator`, `treecapitator`, ...): check player-facing names.

## Style references to keep
`PACK/item/armor/*`, `PACK/item/weapon/*`, `PACK/item/tool/*` (32 px painted), `PACK/item/menu/*`, `PACK/item/utility/*`
(concept sheet `ART/concepts/menu.png`), ore bars and raw ores, painted machine faces (`miner_*`, `coal_generator_*`,
`forge_*`, `fountain_*`, `battery_*`), werewolf card portraits and menu scene illustrations, werewolf HUD frames, rune
shapes, `site/assets/img/lobby.png`, the new ValoBlock 3D guns.

## Order of work
1. ValoBlock HUD resize (done 2026-11-24: plates 60x22, tiles 20x24, minimap drawn in a 64 px corner on a transparent map).
2. ValoBlock agent portraits and heads, then ability icons.
3. ValoBlock menu panels, role and seat tiles, gun sprites from the 3D models.
4. Strip painted text from menus, one shared frame.
5. Skyblock menu background.
6. Machine window faces and old machine icons.
7. Skyblock 16 px to 32 px repaint by visibility: minions, collections, class armour, weapons, boss drops, accessories and
   pets, economy and menu icons, the rest.
8. Werewolf portraits and medallions, HUD shrink with real-text labels.
9. Tactical map and vote thumbnails, agent skins, armour layers and boss skins (with the Blockbench session).
10. Block overrides, then reshoot the site images.
