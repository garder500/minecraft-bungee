import { useEffect, useRef, useState, type FormEvent } from "react";
import { api } from "./api";
import {
  defaultBounds,
  HostToolbar,
  Invitation,
  materials,
  normalizeCorners,
  Notices,
  NumberField,
  Participants,
  servers,
  useEngine,
  type Config,
  type World,
} from "./arenaShared";
import { useConfirm } from "./ConfirmDialog";
import { defaultFreeplay, FreeplayRulesEditor } from "./FreeplayRulesEditor";

export type { Config } from "./arenaShared";

const titles: Record<string, string> = {
  WAITING: "Waiting for players",
  FREEPLAY: "Free play",
  FINISHED: "Game finished",
  RESTORING: "Restoring players",
};
const presetNames: Record<string, string> = {
  zone: "ZONE",
  freeplay: "FREE PLAY",
};
function fresh(world: World): Config {
  return {
    id: "",
    name: "",
    worldName: world.name,
    preset: "zone",
    bounds: defaultBounds(world),
    spawn: { ...world.spawn, yaw: 0, pitch: 0 },
    allowedBreakBlocks: [],
    allowedPlaceBlocks: [],
    allowPvP: false,
    allowChests: false,
    allowDoors: true,
    gameMode: "ADVENTURE",
    durationSeconds: 1800,
    minPlayers: 1,
    maxPlayers: 20,
    discussionSeconds: 60,
    voteSeconds: 30,
    nightSeconds: 30,
    freeplay: structuredClone(defaultFreeplay),
  };
}
/** Generic zones: where a game happens (world, bounds, spawn, players, rewards), never what the game is. */
export function Zones({
  csrf,
  onError,
}: {
  csrf: string;
  onError: (error: unknown) => void;
}) {
  const confirm = useConfirm();
  const [draft, setDraft] = useState<Config | null>(null),
    [editing, setEditing] = useState(false),
    [breaks, setBreaks] = useState(""),
    [places, setPlaces] = useState("");
  const [target, setTarget] = useState(""),
    [reason, setReason] = useState("Managed by network administration");
  const [playerSearch, setPlayerSearch] = useState("");
  const [knownPlayers, setKnownPlayers] = useState<
    { uuid: string; name: string }[]
  >([]);
  const engine = useEngine(
    csrf,
    onError,
    (s) => s.zones,
    () => {
      setDraft(null);
      setTarget("");
    },
  );
  const { server, state, selected, busy, action } = engine;
  useEffect(() => {
    let live = true;
    const timer = setTimeout(() => {
      api<{ players: { uuid: string; name: string }[] }>(
        `/players?search=${encodeURIComponent(playerSearch)}`,
      )
        .then((data) => {
          if (live) setKnownPlayers(data.players);
        })
        .catch(() => {});
    }, 250);
    return () => {
      live = false;
      clearTimeout(timer);
    };
  }, [playerSearch]);
  const editor = useRef<HTMLFormElement>(null);
  useEffect(() => {
    if (draft)
      editor.current?.scrollIntoView({ behavior: "smooth", block: "start" });
  }, [draft !== null]);
  const current = state?.zones.find((a) => a.config.id === selected);
  async function closeDraft() {
    if (
      draft &&
      !(await confirm("Discard the unsaved zone configuration?", {
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
    const next = config ? structuredClone(config) : fresh(state.worlds[0]);
    setDraft(next);
    setEditing(!!config);
    setBreaks(next.allowedBreakBlocks.join(", "));
    setPlaces(next.allowedPlaceBlocks.join(", "));
  }
  async function save(e: FormEvent) {
    e.preventDefault();
    if (!draft) return;
    const normalized = normalizeCorners(draft);
    setDraft(normalized);
    if (!editing && state?.all.some((a) => a.config.id === draft.id)) {
      engine.setError(
        "This ID already exists. Edit the existing zone or choose a different ID.",
      );
      return;
    }
    if (
      !(await confirm(
        "Save these zone bounds and rules? Check that the spawn has safe ground and the zone is ready for players.",
        { title: "Save zone configuration?", confirmLabel: "Save zone" },
      ))
    )
      return;
    engine.setBusy(true);
    try {
      // Unknown fields (places, options, ...) travel with the draft untouched.
      const config: Config = {
        ...normalized,
        allowedBreakBlocks: materials(breaks),
        allowedPlaceBlocks: materials(places),
      };
      if (config.preset === "freeplay" && config.freeplay)
        config.options = { ...config.options, freeplay: config.freeplay };
      await api(`/servers/${server}/game/action`, "POST", csrf, {
        action: "arena-save",
        config,
      });
      engine.setSelected(draft.id);
      setDraft(null);
      engine.setNotice(
        "Zone saved. Players can now join; the game starts when you choose Start.",
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
        create={{ label: "+ Create zone", onClick: () => edit() }}
      />
      <Notices error={engine.error} notice={engine.notice} />
      {!state && !engine.error && <p>Connecting to the arena engine…</p>}
      {state && (
        <>
          <div className="arena-summary">
            <div>
              <span className="eyebrow">YOUR PLAYGROUNDS</span>
              <strong>{state.zones.length}</strong>
              <small>Configured zones</small>
            </div>
            <div>
              <span className="eyebrow">TOGETHER IN GAME</span>
              <strong>
                {state.zones.reduce((n, a) => n + a.players.length, 0)}
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
              <span className="eyebrow">CHOOSE A ZONE</span>
              {state.zones.length === 0 && (
                <p>
                  No zones yet. Choose an existing world and define your first
                  play area.
                </p>
              )}
              {state.zones.map((a) => (
                <button
                  className={`arena-card ${selected === a.config.id ? "arena-selected" : ""}`}
                  key={a.config.id}
                  disabled={busy}
                  aria-pressed={selected === a.config.id}
                  onClick={() => engine.setSelected(a.config.id)}
                >
                  <span className="eyebrow">
                    ✦ {presetNames[a.config.preset] || a.config.preset.toUpperCase()}
                  </span>
                  <strong>{a.config.name}</strong>
                  <small>{titles[a.phase] || a.phase}</small>
                  <span>
                    {a.players.length} / {a.config.maxPlayers} players{" "}
                    <small>· {a.config.worldName}</small>
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
                    <span>
                      {current.config.preset === "freeplay"
                        ? "Free play"
                        : "Zone"}{" "}
                      · {current.config.gameMode.toLowerCase()}
                    </span>
                    <span>
                      {current.config.durationSeconds / 60} min maximum
                    </span>
                    <span>{current.config.minPlayers} players to start</span>
                    {current.phaseEndsAt > 0 && current.phase !== "WAITING" && (
                      <span>
                        Ends in{" "}
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
                          "arena-start",
                          {},
                          `Start ${current.config.name} with ${current.players.length} participants?`,
                        )
                      }
                    >
                      Start →
                    </button>
                    <button
                      disabled={
                        busy ||
                        (current.players.length === 0 &&
                          current.phase === "WAITING")
                      }
                      onClick={() =>
                        action(
                          "arena-stop",
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
                      Edit zone
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
                          `Delete the configuration for ${current.config.name}? The world itself is kept.`,
                        )
                      }
                    >
                      Delete zone
                    </button>
                  </div>
                  <p className="console-note">
                    Borders and gameplay rules apply to participants only. Build
                    the zone in the existing world before inviting players.
                    Allowed block changes are real and stay in the world.
                  </p>
                  <div className="arena-zone">
                    <strong>Play area · {current.config.worldName}</strong>
                    <span>
                      X {current.config.bounds.minX} →{" "}
                      {current.config.bounds.maxX} · Y{" "}
                      {current.config.bounds.minY} →{" "}
                      {current.config.bounds.maxY} · Z{" "}
                      {current.config.bounds.minZ} →{" "}
                      {current.config.bounds.maxZ}
                    </span>
                    <small>
                      Spawn {current.config.spawn.x} / {current.config.spawn.y}{" "}
                      / {current.config.spawn.z}
                    </small>
                  </div>
                  <Participants
                    arena={current}
                    state={state}
                    server={server}
                    busy={busy}
                    action={action}
                    showScore={current.config.preset === "freeplay"}
                    joinHint="/arena join"
                  />
                  <Invitation
                    key={current.config.id}
                    arena={current}
                    state={state}
                    busy={busy}
                    action={action}
                    text="A new play area is open. Join the next game!"
                  />
                </>
              ) : (
                <div className="arena-empty">
                  <span aria-hidden="true">✦</span>
                  <h2>Your next playground starts here.</h2>
                  <p>
                    Define a play area within your existing Minecraft world,
                    then give it a game.
                  </p>
                  <p>Your world's global border stays unchanged.</p>
                </div>
              )}
            </div>
          </div>
          {draft && (
            <form ref={editor} className="arena-editor" onSubmit={save}>
              <div className="management-heading">
                <div>
                  <span className="eyebrow">ZONE WORKSHOP</span>
                  <h2>
                    {editing ? "Refine your zone" : "Create something to play"}
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
                    Zone ID
                    <input
                      value={draft.id}
                      required
                      readOnly={editing}
                      pattern="[a-z0-9][a-z0-9_-]{0,39}"
                      maxLength={40}
                      placeholder="open-field"
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
                      placeholder="Open field"
                      onChange={(e) =>
                        setDraft({ ...draft, name: e.target.value })
                      }
                    />
                  </label>
                  <label>
                    Zone preset
                    <select
                      value={draft.preset}
                      onChange={(e) =>
                        setDraft({
                          ...draft,
                          preset: e.target.value,
                          allowPvP: false,
                          freeplay: draft.freeplay || structuredClone(defaultFreeplay),
                        })
                      }
                    >
                      <option value="zone">Zone · Protected region only</option>
                      <option value="freeplay">
                        Free play · Scoring, kits and checkpoints
                      </option>
                    </select>
                  </label>
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
                </div>
                <h3>Exact play area</h3>
                <p className="console-note">
                  Enter either corner first — coordinates are sorted
                  automatically when saved. The spawn must be inside, with safe
                  ground and enough headroom. No blocks are generated.
                </p>
                <div className="arena-coordinate-grid">
                  {(
                    ["minX", "minY", "minZ", "maxX", "maxY", "maxZ"] as const
                  ).map((key) => (
                    <label key={key}>
                      {key.replace(/^(min|max)/, "$1 ")}
                      <input
                        type="number"
                        step="any"
                        required
                        value={draft.bounds[key]}
                        onChange={(e) =>
                          setDraft({
                            ...draft,
                            bounds: {
                              ...draft.bounds,
                              [key]: Number(e.target.value),
                            },
                          })
                        }
                      />
                    </label>
                  ))}
                </div>
                <h3>Arrival position</h3>
                <div className="arena-coordinate-grid">
                  {(["x", "y", "z", "yaw", "pitch"] as const).map((key) => (
                    <label key={key}>
                      Spawn {key}
                      <input
                        type="number"
                        step="any"
                        required
                        value={draft.spawn[key]}
                        onChange={(e) =>
                          setDraft({
                            ...draft,
                            spawn: {
                              ...draft.spawn,
                              [key]: Number(e.target.value),
                            },
                          })
                        }
                      />
                    </label>
                  ))}
                </div>
                {draft.preset === "freeplay" && (
                  <FreeplayRulesEditor
                    config={draft}
                    onPatch={(patch) => {
                      setDraft({ ...draft, ...patch });
                      if (patch.allowedBreakBlocks)
                        setBreaks(patch.allowedBreakBlocks.join(", "));
                      if (patch.allowedPlaceBlocks)
                        setPlaces(patch.allowedPlaceBlocks.join(", "));
                    }}
                  />
                )}
                <h3>Players &amp; rewards</h3>
                <div className="arena-form-grid">
                  <label>
                    Player game mode
                    <select
                      value={draft.gameMode}
                      onChange={(e) =>
                        setDraft({ ...draft, gameMode: e.target.value })
                      }
                    >
                      {["ADVENTURE", "SURVIVAL", "CREATIVE"].map((mode) => (
                        <option key={mode}>{mode}</option>
                      ))}
                    </select>
                  </label>
                  {field("minPlayers", "Minimum players", 1, 45)}
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
                  {(
                    [
                      ["allowPvP", "Allow player combat"],
                      ["allowChests", "Allow containers"],
                      ["allowDoors", "Allow doors & trapdoors"],
                    ] as const
                  ).map(([key, label]) => (
                    <label key={key}>
                      <input
                        type="checkbox"
                        checked={draft[key]}
                        onChange={(e) =>
                          setDraft({ ...draft, [key]: e.target.checked })
                        }
                      />
                      {label}
                    </label>
                  ))}
                </div>
                <div className="arena-form-grid">
                  <label>
                    Blocks players may break
                    <input
                      value={breaks}
                      maxLength={3000}
                      placeholder="Empty = no breaking"
                      onChange={(e) => setBreaks(e.target.value)}
                    />
                  </label>
                  <label>
                    Blocks players may place
                    <input
                      value={places}
                      maxLength={3000}
                      placeholder="Empty = no placing"
                      onChange={(e) => setPlaces(e.target.value)}
                    />
                  </label>
                </div>
                <p className="console-note">
                  Comma-separated Minecraft material names, e.g. STONE,
                  OAK_PLANKS. Existing claim protections still apply. These
                  changes are not rolled back.
                </p>
                <button className="primary" type="submit">
                  Save zone →
                </button>
              </fieldset>
            </form>
          )}
          <section className="arena-moderation">
            <div>
              <span className="eyebrow">ACROSS ALL YOUR WORLDS</span>
              <h2>
                Network moderation<span>.</span>
              </h2>
              <p className="console-note">
                Network bans persist in Redis and are checked on every backend.
                A kick disconnects the player wherever they are.
              </p>
            </div>
            <div className="arena-form-grid">
              <label>
                Find a player
                <input
                  type="search"
                  placeholder="Search by name…"
                  value={playerSearch}
                  onChange={(e) => setPlayerSearch(e.target.value)}
                />
              </label>
              <label>
                Player
                <select
                  value={target}
                  onChange={(e) => setTarget(e.target.value)}
                >
                  <option value="">Choose a player…</option>
                  {target && !knownPlayers.some((p) => p.uuid === target) && (
                    <option value={target}>
                      Selected player · {target.slice(0, 8)}
                    </option>
                  )}
                  {knownPlayers.map((p) => (
                    <option key={p.uuid} value={p.uuid}>
                      {p.name}
                    </option>
                  ))}
                </select>
              </label>
              <label>
                Reason
                <input
                  maxLength={300}
                  value={reason}
                  onChange={(e) => setReason(e.target.value)}
                />
              </label>
            </div>
            <div className="arena-buttons">
              {(
                [
                  ["arena-kick", "Kick from network"],
                  ["arena-ban", "Ban from network"],
                ] as const
              ).map(([actionName, label]) => (
                <button
                  key={actionName}
                  disabled={
                    busy ||
                    !state.redisConnected ||
                    !reason.trim() ||
                    !/^[a-f0-9]{8}(-[a-f0-9]{4}){3}-[a-f0-9]{12}$/.test(target)
                  }
                  onClick={() =>
                    action(
                      actionName,
                      { uuid: target, reason },
                      `${label} for ${knownPlayers.find((p) => p.uuid === target)?.name || target}? This applies across all three servers.`,
                    )
                  }
                >
                  {label}
                </button>
              ))}
            </div>
            {Object.entries(state.bans).map(([uuid, reason]) => (
              <div className="arena-ban" key={uuid}>
                <code>{uuid}</code>
                <span>{reason}</span>
                <button
                  disabled={busy || !state.redisConnected}
                  onClick={() =>
                    action(
                      "arena-unban",
                      { uuid, reason: "Network ban removed by administrator" },
                      `Remove the network ban for ${uuid}? Backend-specific bans are kept.`,
                    )
                  }
                >
                  Remove network ban
                </button>
              </div>
            ))}
          </section>
        </>
      )}
    </section>
  );
}
