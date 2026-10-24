# Werewolf: roles, calls and how a role is built

The arena plugin plays the whole Ultimate Werewolf role set (plus the classic roles it already had)
with no moderator: the plugin is the moderator. This file is both the player-facing rules summary
and the contract role code is written against.

## 1. Architecture

| Piece | File(s) | Owns |
|---|---|---|
| Rules engine | `WerewolfGame.java` | phases, the night queue, ballots and weights, deaths, win check |
| Role data | `src/main/resources/roles/<ROLE>.json`, `RoleInfo.java` | name, team, impact, colour, HUD palette/icon/words, help text, the role's night call |
| Role modules | `*Module.java`, `RoleModule.java`, `Modules.java` | the rules of a few related roles, as hooks |
| Presentation | `WerewolfPresentation`, `CustomHud`, `WerewolfHud` | text and HUD, driven by `RoleInfo` |
| Bots | `WerewolfBots` | practice players; a module plays its own roles through `RoleModule.bot` |
| Pack art | `resource-pack/art/hud.py`, `icons.py`, `layout.py` | HUD sprites and icons, generated from the role JSON |

A **module never touches shared files**. It owns: its `*Module.java`, its `*ModuleTest.java`, the
JSON files of its roles, and the SVG icon of each role (`resource-pack/art/icons/<icon>.svg`).

### Teams (`team` in the role JSON)

`village`, `wolves` (werewolves and their helpers), `vampires`, `cult`, `solo` (own goal). The win
check lives in the engine: the village wins when no wolf, lone wolf or vampire is left; the wolves
win at parity (killers at least as many as everybody else, helpers aside) while no vampire lives;
modules decide the other teams' wins (`teamWin`) and the side goals (`sideWinners`).

`WerewolfGame.wolf(role)` means "wakes with the pack and hunts" (werewolf, alpha, infector, cub,
lone wolf). The minion and the sorcerer are on the wolves' team without hunting.

## 2. The night

Each night the engine builds a queue from `WerewolfGame.NIGHT_ORDER` and opens, in order, every call
that has somebody to wake; a call with nobody ready is skipped. A call ends when every called actor
is `done` (or the clock runs out), except the two pack calls (`NIGHT`, `VAMPIRE`) which end when the
pack agrees.

| Call (`Phase`) | Order | Called roles | When |
|---|---|---|---|
| `CUPID` | 1 | Cupid | first night |
| `DOPPEL` | 2 | Doppelganger | first night |
| `HOODLUM` | 3 | Hoodlum | first night |
| `PRIEST` | 4 | Priest | every night until used |
| `GUARD` | 5 | Guard | every night |
| `SEER` | 6 | Seer | every night |
| `NIGHT` | 7 | the wolves (`wolf(role)`) | every night |
| `VAMPIRE` | 8 | Vampires (pack vote) | every night |
| `WITCH` | 9 | Witch | every night |
| `PI` | 10 | P.I. | every night until used |
| `AURA` | 11 | Aura seer | every night |
| `SORCERER` | 12 | Sorcerer | every night |
| `SPELL` | 13 | Spellcaster | every night |
| `HAG` | 14 | Old hag | every night |
| `CULT` | 15 | Cult leader | every night |
| `TROUBLE` | 16 | Troublemaker | every night until used |
| `GHOST` | 17 | Ghost (even dead) | every night |
| `AMULET` | 18 | amulet holder | variant |
| `MARTYR` | day | Martyr | when the village condemns somebody else |

Which role owns which call is `call.phase` in the role JSON (the engine reads it through
`RoleInfo.byPhase`). A module that owns a call implements `phases()` and `select`/`use`/`ready`/`bot`.

## 3. The module contract

Read `RoleModule.java`: every hook is documented there and has a do-nothing default. Facts that bite:

* Hooks run inside the game's lock and the module API (the package-private methods at the end of
  `WerewolfGame`: `roleOf`, `setRole`, `living`, `table`, `neighbour`, `nightNumber`, `notify`,
  `notifyLiving`, `log`, `describe`, `random`, `kill`, `slay`, `doom`, `cancelShot`, `exile`, `mute`,
  `callOffNextHunt`, `extraLynches`, `condemned`, `substituteCondemned`, `packVictim`, `done`) is only
  valid from a hook.
* Hooks are asked of **every** module for **every** phase and player. A hook answers its default for
  anything that is not its business (`ready` returns true for a call it does not own).
* A module keeps its per-game state in its own fields; the engine builds new module instances for
  every game (`Modules.create()`), so there is no static state.
