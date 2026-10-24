import type { Config } from "./arenaShared";
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
const teamNames: Record<string, string> = {
  village: "Village team",
  wolves: "Wolves' side",
  vampires: "Vampires",
  cult: "The cult",
  solo: "Playing alone",
};
export function WerewolfRulesEditor({
  config,
  onPatch,
}: {
  config: Config;
  onPatch: (patch: Partial<Config>) => void;
}) {
  const ww = config.werewolf || defaultWerewolf;
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
  return (
    <div className="arena-game-rules">
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
    </div>
  );
}
