# Celestial Breath (GDD 4.7, 19.2-19.4, issue #51)

Feature `breath` (`survival-plugin/.../breath/`), data in `db/seed/breath.sql` (domain `breath`, settings only, no migration).

- **Pulsation**: every `breath.interval-minutes` (180) the clock draws one event from `breath.events` (weighted, never the same twice in a row)
  and runs it for its `minutes`. State in `plugins/VampyuyuSurvival/breath.json` (cycle, running event, next date): a restart resumes the event.
- **Event row**: `id|title|weight|minutes|effects`. Effects: `yield:<resource|*>:<pct>`, `machine:<pct>`, `monster:<pct>`,
  `route:<realm>`, `fracture:<realm>:<floor>`, `invade:<hub|realm>`. A row is refused (logged) unless it gives something and costs something (GDD 19.3).
- **Banner**: chat lines + title when an event starts, again on join while it runs; `/breath` shows it.
- **Void Storms**: `BreathApi.yieldFactor` (Realm resources, `RealmListener`), `machineFactor` (miner Flux, `MachineService`), `monsterFactor`
  (damage of hostile mobs to players, not in dungeons or Bastions), `route:` shuts a Sky Gate (`SkyGateApi.addTravelGuard`).
- **Fractures**: `/breath fracture` in the Realm named by the event starts a dungeon run (`DungeonApi.startRun`, floor and seed of the event, same layout for everyone).
  Clearing it (`DungeonClearEvent`, seed match) pays Memory Shards once per player and Fracture, plus a Fragment chance.
- **Stellar Invasions**: immune cells (`breath.invasion.*`) spawn around players in the Hub or the named Realm only, never on islands; they cannot
  break blocks or doors or explode, drop nothing, and are removed when the event ends. Kills pay a few Memory Shards (capped per pulsation) and a rare Fragment.
- **Interpulsation Fragments**: at most one per player per pulsation, `fragments.per-key` (5) make a key. `BreathApi.keys / consumeKey` are what the Hell Gao arena reads.
  Hell Gao itself is not part of this issue.

Not tested in a game: all the numbers are guesses (GDD 16.2). Admin: `/breath start <id>`, `/breath stop` (`vampyuyu.sb.breath.admin`).
