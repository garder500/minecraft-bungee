# Lore bible: Vampyuyu Skyblock

One place for the story, the names and the voice of Skyblock. It is compiled only from what the repo already says
(`docs/skyblock-notes/*.md`, the seeds, the Guardian spoken lines in `boss.sql`, the art direction sheet "The Devourer's
Realms"). Anything that had to be invented to fill a gap is marked **(proposed)**: it is not canon until the owner
confirms it. Where two sources disagree it is listed in "Known inconsistencies" at the end, not silently fixed.

Rules that frame everything below: all player-facing text is English; the world is original (no name, term or look
from Hypixel or another game); lore changes display text only, never ids, stats, prices or recipes.

## 1. Premise

* **Aethelgard** was the world. The **World Devourer** (the **Devourer**) ate it. What is left are fragments adrift in
  the void. The player wakes on one of them: their **island**, "cut from the world by the World Devourer" (quest
  category *The Awakening*). The stone of Aethelgard is scarce: "the Devourer left only dust and pebbles".
* The Devourer is a world-eater, not a villain with a speech. Its first hunger was not for the world: "It did not want the
  world. It wanted to be told that it was loved" (Paragon rank 6, canon text). It cannot be killed for good, only put
  to sleep (boss row `devourer`).
* **Memories**: what Aethelgard was is kept as crystallised memory. The Forge tiers answer "the crystallised memories of
  Aethelgard"; **Memory Shards** and **Resonance** are the same idea in the economy. Dying places, dead beings and old
  rules are remembered, not simply gone.
* **The Quai (Quai des Huit)**: the hub's landing stage. Eight **Sky Gates** stand around a central light, one per
  Realm, all dark at first. A gate lights for the player who fought that Realm's Guardian. A lit gate is seen only by
  its owner. Back home: `/quai`, `/hub`, `/island`.
* **The eight Realms** are the worlds the Devourer did not finish digesting. Each is kept by a **Guardian**, a
  regulator of the Devourer: it holds one law of the world in order (fire, growth, stillness, time, decay, storm,
  pressure, judgment). The Guardians are not evil; they are tired keepers who speak of the Devourer as the thing they
  keep in check ("I keep the Devourer's fire in order. Do not make it burn.") and ask the player to make something that
  does not hurt the sky with their **core**.
* **Bastions**: the fortress of a Guardian, an instanced fight (1 to 5 players), opened with a **key**. "The Sarcophagus"
  is the sealed door of a Realm where the Guardian lies.
* **Celestial Breath**: the sky breathes. Every 180 minutes a **Pulsation** draws an event (Void Storms, Fractures,
  Stellar Invasions). Players gather **Interpulsation Fragments** (5 make a key). The Breath is the Devourer's
  heartbeat heard from outside ("The Lava Colossus pulses with the Devourer's heartbeat").
* **Hell Gao, the Anomaly**: secret post-game boss, "born between two pulses, where the memories of Aethelgard met the
  Devourer". Not a god, not a beast, not an echo. Villagers cannot name it; they say "Hell Gao" and look at the sky
  between two stars. The final form is the owner's call (`design-decisions.md` section 5).
* **The Astral Interiority** (Intériorité Astrale): the inside of the Devourer, open to those who defeated the
  Guardian of every Realm. A sea of liquid memory (the Sea of Devoured Ideas), a Nervous Tree whose synapses carry you
  up, and the Gravitational Throne around a singularity. Sitting on the Throne makes you a *survivor*.
* **The Raid of the Devourer**: the final fight, a Bastion (`devourer_raid`, arena "Gravitational Throne"). The leader
  spends an **Event Horizon Key**, forged from one core of each of the eight Guardians. Phase I bends time and
  distance (Event Horizon), phase II wears the faces of the eight Guardians (Metamorphic Assimilation), phase III is a
  singularity with no floor (Naked Singularity). "All eight regulators are broken." Rewards are trophies, never stats.
* **Paragon**: whoever ends the Astral Interiority raid. Ten **Transcendence** ranks (Awakened, Ether-touched, Steward,
  Transcendent, Starlit, Witness, Keeper of the Pulse, Elder Paragon, Between Pulses, Paragon of the Breath). Rewards
  are titles, auras, slots and lore, never stats.
* **Vocations**: the player classes (Vanguard, Duelist, Channeler, Ranger). Lore calls them Vocations: "the faces the
  Vocations left behind" (Hell Gao lore).
* **Open worlds, shared sky**: the hub, the Gates and the Breath are shared; Bastions, dungeons and Hell Gao are
  instanced; narrative progress belongs to the player (`design-decisions.md`). So lore must never assume that another
  player has read it or that a Realm was visited before another (after the Ashes and the Grove).

Order: Realm 1 Cracked Ashes, 2 Whispering Grove, 3 Frozen Heights, 4 Citadel of Golden Sands, 5 Putrid Marshes,
6 Tempestuous Archipelago, 7 Abyssal Depths, 8 Ethereal Necropolis. Ashes then Grove are the obligatory opening; the
other six are free (power is the soft gate). Lore for Realms 3 to 8 must not depend on one another.

**First Guardian-like fight, not a Realm:** the **Echo of the Rift** (`voidwyrm`): "a fragment of the Devourer's hunger,
torn loose at the Rift" (End altar, Void Sigil). Killing it lights Gate 1 and opens the Ashes quests.

## 2. The eight Realms

Palette = shadow, base, light, highlight, glow (the glow is the only saturated pixel and marks the motif).
"Say" and "avoid" are vocabulary guides for any new line.

### Realm 1: Cracked Ashes (Realm of the Cracked Ashes)
* **Place**: `sb_ashes`, Nether-like void. Ember Landing, five islets (Needle, Slag Foundry, Cinder Fields, Ember
  Tarn, Rib of the Colossus), the Caldera with the sealed Sarcophagus, contained seas of magma. Heat is the pressure.
* **Guardian**: **Lava Colossus** (id `magma_warden`). Keeps the Devourer's fire in order; its regulation fails in
  phase 3 ("Too hot... the regulation fails"). Core: **Colossus Core**.
* **Resource**: **Cinderheart** (nodes of gilded blackstone). Rune: Scorch.
* **Bastion**: Ashes Bastion, key **Ashes Bastion Key** (`magma_core`).
* **Motif**: cooled obsidian split by lava veins; a jagged crack, brightest where it is widest.
* **Palette**: `#120a16` `#2a1a2e` `#5a2a2a` `#c8461e` `#ff8a2a`.
* **Say**: cooled, slag, ember, vein, crack, tarn, heartbeat, still cooling. **Avoid**: "hell", "demon", "inferno",
  "nether" (it is a vanilla and a Hypixel word for this place), anything that makes the Colossus a fire-demon.

### Realm 2: Whispering Grove
* **Place**: `sb_grove`. Main island, six islets joined by root bridges, farm terrace, heartwood tree, spore pool, the
  arch of the Mycelial Queen. Spore haze (Hunger I) until Respiration.
* **Guardian**: **Mycelial Queen** (`sylvan_matriarch`). Remembers every living thing the Devourer ate; her codes
  (genes) are guarded, not hoarded ("The codes were never mine to hoard"). Core: **Queen Core**.
* **Resource**: **Heartwood** (mangrove logs, moss, dark oak). Rune: Verdant.
* **Bastion**: Grove Bastion, key **Grove Bastion Key** (`matriarch_egg`). Tagline: "guards the genes of a world that is gone".
* **Motif**: living wood and mycelium; growth rings and drifting spores.
* **Palette**: `#2c3a1e` `#4f6b2a` `#8fb84a` `#d6f08a` `#f1e7c9`.
* **Say**: root, ring, spore, seed, sprout, listen, remember, little ones. **Avoid**: "jungle", "forest spirit",
  "druid", "nature's wrath" (too generic and borrowed).

### Realm 3: Frozen Heights
* **Place**: `sb_heights`. Snow and ice island, six peaks on ice bridges, crystal spire, frozen city fragment, frozen
  pool, cooling yard, the Watcher's arch. Cold builds up (never lethal) until Warmth. Low gravity.
* **Guardian**: **Boreal Watcher** (`frost_revenant`). Holds the world still so it cannot be eaten; a brake that
  locks ("Too still... the brake locks..."). Slow, patient. Core: **Watcher Core**.
* **Resource**: **Stasis Crystal**. Rune: Stasis. Variant set: Frost Starcore ("Glacial Poise").
* **Bastion**: Heights Bastion, key **Heights Bastion Key** (`frost_heart`).
* **Motif**: ice that holds a moment still; a single unblinking eye, violet glow.
* **Palette**: `#1f3557` `#3b6fa8` `#7fc8e8` `#e6f7ff` `#b6a8ff`.
* **Say**: still, hold, hush, brake, thaw, moment, unblinking, slow. **Avoid**: "Frostbite" style names, "ice king",
  "winter", "Santa"-adjacent words; never "freeze" as an attack verb outside the Guardian's own phase-3 shout.

### Realm 4: Citadel of Golden Sands
* **Place**: `sb_citadel`. A main sand island around a giant hourglass, six dune islets, four dust columns, the
  Pharaoh's stepped pyramid. "Rivers of gold dust run backwards. Local time is unstable" (GDD 3.1, paraphrased from
  French). The **Time Tide**: calm 720 s, surge 240 s, stall 240 s.
