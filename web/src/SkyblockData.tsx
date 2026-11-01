import { useCallback, useEffect, useRef, useState } from "react";
import { api, APIError } from "./api";
import { useConfirm } from "./ConfirmDialog";

// The Skyblock content database, edited from the panel. Nothing here knows a table by name: the domains, the
// columns and their types all come from the plugin (GET .../skyblock/data and .../{domain}/{table}).

type Root = {
  table: string;
  key: string;
  filter: string | null;
  children: { table: string; fk: string }[];
};
type Domain = { name: string; roots: Root[] };
type Column = {
  name: string;
  type: string;
  kind: "int" | "real" | "text" | "bool";
  notnull: boolean;
  pk: number;
  default: string | null;
  checks: string[];
  choices: string[] | null;
};
type Schema = {
  table: string;
  key: string;
  originColumn: string | null;
  columns: Column[];
  tableChecks: string[];
  children: { table: string; fk: string; columns: Column[] }[];
};
type Value = string | number | null;
type RowData = Record<string, Value>;
type Page = { total: number; offset: number; limit: number; rows: RowData[] };
type Stored = {
  row: RowData;
  origin: string | null;
  children: Record<string, RowData[]>;
};
type Editor = {
  isNew: boolean;
  key: string;
  origin: string | null;
  row: RowData;
  children: Record<string, RowData[]>;
};
type AuditRow = {
  id: number;
  at: number;
  actor: string;
  action: string;
  table: string;
  key: string;
  before: string | null;
  after: string | null;
};
type AuditPage = { total: number; offset: number; limit: number; rows: AuditRow[] };

const SERVERS = ["survival", "lobby", "creative"] as const;
const PAGE = 25;
const HIDDEN_IN_LIST = 6;

function base(server: string) {
  return `/servers/${server}/skyblock/data`;
}
function blank(columns: Column[], skip: string[]): RowData {
  const row: RowData = {};
  for (const c of columns) {
    if (skip.includes(c.name)) continue;
    const d = c.default === null ? null : c.default.replace(/^'(.*)'$/, "$1");
    row[c.name] =
      c.kind === "bool" || c.kind === "int" || c.kind === "real"
        ? d !== null && d !== "" && !Number.isNaN(Number(d))
          ? Number(d)
          : c.notnull
            ? 0
            : null
        : (d ?? (c.notnull ? "" : null));
  }
  return row;
}
function show(value: Value) {
  return value === null || value === undefined ? "—" : String(value);
}

function Field({
  column,
  value,
  disabled,
  invalid,
  onChange,
}: {
  column: Column;
  value: Value;
  disabled?: boolean;
  invalid?: boolean;
  onChange: (value: Value) => void;
}) {
  const cls = invalid ? "invalid" : undefined;
  if (column.kind === "bool")
    return (
      <input
        type="checkbox"
        className={cls}
        checked={Number(value) === 1}
        disabled={disabled}
        aria-label={column.name}
        onChange={(e) => onChange(e.target.checked ? 1 : 0)}
      />
    );
  if (column.choices && column.choices.length)
    return (
      <select
        className={cls}
        value={value === null ? "" : String(value)}
        disabled={disabled}
        aria-label={column.name}
        onChange={(e) => onChange(e.target.value)}
      >
        {!column.choices.includes(String(value ?? "")) && (
          <option value={String(value ?? "")}>{show(value)}</option>
        )}
        {column.choices.map((c) => (
          <option key={c} value={c}>
            {c}
          </option>
        ))}
      </select>
    );
  if (column.kind === "int" || column.kind === "real")
    return (
      <input
        type="number"
        className={cls}
        step={column.kind === "int" ? 1 : "any"}
        value={value === null ? "" : String(value)}
        disabled={disabled}
        aria-label={column.name}
        onChange={(e) =>
          onChange(e.target.value === "" ? null : Number(e.target.value))
        }
      />
    );
  const text = value === null ? "" : String(value);
  if (text.length > 70 || text.includes("\n"))
    return (
      <textarea
        className={cls}
        rows={3}
        value={text}
        disabled={disabled}
        aria-label={column.name}
        onChange={(e) => onChange(e.target.value)}
      />
    );
  return (
    <input
      type="text"
      className={cls}
      value={text}
      disabled={disabled}
      aria-label={column.name}
      onChange={(e) =>
        onChange(e.target.value === "" && !column.notnull ? null : e.target.value)
      }
    />
  );
}

