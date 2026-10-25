# Ideas (not started)

## 1. Global tab list: players of every server, with their server

Wish: in the Minecraft tab list (TAB key), show the players connected on all servers (lobby, creative,
survival), saying which server each one is on, e.g. `[Survival] Steve`.

Notes from the analysis:
- Velocity only shows the players of the server you are on; there is no Velocity plugin in the repo yet.
- Plan: a new module `proxy-plugin` (Velocity API) mounted in the `velocity` container (`/server/plugins`, see
  `compose.local.yaml` / `compose.prod.yaml`). It adds the players of the other servers to each player's
  `TabList` (name, skin through their `GameProfile`, server as prefix or grey suffix) and updates on connect,
  disconnect and server switch.
- Testing needs a proxy, two backends and two clients at once; `scripts/e2e_client.py` only covers one server
  and one client today. Unit-test the logic, then extend the e2e harness if wanted.
- Related, already done: `sample-players-in-ping = true` in `infra/velocity.toml` (names in the server list
  hover tooltip; public to anyone who pings the server).

## 2. A second, empty overworld for mini-games

Wish: an extra empty world to run mini-games in.

Options discussed:
- **Extra void world inside an existing server** (recommended, e.g. on lobby or creative): create it in code
  like `ww_sky` (`werewolf/sky/SkyWorld.java`) or with Multiverse. No new container. Needs per-world
  inventory, gamerules and protection; the arena plugin already snapshots and restores player state.
- **A new `minigames` server in Docker**: full isolation (own gamerules and plugins) but one more container
  to manage.

Open question: which server would host the world.