* **Guardian**: **Temporal Pharaoh** (`temporal_pharaoh`). Keeps the Devourer's years in order; counts "the hours the
  Devourer stole". Core: **Temporal Core**. Material: **Pharaoh's Linen**.
* **Resource**: **Chrono Sand**. Items: Chrono Module, Sandglass.
* **Bastion**: Citadel Bastion, key **Citadel Bastion Key** (`pharaoh_scarab`, a scarab).
* **Motif**: gold, linen and clocks; hourglass and scarab, turquoise inlay.
* **Palette**: `#5a3a12` `#a8742a` `#f2c14e` `#fff1b0` `#3fb6a8`.
* **Say**: grain, hour, tide, surge, stall, linen, scarab, hourglass, borrowed time. **Avoid**: real Egyptian god
  names, "curse", "tomb raider", "mummy's"; the Pharaoh is a clock-keeper, not a religion.

### Realm 5: Putrid Marshes
* **Place**: `sb_marsh`. A sunken bog island, five pools, four islets on causeways (Vat Works, Landfill, Weeping
  Mangrove, Overflow Pit), three **Rot Vats**. Miasma (Weakness I) until the Filtered affinity; Purification up to
  Reclaimer.
* **Guardian**: **Mire Abomination** (`mire_abomination`). Keeps what the Devourer throws away; "the bin of a hungry
  god". Rude, wasteful-hating, tender in death ("Take what is left. Use it again."). Core: **Mire Core**. Material:
  **Bog Ichor**.
