import { useEffect, useRef, useState } from "react";
import { api } from "./api";
import { useConfirm } from "./ConfirmDialog";
import type { FreeplayRules } from "./FreeplayRulesEditor";
import type { WerewolfRules } from "./WerewolfRulesEditor";

/**
 * Pieces shared by the Zones view and the Werewolf view: the config / state types,
 * the polling hook, and the panels that both views show (host toolbar, participants).
 */
export type Spawn = {
  x: number;
  y: number;
  z: number;
  yaw: number;
  pitch: number;
};
export type Config = {
  id: string;
  name: string;
  worldName: string;
  /** "zone" | "freeplay" | "werewolf" (older plugins only know the last two). */
  preset: string;
  bounds: {
    minX: number;
    minY: number;
    minZ: number;
    maxX: number;
    maxY: number;
    maxZ: number;
  };
  spawn: Spawn;
  allowedBreakBlocks: string[];
  allowedPlaceBlocks: string[];
  allowPvP: boolean;
  allowChests: boolean;
  allowDoors: boolean;
  gameMode: string;
  durationSeconds: number;
  minPlayers: number;
  maxPlayers: number;
  discussionSeconds: number;
  voteSeconds: number;
  nightSeconds: number;
  village?: {
    enabled: boolean;
    houses: Record<string, Spawn>;
    meeting?: Spawn;
    daypoint?: Spawn;
    doors: { x: number; y: number; z: number }[];
  };
  /** Places set up in game with /ww place; only the plugin understands them, the panel passes them through. */
  places?: unknown;
  rewards?: { participation: number; victory: number };
  /** Opaque game-specific settings owned by the plugin; always round-tripped untouched. */
  options?: Record<string, unknown>;
  werewolf?: WerewolfRules;
  freeplay?: FreeplayRules;
};
export type Arena = {
  config: Config;
  phase: string;
  startedAt: number;
  phaseEndsAt: number;
  practice?: boolean;
  players: {
    uuid: string;
    name: string;
    alive: boolean;
    bot?: boolean;
    score?: number;
  }[];
};
export type World = {
  name: string;
  minY: number;
  maxY: number;
  spawn: { x: number; y: number; z: number };
};
type RawState = {
  arenas?: Arena[];
  zones?: Arena[];
  werewolfSessions?: Arena[];
  redisConnected: boolean;
  worlds: World[];
  bans: Record<string, string>;
  onlinePlayers: { uuid: string; name: string }[];
};
export type State = {
  /** Generic zones (every preset except Werewolf). */
  zones: Arena[];
  /** Werewolf sessions. */
  sessions: Arena[];
  /** Zones and sessions together, to find who is already in a game. */
  all: Arena[];
  /** True when the plugin reports the split lists, so the ww-* actions exist. */
  split: boolean;
  redisConnected: boolean;
  worlds: World[];
  bans: Record<string, string>;
  onlinePlayers: { uuid: string; name: string }[];
};
export const isWerewolf = (a: Arena) => a.config.preset === "werewolf";
/** Reads the new split lists, or the legacy arenas[] of an older plugin. */
export function normalizeState(raw: RawState): State {
  const legacy = raw.arenas || [];
  const sessions = raw.werewolfSessions ?? legacy.filter(isWerewolf);
  const zones = (raw.zones ?? legacy).filter((a) => !isWerewolf(a));
  return {
    zones,
    sessions,
    all: [...zones, ...sessions],
    split: !!raw.werewolfSessions,
    redisConnected: raw.redisConnected,
    worlds: raw.worlds || [],
    bans: raw.bans || {},
    onlinePlayers: raw.onlinePlayers || [],
  };
}
export const servers = {
  creative: "Creative",
  survival: "Survival",
  lobby: "Lobby",
};
export function normalizeCorners(config: Config): Config {
  const b = config.bounds;
  return {
    ...config,
    bounds: {
      minX: Math.min(b.minX, b.maxX),
      minY: Math.min(b.minY, b.maxY),
      minZ: Math.min(b.minZ, b.maxZ),
      maxX: Math.max(b.minX, b.maxX),
      maxY: Math.max(b.minY, b.maxY),
      maxZ: Math.max(b.minZ, b.maxZ),
    },
  };
}
export function defaultBounds(world: World): Config["bounds"] {
  const x = Math.floor(world.spawn.x),
    z = Math.floor(world.spawn.z);
  return {
    minX: x - 20,
    minY: world.minY,
    minZ: z - 20,
    maxX: x + 20,
    maxY: world.maxY,
    maxZ: z + 20,
  };
}
export const materials = (raw: string) =>
  raw
    .split(",")
    .map((v) =>
      v
        .trim()
        .replace(/^minecraft:/i, "")
        .toUpperCase(),
    )
    .filter(Boolean);

