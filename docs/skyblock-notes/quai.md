# Quai des Huit (GDD 17.1)

Eight Sky Gates around a central light, one per Realm (`api/Realm`: ashes, grove, heights, citadel, marshes,
archipelago, depths, necropolis). Built in the hub world (`sb_hub`) by `QuaiManager` from `QuaiPlan`: platform centre
at world (0, `hub.y`, 72), a bridge from the hub ring road (z 44). Marker `island/quai.json` holds the plan version.
Nothing is built while `sb_hub` does not exist or `hub.enabled` is false.

- **State**: per player, `island/gates.json` (`SkyGateState`). Every gate starts dark.
- **Look**: the world holds the dark gate (black glass). A lit gate is shown to its owner only, with client-side block
  changes (coloured glass, sea lantern crown, sign "Lit") resent every second near the Quai (`SkyGates.tick`).
- **Travel**: walking into the glass of a lit gate. A 1.2 s blindness hides the teleport (title with the Realm).
  Dark gate: a "dormant" notice. Back: `/hub`, `/island`, or `/quai`.
- **API** (`api/SkyGateApi`): `light`, `extinguish`, `isLit`, `litRealms`, `setDestination(realm, player -> Location)`,
  `travel`, `quaiSpawn`. A phase that opens a Realm calls `light` and `setDestination`. Without a destination a lit gate
  leads to a placeholder landing (`QuaiLanding`, a small platform per Realm in the sky of the hub, built on first use).
- **Commands**: `/quai` (go there), `/gates` (list), `/gateadmin light|dark <player> <realm|all>`, `list`,
  `travel <player> <realm>`, `rebuild`.
- Layout changes: bump `QuaiPlan.VERSION` so existing Quais are rebuilt.