* **Resource**: **Rot Resin**. Item: Recycler Module. The Realm is about recycling (Rot Vats, Crucible bonus).
* **Bastion**: Marsh Bastion, key **Mire Cyst** in art, **Marsh Bastion Key** in code (see inconsistencies).
* **Motif**: rot, sap and cysts; bubbles and a bruise-purple core.
* **Palette**: `#2a2a1a` `#4e5a22` `#8a9a2e` `#c9d36a` `#b04aa0`.
* **Say**: heap, vat, resin, ichor, cyst, reclaim, digest, leftovers, overflow. **Avoid**: "zombie swamp", "toxic waste",
  gore words, "disgusting" (the Realm is useful, not shocking).

### Realm 6: Tempestuous Archipelago
* **Place**: `sb_storm`, always raining. Seven islands in a chain joined by moving platforms and wind pads: Gale
  Landing, Weathervane Isle, Rod Field, Cirrus Shelf, Maelstrom Heart, Roc Roost, Dragon's Eyrie (the seal). Gusts every
  30 s, "Sneak to brace."
* **Guardian**: **Primordial Thunder Dragon** (`thunder_dragon`, shown as **Thunder Dragon**). Keeps the winds; wishes
  they did not have to obey ("I only wish they did not have to"). Core: **Dragon Core**.