export function SkyblockData({
  csrf,
  onError,
}: {
  csrf: string;
  onError: (error: unknown) => void;
}) {
  const confirm = useConfirm();
  const [server, setServer] = useState<(typeof SERVERS)[number]>("survival");
  const [tab, setTab] = useState<"data" | "audit">("data");
  const [domains, setDomains] = useState<Domain[] | null>(null);
  const [domain, setDomain] = useState("");
  const [table, setTable] = useState("");
  const [schema, setSchema] = useState<Schema | null>(null);
  const [q, setQ] = useState("");
  const [origin, setOrigin] = useState("");
  const [offset, setOffset] = useState(0);
  const [page, setPage] = useState<Page | null>(null);
  const [editor, setEditor] = useState<Editor | null>(null);
  const [problem, setProblem] = useState<{ message: string; column?: string } | null>(null);
  const [notice, setNotice] = useState("");
  const [busy, setBusy] = useState(false);
  const [unavailable, setUnavailable] = useState("");
  const ticket = useRef(0);

  const roots = domains?.find((d) => d.name === domain)?.roots ?? [];

  // domains of the selected server
  useEffect(() => {
    let live = true;
    setDomains(null);
    setUnavailable("");
    setEditor(null);
    api<{ domains: Domain[] }>(base(server))
      .then((r) => {
        if (!live) return;
        setDomains(r.domains);
        setDomain(r.domains[0]?.name ?? "");
        setTable(r.domains[0]?.roots[0]?.table ?? "");
      })
      .catch((e) => {
        if (!live) return;
        setDomains([]);
        setUnavailable(
          e instanceof APIError
            ? e.message
            : "The Skyblock data service is unavailable.",
        );
      });
    return () => {
      live = false;
    };
  }, [server]);

  // columns of the selected table
  useEffect(() => {
    if (!domain || !table) return;
    let live = true;
    setSchema(null);
    setEditor(null);
    setOffset(0);
    api<Schema>(`${base(server)}/${domain}/${table}`)
      .then((s) => live && setSchema(s))
      .catch((e) => live && onError(e));
    return () => {
      live = false;
    };
  }, [server, domain, table, onError]);

  const load = useCallback(async () => {
    if (!domain || !table || !schema || schema.table !== table) return;
    const mine = ++ticket.current;
    const params = new URLSearchParams({
      offset: String(offset),
      limit: String(PAGE),
    });
    if (q.trim()) params.set("q", q.trim());
    if (origin) params.set("origin", origin);
    try {
      const r = await api<Page>(
        `${base(server)}/${domain}/${table}/rows?${params}`,
      );
      if (mine === ticket.current) setPage(r);
    } catch (e) {
      if (mine === ticket.current) onError(e);
    }
  }, [server, domain, table, schema, q, origin, offset, onError]);
  useEffect(() => {
    if (tab === "data") void load();
  }, [load, tab]);

  const listColumns =
    schema?.columns
      .filter((c) => c.name !== schema.originColumn)
      .slice(0, HIDDEN_IN_LIST) ?? [];

  function open(stored: Stored, key: string, isNew = false) {
    setEditor({
      isNew,
      key,
      origin: stored.origin,
      row: stored.row,
      children: stored.children,
    });
    setProblem(null);
  }

  async function edit(row: RowData) {
    if (!schema) return;
    const key = String(row[schema.key]);
    try {
      const stored = await api<Stored>(
        `${base(server)}/${domain}/${table}/row?key=${encodeURIComponent(key)}`,
      );
      setNotice("");
      open(stored, key);
    } catch (e) {
      onError(e);
    }
  }

  function create() {
    if (!schema) return;
    const children: Record<string, RowData[]> = {};
    for (const ch of schema.children) children[ch.table] = [];
    setNotice("");
    open(
      {
        row: blank(schema.columns, schema.originColumn ? [schema.originColumn] : []),
        origin: null,
        children,
      },
      "",
      true,
    );
  }

  function fail(e: unknown) {
    if (e instanceof APIError && e.status >= 400 && e.status < 500) {
      const column = e.body && typeof e.body.column === "string" ? e.body.column : undefined;
      setProblem({ message: e.message, column });
    } else onError(e);
  }

  async function save() {
    if (!editor || !schema) return;
    const key = editor.isNew ? String(editor.row[schema.key] ?? "") : editor.key;
    if (!key) {
      setProblem({ message: `${schema.key} is required`, column: schema.key });
      return;
    }
    const row = { ...editor.row };
    if (schema.originColumn) delete row[schema.originColumn];
    const children: Record<string, RowData[]> = {};
    for (const ch of schema.children) {
      const hasPosition = ch.columns.some((c) => c.name === "position");
      children[ch.table] = (editor.children[ch.table] ?? []).map((r, i) => {
        const copy = { ...r };
        delete copy[ch.fk];
        if (hasPosition) copy.position = i;
        return copy;
      });
    }
    setBusy(true);
    try {
      const stored = await api<Stored>(
        `${base(server)}/${domain}/${table}/row?key=${encodeURIComponent(key)}`,
        "PUT",
        csrf,
        { row, children },
      );
      open(stored, key);
      setNotice(
        "Saved as modified. Features that follow the database re-read it now; the others on the next restart.",
      );
      await load();
    } catch (e) {
      fail(e);
    } finally {
      setBusy(false);
    }
  }

  async function remove() {
    if (!editor || editor.isNew) return;
    const ok = await confirm(
      `Delete ${table} "${editor.key}" with its sub-rows? A default row comes back when the plugin's defaults change, or at once with "Reset to default".`,
      { title: "Delete this row?", confirmLabel: "Delete" },
    );
    if (!ok) return;
    setBusy(true);
    try {
      await api(
        `${base(server)}/${domain}/${table}/row?key=${encodeURIComponent(editor.key)}`,
        "DELETE",
        csrf,
      );
      setEditor(null);
      setNotice(`Deleted ${editor.key}.`);
      await load();
    } catch (e) {
      fail(e);
    } finally {
      setBusy(false);
    }
  }

  async function reset() {
    if (!editor || editor.isNew) return;
    const ok = await confirm(
      `Discard the changes to ${table} "${editor.key}" and restore the plugin's default?`,
      { title: "Reset to default?", confirmLabel: "Reset" },
    );
    if (!ok) return;
    setBusy(true);
    try {
      const stored = await api<Stored>(
        `${base(server)}/${domain}/${table}/reset?key=${encodeURIComponent(editor.key)}`,
        "POST",
        csrf,
      );
      open(stored, editor.key);
      setNotice("Restored to the plugin's default.");
      await load();
    } catch (e) {
      fail(e);
    } finally {
      setBusy(false);
    }
  }

  const bad = (name: string) => !!problem && problem.column === name;

  return (
    <section className="management-section skyblock-data">
      <div className="management-heading">
        <div>
          <span className="eyebrow">SKYBLOCK CONTENT</span>
          <h2>
            Game data<span>.</span>
          </h2>
        </div>
        <div className="tool-tabs" role="tablist" aria-label="Game data views">
          {(["data", "audit"] as const).map((id) => (
            <button
              key={id}
              role="tab"
              aria-selected={tab === id}
              onClick={() => setTab(id)}
            >
              {id === "data" ? "Data" : "Changes"}
            </button>
          ))}
        </div>
      </div>
      <div className="plugin-intro">
        <p>
          Quests, shops, recipes and tuning numbers of the Skyblock. A row you
          edit is marked <b>modified</b> and is kept when the plugin ships new
          defaults; <b>Reset to default</b> brings the plugin&apos;s version
          back.
        </p>
        <label>
          Server{" "}
          <select
            value={server}
            onChange={(e) => setServer(e.target.value as (typeof SERVERS)[number])}
          >
            {SERVERS.map((id) => (
              <option key={id} value={id}>
                {id[0].toUpperCase() + id.slice(1)}
              </option>
            ))}
          </select>
        </label>
      </div>
      {unavailable && (
        <p className="error" role="alert">
          {unavailable}
        </p>
      )}
      {tab === "audit" ? (
        <AuditView server={server} onError={onError} />
      ) : (
        domains &&
        domains.length > 0 && (
          <>
            <div className="player-filters">
              <label>
                Data set
                <select
                  value={domain}
                  onChange={(e) => {
                    setDomain(e.target.value);
                    setTable(
                      domains.find((d) => d.name === e.target.value)?.roots[0]
                        ?.table ?? "",
                    );
                  }}
                >
                  {domains.map((d) => (
                    <option key={d.name} value={d.name}>
                      {d.name}
                    </option>
                  ))}
                </select>
              </label>
              <label>
                Table
                <select value={table} onChange={(e) => setTable(e.target.value)}>
                  {roots.map((r) => (
                    <option key={r.table} value={r.table}>
                      {r.table}
                    </option>
                  ))}
                </select>
              </label>
              <label>
                Search
                <input
                  type="search"
                  value={q}
                  placeholder="Any column"
                  onChange={(e) => {
                    setQ(e.target.value);
                    setOffset(0);
                  }}
                />
              </label>
              <label>
                Show
                <select
                  value={origin}
                  onChange={(e) => {
                    setOrigin(e.target.value);
                    setOffset(0);
                  }}
                >
                  <option value="">All rows</option>
                  <option value="panel">Modified</option>
                  <option value="seed">Default</option>
                </select>
              </label>
            </div>
            {notice && (
              <p className="management-notice" role="status">
                {notice}
              </p>
            )}
            <div className="plugin-list-heading">
              <h3>
                {table} <span>{page?.total ?? 0}</span>
              </h3>
              <button className="primary" disabled={!schema || busy} onClick={create}>
                New row
              </button>
            </div>
            <div className="player-table-wrap">
              <table className="player-table">
                <thead>
                  <tr>
                    {listColumns.map((c) => (
                      <th key={c.name}>{c.name}</th>
                    ))}
                    {schema?.originColumn && <th>State</th>}
                    <th>Edit</th>
                  </tr>
                </thead>
                <tbody>
                  {page?.rows.map((r) => {
                    const key = String(r[schema!.key]);
                    const modified = schema?.originColumn && r[schema.originColumn] === "panel";
                    return (
                      <tr key={key}>
                        {listColumns.map((c) => (
                          <td key={c.name} className="data-cell">
                            {show(r[c.name])}
                          </td>
                        ))}
                        {schema?.originColumn && (
                          <td>
                            <span className={`origin-badge ${modified ? "modified" : ""}`}>
                              {modified ? "modified" : "default"}
                            </span>
                          </td>
                        )}
                        <td>
                          <button onClick={() => void edit(r)}>Edit</button>
                        </td>
                      </tr>
                    );
                  })}
                  {page && page.rows.length === 0 && (
                    <tr>
                      <td colSpan={listColumns.length + 2} className="empty-players">
                        No rows match.
                      </td>
                    </tr>
                  )}
                </tbody>
              </table>
            </div>
            <Pager page={page} setOffset={setOffset} />
            {editor && schema && (
              <form
                className="data-editor"
                onSubmit={(e) => {
                  e.preventDefault();
                  void save();
                }}
              >
                <div className="plugin-list-heading">
                  <h3>
                    {editor.isNew ? `New ${table}` : `${table} “${editor.key}”`}{" "}
                    {schema.originColumn && !editor.isNew && (
                      <span className={`origin-badge ${editor.origin === "panel" ? "modified" : ""}`}>
                        {editor.origin === "panel" ? "modified" : "default"}
                      </span>
                    )}
                  </h3>
                  <button type="button" onClick={() => setEditor(null)}>
                    Close
                  </button>
                </div>
                {problem && (
                  <p className="error" role="alert">
                    {problem.message}
                  </p>
                )}
                <div className="data-fields">
                  {schema.columns
                    .filter((c) => c.name !== schema.originColumn)
                    .map((c) => (
                      <label key={c.name} className={c.kind === "bool" ? "data-check" : undefined}>
                        <span>
                          {c.name}
                          {c.notnull && c.default === null && c.pk === 0 ? " *" : ""}
                          <small> {c.kind}</small>
                        </span>
                        <Field
                          column={c}
                          value={editor.row[c.name] ?? null}
                          disabled={c.name === schema.key && !editor.isNew}
                          invalid={bad(c.name)}
                          onChange={(v) =>
                            setEditor({ ...editor, row: { ...editor.row, [c.name]: v } })
                          }
                        />
                        {c.checks.length > 0 && <em>{c.checks.join("; ")}</em>}
                      </label>
                    ))}
                </div>
                {schema.children.map((ch) => {
                  const cols = ch.columns.filter(
                    (c) => c.name !== ch.fk && c.name !== "position",
                  );
                  const rows = editor.children[ch.table] ?? [];
                  const setRows = (next: RowData[]) =>
                    setEditor({ ...editor, children: { ...editor.children, [ch.table]: next } });
                  return (
                    <fieldset key={ch.table} className="data-children">
                      <legend>
                        {ch.table} <span>{rows.length}</span>
                      </legend>
                      <table className="player-table">
                        <thead>
                          <tr>
                            {cols.map((c) => (
                              <th key={c.name}>{c.name}</th>
                            ))}
                            <th />
                          </tr>
                        </thead>
                        <tbody>
                          {rows.map((r, i) => (
                            <tr key={i}>
                              {cols.map((c) => (
                                <td key={c.name}>
                                  <Field
                                    column={c}
                                    value={r[c.name] ?? null}
                                    onChange={(v) =>
                                      setRows(rows.map((x, j) => (j === i ? { ...x, [c.name]: v } : x)))
                                    }
                                  />
                                </td>
                              ))}
                              <td>
                                <button
                                  type="button"
                                  aria-label={`Remove ${ch.table} row ${i + 1}`}
                                  onClick={() => setRows(rows.filter((_, j) => j !== i))}
                                >
                                  Remove
                                </button>
                              </td>
                            </tr>
                          ))}
                        </tbody>
                      </table>
                      <button
                        type="button"
                        onClick={() =>
                          setRows([...rows, blank(ch.columns, [ch.fk, "position"])])
                        }
                      >
                        Add row
                      </button>
                    </fieldset>
                  );
                })}
                {schema.tableChecks.length > 0 && (
                  <p className="data-hint">Rules: {schema.tableChecks.join("; ")}</p>
                )}
                <div className="actions">
                  <button className="primary" type="submit" disabled={busy}>
                    Save
                  </button>
                  {!editor.isNew && editor.origin === "panel" && (
                    <button type="button" disabled={busy} onClick={() => void reset()}>
                      Reset to default
                    </button>
                  )}
                  {!editor.isNew && (
                    <button type="button" className="danger" disabled={busy} onClick={() => void remove()}>
                      Delete
                    </button>
                  )}
                </div>
              </form>
            )}
          </>
        )
      )}
    </section>
  );
}