/** Loads and polls the game state of one host server, and runs game actions. */
export function useEngine(
  csrf: string,
  onError: (error: unknown) => void,
  pick: (state: State) => Arena[],
  onReset: () => void,
) {
  const confirm = useConfirm();
  const [server, setServer] = useState("creative"),
    [state, setState] = useState<State | null>(null),
    [selected, setSelected] = useState(""),
    [error, setError] = useState(""),
    [busy, setBusy] = useState(false),
    [notice, setNotice] = useState("");
  const generation = useRef(0);
  const picker = useRef(pick);
  picker.current = pick;
  const reset = useRef(onReset);
  reset.current = onReset;
  async function load() {
    const version = generation.current;
    try {
      const data = normalizeState(
        await api<RawState>(`/servers/${server}/game/arenas`),
      );
      if (version !== generation.current) return;
      setState(data);
      setError("");
      const list = picker.current(data);
      setSelected((id) =>
        list.some((a) => a.config.id === id) ? id : list[0]?.config.id || "",
      );
    } catch (e) {
      if (version !== generation.current) return;
      setError(e instanceof Error ? e.message : "Arena engine unavailable");
    }
  }
  useEffect(() => {
    generation.current++;
    setState(null);
    setSelected("");
    setNotice("");
    reset.current();
    void load();
    const timer = setInterval(load, 3000);
    return () => {
      generation.current++;
      clearInterval(timer);
    };
  }, [server]);
  async function action(
    name: string,
    extra: Record<string, unknown> = {},
    question?: string,
    done = "Operation completed.",
  ) {
    if (question && !(await confirm(question))) return;
    setBusy(true);
    setNotice("");
    try {
      await api(`/servers/${server}/game/action`, "POST", csrf, {
        action: name,
        arenaId: selected,
        ...extra,
      });
      setNotice(done);
      await load();
    } catch (e) {
      onError(e);
    } finally {
      setBusy(false);
    }
  }
  return {
    server,
    setServer,
    state,
    selected,
    setSelected,
    error,
    setError,
    busy,
    setBusy,
    notice,
    setNotice,
    action,
    load,
  };
}
export type Engine = ReturnType<typeof useEngine>;
type Act = (
  name: string,
  extra?: Record<string, unknown>,
  question?: string,
) => Promise<void>;

export function NumberField<T extends object>({
  draft,
  setDraft,
  field,
  label,
  min,
  max,
}: {
  draft: T;
  setDraft: (next: T) => void;
  field: keyof T;
  label: string;
  min: number;
  max: number;
}) {
  return (
    <label>
      {label}
      <input
        type="number"
        min={min}
        max={max}
        required
        value={Number(draft[field])}
        onChange={(e) => setDraft({ ...draft, [field]: Number(e.target.value) })}
      />
    </label>
  );
}

