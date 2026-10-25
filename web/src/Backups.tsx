import { ChangeEvent, useCallback, useEffect, useRef, useState } from "react";
import { api, APIError } from "./api";
import { useConfirm } from "./ConfirmDialog";
import { bytes } from "./ServerTools";

type Backup = {
  name: string;
  server?: string;
  kind: string;
  minecraft?: string;
  bytes: number;
  createdAt: string;
  files: number;
  sha256?: string;
  problem?: string;
};
type Job = {
  id: string;
  kind: "backup" | "restore";
  server: string;
  file?: string;
  state: "running" | "done" | "failed";
  step: string;
  error?: string;
  warning?: string;
};
type Listing = { version: string; backups: Backup[]; jobs: Job[] };

const SERVERS = ["lobby", "creative", "survival"] as const;
const NAME = /^[A-Za-z0-9][A-Za-z0-9._-]{0,119}\.zip$/;
const KINDS: Record<string, string> = {
  export: "MANUAL",
  "before-restore": "BEFORE RESTORE",
  "before-import": "BEFORE IMPORT",
  uploaded: "UPLOADED",
};

function when(value: string) {
  const t = Date.parse(value);
  return Number.isFinite(t) ? new Date(t).toLocaleString() : "—";
}

export function Backups({
  csrf,
  onError,
}: {
  csrf: string;
  onError: (error: unknown) => void;
}) {
  const confirm = useConfirm();
  const [data, setData] = useState<Listing | null>(null);
  const [server, setServer] = useState<(typeof SERVERS)[number]>("creative");
  const [busy, setBusy] = useState(false);
  const [notice, setNotice] = useState("");
  const [job, setJob] = useState<Job | null>(null);
  const [progress, setProgress] = useState<number | null>(null);
  const fileInput = useRef<HTMLInputElement>(null);
  const failure = useRef(onError);
  failure.current = onError;
  const alive = useRef(true);
  useEffect(() => {
    alive.current = true;
    return () => {
      alive.current = false;
    };
  }, []);

  const load = useCallback(async () => {
    try {
      const next = await api<Listing>("/backups");
      if (!alive.current) return;
      setData(next);
      // Resume watching a job started before this page was opened.
      setJob(
        (current) =>
          current ?? next.jobs.find((j) => j.state === "running") ?? null,
      );
    } catch (e) {
      failure.current(e);
    }
  }, []);
  useEffect(() => {
    void load();
  }, [load]);

  // Poll the running job, then refresh the list once it ends.
  const jobId = job?.state === "running" ? job.id : "";
  useEffect(() => {
    if (!jobId) return;
    const timer = window.setInterval(async () => {
      try {
        const next = await api<Job>(`/jobs/${jobId}`);
        if (!alive.current) return;
        setJob(next);
        if (next.state !== "running") {
          if (next.state === "done")
            setNotice(
              next.kind === "backup"
                ? `Backup of ${next.server} saved as ${next.file}.`
                : `${next.server} restored from ${next.file}. The previous state was saved first.`,
            );
          void load();
        }
      } catch (e) {
        failure.current(e);
      }
    }, 1500);
    return () => window.clearInterval(timer);
  }, [jobId, load]);

  async function create() {
    setBusy(true);
    setNotice("");
    try {
      setJob(await api<Job>(`/servers/${server}/backups`, "POST", csrf));
    } catch (e) {
      onError(e);
    } finally {
      setBusy(false);
    }
  }

  async function restore(b: Backup) {
    if (!b.server) return;
    const ok = await confirm(
      `Replace ALL worlds, plugins data and settings files of ${b.server} with ${b.name} (${when(b.createdAt)})? ` +
        `${b.server} is stopped during the restore and restarted afterwards if it was running; connected players are disconnected. ` +
        `Everything it holds now is saved to a "before restore" backup first.`,
      { title: `Restore ${b.server}`, confirmLabel: "Restore" },
    );
    if (!ok) return;
    setBusy(true);
    setNotice("");
    try {
      setJob(
        await api<Job>(
          `/servers/${b.server}/restore/${encodeURIComponent(b.name)}`,
          "POST",
          csrf,
        ),
      );
    } catch (e) {
      onError(e);
    } finally {
      setBusy(false);
    }
  }

  async function remove(b: Backup) {
    if (
      !(await confirm(
        `Delete ${b.name} (${bytes(b.bytes)}) for good? This cannot be undone.`,
        { title: "Delete backup", confirmLabel: "Delete" },
      ))
    )
      return;
    setBusy(true);
    setNotice("");
    try {
      await api(`/backups/${encodeURIComponent(b.name)}`, "DELETE", csrf);
      setNotice(`${b.name} deleted.`);
      await load();
    } catch (e) {
      onError(e);
    } finally {
      setBusy(false);
    }
  }

  // fetch() cannot report upload progress, so a multi-gigabyte ZIP goes through XMLHttpRequest.
  function send(file: File) {
    return new Promise<void>((resolve, reject) => {
      const request = new XMLHttpRequest();
      request.open("PUT", `/api/v1/backups/${encodeURIComponent(file.name)}`);
      request.setRequestHeader("X-CSRF-Token", csrf);
      request.setRequestHeader("Content-Type", "application/zip");
      request.upload.onprogress = (event) => {
        if (event.lengthComputable && alive.current)
          setProgress(Math.round((event.loaded / event.total) * 100));
      };
      request.onerror = () =>
        reject(
          new APIError(
            "Upload interrupted. Check the connection and retry.",
            0,
          ),
        );
      request.onload = () => {
        let message = "Upload failed";
        try {
          message = JSON.parse(request.responseText).error || message;
        } catch {
          /* keep the generic message */
        }
        if (request.status >= 200 && request.status < 300) resolve();
        else reject(new APIError(message, request.status));
      };
      request.send(file);
    });
  }

  async function upload(event: ChangeEvent<HTMLInputElement>) {
    const file = event.target.files?.[0];
    if (!file) return;
    if (!NAME.test(file.name)) {
      onError(
        new APIError(
          "Rename the file first: letters, digits, dot, dash and underscore only, ending in .zip.",
          400,
        ),
      );
      event.target.value = "";
      return;
    }
    setBusy(true);
    setNotice("");
    setProgress(0);
    try {
      await send(file);
      setNotice(
        `${file.name} uploaded. Check its server and version below, then restore it.`,
      );
      await load();
    } catch (e) {
      onError(e);
    } finally {
      if (alive.current) {
        setBusy(false);
        setProgress(null);
      }
      if (fileInput.current) fileInput.current.value = "";
    }
  }

  const running = job?.state === "running";
  const locked = busy || running || progress !== null;
  return (
    <section className="management-section">
      <div className="management-heading">
        <div>
          <span className="eyebrow">WORLDS &amp; DATA</span>
          <h2>
            Backups<span>.</span>
          </h2>
        </div>
      </div>
      <div className="plugin-intro">
        <p>
          A backup holds a server&apos;s worlds, plugin data, claims and access
          lists as one ZIP. Creating one does not stop the server or disconnect
          anyone. Restoring replaces the server&apos;s data and restarts it.
        </p>
        <div className="actions">
          <label>
            Server{" "}
            <select
              value={server}
              disabled={locked}
              onChange={(e) =>
                setServer(e.target.value as (typeof SERVERS)[number])
              }
            >
              {SERVERS.map((id) => (
                <option key={id} value={id}>
                  {id[0].toUpperCase() + id.slice(1)}
                </option>
              ))}
            </select>
          </label>
          <button className="primary" disabled={locked} onClick={create}>
            Create backup
          </button>
        </div>
      </div>
      <div className="plugin-upload">
        <label>
          Upload a backup ZIP
          <input
            ref={fileInput}
            type="file"
            accept=".zip,application/zip"
            disabled={locked}
            aria-label="Backup ZIP"
            onChange={upload}
          />
        </label>
        <span>
          {progress === null ? (
            <>
              Made by this panel or <code>make zip</code>
            </>
          ) : (
            `Uploading… ${progress}%`
          )}
        </span>
        {progress !== null && (
          <progress max={100} value={progress} aria-label="Upload progress" />
        )}
      </div>
      {job && job.state !== "done" && (
        <p
          className={job.state === "failed" ? "error" : "management-notice"}
          role={job.state === "failed" ? "alert" : "status"}
        >
          {job.kind === "backup" ? "Backup" : "Restore"} of {job.server}:{" "}
          {job.state === "running"
            ? `${job.step}…`
            : `failed. ${job.error ?? ""}`}
        </p>
      )}
      {job?.warning && (
        <p className="management-notice" role="status">
          {job.warning}
        </p>
      )}
      {notice && !running && (
        <p className="management-notice" role="status">
          {notice}
        </p>
      )}
      <div className="plugin-list-heading">
        <h3>
          Saved backups <span>{data?.backups.length ?? 0}</span>
        </h3>
        <button onClick={load} disabled={busy}>
          Refresh
        </button>
      </div>
      <div className="player-table-wrap">
        <table className="player-table">
          <thead>
            <tr>
              <th>Backup</th>
              <th>Server</th>
              <th>Size</th>
              <th>Created</th>
              <th>Manage</th>
            </tr>
          </thead>
          <tbody>
            {data?.backups.map((b) => {
              const mismatch = !!b.minecraft && b.minecraft !== data.version;
              const reason = b.problem
                ? b.problem
                : !b.server
                  ? "Unknown server"
                  : !(SERVERS as readonly string[]).includes(b.server)
                    ? "Not a world backup: restore it with make import-panel"
                    : mismatch
                      ? `Made on Minecraft ${b.minecraft}; this network runs ${data.version}`
                      : "";
              return (
                <tr key={b.name}>
                  <td>
                    <strong>{b.name}</strong>
                    <small className="last-seen">
                      <span className="tag">
                        {KINDS[b.kind] ?? b.kind.toUpperCase()}
                      </span>{" "}
                      {b.files ? `${b.files} files` : ""}
                      {b.minecraft ? ` · Minecraft ${b.minecraft}` : ""}
                    </small>
                    {reason && (
                      <small className="last-seen backup-problem">
                        {reason}
                      </small>
                    )}
                  </td>
                  <td>{b.server ?? "—"}</td>
                  <td>{bytes(b.bytes)}</td>
                  <td>{when(b.createdAt)}</td>
                  <td>
                    <div className="backup-actions">
                      <a
                        className="button"
                        href={`/api/v1/backups/${encodeURIComponent(b.name)}`}
                        download={b.name}
                      >
                        Download
                      </a>
                      <button
                        disabled={locked || !!reason}
                        title={reason || `Replace ${b.server} with this backup`}
                        onClick={() => restore(b)}
                      >
                        Restore
                      </button>
                      <button disabled={locked} onClick={() => remove(b)}>
                        Delete
                      </button>
                    </div>
                  </td>
                </tr>
              );
            })}
            {data && !data.backups.length && (
              <tr>
                <td colSpan={5}>No backups yet. Create the first one above.</td>
              </tr>
            )}
            {!data && (
              <tr>
                <td colSpan={5}>Loading backups…</td>
              </tr>
            )}
          </tbody>
        </table>
      </div>
      <p className="console-note">
        Backups live in the <code>backups/</code> folder of the host, next to
        the ZIPs of <code>make zip</code>. They contain every player&apos;s data
        and plugin settings: keep them private.
      </p>
    </section>
  );
}
