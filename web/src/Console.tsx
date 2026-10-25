import {
  FormEvent,
  KeyboardEvent,
  useEffect,
  useMemo,
  useRef,
  useState,
} from "react";
import { api, APIError } from "./api";
import {
  Filter,
  Level,
  LogLine,
  invalidPattern,
  parseLines,
  pattern,
  split,
  visible,
} from "./consoleLog";

type Entry = {
  id: number;
  command: string;
  output: string;
  at: string;
  failed?: boolean;
};
type Search = {
  lines: string[];
  matched: number;
  scanned: number;
  truncated: boolean;
};

const LEVELS: Level[] = ["INFO", "WARN", "ERROR"];
const KEEP = 1000;

function loadHistory(server: string): string[] {
  try {
    const raw = JSON.parse(
      localStorage.getItem(`console-history:${server}`) || "[]",
    );
    return Array.isArray(raw)
      ? raw.filter((v) => typeof v === "string").slice(-50)
      : [];
  } catch {
    return [];
  }
}
function saveHistory(server: string, list: string[]) {
  try {
    localStorage.setItem(
      `console-history:${server}`,
      JSON.stringify(list.slice(-50)),
    );
  } catch {
    /* private mode: the history just lasts for this page */
  }
}

export function Console({
  server,
  name,
  running,
  csrf,
  onError,
  onExpired,
}: {
  server: string;
  name: string;
  running: boolean;
  csrf: string;
  onError: (error: unknown) => void;
  onExpired: () => void;
}) {
  const [raw, setRaw] = useState<string[]>([]);
  const [stream, setStream] = useState("Connecting");
  const [follow, setFollow] = useState(true);
  const [wrap, setWrap] = useState(true);
  const [filter, setFilter] = useState<Filter>({
    text: "",
    regex: false,
    matchCase: false,
    levels: { INFO: true, WARN: true, ERROR: true, DEBUG: true },
  });
  const [older, setOlder] = useState<Search | null>(null);
  const [searching, setSearching] = useState(false);
  const [command, setCommand] = useState("");
  const [sending, setSending] = useState(false);
  const [journal, setJournal] = useState<Entry[]>([]);
  const [history, setHistory] = useState<string[]>(() => loadHistory(server));
  const [cursor, setCursor] = useState(-1);
  const [copied, setCopied] = useState(false);
  const window_ = useRef<HTMLDivElement>(null);
  const commandBox = useRef<HTMLInputElement>(null);
  const refocus = useRef(false);
  const failure = useRef(onError);
  failure.current = onError;
  const expired = useRef(onExpired);
  expired.current = onExpired;

  useEffect(() => {
    setRaw([]);
    setOlder(null);
    setJournal([]);
    setHistory(loadHistory(server));
    setStream("Connecting");
    const events = new EventSource(`/api/v1/servers/${server}/console`);
    events.addEventListener("snapshot", (e) => {
      try {
        const data = JSON.parse((e as MessageEvent).data);
        if (Array.isArray(data)) {
          setRaw(data.slice(-KEEP));
          setStream("Live");
        }
      } catch {
        setStream("Invalid console response");
      }
    });
    events.addEventListener("unavailable", () =>
      setStream("Server unavailable · retrying"),
    );
    events.addEventListener("expired", () => {
      events.close();
      expired.current();
    });
    events.onerror = () => {
      setStream("Reconnecting");
      api("/session").catch((e) => {
        if (e instanceof APIError && e.status === 401) expired.current();
      });
    };
    return () => events.close();
  }, [server]);

  // A disabled input loses the focus while a command runs: give it back afterwards so ↑/↓ keep working.
  useEffect(() => {
    if (!sending && refocus.current) {
      refocus.current = false;
      commandBox.current?.focus();
    }
  }, [sending]);

  const live = useMemo(() => parseLines(raw), [raw]);
  const source: LogLine[] = useMemo(
    () => (older ? parseLines(older.lines) : live),
    [older, live],
  );
  const re = useMemo(() => pattern(filter), [filter]);
  const bad = invalidPattern(filter);
  const shown = useMemo(
    () =>
      older
        ? source.filter((l) => filter.levels[l.level])
        : visible(source, filter),
    [older, source, filter],
  );

  useEffect(() => {
    const box = window_.current;
    if (follow && !older && box) box.scrollTop = box.scrollHeight;
  }, [shown, follow, older]);

  function scrolled() {
    const box = window_.current;
    if (!box || older) return;
    const atBottom = box.scrollHeight - box.scrollTop - box.clientHeight < 40;
    if (!atBottom && follow) setFollow(false);
    else if (atBottom && !follow) setFollow(true);
  }

  async function searchOlder() {
    if (!filter.text || bad) return;
    setSearching(true);
    try {
      const wanted = LEVELS.filter((l) => filter.levels[l]);
      const q = new URLSearchParams({
        q: filter.text,
        tail: "all",
        max: "2000",
      });
      if (filter.regex) q.set("regex", "1");
      if (filter.matchCase) q.set("case", "1");
      if (wanted.length && wanted.length < 3) q.set("level", wanted.join(","));
      setOlder(await api<Search>(`/servers/${server}/logs?${q}`));
    } catch (e) {
      failure.current(e);
    } finally {
      setSearching(false);
    }
  }

  async function copy() {
    try {
      await navigator.clipboard.writeText(
        shown.map((l) => `${l.stamp} ${l.text}`).join("\n"),
      );
      setCopied(true);
      window.setTimeout(() => setCopied(false), 1500);
    } catch {
      failure.current(
        new Error("The browser refused to copy; select the lines instead."),
      );
    }
  }

  async function send(e: FormEvent) {
    e.preventDefault();
    const text = command.trim();
    if (!text) return;
    setSending(true);
    const at = new Date().toLocaleTimeString([], { hour12: false });
    const id = Date.now();
    try {
      const v = await api<{ output: string }>(
        `/servers/${server}/command`,
        "POST",
        csrf,
        { command: text },
      );
      setJournal((j) =>
        [
          { id, command: text, output: v.output || "Command sent.", at },
          ...j,
        ].slice(0, 15),
      );
      const next = [...history.filter((h) => h !== text), text];
      setHistory(next);
      saveHistory(server, next);
      setCommand("");
      setCursor(-1);
    } catch (err) {
      setJournal((j) =>
        [
          {
            id,
            command: text,
            output: err instanceof Error ? err.message : "Failed",
            at,
            failed: true,
          },
          ...j,
        ].slice(0, 15),
      );
      failure.current(err);
    } finally {
      setSending(false);
      refocus.current = true;
    }
  }

  function keys(e: KeyboardEvent<HTMLInputElement>) {
    if (e.key !== "ArrowUp" && e.key !== "ArrowDown") return;
    if (!history.length) return;
    e.preventDefault();
    const next =
      e.key === "ArrowUp"
        ? Math.min(cursor + 1, history.length - 1)
        : cursor - 1;
    setCursor(next);
    setCommand(next < 0 ? "" : history[history.length - 1 - next]);
  }

  const toggle = (level: Level) =>
    setFilter((f) => ({
      ...f,
      levels: { ...f.levels, [level]: !f.levels[level] },
    }));
  const counts = useMemo(() => {
    const c = { INFO: 0, WARN: 0, ERROR: 0, DEBUG: 0 } as Record<Level, number>;
    for (const l of source) c[l.level]++;
    return c;
  }, [source]);

  return (
    <>
      <div className="console">
        <div className="console-toolbar">
          <div>
            <span className={stream === "Live" ? "dot" : ""} />
            <span>{older ? "Search results" : stream}</span>
          </div>
          <label>
            <input
              type="checkbox"
              checked={follow && !older}
              disabled={!!older}
              onChange={(e) => setFollow(e.target.checked)}
            />{" "}
            Auto-scroll
          </label>
          <label>
            <input
              type="checkbox"
              checked={wrap}
              onChange={(e) => setWrap(e.target.checked)}
            />{" "}
            Wrap
          </label>
          <span>
            {shown.length} of {source.length} lines
          </span>
        </div>
        <div className="console-filter">
          <input
            aria-label="Filter console lines"
            placeholder="Filter lines…  (press Enter to search the older history)"
            value={filter.text}
            maxLength={200}
            className={bad ? "invalid" : ""}
            onChange={(e) => setFilter({ ...filter, text: e.target.value })}
            onKeyDown={(e) => {
              if (e.key === "Enter") {
                e.preventDefault();
                void searchOlder();
              }
            }}
          />
          <button
            type="button"
            aria-pressed={filter.matchCase}
            title="Match case"
            className={filter.matchCase ? "on" : ""}
            onClick={() =>
              setFilter({ ...filter, matchCase: !filter.matchCase })
            }
          >
            Aa
          </button>
          <button
            type="button"
            aria-pressed={filter.regex}
            title="Regular expression"
            className={filter.regex ? "on" : ""}
            onClick={() => setFilter({ ...filter, regex: !filter.regex })}
          >
            .*
          </button>
          {LEVELS.map((l) => (
            <button
              key={l}
              type="button"
              aria-pressed={filter.levels[l]}
              className={`chip level-${l} ${filter.levels[l] ? "on" : ""}`}
              onClick={() => toggle(l)}
              title={`Show ${l} lines`}
            >
              {l} <span>{counts[l]}</span>
            </button>
          ))}
          <button
            type="button"
            onClick={searchOlder}
            disabled={!filter.text || bad || searching}
            title="Search the whole kept history, not only the live window"
          >
            {searching ? "Searching…" : "Search older ↺"}
          </button>
          <button type="button" onClick={copy} disabled={!shown.length}>
            {copied ? "Copied ✓" : "Copy"}
          </button>
          <a
            className="button"
            href={`/api/v1/servers/${server}/logs/download`}
            download
          >
            Download
          </a>
        </div>
        {bad && (
          <div className="console-banner warn">
            That regular expression is not valid.
          </div>
        )}
        {older && (
          <div className="console-banner">
            {older.matched} match{older.matched === 1 ? "" : "es"} in{" "}
            {older.scanned} lines of history
            {older.truncated || older.matched > older.lines.length
              ? ` · showing the newest ${older.lines.length}`
              : ""}
            .
            <button type="button" onClick={() => setOlder(null)}>
              Back to live ↩
            </button>
          </div>
        )}
        <div
          className={`log-window ${wrap ? "" : "nowrap"}`}
          ref={window_}
          onScroll={scrolled}
          role="log"
          aria-label={`${name} console`}
          tabIndex={0}
        >
          {shown.length ? (
            shown.map((l) => (
              <div
                className={`log-line level-${l.level} ${l.header ? "" : "cont"}`}
                key={l.key}
              >
                <span className="line-time">{l.stamp}</span>
                <span className="line-thread" title={l.thread}>
                  {l.header && l.level !== "INFO" ? l.level : ""}
                </span>
                <span className="line-text">
                  {split(l.text, re).map((p, i) =>
                    p.hit ? (
                      <mark key={i}>{p.part}</mark>
                    ) : (
                      <span key={i}>{p.part}</span>
                    ),
                  )}
                </span>
              </div>
            ))
          ) : (
            <div className="empty-log">
              {source.length
                ? "No line matches the filter."
                : older
                  ? "Nothing found."
                  : "Waiting for server output…"}
            </div>
          )}
        </div>
        <form className="command-bar" onSubmit={send}>
          <span>❯</span>
          <input
            ref={commandBox}
            aria-label="Server command"
            placeholder={
              server === "velocity"
                ? "Proxy command submission is not available"
                : "Enter a Minecraft command…  (↑ ↓ for history)"
            }
            value={command}
            maxLength={2048}
            onChange={(e) => {
              setCommand(e.target.value);
              setCursor(-1);
            }}
            onKeyDown={keys}
            disabled={!running || server === "velocity" || sending}
          />
          <button
            disabled={
              !running || server === "velocity" || sending || !command.trim()
            }
          >
            Send ↵
          </button>
        </form>
      </div>
      {journal.length > 0 && (
        <div className="command-journal" aria-live="polite">
          <div className="journal-head">
            <strong>Commands</strong>
            <button type="button" onClick={() => setJournal([])}>
              Clear
            </button>
          </div>
          {journal.map((j) => (
            <div
              key={j.id}
              className={`journal-entry ${j.failed ? "failed" : ""}`}
            >
              <div>
                <span className="line-time">{j.at}</span>{" "}
                <code>❯ {j.command}</code>
                <button
                  type="button"
                  onClick={() => setCommand(j.command)}
                  title="Put it back in the command bar"
                >
                  reuse
                </button>
              </div>
              <pre>{j.output}</pre>
            </div>
          ))}
        </div>
      )}
    </>
  );
}