* Messages given to `notify` may contain a player's UUID: the plugin shows it as the player's name.
* A refused action throws `IllegalArgumentException` with a short English sentence; everything the
  player reads is English.
* `kill` runs the protections (witch flask, guard, the modules' `saves`); `slay` does not.
  `kill` sources: `WOLVES`, `WOLVES2`, `POISON`, `VOTE`, `VAMPIRE`, `SHOT`, `DELAYED`, `OTHER`.
  The witch's flask and the guard only stop `WOLVES`/`WOLVES2`.
* Never loop or block. Never call `g.select`/`g.use` of another player from a hook except in `bot`.
* A seer's view of a role is `seerSees` in the JSON (lycan reads as a werewolf, minion and sorcerer
  as villagers); the aura seer, the P.I. and the sorcerer use their own tests on the *actual* role.

## 4. What the roles do

"Adapted" marks where the board game needs a human moderator and the plugin does something else.

### Village team

* **Seer** (+7), **Witch** (+4), **Hunter** (+3), **Cupid** (-3), **Guard** (+3, the rulebook's
  Bodyguard; may protect himself), **Scapegoat**, **Bear tamer**: classic roles, already in the game.
  The Witch also covers the **Magician** (same powers).
* **Apprentice seer** (+4): a villager until the Seer dies; then becomes the Seer (role change, a
  private notice) and is called in the `SEER` call from the next night.
* **Aura seer** (+3, `AURA`): each night picks a player and learns "special role" or "plain villager
  or werewolf". Special = any role other than `VILLAGER` and `WEREWOLF`. A cursed, a drunk who has
  not woken and a doppelganger who has not copied read as plain villagers.
* **Diseased** (+3): if the wolves kill him, the next night's hunt is called off
  (`g.callOffNextHunt()` from `died` when the source is `WOLVES`); the wolves still wake, nobody can
  be hunted.
* **Ghost** (+2, `GHOST`): dies on the first night (`slay` at the first dawn). Each night, alive or
  dead, he is called and may type **one letter** in the chat: the module's `chat` hook swallows it
  and `g.notifyLiving` tells the village at dawn ("A ghostly letter appears: T"). Adapted: the board
  game writes the letter on paper. He cannot name anybody (single letter only).
* **Idiot** (+2): his day ballot cannot be a skip: `autoBallot` casts a random one if he has none
  when the vote closes. Secret.
* **Lycan** (-1): a villager the Seer sees as a werewolf (`seerSees`).
* **Martyr** (+3, `MARTYR`): when the village condemns someone else, a martyr phase opens (day, 10
  seconds); button `sacrifice` takes the condemned player's place (`g.substituteCondemned`), `pass`
  lets it go. The condemned is revealed as the one who dies.
* **Mason** (+2, up to 3): on the first night (`deal`/`nightfall`) every mason gets a private notice
  naming the other masons. Adapted: the rulebook's "never speak of the masons" taboo is not
  enforced.
* **Mayor** (+2): his day ballot counts twice (`weight`). Secret. Adapted: the Mayor is dealt, not
  elected.
* **Old hag** (+1, `HAG`): each night picks a player (not herself) who is away for the next day:
  `g.exile`. An exiled player cannot chat, vote or be voted for, cannot be shot by the hunter; the
  plugin hides them from the table.
* **Old man** (0): dies at the dawn of night number *(living wolves + 1)*, recounted every night (a
  wolf falling brings his death closer), `dawn` hook, `slay` ("died of old age").
* **P.I.** (+3, `PI`): once per game picks a player and learns whether a werewolf (`wolf(role)`) is
  among that player and the two living players beside them (`g.neighbour(p, -1/+1)`).
* **Pacifist** (-2): his day ballots never count (`weight` 0). Secret.
* **Priest** (+3, `PRIEST`): once per game blesses a player (not himself). The next attempt to kill
  the blessed player by any means except old age and grief fails (`saves`), and the blessing is
  consumed. It stays if the priest dies.
* **Prince** (+3): the first time the village condemns him (`saves` on source `VOTE`) he is revealed
  (`notifyLiving`) and spared; the day ends as after any verdict.
* **Spellcaster** (+1, `SPELL`): each night silences a player for the next day (`g.mute`): no chat,
  may still vote. May silence himself, never the same player twice in a game.
* **Tough guy** (+3): if the wolves attack him he survives the night (`saves`, `g.doom(...)`) and
  dies at the next dawn, in addition to that night's victim. The village is told nobody died. Source
  `DELAYED` is never saved by him.
* **Troublemaker** (+2, `TROUBLE`): once per game, button `stir`: tomorrow's day vote eliminates two
  players instead of one (`g.extraLynches(1)`); the village is told after the first verdict. `pass`
  keeps the power.

