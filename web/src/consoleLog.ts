export type Level = "INFO" | "WARN" | "ERROR" | "DEBUG";
export type LogLine = {
  key: string;
  stamp: string; // UTC time from Docker, "17:40:06" in the viewer's clock
  thread: string;
  level: Level;
  text: string;
  header: boolean; // false for stack-trace and other continuation lines
};

const DOCKER = /^(\d{4}-\d\d-\d\dT[\d:.]+Z) /;
const PAPER =
  /^\[(\d\d:\d\d:\d\d)\] \[([^\]]*)\/(TRACE|DEBUG|INFO|WARN|ERROR|FATAL)\]: ?(.*)$/;
const VELOCITY =
  /^\[(\d\d:\d\d:\d\d) (TRACE|DEBUG|INFO|WARN|ERROR|FATAL)\]: ?(.*)$/;

function level(raw: string): Level {
  if (raw === "FATAL") return "ERROR";
  if (raw === "TRACE") return "DEBUG";
  return raw as Level;
}

function clock(iso: string): string {
  const t = Date.parse(iso);
  if (!Number.isFinite(t)) return "";
  return new Date(t).toLocaleTimeString([], { hour12: false });
}

/**
 * Turns the controller's lines ("<docker timestamp> <text>") into rows. Paper, Velocity and the image's
 * own scripts all log differently; a line without a header of its own (a stack trace) keeps the level of
 * the line above, so filtering on ERROR keeps the whole trace. Keys are stable across snapshots (the
 * Docker timestamp plus a counter for equal ones), so a sliding window does not re-render every row.
 */
export function parseLines(raw: string[]): LogLine[] {
  const seen = new Map<string, number>();
  let current: Level = "INFO";
  return raw.map((line, index) => {
    const d = DOCKER.exec(line);
    const stamp = d ? d[1] : "";
    const body = d ? line.slice(d[0].length) : line;
    const n = (seen.get(stamp) ?? 0) + 1;
    seen.set(stamp, n);
    const key = stamp ? `${stamp}#${n}` : `i${index}`;
    let m = PAPER.exec(body);
    if (m) {
      current = level(m[3]);
      return {
        key,
        stamp: m[1],
        thread: m[2],
        level: current,
        text: m[4],
        header: true,
      };
    }
    m = VELOCITY.exec(body);
    if (m) {
      current = level(m[2]);
      return {
        key,
        stamp: m[1],
        thread: "",
        level: current,
        text: m[3],
        header: true,
      };
    }
    return {
      key,
      stamp: stamp ? clock(stamp) : "",
      thread: "",
      level: current,
      text: body,
      header: false,
    };
  });
}

export type Filter = {
  text: string;
  regex: boolean;
  matchCase: boolean;
  levels: Record<Level, boolean>;
};

/** The pattern behind the search box, or null when it is empty or not a valid regular expression. */
export function pattern(
  f: Pick<Filter, "text" | "regex" | "matchCase">,
): RegExp | null {
  if (!f.text) return null;
  try {
    const source = f.regex
      ? f.text
      : f.text.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
    return new RegExp(source, f.matchCase ? "g" : "gi");
  } catch {
    return null;
  }
}

export function invalidPattern(
  f: Pick<Filter, "text" | "regex" | "matchCase">,
): boolean {
  return !!f.text && f.regex && pattern(f) === null;
}

export function visible(lines: LogLine[], f: Filter): LogLine[] {
  const re = pattern(f);
  return lines.filter((l) => {
    if (!f.levels[l.level]) return false;
    if (!re) return true;
    re.lastIndex = 0;
    return re.test(l.text);
  });
}

/** Splits text around matches so the matches can be wrapped in <mark>. */
export function split(
  text: string,
  re: RegExp | null,
): { part: string; hit: boolean }[] {
  if (!re) return [{ part: text, hit: false }];
  const out: { part: string; hit: boolean }[] = [];
  let last = 0;
  re.lastIndex = 0;
  for (let m = re.exec(text); m; m = re.exec(text)) {
    if (m[0] === "") {
      re.lastIndex++;
      continue;
    }
    if (m.index > last)
      out.push({ part: text.slice(last, m.index), hit: false });
    out.push({ part: m[0], hit: true });
    last = m.index + m[0].length;
  }
  if (last < text.length) out.push({ part: text.slice(last), hit: false });
  return out.length ? out : [{ part: text, hit: false }];
}