function Pager({
  page,
  setOffset,
}: {
  page: { total: number; offset: number; limit: number } | null;
  setOffset: (offset: number) => void;
}) {
  if (!page || page.total <= page.limit) return null;
  const last = page.offset + page.limit >= page.total;
  return (
    <div className="player-pagination">
      <button disabled={page.offset === 0} onClick={() => setOffset(Math.max(0, page.offset - page.limit))}>
        Previous
      </button>
      <span>
        {page.offset + 1}–{Math.min(page.total, page.offset + page.limit)} of {page.total}
      </span>
      <button disabled={last} onClick={() => setOffset(page.offset + page.limit)}>
        Next
      </button>
    </div>
  );
}

function pretty(text: string | null) {
  if (!text) return "—";
  try {
    return JSON.stringify(JSON.parse(text), null, 2);
  } catch {
    return text;
  }
}

function AuditView({
  server,
  onError,
}: {
  server: string;
  onError: (error: unknown) => void;
}) {
  const [actor, setActor] = useState("");
  const [key, setKey] = useState("");
  const [offset, setOffset] = useState(0);
  const [page, setPage] = useState<AuditPage | null>(null);
  useEffect(() => {
    let live = true;
    const params = new URLSearchParams({ offset: String(offset), limit: String(PAGE) });
    if (actor.trim()) params.set("actor", actor.trim());
    if (key.trim()) params.set("key", key.trim());
    api<AuditPage>(`${base(server)}/audit?${params}`)
      .then((r) => live && setPage(r))
      .catch((e) => live && onError(e));
    return () => {
      live = false;
    };
  }, [server, actor, key, offset, onError]);
  return (
    <>
      <div className="player-filters">
        <label>
          Row key
          <input
            type="search"
            value={key}
            onChange={(e) => {
              setKey(e.target.value);
              setOffset(0);
            }}
          />
        </label>
        <label>
          Staff
          <input
            type="search"
            value={actor}
            onChange={(e) => {
              setActor(e.target.value);
              setOffset(0);
            }}
          />
        </label>
      </div>
      <div className="player-table-wrap">
        <table className="player-table">
          <thead>
            <tr>
              <th>When</th>
              <th>Staff</th>
              <th>Change</th>
              <th>Row</th>
              <th>Before / after</th>
            </tr>
          </thead>
          <tbody>
            {page?.rows.map((r) => (
              <tr key={r.id}>
                <td>{new Date(r.at).toLocaleString()}</td>
                <td>{r.actor}</td>
                <td>{r.action}</td>
                <td className="data-cell">
                  {r.table} / {r.key}
                </td>
                <td>
                  <details>
                    <summary>Show</summary>
                    <div className="audit-diff">
                      <pre>{pretty(r.before)}</pre>
                      <pre>{pretty(r.after)}</pre>
                    </div>
                  </details>
                </td>
              </tr>
            ))}
            {page && page.rows.length === 0 && (
              <tr>
                <td colSpan={5} className="empty-players">
                  No changes recorded.
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>
      <Pager page={page} setOffset={setOffset} />
    </>
  );
}
