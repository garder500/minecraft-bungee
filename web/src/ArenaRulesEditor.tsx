import type { Config } from "./Arenas";
import { werewolfRoles } from "./werewolfRoles";

export type WerewolfRules = {
  roles: Record<string, number>;
  cupidSeconds: number;
  witchSeconds: number;
  hunterSeconds: number;
  ambienceEnabled?: boolean;
  reveal?: string;
  ballots?: string;
  dice?: boolean;
  amulet?: boolean;
  voting?: string;
  mayor?: string;
  minion?: string;
};
export type FreeplayRules = {
  objective: string;
  targetScore: number;
  pointsPerEvent: number;
  allowFlight: boolean;
  hunger: boolean;
  personalTime: number;
  weather: string;
  kit: Record<string, number>;
  effects: Record<string, number>;
  checkpoints: { x: number; y: number; z: number; radius: number }[];
  instructions: string;
};
export const defaultWerewolf: WerewolfRules = {
  roles: {},
  cupidSeconds: 15,
  witchSeconds: 15,
  hunterSeconds: 15,
  ambienceEnabled: true,
  reveal: "FULL",
  ballots: "OPEN",
  dice: false,
  amulet: false,
  voting: "POINT",
  mayor: "DEALT",
  minion: "DEALT",
};
export const defaultFreeplay: FreeplayRules = {
  objective: "SANDBOX",
  targetScore: 10,
  pointsPerEvent: 1,
  allowFlight: false,
  hunger: false,
  personalTime: -1,
  weather: "WORLD",
  kit: {},
  effects: {},
  checkpoints: [],
  instructions:
    "Enjoy the arena. Use your compass to follow the game or leave.",
};
const teamNames: Record<string, string> = {
  village: "Village team",
  wolves: "Wolves' side",
  vampires: "Vampires",
  cult: "The cult",
  solo: "Playing alone",
};
export function ArenaRulesEditor({
  config,
  onPatch,
}: {
  config: Config;
  onPatch: (patch: Partial<Config>) => void;
}) {
  const ww = config.werewolf || defaultWerewolf,
    free = config.freeplay || defaultFreeplay;
  const automatic = Object.keys(ww.roles).length === 0;
  function cast(next: Record<string, number>) {
    const sum = Object.values(next).reduce((a, b) => a + b, 0);
    const minimum = Math.max(
      4,
      sum,
      ((next.WEREWOLF || 0) + (next.VAMPIRE || 0) + (next.LONE_WOLF || 0) || 1) * 2 + 1,
      config.minPlayers,
    );
    onPatch({
      werewolf: { ...ww, roles: next },
      minPlayers: minimum,
      maxPlayers: Math.max(config.maxPlayers, minimum),
    });
  }
  function rule(patch: Partial<FreeplayRules>) {
    onPatch({ freeplay: { ...free, ...patch } });
  }
  function objective(value: string) {
    const patch: Partial<Config> = { freeplay: { ...free, objective: value } };
    if (value === "PVP") {
      patch.allowPvP = true;
      patch.gameMode = "SURVIVAL";
      patch.freeplay = {
        ...patch.freeplay!,
        kit: { IRON_SWORD: 1, SHIELD: 1, COOKED_BEEF: 16 },
        instructions:
          "Win points by defeating other participants. You respawn inside the arena; your original belongings stay safe.",
      };
    }
    if (value === "BLOCK_BREAK") {
      patch.allowedBreakBlocks = config.allowedBreakBlocks.length
        ? config.allowedBreakBlocks
        : ["DIRT"];
      patch.gameMode = "SURVIVAL";
      patch.freeplay = {
        ...patch.freeplay!,
        kit: { IRON_SHOVEL: 1 },
        instructions:
          "Break the allowed blocks to earn points. The first player to the target score wins.",
      };
    }
    if (value === "BLOCK_PLACE") {
      patch.allowedPlaceBlocks = config.allowedPlaceBlocks.length
        ? config.allowedPlaceBlocks
        : ["OAK_PLANKS"];
      patch.gameMode = "SURVIVAL";
      patch.freeplay = {
        ...patch.freeplay!,
        kit: { OAK_PLANKS: 64 },
        instructions:
          "Build with the allowed blocks. Each valid placement earns points.",
      };
    }
    if (value === "RACE") {
      patch.allowPvP = false;
      patch.freeplay = {
        ...patch.freeplay!,
        targetScore: 1,
        checkpoints:
          free.checkpoints.length >= 2
            ? free.checkpoints
            : [
                {
                  x: config.bounds.minX + 1,
                  y: config.spawn.y,
                  z: (config.bounds.minZ + config.bounds.maxZ) / 2,
                  radius: 1,
                },
                {
                  x: config.bounds.maxX - 1,
                  y: config.spawn.y,
                  z: (config.bounds.minZ + config.bounds.maxZ) / 2,
                  radius: 1,
                },
              ],
        instructions:
          "Reach each checkpoint in order. Complete the required laps to win.",
      };
    }
    onPatch(patch);
  }
  return (
    <div className="arena-game-rules">
      {config.preset === "werewolf" ? (
        <>
          <div className="management-heading">
            <div>
              <span className="eyebrow">MAKE YOUR OWN CAST</span>
              <h3>Secret roles. Different stories.</h3>
            </div>
            <label className="arena-inline-toggle">
              <input
                type="checkbox"
                checked={automatic}
                onChange={(e) =>
                  e.target.checked
                    ? onPatch({ werewolf: { ...ww, roles: {} } })
                    : cast({
                        WEREWOLF: 1,
                        SEER: 1,
                        CUPID: 1,
                        HUNTER: 1,
                        WITCH: 1,
                        GUARD: 1,
                      })
                }
              />
              Automatic cast
            </label>
          </div>
          <p className="console-note">
            Automatic casting follows the rulebook: one wolf up to 8 players, two
            up to 11, three up to 15, then one per five. It adds a Seer, then
            the Witch from 6 players, the Hunter from 7, Cupid from 8 and the
            Guard from 10, and keeps adding village roles on bigger tables.
            Vampires, the cult leader, the lone wolf, the hoodlum and the tanner
            only join a custom cast, which fills any remaining places with
            villagers.
          </p>
          {Object.keys(teamNames).map((team) => (
            <div key={team}>
              <span className="eyebrow">{teamNames[team].toUpperCase()}</span>
              <div className="arena-role-grid">
                {werewolfRoles
                  .filter((role) => role.team === team)
                  .map(({ id, name, blurb, icon, max, impact }) => (
                    <label key={id} className="arena-role-card">
                      <span aria-hidden="true">{icon}</span>
                      <strong>{name}</strong>
                      <small>
                        {blurb} <i>Balance {impact > 0 ? "+" : ""}{impact}</i>
                      </small>
                      {automatic ? (
                        <b>Automatic</b>
                      ) : (
                        <input
                          type="number"
                          aria-label={`${name} count`}
                          value={ww.roles[id] || 0}
                          min={0}
                          max={max}
                          onChange={(e) =>
                            cast({ ...ww.roles, [id]: Number(e.target.value) })
                          }
                        />
                      )}
                    </label>
                  ))}
              </div>
            </div>
          ))}
          {!automatic && (
            <p className="console-note">
              {Object.values(ww.roles).reduce((a, b) => a + b, 0)} assigned
              roles · minimum {config.minPlayers} players. Killers (wolves,
              vampires) must start in the minority. Balance adds up the roles'
              weights: aim for about zero.
            </p>
          )}
          <div className="arena-form-grid">
            {(
              [
                ["cupidSeconds", "Night 1 · Cupid picks the lovers (seconds)"],
                ["witchSeconds", "Night · Witch potions time (seconds)"],
                ["hunterSeconds", "Hunter's final shot time (seconds)"],
              ] as const
            ).map(([key, label]) => (
              <label key={key}>
                {label}
                <input
                  type="number"
                  min={5}
                  max={120}
                  required
                  value={ww[key]}
                  onChange={(e) =>
                    onPatch({
                      werewolf: { ...ww, [key]: Number(e.target.value) },
                    })
                  }
                />
              </label>
            ))}
          </div>
          <span className="eyebrow">VARIANTS</span>
          <div className="arena-form-grid">
            <label>
              When someone dies, reveal…
              <select
                value={ww.reveal || "FULL"}
                onChange={(e) =>
                  onPatch({ werewolf: { ...ww, reveal: e.target.value } })
                }
              >
                <option value="FULL">Their role</option>
                <option value="TEAM">Only their team</option>
                <option value="WOLF">Only wolf or not</option>
                <option value="NONE">Nothing</option>
              </select>
            </label>
            <label>
              How the village eliminates
              <select
                value={ww.voting || "POINT"}
                onChange={(e) =>
                  onPatch({ werewolf: { ...ww, voting: e.target.value } })
                }
              >
                <option value="POINT">Everybody points: most votes falls</option>
                <option value="NOMINATE">
                  Nominate, second, defend, thumbs up or down
                </option>
              </select>
            </label>
            <label>
              The mayor
              <select
                value={ww.mayor || "DEALT"}
                onChange={(e) =>
                  onPatch({ werewolf: { ...ww, mayor: e.target.value } })
                }
              >
                <option value="DEALT">A secret card (Mayor role)</option>
                <option value="ELECTED">Elected by the village on day one</option>
              </select>
            </label>
            <label>
              The minion
              <select
                value={ww.minion || "DEALT"}
                onChange={(e) =>
                  onPatch({ werewolf: { ...ww, minion: e.target.value } })
                }
              >
                <option value="DEALT">A card (Minion role)</option>
                <option value="PICKED">Picked by the wolves on night one</option>
              </select>
            </label>
            <label>
              Day ballots
              <select
                value={ww.ballots || "OPEN"}
                onChange={(e) =>
                  onPatch({ werewolf: { ...ww, ballots: e.target.value } })
                }
              >
                <option value="OPEN">Open: everybody sees the count</option>
                <option value="SECRET">Secret until the vote closes</option>
              </select>
            </label>
            <label className="arena-inline-toggle">
              <input
                type="checkbox"
                checked={!!ww.dice}
                onChange={(e) =>
                  onPatch({ werewolf: { ...ww, dice: e.target.checked } })
                }
              />
              Daily dice: a twist every dawn
            </label>
            <label className="arena-inline-toggle">
              <input
                type="checkbox"
                checked={!!ww.amulet}
                onChange={(e) =>
                  onPatch({ werewolf: { ...ww, amulet: e.target.checked } })
                }
              />
              Amulet of Protection
            </label>
          </div>
        </>
      ) : (
        <>
          <span className="eyebrow">YOUR RULES. YOUR PLAYGROUND.</span>
          <h3>Give the game a purpose.</h3>
          <div className="arena-form-grid">
            <label>
              Objective
              <select
                value={free.objective}
                onChange={(e) => objective(e.target.value)}
              >
                <option value="SANDBOX">Sandbox · Play without scoring</option>
                <option value="PVP">PvP · Defeats earn points</option>
                <option value="BLOCK_BREAK">
                  Block challenge · Break to score
                </option>
                <option value="BLOCK_PLACE">
                  Build challenge · Place to score
                </option>
                <option value="RACE">Checkpoint race · Complete laps</option>
              </select>
            </label>
            <label>
              {free.objective === "RACE" ? "Laps to win" : "Points to win"}
              <input
                type="number"
                min={1}
                max={1000}
                disabled={free.objective === "SANDBOX"}
                value={free.targetScore}
                onChange={(e) => rule({ targetScore: Number(e.target.value) })}
              />
            </label>
            {!["SANDBOX", "RACE"].includes(free.objective) && (
              <label>
                Points per valid action
                <input
                  type="number"
                  min={1}
                  max={100}
                  required
                  value={free.pointsPerEvent}
                  onChange={(e) =>
                    rule({ pointsPerEvent: Number(e.target.value) })
                  }
                />
              </label>
            )}
            <label>
              Personal time
              <select
                value={free.personalTime}
                onChange={(e) => rule({ personalTime: Number(e.target.value) })}
              >
                <option value={-1}>Follow the world</option>
                <option value={0}>Dawn</option>
                <option value={6000}>Noon</option>
                <option value={12000}>Sunset</option>
                <option value={18000}>Midnight</option>
                {![-1, 0, 6000, 12000, 18000].includes(free.personalTime) && (
                  <option value={free.personalTime}>
                    {free.personalTime} ticks
                  </option>
                )}
              </select>
            </label>
            <label>
              Personal weather
              <select
                value={free.weather}
                onChange={(e) => rule({ weather: e.target.value })}
              >
                <option value="WORLD">Follow the world</option>
                <option value="CLEAR">Clear skies</option>
                <option value="DOWNFALL">Rain / snow</option>
              </select>
            </label>
          </div>
          <div className="arena-flags">
            {(
              [
                ["allowFlight", "Allow flight"],
                ["hunger", "Enable hunger"],
              ] as const
            ).map(([key, label]) => (
              <label key={key}>
                <input
                  type="checkbox"
                  checked={free[key]}
                  onChange={(e) => rule({ [key]: e.target.checked })}
                />
                {label}
              </label>
            ))}
          </div>
          <label>
            Instructions for players
            <textarea
              rows={3}
              maxLength={600}
              value={free.instructions}
              onChange={(e) => rule({ instructions: e.target.value })}
            />
          </label>
          <p className="console-note">
            Time and weather affect participants only. Lethal damage respawns
            participants at the arena spawn; PvP points require a valid
            opponent. At the time limit, the unique top scorer wins; a tie is a
            draw.
          </p>
          <h3>Starting kit</h3>
          <p className="console-note">
            This temporary kit replaces the arena inventory. Original items are
            restored when leaving. Use Minecraft material names and valid stack
            sizes.
          </p>
          {Object.entries(free.kit).map(([material, amount], index) => (
            <div className="arena-kit-row" key={index}>
              <label>
                Material
                <input
                  aria-label={`Kit material ${index + 1}`}
                  value={material}
                  onChange={(e) =>
                    rule({
                      kit: Object.fromEntries(
                        Object.entries(free.kit).map((entry, i) =>
                          i === index
                            ? [e.target.value.toUpperCase(), amount]
                            : entry,
                        ),
                      ),
                    })
                  }
                />
              </label>
              <label>
                Amount
                <input
                  type="number"
                  aria-label={`Kit amount ${index + 1}`}
                  min={1}
                  max={64}
                  value={amount}
                  onChange={(e) =>
                    rule({
                      kit: { ...free.kit, [material]: Number(e.target.value) },
                    })
                  }
                />
              </label>
              <button
                type="button"
                onClick={() =>
                  rule({
                    kit: Object.fromEntries(
                      Object.entries(free.kit).filter(
                        ([key]) => key !== material,
                      ),
                    ),
                  })
                }
              >
                Remove item
              </button>
            </div>
          ))}
          <button
            type="button"
            disabled={
              Object.keys(free.kit).length >= 24 || "NEW_ITEM" in free.kit
            }
            onClick={() => rule({ kit: { ...free.kit, NEW_ITEM: 1 } })}
          >
            + Add kit item
          </button>
          <h3>Player effects</h3>
          <div className="arena-effect-grid">
            {(
              [
                "speed",
                "jump_boost",
                "strength",
                "regeneration",
                "night_vision",
                "haste",
              ] as const
            ).map((effect) => (
              <label key={effect}>
                {effect.replaceAll("_", " ")}
                <select
                  value={free.effects[effect] || 0}
                  onChange={(e) => {
                    const effects = { ...free.effects };
                    if (Number(e.target.value))
                      effects[effect] = Number(e.target.value);
                    else delete effects[effect];
                    rule({ effects });
                  }}
                >
                  <option value={0}>Off</option>
                  {[1, 2, 3, 4, 5].map((n) => (
                    <option key={n} value={n}>
                      Level {n}
                    </option>
                  ))}
                </select>
              </label>
            ))}
          </div>
          {free.objective === "RACE" && (
            <>
              <h3>Ordered checkpoints</h3>
              <p className="console-note">
                Players must enter these areas in order. Use at least two
                non-overlapping checkpoints, all inside the play area. Build the
                route and review every coordinate.
              </p>
              {free.checkpoints.map((point, index) => (
                <div className="arena-checkpoint" key={index}>
                  <strong>#{index + 1}</strong>
                  {(["x", "y", "z", "radius"] as const).map((key) => (
                    <label key={key}>
                      {key}
                      <input
                        type="number"
                        step="any"
                        min={key === "radius" ? 1 : undefined}
                        max={key === "radius" ? 8 : undefined}
                        required
                        value={point[key]}
                        onChange={(e) =>
                          rule({
                            checkpoints: free.checkpoints.map((p, i) =>
                              i === index
                                ? { ...p, [key]: Number(e.target.value) }
                                : p,
                            ),
                          })
                        }
                      />
                    </label>
                  ))}
                  <button
                    type="button"
                    onClick={() =>
                      rule({
                        checkpoints: free.checkpoints.filter(
                          (_, i) => i !== index,
                        ),
                      })
                    }
                  >
                    Remove
                  </button>
                </div>
              ))}
              <button
                type="button"
                disabled={free.checkpoints.length >= 20}
                onClick={() =>
                  rule({
                    checkpoints: [
                      ...free.checkpoints,
                      {
                        x: config.spawn.x,
                        y: config.spawn.y,
                        z: config.spawn.z,
                        radius: 1,
                      },
                    ],
                  })
                }
              >
                + Add checkpoint
              </button>
            </>
          )}
        </>
      )}
    </div>
  );
}
