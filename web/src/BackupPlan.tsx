import { useCallback, useEffect, useRef, useState } from "react";
import { api } from "./api";
import { useConfirm } from "./ConfirmDialog";

export type PlanView = {
  auto: {
    enabled: boolean;
    time: string;
    timezone: string;
    servers: string[];
    keepLocal: number;
    next?: string;
  };
  drive: {
    clientId: string;
    hasClientSecret: boolean;
    connected: boolean;
    folder: string;
    upload: boolean;
    keepDrive: number;
    problem?: string;
  };
  lastRunAt?: string;
  lastRun?: {
    server: string;
    backup?: string;
    uploaded: boolean;
    error?: string;
    at: string;
  }[];
  running: boolean;
  timezones: string[];
};

const SERVERS = ["lobby", "creative", "survival"];

function when(value?: string) {
  const t = value ? Date.parse(value) : NaN;
  return Number.isFinite(t) ? new Date(t).toLocaleString() : "—";
}

export function BackupPlan({
  csrf,
  onError,
  onView,
  notice,
}: {
  csrf: string;
  onError: (error: unknown) => void;
  onView: (view: PlanView) => void;
  notice: string;
}) {
  const confirm = useConfirm();
  const [view, setView] = useState<PlanView | null>(null);
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState("");
  const [enabled, setEnabled] = useState(false);
  const [time, setTime] = useState("04:00");
  const [zone, setZone] = useState("UTC");
  const [servers, setServers] = useState<string[]>([]);
  const [keepLocal, setKeepLocal] = useState(3);
  const [clientId, setClientId] = useState("");
  const [secret, setSecret] = useState("");
  const [folder, setFolder] = useState("");
  const [upload, setUpload] = useState(true);
  const [keepDrive, setKeepDrive] = useState(3);
  const failure = useRef(onError);
  failure.current = onError;
  const seen = useRef(onView);
  seen.current = onView;

  const fill = useCallback((next: PlanView) => {
    setView(next);
    seen.current(next);
    setEnabled(next.auto.enabled);
    setTime(next.auto.time);
    setZone(next.auto.timezone);
    setServers(next.auto.servers);
    setKeepLocal(next.auto.keepLocal);
    setClientId(next.drive.clientId);
    setSecret("");
    setFolder(next.drive.folder);
    setUpload(next.drive.upload);
    setKeepDrive(next.drive.keepDrive);
  }, []);

  useEffect(() => {
    api<PlanView>("/backup-settings").then(fill, (e) => failure.current(e));
  }, [fill]);

  // A run in progress changes the "last run" lines: look again until it ends.
  const running = view?.running ?? false;
  useEffect(() => {
    if (!running) return;
    const timer = window.setInterval(() => {
      api<PlanView>("/backup-settings").then(
        (next) => setView(next),
        () => undefined,
      );
    }, 5000);
    return () => window.clearInterval(timer);
  }, [running]);

  async function save(): Promise<PlanView | null> {
    setBusy(true);
    setMessage("");
    try {
      const next = await api<PlanView>("/backup-settings", "PUT", csrf, {
        auto: { enabled, time, timezone: zone, servers, keepLocal },
        drive: { clientId, clientSecret: secret, folder, upload, keepDrive },
      });
      fill(next);
      setMessage("Settings saved.");
      return next;
    } catch (e) {
      onError(e);
      return null;
    } finally {
      setBusy(false);
    }
  }

  async function connect() {
    const saved = await save();
    if (!saved) return;
    if (!saved.drive.clientId || !saved.drive.hasClientSecret) {
      setMessage("Enter the Google client ID and secret first.");
      return;
    }
    setBusy(true);
    try {
      const out = await api<{ url: string }>("/drive/connect", "POST", csrf);
      window.location.assign(out.url);
    } catch (e) {
      onError(e);
      setBusy(false);
    }
  }

  async function disconnect() {
    if (
      !(await confirm(
        "Disconnect Google Drive? Automatic uploads stop. Files already on Drive are kept.",
        { title: "Disconnect Drive", confirmLabel: "Disconnect" },
      ))
    )
      return;
    setBusy(true);
    try {
      fill(await api<PlanView>("/drive/disconnect", "POST", csrf));
      setMessage("Drive disconnected.");
    } catch (e) {
      onError(e);
    } finally {
      setBusy(false);
    }
  }

  async function runNow() {
    if (
      !(await confirm(
        `Back up ${servers.join(", ")} now, the same way the daily run does (upload and cleanup included)?`,
        { title: "Run the automatic backup", confirmLabel: "Run now" },
      ))
    )
      return;
    setBusy(true);
    setMessage("");
    try {
      await api("/backup-settings/run", "POST", csrf);
      setMessage("Started. Backups appear below as they finish.");
      setView((v) => (v ? { ...v, running: true } : v));
    } catch (e) {
      onError(e);
    } finally {
      setBusy(false);
    }
  }

  function toggle(id: string) {
    setServers((now) =>
      now.includes(id) ? now.filter((s) => s !== id) : [...now, id],
    );
  }

  if (!view) return null;
  const redirect = `${window.location.origin}/api/v1/drive/callback`;
  return (
    <>
      <div className="plan-grid">
        <section className="plan-card" aria-label="Automatic backups">
          <h3>Automatic backups</h3>
          <label className="plan-check">
            <input
              type="checkbox"
              checked={enabled}
              disabled={busy}
              onChange={(e) => setEnabled(e.target.checked)}
            />
            Back up every day
          </label>
          <div className="plan-row">
            <label>
              Time
              <input
                type="time"
                value={time}
                disabled={busy}
                onChange={(e) => setTime(e.target.value)}
              />
            </label>
            <label>
              Time zone
              <select
                value={zone}
                disabled={busy}
                onChange={(e) => setZone(e.target.value)}
              >
                {view.timezones.map((z) => (
                  <option key={z}>{z}</option>
                ))}
              </select>
            </label>
          </div>
          <fieldset className="plan-servers" disabled={busy}>
            <legend>Servers</legend>
            {SERVERS.map((id) => (
              <label key={id} className="plan-check">
                <input
                  type="checkbox"
                  checked={servers.includes(id)}
                  onChange={() => toggle(id)}
                />
                {id[0].toUpperCase() + id.slice(1)}
              </label>
            ))}
          </fieldset>
          <label>
            Keep on this machine (per server)
            <input
              type="number"
              min={1}
              max={60}
              value={keepLocal}
              disabled={busy}
              onChange={(e) => setKeepLocal(Number(e.target.value))}
            />
          </label>
          <p className="console-note">
            Only automatic backups are removed to respect these limits. Manual
            backups, uploads and the copies made before a restore are never
            deleted automatically.
          </p>
          <p className="console-note">
            {enabled && view.auto.next
              ? `Next run: ${when(view.auto.next)}.`
              : "Automatic backups are off."}
          </p>
          {view.lastRun && view.lastRun.length > 0 && (
            <ul className="plan-results" aria-label="Last automatic run">
              <li className="last-seen">Last run: {when(view.lastRunAt)}</li>
              {view.lastRun.map((r) => (
                <li key={r.server}>
                  <strong>{r.server}</strong>{" "}
                  {r.error ? (
                    <span className="backup-problem">{r.error}</span>
                  ) : (
                    <span>
                      {r.backup}
                      {r.uploaded ? " · on Drive" : ""}
                    </span>
                  )}
                </li>
              ))}
            </ul>
          )}
          <div className="backup-actions">
            <button className="primary" disabled={busy} onClick={save}>
              Save
            </button>
            <button
              disabled={busy || view.running || servers.length === 0}
              onClick={runNow}
            >
              {view.running ? "Running…" : "Run now"}
            </button>
          </div>
        </section>

        <section className="plan-card" aria-label="Google Drive">
          <h3>Google Drive</h3>
          <p
            className={
              view.drive.problem
                ? "backup-problem"
                : view.drive.connected
                  ? "management-notice"
                  : "console-note"
            }
            role="status"
          >
            {view.drive.problem === "reconnect"
              ? "Google refused the saved access (revoked or expired). Connect again."
              : view.drive.problem
                ? view.drive.problem
                : view.drive.connected
                  ? "Connected. Backups are stored in the folder below."
                  : "Not connected."}
          </p>
          <label>
            Google client ID
            <input
              value={clientId}
              disabled={busy}
              autoComplete="off"
              onChange={(e) => setClientId(e.target.value)}
            />
          </label>
          <label>
            Google client secret
            <input
              type="password"
              value={secret}
              disabled={busy}
              autoComplete="new-password"
              placeholder={
                view.drive.hasClientSecret
                  ? "Saved — leave empty to keep it"
                  : ""
              }
              onChange={(e) => setSecret(e.target.value)}
            />
          </label>
          <label>
            Drive folder
            <input
              value={folder}
              disabled={busy}
              maxLength={100}
              onChange={(e) => setFolder(e.target.value)}
            />
          </label>
          <label className="plan-check">
            <input
              type="checkbox"
              checked={upload}
              disabled={busy}
              onChange={(e) => setUpload(e.target.checked)}
            />
            Upload each automatic backup
          </label>
          <label>
            Keep on Drive (per server)
            <input
              type="number"
              min={1}
              max={60}
              value={keepDrive}
              disabled={busy}
              onChange={(e) => setKeepDrive(Number(e.target.value))}
            />
          </label>
          <p className="console-note">
            A personal Drive holds 15 GB and one Creative backup is about 1.5
            GB: keep this number small. The app only sees the files it created
            itself.
          </p>
          <div className="backup-actions">
            <button className="primary" disabled={busy} onClick={save}>
              Save
            </button>
            <button disabled={busy || !clientId} onClick={connect}>
              {view.drive.connected ? "Reconnect" : "Connect Google Drive"}
            </button>
            {view.drive.connected && (
              <button disabled={busy} onClick={disconnect}>
                Disconnect
              </button>
            )}
          </div>
          <details className="plan-help">
            <summary>How to get the client ID and secret</summary>
            <ol>
              <li>
                In Google Cloud Console create a project and enable the{" "}
                <em>Google Drive API</em>.
              </li>
              <li>
                Under <em>APIs &amp; Services → OAuth consent screen</em>, set
                it up and{" "}
                <strong>publish it (&ldquo;In production&rdquo;)</strong>. In
                &ldquo;Testing&rdquo; Google expires the access after 7 days and
                the daily upload silently stops.
              </li>
              <li>
                Create <em>Credentials → OAuth client ID</em>, type{" "}
                <strong>Web application</strong>.
              </li>
              <li>
                Add this <em>Authorized redirect URI</em> exactly:{" "}
                <code>{redirect}</code>
              </li>
              <li>
                Paste the client ID and secret here, save, then connect. Only
                the <code>drive.file</code> permission is requested.
              </li>
            </ol>
          </details>
        </section>
      </div>
      {(message || notice) && (
        <p className="management-notice" role="status">
          {message || notice}
        </p>
      )}
    </>
  );
}