export function HostToolbar({
  engine,
  onSwitch,
  create,
}: {
  engine: Engine;
  onSwitch: (next: string) => Promise<void>;
  create: { label: string; onClick: () => void };
}) {
  const { server, busy, state, load } = engine;
  return (
    <div className="arena-toolbar">
      <label>
        Host server
        <select
          value={server}
          disabled={busy}
          onChange={(e) => void onSwitch(e.target.value)}
        >
          {Object.entries(servers).map(([id, name]) => (
            <option key={id} value={id}>
              {name}
            </option>
          ))}
        </select>
      </label>
      <span className={`status ${state?.redisConnected ? "on" : ""}`}>
        {state?.redisConnected
          ? "Cross-server connected"
          : "Cross-server unavailable"}
      </span>
      <button onClick={load} disabled={busy}>
        Refresh
      </button>
      <button
        className="primary"
        disabled={busy || !state}
        onClick={create.onClick}
      >
        {create.label}
      </button>
    </div>
  );
}

export function Notices({ error, notice }: { error: string; notice: string }) {
  return (
    <>
      {error && (
        <p className="error" role="alert">
          {error}
        </p>
      )}
      {notice && (
        <p className="management-notice" role="status">
          {notice}
        </p>
      )}
    </>
  );
}

/** Players of the selected game, plus the picker to move a connected player in. */
export function Participants({
  arena,
  state,
  server,
  busy,
  action,
  showScore,
  joinHint,
}: {
  arena: Arena;
  state: State;
  server: string;
  busy: boolean;
  action: Act;
  showScore?: boolean;
  joinHint: string;
}) {
  return (
    <>
      <h3>
        Participants <small>· {arena.players.length}</small>
      </h3>
      {arena.players.length === 0 ? (
        <p className="console-note">
          Players can use{" "}
          <code>
            {joinHint} {server}:{arena.config.id}
          </code>{" "}
          or click an invitation.
        </p>
      ) : (
        <div className="arena-participants">
          {arena.players.map((p) => (
            <div key={p.uuid}>
              <strong>
                {p.name}
                {p.bot ? " · Practice" : ""}
              </strong>
              {showScore && <span>{p.score || 0} pts</span>}
              <span className={`status ${p.alive ? "on" : ""}`}>
                {p.alive ? "In play" : "Eliminated"}
              </span>
              <button
                disabled={busy || p.bot}
                onClick={() =>
                  action(
                    "arena-leave",
                    { uuid: p.uuid },
                    `Return ${p.name} to their original inventory and position?`,
                  )
                }
              >
                Return player
              </button>
            </div>
          ))}
        </div>
      )}
      {arena.phase === "WAITING" && (
        <label className="arena-player-add">
          Add a connected player
          <select
            value=""
            disabled={busy}
            onChange={(e) => {
              const uuid = e.target.value;
              void action(
                "arena-join",
                { uuid },
                `Move this player into ${arena.config.name}? Their current state will be saved first.`,
              );
            }}
          >
            <option value="">Choose a player…</option>
            {state.onlinePlayers
              .filter(
                (p) =>
                  !state.all.some((a) =>
                    a.players.some((member) => member.uuid === p.uuid),
                  ),
              )
              .map((p) => (
                <option key={p.uuid} value={p.uuid}>
                  {p.name}
                </option>
              ))}
          </select>
        </label>
      )}
    </>
  );
}

export function Invitation({
  arena,
  state,
  busy,
  action,
  text,
}: {
  arena: Arena;
  state: State;
  busy: boolean;
  action: Act;
  text: string;
}) {
  const [invite, setInvite] = useState(text);
  return (
    <div className="arena-invite">
      <span className="eyebrow">A LITTLE INVITATION</span>
      <h3>Bring the network together.</h3>
      <p>
        Send a clickable invitation to Lobby, Creative and Survival. Players on
        another server transfer automatically.
      </p>
      <label>
        Invitation message
        <input
          maxLength={300}
          value={invite}
          onChange={(e) => setInvite(e.target.value)}
        />
      </label>
      <button
        disabled={
          busy ||
          !state.redisConnected ||
          arena.phase !== "WAITING" ||
          !invite.trim()
        }
        onClick={() =>
          action(
            "arena-announce",
            { message: invite },
            `Send this invitation to every player on the network?`,
          )
        }
      >
        Send network invitation ↗
      </button>
    </div>
  );
}
