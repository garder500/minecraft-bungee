import { useEffect, useRef, useState, type FormEvent } from "react";
import { api } from "./api";
import {
  defaultBounds,
  HostToolbar,
  Invitation,
  Notices,
  NumberField,
  Participants,
  servers,
  useEngine,
  type Config,
  type World,
} from "./arenaShared";
import { useConfirm } from "./ConfirmDialog";
import { GameLogs } from "./GameLogs";
import { defaultWerewolf, WerewolfRulesEditor } from "./WerewolfRulesEditor";

const titles: Record<string, string> = {
  WAITING: "Waiting for players",
  DISCUSSION: "Day · Discussion",
  DAY_VOTE: "Day · Voting",
  GUARD: "Night · Guard",
  SEER: "Night · Seer",
  NIGHT: "Night · Wolves",
  FINISHED: "Game finished",
  CUPID: "Cupid · Choose the lovers",
  WITCH: "Night · Potions",
  HUNTER_SHOT: "Hunter · Final shot",
  RESTORING: "Restoring players",
};
function fresh(world: World): Config {
  return {
    id: "",
    name: "",
    worldName: world.name,
    preset: "werewolf",
    bounds: defaultBounds(world),
    spawn: { ...world.spawn, yaw: 0, pitch: 0 },
    allowedBreakBlocks: [],
    allowedPlaceBlocks: [],
    allowPvP: false,
    allowChests: false,
    allowDoors: true,
    gameMode: "ADVENTURE",
    durationSeconds: 1800,
    minPlayers: 4,
    maxPlayers: 20,
    discussionSeconds: 60,
    voteSeconds: 30,
    nightSeconds: 30,
    werewolf: structuredClone(defaultWerewolf),
  };
}
/** Werewolf sessions: cast, timers, rule variants and practice. Places and the platform are set up in game. */
export function WerewolfSessions({
  csrf,
  onError,
}: {
  csrf: string;
  onError: (error: unknown) => void;
}) {
  const confirm = useConfirm();
  const [practiceBots, setPracticeBots] = useState(7);
  const [practicePlayer, setPracticePlayer] = useState("");
  const [draft, setDraft] = useState<Config | null>(null),
    [editing, setEditing] = useState(false);
  const engine = useEngine(
    csrf,
    onError,
    (s) => s.sessions,
    () => {
      setDraft(null);
      setPracticePlayer("");
    },
  );
  const { server, state, selected, busy, action } = engine;
  // Older plugins only know the arena-* actions; the new ones speak ww-*.
  const ww = (name: string) =>
    state?.split ? `ww-${name}` : `arena-${name}`;
  const editor = useRef<HTMLFormElement>(null);
  useEffect(() => {
    if (draft)
      editor.current?.scrollIntoView({ behavior: "smooth", block: "start" });
  }, [draft !== null]);
  const current = state?.sessions.find((a) => a.config.id === selected);
  const practiceHumans = current?.players.filter((p) => !p.bot).length || 0;
  const practiceCandidates =
    state?.onlinePlayers.filter(
      (p) =>
        !state.all.some((a) =>
          a.players.some((member) => member.uuid === p.uuid),
        ),
    ) || [];
  const practiceTarget =
    practicePlayer ||
    (practiceCandidates.length === 1 ? practiceCandidates[0].uuid : "");
  const practiceCount = current
    ? Math.min(
        current.config.maxPlayers - Math.max(1, practiceHumans),
        Math.max(
          current.config.minPlayers - Math.max(1, practiceHumans),
          practiceBots,
        ),
      )
    : practiceBots;
  async function closeDraft() {
    if (
      draft &&
      !(await confirm("Discard the unsaved Werewolf configuration?", {
        title: "Discard changes?",
        confirmLabel: "Discard changes",
      }))
    )
      return false;
    setDraft(null);
    return true;
  }
  async function edit(config?: Config) {
    if (draft && !(await closeDraft())) return;
    if (!state?.worlds.length) return;
    setDraft(config ? structuredClone(config) : fresh(state.worlds[0]));
    setEditing(!!config);
  }
  async function save(e: FormEvent) {
    e.preventDefault();
    if (!draft) return;
    if (!editing && state?.all.some((a) => a.config.id === draft.id)) {
      engine.setError(
        "This ID already exists. Edit the existing session or choose a different ID.",
      );
      return;
    }
    if (
      !(await confirm(
        "Save these Werewolf settings? They apply from the next game.",
        { title: "Save Werewolf settings?", confirmLabel: "Save settings" },
      ))
    )
      return;
    engine.setBusy(true);
    try {
      // Unknown fields (places, options, ...) travel with the draft untouched.
      await api(`/servers/${server}/game/action`, "POST", csrf, {
        action: state?.split ? "ww-config-save" : "arena-save",
        arenaId: draft.id,
        config: draft,
      });
      engine.setSelected(draft.id);
      setDraft(null);
      engine.setNotice(
        "Settings saved. Players can now join; the game starts when you choose Start game.",
      );
      await engine.load();
    } catch (e) {
      onError(e);
    } finally {
      engine.setBusy(false);
    }
  }
  const field = (key: keyof Config, label: string, min: number, max: number) => (
    <NumberField
      key={key}
      draft={draft!}
      setDraft={setDraft}
      field={key}
      label={label}
      min={min}
      max={max}
    />
  );
  return (
    <section className="arena-page">
      <HostToolbar
        engine={engine}
        onSwitch={async (next) => {
          if (await closeDraft()) engine.setServer(next);
        }}
        create={{ label: "+ Create session", onClick: () => edit() }}
      />
      <Notices error={engine.error} notice={engine.notice} />
      {!state && !engine.error && <p>Connecting to the arena engine…</p>}
      {state && (
        <>
          <div className="arena-summary">
            <div>
              <span className="eyebrow">MOONLIT TABLES</span>
              <strong>{state.sessions.length}</strong>
              <small>Werewolf sessions</small>
            </div>
            <div>
              <span className="eyebrow">TOGETHER IN GAME</span>
              <strong>
                {state.sessions.reduce((n, a) => n + a.players.length, 0)}
              </strong>
              <small>
                Participants on {servers[server as keyof typeof servers]}
              </small>
            </div>
            <div>
              <span className="eyebrow">SAFE RETURNS</span>
              <strong>Inventory snapshots</strong>
              <small>
                Original items, stats and position restored on leave
              </small>
            </div>
          </div>
          <div className="arena-layout">
            <div className="arena-list">
              <span className="eyebrow">CHOOSE A SESSION</span>
              {state.sessions.length === 0 && (
                <p>
                  No Werewolf sessions yet. Create one to choose its cast,
                  timers and rule variants.
                </p>
              )}
              {state.sessions.map((a) => (
                <button
                  className={`arena-card ${selected === a.config.id ? "arena-selected" : ""}`}
                  key={a.config.id}
                  disabled={busy}
                  aria-pressed={selected === a.config.id}
                  onClick={() => engine.setSelected(a.config.id)}
                >
                  <span className="eyebrow">☾ WEREWOLF</span>
                  <strong>{a.config.name}</strong>
                  <small>{titles[a.phase] || a.phase}</small>
                  <span>
                    {a.players.length} / {a.config.maxPlayers} players
                  </span>
                </button>
              ))}
            </div>
            <div className="arena-detail">
              {current ? (
                <>
                  <div className="management-heading">
                    <div>
                      <span className="eyebrow">
                        {current.config.id} ·{" "}
                        {servers[server as keyof typeof servers]}
                      </span>
                      <h2>
                        {current.config.name}
                        <span>.</span>
                      </h2>
                    </div>
                    <span
                      className={`status ${current.phase !== "WAITING" ? "on" : ""}`}
                    >
                      {titles[current.phase] || current.phase}
                    </span>
                  </div>
                  <div className="arena-facts">
                    <span>Werewolf</span>
                    <span>
                      {current.config.durationSeconds / 60} min maximum
                    </span>
                    <span>{current.config.minPlayers} players to start</span>
                    {current.phaseEndsAt > 0 && current.phase !== "WAITING" && (
                      <span>
                        Phase ends in{" "}
                        {Math.max(
                          0,
                          Math.ceil((current.phaseEndsAt - Date.now()) / 1000),
                        )}
                        s
                      </span>
                    )}
                  </div>
                  <div className="arena-buttons">
                    <button
                      className="primary"
                      disabled={
                        busy ||
                        current.phase !== "WAITING" ||
                        current.players.length < current.config.minPlayers
                      }
                      onClick={() =>
                        action(
                          ww("start"),
                          {},
                          `Start ${current.config.name} with ${current.players.length} participants?`,
                        )
                      }
                    >
                      Start game →
                    </button>
                    <button
                      disabled={
                        busy ||
                        (current.players.length === 0 &&
                          current.phase === "WAITING")
                      }
                      onClick={() =>
                        action(
                          ww("stop"),
                          {},
                          `Stop ${current.config.name} and restore every participant's original state?`,
                        )
                      }
                    >
                      Stop & restore
                    </button>
                    <button
                      disabled={
                        busy ||
                        current.players.length > 0 ||
                        current.phase !== "WAITING"
                      }
                      onClick={() => edit(current.config)}
                    >
                      Edit settings
                    </button>
                    <button
                      disabled={
                        busy ||
                        current.players.length > 0 ||
                        current.phase !== "WAITING"
                      }
                      onClick={() =>
                        action(
                          "arena-delete",
                          {},
                          `Delete the configuration for ${current.config.name}?`,
                        )
                      }
                    >
                      Delete session
                    </button>
                  </div>
                  <p className="console-note">
                    Each game runs on its own flying platform around a campfire,
                    built when it starts and removed when it ends; players wait
                    in this zone until then. Saving settings never touches it.
                  </p>
                  {current.phase === "WAITING" && (
                    <section
                      className="arena-invite"
                      aria-label="Solo practice"
                    >
                      <span className="eyebrow">SOLO PRACTICE</span>
                      <h3>Try the village with a virtual cast.</h3>
                      <p>
                        Practice players receive secret roles and act
                        automatically at the end of each phase. Phases last at
                        most 15 seconds. They are virtual participants, with no
                        Minecraft accounts, NPCs or inventory.
                      </p>
                      {practiceHumans === 0 && (
                        <label>
                          Player to join
                          <select
                            value={practiceTarget}
                            onChange={(e) => setPracticePlayer(e.target.value)}
                            disabled={busy}
                          >
                            <option value="">Choose a connected player…</option>
                            {practiceCandidates.map((p) => (
                              <option key={p.uuid} value={p.uuid}>
                                {p.name}
                              </option>
                            ))}
                          </select>
                        </label>
                      )}
                      <label>
                        Practice players
                        <input
                          type="number"
                          min={Math.max(
                            1,
                            current.config.minPlayers -
                              Math.max(1, practiceHumans),
                          )}
                          max={
                            current.config.maxPlayers -
                            Math.max(1, practiceHumans)
                          }
                          value={practiceCount}
                          disabled={busy}
                          onChange={(e) =>
                            setPracticeBots(Math.max(1, Number(e.target.value)))
                          }
                        />
                      </label>
                      <button
                        className="primary"
                        disabled={
                          busy ||
                          practiceCount < 1 ||
                          (practiceHumans === 0 && !practiceTarget)
                        }
                        onClick={() =>
                          action(
                            ww("practice-start"),
                            {
                              botCount: practiceCount,
                              ...(practiceHumans === 0
                                ? { uuid: practiceTarget }
                                : {}),
                            },
                            `Start practice with ${Math.max(1, practiceHumans)} real player(s) and ${practiceCount} virtual players? Real inventories and positions will be saved and restored normally.`,
                          )
                        }
                      >
                        Start practice →
                      </button>
                    </section>
                  )}
                  {current.practice && (
                    <p className="console-note">
                      Practice game · virtual players act automatically. Stop
                      &amp; restore removes the entire virtual cast.
                    </p>
                  )}
                  <Participants
                    arena={current}
                    state={state}
                    server={server}
                    busy={busy}
                    action={action}
                    joinHint="/arena join"
                  />
                  <Invitation
                    key={current.config.id}
                    arena={current}
                    state={state}
                    busy={busy}
                    action={action}
                    text="A moonlit village is waiting. Join the next game!"
                  />
                </>
              ) : (
                <div className="arena-empty">
                  <span aria-hidden="true">☾</span>
                  <h2>Your next story starts here.</h2>
                  <p>
                    Create a Werewolf session, pick its cast and rule variants,
                    then gather your players.
                  </p>
                </div>
              )}
            </div>
          </div>
          {draft && (
            <form ref={editor} className="arena-editor" onSubmit={save}>
              <div className="management-heading">
                <div>
                  <span className="eyebrow">WEREWOLF WORKSHOP</span>
                  <h2>
                    {editing ? "Refine your session" : "Set the table"}
                    <span>.</span>
                  </h2>
                </div>
                <button type="button" disabled={busy} onClick={closeDraft}>
                  Close editor ×
                </button>
              </div>
              <fieldset disabled={busy}>
                <div className="arena-form-grid">
                  <label>
                    Session ID
                    <input
                      value={draft.id}
                      required
                      readOnly={editing}
                      pattern="[a-z0-9][a-z0-9_-]{0,39}"
                      maxLength={40}
                      placeholder="moonlit-village"
                      onChange={(e) =>
                        setDraft({ ...draft, id: e.target.value })
                      }
                    />
                  </label>
                  <label>
                    Display name
                    <input
                      required
                      maxLength={80}
                      value={draft.name}
                      placeholder="Moonlit village"
                      onChange={(e) =>
                        setDraft({ ...draft, name: e.target.value })
                      }
                    />
                  </label>
                  {!state.split && !editing && (
                    <label>
                      Existing world
                      <select
                        value={draft.worldName}
                        onChange={(e) => {
                          const w = state.worlds.find(
                            (w) => w.name === e.target.value,
                          )!;
                          setDraft({
                            ...draft,
                            worldName: w.name,
                            bounds: defaultBounds(w),
                            spawn: { ...w.spawn, yaw: 0, pitch: 0 },
                          });
                        }}
                      >
                        {state.worlds.map((w) => (
                          <option key={w.name}>{w.name}</option>
                        ))}
                      </select>
                    </label>
                  )}
                </div>
                <WerewolfRulesEditor
                  config={draft}
                  onPatch={(patch) => setDraft({ ...draft, ...patch })}
                />
                <h3>Werewolf timers</h3>
                <p className="console-note">
                  Each round is a day then a night. Day: players talk, then vote
                  to eliminate someone. Night: roles wake one after another
                  (Guard, Seer, Werewolves, Witch). The wolves' vote ends early
                  once every wolf has voted. Eliminated players become
                  spectators and their role is revealed.
                </p>
                <div className="arena-form-grid">
                  {field(
                    "discussionSeconds",
                    "Day · discussion time (seconds)",
                    5,
                    600,
                  )}
                  {field(
                    "voteSeconds",
                    "Day · village vote time (seconds)",
                    5,
                    300,
                  )}
                  {field(
                    "nightSeconds",
                    "Night · time per role: Guard, Seer, Werewolf vote (seconds)",
                    5,
                    300,
                  )}
                </div>
                <h3>Table &amp; rewards</h3>
                <div className="arena-form-grid">
                  {field("minPlayers", "Minimum players", 4, 45)}
                  {field("maxPlayers", "Maximum players", 1, 45)}
                  {field(
                    "durationSeconds",
                    "Whole game time limit (seconds)",
                    60,
                    14400,
                  )}
                  {(
                    [
                      ["participation", "Participation reward (Shards)"],
                      ["victory", "Victory reward (Shards)"],
                    ] as const
                  ).map(([key, label]) => (
                    <label key={key}>
                      {label}
                      <input
                        type="number"
                        min={0}
                        value={draft.rewards?.[key] ?? 0}
                        onChange={(e) =>
                          setDraft({
                            ...draft,
                            rewards: {
                              participation: 0,
                              victory: 0,
                              ...draft.rewards,
                              [key]: Number(e.target.value),
                            },
                          })
                        }
                      />
                    </label>
                  ))}
                </div>
                <div className="arena-flags">
                  <label>
                    <input type="checkbox" checked={false} disabled readOnly />
                    Allow player combat (always off during Werewolf)
                  </label>
                </div>
                <button className="primary" type="submit">
                  Save settings →
                </button>
              </fieldset>
            </form>
          )}
          <GameLogs server={server} />
        </>
      )}
    </section>
  );
}