* **Resource**: **Thunder Quartz** (chiseled quartz nodes). Machine: Lightning Rod (~38 Flux/s, unstable).
* **Bastion**: Tempest Bastion, key **Tempest Bastion Key** (`tempest_key`).
* **Motif**: storm glass and wind; a two-step zigzag bolt.
* **Palette**: `#1e2440` `#3a4a8a` `#7a8ee0` `#f0f4ff` `#ffe94a`.
* **Say**: gust, rod, bolt, eyrie, roost, brace, stormfront, tether, wind pad. **Avoid**: "Zeus", "Thor", "god of
  thunder", "dragonborn", "Ender" for the Dragon (it is only an ender dragon model).

### Realm 7: Abyssal Depths
* **Place**: `sb_depths`. Pressure Landing, four suspended domes (Lantern Shoals, Kelp Spire, Hollow Trench, Chain
  Dome), the Drowned Bell, the Sarcophagus door on the Chain Dome. Water pressure by depth tier; pumps lower it.
* **Guardian**: **Chained Leviathan** (`abyssal_leviathan`). It holds the seas in place, and gravity ("Gravity was a
  promise. Keep it better than I did."). Core: **Leviathan Core**.
* **Resource**: **Pressure Pearl** (fished). Machine module: Tide Module. Realm of fishing.
* **Bastion**: Abyss Bastion, key **Abyss Bastion Key** (`leviathan_scale`).
* **Motif**: crushing water; pearl sheen with pink bioluminescence. The art sheet lists no Guardian here ("fishing
  realm"); the Leviathan is the canon Guardian.
* **Palette**: `#0a1a2a` `#12405a` `#2a8a9a` `#bff0f0` `#ff6ab0`.
* **Say**: pressure, chain, dome, bell, shoal, trench, pull, tide, anchor. **Avoid**: "Kraken", "Poseidon",
  "Atlantis", "Davy Jones"; do not make the sea evil, it is held.

### Realm 8: Ethereal Necropolis
* **Place**: `sb_necropolis`. Deepslate and soul soil island, ossuary, soul brazier, grave field, blackstone bridges,
  the dais arch of the Judge, soul sand field. Memories fade (Darkness pulses, no damage) until Lucidity; Recall gives
  Memory Shards.
* **Guardian**: **Judge of Lost Souls** (`crypt_lord`, technical id kept; the dungeon's own Crypt Lord is a different
  mob). Reads verdicts "of a world that is gone"; asks to be judged more kindly than it judged ("Take my seal. Judge the
  Devourer more kindly than I judged the lost."). Core: **Judge Core**. Material: Crypt Lord's Bone.
* **Resource**: **Soul Ash**. Item: Memory Module.
* **Bastion**: Necropolis Bastion, key **Judge's Seal** (art and `judge_seal` id) / **Necropolis Bastion Key** (code).
* **Motif**: memory and judgment; soul flame, cyan on lavender ash.
* **Palette**: `#241f3a` `#4a4570` `#9c95c8` `#e8e4ff` `#6affd8`.
* **Say**: ledger, verdict, scales, memory, name, witness, ash, forgotten, recall. **Avoid**: "undead horde", "lich",
  "necromancer", "death knight", "Hades"; the dead here are remembered, not hostile because they are evil.

### Beyond the eight
* **Astral Interiority**: palette `#0e0a1a` `#3a1a6a` `#8a4aff` `#e0c8ff` `#ff4a8a`; motif "a rift that looks back".
  Guardian: the Devourer itself. Say: rift, synapse, anchor, throne, nexus, singularity, devoured idea. Avoid: "End",
  "Void" used as a place name for it (Void is an element: Voidglass).
* **Hell Gao**: the Devourer's violet with the Breath's gold (`design-decisions.md`). Say: pulse, interpulse, between,
  borrowed, silence. Avoid: any real-world Hell reading of the name; it is a name villagers cannot explain.
* **Flux machinery**: palette `#4a2a12` `#7a4a1e` `#c98442` `#f2c14e` `#4af0ff`; bronzite casings, cyan Flux, the
  window on the casing face shows what the machine eats or makes.

## 3. Naming rules for items

General: Title Case, English, plain concrete nouns, 1 to 3 words, no apostrophe-heavy fantasy names. A Realm is named
by its place, a thing by what it is. Technical ids never change; only the displayed name does.

| Family | Pattern | Existing examples | Rule for new ones |
|---|---|---|---|
| Guardian cores (components) | `<Guardian short name> Core` | Colossus Core, Queen Core, Watcher Core, Temporal Core, Mire Core, Judge Core, Dragon Core, Leviathan Core | Use the Guardian's short name, not the Realm. **Temporal Core** is the only one that uses an adjective (Pharaoh would also fit); keep it, it is shipped. One caged round core silhouette for all. |
| Bastion keys | `<Realm word> Bastion Key` | Ashes, Grove, Heights, Citadel, Tempest, Abyss, Marsh, Necropolis Bastion Key; Event Horizon Key; Interpulse Key | Name by the Bastion. A key is forged: a shaft with an emblem bow. Organic exceptions (cyst, seal) may keep a flavour name in lore text only. |
| Realm resources | One or two concrete words, raw material, never a tool | Cinderheart, Heartwood, Stasis Crystal, Chrono Sand, Rot Resin, Thunder Quartz, Pressure Pearl, Soul Ash | Material noun plus the Realm's force. No "Essence", "Shard of ..." (Shard is reserved for Memory Shards). |
| Resonance materials | The Guardian's possession | Pharaoh's Linen, Bog Ichor, Crypt Lord's Bone, Mycelial Silk | `<Guardian>'s <thing>` or a plain material. |
| Realm modules (Miner) | `<Force> Module` | Chrono, Cryo, Tide, Recycler, Memory Module | One verb-like word for what the Miner gains. |
| Flux machines | Plain machine nouns | Miner, Coal Generator, Solar Panel, Spring Wheel, Heat Tap, Void Reactor, Astral Lattice, Lightning Rod | Describe the source or the shape, never a person. Bronzite casing, dark window. |
| Compression | `<Ore> Bar`, `<Ore> Block` | Starcore Block | Nine to one, then nine to one. |
| Tiered ores and elements | Fixed | Bronzite (Earth), Frostium (Frost), Sunstone (Ember), Voidglass (Void), Starcore (Astral) | Do not add a sixth without a design decision. |
| Gear sets | `<Tier> armor`, set bonus named | Bronze, Rime, Ember, Void, Starcore; Frost Starcore "Glacial Poise" | Realm variants: `<Realm word> Starcore`, bonus is a two-word noun phrase. Lore line on every piece: "Part of the X armor." / "Wear all four pieces to be known by it." |
| Trophies and cosmetics | Plain object that proves it | Singularity Relic, Horizon Lens | No stats ever. |
| Titles, ranks | Short noun or noun phrase | Between Pulses, Keeper of the Pulse, Paragon of the Breath | Title Case. |
| Fights and phases | Evocative noun phrase | Event Horizon, Metamorphic Assimilation, Naked Singularity, Frozen Hour | UPPERCASE only in the on-screen phase title. |

Always avoid: "of Doom", "Ultimate", "Legendary X of Y" stacking, real-world myth names, numbers in names.

## 4. Tone of voice

* **Lore lines on items**: short and concrete, 1 to 3 lines, about 50 characters per line at most (a tooltip wraps badly
  beyond that). One image per line, no explanation of mechanics in the lore lines (the mechanics are separate lines).
* Present tense, plain words. Prefer an object and a verb to an adjective: "The sand inside runs up, then down." (proposed)
* Second person sparingly: at most one line in a set of three. Never "you will".
* The lore is quiet and sad, not heroic. The Devourer hungers; the Guardians are tired keepers; the player is small and
  useful. No jokes, no puns, no meta (no "players", "server", "dev").
* MiniMessage: lore lines use `<gray>` by default; the italic is turned off by the item builder. Keep tags closed. Use
  the Guardian's own gradient only for its name (see `speaker` rows in `boss.sql`).
* **Guardian speech** (spoken lines, `boss.sql`) has a fixed arc, keep it for new Guardians: intro (they warn), phase 1
  (they begin), phase 2 (they lose control, final line in CAPITALS with full stops: "FREEZE. ALL OF IT. FREEZE."), death
  (relief, then a request to use the core better), first clear ("The X lets you through for the first time: take this Y.").
  The player is addressed as "small ones" or "little ones" by the older Guardians.
* Quest lore lines are one sentence of Aethelgard story shown before the instruction.

Good: "It is still cooling. It never finishes." (proposed). Bad: "A very powerful legendary core forged in lava that
increases your fire power!" (too long, boasting, mechanical).

### Proposed lore lines per item family (from the art direction sheet, all "proposed")
* Colossus Core: "It is still cooling. It never finishes."
* Queen Core: "Spores drift out of it toward whoever holds it."
* Watcher Core: "The eye inside has not blinked since the Heights froze."
* Temporal Core: "The sand inside runs up, then down, then up."
* Mire Core: "Warm. It should not be warm."
* Judge Core: "It weighs the same as the soul you last forgot."
* Dragon Core: "Hold it by the cage. Only by the cage."
* Leviathan Core **(proposed, not in the sheet)**: "Heavy. It is still pulling at something far below."
* Resources, two lines each **(proposed)**: Cinderheart "A heart of the Ashes. / Warm long after it stops beating."
  Heartwood "Wood that remembers being alive." Stasis Crystal "A moment, held. / Do not drop it." Chrono Sand "One grain
  is one hour. / Not yours." Rot Resin "Sticky. Reusable. Nothing is wasted." Thunder Quartz "Hums before the sky does."
  Pressure Pearl "It was pulled down for a very long time." Soul Ash "What a name leaves behind when it is forgotten."

## 5. Glossary

| Term | Meaning |
|---|---|
| Aethelgard | The lost world; your island is a fragment of it. |
| The Devourer / World Devourer | The world-eater. Final boss of the Raid; can only be put to sleep. |
| Island | The player's fragment of Aethelgard. |
| Quai (Quai des Huit) | The hub's landing with eight Sky Gates. |
| Sky Gate | A Realm's door on the Quai; dark until you fight its Guardian. |
| Realm | One of the eight kept worlds. Realm 1 Cracked Ashes ... Realm 8 Ethereal Necropolis. |
| Guardian | A Realm's keeper/regulator and its boss. |
| Sarcophagus | The sealed door where a Guardian lies. |
| Bastion | The Guardian's instanced fortress (1 to 5 players), opened with a key. |
| Core / Component | A Guardian's heart item, guaranteed by hard pity. |
| Memory Shard | Currency of Resonance; crystallised memory. |
| Resonance | The memory-based rank system: harder Guardians, better rewards. |
| Celestial Breath | The sky's 180-minute pulse; drives world events. |
| Pulsation | One breath event. Void Storm, Fracture or Stellar Invasion. |
| Fracture | A dungeon run opened by a Pulsation in a named Realm. |
| Stellar Invasion | Immune cells in the Hub or a Realm during a Pulsation. |
| Interpulsation Fragment | Rare drop; 5 make a Breath key to Hell Gao. |
| Interpulse Key | Bastion key for Hell Gao (exchanged for a Breath key). |
| Event Horizon Key | Bastion key for the Raid, forged from eight cores. |
| Astral Interiority | The Devourer's inside; needs all eight Guardians beaten. |
| Raid of the Devourer | Final Bastion; three phases (Event Horizon, Metamorphic Assimilation, Naked Singularity). |
| Paragon | Title of those who survive the Astral Interiority; 10 ranks. |
| Vocation | Lore word for a class (Vanguard, Duelist, Channeler, Ranger). |
| Flux | Energy of the machines (Miner, generators, batteries). |
| Forge | Where keys, modules and gear are made. |
| Starcore | The fifth tier ore, element Astral. |
| Bronzite / Frostium / Sunstone / Voidglass | Tier ores, elements Earth, Frost, Ember, Void. |
| Ascension | Hell Gao difficulty rank 0 to 5. |

## 6. Known inconsistencies (to decide; the ones marked fixed were cleaned in the text pass)

1. **Raid key**: the art sheet describes `interpulse_key` as the Raid key; in code the Raid key is the **Event Horizon
   Key** (`devourer_sigil`) and the **Interpulse Key** opens Hell Gao. This bible follows the code.
2. **Marsh key name**: art sheet "Mire Cyst", code `mire_cyst` named "Marsh Bastion Key". Necropolis likewise: "Judge's
   Seal" (art) vs "Necropolis Bastion Key" (code). Items follow the code name; "cyst" and "seal" may live in lore lines.
3. **Colossus Core** is also the name of a world-boss material (`colossus_core`, the Ancient Colossus); the Guardian core
   is `lava_core`. Proposed: rename the world-boss one to "Colossus Plate" in a later text pass (ids unchanged).
4. **Mire Abomination** (code, Bastion tagline) vs "Marsh Guardian" (art sheet): the Guardian is the Mire Abomination.
5. **Realm 7 Guardian** is blank in the art sheet ("fishing realm"): the Leviathan is canon.
6. **Thunder Dragon**: the row is named "Primordial Thunder Dragon", the spoken speaker "Thunder Dragon". Both stand.
7. **Realm order vs Bastion list order**: Bastion `position` puts Necropolis third; the Realm order (GDD) puts it eighth.
   The Realm number in this bible is the GDD one.
8. The Great Disillusion, the Reality Pillars and the mouth in the Quai appear only in passing in older notes; they
   are not built, and this bible does not define them **(proposed: leave undefined until the owner does)**.
9. The Realm resource names Chrono Sand, Rot Resin, Thunder Quartz, Pressure Pearl, Soul Ash are validated
   (`design-decisions.md`); no rename.

10. **Fixed (text pass)**: the five slayer bosses had borrowed names; they are now The Unrotting (zombie), The Great
    Spinner (spider), The Gaunt Alpha (wolf), The Hollow Walker (enderman) and The Furnace Tender (blaze), matching
    their drops (Unrotting Flesh, Spinner Web, Hollow Pearl, Living Ember, Furnace Heart). Slayer ids unchanged.
11. **Fixed (text pass)**: compressed resources are no longer "Enchanted X": tier 1 is "Compressed X", tier 2 is
    "Compacted X" (ids `enchanted_*` unchanged). The menu title "Enchanted Recipes" and internal ids keep the old word.
12. **Fixed (text pass)**: dungeon items renamed (ids unchanged): Wither Essence -> Barrow Ash, Spirit Dust -> Mourning
    Dust, Spirit Leaf -> Recall Leaf, Spirit Cache -> Mourning Cache, Essence Broker -> Ash Broker. The quarry skin
    drop text now says Boreal Watcher instead of "Frost Revenant" (the id `frost_revenant` stays).

## 7. What is invented here

Everything marked **(proposed)**: the Leviathan Core line, the resource lore lines, the vocabulary lists ("say" and
"avoid"), the "Colossus Plate" rename suggestion and the tone examples. The premise itself, the Realm facts, names,
Guardian speech, palettes and motifs are taken from the repo and the art sheet.
