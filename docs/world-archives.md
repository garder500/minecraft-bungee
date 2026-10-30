# Portable worlds and repeatable deployment

Run `make help` from the project root. Requirements: GNU Make, Python 3.9+, Docker Engine and Docker Compose. The Makefile reads the resolved Compose configuration; it never executes `.env` as shell code. Use `COMPOSE=compose.prod.yaml` for every production command.

## Create ZIPs

```bash
make zip SERVER=creative
make zip SERVER=lobby
make zip SERVER=survival
make export-panel
# Or export all four in sequence:
make zip-all
```

Each operation briefly stops **only its selected service**, waits for shutdown, exports and restarts the existing container only if it was running beforehand. Deliberately stopped services stay stopped. An export does not pull images, rebuild, recreate containers or change their configuration. A running game server will disconnect its players; choose a suitable maintenance moment. `zip-all` is a sequence of consistent per-service snapshots, not a single simultaneous network snapshot.

Files appear in `backups/<server>-export-<UTC timestamp>.zip`, with a `.zip.sha256` sidecar. Set `OUT=/path/to/name.zip` to choose a filename for a single export. Existing output files are refused. Outputs and hashes have mode 0600; Git ignores the backup directory. Do not put an output archive inside its own server data directory.

A server ZIP contains:

- Every immediate world folder with `level.dat`, including Nether/End and player UUID saves, inventory, spawn, gamerules, datapacks and the exact built Lobby. On Survival that includes the Skyblock worlds `sb_hub`, `sb_world`, `sb_nether` and `sb_end`. The throw-away dungeon worlds `sb_dungeon_*` (created and deleted by the survival plugin for each run, stored as `world/dimensions/<namespace>/sb_dungeon_*`) are **excluded** from `make zip`/`make export`; a dungeon run in progress at export time is simply not saved. (The panel's own Backups page does not apply this rule yet; see `docs/skyblock-notes/netmenu.md`.)
- Plugin configuration/data, claims, arena definitions, pending player restoration snapshots and third-party plugin JARs.
- OP/whitelist/ban lists, player-name cache, `permissions.yml` and the managed Lobby marker when present.
- A manifest with the server identity, Minecraft version and SHA-256/size of every payload file.

Java/server runtimes, logs, Minecraft download caches, Paper remapping caches and the managed network/lobby/arena plugin JARs are excluded. Managed plugins are supplied by the selected Docker images on startup. Network configuration (`server.properties`, Paper forwarding config), forwarding/RCON secrets, root `.env` and the panel database are **not** copied by a world ZIP. Plugin data can itself contain credentials: treat every ZIP as private and transfer it over SSH.

## From the panel

The **Backups** page of the panel does the same for Lobby, Creative and Survival, with the same ZIP format and the same `backups/` folder, so a ZIP made there restores with `make restore` and the other way round:

- **Create** works on a running server without stopping it or disconnecting anyone: autosave is paused over RCON, the worlds are flushed, the ZIP is written, autosave is turned back on. A stopped server is zipped as it is.
- **Download** streams the ZIP (resumable); **Upload** takes a ZIP made by the panel or by `make zip` (up to 40 GiB, checked for a manifest). Names use letters, digits, dot, dash and underscore.
- **Restore** checks the ZIP first (paths, checksums, server, exact Minecraft version), stops the server, saves its current state as a `before-restore` ZIP, swaps the data in with rollback, and restarts the server if it was running. Players are disconnected. If the old files cannot be deleted afterwards (root-owned), the restore still counts and the page names the leftover `.archive-staging-*` folder.
- Panel database ZIPs (`make export-panel`) are listed but restored only with `make import-panel`.

### Automatic daily backups and Google Drive

The same page can run a backup every day and keep a copy on Google Drive.

- **Schedule**: a time (HH:MM) in a chosen time zone, and which servers to back up. Each run backs up the selected servers one after the other, with the same live-safe method as a manual backup, then uploads, then cleans up. A run missed because the controller was down is made up the same day; turning the schedule on (or moving its hour) after today's hour does not start a backup at once. **Run now** does the whole thing on demand.
- **Retention** (how many to keep, per server): *on this machine* and *on Drive*. Only automatic backups (`<server>-auto-<stamp>.zip`) are deleted to respect it: manual backups, uploads and the `before-restore` copies are never removed automatically. Drive files are cleaned only after the new upload succeeded, and only files this app tagged as automatic.
- **Guard rails**: a server is skipped (and the failure shown on the page) when the free disk space is below the size of its last backup plus a margin, because the backups share the servers' disk. A server busy with a restore or a manual backup is retried the same day.
- **Settings** live in `state/<stack>/controller/backup-settings.json` (mode 0600, on the controller only). The panel never gets the Google client secret or the token back; the secret field is write-only (empty keeps the saved one).

Google setup (once):

1. In the [Google Cloud console](https://console.cloud.google.com/) create a project and enable the **Google Drive API**.
2. *OAuth consent screen*: configure it and **publish it ("In production")**. While it is in *Testing*, Google expires the refresh token after 7 days and the daily upload would silently stop (the page then says "connect again"). The only permission requested is `drive.file` (files the app creates itself), which needs no Google verification.
3. *Credentials → Create credentials → OAuth client ID*, type **Web application**, with the authorised redirect URI exactly `https://panel.vampyuyu.win/api/v1/drive/callback` (plus `http://localhost:5173/api/v1/drive/callback` for the local stack).
4. In the panel paste the client ID and secret, **Save**, then **Connect Google Drive** and accept. A personal Drive holds 15 GB and one Creative backup is about 1.5 GB: keep *Keep on Drive* small. A quota error from Google is shown as is.

`scripts/up.sh` (run by `make up` and by `scripts/deploy.sh`) creates `backups/` and the controller's state folder owned by the controller's user, which Docker would otherwise create as root.

The controller needs the `backups/` folder mounted (`BACKUPS_DIR`, default `./backups`) and `MC_VERSION`; both Compose files already do this. Every create, download, upload, restore and delete is written to the panel's audit log.

## Inspect and restore

```bash
make inspect SERVER=creative ZIP="/path/to/creative-export.zip"
make restore SERVER=creative ZIP="/path/to/creative-export.zip"
```

The tool verifies archive paths, duplicates, links, size limits, destination server identity and exact Minecraft version. It reads the version from the worlds' `level.dat`, not only from the manifest. If a `.sha256` sidecar is present, the entire ZIP must match it too. Extraction is staged and payload hashes checked **before stopping the target**.

The selected service then stops, its previous portable state is exported to `backups/<server>-before-import-<timestamp>.zip`, and the staged data is installed using same-filesystem renames under the existing bind mount. A failed installation rolls those renames back. Keep both the original input ZIP and the automatic previous-state ZIP. To roll back, restore the previous-state ZIP with the same command; image rollback alone never downgrades worlds.

A complete server ZIP replaces that server's portable worlds and plugins as a set, including removing former portable roots absent from the new snapshot. It keeps the destination's network configuration and secrets. The target data path must already be initialized with `make configure`. Imports never start a previously stopped service. Concurrent archive operations on the same service are refused. The operator must own the data or run as root; restored files use the target data owner's UID/GID.

If an old root-owned folder cannot be cleaned up, the tool preserves its `.archive-staging-*` directory and reports its exact path; this does not prevent the restored server resuming. An incomplete rollback preserves recovery files and leaves the service stopped for inspection.

## A conventional Minecraft world ZIP

```bash
make inspect SERVER=creative ZIP="/path/to/My World.zip"
make restore SERVER=creative ZIP="/path/to/My World.zip" WORLD=world
```

A plain ZIP must contain exactly **one** `level.dat`, either at the archive root or inside one enclosing folder, with all files inside that world. This restores only the selected `WORLD` directory; other dimensions, plugins and network settings remain. Matching release version metadata is required. Multiworld server ZIPs from elsewhere need the existing reviewed Creative importer; they are not silently treated as a single world. Old worlds, snapshot-version worlds and unknown version metadata require separate compatibility review. Never solve a mismatch by changing the configured version to an older release.

Claims are plugin data: a plain world-only ZIP does not transfer claims. Prefer the project's complete server export for moving Creative with its claims and plugins.

## Panel and network state

```bash
make export-panel
make import-panel ZIP="/path/to/panel-export.zip"
```

The panel ZIP contains SQLite accounts/password hashes, player roles, role command grants, audit, sessions and other files in its data directory. The panel is stopped for consistency; restoration checks SQLite integrity and backs up the previous panel state. No administrator bootstrap is needed after restoring an existing panel database; the existing administrator password remains valid.

World ZIPs plus the panel ZIP are convenient for map iteration. For an **exact complete network migration**, including Redis/network bans, forwarding/RCON tokens and `.env`, also use:

```bash
make backup
```

This uses the existing full-network stopped backup, includes state/configuration/secrets in a private TAR.GZ, and deliberately leaves the stack stopped. Follow the full backup/restore procedure in the root README. Redis state is not contained in the per-world or panel ZIPs. Keep the original Creative source ZIP separately; it is not required for normal map iteration.

## First production installation

1. Clone the repository using SSH and choose a release whose GHCR publishing workflow succeeded. Set the full published commit SHA as `IMAGE_TAG`; production uses immutable image tags.
2. Create the production `.env`: Linux amd64, real UID/GID/Docker socket group, production paths/domain, heaps appropriate to the host and the **same Minecraft version as the ZIPs**. Preserve the existing explicit EULA acceptance.
3. Run `make configure COMPOSE=compose.prod.yaml`. This creates only missing secrets and configures private modern forwarding. Do not start the games yet.
4. Transfer the server ZIPs, panel ZIP and checksum files over SSH. Restore each into its matching service using `make restore ... COMPOSE=compose.prod.yaml` and `make import-panel ... COMPOSE=compose.prod.yaml`. They remain stopped.
5. Configure Cloudflare DNS/Full (strict), firewall and the mounted Origin CA certificate as described in the root README. When seeding from an existing full network backup, keep its matched state/config/secrets together.
6. Run `make up COMPOSE=compose.prod.yaml`. Check panel login, inventories, roles, claims, arena settings and real-player transfers before opening the network.

No SSH host, remote credentials, DNS changes or production deployment are embedded in these commands.
