# Runes and Realm runes (GDD 10, 3.2; issue #37)

Feature `runes` (`survival-plugin/.../runes/`), API `api/RuneApi`, seed `db/seed/runes.sql` (domain `runes`, settings only: no migration).

- **Runecraft table**: a vanilla grindstone (setting `runes.station-block`) opens it in Skyblock worlds (sneak with an item still places blocks), or `/runecraft`. One row per rune, one button per rank, paid from the player's inventory. A grinder turns an Amethyst Shard into 4 Rune Powder; a scraper (shift-click) removes every rune from the item in hand (the runes are lost).
- **Stat runes** (Vitality, Ward on armor, Might on weapons, Aether on both): rank I/II/III = +2/+4/+6 points. Rank I = 8 Rune Powder, II = 3 runes I + a component (bronzite, frostium, sunstone, voidglass), III = 3 runes II + a diamond.
- **Slots**: by item power tier, 1 / 2 / 3 / 4 / 4 (`CustomItemDef.tier`, 1 when the item has none). One rune of each kind per item.
- **Engraving**: left-click a rune held on the cursor onto a weapon or armor piece in the player's own inventory (worn armor is refused: take it off first, so the Vitality modifier stays right).
- **Storage**: the engraved list is the PDC value `runes` (`vitality:3,scorch:0`); the points are added to the item's own `bonus_<stat>` (shown as "(+6)" by the core renderer, counted by `Items.statsOf`). `LadderCombat` and `LadderDefense` now read `statsOf` instead of the bare definition stats, so a rune counts for Might, Ward and Vitality. The rune lines of the lore come from `Items.loreExtra`.
- **Budget**: a kit has at most 4 armor pieces x 6 = 24 points per stat from runes and one weapon rune, well under the +100 of GDD 8.5 and the +25 Force cap. A test checks it from the seed.
- **Realm runes** (no rank, worth a rank III, an effect instead of points): **Scorch** (Cinderheart, weapon): a Surge sets the target ablaze for 4 s (`RuneApi.onSurge`, called by `LadderCombat` for melee and arrows). **Verdant** (Heartwood, armor): taking a hit grants Regeneration I for 5 s, 12 s cooldown. Cost 8 powder + 4 resource + 1 diamond.

Guessed values, to measure in play: where Rune Powder comes from (amethyst grinder: the GDD only gives the price), the Realm rune cost, the burn and regeneration durations, the rank II components.
Not done: pack textures for the rune items (vanilla materials for now), the items feature's `Gear` (not loaded in the simple Skyblock) does not know about runes, a Realm rune does not yet exist for the other six Realms (only the first two Realms are in scope).
