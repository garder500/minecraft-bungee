# Quai des Huit (GDD 17.1)

Eight Sky Gates around a central light, one per Realm (`api/Realm`: ashes, grove, heights, citadel, marshes,
archipelago, depths, necropolis). Built in the hub world (`sb_hub`) by `QuaiManager` from `QuaiPlan`: platform centre
at world (0, `hub.y`, 72), a bridge that continues the hub's processional (hub z 43 to 53, see `hub.md`). Marker `island/quai.json` holds the plan version.
Nothing is built while `sb_hub` does not exist or `hub.enabled` is false.

- **State**: per player, `island/gates.json` (`SkyGateState`). Every gate starts dark.
- **Look**: the world holds the dark gate (tinted glass, near-black: black stained glass looks light blue against the sky). A lit gate is shown to its owner only, with client-side block
  changes (coloured glass, sea lantern crown, sign "Lit") resent every second near the Quai (`SkyGates.tick`).
- **Travel**: walking into the glass of a lit gate. A 1.2 s blindness hides the teleport (title with the Realm).
  Dark gate: a "dormant" notice. Back: `/hub`, `/island`, or `/quai`.
- **API** (`api/SkyGateApi`): `light`, `extinguish`, `isLit`, `litRealms`, `setDestination(realm, player -> Location)`,
  `travel`, `quaiSpawn`. A phase that opens a Realm calls `light` and `setDestination`. All eight Realms (Ashes, Grove, Heights, Citadel, Marshes, Archipelago, Depths, Necropolis) register a destination
when their world is built (`AshesRealm`, `GroveRealm`, `FrostRealm`, `CitadelRealm`, `MarshRealm`, `StormRealm`,
`DepthsRealm`, `NecropolisRealm`), so a lit gate leads to the Realm's own landing, a themed platform with a sign
(`<Realm>Plan`, anchor `spawn`). Every landing has the same **return arch** (`island/ReturnArch.java`, plan versions
bumped to 2): two pillars and a lintel (5 wide, 5 tall with the lights) around a 3 x 3 veil of stained glass, two blocks
south of the arrival spot (veil at `spawn.z + 2`), a Realm palette (`ReturnArch.palette`) and a wall sign on the east
pillar: Realm name / "Walk through the" / "arch to the Quai" / "/hub or /island". Walking into the veil
(`ReturnArch.inVeil`, handled in `SkyGates.onMove`, no travel guard on the way home) gives the Gate's beat
(1.2 s blindness, title "Quai des Huit") and teleports to the Quai; `/quai`, `/hub` and `/island` still work.
Each `*Realm` registers its arrival spot with `SkyGateApi.setReturnArch` (world and position come from `spawn()`). `QuaiLanding` (a small platform per Realm in the sky of the hub) is only a fallback for a
Realm that registers no destination (none today); while a Realm is not ready its destination returns null and the
player sees "The way through the Gate is not open right now." instead.
- **Waking Gates (title, then reveal)**: every Guardian kill that lights Gates (`*.gate-boss` settings: the Queen wakes five at
  once, each `*Realm` handler calls `SkyGateApi.light`) goes through `SkyGates.light`, which does two things for the player.
  (1) The same tick, one batch (`SkyGates.announce`, words in `island/GateReveal`, fr/de in `lang/menu_*.properties`
  `quai.wake.*`): a short title ("A Gate wakes", or "5 Gates wake"; a longer one clips in a small window) with the Realm name(s) as subtitle
  (one or two Gates; more would not fit, the names go to the chat line "... wake on the Quai: names"), a beacon and amethyst sound, and a chat line with a clickable `[Go to the Quai]` (`/quai`).
  The Guardian's voiced first-clear line is still played by `BossRewards`; nothing new is recorded. The old per-Gate "is lit" lines
  of `light`, Storm, Depths and Ashes are gone (the title replaces them). An offline winner gets no title, only the reveal.
  (2) The Gate stays dark in the player's view until its reveal: the first time they are near the Quai (`VIEW_RANGE`, not travelling)
  `GateReveal.Pacer` waits `island.quai.reveal-start-ticks` (40), then wakes one Gate every `island.quai.reveal-gap-ticks` (20), in
  Gate order: END_ROD and totem burst, a flash, a chime that rises with each Gate, then it stays lit. Shown only to that player.
  "Seen" is saved per player in `island/gates.json` (`seen`, next to the lit list: it also covers offline winners). A file without
  `seen` (before this feature) counts every lit Gate as seen, so nobody replays old Gates; `extinguish` forgets a Gate, so
  `/gateadmin dark` then `light` replays it. Leaving the Quai mid-sequence resumes the rest on the next visit.
  The reveal only runs within 48 blocks of the platform centre (`VIEW_RANGE`, 3D). Tests: `GateRevealTest` (new Gates, order, once, migration, pacing, words), `SkyGatesTest` (five Gates in one tick: one chat line). Real client: `--scenario realms` step 2b
  (`E2E_SKIP=reveal` skips it): `rl-1b-wake-*.png`, `rl-1b-reveal-*.png`.
- **Commands**: `/quai` (go there), `/gates` (list), `/gateadmin light|dark <player> <realm|all>`, `list`,
  `travel <player> <realm>`, `rebuild`.
- Layout changes: bump `QuaiPlan.VERSION` so existing Quais are rebuilt (2: the bridge is restyled as the end of the processional, with a ruined arch).
- **Mouth of the Devourer** (`island/QuaiMouth.java`, plan version 4): a dark rift in the south rim of the platform, between Gates 4 and 5,
  opposite the bridge, reached by a dark strip from the dais. An elliptical opening (obsidian membrane, blackstone frame,
  dripstone teeth, sign "The Mouth of the Devourer"). Same mechanism as the Gates: the world holds it sealed, a player who may
  enter sees it open (client-side blocks: magenta glass with a sea lantern behind, sign "Open", portal particles; resent every second,
  `SkyGates.refreshMouth`, its own shown-set). **Open condition = `AstralApi.canEnter`**: the player defeated the Guardian of
  every Realm (setting `realm.astral.require-guardians` false opens it to all). This is what the code has for the "Great
  Disillusion" (the eighth Guardian down, the text of `AstralRealm.enter`); nothing else is named that. The answer is cached 3 s per player.
  Walking up to the opening (`QuaiMouth.inMouth`, never overlapping a Gate zone): open = 1.2 s blindness then `AstralApi.enter`;
  sealed = `enter` tells the player which Guardians are missing (2.5 s cooldown). `/astral go` stays as the other way in.