### Wolf side

* **Werewolf** (-6), **Alpha wolf**, **Infector**, **Wolf cub** (-8): classic, already in the game.
* **Sorcerer** (-3, `SORCERER`): wolves' team, does not wake with the pack; each night picks a player
  and learns whether they are the Seer. The Seer sees him as a villager. The wolves do not know him.
* **Minion** (-6): wolves' team, does not wake with the pack; on the first night he learns who the
  wolves are (private notice). The Seer sees him as a villager; the wolves do not know him.
* **Lone wolf** (-5, solo): hunts with the pack (`wolf(role)`), wins only if he is the last player
  alive, or one of the last two with a non-wolf-side player (`teamWin` returns `LONE_WOLF`).

### Switching teams, hidden roles

* **Cursed** (-3): a villager (the Seer sees one). If the wolves choose him he does not die
  (`saves`) and becomes a werewolf at the next nightfall (`nightfall` hook, `setRole`, notice to him;
  the pack learns him at that moment).
* **Doppelganger** (-2, `DOPPEL`): first night, picks a player; when that player dies (`died`) she
  secretly takes over their role (`setRole`, a private notice). She is a villager meanwhile.
* **Drunk** (-3): dealt a hidden real role (chosen in `deal`: werewolf, villager or another role the
  deal did not use) and believes he is a villager (`perceived`) until the third night (`nightfall`
  reveals it with a notice). Until then he is not called (`ready` false for every call). The Seer
  sees the real role.

### Other teams

* **Vampire** (-8, up to 6, team `vampires`, `VAMPIRE` pack call): the vampires vote a victim each
  night (like the wolves, `g.packVictim(Phase.VAMPIRE)` after the call). The victim dies when the day
  vote opens (`entered(DAY_VOTE)` -> `g.kill(v, "VAMPIRE", ...)`). Wolves cannot kill a vampire
  (`saves` on `WOLVES`). With vampires in the game the **Hunter is a vampire hunter**: when he dies
  (`died`) `g.cancelShot(hunter)` and the vampire seated closest to him (by table distance) is killed
  instead. Vampires win when every living player is a vampire (`teamWin` -> `VAMPIRES`).
* **Cult leader** (+1, `CULT`): each night recruits a player (not a member, not himself); recruits do
  not know. The cult wins when every living player is a member, the leader included or not
  (`teamWin` -> `CULT`); `members` lists the leader and the recruits for the winners.
* **Hoodlum** (0, `HOODLUM`, solo): on the first night marks two players (two `select` calls, the
  second completes). He wins (`sideWinners`) if both marks are dead and he is alive when the game
  ends.
* **Tanner** (+1, solo): wins (`sideWinners`) if he dies, whatever the cause; the game goes on.
* **Soulmates** are Cupid's lovers, already in the game; they win with their team if it is the same,
  else alone as the last two.

## 5. Rules that are not roles (engine)

* **Parity**: the wolves win when their killers are at least as many as everyone else.
* **Revote**: a tied day vote asks once for a new vote among the tied players only; a second tie
  eliminates nobody (the Scapegoat, if alive, still takes a tie at once).
* **Day vote modes, reveal modes, daily dice, amulet**: variants, defaults off. See the arena rules.

## 6. Writing and testing a module

* Tests are JUnit 4 in `arena-plugin/src/test/java/win/vampyuyu/arena/<Module>Test.java`; build a
  game with `new WerewolfGame(ids, 5, 5, 5, new Random(n), new GameRules.Werewolf(cast, 5, 5, 5))`
  (see `NewRolesTest`). Use `Map.of("WEREWOLF", 1, "<ROLE>", 1)`-style casts and read roles back with
  `g.role(p)`; `GameRules.Werewolf` accepts every role, at most `max` copies each.
* Run `make plugin-build PLUGIN=arena` from your own checkout (about two minutes); it runs the whole
  suite. `FAST=1` skips the tests (compile only).
* A bot hook must always leave the game able to continue: if it does nothing the clock ends the call.
* Each role needs: a rule test, a bot test (the practice game with that role in the cast finishes:
  see `PracticeGameTest`), its JSON texts (`help`, `call.*` in English, within the lengths the
  `scripts/test_pack.py` width test enforces), and an icon.
* Icons: `resource-pack/art/icons/<icon>.svg`, `viewBox="0 0 32 32"`, bold flat shapes with the
  `#0e0b18` outline (stroke 1.4-1.8), filled in the role's `color` plus one lighter and one darker
  tone, readable at 32 px (see `guard.svg`, `witch.svg`).
