# Prologue journey, island to the Echo of the Rift (target ~5 h, GDD 4.1 / 13.2 / 21)

Tool: `python3 scripts/sim_journey.py` (Monte Carlo of `OreRoller`, ore chances, pity and Forge recipes read from
`quarry.sql`; `--check` fails when the median is outside 4-6 h). Run on 2026-11-02 with the default guesses.

## What the numbers say (ore side)
Bronze stage = Forge, Bronze Core, Bronze set, Coal Generator, Battery, Miner I (+ iron, copper, coal, bucket, pickaxes):
84 Bronzite, 71 iron ingots (639 nuggets), 17 copper, 57 coal, 6 redstone (bought, 8 coins each).
Blocks to break: p10 6 200, p50 6 500, p90 6 900 (pity 300 keeps Bronzite tight; **iron nuggets at 10 % are the real
bottleneck**, not Bronzite).
At 2 blocks/s by hand, 60 % of the time mining: 1 stone ring = 0.6 blocks/s effective, about 3 h of mining, total ~5.0 h;
2 or 3 rings = 1.2 blocks/s, 1.5 h, total ~3.5 h. So the ore economy fits the 5 h target as long as the player runs
at least one ring from the start and the quest chain pushes a second ring early. No change to chances, pity or
recipes is needed from this simulation.

## Gaps found (not tuning, content)
1. **A9-A13 are not in the quest plan.** There is no island gate, NPC / Echo Key, market tutorial, Rift entry or Echo quest
   at the end of the prologue (#24 did the lore only).
2. **The Echo quest sits at the end of the main chain** (`end` category: tier III unlock, End, Void Sigil which needs
   the three other bosses' drops and a 6 h Forge job). Reaching it takes tens of hours, far above 5 h. The A13 fight must
   be reachable with the Bronze set (the Echo as dungeon 0, #23) and the old `end` quests should become later content.
3. Boss HP is still on the old curve (#20), so the fight length cannot be estimated; `--echo` is a flat 25 min guess.
4. Hub, Quai des Huit and the Novice class (#21, #22, #25) do not exist yet: `--hub` is a flat guess.

## Not verified
Nothing was compiled or run in game (no Docker, Paper repository unreachable from this sandbox). Speed of the player,
tutorial and build time are guesses to be replaced by a timed playtest with `e2e_client.py --scenario start`.

## Update (#60): A9-A13 and the Echo moved up
The `rift` main category ("The Hub and the Rift") opens right after the Bronze armor quest (`bronze_armor`):
`hub_gate` (event `hub_arrive`, fired by `/hub`), `hub_guard`, `hub_archivist` (reward: the **Echo Key**), `hub_merchant`
(events `hub_npc:<pad>`, fired by `HubNpcs.click`), `hub_market_lesson` (earn 300), `rift_enter` (click the Rift
Gatekeeper while carrying the key) and `echo_defeat` (`BOSS_KILL voidwyrm`). The Echo is summoned by the `echo_key` at an
altar at the Rift arch of `sb_hub` (-34.5, 101, 0.5, assumes `hub.y` 100); `echo_key` is also forgeable (16 Bronzite, 4 iron,
5 min) for repeat fights. The old "Face the Echo" quest of the `end` category is retired (its id slot kept). The Void Sigil
no longer summons anything. Not compiled or played: no Maven repository or Docker in the sandbox.
