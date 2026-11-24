# The Hub: the last anchored fragment of Aethelgard

Design of the Skyblock hub (world `sb_hub`), written from the lore bible (`lore.md`). Code: `island/HubPlan.java`
(pure data, relative coordinates, surface at y = 0), built in batches by `HubManager` + `PlanJob`. The Quai des Huit
(`quai.md`) is a separate plan at world z = 72 that this hub leads to.

## Story in one paragraph

The Devourer ate Aethelgard and left fragments adrift in the void. The hub is the one fragment that still holds: its
stone is pale because it was part of a ceremonial quarter, and it stays up because a crystallised memory sits at its
heart. Survivors camped in the ruins. They did not rebuild; they hung lanterns on what was left. Everything that
leaves the hub (your island, the Crypt Depths, the Rift, the eight Realms) leaves through a gate, and the biggest
gate stands at the end of the long processional on the south side: the Quai des Huit. The hub is a landing stage
with a camp on it, not a plaza.

## Shape and scale

* A floating fragment, about 60 blocks across, with a wobbly outline (radius 27 to 37, fixed formula, no randomness
  so the plan is reproducible) and four bites broken out of its rim (never on a road, never near an NPC).
* Four approaches leave the disc: **north** to the Island Gate, **south** to the Quai (the spine of the hub), **east**
  the Crypt Depths barrow, **west** the Rift tear. Guard line and signposts keep these directions.
* The underside is a hollow shell that tapers into a rough keel with a few stalactites (a solid keel would use most of
  the 45000-block cap): top two layers are solid, deeper layers are a thin skin plus the rim.
* Drifting debris: about eight small inverted islets and loose bricks floating around the fragment, kept out of the
  Quai footprint (world z 50 to 94) and inside the +-100 border.

## Palette and materials

| Role | Blocks |
| --- | --- |
| Pale Aethelgard stone (floors, walls) | calcite, polished diorite, stone bricks (plain, cracked, mossy), smooth quartz |
| Processional and paths | quartz bricks centre, polished diorite sides, calcite edge |
| Broken rim, keel | tuff, cobblestone, cobbled deepslate, deepslate |
| Overgrowth | moss block, moss carpet, azalea |
| Crystallised memory | amethyst block, amethyst cluster and buds, light blue and tinted glass, sea lantern, end rod |
| Camp | spruce, barrels, white and light grey wool tents, campfire, lanterns |
| Gates | crying obsidian and purple glass (Rift), deepslate and blackstone (barrow), quartz (island, Quai) |

The only saturated colours are the crystals (violet, light blue) and the lantern gold. Rule from the art bible: one
glow colour per place.

## Landmarks

1. **The Well of Remembrance** (centre, radius 9). A round court of chiselled stone with eight low sigil stones (an
   echo of the Quai's eight gates) around the **Memory Spire**: a cluster of five amethyst shards wrapped in light
   blue glass, 8 to 16 blocks high, a sea lantern inside. It is what keeps the fragment anchored. Four smaller spires
   stand on the rim.
2. **The Landing** (spawn, `(0, 14)`). A paved disc with four lantern posts, on the south axis. Players arrive here
   facing south: the processional runs ahead with the Quai's central light at its end.
3. **The Processional** (south axis, 9 wide, z 10 to 44). Quartz-brick road with two ruined arches (one broken),
   lantern posts, and at its end the **Threshold**: two tall pillars and a lintel crowned with a sea lantern. The Quai
   bridge (restyled in `QuaiPlan`, version 2) starts at z = 43 and carries on the same road to the Quai deck.
4. **Island Gate** (north). A tall pale arch, anchor-chain pillars and an amethyst keystone; the gatekeeper stands
   in front, the road beyond ends in a small landing disc.
5. **Crypt barrow** (east, dungeon gate). A mound of deepslate and tuff with a sealed doorway: a short tunnel ending
   in blackstone and iron bars, soul lanterns either side.
6. **The Rift tear** (west). Two obsidian pillars with crying obsidian, a broken lintel, purple glass shards hanging
   in the gap and floating amethyst.
7. **Survivors' camp**: the ruins around the Well, each NPC in a fitting ruin with lanterns.

## NPC spots (pad ids are a contract with the features that spawn NPCs)

| Pad id | Place | Ruin |
| --- | --- | --- |
| `merchant` | west-south-west | salvager's stall: spruce canopy, counter, barrels |
| `archivist` | west | roofless archive: bookshelf wall, lectern |
| `guard` | south-east, over the processional | watch post: stub of a tower |
| `contract_hunter` | east-south-east | notice wall: fletching and cartography tables, targets |
| `quest_master` | north-west | camp fire with a tent and hay seats |
| `banker` | north-east | vault: deepslate wall, iron bars, a gold block |
| `boss_arena` | east-north-east | the Challenger's Ring: broken pillars around a blackstone circle |
| `island_gate` | north, on the road to the arch | Island Gate |
| `dungeon_gate` | east | Crypt barrow |
| `rift_gate` | west | Rift tear |
| `eight_docks` | south, at the Threshold | no NPC: a pad and a sign for the Quai des Huit |

The old `fairy_souls` pad (Wisp Guide) is gone together with the mechanic. NPCs face the centre (`hubPads`), so the
ruins stand behind them on the outer side.

## The Breach (plan version 4)

The hub is a safe zone (`IslandTracker` cancels every player damage in `sb_hub`), so immune cells walking among the NPCs
were no challenge. The **Breach** (`HubBreach`, pure data) is the one place of the hub where players can be hurt: a torn
islet north-west of the fragment (centre `(-62, -30)`, radius 13), dark deepslate split by crying obsidian, a tear of
amethyst at its centre with violet shards above, four broken pillars with soul lanterns. A railed bridge leaves the rim
near the Archivist; a sign at each end warns "No sanctuary". When a Celestial Breath invasion targets the hub
(`invade:hub`), its immune cells spawn on the islet only, for the players standing on it (`breath/BreachSpawns`), and a
cell that leaves it (onto the bridge or the hub) is removed within a second. Death there (checked in game): inventory kept
(`keep_inventory`), the purse penalty applies, respawn on the player's island (the Skyblock respawn point of `ModeTravel`),
`/back` offered. Cells only come during an invasion event; the islet is empty the rest of the time.

## Travel

Spawn on the Landing; `/hub` and falling back from a Realm land there too. Island: Island Gatekeeper (`/island`).
Dungeon: Dungeon Gatekeeper. Rift: Rift Gatekeeper. Realms: walk the processional south, cross the Threshold and the
bridge, step into the gate of the Realm (lit gates only). The hub is safe: lit, no mobs, no fall damage.

## Rebuilding

`HubPlan.VERSION` is bumped when the layout changes. `HubManager` notices an older marker, wipes the old hub square
(x -45..45, z -49..41, the Quai and its bridge are outside it), then rebuilds the spawn and the rest in batches. NPC
entities are respawned by `HubNpcs`. Plan order, so a cap cuts decoration and never the road: spawn, ground, pads and
gates, causeway, camp ruins, spires, debris, underside.
