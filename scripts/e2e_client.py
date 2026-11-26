#!/usr/bin/env python3
"""End-to-end check of the resource pack: a creative-only Paper 26.3 server, a headless vanilla
26.3 client (Prism offline, Xvfb, Mesa lavapipe) and the real pack served over HTTP.

    make e2e-pack          # or: python3 scripts/e2e_client.py [--keep] [--shots DIR]

It fails when the client reports "Resource reload failed", a shader pipeline that does not compile
or a missing glyph, and it checks on screenshots that the night effect really darkens the screen.
Everything lives in the gitignored .e2e/ folder; nothing of the real stack is touched. Needs: java 25
(JAVA25), Prism Launcher with an installed 26.3 instance (PRISM_DIR, PRISM_INSTANCE), Xvfb, and
xdotool and ImageMagick (found on PATH or built through nix). EULA=TRUE must be set in .env.
E2E_VIEW=<chunks> raises the server view distance and the client render distance (default 4): far views of a
map or its scenery stay empty at 4 (slower client).
"""
import argparse, shlex, hashlib, http.server, json, os, pathlib, re, shutil, socket, struct, subprocess, sys
import math, threading, time, urllib.request, uuid

ROOT = pathlib.Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT / "scripts"))
import build_pack

WORK = ROOT / ".e2e"
PAPER = ("26.3", "223", "f8e684a4fef2a8b46d03aac049b49d59275e9ce5367dba7c6075cb5780f38470")
MC_PORT, RCON_PORT, HTTP_PORT, DISPLAY = 25599, 25598, int(os.environ.get("E2E_HTTP_PORT", 25597)), ":99"
RCON_PASSWORD = "e2e-only"
DEBUG_PORT = 25596
DEBUG = False
GLYPH = ""
PRISM = pathlib.Path(os.environ.get("PRISM_DIR", "~/.local/share/PrismLauncher")).expanduser()
INSTANCE = os.environ.get("PRISM_INSTANCE", "26.3")
FATAL = ["Resource reload failed", "Couldn't compile pipeline", "ShaderCompileException",
         "Failed to load pack", "Unable to parse", "Missing glyph"]


def say(msg):
    print(f"[e2e] {msg}", flush=True)


def tool(name, package):
    found = shutil.which(name)
    if found:
        return found
    out = subprocess.run(["nix", "--extra-experimental-features", "nix-command flakes", "build",
                          f"nixpkgs#{package}", "--no-link", "--print-out-paths"],
                         capture_output=True, text=True, check=True).stdout.split()[-1]
    for sub in ("bin",):
        if (pathlib.Path(out) / sub / name).exists():
            return str(pathlib.Path(out) / sub / name)
    sys.exit(f"{name} not found")


def java25():
    candidates = [os.environ.get("JAVA25")]
    cfg = PRISM / "instances" / INSTANCE / "instance.cfg"
    if cfg.exists():
        candidates += re.findall(r"^JavaPath=(.*)$", cfg.read_text(), re.M)
    for c in candidates:
        if c and pathlib.Path(c).exists():
            return c
    sys.exit("Set JAVA25 to a Java 25 binary")


def paper_jar():
    version, build, sha = PAPER
    jar = WORK / f"paper-{version}-{build}.jar"
    if not jar.exists():
        say(f"downloading Paper {version} build {build}")
        url = f"https://fill-data.papermc.io/v1/objects/{sha}/{jar.name}"
        WORK.mkdir(exist_ok=True)
        request = urllib.request.Request(url, headers={"User-Agent": "vampyuyu-e2e"})
        with urllib.request.urlopen(request) as response, open(jar, "wb") as out:
            shutil.copyfileobj(response, out)
    if hashlib.sha256(jar.read_bytes()).hexdigest() != sha:
        jar.unlink()
        sys.exit("Paper jar checksum mismatch")
    return jar


class Rcon:
    def __init__(self):
        self.sock = socket.create_connection(("127.0.0.1", RCON_PORT), timeout=10)
        self.send(3, RCON_PASSWORD)

    def send(self, kind, body):
        data = struct.pack("<ii", 1, kind) + body.encode() + b"\0\0"
        self.sock.sendall(struct.pack("<i", len(data)) + data)
        size = struct.unpack("<i", self.sock.recv(4))[0]
        payload = b""
        while len(payload) < size:
            payload += self.sock.recv(size - len(payload))
        return payload[8:-2].decode(errors="replace")

    def command(self, text):
        return self.send(2, text)


def serve_pack(directory):
    handler = lambda *a, **k: http.server.SimpleHTTPRequestHandler(*a, directory=str(directory), **k)
    handler.log_message = lambda *a: None
    server = http.server.ThreadingHTTPServer(("127.0.0.1", HTTP_PORT), handler)
    threading.Thread(target=server.serve_forever, daemon=True).start()
    return server


def wait_for(path, needle, seconds, procs=()):
    deadline = time.time() + seconds
    while time.time() < deadline:
        if path.exists() and needle in path.read_text(errors="replace"):
            return True
        for p in procs:
            if p.poll() is not None:
                return False
        time.sleep(1)
    return False


def stop_stale():
    """Leftovers of an interrupted run would hold our ports."""
    pack_server = r"[p]ython.*-m http.server " + str(HTTP_PORT) + r" .*--directory " + re.escape(str(WORK / "pack"))
    for pattern in ("[.]e2e/paper", "[p]rismlauncher -d", "[X]vfb " + DISPLAY, pack_server):
        subprocess.run(["pkill", "-9", "-f", pattern], check=False)
    time.sleep(1)


ARENA_JAR = ROOT / "arena-plugin/target/nexus-arena-engine.jar"
NETWORK_JAR = ROOT / "network-plugin/target/network-display.jar"
SURVIVAL_JAR = ROOT / "survival-plugin/target/survival-plugin.jar"
VALOBLOCK = False  # set by the valoblock scenario: the e2e arena is a ValoBlock arena
WITH_SURVIVAL = False  # set by the back scenario: also load the survival plugin
API_PORT = 8085
API_TOKEN = "e2e-token-" + "x" * 30


def menu_pack_id(sha1):
    """Same derivation as MenuSkin.packId: the plugin only trusts status events of this id."""
    raw = bytearray(hashlib.md5(("vampyuyu-menus:" + sha1).encode()).digest())
    raw[6] = raw[6] & 0x0F | 0x30
    raw[8] = raw[8] & 0x3F | 0x80
    return str(uuid.UUID(bytes=bytes(raw)))


ARENA = {
    "id": "e2e", "name": "E2E village", "worldName": "world", "preset": "werewolf",
    "bounds": {"minX": -40, "minY": -64, "minZ": -40, "maxX": 40, "maxY": 320, "maxZ": 40},
    "spawn": {"x": 0.5, "y": -60, "z": 0.5, "yaw": 0, "pitch": 0},
    "allowedBreakBlocks": [], "allowedPlaceBlocks": [], "allowPvP": False, "allowChests": False,
    "allowDoors": True, "gameMode": "ADVENTURE", "durationSeconds": 1800, "minPlayers": 12,
    "maxPlayers": 20, "discussionSeconds": 60, "voteSeconds": 30, "nightSeconds": 30,
    "werewolf": {"roles": {}, "cupidSeconds": 15, "witchSeconds": 15, "hunterSeconds": 15,
                 "ambienceEnabled": True},
}


# The valoblock scenario's arena: the lobby zone is the flat world, the match is played in the plugin's own vb_arena world.
ARENA_VB = {
    "id": "e2e", "name": "E2E ValoBlock", "worldName": "world", "preset": "valoblock",
    "bounds": {"minX": -40, "minY": -64, "minZ": -40, "maxX": 40, "maxY": 320, "maxZ": 40},
    "spawn": {"x": 0.5, "y": -60, "z": 0.5, "yaw": 0, "pitch": 0},
    "allowedBreakBlocks": [], "allowedPlaceBlocks": [], "allowPvP": True, "allowChests": False,
    "allowDoors": True, "gameMode": "ADVENTURE", "durationSeconds": 3600, "minPlayers": 1,
    "maxPlayers": 10,
}


def start_server(zip_path, sha1, plugin=False, pack_required=True):
    server = WORK / "server"
    shutil.rmtree(server, ignore_errors=True)
    server.mkdir(parents=True)
    (server / "eula.txt").write_text("eula=true\n")
    (server / "server.properties").write_text("\n".join([
        "online-mode=false", "white-list=false", "enforce-whitelist=false", "gamemode=creative", "force-gamemode=true", "level-type=minecraft:flat",
        "generate-structures=false",
        'generator-settings={"biome":"minecraft:plains","layers":[{"block":"minecraft:bedrock","height":1},{"block":"minecraft:dirt","height":2},{"block":"minecraft:grass_block","height":1}]}', "spawn-monsters=false", "spawn-protection=0", "view-distance=" + os.environ.get("E2E_VIEW", "4"),
        f"server-port={MC_PORT}", "enable-rcon=true", f"rcon.port={RCON_PORT}",
        f"rcon.password={RCON_PASSWORD}", "enforce-secure-profile=false",
        *([] if plugin else [
            f"resource-pack=http://127.0.0.1:{HTTP_PORT}/{zip_path.name}",
            f"resource-pack-sha1={sha1}", "require-resource-pack=true"]),
        "motd=Isolated Skyblock test", "server-ip=127.0.0.1", "level-name=world", ""]))
    env = dict(os.environ)
    if plugin:
        data = server / "plugins" / "NexusArenaEngine"
        (data / "arenas").mkdir(parents=True)
        shutil.copy(ARENA_JAR, server / "plugins" / ARENA_JAR.name)
        shutil.copy(NETWORK_JAR, server / "plugins" / NETWORK_JAR.name)  # offers the pack on join
        if WITH_SURVIVAL:
            shutil.copy(SURVIVAL_JAR, server / "plugins" / SURVIVAL_JAR.name)
        served = WORK / "pack" / "pack"
        served.mkdir(exist_ok=True)
        shutil.copy(zip_path, served / f"{sha1}.zip")
        # The second pack (guns, agents, scope): the plugin adds it over the menu pack when a player joins a ValoBlock arena.
        valo_zip, valo_sha1 = build_pack.build(WORK / "pack", "valoblock")
        shutil.copy(valo_zip, served / f"{valo_sha1}.zip")
        (data / "arenas" / "e2e.json").write_text(json.dumps(ARENA_VB if VALOBLOCK else ARENA, indent=1))
        (server / "token").write_text(API_TOKEN)
        if os.environ.get("E2E_QUICK_ACTIONS"):
            # The ValoBlock Quick Actions datapack (the plugin writes it itself only on SERVER_ID=creative, and a datapack needs a
            # restart): pre-write the same files so the first boot already has the dialog. Keep in step with VbQuickActions.java.
            buttons = [("Shop", "Open the Armory (buy phase only)", "vb buy"), ("Agent", "Open the agent select (while agents are chosen)", "vb agent"),
                       ("Map", "Open the tactical map for the ability in your hand", "vb tac"), ("Vote", "Reopen the map vote (while voting is open)", "vb vote")]
            acts = ",".join('{"label":{"text":"%s"},"tooltip":{"text":"%s"},"width":120,"action":{"type":"minecraft:run_command","command":"%s"}}' % b for b in buttons)
            dialog = ('{"type":"minecraft:multi_action","title":{"text":"ValoBlock"},"can_close_with_escape":true,'
                      '"body":[{"type":"minecraft:plain_message","contents":{"text":"Quick actions for a ValoBlock match. Outside a match they only say so."}}],'
                      '"columns":2,"actions":[' + acts + '],"exit_action":{"label":{"text":"Close"},"width":120}}')
            dp = server / "world" / "datapacks" / "vampyuyu-valoblock-quick"
            (dp / "data/vampyuyu/dialog").mkdir(parents=True)
            (dp / "data/minecraft/tags/dialog").mkdir(parents=True)
            (dp / "pack.mcmeta").write_text('{"pack":{"description":"ValoBlock quick actions","min_format":[82,0],"max_format":[1000,0]}}')
            (dp / "data/vampyuyu/dialog/valoblock.json").write_text(dialog)
            (dp / "data/minecraft/tags/dialog/quick_actions.json").write_text('{"values":["vampyuyu:valoblock"]}')
        env.update(ROLE_BRIDGE_TOKEN_FILE=str(server / "token"), SERVER_ID=os.environ.get("E2E_SERVER_ID", "survival"),
                   MENU_PACK_BASE_URL=f"http://127.0.0.1:{HTTP_PORT}", MENU_PACK_REQUIRED="true" if pack_required else "false",
                   MENU_PACK_SHA1_FILE=str(WORK / "pack" / "vampyuyu-menus.sha1"),
                   VALOBLOCK_PACK_SHA1_FILE=str(WORK / "pack" / "vampyuyu-valoblock.sha1"),
                   ROLE_BRIDGE_URL="http://127.0.0.1:9", ARENA_REDIS_HOST="127.0.0.1",
                   WEREWOLF_LOG_CONSOLE="false",
                   WEREWOLF_ARENA=os.environ.get("E2E_SERVER_ID", "survival") + ":e2e")
    log = open(server / "console.log", "w")
    # DEBUG_PORT: JDWP for scripts/hotswap.py (class redefinition without a restart).
    debug = [f"-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=127.0.0.1:{DEBUG_PORT}"] if DEBUG else []
    command = [java25(), "-Dterminal.jline=false", "-Dterminal.ansi=false", "-Xmx1G", *debug, "-jar", str(paper_jar()), "--nogui"]
    if plugin:
        # The network plugin only reads /run/secrets/role_bridge_token: give a private mount
        # namespace its own tmpfs there, so nothing on the real /run is touched.
        # Binaries by their store directory (not under /run, and not the multi-call coreutils name).
        store_bin = os.path.dirname(os.path.realpath(shutil.which("mkdir")))
        mkdir, cp = f"{store_bin}/mkdir", f"{store_bin}/cp"
        script = (f'mount -t tmpfs tmpfs /run && {mkdir} -p /run/secrets && '
                  f'{cp} {shlex.quote(str(server / "token"))} /run/secrets/role_bridge_token && exec "$@"')
        command = ["unshare", "-rm", "bash", "-c", script, "bash", *command]
    proc = subprocess.Popen(command, cwd=server, stdout=log, stderr=subprocess.STDOUT,
                            stdin=subprocess.DEVNULL, env=env)
    if not wait_for(server / "console.log", "Done (", 600, [proc]):
        proc.kill()
        sys.exit("server did not start:\n" + (server / "console.log").read_text()[-2000:])
    return proc, server


def prepare_client(tools):
    """A private Prism data dir: shared libraries and assets, a throw-away copy of the instance."""
    data = WORK / "prism"
    shutil.rmtree(data, ignore_errors=True)
    (data / "instances").mkdir(parents=True)
    for entry in PRISM.iterdir():
        if entry.name not in ("instances", "logs", "prismlauncher.cfg"):
            (data / entry.name).symlink_to(entry)
    shutil.copy(PRISM / "prismlauncher.cfg", data / "prismlauncher.cfg")  # skips the first-run wizard
    src = PRISM / "instances" / INSTANCE
    dst = data / "instances" / INSTANCE
    dst.mkdir()
    for name in ("instance.cfg", "mmc-pack.json"):
        shutil.copy(src / name, dst / name)
    cfg = dst / "instance.cfg"
    # A garbage-collected Java store path in the copied instance: point the copy at the JAVA25 one.
    for path in re.findall(r"^JavaPath=(.*)$", cfg.read_text(), re.M):
        if not pathlib.Path(path).exists():
            cfg.write_text(cfg.read_text().replace("JavaPath=" + path, "JavaPath=" + java25()))
    game = dst / "minecraft"
    game.mkdir()
    (game / "options.txt").write_text("\n".join([
        "lang:" + os.environ.get("E2E_LANG", "en_us"), "onboardAccessibility:false", "tutorialStep:none", "joinedFirstServer:true",
        "fullscreen:false", "maxFps:30", "renderDistance:" + os.environ.get("E2E_VIEW", "4"), "pauseOnLostFocus:false",
        "skipMultiplayerWarning:true", "narrator:0", "hideServerAddress:true", ""]))
    return data, game


def shot(tools, path):
    env = dict(os.environ, DISPLAY=DISPLAY)
    subprocess.run([tools["import"], "-window", "root", str(path)], env=env, check=True)


_FFMPEG = []


def ffmpeg_bin():
    """An ffmpeg that can grab X11 (x11grab): $E2E_FFMPEG, the PATH, or any build in the nix store, looked up once."""
    if not _FFMPEG:
        import glob
        found = None
        for exe in [os.environ.get("E2E_FFMPEG"), shutil.which("ffmpeg")] + sorted(glob.glob("/nix/store/*ffmpeg*/bin/ffmpeg")):
            if not exe or not os.access(exe, os.X_OK):
                continue
            devices = subprocess.run([exe, "-hide_banner", "-devices"], capture_output=True, text=True).stdout
            if "x11grab" in devices:
                found = exe
                break
        _FFMPEG.append(found)
    return _FFMPEG[0]


def client_geometry(tools):
    """(x, y, w, h) of the game window on the Xvfb screen (the client draws 854x480 in the middle of it)."""
    env = dict(os.environ, DISPLAY=DISPLAY)
    out = subprocess.run([tools["xdotool"], "search", "--name", "Minecraft", "getwindowgeometry", "--shell"],
                         capture_output=True, text=True, env=env).stdout
    vals = dict(line.split("=", 1) for line in out.splitlines() if "=" in line)
    try:
        x, y, w, h = int(vals["X"]), int(vals["Y"]), int(vals["WIDTH"]), int(vals["HEIGHT"])
        if w >= 320 and h >= 240:
            return x, y, w - w % 2, h - h % 2
    except (KeyError, ValueError):
        pass
    return 213, 120, 854, 480


def record(tools, path, seconds, fps=30, slow=1):
    """A short H.264 clip of the game window (x11grab on the Xvfb display), small enough to send (CRF 30).
    slow: the caller has slowed the game down that many times (/tick rate 20/slow): the grab lasts slow x longer and the
    clip is sped back up, so it plays at the real speed with slow x more distinct frames than the software-rendered
    client manages in real time (about 12 a second)."""
    exe = ffmpeg_bin()
    if not exe:
        say("record: no ffmpeg, skipped " + str(path))
        return None
    x, y, w, h = client_geometry(tools)
    cmd = [exe, "-y", "-loglevel", "error", "-f", "x11grab", "-video_size", f"{w}x{h}", "-framerate", str(fps),
           "-t", str(seconds * slow), "-i", f"{DISPLAY}+{x},{y}"]   # -t before -i: the length of the grab
    if slow > 1:
        cmd += ["-vf", f"setpts=PTS/{slow}", "-r", str(fps)]
    cmd += ["-c:v", "libx264", "-preset", "veryfast", "-crf", "30", "-pix_fmt", "yuv420p", "-movflags", "+faststart", str(path)]
    subprocess.run(cmd, check=False)
    return path


def brightness(tools, path):
    out = subprocess.run([tools["magick"], str(path), "-colorspace", "Gray", "-format", "%[fx:mean]", "info:"],
                         capture_output=True, text=True, check=True).stdout
    return float(out)


def shader_scenario(ctx):
    tools, shots, client_log, failures = ctx['tools'], ctx['shots'], ctx['client_log'], ctx['failures']
    rcon = Rcon()
    rcon.command("tp E2EBot 0 -50 0")  # underground: a uniform scene to compare against
    rcon.command("time set noon")
    time.sleep(4)
    shot(tools, shots / "baseline.png")
    base = brightness(tools, shots / "baseline.png")
    results = {}
    for fx, name in ((1, "night"), (2, "dawn")):
        for _ in range(5):
            rcon.command('title E2EBot actionbar {"text":"%s","font":"vampyuyu:fx","color":"#4e5c0%d"}'
                         % (GLYPH, fx))
            time.sleep(0.4)
        shot(tools, shots / f"{name}.png")
        results[name] = brightness(tools, shots / f"{name}.png")
    say(f"brightness baseline {base:.3f} night {results['night']:.3f} dawn {results['dawn']:.3f}")
    if not results["night"] < base * 0.85:
        failures.append("the night effect did not darken the screen")
    if abs(results["dawn"] - base) < 0.01 and abs(results["night"] - base) < 0.01:
        failures.append("no effect visible at all")
    text = client_log.read_text(errors="replace")
    for needle in FATAL:
        if needle in text and f"client log contains '{needle}'" not in failures:
            failures.append(f"client log contains '{needle}'")


def api(method, path, body=None):
    request = urllib.request.Request(
        f"http://127.0.0.1:{API_PORT}{path}", method=method,
        data=None if body is None else json.dumps(body).encode(),
        headers={"Authorization": f"Bearer {API_TOKEN}", "Content-Type": "application/json"})
    try:
        with urllib.request.urlopen(request, timeout=30) as response:
            raw = response.read().decode()
    except urllib.error.HTTPError as error:
        raise RuntimeError(f"{method} {path}: {error.code} {error.read().decode()}") from None
    return json.loads(raw) if raw.startswith(("{", "[")) else raw


def same_place(a, b):
    """Same game mode and within a block: a restored player may settle a few tenths away."""
    return a["mode"] == b["mode"] and all(abs(x - y) < 1.0 for x, y in zip(a["pos"], b["pos"]))


def on_platform(rcon):
    """The human sits on the sky platform (world ww_sky, around height 200), not in the lobby zone."""
    dimension = rcon.command("data get entity E2EBot Dimension")
    pos = rcon.command("data get entity E2EBot Pos")
    y = float(pos.split("[")[1].split(",")[1].rstrip("d "))
    return "ww_sky" in dimension and 199 <= y <= 204, f"{dimension.strip()} y={y}"


def player_state(rcon):
    pos = rcon.command("data get entity E2EBot Pos")
    nums = [round(float(x.rstrip("d")), 1) for x in pos.split("[")[1].rstrip("]").split(",")]
    mode = rcon.command("data get entity E2EBot playerGameType")
    return {"pos": nums, "mode": mode.split(":")[-1].strip()}


def _call_phases():
    """The night calls of the roles of the Ultimate Werewolf set, from the plugin's role files."""
    roles = ROOT / "arena-plugin/src/main/resources/roles"
    return {json.loads(f.read_text())["call"]["phase"] for f in roles.glob("*.json")
            if "call" in json.loads(f.read_text())
            and json.loads(f.read_text())["call"]["phase"] not in ("MARTYR", "MINION")}


NIGHT = {"CUPID", "GUARD", "SEER", "NIGHT", "WITCH"} | _call_phases()


def journal_text():
    logs = api("GET", "/arenas/logs").get("logs", [])
    if not logs:
        return ""
    names = sorted(x["name"] if isinstance(x, dict) else x for x in logs)
    return api("GET", f"/arenas/logs/{names[-1]}")


def arena_phase():
    return api("GET", "/arenas")["arenas"][0]["phase"]


def wait_phase(wanted, seconds=60):
    """Waits until the arena reaches one of the phases; returns it, or None."""
    wanted = {wanted} if isinstance(wanted, str) else set(wanted)
    deadline = time.time() + seconds
    while time.time() < deadline:
        phase = arena_phase()
        if phase in wanted:
            return phase
        time.sleep(0.4)
    return None


def wait_journal(needle, seconds=25):
    deadline = time.time() + seconds
    while time.time() < deadline:
        if needle in journal_text():
            return True
        time.sleep(0.5)
    return False


def peek(rcon, alive_only=False):
    """role of every participant, from the admin /ww peek (dead ones left out when asked)."""
    import re
    roles = {}
    for line in re.sub("\u00a7.", "", rcon.command("ww peek")).splitlines():
        if ": " in line:
            name, role = line.rsplit(": ", 1)
            if alive_only and "(dead)" in role:
                continue
            roles[name.strip()] = role.split()[0]
    return roles


def bot_with(rcon, wanted, exclude=()):
    """Name token (botN) of a living bot whose role satisfies wanted (a predicate), looked up now."""
    for name, role in peek(rcon, alive_only=True).items():
        if name.startswith("[Bot] Guest ") and wanted(role) and name not in exclude:
            return "bot" + name.rsplit(" ", 1)[1]
    raise AssertionError("no such living bot")


def click_until(rcon, args, needle, tries=12):
    """Right-clicks as the human until the journal shows the effect (presentations delay the first)."""
    for _ in range(tries):
        rcon.command("ww click E2EBot " + args)
        if wait_journal(needle, 2.5):
            return True
    return False


WOLVES = lambda role: role in ("WEREWOLF", "ALPHA_WOLF", "INFECTOR", "WOLF_CUB")
NOT_WOLF = lambda role: not WOLVES(role)

# role -> (cast of the other roles, what to check)
ROLE_CASES = {
    "VILLAGER": {"WEREWOLF": 2, "SEER": 1},
    "WEREWOLF": {"WEREWOLF": 2, "SEER": 1},
    "SEER": {"WEREWOLF": 2, "SEER": 1},
    "WITCH": {"WEREWOLF": 2, "WITCH": 1},
    "HUNTER": {"WEREWOLF": 2, "HUNTER": 1},
    "CUPID": {"WEREWOLF": 2, "CUPID": 1},
    "GUARD": {"WEREWOLF": 2, "GUARD": 1},
    "ALPHA_WOLF": {"WEREWOLF": 1, "ALPHA_WOLF": 1},
    "INFECTOR": {"WEREWOLF": 1, "INFECTOR": 1},
    "WOLF_CUB": {"WEREWOLF": 1, "WOLF_CUB": 1},
    "SCAPEGOAT": {"WEREWOLF": 2, "SCAPEGOAT": 1},
    "BEAR_TAMER": {"WEREWOLF": 2, "BEAR_TAMER": 1},
    "VOTE_UX": {"WEREWOLF": 2, "SEER": 1},
}
FORCED = {"VOTE_UX": "VILLAGER"}  # scenario -> the role the human is dealt


def _role_files():
    """role id -> its JSON, from the plugin's resources (one file per role)."""
    folder = ROOT / "arena-plugin/src/main/resources/roles"
    return {f.stem: json.loads(f.read_text()) for f in sorted(folder.glob("*.json"))}


# The roles of the Ultimate Werewolf set have no hand-written scenario: the human holds the role, plays
# its night call through the real controls if it has one, else votes, and the game must go on.
ULTIMATE = [r for r in _role_files() if r not in ROLE_CASES]
CAST_OF = {
    "MASON": {"WEREWOLF": 2, "MASON": 2},
    "VAMPIRE": {"WEREWOLF": 1, "VAMPIRE": 2},
    "LONE_WOLF": {"WEREWOLF": 1, "LONE_WOLF": 1},
    "APPRENTICE_SEER": {"WEREWOLF": 2, "SEER": 1, "APPRENTICE_SEER": 1},
    "DOPPELGANGER": {"WEREWOLF": 2, "SEER": 1, "DOPPELGANGER": 1},
    "CULT_LEADER": {"WEREWOLF": 2, "CULT_LEADER": 1},
}
for _role in ULTIMATE:
    ROLE_CASES[_role] = CAST_OF.get(_role, {"WEREWOLF": 2, _role: 1})
# How the human plays a call: ("pick", n) right-clicks n different players, ("button", key) presses a
# button, ("watch",) only checks that the call opens (the ghost writes in the chat).
CALL_PLAY = {"HOODLUM": ("pick", 2), "TROUBLEMAKER": ("button", "stir"), "GHOST": ("watch",)}


def ultimate_checks(role, rcon, failures, tools, shots):
    me = "E2EBot"
    info = _role_files()[role]
    call = info.get("call") if role != "MINION" else None  # the minion's call exists only when picked
    any_bot = lambda exclude=(): bot_with(rcon, lambda r: True, exclude)
    if role == "MARTYR":
        if not wait_phase("DAY_VOTE", 120): return failures.append("MARTYR: no day vote")
        time.sleep(3)
        victim = bot_with(rcon, NOT_WOLF)
        for k in range(1, 12):
            if f"bot{k}" != victim:
                rcon.command(f"ww act bot{k} select {victim}")
        rcon.command("ww phase skip")
        if not wait_phase("MARTYR", 20): return failures.append("MARTYR: the martyr phase never opened")
        time.sleep(3)
        shot(tools, shots / "call-martyr.png")
        if not click_until(rcon, "ability:sacrifice", "presses SACRIFICE"):
            failures.append("MARTYR: sacrifice was refused")
    elif call:
        if not wait_phase(call["phase"], 150):
            return failures.append(f"{role}: its call {call['phase']} never opened")
        time.sleep(3)
        shot(tools, shots / f"call-{role.lower()}.png")
        play = CALL_PLAY.get(role, ("pick", 1))
        if play[0] == "pick":
            used = []
            for _ in range(play[1]):
                target = any_bot(tuple("[Bot] Guest " + u[3:] for u in used))
                used.append(target)
                if not click_until(rcon, target, f"{me}[{role}] selects"):
                    failures.append(f"{role}: picking {target} was refused")
        elif play[0] == "button":
            if not click_until(rcon, f"ability:{play[1]}", f"presses {play[1].upper()}"):
                failures.append(f"{role}: button {play[1]} was refused")
        if wait_phase("DISCUSSION", 120) is None:
            failures.append(f"{role}: the night never ended after its call")
    else:
        if not wait_phase("DAY_VOTE", 150): return failures.append(f"{role}: no day vote")
        time.sleep(3)
        if me not in peek(rcon, alive_only=True):  # the practice wolves ate him: nothing to vote with
            return say(f"{role}: eaten during the night, no vote to cast")
        if role == "DRUNK":  # the journal shows the real role he does not know yet
            return
        if not click_until(rcon, bot_with(rcon, NOT_WOLF), f"{me}[{role}] selects"):
            failures.append(f"{role}: the vote was not recorded")


def role_checks(role, rcon, roles, failures, tools, shots):
    """The human holds `role`: do what the role does, as a player, and check the game answered."""
    me = "E2EBot"
    others = lambda pred, exclude=(): bot_with(rcon, pred, exclude)
    if role == "VILLAGER":
        if not wait_phase("DAY_VOTE", 90): return failures.append("VILLAGER: no day vote")
        time.sleep(3)
        if not click_until(rcon, others(NOT_WOLF), f"{me}[VILLAGER] selects"):
            failures.append("VILLAGER: the vote was not recorded")
        if not click_until(rcon, "ability:skip", "presses SKIP"):
            failures.append("VILLAGER: skip did not work")
    elif role in ("WEREWOLF", "ALPHA_WOLF", "WOLF_CUB"):
        if not wait_phase("NIGHT", 60): return failures.append(f"{role}: no night")
        time.sleep(3)
        if not click_until(rcon, others(NOT_WOLF), f"{me}[{role}] selects"):
            failures.append(f"{role}: the hunt vote was not recorded")
    elif role == "INFECTOR":
        if not wait_phase("NIGHT", 60): return failures.append("INFECTOR: no night")
        time.sleep(3)
        if not click_until(rcon, "ability:infect", "will infect tonight"):
            failures.append("INFECTOR: infect was refused")
        click_until(rcon, others(NOT_WOLF), f"{me}[INFECTOR] selects")
        if not wait_journal("turns into a werewolf", 40):
            failures.append("INFECTOR: nobody was infected")
    elif role == "SEER":
        if not wait_phase("SEER", 60): return failures.append("SEER: no seer phase")
        time.sleep(3)
        if not click_until(rcon, others(lambda r: True), "Your vision"):
            failures.append("SEER: no vision")
    elif role == "WITCH":
        if not wait_phase("WITCH", 90): return failures.append("WITCH: no witch phase")
        time.sleep(3)
        victim_ok = click_until(rcon, "ability:heal", "saves")
        if not victim_ok: failures.append("WITCH: the life potion did not work")
        rcon.command("ww click E2EBot " + others(NOT_WOLF))
        if not click_until(rcon, "ability:poison", "poisons"):
            failures.append("WITCH: the death potion did not work")
    elif role == "HUNTER":
        if not wait_phase("DAY_VOTE", 90): return failures.append("HUNTER: no day vote")
        time.sleep(3)
        for n in range(1, 12):
            rcon.command(f"ww act bot{n} select {me}")
        rcon.command("ww phase skip")
        if not wait_phase("HUNTER_SHOT", 20): return failures.append("HUNTER: no last shot")
        time.sleep(3)
        if not click_until(rcon, others(lambda r: True), f"{me}[HUNTER]\u2020 picks"):
            failures.append("HUNTER: the target was not picked")
        if not click_until(rcon, "ability:shoot", "shot by the hunter"):
            failures.append("HUNTER: the shot was not fired")
    elif role == "CUPID":
        if not wait_phase("CUPID", 60): return failures.append("CUPID: no cupid phase")
        time.sleep(3)
        a = others(lambda r: True)
        b = others(lambda r: True, exclude=("[Bot] Guest " + a[3:],))
        rcon.command("ww click E2EBot " + a)
        rcon.command("ww click E2EBot " + b)
        if not click_until(rcon, "ability:bind", "are bound by"):
            failures.append("CUPID: the lovers were not bound")
    elif role == "GUARD":
        if not wait_phase("GUARD", 60): return failures.append("GUARD: no guard phase")
        time.sleep(3)
        if not click_until(rcon, others(lambda r: True), "protects"):
            failures.append("GUARD: no protection")
    elif role == "SCAPEGOAT":
        if not wait_phase("DAY_VOTE", 90): return failures.append("SCAPEGOAT: no day vote")
        time.sleep(3)
        living = peek(rcon, alive_only=True)
        pool = [n for n in living if n.startswith("[Bot] Guest ") and NOT_WOLF(living[n])]
        x, y = ["bot" + n.rsplit(" ", 1)[1] for n in pool[:2]]
        voters = [n for n in range(1, 12) if f"bot{n}" not in (x, y) and f"[Bot] Guest {n}" in living]
        for i, n in enumerate(voters):
            rcon.command(f"ww act bot{n} select {x if i % 2 == 0 else y}")
        rcon.command(f"ww act {x} select {y}")
        rcon.command(f"ww act {y} select {x}")
        rcon.command("ww phase skip")
        if not wait_journal("takes the blame", 15):
            failures.append("SCAPEGOAT: the tied vote did not fall on the scapegoat")
    elif role == "BEAR_TAMER":
        if not wait_phase("DISCUSSION", 90): return failures.append("BEAR_TAMER: no day")
        time.sleep(2)
    elif role in ULTIMATE:
        ultimate_checks(role, rcon, failures, tools, shots)
    elif role == "VOTE_UX":
        if not wait_phase("DAY_VOTE", 90): return failures.append("VOTE_UX: no day vote")
        time.sleep(3)
        for n in (1, 2, 3):
            rcon.command(f"ww act bot{n} select {me}")
        target = others(NOT_WOLF)
        if not click_until(rcon, target, f"{me}[VILLAGER] selects"):
            failures.append("VOTE_UX: the human's vote was not recorded")
        time.sleep(2)
        rcon.command("execute as E2EBot at @s facing entity @e[type=minecraft:mannequin,name=\"[Bot] Guest "
                     + target[3:] + "\",limit=1] eyes run tp @s ~ ~ ~ ~ ~")
        time.sleep(2)
        tags = rcon.command("execute if entity @e[type=minecraft:text_display]")
        frames = rcon.command("execute if entity @e[type=minecraft:block_display]")
        say(f"vote markers: counters -> {tags.strip()}; frames -> {frames.strip()}")
        if "passed" not in tags:
            failures.append("VOTE_UX: no vote counter was spawned")
        if "passed" not in frames:
            failures.append("VOTE_UX: no glowing frame was spawned")
        shot(tools, shots / "vote-ux.png")
    time.sleep(2)
    shot(tools, shots / f"role-{role.lower()}.png")


def roles_scenario(ctx, only):
    """Every role, one game each: the human holds the role and plays it through real controls."""
    tools, shots, client_log, failures, server = (ctx[k] for k in
                                                  ("tools", "shots", "client_log", "failures", "server"))
    for _ in range(60):
        try:
            state = api("GET", "/arenas")
            break
        except Exception:
            time.sleep(2)
    else:
        failures.append("the plugin's private API never answered")
        return
    me = next(p["uuid"] for p in state["onlinePlayers"] if p["name"] == "E2EBot")
    rcon = Rcon()
    wanted = [r for r in ROLE_CASES if not only or r in only.split(",")]
    for role in wanted:
        say(f"--- {role}")
        cast = ", ".join(f"{k.lower()}:{v}" for k, v in ROLE_CASES[role].items())
        if role not in ROLE_CASES[role] and role not in FORCED:
            cast += f" {role.lower()}:1" if role not in ("VILLAGER",) else ""
        out = rcon.command("ww cast set " + cast.replace(", ", " "))
        rcon.command("ww force clear")
        rcon.command(f"ww force E2EBot {FORCED.get(role, role)}")
        api("POST", "/arenas/action", {"action": "arena-practice-start", "arenaId": "e2e", "uuid": me, "botCount": 11})
        if not wait_phase({"CUPID", "SEER", "NIGHT", "GUARD", "DISCUSSION"} | NIGHT, 40):
            failures.append(f"{role}: the game did not start ({out.strip()[:120]})")
            continue
        roles = peek(rcon)
        # A drunk's card is swapped for his real role at the deal: peek shows the real one.
        if roles.get("E2EBot") != FORCED.get(role, role) and role != "DRUNK":
            failures.append(f"{role}: forced role missing, the human is {roles.get('E2EBot')}")
        time.sleep(4)
        shot(tools, shots / f"hud-{role.lower()}.png")
        ok, where = on_platform(rcon)
        print(f"[e2e] {role}: seated at {where}")
        if not ok:
            failures.append(f"{role}: the player is not on the sky platform: {where}")
        try:
            role_checks(role, rcon, roles, failures, tools, shots)
        except Exception as error:  # a broken step must not hide the other roles
            failures.append(f"{role}: {error}")
        api("POST", "/arenas/action", {"action": "arena-stop", "arenaId": "e2e"})
        if wait_phase("WAITING", 40) is None:
            failures.append(f"{role}: the arena did not come back to WAITING")
            api("POST", "/arenas/action", {"action": "arena-leave", "arenaId": "e2e", "uuid": me})
            wait_phase("WAITING", 20)
        say(f"{role}: done")
    console = (server / "console.log").read_text(errors="replace")
    for line in console.splitlines():
        if ("win.vampyuyu" in line or "NexusArena" in line) and (
                "Exception" in line or "ERROR" in line or "SEVERE" in line):
            failures.append("server: " + line.strip()[:200])
    for needle in FATAL:
        if needle in client_log.read_text(errors="replace"):
            failures.append(f"client log contains '{needle}'")


DEBUG_HUDS = ["night-seer", "day-day_vote-2", "role-witch", "danger-4"]


def showcase_scenario(ctx):
    """Screenshots of the controls: a practice game, then aim at a bot, left-click it (the outline), a far bot,
    a panorama of the village and the role card. Look at the PNGs in --shots."""
    tools, shots, env, failures, server = (ctx[k] for k in ("tools", "shots", "env", "failures", "server"))
    for _ in range(60):
        try:
            state = api("GET", "/arenas")
            break
        except Exception:
            time.sleep(2)
    else:
        failures.append("the plugin's private API never answered")
        return
    me = next(p["uuid"] for p in state["onlinePlayers"] if p["name"] == "E2EBot")
    rcon = Rcon()
    rcon.command("time set noon")
    rcon.command("tp E2EBot 12.5 -60 12.5")
    api("POST", "/arenas/action", {"action": "arena-practice-start", "arenaId": "e2e", "uuid": me, "botCount": 11})
    xdo = tools["xdotool"]
    # The role card (slot 9): open the inventory once the opening presentation is over and hover it.
    time.sleep(14)
    subprocess.run([xdo, "mousemove", "640", "360", "click", "1"], env=env)  # grab the mouse
    time.sleep(1)
    subprocess.run([xdo, "key", "e"], env=env)
    time.sleep(1.5)
    subprocess.run([xdo, "mousemove", "783", "494"], env=env)
    time.sleep(1.5)
    shot(tools, shots / "sc-07-role-card-tooltip.png")
    subprocess.run([xdo, "key", "Escape"], env=env)
    for _ in range(120):
        if api("GET", "/arenas")["arenas"][0]["phase"] == "DAY_VOTE":
            break
        time.sleep(2)
    else:
        failures.append("the game never reached the day vote")
        return
    time.sleep(4)

    def face(which):
        rcon.command(f"execute as E2EBot at @s anchored eyes run rotate @s facing entity @e[type=minecraft:mannequin,sort={which},limit=1] eyes")
        time.sleep(1)

    def click(grab=False):
        # Moving the pointer turns the camera once the window has grabbed the mouse: only the first click moves it.
        if grab:
            subprocess.run([xdo, "mousemove", "640", "360"], env=env)
        subprocess.run([xdo, "click", "1"], env=env)
        time.sleep(2)

    shot(tools, shots / "sc-00-day-vote.png")
    for q in ("data get entity E2EBot Pos", "data get entity E2EBot Rotation",
              "data get entity @e[type=minecraft:mannequin,sort=nearest,limit=1] Pos",
              "data get entity @e[type=minecraft:mannequin,sort=furthest,limit=1] Pos"):
        say(q + " -> " + rcon.command(q).strip()[:120])
    face("nearest")
    say("after face nearest: " + rcon.command("data get entity E2EBot Rotation").strip())
    shot(tools, shots / "sc-01-aiming-nearest.png")
    click()
    for q in ("execute if entity @e[type=minecraft:mannequin]",
              "execute if entity @e[type=minecraft:mannequin,nbt={Glowing:1b}]",
              "execute if entity @e[type=minecraft:mannequin,nbt={Invisible:1b}]",
              "execute if entity @e[type=minecraft:mannequin,nbt={Glowing:1b,Invisible:1b}]"):
        say(q[-70:] + " -> " + rcon.command(q).strip()[:140])
    shot(tools, shots / "sc-02-after-left-click-nearest.png")
    for q in ("data get entity @e[type=minecraft:mannequin,nbt={Glowing:1b},limit=1] Pos",
              "execute as @e[type=minecraft:mannequin,nbt={Glowing:1b},limit=1] at @s run data get entity @e[type=minecraft:mannequin,nbt=!{Glowing:1b},sort=nearest,limit=1] Pos"):
        say("ghost: " + rcon.command(q).strip()[:200])
    rcon.command("execute as E2EBot at @s anchored eyes run rotate @s facing entity @e[type=minecraft:mannequin,nbt={Glowing:1b},limit=1] eyes")
    time.sleep(1.5)
    shot(tools, shots / "sc-02b-facing-the-outline.png")
    face("furthest")
    say("after face furthest: " + rcon.command("data get entity E2EBot Rotation").strip())
    shot(tools, shots / "sc-03-aiming-far.png")
    click()
    shot(tools, shots / "sc-04-after-left-click-far.png")
    for i, yaw in enumerate((0, 90, 180, 270)):
        rcon.command(f"rotate E2EBot {yaw} -8")
        time.sleep(1)
        shot(tools, shots / f"sc-05-panorama-{yaw}.png")
    console = (server / "console.log").read_text(errors="replace")
    for line in console.splitlines():
        if ("win.vampyuyu" in line or "NexusArena" in line) and ("Exception" in line or "SEVERE" in line):
            failures.append("server: " + line.strip()[:200])


def offline_uuid(name):
    """The UUID an offline-mode server gives a player (Java's nameUUIDFromBytes of "OfflinePlayer:<name>")."""
    raw = bytearray(hashlib.md5(("OfflinePlayer:" + name).encode()).digest())
    raw[6] = raw[6] & 0x0F | 0x30
    raw[8] = raw[8] & 0x3F | 0x80
    return str(uuid.UUID(bytes=bytes(raw)))


def worlds_scenario(ctx):
    """/menu with the pack: the worlds on top, the Werewolf and ValoBlock cards under them. Hovers both game cards,
    then clicks Werewolf, which joins the e2e arena (WEREWOLF_ARENA points at it for this run)."""
    tools, env, shots, failures = (ctx[k] for k in ("tools", "env", "shots", "failures"))
    xdo = tools["xdotool"]

    def command(text):
        subprocess.run([xdo, "key", "slash"], env=env)
        time.sleep(.5)
        subprocess.run([xdo, "type", "--delay", "35", text], env=env)
        subprocess.run([xdo, "key", "Return"], env=env)
        time.sleep(3)

    # The 854x480 game window sits centred on the 1280x720 screen at GUI scale 2: the 176x222 container's top left
    # is at screen (464, 138), slot (row r, col c) at GUI (8 + 18c, 18 + 18r).
    def slot_xy(row, col):
        return str(464 + 2 * (16 + 18 * col)), str(138 + 2 * (26 + 18 * row))

    command("menu")
    shot(tools, shots / "worlds-1-menu.png")
    subprocess.run([xdo, "mousemove", *slot_xy(4, 1)], env=env)
    time.sleep(1.5)
    shot(tools, shots / "worlds-2-werewolf-card.png")
    subprocess.run([xdo, "mousemove", *slot_xy(4, 6)], env=env)
    time.sleep(1.5)
    shot(tools, shots / "worlds-3-valoblock-card.png")
    subprocess.run([xdo, "mousemove", *slot_xy(4, 1), "click", "1"], env=env)
    time.sleep(6)
    shot(tools, shots / "worlds-4-joined.png")
    state = api("GET", "/arenas")
    joined = [p["name"] for a in state["arenas"] if a["config"]["id"] == "e2e" for p in a["players"]]
    if "E2EBot" not in joined:
        failures.append(f"clicking the Werewolf card did not put the bot in the e2e arena (players: {joined})")
    if "Unhandled exception" in (ctx["server"] / "console.log").read_text(errors="replace"):
        failures.append("a command threw on the server: see .e2e/server/console.log")
    say("worlds: menu, both game cards and the Werewolf join checked")


# Camera views of the hubs scenario: (name, x, y, z, yaw, pitch) relative to the hub centre (WaitingHubs.HUB_X) and the
# players' standing height; all inside the hub's +-40 world border and the client's 4-chunk view.
HUB_VIEWS = [("close", 9, 7, 13, 145, 22), ("wide", 0.5, 16, 36, 180, 20), ("high", 30, 30, 30, 135, 35),
             ("back", 0.5, 8, -27, 0, 12), ("under", 22, -14, 26, 140, -18)]


def hubs_scenario(ctx):
    """The two built-in waiting hubs (WaitingHubs; SERVER_ID=creative provisions their arenas): joins the valoblock
    arena (the Staging Deck in vb_arena, fixed noon), then the werewolf one (the Moonlit Village in ww_sky, midnight for
    waiting players). A hub is built when its first player joins, so the join is the real check: the spawn shot keeps the
    HUD and hotbar, then a spectator camera with the HUD hidden takes the wide views. E2E_HUBS=vb or ww shortens it."""
    tools, env, shots, failures, server = (ctx[k] for k in ("tools", "env", "shots", "failures", "server"))
    xdo = tools["xdotool"]
    me = offline_uuid("E2EBot")
    for _ in range(60):
        try:
            api("GET", "/arenas")
            break
        except Exception:
            time.sleep(2)
    else:
        failures.append("the plugin's private API never answered")
        return
    rcon = Rcon()
    which = os.environ.get("E2E_HUBS", "vb,ww").split(",")
    hubs = [("vb", "valoblock", "vb_arena", 65, "day"), ("ww", "werewolf", "ww_sky", 100, "night")]
    for key, arena, world, floor, label in hubs:
        if key not in which:
            continue
        try:
            api("POST", "/arenas/action", {"action": "arena-join", "arenaId": arena, "uuid": me})
        except Exception as e:
            failures.append(f"hubs: joining {arena} failed: {e}")
            continue
        time.sleep(10)
        out = re.sub("\u00a7.", "", rcon.command("data get entity E2EBot Pos"))
        if f"{-512 + 1}" not in out and "-511" not in out:
            failures.append(f"hubs: E2EBot is not at the {arena} hub after the join: {out.strip()[:120]}")
        if key == "vb":
            subprocess.run([xdo, "key", "Escape"], env=env)  # the ValoBlock map vote opens on join
            time.sleep(2)
        shot(tools, shots / f"hub-{key}-{label}-spawn.png")
        # The arena keeps its members in place: leave it (the hub stays built, its models stay up while anyone is
        # near), then fly a spectator camera at the hub's own time of day.
        api("POST", "/arenas/action", {"action": "arena-leave", "arenaId": arena, "uuid": me})
        time.sleep(6)
        rcon.command(f"execute in minecraft:{world} run time set " + ("18000" if label == "night" else "6000"))
        subprocess.run([xdo, "key", "F1"], env=env)
        rcon.command("gamemode spectator E2EBot")
        for name, dx, dy, dz, yaw, pitch in HUB_VIEWS:
            out = rcon.command(f"execute in minecraft:{world} run tp E2EBot {-512 + dx} {floor + dy} {dz} {yaw} {pitch}")
            if "Teleported" not in out:
                failures.append(f"hubs: tp into {world} failed: {out.strip()[:120]}")
            time.sleep(5)
            shot(tools, shots / f"hub-{key}-{label}-{name}.png")
        subprocess.run([xdo, "key", "F1"], env=env)
        rcon.command("gamemode creative E2EBot")
        rcon.command("execute in minecraft:overworld run tp E2EBot 0 -60 0")
        time.sleep(4)
    console = (server / "console.log").read_text(errors="replace")
    for hub in (["ValoBlock"] if "vb" in which else []) + (["Werewolf"] if "ww" in which else []):
        if f"Built the {hub} waiting hub" not in console:
            failures.append(f"hubs: the {hub} hub was not built on join (no 'Built the {hub} waiting hub' line)")
    for line in console.splitlines():
        if ("win.vampyuyu" in line or "NexusArena" in line) and ("Exception" in line or "SEVERE" in line):
            failures.append("server: " + line.strip()[:200])
    say("hubs: " + ", ".join(which) + " captured")


def staffchat_scenario(ctx):
    """The private staff<->player channel through the network plugin's management API, with a real client:
    staff write to the bot, the bot answers by typing in the chat box, and /exitchat leaves."""
    tools, env, shots, failures, server = (ctx[k] for k in ("tools", "env", "shots", "failures", "server"))
    xdo = tools["xdotool"]
    me = offline_uuid("E2EBot")

    def chat(**body):
        request = urllib.request.Request("http://127.0.0.1:8084/chat", method="POST",
                                         data=json.dumps({"uuid": me, **body}).encode(),
                                         headers={"Authorization": f"Bearer {API_TOKEN}", "Content-Type": "application/json"})
        with urllib.request.urlopen(request, timeout=20) as response:
            return json.load(response)

    def type_in_chat(text, key="t"):
        subprocess.run([xdo, "key", key], env=env)
        time.sleep(1)
        subprocess.run([xdo, "type", "--delay", "60", text], env=env)
        time.sleep(0.5)
        subprocess.run([xdo, "key", "Return"], env=env)

    for _ in range(60):
        try:
            chat(action="read")
            break
        except Exception:
            time.sleep(2)
    else:
        failures.append("the management API never answered")
        return
    state = chat(action="open", staff="Admin")
    if not state["open"]:
        failures.append(f"open did not open the channel: {state}")
    chat(action="send", staff="Admin", message="Hello from staff: this is private.")
    time.sleep(1.5)
    shot(tools, shots / "staffchat-1-from-staff.png")
    public = "the bot said this out loud"
    type_in_chat("hi staff, only you can read this")
    time.sleep(2)
    shot(tools, shots / "staffchat-2-bot-types.png")
    state = chat(action="read", since=0)
    said = [m["text"] for m in state["messages"] if m["from"] == "player"]
    if said != ["hi staff, only you can read this"]:
        failures.append(f"the player's chat did not reach the channel: {state['messages']}")
    if "hi staff, only you can read this" in (server / "console.log").read_text(errors="replace").replace("\n", " ").split("Done (")[-1].replace("[Staff]", "").replace("[You → Staff]", ""):
        # The line may only be there as the plugin's own echo, which the console does not print.
        failures.append("the private message leaked to the public chat log")
    type_in_chat("exitchat", key="slash")
    time.sleep(2)
    shot(tools, shots / "staffchat-3-left.png")
    state = chat(action="read", since=0)
    if state["open"] or not any("left the channel" in m["text"] for m in state["messages"] if m["from"] == "system"):
        failures.append(f"/exitchat did not close the channel: {state}")
    type_in_chat(public)
    time.sleep(1.5)
    if public not in (server / "console.log").read_text(errors="replace"):
        failures.append("after leaving, the bot's chat is not public again")
    shot(tools, shots / "staffchat-4-public-again.png")
    say("staffchat: " + str(len(state["messages"])) + " messages in the channel")


def debughud_scenario(ctx):
    """/ww debug hud: synthetic HUD states drawn for the bot with no game running."""
    tools, shots, client_log, failures, server = (ctx[k] for k in
                                                  ("tools", "shots", "client_log", "failures", "server"))
    for _ in range(60):
        try:
            api("GET", "/arenas")
            break
        except Exception:
            time.sleep(2)
    else:
        failures.append("the plugin's private API never answered")
        return
    rcon = Rcon()
    out = re.sub("\u00a7.", "", rcon.command("ww debug hud list badge"))
    if "badge-lover" not in out:
        failures.append("debug hud list: " + out.strip()[:120])
    for sid in DEBUG_HUDS:
        out = re.sub("\u00a7.", "", rcon.command(f"ww debug hud {sid} E2EBot"))
        say(f"debug hud {sid}: " + out.strip().replace("\n", " | ")[:160])
        if "Unknown" in out or "not active" in out or "offline" in out:
            failures.append(f"debug hud {sid}: {out.strip()[:120]}")
        time.sleep(3)
        shot(tools, shots / f"debughud-{sid}.png")
        rcon.command("ww debug hud stop E2EBot")
    for text in ("ww debug ambience night", "ww debug sky 12", "ww phase skip"):
        say(text + ": " + re.sub("\u00a7.", "", rcon.command(text)).strip().replace("\n", " | ")[:200])
    console = (server / "console.log").read_text(errors="replace")
    for line in console.splitlines():
        if ("win.vampyuyu" in line or "NexusArena" in line) and (
                "Exception" in line or "ERROR" in line or "SEVERE" in line):
            failures.append("server: " + line.strip()[:200])
    for needle in FATAL:
        if needle in client_log.read_text(errors="replace"):
            failures.append(f"client log contains '{needle}'")


def tooltip_scenario(ctx):
    """Online inventory tooltips: enchantments, lore, custom data and durability come back as lines."""
    failures, server = ctx["failures"], ctx["server"]
    rcon = Rcon()
    me = None
    for _ in range(60):
        try:
            me = next(p["uuid"] for p in api("GET", "/arenas")["onlinePlayers"] if p["name"] == "E2EBot")
            break
        except Exception:
            time.sleep(2)
    if not me:
        failures.append("no online player")
        return
    gives = [
        'give E2EBot minecraft:diamond_sword[minecraft:enchantments={"minecraft:sharpness":5,"minecraft:unbreaking":3,"minecraft:mending":1},minecraft:custom_name=\'{"text":"Vampyuyu Blade","color":"gold"}\',minecraft:lore=[\'{"text":"Forged in the moonlight","color":"dark_purple"}\'],minecraft:custom_data={owner:"jeremy",tier:3},minecraft:damage=120]',
        'give E2EBot minecraft:enchanted_book[minecraft:stored_enchantments={"minecraft:protection":4}]',
        'give E2EBot minecraft:apple 5',
    ]
    for g in gives:
        out = rcon.command(g)
        say(out.strip()[:120])
    time.sleep(1)
    token = {"Authorization": f"Bearer {API_TOKEN}"}
    request = urllib.request.Request(f"http://127.0.0.1:8084/players/{me}/inventory", headers=token)
    with urllib.request.urlopen(request, timeout=20) as response:
        view = json.loads(response.read().decode())
    (ctx["shots"] / "inventory.json").write_text(json.dumps(view, indent=1))
    slots = {s["slot"]: s for s in view["slots"] if s["amount"]}
    sword = next((s for s in slots.values() if s["material"] == "minecraft:diamond_sword"), None)
    if not sword:
        failures.append("the sword is not in the inventory view")
        return
    lines = [l["text"] for l in sword.get("lines", [])]
    say("sword lines: " + " | ".join(lines))
    for want in ("Sharpness V", "Unbreaking III", "Mending", "Forged in the moonlight", "Durability: 1441 / 1561"):
        if want not in lines:
            failures.append(f"tooltip line missing: {want} (got {lines})")
    if not any("owner" in l for l in lines):
        failures.append("custom data missing from the tooltip")
    if sword.get("nameColor") != "#ffaa00":
        failures.append(f"custom name colour: {sword.get('nameColor')}")
    book = next((s for s in slots.values() if s["material"] == "minecraft:enchanted_book"), None)
    if not book or "Protection IV" not in [l["text"] for l in book.get("lines", [])]:
        failures.append("stored enchantment of the book missing")


# Camera spots around a sky platform centre: dx, dy, dz, yaw, pitch (dy from the platform floor).
PLATFORM_VIEWS = [("wide", -38, 24, 38, 225, 24), ("far", 0, 10, 62, 180, 6), ("low", 0, 3, 27, 180, 2),
                  ("under", 30, -22, 30, 135, -18), ("high", 0, 48, 1, 180, 90),
                  # eye level from a seat on the +z side, across the fire (a seated player's eye is about 1.6 up)
                  ("seat", 0, 2.1, 3, 180, -8), ("seat-back", 0, 2.1, -3, 0, -8),
                  # the display models: the moon over the belfry, the belfry's bell and vane, the mill's sails
                  ("moon", 0, 6, 6, 180, -32), ("belfry", 0, 18, 2, 180, -6), ("mill", 0, 8, 0, 60, -2)]


def platform_views(rcon, tools, shots, players=12, prefix="view"):
    """E2E_WW_VIEWS=1: /ww debug decor builds a platform with no game on it, the player looks at it from around, at
    noon and at midnight, then it is cleared. A player in a game cannot be teleported (the arena cancels it)."""
    out = rcon.command(f"ww debug decor {players}")
    for _ in range(30):
        log = (WORK / "server" / "logs" / "latest.log").read_text(errors="replace")
        found = re.findall(rf"Showcase platform for {players} players built: centre (-?\d+) (-?\d+) (-?\d+)", log)
        if found:
            break
        time.sleep(1)
    else:
        say(f"decor showcase did not build: {out!r}")
        return
    cx, cy, cz = (int(v) + 0.5 for v in found[-1])
    state = player_state(rcon)
    mode = {"0": "survival", "1": "creative", "2": "adventure", "3": "spectator"}.get(state["mode"], "creative")
    rcon.command("gamemode spectator E2EBot")
    time.sleep(2)
    say(f"decor models around the {players}-player platform: "
        + rcon.command("execute in minecraft:ww_sky if entity @e[tag=ww_decor_model]").strip())
    for kind, clock in (("day", "noon"), ("night", "midnight")):
        rcon.command(f"execute in minecraft:ww_sky run time set {clock}")
        for name, dx, dy, dz, yaw, pitch in PLATFORM_VIEWS:
            rcon.command(f"execute in minecraft:ww_sky run tp E2EBot {cx + dx} {cy - 0.5 + dy} {cz + dz} {yaw} {pitch}")
            time.sleep(3)
            shot(tools, shots / f"{prefix}-{kind}-{name}.png")
    rcon.command("ww debug decor clear")
    x0, y0, z0 = state["pos"]
    rcon.command(f"execute in minecraft:overworld run tp E2EBot {x0} {y0} {z0}")
    rcon.command(f"gamemode {mode} E2EBot")
    rcon.command("time set noon")
    time.sleep(3)


def werewolf_scenario(ctx):
    """A whole practice game (human idle, seven bots) driven through the plugin's private API."""
    tools, shots, client_log, failures, server = (ctx[k] for k in
                                                  ("tools", "shots", "client_log", "failures", "server"))
    say("plugin API: waiting for the arena")
    for _ in range(60):
        try:
            state = api("GET", "/arenas")
            break
        except Exception:
            time.sleep(2)
    else:
        failures.append("the plugin's private API never answered")
        return
    if [a["config"]["id"] for a in state["arenas"]] != ["e2e"]:
        failures.append(f"arena e2e not loaded: {state['arenas']}")
        return
    me = next(p["uuid"] for p in state["onlinePlayers"] if p["name"] == "E2EBot")
    rcon = Rcon()
    for rule in filter(None, ctx.get("rules", "").split(",")):
        key, _, value = rule.partition("=")
        say(f"/ww rules {key} {value}: " + rcon.command(f"ww rules {key} {value}").strip())
    time.sleep(1)
    rcon.command("time set noon")
    time.sleep(2)
    shot(tools, shots / "ww-00-lobby.png")
    base = brightness(tools, shots / "ww-00-lobby.png")
    if os.environ.get("E2E_WW_ROLE"):  # e.g. WEREWOLF: the human is awake at night, so the night shots show the decor
        say(rcon.command(f"ww debug force E2EBot {os.environ['E2E_WW_ROLE']}").strip())
    if os.environ.get("E2E_WW_VIEWS"):
        # E2E_WW_VIEWS_PLAYERS=4,12,45: one set of views per table size (view-<n>-day-wide.png...); default 12.
        sizes = os.environ.get("E2E_WW_VIEWS_PLAYERS", "12").split(",")
        for n in sizes:
            platform_views(rcon, tools, shots, int(n), "view" if len(sizes) == 1 else f"view-{n}")
        if os.environ.get("E2E_WW_VIEWS") == "only":  # just the decor, no game
            return
    rcon.command("tp E2EBot 12.5 -60 12.5")
    time.sleep(1)
    before = player_state(rcon)
    api("POST", "/arenas/action", {"action": "arena-practice-start", "arenaId": "e2e", "uuid": me,
                                   "botCount": 11})
    say("practice game started (1 human + 11 bots)")
    # Nominated voting plays a longer day: every nomination adds a defense and a trial.
    seen, last, deadline = [], None, time.time() + (1500 if ctx.get("rules") else 600)
    dark = {}
    while time.time() < deadline:
        row = api("GET", "/arenas")["arenas"][0]
        phase = row["phase"]
        if phase != last:
            last = phase
            seen.append(phase)
            time.sleep(3)  # let the HUD, title and effect settle
            name = f"ww-{len(seen):02d}-{phase.lower()}.png"
            shot(tools, shots / name)
            dark.setdefault(phase, []).append(brightness(tools, shots / name))
            say(f"phase {phase}: brightness {dark[phase][-1]:.3f}")
            # E2E_WW_VIEWS=1: aerial views of the sky platform and its village, once by day and once by night.
        if phase in ("FINISHED", "WAITING") and len(seen) > 1:
            break
        time.sleep(2)
    else:
        failures.append(f"the game did not finish in 10 minutes (phases seen: {seen})")
    time.sleep(8)  # the verdict and the finale
    shot(tools, shots / "ww-99-end.png")
    logs = api("GET", "/arenas/logs").get("logs", [])
    if not logs:
        failures.append("no werewolf journal was written")
    else:
        name = logs[0]["name"] if isinstance(logs[0], dict) else logs[0]
        journal = api("GET", f"/arenas/logs/{name}")
        (shots / "journal.txt").write_text(journal)
        if "Winner:" not in journal and "time limit" not in journal:
            failures.append("the journal has no outcome")
    nominated = "voting=nominate" in ctx.get("rules", "")
    expected = {"DISCUSSION"} | (set() if nominated else {"DAY_VOTE"})
    if nominated and not {"DEFENSE", "TRIAL"} & set(seen):
        say("nominated voting: no nomination was seconded in this game (not a failure)")
    if not expected <= set(seen):
        failures.append(f"day phases missing from {seen}")
    if not NIGHT & set(seen):
        failures.append(f"no night phase in {seen}")
    nights = [b for ph in NIGHT for b in dark.get(ph, [])]
    days = [b for ph in ("DISCUSSION", "DAY_VOTE") for b in dark.get(ph, [])]
    if nights and days:
        say(f"brightness: lobby {base:.3f} day {max(days):.3f} night {min(nights):.3f}")
        if not min(nights) < max(days) * 0.8:
            failures.append("nights are not visibly darker than days (shader driven by the plugin)")
    time.sleep(5)
    after = player_state(rcon)
    if not same_place(after, before):
        failures.append(f"the player was not restored after the game: {before} -> {after}")
    # Second game: the human walks out in the middle of a night.
    api("POST", "/arenas/action", {"action": "arena-practice-start", "arenaId": "e2e", "uuid": me,
                                   "botCount": 11})
    for _ in range(30):
        if api("GET", "/arenas")["arenas"][0]["phase"] not in ("WAITING", "FINISHED"):
            break
        time.sleep(1)
    time.sleep(6)
    inside = player_state(rcon)
    say(f"inside a game: {inside}")
    if "ADVENTURE" not in inside["mode"] and "2" not in inside["mode"]:
        failures.append(f"the player is not in adventure mode during the game: {inside['mode']}")
    ok, where = on_platform(rcon)
    say(f"seated at {where}")
    if not ok:
        failures.append(f"the player is not on the sky platform during the game: {where}")
    shot(tools, shots / "ww-leave-before.png")
    # Through the private API: the typed command is not reliable while a role menu is open.
    api("POST", "/arenas/action", {"action": "arena-leave", "arenaId": "e2e", "uuid": me})
    for _ in range(30):
        if api("GET", "/arenas")["arenas"][0]["phase"] == "WAITING":
            break
        time.sleep(1)
    else:
        failures.append("the arena did not return to WAITING after the only human left")
    time.sleep(5)
    left = player_state(rcon)
    if not same_place(left, before):
        failures.append(f"the player was not restored after leaving: {before} -> {left}")
    say("leave mid-game restored the player" if same_place(left, before) else "leave mid-game FAILED")
    console = (server / "console.log").read_text(errors="replace")
    for line in console.splitlines():
        if ("win.vampyuyu" in line or "NexusArena" in line) and (
                "Exception" in line or "ERROR" in line or "SEVERE" in line):
            failures.append("server: " + line.strip()[:200])
    for needle in FATAL:
        if needle in client_log.read_text(errors="replace"):
            failures.append(f"client log contains '{needle}'")
    # The ambient bed follows the phase: the plugin logs "[ambience] bed <player> <phase> -> <sound>".
    beds = [l.split("[ambience] bed ", 1)[1] for l in console.splitlines() if "[ambience] bed E2EBot" in l]
    sounds = {b.rsplit("-> ", 1)[1].split()[0] for b in beds}
    say(f"ambience: {len(beds)} bed changes, sounds {sorted(sounds)}")
    if len(sounds) < 2:
        failures.append(f"the ambient bed never followed the phases: {beds}")
    say(f"phases: {' > '.join(seen)}")



def vb(rcon, command):
    """A /vb command over RCON, colour codes stripped."""
    return re.sub("\u00a7.", "", rcon.command("vb " + command)).strip()


def valoblock_extras(ctx, rcon, map_id, failures):
    """Combat-phase captures of the valoblock scenario: the held 3D gun (idle, firing, reloading), the combat HUD,
    one Realm mechanic of the map (gate, root drop, wind pad) and an overview from a high point."""
    tools, shots, env = ctx["tools"], ctx["shots"], ctx["env"]
    xdo = tools["xdotool"]
    cell = {"stormbreak": 5}.get(map_id, 4)

    def at(col, row):  # cell centre in blocks (slot 0 of the vb_arena world)
        return (col + 0.5) * cell, (row + 0.5) * cell

    def tp(x, y, z, yaw=0, pitch=0):
        say("tp: " + vb(rcon, f"debug tp E2EBot {x} {y} {z} {yaw} {pitch}"))
        time.sleep(1)

    def key(*k):
        subprocess.run([xdo, *k], env=env)

    say("inventory: " + rcon.command("data get entity E2EBot Inventory")[:900])
    rcon.command("gamemode adventure E2EBot")
    say(vb(rcon, "debug god E2EBot on"))  # the bots would kill the human within seconds
    sx, sz = {"stormbreak": (62.5, 142.5)}.get(map_id, (50.0, 114.0))
    # Realm mechanic
    if map_id == "hourfall":
        gates = [at(12, 11), at(17, 11), at(12, 18), at(17, 18)]
        gx, gz = gates[0]
        seen = set()
        t0 = time.time()
        tp(gx, 65, gz + 6, 180, 5)
        # The Time Tide swaps every 30 s of the combat clock (server ticks), and a round is only about 90 s long: the loop may start
        # anywhere in it, so it watches 100 s and ends early once both states of the north gate were seen. The two gates of a
        # pair must always agree (a split pair means a hole in a wall or a gate that was held open), at every height of the wall.
        while time.time() - t0 < 100 and len(seen) < 2:
            states = ["AIR" if "AIR" in vb(rcon, f"debug block {int(x)} 66 {int(z)}") else "WALL" for x, z in gates]
            say(f"t+{time.time() - t0:.0f}s gates (12,11) (17,11) (12,18) (17,18): {states} | {vb(rcon, 'status')[:70]} | {vb(rcon, 'debug where E2EBot')[:60]}")
            for pair, (a_, b_) in enumerate([(0, 1), (2, 3)]):
                if states[a_] != states[b_]:
                    time.sleep(1)  # a swap may have landed between the two reads: read the pair again before blaming the map
                    again = ["AIR" if "AIR" in vb(rcon, f"debug block {int(gates[g][0])} 66 {int(gates[g][1])}") else "WALL" for g in (a_, b_)]
                    if again[0] == again[1]:
                        continue
                    heights = [["AIR" if "AIR" in vb(rcon, f"debug block {int(gates[g][0])} {y} {int(gates[g][1])}") else "WALL" for y in (65, 66, 67)] for g in (a_, b_)]
                    failures.append(f"hourfall: the gates of the {'north' if pair == 0 else 'south'} pair disagree: {states}, heights 65-67 of each: {heights}")
                    seen = {"open", "closed"}  # reported: stop watching
                    break
            if len(seen) == 2:
                break
            name = "open" if states[0] == "AIR" else "closed"
            if name not in seen:
                time.sleep(1.5)
                shot(tools, shots / f"vb-20-gate-{name}.png")
                seen.add(name)
            time.sleep(2)
        if len(seen) < 2:
            failures.append(f"hourfall: the gate (12,11) was only seen {seen} in 100 s: {vb(rcon, 'status')}")
    elif map_id == "rootvault":
        rx, rz = at(8, 7)
        tp(rx, 71.3, rz - 3, 0, 20)
        shot(tools, shots / "vb-20-rootdrop-branch.png")  # on the canopy branch, before the hole
        vb(rcon, f"debug tp E2EBot {rx} 71.3 {rz} 0 20")
        t0 = time.time()
        for i in range(8):
            time.sleep(0.25)
            say(f"drop t+{time.time() - t0:.1f}s: " + vb(rcon, "debug where E2EBot"))
            if i == 0:
                say("effects mid-fall: " + rcon.command("data get entity E2EBot active_effects").strip()[:300])
            if i in (1, 3, 5):
                shot(tools, shots / f"vb-20-rootdrop-{i}.png")
        say("effects: " + rcon.command("data get entity E2EBot active_effects").strip()[:300])
    elif map_id == "stormbreak":
        px, pz = at(12, 4)
        tp(px, 65, pz, 0, 20)
        time.sleep(1)
        shot(tools, shots / "vb-20-windpad-before.png")
        subprocess.run([xdo, "keydown", "shift"], env=env)
        for i in range(4):
            time.sleep(0.6)
            say(f"flight {i}: " + vb(rcon, "debug where E2EBot"))
            shot(tools, shots / f"vb-20-windpad-{i}.png")
        subprocess.run([xdo, "keyup", "shift"], env=env)
    # Gun in first person: look at a wall at the spawn, hotbar slot 1 then 2.
    for slot in ("1", "2", "3"):
        tp(sx, 65, sz, 180, 0)
        key("key", slot)
        time.sleep(2)
        shot(tools, shots / f"vb-10-gun-slot{slot}-idle.png")
    tp(sx, 65, sz, 180, 0)
    key("key", "2")
    time.sleep(1)
    key("mousemove", "427", "240")
    key("click", "3")  # right-click: fire
    shot(tools, shots / "vb-11-gun-firing.png")
    time.sleep(0.4)
    shot(tools, shots / "vb-11-gun-after-shot.png")
    time.sleep(1)
    key("key", "f")  # reload
    time.sleep(0.5)
    shot(tools, shots / "vb-12-gun-reloading.png")
    time.sleep(3)
    shot(tools, shots / "vb-13-hud-combat.png")
    say("where: " + vb(rcon, "debug where E2EBot"))
    if os.environ.get("E2E_VB_GALLERY"):
        # Every gun and the knife held (its item model, no plugin state): first person, then third person from behind
        # and from the front (F5 twice), hotbar slot 1 (empty in a match; slot 6 is rewritten by the abilities). E2E_VB_GALLERY=1 turns it on; E2E_VB_GALLERY=pip,gnat picks some.
        # A visual tool: waiting for each combat phase can eat whole rounds, so with many weapons the later respawn check may fail.
        data = json.loads((ROOT / "arena-plugin/src/main/resources/valoblock/weapons.json").read_text())["weapons"]
        pick = os.environ["E2E_VB_GALLERY"].split(",")
        for w in data:
            if w["texture"] == "ward" or (pick != ["1"] and w["id"] not in pick):
                continue
            t0 = time.time()
            while "Phase COMBAT" not in vb(rcon, "status") and time.time() - t0 < 60:
                time.sleep(1)  # the buy phase opens the Armory over the view
            tp(sx, 65, sz, 180, 0)
            rcon.command(f'item replace entity E2EBot hotbar.0 with {w["base"].lower() if ":" in w["base"] else "minecraft:" + w["base"].lower()}[item_model="vampyuyu:valoblock/{w["id"]}"' + (',charged_projectiles=[{id:"minecraft:arrow"}]' if w["base"] == "CROSSBOW" else "") + ']')  # charged like WeaponItems: the two-handed pose
            key("key", "1")
            time.sleep(1.5)
            shot(tools, shots / f"vb-40-{w['id']}-first.png")
            key("key", "F5")
            time.sleep(1.2)
            shot(tools, shots / f"vb-41-{w['id']}-back.png")
            key("key", "F5")
            time.sleep(1.2)
            shot(tools, shots / f"vb-42-{w['id']}-front.png")
            key("key", "F5")
            time.sleep(0.5)
    # Overview in combat: spectator, high above the south half, looking north.
    size = 150 if map_id == "stormbreak" else 120
    m = size / 2
    rcon.command("gamemode spectator E2EBot")
    tp(m, size * 0.7, size - 6, 180, 40)
    time.sleep(12)
    shot(tools, shots / "vb-31-overview-combat.png")
    tp(0, size * 0.7, m, -90, 45)
    time.sleep(12)
    shot(tools, shots / "vb-32-overview-side.png")
    rcon.command("gamemode adventure E2EBot")
    tp(sx, 65, sz, 180, 0)


def valoblock_items(ctx, rcon, failures):
    """--only items (Hourfall, the human is a god): what the player holds and wears. Round 1 combat: the hotbar with the
    four ability items, the tooltip of one, an ability used (cooldown), the ultimate once the orbs filled it. Then rounds are
    played (a bot squad wins, one then the other so the match does not end) until each of these has been seen: the Longwind
    sniper scoped (the gun is not drawn), the Bronzite rifle aimed, the Seed on a teammate bot's back from behind (buy phase,
    the bots stand still), the Seed carrier's HUD icon and hotbar item and the plant progress inside a site (rounds where
    the human got the Seed: 1 in 5 per round, only while the human's squad is the Seedbearers, the first 6 rounds)."""
    tools, shots, env = ctx["tools"], ctx["shots"], ctx["env"]
    xdo = tools["xdotool"]
    GUI, OX, OY = 2, 213, 120  # 854x480 game window centred in the 1280x720 Xvfb root: GUI scale 2
    WANT = {"scope", "rifle", "back", "seed", "plant", "ult", "cooldown", "tooltip"}
    SPAWN = (50.0, 65, 114.0)

    def key(*k):
        subprocess.run([xdo, *k], env=env)

    def tp(x, y, z, yaw=0, pitch=0):
        vb(rcon, f"debug tp E2EBot {x} {y} {z} {yaw} {pitch}")
        time.sleep(1.2)

    def inv():
        return rcon.command("data get entity E2EBot Inventory")

    def has(query):  # the whole Inventory dump is cut by RCON's 4 KB packets: ask for one thing
        return "following entity data" in rcon.command("data get entity E2EBot " + query)

    def carrying():
        return has('Inventory[{id:"minecraft:echo_shard"}].id')

    def ult_ready():
        return "_ready" in rcon.command('data get entity E2EBot Inventory[{Slot:6b}].components."minecraft:item_model"')

    def phase():
        return re.search(r"Phase (\w+)", vb(rcon, "status")).group(1)

    def wait_phase(name, seconds=40):
        t0 = time.time()
        while time.time() - t0 < seconds:
            if phase() == name:
                return True
            time.sleep(0.5)
        failures.append(f"items: the match never reached the {name} phase")
        return False

    def to_combat():
        """Whatever the round is doing (the bots fight, a round can end by itself), get to a running combat phase."""
        for _ in range(4):
            ph = phase()
            if ph in ("COMBAT", "PLANTED"):
                return True
            if ph == "BUY":
                vb(rcon, "debug skip")
                time.sleep(5)
            else:
                time.sleep(3)
        return False

    def next_buy(rnd):
        """End the round (alternating winners: the match must not end) and wait for the next buy phase."""
        if phase() in ("COMBAT", "PLANTED"):
            vb(rcon, "debug win " + "ab"[rnd % 2])
        return wait_phase("BUY")

    def hotbar_tooltip(slot, name):  # inventory screen, mouse over hotbar slot (0-based)
        key("key", "e")
        time.sleep(1)
        key("mousemove", str(OX + (125 + 8 + 18 * slot + 8) * GUI), str(OY + (37 + 142 + 8) * GUI))
        time.sleep(1.2)
        shot(tools, shots / name)
        key("key", "Escape")
        time.sleep(0.5)

    def seed_back(tag):
        """Buy phase: the Seed is on a bot's back (a display shown to the squad); stand 3 blocks behind that bot."""
        out = rcon.command('data get entity @e[type=minecraft:item_display,nbt={item:{id:"minecraft:echo_shard"}},limit=1] Pos')
        m = re.search(r"\[(-?[\d.]+)d, (-?[\d.]+)d, (-?[\d.]+)d\]", out)
        say("seed back display: " + out.strip()[:200])
        if not m:
            return False
        x, y, z = (float(v) for v in m.groups())
        out = rcon.command(f"execute positioned {x} {y} {z} run data get entity @e[type=minecraft:mannequin,sort=nearest,limit=1] Pos")
        b = re.search(r"\[(-?[\d.]+)d, (-?[\d.]+)d, (-?[\d.]+)d\]", out)
        say("nearest bot: " + out.strip()[:200])
        bx, bz = (float(b.group(1)), float(b.group(3))) if b else (x, z + 0.3)
        dx, dz = x - bx, z - bz  # from the bot to its back
        n = math.hypot(dx, dz) or 1.0
        dx, dz = dx / n, dz / n
        yaw = math.degrees(math.atan2(dx, -dz))  # looking at the bot from behind it
        # The display sits behind the bot's server-side yaw; the mannequin's body may not agree, so shoot both sides.
        for dist, name in ((3.0, "-display-side"), (2.0, "-display-side-close"), (-3.0, "-opposite-side")):
            tp(round(bx + dx * dist, 2), 65, round(bz + dz * dist, 2), round((yaw + (180 if dist < 0 else 0)) % 360, 1), 6)
            time.sleep(0.8)
            shot(tools, shots / f"pt2-seed-back-{tag}{name}.png")
        return True

    def armory_buy(tab, card_slot, name, extra=()):
        """Armory tab (column 0 row `tab`) then the card (slot = row * 9 + col), then the extra slots."""
        vb(rcon, "debug armory E2EBot")
        time.sleep(2)

        def click(slot):
            row, col = divmod(slot, 9)
            key("mousemove", str(OX + (125 + 16 + 18 * col) * GUI), str(OY + (9 + 26 + 18 * row) * GUI))
            time.sleep(0.4)
            key("click", "1")
            time.sleep(0.8)
        if tab is not None:
            click(tab * 9)
            click(card_slot)
        for slot in extra:
            click(slot)
        shot(tools, shots / f"pt2-armory-{name}.png")
        key("key", "Escape")
        time.sleep(1)

    def orbs():
        for ox, oz in ((30.0, 58.0), (90.0, 62.0)):
            tp(ox, 65, oz, 0, 10)
            time.sleep(0.8)

    def ult_shots(done):
        if "ult" in done or not ult_ready():
            return
        key("key", "7")
        time.sleep(1)
        shot(tools, shots / "pt2-ultimate-ready.png")
        hotbar_tooltip(6, "pt2-ultimate-ready-tooltip.png")
        done.add("ult")

    def seed_shots(done):
        """The human carries the Seed in a combat phase: HUD icon, hotbar item and tooltip, then the plant hold in site A."""
        if "seed" not in done:
            tp(*SPAWN, 180, 3)
            key("key", "8")
            time.sleep(1)
            shot(tools, shots / "pt2-seed-carrier-hud.png")
            hotbar_tooltip(7, "pt2-seed-tooltip.png")
            done.add("seed")
        if "plant" not in done and carrying():
            key("key", "8")
            tp(18.0, 65, 34.0, 180, 20)
            key("mousemove", "427", "240")
            subprocess.run([xdo, "mousedown", "3"], env=env)
            for i in range(3):
                time.sleep(1.0)
                shot(tools, shots / f"pt2-plant-{i}.png")
            say("plant status (held 3 s of 4): " + vb(rcon, "status"))
            subprocess.run([xdo, "mouseup", "3"], env=env)
            done.add("plant")

    done = set()
    rcon.command("gamemode adventure E2EBot")
    say(vb(rcon, "debug god E2EBot on"))  # the bots would kill the human within seconds
    # ---- round 1, combat: abilities
    to_combat()
    tp(*SPAWN, 180, 5)
    key("key", "4")
    time.sleep(1)
    shot(tools, shots / "pt2-01-hotbar-abilities.png")
    say("round 1: " + vb(rcon, "status") + (", the human carries the Seed" if carrying() else ""))
    hotbar_tooltip(3, "pt2-02-ability-tooltip.png")
    hotbar_tooltip(5, "pt2-02-ability-tooltip-signature.png")
    hotbar_tooltip(6, "pt2-03-ultimate-tooltip-locked.png")
    done.add("tooltip")
    if to_combat():  # the signature ability has a free charge (4 and 5 are bought in the Armory)
        key("key", "6")
        time.sleep(0.4)
        key("mousemove", "427", "240")
        key("click", "3")
        shot(tools, shots / "pt2-04-ability-cooldown-0.png")
        shot(tools, shots / "pt2-04-ability-cooldown-1.png")
        time.sleep(0.5)
        shot(tools, shots / "pt2-04-ability-after.png")
        done.add("cooldown")
    if to_combat():
        orbs()
        shot(tools, shots / "pt2-05-after-orbs.png")
        ult_shots(done)
        if carrying():
            seed_shots(done)

    for rnd in range(2, 13):
        if WANT <= done:
            break
        if not next_buy(rnd):
            break
        time.sleep(2)
        rcon.command("gamemode adventure E2EBot")
        vb(rcon, "debug god E2EBot on")  # the god flag is dropped with each round
        mine = carrying()
        say(f"round {rnd}: {vb(rcon, 'status')}; the human {'carries' if mine else 'does not carry'} the Seed; credits in the Armory title")
        tp(*SPAWN, 180, 5)
        if "scope" not in done:
            armory_buy(4, 1, "longwind", extra=(42,))  # the Longwind sniper, then the first buyable ability
        elif "rifle" not in done:
            armory_buy(3, 1, "bronzite")
        if not mine and "back" not in done and seed_back(f"r{rnd}"):
            done.add("back")
        tp(*SPAWN, 180, 5)
        if not (WANT - done - {"back"}):
            continue
        vb(rcon, "debug skip")  # buy -> combat
        time.sleep(8)
        if not to_combat():
            continue
        tp(*SPAWN, 180, 3)
        held = ("scope" not in done and "longwind") or ("rifle" not in done and "bronzite") or ""
        if held and has('Inventory[{Slot:0b}]'):
            key("key", "1")  # the primary
            time.sleep(1)
            shot(tools, shots / f"pt2-{held}-hip.png")
            subprocess.run([xdo, "keydown", "shift"], env=env)
            time.sleep(1.5)
            shot(tools, shots / f"pt2-{held}-aimed.png")
            subprocess.run([xdo, "keyup", "shift"], env=env)
            done.add("scope" if held == "longwind" else "rifle")
        if mine and to_combat():
            seed_shots(done)
        if "ult" not in done and to_combat():
            orbs()
            ult_shots(done)
    say(f"items scenario done: {sorted(done)}")
    for missing in sorted(WANT - done):
        say("NOT captured: " + missing)
    if phase() in ("COMBAT", "PLANTED", "BUY"):
        tp(*SPAWN, 180, 0)


def valoblock_overview(ctx, rcon, size=120):
    """Spectator shots from a high point (buy phase: nothing fights, no HUD)."""
    tools, shots = ctx["tools"], ctx["shots"]

    def tp(x, y, z, yaw, pitch):
        say("tp: " + vb(rcon, f"debug tp E2EBot {x} {y} {z} {yaw} {pitch}"))

    # Overview from a high point: spectator (no HUD), looking down at the middle of the map.
    rcon.command("gamemode spectator E2EBot")
    m = size / 2
    tp(m, size * 0.7, 6, 0, 40)
    time.sleep(12)  # the software client loads far chunks slowly
    shot(tools, shots / "vb-30-overview-a.png")
    rcon.command("gamemode adventure E2EBot")


def valoblock_rewind(ctx, rcon, failures):
    """Sahrin's Rewind with real clicks: the first right-click marks the position, a second one within 8 s must
    teleport back (the owner's bug: the mark appeared but the return never happened)."""
    tools, env = ctx["tools"], ctx["env"]
    xdo = tools["xdotool"]
    rcon.command("gamemode adventure E2EBot")
    vb(rcon, "debug god E2EBot on")
    if os.environ.get("E2E_VB_AGENT", "sahrin") == "murk":
        return valoblock_wall(ctx, rcon, failures)

    def where():
        out = vb(rcon, "debug where E2EBot")
        m = re.search(r"where (\S+) (-?[\d.]+) (-?[\d.]+) (-?[\d.]+)", out)
        return (float(m.group(2)), float(m.group(3)), float(m.group(4))) if m else None

    subprocess.run([xdo, "mousemove", "640", "360"], env=env)
    subprocess.run([xdo, "key", "4"], env=env)

    def trial(name, hold, wait, away_x):
        say(f"{name}: charge " + vb(rcon, "debug charge E2EBot 4"))
        vb(rcon, "debug tp E2EBot 50 65 114 180 0")
        time.sleep(2)
        start = where()
        subprocess.run([xdo, "mousedown", "3"], env=env)  # mark (hold = seconds the button stays down)
        time.sleep(hold)
        subprocess.run([xdo, "mouseup", "3"], env=env)
        time.sleep(wait)
        vb(rcon, f"debug tp E2EBot {away_x} 65 114 180 0")
        time.sleep(2)
        away = where()
        subprocess.run([xdo, "click", "3"], env=env)  # return
        time.sleep(2)
        back = where()
        say(f"rewind {name}: start={start} away={away} after the second click={back}")
        if not (start and back and abs(back[0] - start[0]) < 1.5 and abs(back[2] - start[2]) < 1.5):
            failures.append(f"Rewind {name} did not bring the player back: start={start} away={away} back={back}")

    trial("quick click", 0.05, 0.5, 60)
    trial("click then 4 s", 0.05, 2, 62)
    trial("slow click held 0.5 s", 0.5, 0.5, 64)


def valoblock_wall(ctx, rcon, failures):
    """Murk's Miasma Wall (signature, slot 6 = hotbar key 6): placed at two map spots, then a right-click switches it."""
    xdo, env = ctx["tools"]["xdotool"], ctx["env"]
    vb(rcon, "debug tp E2EBot 50 65 114 180 0")
    time.sleep(2)
    subprocess.run([xdo, "mousemove", "640", "360"], env=env)
    subprocess.run([xdo, "key", "6"], env=env)
    say("cast: " + vb(rcon, "debug cast E2EBot 6 46 110 54 110"))
    time.sleep(1.5)
    seen = [vb(rcon, "debug wall E2EBot")]
    for name, hold in (("click", 0.05), ("click", 0.05), ("held 0.5 s", 0.5)):
        time.sleep(1)
        subprocess.run([xdo, "mousedown", "3"], env=env)
        time.sleep(hold)
        subprocess.run([xdo, "mouseup", "3"], env=env)
        time.sleep(1)
        seen.append(vb(rcon, "debug wall E2EBot"))
        say(f"wall after {name}: {seen[-1]}")
    say(f"wall states: {seen}")
    if seen != ["wall on", "wall off", "wall on", "wall off"]:
        failures.append(f"Miasma Wall did not switch exactly once per click: {seen}")


def valoblock_scenario(ctx):
    """A ValoBlock practice match on the Hourfall greybox: 1 human, 9 bots, driven by /vb debug commands.
    Screenshots: agent select, buy phase, Armory, combat HUD, the next round after a death (respawn).
    The checks use the server only; the screenshots must be looked at by a person."""
    tools, shots, client_log, failures, server = (ctx[k] for k in
                                                  ("tools", "shots", "client_log", "failures", "server"))
    for _ in range(60):
        try:
            state = api("GET", "/arenas")
            break
        except Exception:
            time.sleep(2)
    else:
        failures.append("the plugin's private API never answered")
        return
    if [a["config"]["id"] for a in state["arenas"]] != ["e2e"]:
        failures.append(f"arena e2e not loaded: {state['arenas']}")
        return
    me = next(p["uuid"] for p in state["onlinePlayers"] if p["name"] == "E2EBot")
    rcon = Rcon()
    say("maps: " + vb(rcon, "map list").replace("\n", " | "))
    # The next map is random (a vote) among the three themed maps: the default checks below are Hourfall's (spawn barrier row, corner
    # wall, Time Tide gates), so Hourfall is pinned; --rules map=stormbreak plays another map and skips them.
    say(vb(rcon, "map use " + (ctx["rules"].split("=", 1)[1] if ctx.get("rules", "").startswith("map=") else "hourfall")))
    reloads = client_log.read_text(errors="replace").count("Reloading ResourceManager")
    api("POST", "/arenas/action", {"action": "arena-join", "arenaId": "e2e", "uuid": me})
    # Joining the arena adds the second pack (ValoBlock guns, agents, scope): accept its prompt, wait for the reload.
    for _ in range(40):
        if client_log.read_text(errors="replace").count("Reloading ResourceManager") > reloads:
            break
        subprocess.run([tools["xdotool"], "mousemove", "485", "445", "click", "1"], env=ctx["env"])
        time.sleep(3)
    else:
        failures.append("the client never reloaded for the ValoBlock pack (second pack not offered or not accepted)")
    time.sleep(4)
    out = vb(rcon, "start")
    say("vb start: " + out)
    if "started" not in out:
        failures.append("vb start: " + out)
        return
    time.sleep(8)
    shot(tools, shots / "vb-01-agent-select.png")
    xdo = tools["xdotool"]
    for i, (hx, hy) in enumerate([(603, 189), (675, 190), (603, 262), (747, 226)]):  # hover a portrait: the left panel shows it
        subprocess.run([xdo, "mousemove", str(hx - 3), str(hy), "mousemove", str(hx), str(hy)], env=ctx["env"])
        time.sleep(1.5)
        shot(tools, shots / f"vb-01-agent-hover{i}.png")
    subprocess.run([xdo, "click", "1"], env=ctx["env"])  # lock the hovered agent in
    time.sleep(1.5)
    shot(tools, shots / "vb-01-agent-picked.png")
    say("where: " + vb(rcon, "debug where E2EBot"))
    out = vb(rcon, "debug where E2EBot")
    if "vb_arena" not in out:
        failures.append("the human is not in the vb_arena world at the start: " + out)
    # A known agent: the Rewind check needs Sahrin, the Miasma Wall check Murk (E2E_VB_AGENT=murk)
    say("lock: " + vb(rcon, "debug agent E2EBot " + os.environ.get("E2E_VB_AGENT", "sahrin")))
    vb(rcon, "debug skip")  # agent select -> buy (unlocked players get a random agent, bots fill)
    time.sleep(6)
    if os.environ.get("E2E_VB_AGENT", "sahrin") == "sahrin":
        say("buy rewind: " + vb(rcon, "debug buy E2EBot 4"))
    say("status: " + vb(rcon, "status"))
    # Hourfall: the Seedbearer barrier is the row z 104-107, x 40-79 (the markers of the other maps differ).
    if not ctx.get("rules"):
        out = vb(rcon, "debug block 60 65 106")
        say("barrier at the Seedbearer exit in the buy phase: " + out)
        if "BARRIER" not in out:
            failures.append("no spawn barrier in the buy phase: " + out)
        out = vb(rcon, "debug block 0 65 0")
        if out.split()[-1] in ("AIR", "STONE"):
            failures.append("the corner cell of the greybox is not a wall: " + out)
    shot(tools, shots / "vb-02-buy.png")
    say(vb(rcon, "debug armory E2EBot"))
    time.sleep(3)
    shot(tools, shots / "vb-03-armory.png")
    xdo = tools["xdotool"]
    for i, ty in enumerate((190, 226, 262, 298, 334, 370)):  # one capture per tab, cursor on the first card
        subprocess.run([xdo, "mousemove", "495", str(ty), "click", "1"], env=ctx["env"])
        time.sleep(1)
        subprocess.run([xdo, "mousemove", "545", "205", "mousemove", "550", "207"], env=ctx["env"])
        time.sleep(1)
        shot(tools, shots / f"vb-03-armory-tab{i}.png")
    subprocess.run([xdo, "mousemove", "550", "207", "click", "1"], env=ctx["env"])  # buy the hovered card (owned / bought state)
    time.sleep(1.5)
    shot(tools, shots / "vb-03-armory-bought.png")
    subprocess.run([tools["xdotool"], "key", "Escape"], env=ctx["env"])
    time.sleep(1)
    valoblock_overview(ctx, rcon, 150 if ctx.get("rules") == "map=stormbreak" else 120)
    vb(rcon, "debug tp E2EBot %s 65 %s" % ((62.5, 142.5) if ctx.get("rules") == "map=stormbreak" else (50.0, 114.0)))
    time.sleep(2)
    vb(rcon, "debug skip")  # buy -> combat
    time.sleep(8)
    if not ctx.get("rules") and "BARRIER" in vb(rcon, "debug block 60 65 106"):
        failures.append("the spawn barrier is still up in combat")
    mannequins = rcon.command("execute if entity @e[type=minecraft:mannequin]")
    say("bot bodies: " + mannequins.strip())
    if "count: 9" not in mannequins.lower():
        failures.append("expected 9 bot bodies (1 human, 5v5): " + mannequins.strip())
    shot(tools, shots / "vb-04-combat.png")
    # A notice must not touch the glyph HUD (it is a subtitle), and each bot carries one tag.
    vb(rcon, "debug god E2EBot on")  # the bots would kill the human before the close-ups
    rcon.command("gamemode adventure E2EBot")
    say(vb(rcon, "debug notice E2EBot Ability not ready: 3 s"))
    time.sleep(0.8)
    shot(tools, shots / "vb-14-hud-notice.png")
    say("tags: " + rcon.command("execute if entity @e[type=minecraft:text_display]").strip())
    # Each bot up close: an ally shows one readable tag, an enemy none (the tags are per-viewer text displays).
    say("tag visibility per bot:\n" + vb(rcon, "debug tags E2EBot"))
    rcon.command("tag @e[type=minecraft:mannequin] remove tagshot")
    for i in range(6):
        pick = "@e[type=minecraft:mannequin,tag=!tagshot,limit=1,sort=nearest]"
        bx = rcon.command(f"data get entity {pick} Pos").strip()
        nums = [float(v) for v in re.findall(r"(-?[0-9.]+)d", bx)]
        if len(nums) != 3:
            break
        say(f"bot {i}: {bx}")
        rcon.command(f"tag {pick} add tagshot")
        vb(rcon, f"debug tp E2EBot {nums[0]} {nums[1] + 0.2} {nums[2] + 2.5} 180 0")
        time.sleep(1.5)
        shot(tools, shots / f"vb-15-bot-tag-{i}.png")
    valoblock_rewind(ctx, rcon, failures)
    if ctx.get("only") == "items":  # --only items: the gear of the match (scope, rifle, abilities, Seed) instead of the Realm tour
        valoblock_items(ctx, rcon, failures)
    else:
        valoblock_extras(ctx, rcon, ctx.get("rules", "map=hourfall").split("=", 1)[-1] or "hourfall", failures)
    time.sleep(10)
    shot(tools, shots / "vb-05-combat-later.png")
    say("where: " + vb(rcon, "debug where E2EBot"))
    # Death and respawn: the human dies, the round ends, the next round puts them back on a spawn, alive.
    vb(rcon, "debug kill E2EBot")
    rcon.command("gamemode spectator E2EBot")  # what the engine / weapon service does to a dead player
    time.sleep(2)
    say("dead: " + vb(rcon, "debug where E2EBot"))
    shot(tools, shots / "vb-06-dead.png")
    vb(rcon, "debug win b")
    time.sleep(1)
    vb(rcon, "debug skip")  # round end -> next buy phase
    for _ in range(8):  # the respawn (teleport, alive, gamemode) lands a little after the phase starts: poll instead of one fixed wait
        time.sleep(2)
        out = vb(rcon, "debug where E2EBot")
        if "alive=true" in out and "mode=ADVENTURE" in out:
            break
    say("after the next round started: " + out)
    if "alive=true" not in out or "mode=ADVENTURE" not in out or "hp=100" not in out or "vb_arena" not in out:
        failures.append("the human was not respawned for the next round: " + out)
    shot(tools, shots / "vb-07-next-round.png")
    say(vb(rcon, "stop"))
    for _ in range(40):
        if api("GET", "/arenas")["arenas"][0]["phase"] == "WAITING":
            break
        time.sleep(1)
    else:
        failures.append("the arena did not return to WAITING after /vb stop")
    time.sleep(8)
    now = client_log.read_text(errors="replace").count("Reloading ResourceManager")
    say(f"client resource reloads: before join {reloads}, after the stop {now}")
    if now < reloads + 2:
        failures.append("the client did not reload again after the stop (ValoBlock pack not removed?)")
    out = rcon.command("execute if entity @e[type=minecraft:mannequin]")
    if "passed" in out.lower():
        failures.append("bot bodies are still in the world after the stop")
    back = player_state(rcon)
    dimension = rcon.command("data get entity E2EBot Dimension")
    say(f"after the stop: {back} {dimension.strip()}")
    if "vb_arena" in dimension:
        failures.append("the human was not sent back out of vb_arena after the stop")
    shot(tools, shots / "vb-08-after-stop.png")
    console = (server / "console.log").read_text(errors="replace")
    for line in console.splitlines():
        if ("win.vampyuyu" in line or "NexusArena" in line) and (
                "Exception" in line or "ERROR" in line or "SEVERE" in line):
            failures.append("server: " + line.strip()[:200])
    for needle in FATAL:
        if needle in client_log.read_text(errors="replace"):
            failures.append(f"client log contains '{needle}'")


def hold_scenario(ctx):
    """Screenshot tour: join, then keep the server and client alive (and the e2e lock) until .e2e/tour-stop exists.
    A helper script drives them meanwhile (Rcon, xdotool, shot)."""
    stop = WORK / "tour-stop"
    stop.unlink(missing_ok=True)
    (WORK / "tour-ready").write_text("ready")
    say("hold: ready, waiting for " + str(stop))
    deadline = time.time() + 4 * 3600
    while not stop.exists() and time.time() < deadline:
        time.sleep(2)
    (WORK / "tour-ready").unlink(missing_ok=True)


def vfx_scenario(ctx):
    """The custom boss VFX and the potion-free status veils with a real client: Hell Gao on a flat floor at night, a
    targetable unhurt bot in front of it, each ability cast with /boss cast and filmed (.e2e/vfx-videos/<ability>.mp4),
    then each status veil laid with /boss status and shot. E2E_VFX=void_zones,laser_beam picks the abilities.
    E2E_VFX_BOSS picks the boss (default hell_gao)."""
    import math
    tools, env, shots, failures = (ctx[k] for k in ("tools", "env", "shots", "failures"))
    xdo, rcon = tools["xdotool"], Rcon()

    def command(text, wait=3):
        subprocess.run([xdo, "key", "slash"], env=env)
        time.sleep(.5)
        subprocess.run([xdo, "type", "--delay", "35", text], env=env)
        subprocess.run([xdo, "key", "Return"], env=env)
        time.sleep(wait)

    def as_bot(text):
        return rcon.command("execute as E2EBot at E2EBot run " + text)

    rcon.command("op E2EBot")
    command("menu skyblock")
    for _ in range(150):
        if "sb_world" in rcon.command("data get entity E2EBot Dimension"):
            break
        time.sleep(1)
    else:
        failures.append("never reached sb_world")
        return
    time.sleep(8)
    pos = player_state(rcon)["pos"]
    cx, y, cz = math.floor(pos[0]) + 8, math.floor(pos[1]), math.floor(pos[2])
    # night shows the glow, but night spawns monsters: no spawning in this world, and every stray monster killed before a take
    for rule in ("spawn_mobs", "spawn_monsters", "spawn_patrols", "spawn_phantoms"):
        say(f"vfx: gamerule {rule}: " + as_bot(f"gamerule {rule} false")[:60])
    hostiles = ",".join(f"type=!{t}" for t in ("player", "item_display", "text_display", "item", "enderman"))

    def clear_monsters():
        as_bot(f"kill @e[{hostiles},distance=..120]")
    as_bot("time set midnight")
    as_bot(f"fill {cx-40} {y-1} {cz-40} {cx+40} {y-1} {cz+40} deepslate_tiles")
    for yy in range(y, y + 24):
        as_bot(f"fill {cx-40} {yy} {cz-40} {cx+40} {yy} {cz+40} air")
    as_bot("kill @e[type=!player,distance=..80]")
    rcon.command("gamerule send_command_feedback false")
    boss = os.environ.get("E2E_VFX_BOSS", "hell_gao")
    abilities = [a for a in os.environ.get("E2E_VFX", "void_zones,lightning_strikes,laser_beam").split(",") if a]
    out_dir = ROOT / ".e2e" / "vfx-videos"
    out_dir.mkdir(parents=True, exist_ok=True)
    mob = "@e[type=!player,type=!item_display,type=!text_display,type=!item,distance=..30,sort=nearest,limit=1]"
    subprocess.run([xdo, "key", "F1"], env=env)
    for ability in abilities:
        rcon.command("boss kill all")
        clear_monsters()
        time.sleep(1)
        rcon.command(f"boss spawn {boss} {cx+0.5} {y} {cz+0.5} sb_world")
        time.sleep(2)
        as_bot(f"execute as {mob} run data merge entity @s {{NoAI:1b}}")
        as_bot(f"tp {mob} {cx+0.5} {y} {cz+0.5} 0 0")
        say(f"vfx: {boss} mob Silent: " + as_bot(f"data get entity {mob} Silent").strip()[-40:])
        # the target: adventure (targetable), unhurt, 9 blocks in front of the boss, looking at it from a little above
        rcon.command("gamemode adventure E2EBot")
        rcon.command("effect give E2EBot minecraft:resistance infinite 255 true")
        rcon.command("effect give E2EBot minecraft:regeneration infinite 255 true")
        as_bot(f"tp @s {cx+0.5} {y} {cz+9.5} 180 12")
        time.sleep(2)
        say(f"vfx: {ability}: " + rcon.command(f"boss cast {ability}")[:120])
        record(tools, out_dir / f"{boss}-{ability}.mp4", 7)
        shot(tools, shots / f"vfx-{ability}-end.png")
        say(f"vfx: {ability}: displays left after the clip: "
            + rcon.command("execute if entity @e[type=item_display,distance=..60]")[:80])
    rcon.command("boss kill all")
    clear_monsters()
    rcon.command("effect clear E2EBot")
    rcon.command("gamemode survival E2EBot")   # the Skyblock HUD line (which carries the veils) is the survival player's
    rcon.command("effect give E2EBot minecraft:resistance infinite 255 true")
    subprocess.run([xdo, "key", "F1"], env=env)   # the HUD back on: the veil rides on the action bar
    as_bot(f"tp @s {cx+0.5} {y} {cz+9.5} 180 0")
    for status in ("blind", "frost", "decay", "burn", "lift"):
        command(f"boss status {status} 6", 1.5)
        time.sleep(1.5)
        shot(tools, shots / f"veil-{status}.png")
        command("boss status clear", 1.5)


def dev_server():
    """Server only (arena + network jars, JDWP open): the target of scripts/hotswap.py. No client, no pack check."""
    import fcntl
    global DEBUG
    DEBUG = True
    WORK.mkdir(exist_ok=True)
    lock = open("/tmp/vampyuyu-e2e.lock", "w")
    fcntl.flock(lock, fcntl.LOCK_EX)
    stop_stale()
    (WORK / "hotswap-stamps.json").unlink(missing_ok=True)  # a fresh boot loads the jars: nothing swapped yet
    pack_dir = WORK / "pack"
    zip_path, sha1 = build_pack.build(pack_dir)
    start_server(zip_path, sha1, plugin=True, pack_required=False)
    say(f"dev server up: Minecraft :{MC_PORT}, rcon :{RCON_PORT} ({RCON_PASSWORD}), JDWP :{DEBUG_PORT}")


def back_scenario(ctx):
    """/back of the survival plugin with a real client: die, respawn elsewhere, type /back, land on the death
    spot; die again and meet the 5 minute cooldown."""
    tools, env, shots, failures, server = (ctx[k] for k in ("tools", "env", "shots", "failures", "server"))
    xdo = tools["xdotool"]
    rcon = Rcon()

    def type_command(text):
        subprocess.run([xdo, "key", "slash"], env=env)
        time.sleep(1)
        subprocess.run([xdo, "type", "--delay", "60", text], env=env)
        time.sleep(0.5)
        subprocess.run([xdo, "key", "Return"], env=env)
        time.sleep(6)  # utility's /back warms up 3 s (stand still)

    def pos():
        return player_state(rcon)["pos"]

    def near(a, b, r=3.0):
        return abs(a[0] - b[0]) < r and abs(a[2] - b[2]) < r

    def die_at(x, z):
        rcon.command(f"tp E2EBot {x} -60 {z}")
        time.sleep(2)
        rcon.command("gamemode survival E2EBot")
        time.sleep(1)
        rcon.command("kill E2EBot")
        time.sleep(4)  # immediate respawn is on: the bot is back at the world spawn

    say("back: " + rcon.command("gamerule immediate_respawn true").strip())
    first = (60, 60)
    die_at(*first)
    here = pos()
    if near(here, (first[0], 0, first[1]), 10):
        failures.append(f"the bot did not respawn away from its death spot: {here}")
    shot(tools, shots / "back-1-respawned.png")
    type_command("back")
    after = pos()
    shot(tools, shots / "back-2-returned.png")
    if not near(after, (first[0], 0, first[1])):
        failures.append(f"/back did not return to the death spot {first}: {after}")
    die_at(-60, -60)
    type_command("back")
    again = pos()
    shot(tools, shots / "back-3-cooldown.png")
    if near(again, (-60, 0, -60), 10):
        failures.append(f"/back worked during the cooldown: {again}")
    saved = server / "plugins" / "VampyuyuSurvival" / "back.json"
    if not saved.exists() or "lastUse" not in saved.read_text():
        failures.append("back.json was not written")
    say(f"back: respawn {here}, returned {after}, during cooldown {again}")


def skyblock_scenario(ctx):
    """Simple Skyblock on an isolated Paper: explicit modes, two beds, Nether death, blocked cross-mode TP."""
    tools, env, shots, failures, server = (ctx[k] for k in ("tools", "env", "shots", "failures", "server"))
    xdo, rcon = tools["xdotool"], Rcon()

    def command(text):
        subprocess.run([xdo, "key", "slash"], env=env)
        time.sleep(.5)
        subprocess.run([xdo, "type", "--delay", "35", text], env=env)
        subprocess.run([xdo, "key", "Return"], env=env)
        time.sleep(3)

    def as_bot(text):
        return rcon.command("execute as E2EBot at E2EBot run " + text)

    def dimension():
        return rcon.command("data get entity E2EBot Dimension")

    def wait_dimension(name, seconds=60):
        for _ in range(seconds):
            if name in dimension(): return True
            time.sleep(1)
        failures.append("expected dimension " + name + ": " + dimension())
        return False

    def make_bed():
        import math
        pos = player_state(rcon)["pos"]
        x, y, z = math.floor(pos[0]) + 5, math.floor(pos[1]), math.floor(pos[2])
        as_bot(f"fill {x-2} {y-1} {z-2} {x+2} {y-1} {z+3} stone")
        as_bot(f"fill {x-2} {y} {z-2} {x+2} {y+2} {z+3} air")
        as_bot(f"setblock {x} {y} {z} red_bed[facing=north,part=foot]")
        as_bot(f"setblock {x} {y} {z-1} red_bed[facing=north,part=head]")
        as_bot(f"tp @s {x+.5} {y} {z+2.5} 180 28")
        time.sleep(2)
        subprocess.run([xdo, "click", "3"], env=env)
        time.sleep(2)
        return x, y, z

    rcon.command("op E2EBot")
    rcon.command("gamemode survival E2EBot")
    rcon.command("gamerule immediate_respawn true")
    rcon.command("gamerule spawn_mobs false")
    rcon.command("kill @e[type=slime]")
    command("menu")
    shot(tools, shots / "sb-1-mode-selector.png")
    subprocess.run([xdo, "key", "Escape"], env=env)
    command("menu skyblock")
    if not wait_dimension("sb_world", 150): return
    time.sleep(8)
    shot(tools, shots / "sb-2-personal-island.png")
    command("sbmenu")
    shot(tools, shots / "sb-3-island-menu.png")
    subprocess.run([xdo, "key", "Escape"], env=env)
    sb_bed = make_bed()
    as_bot("gamerule immediate_respawn true")
    command("menu survival")
    if not wait_dimension('minecraft:overworld'): return
    normal_bed = make_bed()
    command("menu skyblock")
    wait_dimension("sb_world")
    command("island nether")
    if not wait_dimension("sb_nether"): return
    shot(tools, shots / "sb-4-isolated-nether.png")
    as_bot("gamerule immediate_respawn true")
    rcon.command("kill E2EBot")
    time.sleep(5)
    wait_dimension("sb_world")
    sky_respawn = player_state(rcon)["pos"]
    if abs(sky_respawn[0]-sb_bed[0]) > 3 or abs(sky_respawn[2]-sb_bed[2]) > 3:
        failures.append(f"Skyblock Nether death missed the Skyblock bed: {sky_respawn}, bed={sb_bed}")
    shot(tools, shots / "sb-5-skyblock-bed-respawn.png")
    # Even an operator's cross-world /tp must be rejected outside the mode selector.
    as_bot("execute in minecraft:overworld run tp @s 0 -60 0")
    if "sb_world" not in dimension(): failures.append("cross-mode /tp escaped Skyblock")
    command("menu survival")
    if not wait_dimension('minecraft:overworld'): return
    rcon.command("kill E2EBot")
    time.sleep(5)
    wait_dimension('minecraft:overworld')
    normal_respawn = player_state(rcon)["pos"]
    if abs(normal_respawn[0]-normal_bed[0]) > 3 or abs(normal_respawn[2]-normal_bed[2]) > 3:
        failures.append(f"Survival death missed the independent Survival bed: {normal_respawn}, bed={normal_bed}")
    shot(tools, shots / "sb-6-survival-bed-respawn.png")
    if any((base / name).exists() for base in (server, server / "world/dimensions/minecraft")
           for name in ("sb_end",)):
        failures.append("retired Skyblock End was generated")
    rcon.command("gamemode creative E2EBot")
    say(f"skyblock: two beds checked; Skyblock={sky_respawn}, Survival={normal_respawn}")


def quarry_scenario(ctx):
    """The Quarry with a real client: custom item icons, the Forge menu, machines placed with real clicks, and a coal
    generator powering a Miner I that breaks generator cobble into a barrel."""
    import math
    tools, env, shots, failures, server = (ctx[k] for k in ("tools", "env", "shots", "failures", "server"))
    xdo, rcon = tools["xdotool"], Rcon()

    def command(text):
        subprocess.run([xdo, "key", "slash"], env=env)
        time.sleep(.5)
        subprocess.run([xdo, "type", "--delay", "35", text], env=env)
        subprocess.run([xdo, "key", "Return"], env=env)
        time.sleep(3)

    def as_bot(text):
        return rcon.command("execute as E2EBot at E2EBot run " + text)

    def dimension():
        return rcon.command("data get entity E2EBot Dimension")

    def what(x, yy, z):
        for name in ("lava", "water", "cobblestone", "obsidian", "air", "stone", "stonecutter", "lodestone", "barrel"):
            if "passed" in as_bot(f"execute if block {x} {yy} {z} minecraft:{name}"):
                return name
        return "?"

    def stand(x, y, z, yaw, pitch):
        as_bot(f"tp @s {x} {y} {z} {yaw} {pitch}")  # as_bot keeps the dimension: a bare tp would leave Skyblock
        time.sleep(1.5)

    def use(slot, x, y, z, yaw, pitch):
        subprocess.run([xdo, "key", str(slot)], env=env)
        time.sleep(.5)
        stand(x, y, z, yaw, pitch)
        subprocess.run([xdo, "click", "3"], env=env)
        time.sleep(1.5)

    rcon.command("op E2EBot")
    rcon.command("gamemode survival E2EBot")
    rcon.command("gamerule spawn_mobs false")
    rcon.command("kill @e[type=slime]")
    command("menu skyblock")
    for _ in range(150):
        if "sb_world" in dimension():
            break
        time.sleep(1)
    else:
        failures.append("never reached sb_world: " + dimension())
        return
    time.sleep(8)
    pos = player_state(rcon)["pos"]
    cx, y, cz = math.floor(pos[0]) + 6, math.floor(pos[1]), math.floor(pos[2])
    # a flat arena: floor, walls, a lava source and a water source with one empty block between them
    as_bot(f"fill {cx-3} {y-1} {cz-3} {cx+6} {y-1} {cz+9} stone")
    as_bot(f"fill {cx-3} {y} {cz-3} {cx+6} {y+4} {cz+9} air")
    as_bot(f"fill {cx-1} {y} {cz-1} {cx+3} {y} {cz-1} stone")
    as_bot(f"setblock {cx-1} {y} {cz} stone")
    as_bot(f"setblock {cx} {y} {cz} stone")
    as_bot(f"setblock {cx+3} {y} {cz} stone")
    # the lava source sits above the empty block and is walled in: its flow falls into that block, where the water
    # source next to it turns it into cobblestone (a lava source touched by water would become obsidian instead)
    for dx, dz in ((0, 0), (2, 0), (1, -1), (1, 1)):
        as_bot(f"setblock {cx+dx} {y+1} {cz+dz} stone")
    as_bot(f"setblock {cx+2} {y} {cz+1} stone")  # keeps the water from spreading south
    rcon.command("clear E2EBot")
    for item in ("forge 1", "coal_generator 1", "miner_1 1"):
        rcon.command("sbgive " + item + " E2EBot")
    rcon.command("give E2EBot minecraft:barrel 1")
    rcon.command("give E2EBot minecraft:coal 16")
    rcon.command("sbgive bronzite 12 E2EBot")
    rcon.command("sbgive bronze_pick 1 E2EBot")
    rcon.command("sbgive rimeblade 1 E2EBot")
    rcon.command("sbgive miner_2 1 E2EBot")
    rcon.command("sbgive battery 2 E2EBot")
    rcon.command("sbgive cable 8 E2EBot")
    time.sleep(2)
    stand(cx + 1.5, y, cz + 6.5, 180, 25)
    shot(tools, shots / "qa-0-sidebar.png")
    command("guide")
    shot(tools, shots / "qa-0b-guide.png")
    command("hud off")
    shot(tools, shots / "qa-0c-hud-off.png")
    command("hud on")
    shot(tools, shots / "qa-1-hotbar.png")
    subprocess.run([xdo, "key", "e"], env=env)
    time.sleep(2)
    shot(tools, shots / "qa-2-inventory.png")
    subprocess.run([xdo, "key", "Escape"], env=env)
    time.sleep(1)
    command("forge")
    shot(tools, shots / "qa-3-forge-menu.png")
    subprocess.run([xdo, "key", "Escape"], env=env)
    time.sleep(1)
    # generator (slot 2), miner (slot 3) and barrel (slot 4) with real clicks on the floor
    use(2, cx + 0.5, y, cz + 3.5, 180, 39)
    use(3, cx + 1.5, y, cz + 3.5, 180, 39)
    use(4, cx + 1.5, y, cz + 4.5, 180, 39)
    # the Forge block (slot 1), then open it
    use(1, cx + 4.5, y, cz + 6.5, 180, 39)
    stand(cx + 4.5, y, cz + 7.5, 180, 24)
    subprocess.run([xdo, "click", "3"], env=env)
    time.sleep(2)
    shot(tools, shots / "qa-4-forge-block-menu.png")
    subprocess.run([xdo, "key", "Escape"], env=env)
    time.sleep(1)
    # fuel the generator with coal (slot 5)
    subprocess.run([xdo, "key", "5"], env=env)
    time.sleep(.5)
    stand(cx + 0.5, y, cz + 5.5, 180, 24)
    subprocess.run([xdo, "click", "3"], env=env)
    time.sleep(2)
    # only now the lava and the water: the machines are in place, so the fluids cannot flood the arena
    as_bot(f"setblock {cx+1} {y+1} {cz} lava")
    as_bot(f"setblock {cx+2} {y} {cz} water")
    for t in (2, 4, 6):
        time.sleep(2)
        say(f"quarry t+{t}s: lava-above={what(cx+1, y+1, cz)} spot={what(cx+1, y, cz)} water={what(cx+2, y, cz)} "
            f"west={what(cx, y, cz)} north={what(cx+1, y, cz-1)}")
    stand(cx + 1.5, y + 1, cz + 8.0, 180, 28)
    shot(tools, shots / "qa-5-machines.png")
    stand(cx + 1.0, y + 1, cz + 6.2, 180, 38)
    shot(tools, shots / "qa-5b-machines-close.png")
    # a second row for the render: miner II, battery, cable and solar panel on slots 1 to 4 (the lava is far to the east)
    rcon.command("clear E2EBot")
    for item in ("miner_2", "battery", "cable", "solar_panel"):
        rcon.command(f"sbgive {item} 1 E2EBot")
    for slot, dx in ((1, -1.5), (2, -2.5), (3, -3.5), (4, -4.5)):
        use(slot, cx + dx, y, cz + 5.5, 180, 39)
    rcon.command("clear E2EBot")
    stand(cx + 4.5, y, cz + 8.4, 180, 15)
    shot(tools, shots / "qa-5c-forge-close.png")
    stand(cx - 3.0, y, cz + 8.0, 180, 18)
    shot(tools, shots / "qa-5d-row.png")
    stand(cx + 1.0, y, cz + 5.6, 180, 18)
    shot(tools, shots / "qa-5e-generator-close.png")
    cobble = as_bot(f"data get block {cx+1} {y} {cz}")
    say("quarry: block between lava and water: " + cobble.strip()[:80])
    time.sleep(45)  # the generator makes 10 Flux/s, a Miner I draws 8 and breaks a block every 2 s
    shot(tools, shots / "qa-6-after-mining.png")
    for label, bx, bz, block in (("spot cobble", cx + 1, cz, "cobblestone"),
                                 ("spot air", cx + 1, cz, "air"), ("spot obsidian", cx + 1, cz, "obsidian"),
                                 ("water", cx + 2, cz, "water"), ("generator", cx, cz + 1, "stonecutter"),
                                 ("miner", cx + 1, cz + 1, "lodestone"), ("barrel", cx + 1, cz + 2, "barrel")):
        say(f"quarry check {label}: " + as_bot(f"execute if block {bx} {y} {bz} minecraft:{block}").strip())
    say(f"quarry end: lava-above={what(cx+1, y+1, cz)} spot={what(cx+1, y, cz)} water={what(cx+2, y, cz)}")
    say("quarry check lava above: " + as_bot(f"execute if block {cx+1} {y+1} {cz} minecraft:lava").strip())
    saved = server / "plugins" / "VampyuyuSurvival" / "quarry" / "machines.json"
    say("quarry machines.json: " + (saved.read_text()[:600].replace("\n", " ") if saved.exists() else "(not written yet)"))
    barrel = as_bot(f"data get block {cx+1} {y} {cz+2} Items")
    say("quarry: barrel items: " + barrel.strip()[:1200])
    if "id:" not in barrel:
        failures.append("the Miner I did not put any ore in the barrel: " + barrel.strip()[:120])
    log = (server / "console.log").read_text(errors="replace")
    for line in log.splitlines():
        if ("Exception" in line or "SEVERE" in line) and " WARN]" not in line \
                and ("Quarry" in line or "quarry" in line or "VampyuyuSurvival" in line):
            failures.append("server error: " + line[:160])
            break
    rcon.command("gamemode creative E2EBot")


def progress_scenario(ctx):
    """Progression with a real client: no End gate on the island, /island end sealed until the Void Lens, tiers II to IV,
    the End platform, the Matriarch and the Voidwyrm summoned, skin token and /skins, the dungeon menu and a run."""
    import math
    tools, env, shots, failures, server = (ctx[k] for k in ("tools", "env", "shots", "failures", "server"))
    xdo, rcon = tools["xdotool"], Rcon()

    def command(text, wait=3):
        subprocess.run([xdo, "key", "slash"], env=env)
        time.sleep(.5)
        subprocess.run([xdo, "type", "--delay", "35", text], env=env)
        subprocess.run([xdo, "key", "Return"], env=env)
        time.sleep(wait)

    def as_bot(text):
        return rcon.command("execute as E2EBot at E2EBot run " + text)

    def dimension():
        return rcon.command("data get entity E2EBot Dimension")

    def wait_dimension(name, seconds=90):
        for _ in range(seconds):
            if name in dimension():
                return True
            time.sleep(1)
        failures.append("expected dimension " + name + ": " + dimension())
        return False

    def use(slot, pitch=0):
        subprocess.run([xdo, "key", str(slot)], env=env)
        time.sleep(.6)
        as_bot(f"tp @s ~ ~ ~ 0 {pitch}")
        time.sleep(.6)
        subprocess.run([xdo, "click", "3"], env=env)
        time.sleep(2)

    def active_bosses():
        return rcon.command("boss active")

    rcon.command("op E2EBot")
    rcon.command("gamemode survival E2EBot")
    rcon.command("gamerule spawn_mobs false")
    rcon.command("kill @e[type=slime]")
    command("menu skyblock")
    if not wait_dimension("sb_world", 150):
        return
    time.sleep(8)
    pos = player_state(rcon)["pos"]
    cx, y, cz = math.floor(pos[0]), math.floor(pos[1]), math.floor(pos[2])
    # 1. the old End gate stood 6 blocks north of the island centre, one layer below the feet
    frames = 0
    for dx in range(-3, 4):
        for dz in range(-9, -3):
            if "passed" in as_bot(f"execute if block {cx+dx} {y-1} {cz+dz} minecraft:end_portal_frame"):
                frames += 1
    say(f"progress: end portal frames around the old gate spot: {frames}")
    if frames:
        failures.append(f"the island still has {frames} End portal frames")
    as_bot(f"tp @s {cx+0.5} {y} {cz+2.5} 0 20")
    time.sleep(2)
    shot(tools, shots / "pr-1-no-end-gate.png")
    # 2. sealed End
    command("island end")
    time.sleep(2)
    shot(tools, shots / "pr-2-end-sealed.png")
    if "sb_end" in dimension():
        failures.append("/island end worked without a Void Lens")
    # 3. tiers II, III and IV then the End
    rcon.command("clear E2EBot")
    for item in ("bronze_core", "ember_core", "void_lens", "void_sigil", "matriarch_egg", "skin_flame_brand",
                 "bronze_cleaver", "voidglass", "solar_panel"):
        rcon.command(f"sbgive {item} 1 E2EBot")
    rcon.command("sbgive starcore 1 E2EBot")
    time.sleep(2)
    shot(tools, shots / "pr-3-items.png")
    subprocess.run([xdo, "key", "e"], env=env)
    time.sleep(2)
    shot(tools, shots / "pr-3b-inventory.png")
    subprocess.run([xdo, "key", "Escape"], env=env)
    time.sleep(1)
    use(3)  # the lens before tier III: refused
    shot(tools, shots / "pr-4-lens-refused.png")
    use(1)
    use(2)
    use(3)
    shot(tools, shots / "pr-5-lens-used.png")
    command("island end")
    if not wait_dimension("sb_end", 90):
        return
    time.sleep(6)
    shot(tools, shots / "pr-6-the-end.png")
    # 4. the Voidwyrm at the island's altar
    rcon.command("gamemode creative E2EBot")  # the Voidwyrm kills a bare player in two seconds: the bot only looks
    use(4)
    time.sleep(6)
    shot(tools, shots / "pr-7-voidwyrm.png")
    out = active_bosses()
    say("progress: boss active (End): " + out.strip()[:160].replace("\n", " "))
    if "voidwyrm" not in out.lower():
        failures.append("the Void Sigil did not summon the Voidwyrm: " + out.strip()[:120])
    rcon.command("boss kill all")
    rcon.command("gamemode survival E2EBot")
    time.sleep(2)
    command("island home")
    if not wait_dimension("sb_world", 60):
        return
    time.sleep(5)
    # 5. the Grove key is a Bastion key now: it must NOT summon the boss in the open world (GDD 12.3)
    rcon.command("gamemode creative E2EBot")
    use(5, 20)
    time.sleep(5)
    shot(tools, shots / "pr-8-matriarch.png")
    out = active_bosses()
    say("progress: boss active (island): " + out.strip()[:160].replace("\n", " "))
    if "matriarch" in out.lower() or "mycelial" in out.lower():
        failures.append("the Grove Bastion key summoned the boss in the open world: " + out.strip()[:120])
    rcon.command("boss kill all")
    rcon.command("gamemode survival E2EBot")
    time.sleep(2)
    # 6. skin token then /skins
    use(6)
    shot(tools, shots / "pr-9-skin-unlocked.png")
    command("skins", 4)
    shot(tools, shots / "pr-10-skins-menu.png")
    subprocess.run([xdo, "key", "Escape"], env=env)
    time.sleep(1)
    # 7. dungeons
    command("dungeon", 4)
    shot(tools, shots / "pr-11-dungeon-menu.png")
    subprocess.run([xdo, "key", "Escape"], env=env)
    time.sleep(1)
    command("dungeon start 1", 5)
    for _ in range(120):
        if "sb_dungeon" in dimension():
            break
        time.sleep(1)
    else:
        failures.append("the dungeon run never started: " + dimension())
        return
    time.sleep(8)
    shot(tools, shots / "pr-12-in-dungeon.png")
    command("dungeon leave", 6)
    if not wait_dimension("sb_world", 60):
        return
    shot(tools, shots / "pr-13-back-on-island.png")
    log = (server / "console.log").read_text(errors="replace")
    for line in log.splitlines():
        if ("Exception" in line or "SEVERE" in line) and " WARN]" not in line and "VampyuyuSurvival" in line:
            failures.append("server error: " + line[:160])
            break
    rcon.command("gamemode creative E2EBot")


def dungeon_scenario(ctx):
    """A real dungeon floor: three mob rooms, the floor's boss with its warned attacks and its shield, and the sword
    effects on a target inside the dungeon. Admin shortcuts stand in for walking through the rooms."""
    import re as _re
    tools, env, shots = (ctx[k] for k in ("tools", "env", "shots"))
    failures = ctx["failures"]
    xdo, rcon = tools["xdotool"], Rcon()
    rcon.command("op E2EBot")

    def as_bot(cmd):
        return rcon.command(f"execute as E2EBot at @s run {cmd}")

    def typed(cmd):
        """Admin commands that need a real player: the bot types them."""
        subprocess.run([xdo, "key", "t"], env=env)
        time.sleep(0.8)
        subprocess.run([xdo, "type", "--delay", "20", "/" + cmd], env=env)
        time.sleep(0.3)
        subprocess.run([xdo, "key", "Return"], env=env)
        time.sleep(0.6)

    rcon.command("gamemode survival E2EBot")
    typed("menu skyblock")  # a dungeon is reached from the Skyblock, not from another game mode
    for _ in range(150):
        if "sb_world" in rcon.command("data get entity E2EBot Dimension"):
            break
        time.sleep(1)
    else:
        failures.append("never reached sb_world")
        return
    time.sleep(6)
    rcon.command("gamerule spawn_mobs false")
    typed("dungeon admin start 5 7")
    shot(tools, shots / "dg-start-typed.png")
    dimension = ""
    for _ in range(60):
        time.sleep(2)
        dimension = rcon.command("data get entity E2EBot Dimension")
        if "sb_dungeon" in dimension:
            break
    else:
        shot(tools, shots / "dg-start-failed.png")
        failures.append("the dungeon floor never started: " + dimension[:100])
        return
    rcon.command("gamerule send_command_feedback false")

    def protect():
        """The bot does not fight back: it must stay alive to be moved around (a ghost is not)."""
        rcon.command("effect give E2EBot minecraft:resistance 600 4 true")
        rcon.command("effect give E2EBot minecraft:regeneration 600 4 true")

    for _ in range(14):  # the countdown
        protect()
        time.sleep(1)
    shot(tools, shots / "dg-0-entrance.png")
    # the sword effects, on a plain target in the dungeon world
    def health():
        found = _re.findall(r"([0-9.]+)f", rcon.command("data get entity @e[tag=e2e_target,limit=1] Health"))
        return float(found[0]) if found else None

    rcon.command("kill @e[type=minecraft:zombie]")
    found = _re.findall(r"(-?[0-9.]+)d", rcon.command("data get entity E2EBot Pos"))
    x, y, z = [float(v) for v in found[:3]]
    as_bot(f"tp @s {x} {y} {z} 180 10")
    as_bot(f"summon zombie {x} {y} {z - 2} {{NoAI:1b,Silent:1b,Tags:[\"e2e_target\"],"
           "attributes:[{id:\"minecraft:max_health\",base:1000}],Health:1000f}")
    rcon.command("clear E2EBot")
    rcon.command("sbgive bronze_cleaver 1 E2EBot")
    subprocess.run([xdo, "key", "1"], env=env)
    time.sleep(2)
    before = health()
    subprocess.run([xdo, "click", "1"], env=env)
    time.sleep(0.5)
    after_hit = health()
    time.sleep(4)
    after_bleed = health()
    say(f"dungeon: Bronze Cleaver on a target: {before} -> {after_hit} after the hit, {after_bleed} after the bleed")
    if None in (before, after_hit, after_bleed) or before - after_hit < 30:
        failures.append(f"a Bronze Cleaver hit in a dungeon dealt {before} -> {after_hit}")
    elif after_hit - after_bleed < 3:
        failures.append(f"Rend did not bleed in a dungeon: {after_hit} -> {after_bleed}")
    rcon.command("clear E2EBot")
    rcon.command("sbgive rimeblade 1 E2EBot")
    as_bot(f"tp @s {x} {y} {z} 180 10")
    as_bot(f"tp @e[tag=e2e_target] {x} {y} {z - 2}")  # the cleaver's hit knocked it back
    time.sleep(2)  # a full swing
    subprocess.run([xdo, "click", "1"], env=env)
    time.sleep(0.5)
    effects = rcon.command("data get entity @e[tag=e2e_target,limit=1] active_effects")
    say("dungeon: after a Rimeblade hit: " + effects[:120])
    if "slowness" not in effects:
        failures.append("Frostbite did not slow in a dungeon: " + effects[:120])
    # two swings in a row: the bleed must not cost the second one its damage
    rcon.command("kill @e[tag=e2e_target]")
    rcon.command("clear E2EBot")
    rcon.command("sbgive bronze_cleaver 1 E2EBot")
    as_bot(f"summon zombie {x} {y} {z - 2} {{NoAI:1b,Silent:1b,Tags:[\"e2e_target\"],"
           "attributes:[{id:\"minecraft:max_health\",base:1000}],Health:1000f}")
    time.sleep(2)
    before = health()
    subprocess.run([xdo, "click", "1"], env=env)
    time.sleep(1.5)
    as_bot(f"tp @e[tag=e2e_target] {x} {y} {z - 2}")
    subprocess.run([xdo, "click", "1"], env=env)
    time.sleep(0.4)
    two = health()
    say(f"dungeon: two swings 1.5 s apart: {before} -> {two}")
    if None in (before, two) or before - two < 60:
        failures.append(f"two Bronze Cleaver swings only dealt {before} -> {two}")
    rcon.command("kill @e[tag=e2e_target]")
    rcon.command("clear E2EBot")

    protect()
    typed("dungeon admin goto BOSS")
    time.sleep(0.8)
    shot(tools, shots / "dg-2-boss-intro.png")
    time.sleep(5)
    shot(tools, shots / "dg-2-boss-spawned.png")
    def clear_room():
        """Only the boss stays: the guards and what they bring would stand in front of the camera."""
        rcon.command("kill @e[type=!minecraft:player,type=!minecraft:wither_skeleton,type=!minecraft:item_display,type=!minecraft:text_display,type=!minecraft:armor_stand]")

    found = _re.findall(r"(-?[0-9.]+)d", rcon.command("execute as E2EBot at @s run data get entity @e[type=minecraft:wither_skeleton,limit=1,sort=nearest] Pos"))
    bx, by, bz = [float(v) for v in found[:3]]

    def watch():
        """A fixed spot of the boss room, looking at the boss and at the floor around it."""
        as_bot(f"tp @s {bx} {by} {bz + 12} 180 24")

    for i in range(16):
        clear_room()
        watch()
        time.sleep(1.2)
        shot(tools, shots / f"dg-3-attack{i}.png")
    watch()
    typed("dungeon admin hurtboss 50")
    time.sleep(1.5)
    def boss_health():
        found = _re.findall(r"([0-9.]+)f", rcon.command("execute as E2EBot at @s run data get entity @e[type=minecraft:wither_skeleton,limit=1,sort=nearest] Health"))
        return float(found[0]) if found else None

    before = boss_health()
    # far more than its whole health: through the shield this would end the fight
    as_bot("damage @e[type=minecraft:wither_skeleton,limit=1,sort=nearest] 10000000 minecraft:player_attack by E2EBot")
    time.sleep(0.5)
    after = boss_health()
    time.sleep(4)
    later = boss_health()
    say(f"dungeon: a huge hit on the shielded boss: {before} -> {after}, 4 s later {later}")
    if later is not None and after is not None and later < after:
        failures.append(f"the boss lost health with nobody hitting it: {after} -> {later}")
    if before is None or after is None or after < before:
        failures.append(f"the shield let a hit through: {before} -> {after}")
    shot(tools, shots / "dg-4-shielded.png")
    if "E2EBot" not in rcon.command("list"):
        failures.append("the bot left the run")
    rcon.command("kill @e[type=minecraft:blaze]")
    time.sleep(2)
    shot(tools, shots / "dg-5-shield-broken.png")

    rcon.command("clear E2EBot")
    for i in range(2):
        protect()
        typed("dungeon admin goto MOB" + (" HOLD" if i == 0 else ""))
        as_bot("tp @s ~ ~ ~6 180 20")
        time.sleep(0.8)
        shot(tools, shots / f"dg-1-room{i}-title.png")
        time.sleep(4)
        shot(tools, shots / f"dg-1-room{i}-fight.png")


def tools_scenario(ctx):
    """The bows, axes and shovels: held, in the inventory, a bow being drawn, and what an arrow really deals."""
    import re as _re
    tools, env, shots = (ctx[k] for k in ("tools", "env", "shots"))
    failures = ctx["failures"]
    xdo, rcon = tools["xdotool"], Rcon()
    rcon.command("op E2EBot")
    rcon.command("gamemode creative E2EBot")
    rcon.command("clear E2EBot")
    tiers = ["bronze", "rime", "ember", "void", "starcore"]
    kinds = ["axe", "shovel", "bow"]
    for kind in kinds:
        rcon.command("clear E2EBot")
        for tier in tiers:
            rcon.command(f"sbgive {tier}_{kind} 1 E2EBot")
        time.sleep(2)
        for i, tier in enumerate(tiers):
            subprocess.run([xdo, "key", str(i + 1)], env=env)
            time.sleep(1.2)
            shot(tools, shots / f"to-{kind}-{tier}.png")
        subprocess.run([xdo, "key", "e"], env=env)
        time.sleep(2)
        shot(tools, shots / f"to-{kind}-inventory.png")
        subprocess.run([xdo, "key", "Escape"], env=env)
        time.sleep(1)
        subprocess.run([xdo, "key", "F5", "F5"], env=env)  # third person, from the front
        time.sleep(2)
        shot(tools, shots / f"to-{kind}-third-person.png")
        subprocess.run([xdo, "key", "F5"], env=env)
        time.sleep(1)
    # a full draw and a real arrow on a still dummy, in survival, at a void bow (damage 180)
    rcon.command("clear E2EBot")
    rcon.command("sbgive void_bow 1 E2EBot")
    rcon.command("give E2EBot minecraft:arrow 16")
    rcon.command("gamemode survival E2EBot")
    pos = rcon.command("data get entity E2EBot Pos")
    x, y, z = [float(v) for v in _re.findall(r"(-?[0-9.]+)d", pos)][:3]
    rcon.command(f"execute as E2EBot run tp @s {x} {y} {z} 180 0")
    rcon.command(f"summon zombie {x} {y} {z - 4} {{NoAI:1b,Silent:1b,Tags:[\"e2e_dummy\"],"
                 "attributes:[{id:\"minecraft:max_health\",base:1000}],Health:1000f}")
    time.sleep(2)
    subprocess.run([xdo, "key", "1"], env=env)
    time.sleep(0.5)

    def dummy_health():
        found = _re.findall(r"([0-9.]+)f", rcon.command("data get entity @e[tag=e2e_dummy,limit=1] Health"))
        return float(found[0]) if found else None

    before = dummy_health()
    subprocess.run([xdo, "mousedown", "3"], env=env)
    time.sleep(0.25)
    shot(tools, shots / "to-bow-draw-start.png")
    time.sleep(1.2)
    shot(tools, shots / "to-bow-draw-full.png")
    subprocess.run([xdo, "mouseup", "3"], env=env)
    time.sleep(2)
    after = dummy_health()
    dealt = None if before is None or after is None else before - after
    say(f"tools: a full draw of the Void Bow on the dummy: health {before} -> {after}, dealt {dealt}")
    if dealt is None or dealt < 100:
        failures.append(f"a Void Bow arrow dealt {dealt} (expected about 180, vanilla would be about 6-9)")


def imported_scenario(ctx):
    """The hand-imported items that became 3D: the five main swords, the five pickaxes and the five ores, each held
    (first person), then in the inventory (the flat sprite) and in third person."""
    tools, env, shots = (ctx[k] for k in ("tools", "env", "shots"))
    xdo, rcon = tools["xdotool"], Rcon()
    rcon.command("op E2EBot")
    rcon.command("gamemode creative E2EBot")
    groups = {
        "sword": ["weapon/bronze_cleaver", "weapon/rimeblade", "weapon/ember_sword", "weapon/void_sword", "weapon/starcore_sword"],
        "pick": [f"tool/{t}_pick" for t in ("bronze", "rime", "ember", "void", "starcore")],
        "ore": ["ore/bronzite", "ore/frostium", "ore/sunstone", "ore/voidglass", "ore/starcore"],
        "core": ["ore/bronze_core", "ore/ember_core", "ore/void_lens", "ore/pebble"],
        # one whole tier side by side: are the pick, axe, shovel, bow, sword, ore and core of one family?
        "tier": ["tool/bronze_pick", "tool/bronze_axe", "tool/bronze_shovel", "tool/bronze_bow", "weapon/bronze_cleaver",
                 "ore/bronzite", "ore/bronze_core", "skin/kitsune", "ore/pebble"],
        # held blocks: the machines against a vanilla stone and furnace (a vanilla block is the size reference)
        "machine": ["minecraft:stone", "forge/forge", "machine/solar_panel", "machine/miner_1", "minecraft:furnace"],
    }
    only = os.environ.get("E2E_GROUPS")
    for group, models in groups.items():
        if only and group not in only.split(","):
            continue
        rcon.command("clear E2EBot")
        for i, m in enumerate(models):
            if m.startswith("minecraft:"):
                rcon.command(f"item replace entity E2EBot hotbar.{i} with {m}")
            else:
                rcon.command(f'item replace entity E2EBot hotbar.{i} with minecraft:iron_sword[item_model="vampyuyu:{m}"]')
        time.sleep(3)
        for i, m in enumerate(models):
            subprocess.run([xdo, "key", str(i + 1)], env=env)
            time.sleep(1.5)
            shot(tools, shots / f"im-{group}-{i + 1}-{m.replace(":", "/").split("/")[-1]}.png")
        subprocess.run([xdo, "key", "e"], env=env)
        time.sleep(2)
        shot(tools, shots / f"im-{group}-inventory.png")
        subprocess.run([xdo, "key", "Escape"], env=env)
        time.sleep(1)
        subprocess.run([xdo, "key", "F5", "F5"], env=env)
        time.sleep(2)
        shot(tools, shots / f"im-{group}-third-person.png")
        subprocess.run([xdo, "key", "F5"], env=env)
        time.sleep(1)
        # dropped on the ground, next to a vanilla raw copper for scale: what a player sees after a drop
        rcon.command("tp E2EBot 0.5 -60 0.5 0 35")
        rcon.command("kill @e[type=item]")
        for i, m in enumerate(["minecraft:raw_copper"] + [x for x in models if not x.startswith("minecraft:")]):
            stack = ('{id:"minecraft:raw_copper",count:1}' if m.startswith("minecraft:") else
                     '{id:"minecraft:iron_sword",count:1,components:{"minecraft:item_model":"vampyuyu:%s"}}' % m)
            rcon.command(f"summon item {i * 0.8 - 2.0:.1f} -60 3.0 {{Item:{stack},PickupDelay:32767,Age:-32768}}")
        time.sleep(2)
        shot(tools, shots / f"im-{group}-dropped.png")
        rcon.command("kill @e[type=item]")


def swords_scenario(ctx):
    """The two weapons and the six skins as real 3D box models: each one held in first person, then the inventory."""
    tools, env, shots = (ctx[k] for k in ("tools", "env", "shots"))
    xdo, rcon = tools["xdotool"], Rcon()
    rcon.command("op E2EBot")
    rcon.command("gamemode creative E2EBot")
    rcon.command("clear E2EBot")
    models = ["weapon/bronze_cleaver", "weapon/rimeblade", "skin/kitsune", "skin/titan_edge", "skin/crystal_saber",
              "skin/flame_brand", "skin/frost_fang", "skin/wyrmfang"]
    for i, m in enumerate(models):
        rcon.command(f'item replace entity E2EBot hotbar.{i} with minecraft:iron_sword[item_model="vampyuyu:{m}"]')
    time.sleep(3)
    for i, m in enumerate(models):
        subprocess.run([xdo, "key", str(i + 1)], env=env)
        time.sleep(1.5)
        shot(tools, shots / f"sw-{i + 1}-{m.replace(":", "/").split("/")[-1]}.png")
    subprocess.run([xdo, "key", "e"], env=env)
    time.sleep(2)
    shot(tools, shots / "sw-9-inventory.png")
    subprocess.run([xdo, "key", "Escape"], env=env)
    time.sleep(1)
    subprocess.run([xdo, "key", "F5", "F5"], env=env)  # third person, from the front: the sword in the model's hand
    time.sleep(2)
    shot(tools, shots / "sw-10-third-person.png")
    # the same pose with a vanilla iron sword then the Bronze Cleaver: a ladder weapon must be held like a vanilla one
    rcon.command("item replace entity E2EBot weapon.mainhand with minecraft:iron_sword")
    time.sleep(1)
    shot(tools, shots / "sw-11-vanilla-third.png")
    subprocess.run([xdo, "key", "F5"], env=env)  # front -> first person
    time.sleep(1)
    shot(tools, shots / "sw-12-vanilla-first.png")
    rcon.command("item replace entity E2EBot weapon.mainhand with minecraft:air")
    rcon.command("sbgive bronze_cleaver 1 E2EBot")
    time.sleep(1)
    shot(tools, shots / "sw-13-cleaver-first.png")
    subprocess.run([xdo, "key", "F5"], env=env)
    time.sleep(0.5)
    subprocess.run([xdo, "key", "F5"], env=env)  # first -> behind -> front
    time.sleep(1)
    shot(tools, shots / "sw-14-cleaver-third.png")
    # starcore armor is tougher than void armor (both are netherite underneath)
    rcon.command("clear E2EBot")
    armor_values = {}
    for tier in ("void", "starcore"):
        rcon.command(f"sbgive {tier}_chestplate 1 E2EBot")
        total, listed = 0.0, []
        for idx in range(6):
            data = rcon.command(f'data get entity E2EBot Inventory[0].components."minecraft:attribute_modifiers"[{idx}]')
            if "Found no elements" in data:
                break
            m = re.search(r'type: "minecraft:(armor|armor_toughness)", amount: ([0-9.]+)', data)
            if m:
                listed.append(f"{m.group(1)}={m.group(2)}")
                if m.group(1) == "armor":
                    total += float(m.group(2))
        # a piece without its own list wears the base material's: 8 armor for a netherite chestplate
        armor_values[tier] = total if listed else 8.0
        say(f"armor: {tier}_chestplate -> {armor_values[tier]} ({listed or 'base material'})")
        rcon.command("clear E2EBot")
    if not armor_values["starcore"] > armor_values["void"]:
        ctx["failures"].append(f"starcore armor is not tougher than void armor: {armor_values}")
    # the five armor tiers worn, seen from the front then the back in third person, without the HUD and chat
    rcon.command("clear E2EBot")
    subprocess.run([xdo, "key", "F1"], env=env)
    for tier in ("bronze", "rime", "ember", "void", "starcore"):
        for i, slot in enumerate(("helmet", "chestplate", "leggings", "boots")):
            rcon.command(f"sbgive {tier}_{slot} 1 E2EBot")
        for source, target in ((0, "head"), (1, "chest"), (2, "legs"), (3, "feet")):
            rcon.command(f"item replace entity E2EBot armor.{target} from entity E2EBot container.{source}")
        time.sleep(2)
        shot(tools, shots / f"ar-{tier}.png")
        subprocess.run([xdo, "key", "F5"], env=env)  # front -> first person
        time.sleep(0.5)
        subprocess.run([xdo, "key", "F5"], env=env)  # -> from behind (the wings)
        time.sleep(1.5)
        shot(tools, shots / f"ar-{tier}-back.png")
        subprocess.run([xdo, "key", "F5"], env=env)  # -> from the front again
        time.sleep(1)
        rcon.command("item replace entity E2EBot armor.head with minecraft:air")
        rcon.command("item replace entity E2EBot armor.chest with minecraft:air")
        rcon.command("item replace entity E2EBot armor.legs with minecraft:air")
        rcon.command("item replace entity E2EBot armor.feet with minecraft:air")
        rcon.command("clear E2EBot")
    subprocess.run([xdo, "key", "F1"], env=env)


def armorwings_scenario(ctx):
    """Every Skyblock armor set and every Wings tier worn, in third person (front, back, three-quarter side), the
    Wings standing and gliding, plus a first-person shot with the Wings on. Shots: aw-*.png (armor) and ww-*.png."""
    import math
    tools, env, shots, failures = (ctx[k] for k in ("tools", "env", "shots", "failures"))
    xdo, rcon = tools["xdotool"], Rcon()
    state = {"view": 0}  # 0 first person, 1 behind, 2 front (F5 cycles 0 -> 1 -> 2 -> 0)

    def view(want):
        while state["view"] != want:
            subprocess.run([xdo, "key", "F5"], env=env)
            state["view"] = (state["view"] + 1) % 3
            time.sleep(0.4)

    def as_bot(text):
        return rcon.command("execute as E2EBot at E2EBot run " + text)

    def jump():  # a real key press lasting a few ticks (a bare "key" is too short for the client to see)
        subprocess.run([xdo, "keydown", "space"], env=env)
        time.sleep(0.2)
        subprocess.run([xdo, "keyup", "space"], env=env)

    def put(x, y, z, yaw, pitch=0):
        as_bot(f"tp @s {x} {y} {z} {yaw} {pitch}")

    rcon.command("op E2EBot")
    rcon.command("gamemode survival E2EBot")
    rcon.command("gamerule spawn_mobs false")
    rcon.command("kill @e[type=slime]")
    subprocess.run([xdo, "key", "slash"], env=env)
    time.sleep(.5)
    subprocess.run([xdo, "type", "--delay", "35", "menu skyblock"], env=env)
    subprocess.run([xdo, "key", "Return"], env=env)
    for _ in range(150):
        if "sb_world" in rcon.command("data get entity E2EBot Dimension"):
            break
        time.sleep(1)
    else:
        failures.append("never reached sb_world")
        return
    time.sleep(8)
    pos = player_state(rcon)["pos"]
    cx, y, cz = math.floor(pos[0]) + 20, math.floor(pos[1]), math.floor(pos[2])
    as_bot(f"fill {cx-12} {y-1} {cz-12} {cx+12} {y-1} {cz+12} smooth_sandstone")
    as_bot(f"fill {cx-12} {y} {cz-12} {cx+12} {y+6} {cz+12} air")
    rcon.command("time set noon")
    rcon.command("weather clear")
    rcon.command("clear E2EBot")
    subprocess.run([xdo, "key", "F1"], env=env)  # no HUD, no chat
    time.sleep(1)

    def clear_worn():
        for slot in ("head", "chest", "legs", "feet"):
            rcon.command(f"item replace entity E2EBot armor.{slot} with minecraft:air")
        rcon.command("clear E2EBot")

    def three_views(prefix):
        """Front, back, then the three-quarter side (the head turned ~70 degrees from the body)."""
        put(cx + .5, y, cz + .5, 0)
        time.sleep(2.5)
        view(2)
        time.sleep(1)
        shot(tools, shots / f"{prefix}-front.png")
        view(1)
        time.sleep(1)
        shot(tools, shots / f"{prefix}-back.png")
        put(cx + .5, y, cz + .5, 70)
        time.sleep(0.35)
        shot(tools, shots / f"{prefix}-side.png")
        put(cx + .5, y, cz + .5, 0)
        time.sleep(1)

    # 1. armor sets
    only = os.environ.get("ARMORWINGS_ONLY", "")  # "armor" or "wings" to run half of it
    for tier in () if only == "wings" else ("bronze", "ember", "rime", "void", "starcore", "cinder_starcore", "grove_starcore", "frost_starcore"):
        clear_worn()
        for slot in ("helmet", "chestplate", "leggings", "boots"):
            rcon.command(f"sbgive {tier}_{slot} 1 E2EBot")
        for source, target in ((0, "head"), (1, "chest"), (2, "legs"), (3, "feet")):
            rcon.command(f"item replace entity E2EBot armor.{target} from entity E2EBot container.{source}")
        for source in range(4):  # nothing left in the hand
            rcon.command(f"item replace entity E2EBot container.{source} with minecraft:air")
        for slot in ("head", "chest", "legs", "feet"):  # the RCON reply of a full inventory dump is cut, ask per slot
            if "passed" not in rcon.command(f"execute if items entity E2EBot armor.{slot} *").lower():
                failures.append(f"{tier}: nothing worn in armor.{slot}")
        three_views(f"aw-{tier}")
    clear_worn()

    # 2. wings, one pair per tier (levels 1, 3, 5, 7, 9)
    levels = [int(v) for v in os.environ.get("ARMORWINGS_LEVELS", "1,3,5,7,9").split(",")]
    for tier, level in [((lv + 1) // 2, lv) for lv in ([] if only == "armor" else levels)]:
        clear_worn()
        say(rcon.command(f"wings give E2EBot {level}").strip())
        time.sleep(1)
        inv = rcon.command("data get entity E2EBot Inventory")
        say(f"wings tier {tier}: inventory={inv.strip()[:300]}")
        if "wings" not in inv.lower() and "elytra" not in inv.lower():
            failures.append(f"no wings after /wings give level {level}")
        say("equip: " + rcon.command("item replace entity E2EBot armor.chest from entity E2EBot container.0").strip()[:200])
        time.sleep(1)
        worn = rcon.command("execute if items entity E2EBot armor.chest minecraft:elytra").strip()
        say("chest slot after the command: " + worn[:200] + " | slot 0: "
            + rcon.command("execute if items entity E2EBot container.0 minecraft:elytra").strip()[:80])
        if "passed" not in worn.lower():  # like a player: hold them and right-click
            view(0)
            subprocess.run([xdo, "key", "1"], env=env)
            time.sleep(0.5)
            subprocess.run([xdo, "click", "3"], env=env)
            time.sleep(1)
            worn = rcon.command("execute if items entity E2EBot armor.chest minecraft:elytra").strip()
            say("chest slot after the right-click: " + worn[:200])
        if "passed" not in worn.lower():
            failures.append(f"tier {tier}: the Wings are not in the chest slot")
        rcon.command("effect give E2EBot resistance 999 6 true")  # a failed glide must not end in a death screen
        say("wings info: " + re.sub(r"§.", "", rcon.command("wings info E2EBot")).strip()[:600])
        put(cx + .5, y, cz + .5, 0)
        time.sleep(3)
        if tier == 1:
            view(0)
            shot(tools, shots / "ww-first-person-standing.png")
        three_views(f"ww-t{tier}-stand")
        # gliding: drop from high above, press jump once in the air to open the wings
        for name, yaw, vw in (("back", 0, 1), ("side", 70, 1), ("front", 0, 2)):
            put(cx + .5, y + 90, cz + .5, 0, 8)
            time.sleep(0.4)
            view(vw)
            if name == "side":
                time.sleep(1.2)
                put(cx + .5, y + 88, cz + .5, 0, 8)
                time.sleep(0.8)
            time.sleep(0.8)
            jump()
            time.sleep(1.5)
            if name == "side":
                as_bot("tp @s ~ ~ ~ 70 8")
                time.sleep(0.3)
            fall = rcon.command("data get entity E2EBot FallFlying").strip()
            say(f"wings tier {tier} glide {name}: FallFlying -> {fall}")
            if "1b" not in fall:
                failures.append(f"tier {tier} {name}: the bot is not gliding ({fall})")
            shot(tools, shots / f"ww-t{tier}-glide-{name}.png")
            time.sleep(0.6)
            shot(tools, shots / f"ww-t{tier}-glide-{name}-b.png")  # another flap phase
        put(cx + .5, y, cz + .5, 0)
        time.sleep(2)
        if tier == 5:
            put(cx + .5, y + 90, cz + .5, 0, 8)
            time.sleep(1.2)
            view(0)
            jump()
            time.sleep(1.5)
            shot(tools, shots / "ww-first-person-glide.png")
            put(cx + .5, y, cz + .5, 0)
            time.sleep(2)
    view(0)
    subprocess.run([xdo, "key", "F1"], env=env)


def start_scenario(ctx):
    """The start of the game with a real client: pebbles from the ground, the Fountain, a stone ring that makes it a
    generator, a Spring Stone, cables on redstone, and the quest menus."""
    import math
    tools, env, shots, failures, server = (ctx[k] for k in ("tools", "env", "shots", "failures", "server"))
    xdo, rcon = tools["xdotool"], Rcon()

    def command(text, wait=3):
        subprocess.run([xdo, "key", "slash"], env=env)
        time.sleep(.5)
        subprocess.run([xdo, "type", "--delay", "35", text], env=env)
        subprocess.run([xdo, "key", "Return"], env=env)
        time.sleep(wait)

    def as_bot(text):
        return rcon.command("execute as E2EBot at E2EBot run " + text)

    def stand(x, y, z, yaw, pitch):
        as_bot(f"tp @s {x} {y} {z} {yaw} {pitch}")
        time.sleep(1.5)

    def use(slot, x, y, z, yaw, pitch):
        subprocess.run([xdo, "key", str(slot)], env=env)
        time.sleep(.5)
        stand(x, y, z, yaw, pitch)
        subprocess.run([xdo, "click", "3"], env=env)
        time.sleep(1.5)

    rcon.command("op E2EBot")
    rcon.command("gamemode survival E2EBot")
    rcon.command("gamerule spawn_mobs false")
    rcon.command("kill @e[type=slime]")
    command("menu skyblock")
    for _ in range(150):
        if "sb_world" in rcon.command("data get entity E2EBot Dimension"):
            break
        time.sleep(1)
    else:
        failures.append("never reached sb_world")
        return
    time.sleep(8)
    pos = player_state(rcon)["pos"]
    cx, y, cz = math.floor(pos[0]) + 6, math.floor(pos[1]), math.floor(pos[2])
    as_bot(f"fill {cx-3} {y-1} {cz-3} {cx+8} {y-1} {cz+9} grass_block")
    as_bot(f"fill {cx-3} {y} {cz-3} {cx+8} {y+4} {cz+9} air")
    rcon.command("clear E2EBot")
    # 1. pebbles: right-click the ground with an empty hand
    stand(cx + 1.5, y, cz + 4.5, 180, 40)
    for _ in range(6):
        subprocess.run([xdo, "click", "3"], env=env)
        time.sleep(0.8)
    shot(tools, shots / "st-1-pebbles.png")
    inv = rcon.command("data get entity E2EBot Inventory")
    say("start: inventory after the clicks: " + inv.strip()[:200])
    if "count: 6" not in inv and "count: 5" not in inv:
        failures.append("right-clicking the ground gave no pebble")
    # 2. craft cobblestone and the Fountain are checked by the unit tests; give the items for the build
    rcon.command("clear E2EBot")
    for item in ("fountain 1", "spring_stone 2", "cable 3"):
        rcon.command("sbgive " + item + " E2EBot")
    # the ring of stone bricks around the Fountain at (cx, y, cz), with the Spring Stone on the south side
    use(1, cx + 0.5, y, cz + 2.5, 180, 39)  # Fountain on the floor in front
    as_bot("")
    time.sleep(1)
    shot(tools, shots / "st-2-fountain.png")
    for dx, dz in ((-1, -1), (0, -1), (1, -1), (-1, 0), (1, 0), (-1, 1), (1, 1)):
        as_bot(f"setblock {cx+dx} {y} {cz+dz} stone_bricks")
    time.sleep(1)
    stand(cx + 0.5, y, cz + 4.5, 180, 28)
    shot(tools, shots / "st-3-ring-missing-spring.png")
    # right-click the Fountain: it says what is missing
    stand(cx + 0.5, y, cz + 3.5, 180, 40)
    subprocess.run([xdo, "click", "3"], env=env)
    time.sleep(1)
    shot(tools, shots / "st-4-missing-message.png")
    # 3. the Spring Stone on the south side (slot 2)
    use(2, cx + 0.5, y, cz + 3.5, 180, 40)
    time.sleep(2)
    stand(cx + 0.5, y, cz + 5.5, 180, 25)
    shot(tools, shots / "st-5-generator.png")
    block_above = as_bot(f"data get block {cx} {y+1} {cz} id")
    time.sleep(3)
    above = as_bot(f"execute if block {cx} {y+1} {cz} minecraft:cobblestone")
    say("start: above raw: " + repr(above) + " / " + repr(block_above))
    say("start: block above the generator: " + above.strip())
    if "passed" not in above:
        failures.append("the generator made no cobblestone above the Fountain")
    # 4. cables: placed while facing different ways, they must join each other and the machine next to them
    #    an L: (cx+5,cz+2) and (cx+4,cz+2) placed facing east, (cx+5,cz+3) facing north. The far one goes first: a click
    #    whose ray crosses a cable's hit cube targets that cable (status message, nothing placed)
    for x, z, yaw, dx_, dz_ in ((5, 2, -90, -2.0, 0), (4, 2, -90, -2.0, 0), (5, 3, 180, 0, 2.0)):
        use(3, cx + x + 0.5 + dx_, y, cz + z + 0.5 + dz_, yaw, 39)
    if "Found no elements" not in rcon.command("data get entity E2EBot Inventory[{Slot:2b}]"):
        failures.append("a cable click placed nothing: cables are left in the hotbar")
    time.sleep(2)
    stand(cx + 5.0, y + 7, cz + 3.0, 180, 85)
    shot(tools, shots / "st-6-cables-above.png")
    stand(cx + 5.0, y, cz + 6.5, 180, 30)
    shot(tools, shots / "st-6-cables.png")
    masks = []
    for x, z in ((4, 2), (5, 2), (5, 3)):
        item = rcon.command(f"execute positioned {cx+x+0.5} {y+0.5} {cz+z+0.5} run data get entity "
                            "@e[type=item_display,limit=1,sort=nearest] item.components.\"minecraft:item_model\"")
        masks.append(item.strip().split()[-1])
    say("start: cable models: " + str(masks))
    if masks != ['"vampyuyu:machine/cable_2"', '"vampyuyu:machine/cable_12"', '"vampyuyu:machine/cable_1"']:
        failures.append(f"the cables do not join: {masks}")
    # sneaking skips the status message: the click must still not replace the cable it aims at (slot 1 is free again)
    rcon.command("sbgive cable 1 E2EBot")
    subprocess.run([xdo, "keydown", "shift"], env=env)
    time.sleep(.5)
    use(1, cx + 3.5, y, cz + 2.5, -90, 38)
    subprocess.run([xdo, "keyup", "shift"], env=env)
    if "Found no elements" in rcon.command("data get entity E2EBot Inventory[{Slot:0b}]"):
        failures.append("a sneaking click ate a cable")
    # 4b. the other generators and a bucket from the Spring Stone, each in its own corner of a bigger floor
    as_bot(f"fill {cx-14} {y-1} {cz-14} {cx+16} {y-1} {cz+16} grass_block")
    as_bot(f"fill {cx-14} {y} {cz-14} {cx+16} {y+4} {cz+16} air")
    rcon.command("clear E2EBot")
    rcon.command("sbgive fountain 3 E2EBot")
    rcon.command("sbgive spring_stone 3 E2EBot")
    rcon.command("give E2EBot minecraft:bucket 1")
    kinds = (("dunes", "sandstone", "spring", -9, 0, ("sand", "gravel", "red_sand")),
             ("frost", "glass", "spring", 10, 0, ("snow_block", "packed_ice")),
             ("ember", "nether_bricks", "magma_block", 0, -9, ("netherrack", "soul_sand")))
    for name, ring, south, fx, fz, outputs in kinds:
        use(1, cx + fx + 0.5, y, cz + fz + 2.5, 180, 39)   # Fountain
        for dx, dz in ((-1, -1), (0, -1), (1, -1), (-1, 0), (1, 0), (-1, 1), (1, 1)):
            as_bot(f"setblock {cx+fx+dx} {y} {cz+fz+dz} {ring}")
        if south == "spring":
            use(2, cx + fx + 0.5, y, cz + fz + 3.5, 180, 40)   # Spring Stone on the south side
        else:
            as_bot(f"setblock {cx+fx} {y} {cz+fz+1} {south}")
    time.sleep(4)
    for name, ring, south, fx, fz, outputs in kinds:
        as_bot(f"setblock {cx+fx} {y+1} {cz+fz} air")  # the plain cobblestone made before the ring was complete
    time.sleep(4)
    for name, ring, south, fx, fz, outputs in kinds:
        stand(cx + fx + 0.5, y, cz + fz + 6.5, 180, 22)
        shot(tools, shots / f"st-gen-{name}.png")
        found = [o for o in outputs if "passed" in as_bot(f"execute if block {cx+fx} {y+1} {cz+fz} minecraft:{o}")]
        say(f"start: {name} generator makes: {found}")
        if not found:
            failures.append(f"the {name} generator made none of {outputs} above its Fountain")
    # the ore of the frost and ember generators: hold the left button on the block above the Fountain and wait
    rcon.command("gamemode creative E2EBot")
    for name, ring, south, fx, fz, outputs in kinds[1:]:
        want = "frostium" if name == "frost" else "sunstone"
        rcon.command("clear E2EBot")
        stand(cx + fx + 0.5, y, cz + fz + 3.5, 180, 4)
        subprocess.run([xdo, "mousedown", "1"], env=env)
        found = False
        for _ in range(40):
            time.sleep(5)
            dropped = rcon.command('execute if entity @e[type=item,nbt={Item:{components:{"minecraft:item_model":"vampyuyu:ore/'
                                   + want + '"}}}]')
            if "passed" in dropped or want in rcon.command("data get entity E2EBot Inventory"):
                found = True
                break
        subprocess.run([xdo, "mouseup", "1"], env=env)
        shot(tools, shots / f"st-ore-{name}.png")
        say(f"start: {name} generator dropped {want} in the real client: {found}")
        if not found:
            failures.append(f"no {want} dropped from the {name} generator blocks in 200 s")
    rcon.command("gamemode survival E2EBot")
    # a bucket filled from a lone Spring Stone (the ore waiting cleared the inventory)
    rcon.command("clear E2EBot")
    rcon.command("sbgive spring_stone 1 E2EBot")
    rcon.command("give E2EBot minecraft:bucket 1")
    use(1, cx + 4.5, y, cz + 8.5, 180, 39)
    subprocess.run([xdo, "key", "2"], env=env)
    time.sleep(.5)
    subprocess.run([xdo, "click", "3"], env=env)
    time.sleep(1.5)
    bucket = rcon.command("data get entity E2EBot Inventory")
    say("start: inventory after the bucket click: " + bucket.strip()[:300])
    if "water_bucket" not in bucket:
        failures.append("a bucket was not filled from the Spring Stone")
    # a machine is a barrier under a model: a survival player breaks it with one punch and gets the item back
    is_barrier = as_bot(f"execute if block {cx+4} {y} {cz+6} minecraft:barrier")
    say("start: the lone Spring Stone block is a barrier: " + is_barrier.strip())
    if "passed" not in is_barrier:
        failures.append("a placed Spring Stone is not a barrier under its model")
    rcon.command("gamemode survival E2EBot")
    stand(cx + 4.5, y, cz + 8.5, 180, 39)
    subprocess.run([xdo, "mousedown", "1"], env=env)
    time.sleep(2.5)
    subprocess.run([xdo, "mouseup", "1"], env=env)
    time.sleep(1)
    gone = as_bot(f"execute if block {cx+4} {y} {cz+6} minecraft:air")
    dropped_item = rcon.command('execute if entity @e[type=item,nbt={Item:{components:{"minecraft:item_model":"vampyuyu:machine/spring_stone"}}}]')
    if "passed" not in dropped_item:  # the bot stands next to it and may already have picked it up
        dropped_item = rcon.command('execute if entity @a[name=E2EBot,nbt={Inventory:[{components:{"minecraft:item_model":"vampyuyu:machine/spring_stone"}}]}]')
    say(f"start: punched the Spring Stone: block gone {gone.strip()}, item dropped {dropped_item.strip()}")
    shot(tools, shots / "st-punch.png")
    if "passed" not in gone or "passed" not in dropped_item:
        failures.append("one punch did not break the Spring Stone barrier and drop its item")
    # migration: a machine an older version left as a vanilla block becomes the barrier + model setup at the next tick
    rcon.command("clear E2EBot")
    rcon.command("sbgive coal_generator 1 E2EBot")
    use(1, cx + 8.5, y, cz + 8.5, 180, 39)  # a generator at (cx+8, cz+6)
    time.sleep(2)
    as_bot(f"setblock {cx+8} {y} {cz+6} stonecutter")  # what an old world holds
    time.sleep(4)
    migrated = as_bot(f"execute if block {cx+8} {y} {cz+6} minecraft:barrier")
    shown_at = rcon.command(f"execute positioned {cx+8.5} {y+0.5} {cz+6.5} run data get entity "
                            "@e[type=item_display,limit=1,sort=nearest] Pos")
    shown = "passed" if f"{cx+8.5}d" in shown_at and f"{cz+6.5}d" in shown_at else "failed: " + shown_at.strip()
    say(f"start: old stonecutter machine migrated: barrier {migrated.strip()}, model {shown.strip()}")
    say("start: displays near: " + rcon.command(f"execute positioned {cx+8.5} {y+0.5} {cz+6.5} run data get entity @e[type=item_display,limit=1,sort=nearest] Pos").strip()
        + " / machine at: " + (server / "plugins" / "VampyuyuSurvival" / "quarry" / "machines.json").read_text()[-700:].replace("\n", " "))
    if "passed" not in migrated or "passed" not in shown:
        failures.append("a machine left as a vanilla block was not converted to the barrier + model setup")
    # 4c. the Endless Bucket pours water and stays full; the Generator Plan prints the plans
    rcon.command("clear E2EBot")
    rcon.command("sbgive endless_bucket 1 E2EBot")
    rcon.command("sbgive generator_plan 1 E2EBot")
    subprocess.run([xdo, "key", "1"], env=env)
    time.sleep(.5)
    stand(cx + 12.5, y, cz + 12.5, 180, 40)
    subprocess.run([xdo, "click", "3"], env=env)
    time.sleep(1.5)
    water = as_bot(f"execute if block {cx+12} {y} {cz+10} minecraft:water")
    inv = rcon.command("data get entity E2EBot Inventory")
    say("start: after pouring the Endless Bucket: " + inv.strip()[:200])
    shot(tools, shots / "st-bucket.png")
    if "endless_bucket" not in inv.replace("minecraft:water_bucket", "endless_bucket") or "water_bucket" not in inv:
        failures.append("the Endless Bucket was emptied or lost")
    subprocess.run([xdo, "key", "2"], env=env)
    time.sleep(.5)
    subprocess.run([xdo, "click", "3"], env=env)
    time.sleep(1.5)
    shot(tools, shots / "st-plan.png")
    # 5. the quest menus
    command("quests categories")
    shot(tools, shots / "st-7-categories.png")
    # the tooltips of two locked/open categories: wrapped description, labels in the client's language
    for x, name in (("567", "st-7b-category-tooltip.png"), ("603", "st-7c-category-tooltip.png")):
        subprocess.run([xdo, "mousemove", x, "226"], env=env)
        time.sleep(1)
        shot(tools, shots / name)
    subprocess.run([xdo, "key", "Escape"], env=env)
    time.sleep(1)
    command("quests category start")
    shot(tools, shots / "st-8-category-start.png")
    subprocess.run([xdo, "mousemove", "531", "226"], env=env)
    time.sleep(1)
    shot(tools, shots / "st-8b-quest-tooltip.png")
    subprocess.run([xdo, "key", "Escape"], env=env)
    time.sleep(1)
    command("guide generators")
    shot(tools, shots / "st-9-guide-generators.png")
    # a fresh player: the first recipe (Bronze Core) waits for its quest
    command("forge")
    subprocess.run([xdo, "mousemove", "495", "226"], env=env)
    time.sleep(1)
    shot(tools, shots / "st-forge-locked.png")
    subprocess.run([xdo, "key", "Escape"], env=env)
    log = (server / "console.log").read_text(errors="replace")
    for line in log.splitlines():
        if ("Exception" in line or "SEVERE" in line) and " WARN]" not in line and "VampyuyuSurvival" in line:
            failures.append("server error: " + line[:160])
            break


def craft_scenario(ctx):
    """Nine pebbles in a real crafting table make a cobblestone (the Quarry guard must let this recipe through)."""
    import math
    tools, env, shots, failures, server = (ctx[k] for k in ("tools", "env", "shots", "failures", "server"))
    xdo, rcon = tools["xdotool"], Rcon()

    def as_bot(text):
        return rcon.command("execute as E2EBot at E2EBot run " + text)

    rcon.command("op E2EBot")
    rcon.command("gamemode survival E2EBot")
    rcon.command("gamerule spawn_mobs false")
    rcon.command("kill @e[type=slime]")
    subprocess.run([xdo, "key", "slash"], env=env)
    time.sleep(.5)
    subprocess.run([xdo, "type", "--delay", "35", "menu skyblock"], env=env)
    subprocess.run([xdo, "key", "Return"], env=env)
    for _ in range(150):
        if "sb_world" in rcon.command("data get entity E2EBot Dimension"):
            break
        time.sleep(1)
    else:
        failures.append("never reached sb_world")
        return
    time.sleep(8)
    pos = player_state(rcon)["pos"]
    cx, y, cz = math.floor(pos[0]) + 6, math.floor(pos[1]), math.floor(pos[2])
    as_bot(f"fill {cx-3} {y-1} {cz-3} {cx+6} {y-1} {cz+6} grass_block")
    as_bot(f"fill {cx-3} {y} {cz-3} {cx+6} {y+3} {cz+6} air")
    as_bot(f"setblock {cx} {y} {cz} crafting_table")
    rcon.command("clear E2EBot")
    rcon.command("sbgive pebble 9 E2EBot")
    as_bot(f"tp @s {cx+0.5} {y} {cz+2.5} 180 35")
    time.sleep(2)
    subprocess.run([xdo, "click", "3"], env=env)
    time.sleep(2)
    shot(tools, shots / "cr-1-table.png")
    # the 1280x720 screen: the pebbles in the first hotbar slot, the 3x3 grid, the result slot
    grid = [(538 + 36 * c, 244 + 36 * r) for r in range(3) for c in range(3)]
    subprocess.run([xdo, "mousemove", "495", "494"], env=env)
    time.sleep(.3)
    subprocess.run([xdo, "click", "1"], env=env)
    time.sleep(.4)
    for x, yy in grid:
        subprocess.run([xdo, "mousemove", str(x), str(yy)], env=env)
        time.sleep(.3)
        subprocess.run([xdo, "click", "3"], env=env)
        time.sleep(.4)
    time.sleep(1)
    shot(tools, shots / "cr-2-filled.png")
    subprocess.run([xdo, "mousemove", "727", "280"], env=env)
    time.sleep(.3)
    subprocess.run([xdo, "keydown", "shift", "click", "1", "keyup", "shift"], env=env)
    time.sleep(1)
    shot(tools, shots / "cr-3-crafted.png")
    subprocess.run([xdo, "key", "Escape"], env=env)
    time.sleep(1)
    inv = rcon.command("data get entity E2EBot Inventory")
    say("craft: inventory after crafting: " + inv.strip()[:400])
    if "cobblestone" not in inv:
        failures.append("nine pebbles did not craft a cobblestone in a real crafting table")
    # the Fountain: 4 cobblestone and 4 logs, then a Spring Stone: stone bricks around a water bucket
    def fill(stacks_to_cells):
        for hotbar_x, cells in stacks_to_cells:
            subprocess.run([xdo, "mousemove", str(hotbar_x), "494"], env=env)
            time.sleep(.3)
            subprocess.run([xdo, "click", "1"], env=env)
            time.sleep(.4)
            for x, yy in cells:
                subprocess.run([xdo, "mousemove", str(x), str(yy)], env=env)
                time.sleep(.3)
                subprocess.run([xdo, "click", "3"], env=env)
                time.sleep(.4)
            subprocess.run([xdo, "mousemove", str(hotbar_x), "494"], env=env)
            subprocess.run([xdo, "click", "1"], env=env)  # put what is left back
            time.sleep(.4)

    def craft_and_check(name, items, stacks, expect):
        rcon.command("clear E2EBot")
        for item in items:
            if item.startswith("sbgive:"):
                rcon.command("sbgive " + item[7:] + " E2EBot")
            else:
                rcon.command("give E2EBot " + item)
        as_bot(f"tp @s {cx+0.5} {y} {cz+2.5} 180 35")
        time.sleep(1.5)
        subprocess.run([xdo, "click", "3"], env=env)
        time.sleep(2)
        fill(stacks)
        shot(tools, shots / f"cr-{name}-filled.png")
        subprocess.run([xdo, "mousemove", "727", "280"], env=env)
        time.sleep(.3)
        subprocess.run([xdo, "keydown", "shift", "click", "1", "keyup", "shift"], env=env)
        time.sleep(1)
        subprocess.run([xdo, "key", "Escape"], env=env)
        time.sleep(1)
        have = rcon.command("data get entity E2EBot Inventory[{Slot:8b}].id")
        say(f"craft: {name}: slot 8 holds " + have.strip()[:120])
        if expect not in have:
            failures.append(f"{name} was not crafted in a real crafting table")

    craft_and_check("bronzite_bar", ["sbgive:bronzite 9"], [(495, grid)], "copper_ingot")
    craft_and_check("fountain", ["minecraft:cobblestone 4", "minecraft:oak_log 4"],
                    [(495, grid[0:4]), (531, grid[4:8])], "smooth_stone")
    craft_and_check("spring", ["minecraft:stone_bricks 8", "minecraft:water_bucket 1"],
                    [(495, [g for i, g in enumerate(grid) if i != 4]), (531, [grid[4]])], "prismarine_bricks")
    # the recipe book lists the Quarry recipes without anyone unlocking them by hand
    rcon.command("clear E2EBot")
    as_bot(f"tp @s {cx+0.5} {y} {cz+2.5} 180 35")
    time.sleep(1.5)
    subprocess.run([xdo, "click", "3"], env=env)
    time.sleep(2)
    subprocess.run([xdo, "mousemove", "491", "280"], env=env)
    time.sleep(.3)
    subprocess.run([xdo, "click", "1"], env=env)
    time.sleep(1.5)
    shot(tools, shots / "cr-recipe-book.png")
    subprocess.run([xdo, "key", "Escape"], env=env)


def bosses_scenario(ctx):
    """One screenshot of every boss, spawned with the admin command in front of a creative bot."""
    import math
    tools, env, shots, failures, server = (ctx[k] for k in ("tools", "env", "shots", "failures", "server"))
    xdo, rcon = tools["xdotool"], Rcon()

    def command(text, wait=3):
        subprocess.run([xdo, "key", "slash"], env=env)
        time.sleep(.5)
        subprocess.run([xdo, "type", "--delay", "35", text], env=env)
        subprocess.run([xdo, "key", "Return"], env=env)
        time.sleep(wait)

    def as_bot(text):
        return rcon.command("execute as E2EBot at E2EBot run " + text)

    rcon.command("op E2EBot")
    rcon.command("gamemode creative E2EBot")
    rcon.command("gamerule spawn_mobs false")
    command("menu skyblock")
    for _ in range(150):
        if "sb_world" in rcon.command("data get entity E2EBot Dimension"):
            break
        time.sleep(1)
    else:
        failures.append("never reached sb_world")
        return
    time.sleep(8)
    pos = player_state(rcon)["pos"]
    cx, y, cz = math.floor(pos[0]) + 8, math.floor(pos[1]), math.floor(pos[2])
    as_bot(f"time set day")
    as_bot(f"fill {cx-45} {y-1} {cz-45} {cx+45} {y-1} {cz+45} stone")
    for yy in range(y, y + 31):  # one layer at a time: a fill is capped at 32768 blocks, the whole volume failed
        as_bot(f"fill {cx-45} {yy} {cz-45} {cx+45} {yy} {cz+45} air")
    as_bot(f"kill @e[type=!player,distance=..80]")
    rcon.command("gamerule send_command_feedback false")
    # a creative camera standing on a barrier block: a spectator would see the invisible mob as a grey ghost
    rcon.command("gamemode creative E2EBot")
    subprocess.run([xdo, "key", "F1"], env=env)  # no HUD, no chat: the model fills the frame
    time.sleep(1)
    import re as _re
    strip = lambda t: _re.sub(r"\u00a7x(\u00a7[0-9a-f]){6}|\u00a7.", "", t).strip()  # noqa: E731
    # boss -> plugin scale (BossSkinService.LOOKS): the model is 32 units high at most, 16 units = scale blocks
    only = [b for b in os.environ.get("E2E_BOSSES", "").split(",") if b]   # e.g. E2E_BOSSES=magma_warden
    for boss, scale in (("magma_warden", 2.4), ("frost_revenant", 3.6), ("sylvan_matriarch", 2.8),
                        ("abyssal_leviathan", 2.8), ("crypt_lord", 2.4), ("voidwyrm", 4.2),
                        ("hell_gao", 3.4), ("thunder_dragon", 3.4), ("temporal_pharaoh", 3.5),
                        ("mire_abomination", 3.8), ("devourer", 4.4)):
        if only and boss not in only:
            continue
        rcon.command("boss kill all")
        as_bot(f"tp @s {cx+0.5} {y} {cz+8.5} 180 0")
        time.sleep(1.5)
        say(f"bosses: spawn {boss}: " + strip(rcon.command(f"boss spawn {boss} {cx+0.5} {y} {cz+0.5} sb_world"))[:160])
        time.sleep(1.5)
        # freeze it on the spot, facing south (+z), on the ground: the shots must show the model, not a moving mob
        mob = f"@e[type=!player,type=!item_display,type=!text_display,type=!item,distance=..12,sort=nearest,limit=1]"
        as_bot(f"execute as {mob} run data merge entity @s {{NoAI:1b,NoGravity:0b}}")
        as_bot(f"tp {mob} {cx+0.5} {y} {cz+0.5} 0 0")
        time.sleep(2)
        height = 2 * scale                       # blocks
        dist = max(8.0, 2.3 * height)
        eye = y + height / 2 - 1.62
        say(f"bosses: {boss} mob at " + as_bot(f"data get entity {mob} Pos").strip()[-60:]
            + " | display at " + as_bot("data get entity @e[type=minecraft:item_display,sort=nearest,limit=1,distance=..40] Pos").strip()[-60:])
        for view, ddx, ddz, yaw in (("front", 0, 1, 180), ("three-quarter", 0.72, 0.72, 135), ("side", 1, 0, 90), ("back", 0, -1, 0)):
            bx, bz = math.floor(cx + 0.5 + ddx * dist), math.floor(cz + 0.5 + ddz * dist)
            as_bot(f"setblock {bx} {math.floor(eye) - 1} {bz} barrier")
            as_bot(f"tp @s {bx + 0.5} {math.floor(eye)} {bz + 0.5} {yaw} 0")
            time.sleep(2.5)
            shot(tools, shots / f"boss-{boss}-{view}.png")
        video = os.environ.get("E2E_BOSS_VIDEO", "")
        rig_file = ROOT / "survival-plugin/src/main/resources/boss/rigs" / f"{boss}.json"
        if video and (video == "fps" or rig_file.exists()):
            # one short clip per animation of the boss's rig (/boss anim), from the front three-quarter view, then one
            # with its AI back on (the clips the fight picks by itself). E2E_BOSS_VIDEO=fps: 5 s of the frozen boss.
            out_dir = ROOT / ".e2e" / "boss-videos"
            out_dir.mkdir(parents=True, exist_ok=True)
            bx, bz = math.floor(cx + 0.5 + 0.6 * dist), math.floor(cz + 0.5 + 0.8 * dist)
            as_bot(f"setblock {bx} {math.floor(eye) - 1} {bz} barrier")
            as_bot(f"tp @s {bx + 0.5} {math.floor(eye)} {bz + 0.5} {math.degrees(math.atan2(0.6, -0.8)):.1f} 0")  # facing the boss
            time.sleep(1.5)
            if video == "fps":
                record(tools, out_dir / f"{boss}-probe.mp4", 5)
            else:
                clips = json.loads(rig_file.read_text())["animations"]
                order = sorted(clips)
                slow = int(os.environ.get("E2E_VIDEO_SLOW", "4"))   # the game runs 4x slower while recording

                def fresh_boss():
                    # a new frozen boss for every clip: a session with no targetable player despawns after its idle
                    # time (60 s for the Colossus), and the slowed recordings add up past it
                    rcon.command("tick rate 20")
                    rcon.command("boss kill all")
                    time.sleep(1)
                    rcon.command(f"boss spawn {boss} {cx+0.5} {y} {cz+0.5} sb_world")
                    time.sleep(1.5)
                    as_bot(f"execute as {mob} run data merge entity @s {{NoAI:1b}}")
                    as_bot(f"tp {mob} {cx+0.5} {y} {cz+0.5} 0 0")
                    time.sleep(1.5)
                    rcon.command(f"tick rate {20 / slow:g}")
                    time.sleep(1)

                for clip in order:
                    fresh_boss()
                    rcon.command(f"boss anim {clip}")
                    record(tools, out_dir / f"{boss}-{clip}.mp4", min(8, clips[clip]["ticks"] / 20 + (1.5 if clip == "death" else 1.2)), slow=slow)
                rcon.command("tick rate 20")
                # the fight as it plays by itself: AI on, a player it can target (adventure, unhurt) in front of it
                rcon.command("boss kill all")
                time.sleep(1)
                rcon.command(f"boss spawn {boss} {cx+0.5} {y} {cz+0.5} sb_world")
                time.sleep(1.5)
                rcon.command("effect give E2EBot minecraft:resistance infinite 255 true")
                rcon.command("effect give E2EBot minecraft:fire_resistance infinite 0 true")
                rcon.command("effect give E2EBot minecraft:regeneration infinite 255 true")
                rcon.command("gamemode adventure E2EBot")
                rcon.command(f"tick rate {20 / slow:g}")
                record(tools, out_dir / f"{boss}-live.mp4", 8, slow=slow)
                rcon.command("tick rate 20")
                rcon.command("gamemode creative E2EBot")
                rcon.command("effect clear E2EBot")
            say(f"bosses: videos of {boss} in {out_dir}")
    subprocess.run([xdo, "key", "F1"], env=env)
    rcon.command("gamerule send_command_feedback true")
    rcon.command("gamemode creative E2EBot")
    # power scaling: one fully charged Bronze Cleaver hit (tier 1, damage 40) on a still dummy with a big health pool
    # (the ore swords used to deal vanilla damage, about 6). A dummy, because a boss model moves and is hard to aim at.
    import re as _re
    rcon.command("boss kill all")
    rcon.command("clear E2EBot")
    rcon.command("sbgive bronze_cleaver 1 E2EBot")
    rcon.command("gamemode survival E2EBot")
    as_bot(f"tp @s {cx+0.5} {y} {cz+2.5} 180 10")
    as_bot(f"summon zombie {cx+0.5} {y} {cz+0.5} {{NoAI:1b,Silent:1b,Tags:[\"e2e_dummy\"],"
           "attributes:[{id:\"minecraft:max_health\",base:1000}],Health:1000f}")
    time.sleep(2)

    def dummy_health():
        out = rcon.command("data get entity @e[tag=e2e_dummy,limit=1] Health")
        found = _re.findall(r"([0-9.]+)f", out)
        return float(found[0]) if found else None

    before = dummy_health()
    subprocess.run([xdo, "click", "1"], env=env)
    time.sleep(1)
    after = dummy_health()
    shot(tools, shots / "dummy-hit-bronze-cleaver.png")
    dealt = None if before is None or after is None else before - after
    say(f"bosses: Bronze Cleaver hit on the dummy: health {before} -> {after}, dealt {dealt}")
    if dealt is None or dealt < 30:
        failures.append(f"a Bronze Cleaver hit dealt {dealt} (expected about 40, vanilla would be about 6)")
    rcon.command("kill @e[tag=e2e_dummy]")
    rcon.command("gamemode creative E2EBot")
    rcon.command("boss kill all")


def machines_scenario(ctx):
    """The newer machines with a real client: the visible break of a working Miner (seen through a glass wall), a Miner I
    with an Ember Module, a Miner II powered only by a Spring Wheel (Spring Stone cell, Bronzite Blocks: activation then
    overload) and the three stations (Alembic, Crucible, Ranch) placed and opened."""
    import math, re as _re
    tools, env, shots, failures, server = (ctx[k] for k in ("tools", "env", "shots", "failures", "server"))
    xdo, rcon = tools["xdotool"], Rcon()

    def typed(text, wait=1.5):
        subprocess.run([xdo, "key", "t"], env=env)
        time.sleep(0.8)
        subprocess.run([xdo, "type", "--delay", "20", "/" + text], env=env)
        time.sleep(0.3)
        subprocess.run([xdo, "key", "Return"], env=env)
        time.sleep(wait)

    def as_bot(text):
        return rcon.command("execute as E2EBot at E2EBot run " + text)

    def stand(x, y, z, yaw, pitch):
        as_bot(f"tp @s {x} {y} {z} {yaw} {pitch}")
        time.sleep(1.2)

    def holding(item, amount=1):
        rcon.command("clear E2EBot")
        rcon.command(f"sbgive {item} {amount} E2EBot" if not item.startswith("minecraft:") else f"give E2EBot {item} {amount}")
        subprocess.run([xdo, "key", "1"], env=env)
        time.sleep(0.6)

    def click(x, y, z, yaw, pitch, times=1):
        stand(x, y, z, yaw, pitch)
        for _ in range(times):
            subprocess.run([xdo, "click", "3"], env=env)
            time.sleep(0.8)

    def place(item, bx, y, bz):
        """Places on the floor block (bx, y-1, bz): standing 2.5 blocks south, looking down at 39 degrees."""
        holding(item)
        click(bx + 0.5, y, bz + 2.5, 180, 39)

    def on_top(bx, y, bz):
        """Right-clicks the top face of the block at (bx, y, bz), from 2 blocks south over a 1-high block in between."""
        click(bx + 0.5, y, bz + 2.5, 180, 17)

    def south_face(bx, y, bz):
        """Right-clicks the south face of the machine at (bx, y, bz) from 2 blocks away."""
        click(bx + 0.5, y, bz + 2.5, 180, 25)

    def block_is(x, y, z, name):
        return "passed" in rcon.command(f"execute in minecraft:sb_world if block {x} {y} {z} minecraft:{name}").lower()

    def left_in_inventory():
        """Items the bot still holds: what a click did not take (machines.json is only written now and then)."""
        found = _re.findall(r"count: ([0-9]+)", rcon.command("execute if items entity E2EBot container.* *"), _re.I)
        return int(found[0]) if found else 0

    def barrel(x, y, z):
        return rcon.command(f"execute in minecraft:sb_world run data get block {x} {y} {z} Items")

    def cobble_generator(ox, y, cz, glass=False):
        """The quarry scenario's generator: the spot (ox+1, y, cz) between a walled lava source above and water east."""
        as_bot(f"fill {ox-1} {y} {cz-1} {ox+3} {y} {cz-1} stone")
        if glass:  # the visible break is watched from the north, through the spot's wall and the lava's wall above it
            as_bot(f"setblock {ox+1} {y} {cz-1} glass")
        as_bot(f"setblock {ox-1} {y} {cz} stone")
        as_bot(f"setblock {ox} {y} {cz} stone")
        as_bot(f"setblock {ox+3} {y} {cz} stone")
        for dx, dz in ((0, 0), (2, 0), (1, -1), (1, 1)):
            as_bot(f"setblock {ox+dx} {y+1} {cz+dz} {'glass' if glass and (dx, dz) == (1, -1) else 'stone'}")
        # the Miner goes at (ox+1, y, cz+1), under the lava's south wall: its barrel stands east of it (it also keeps the
        # water from spreading south) and its south face stays free for the clicks
        as_bot(f"setblock {ox+2} {y} {cz+1} barrel[facing=up]")

    def fluids(ox, y, cz):
        as_bot(f"setblock {ox+1} {y+1} {cz} lava")
        as_bot(f"setblock {ox+2} {y} {cz} water")

    rcon.command("op E2EBot")
    rcon.command("gamerule spawn_mobs false")
    typed("menu skyblock")
    for _ in range(150):
        if "sb_world" in rcon.command("data get entity E2EBot Dimension"):
            break
        time.sleep(1)
    else:
        failures.append("never reached sb_world")
        return
    time.sleep(8)
    rcon.command("gamemode survival E2EBot")
    rcon.command("effect give E2EBot minecraft:resistance 900 4 true")
    rcon.command("execute in minecraft:sb_world run time set day")
    pos = player_state(rcon)["pos"]
    cx, y, cz = math.floor(pos[0]) + 6, math.floor(pos[1]), math.floor(pos[2])
    as_bot(f"fill {cx-6} {y-1} {cz-6} {cx+16} {y-1} {cz+14} stone")
    as_bot(f"fill {cx-6} {y} {cz-6} {cx+16} {y+5} {cz+14} air")

    # A. Miner I with an Ember Module and a coal generator, the spot behind a glass wall
    cobble_generator(cx, y, cz, glass=True)
    place("coal_generator", cx, y, cz + 1)
    place("miner_1", cx + 1, y, cz + 1)
    holding("minecraft:coal", 16)
    south_face(cx, y, cz + 1)
    holding("ember_module")
    south_face(cx + 1, y, cz + 1)
    shot(tools, shots / "mc-0-ember-installed.png")
    if not block_is(cx + 1, y, cz + 1, "barrier"):  # a machine is a barrier block under its 3D display
        failures.append(f"no Miner I at {cx+1},{y},{cz+1}")
    elif left_in_inventory():
        failures.append("the Ember Module was not taken by the Miner I")

    # B. Miner II powered only by a Spring Wheel, 8 blocks east
    ox = cx + 8
    cobble_generator(ox, y, cz)
    place("spring_stone", ox - 1, y, cz + 1)
    place("spring_wheel", ox, y, cz + 1)
    place("miner_2", ox + 1, y, cz + 1)
    holding("bronzite_block", 5)
    stand(ox + 0.5, y, cz + 3.5, 180, 25)
    for _ in range(5):  # one activates it, three overload it, the fifth must be refused
        subprocess.run([xdo, "click", "3"], env=env)
        time.sleep(0.8)
    shot(tools, shots / "mc-1-spring-wheel-blocks.png")
    if not block_is(ox, y, cz + 1, "barrier"):
        failures.append(f"no Spring Wheel at {ox},{y},{cz+1}")
    elif left_in_inventory() != 1:
        failures.append(f"the Spring Wheel should take 4 Bronzite Blocks (1 + 3 overload) and refuse the 5th: {left_in_inventory()} left")
    rcon.command("clear E2EBot")
    stand(ox + 0.5, y + 1, cz + 5.5, 180, 25)
    shot(tools, shots / "mc-1-spring-wheel.png")

    # C. The stations, placed in a row and opened
    for i, (station, block) in enumerate((("alembic", "brewing_stand"), ("crucible", "cauldron"), ("ranch", "composter"))):
        place(station, cx - 4 + 3 * i, y, cz + 9)
        if not block_is(cx - 4 + 3 * i, y, cz + 9, block):
            failures.append(f"the {station} was not placed")
    rcon.command("clear E2EBot")
    stand(cx - 1.5, y + 1, cz + 13.5, 180, 20)
    shot(tools, shots / "mc-2-stations.png")
    for i, station in enumerate(("alembic", "crucible", "ranch")):
        click(cx - 4 + 3 * i + 0.5, y, cz + 11.5, 180, 30)
        time.sleep(1.5)
        shot(tools, shots / f"mc-3-{station}-menu.png")
        subprocess.run([xdo, "key", "Escape"], env=env)
        time.sleep(1)

    # D. Run both Miners: the visible break through the glass, then what landed in the barrels
    fluids(cx, y, cz)
    fluids(ox, y, cz)
    for i in range(6):
        stand(cx + 1.5, y, cz - 1.7, 0, 27)
        time.sleep(0.5 + 0.3 * i)
        shot(tools, shots / f"mc-4-crack-{i}.png")
    time.sleep(40)
    stand(cx + 4.5, y + 2, cz + 6.5, 180, 30)
    shot(tools, shots / "mc-5-both-miners.png")
    rcon.command("clear E2EBot")
    for name, mx in (("miner1", cx + 1), ("miner2", ox + 1)):  # an empty-hand click shows the Miner's state in chat
        south_face(mx, y, cz + 1)
        shot(tools, shots / f"mc-6-{name}-status.png")
    first, second = barrel(cx + 2, y, cz + 1), barrel(ox + 2, y, cz + 1)
    say("machines: Ember barrel " + first.strip()[-400:])
    say("machines: Spring Wheel barrel " + second.strip()[-400:])
    if "id:" not in first:  # (the Ember Module smelts cobble, sand and netherrack: the cobble draw's ores stay as they are)
        failures.append("the Ember Miner I put nothing in its barrel: " + first.strip()[-120:])
    if "id:" not in second:
        failures.append("the Miner II on the Spring Wheel alone put nothing in its barrel: " + second.strip()[-120:])
    log = (server / "console.log").read_text(errors="replace")
    for line in log.splitlines():
        if ("Exception" in line or "SEVERE" in line) and " WARN]" not in line and "VampyuyuSurvival" in line:
            failures.append("server error: " + line[:160])
            break
    rcon.command("gamemode creative E2EBot")


def endgame_scenario(ctx):
    """The late game with a real client: Realm runes engraved by clicking and then triggered by hits (Blight, Tempest,
    Soulbind, Undertow, Rewind), the Raid of the Devourer (refusal, death inside it, the whole fight to the kill and its
    rewards), a Celestial Breath Fracture entered by a normal player, and floor 7 of the Catacombs. Bot HP is protected
    (resistance, regeneration) only where a kill time is measured; the damage a Starcore bot takes unprotected is measured
    separately. The Raid is opened by clearing its requirement rows in the database, which stands in for eight Bastion clears
    (the refusal itself is checked first, as a normal player).
    E2E_EG_STEPS=floor7 runs only the floor 7 step (about 5 min instead of 12): the Starcore set is given, no runes."""
    import re as _re, sqlite3
    tools, env, shots, failures, server = (ctx[k] for k in ("tools", "env", "shots", "failures", "server"))
    xdo, rcon = tools["xdotool"], Rcon()
    clog = WORK / "prism" / "instances" / INSTANCE / "minecraft" / "logs" / "latest.log"
    strip = lambda text: _re.sub("§x(§[0-9a-f]){6}|§.", "", text).strip()

    def typed(cmd, wait=1.0):
        subprocess.run([xdo, "key", "t"], env=env)
        time.sleep(0.8)
        subprocess.run([xdo, "type", "--delay", "20", "/" + cmd], env=env)
        time.sleep(0.3)
        subprocess.run([xdo, "key", "Return"], env=env)
        time.sleep(wait)

    def as_bot(cmd):
        return rcon.command(f"execute as E2EBot at @s run {cmd}")

    def dimension():
        return rcon.command("data get entity E2EBot Dimension")

    def wait_world(name, seconds=60):
        deadline = time.time() + seconds
        while time.time() < deadline:
            if f'"minecraft:{name}' in dimension():
                return True
            time.sleep(1)
        return False

    def chat():
        """The chat lines the client received (client log), without the command noise."""
        if not clog.exists():
            return []
        return [l.split("[CHAT] ", 1)[1] for l in clog.read_text(errors="replace").splitlines()
                if "[CHAT] " in l and "Teleported" not in l]

    def chat_has(text, since=0):
        return any(text in l for l in chat()[since:])

    def hp(target="E2EBot"):
        found = _re.findall(r"([0-9.]+)f", rcon.command(f"data get entity {target} Health"))
        return float(found[0]) if found else None

    def mouse(x, y):
        subprocess.run([xdo, "mousemove", str(x), str(y)], env=env)
        time.sleep(0.4)

    def click():
        subprocess.run([xdo, "click", "1"], env=env)

    def respawn_click():
        """The death screen: the Respawn button of the 1280x720 screen."""
        mouse(638, 403)
        click()
        time.sleep(2.5)
        mouse(640, 300)

    def stats(label, found, want="", note=""):
        say(f"endgame: {label}: {found}" + (f" (want {want})" if want else "") + (" " + note if note else ""))

    def floor7():
        """Floor 7 (seed 11), the first ambush room. Two runs: a passive bot (no potion effects, standing still: the time
        until it falls and becomes a ghost, 96 max health in full Starcore) and a fighting bot (it faces the nearest enemy
        and clicks, healed now and then: the time to 'Room cleared!'), then the boss room. E2E_EG_PASSIVE_SECONDS (75) is
        how long the passive bot is watched, E2E_EG_MODES=passive|fight picks one of the two runs, E2E_EG_BOT
        =strike adds /damage hits (the dungeon does not count them as weapon hits: nothing dies, do not use)."""
        watch = int(os.environ.get("E2E_EG_PASSIVE_SECONDS", "75"))
        modes = os.environ.get("E2E_EG_MODES", "passive,fight").split(",")
        strike = os.environ.get("E2E_EG_BOT", "click") == "strike"  # strike: also hits by /damage (the dungeon ignores it: no kills); click: real clicks only

        def start_run():
            rcon.command("dungeon admin abort all")
            time.sleep(3)
            rcon.command("op E2EBot")
            typed("menu skyblock") if "sb_" not in dimension() else None
            time.sleep(2)
            typed("dungeon admin start 7 11", 1.0)
            if not wait_world("sb_dungeon", 120):
                failures.append("floor 7 did not start: " + dimension()[:80])
                return False
            time.sleep(15)
            rcon.command("effect clear E2EBot")
            rcon.command("effect give E2EBot minecraft:instant_health 1 10 true")
            time.sleep(1)
            return True

        def gamemode():
            found = _re.findall(r"data: (\d+)", rcon.command("data get entity E2EBot playerGameType"))
            return found[0] if found else None

        # exc is defined once here: real enemies only (not players, markers, displays), not the Watcher's roaming guards
        exc = "type=!player,type=!item_display,type=!text_display,type=!armor_stand,type=!interaction,type=!block_display,type=!marker,type=!item,name=!\"Warden Guard\""

        def alive():
            found = _re.findall(r"count: (\d+)", rcon.command(f"execute as E2EBot at @s if entity @e[distance=..40,{exc}]"), _re.I)
            return int(found[0]) if found else 0

        def alive_of(kind):
            found = _re.findall(r"count: (\d+)", rcon.command(f"execute as E2EBot at @s if entity @e[distance=..40,type={kind}]"), _re.I)
            return int(found[0]) if found else 0

        if "passive" in modes:
            if not start_run():
                return
            stats("floor 7 passive bot max health / health",
                  f"{_re.findall(r'is ([0-9.]+)', rcon.command('attribute E2EBot minecraft:max_health get'))} / {hp()}", "96")
            typed("dungeon admin goto MOB AMBUSH", 0.5)
            began = time.time()
            trace, ghost_at, last_h, drops = [], None, None, []
            while time.time() - began < watch:
                t = time.time() - began
                trace.append((int(t), None if hp() is None else round(hp()), alive()))
                if gamemode() == "3":
                    ghost_at = t
                    break
                if int(t) in (3, 8):  # the health pool of a real monster: BukkitValues carries dungeon_hp_max / dungeon_hp / dungeon_hit
                    for kind in ("zombie", "wither_skeleton", "vindicator"):
                        stats(f"floor 7 monster {kind} data at {int(t)} s",
                              rcon.command(f"data get entity @e[type={kind},name=!\"Warden Guard\",limit=1,sort=nearest] BukkitValues")[:400],
                              "", rcon.command(f"attribute @e[type={kind},limit=1,sort=nearest] minecraft:max_health get")[:80])
                if int(t) in (6, 12):
                    kinds = {k: alive_of(k) for k in ("zombie", "skeleton", "wither_skeleton", "witch", "vindicator", "iron_golem", "enderman", "phantom", "vex")}
                    eff = _re.findall(r'id: "minecraft:([a-z_]+)"', rcon.command("data get entity E2EBot active_effects"))
                    stats(f"floor 7 passive bot at {int(t)} s: enemies by type, effects on the bot", {k: v for k, v in kinds.items() if v}, "", str(eff))
                if int(t) in (1, 2, 6, 15, 30) and not (shots / f"eg-6-floor7-passive-{int(t)}.png").exists():
                    shot(tools, shots / f"eg-6-floor7-passive-{int(t)}.png")
                for _ in range(5):  # 0.2 s samples: every drop is one or more hits (health before / after)
                    h2 = hp()
                    if h2 is not None and last_h is not None and h2 < last_h - 0.05:
                        drops.append(round(last_h - h2, 2))
                    last_h = h2 if h2 is not None else last_h
                    time.sleep(0.2)
            stats("floor 7 passive bot damage drops per 0.2 s sample (a hit each, 96 max health)", drops[:60])
            survived = ghost_at if ghost_at is not None else float(watch)
            stats("floor 7 passive bot in the first ambush room (unprotected, standing still)",
                  f"ghost after {ghost_at:.0f} s" if ghost_at is not None else f"alive after {watch} s", "about 30 s")
            stats("floor 7 passive bot health trace (s, hp, enemies alive)", [x for x in trace if x[0] % 3 == 0])
            stats("floor 7 passive bot most enemies alive at once", max([x[2] for x in trace] or [0]))
            shot(tools, shots / "eg-6-floor7-passive-end.png")
            if survived < 30:
                failures.append(f"floor 7 ambush: the passive bot was a ghost after {survived:.0f} s (want at least 30 s; measured 44 s on 2026-11-23)")

        if "fight" not in modes:
            return
        if not start_run():
            return
        typed("dungeon admin goto MOB AMBUSH", 0.5)
        began = time.time()
        cleared, ghosts, last_heal, hp_low, series, pool_trace, now_shots = False, 0, 0, 999.0, [], [], 0
        while time.time() - began < 180:
            # walk up to an enemy that is out of reach, face the nearest one, swing (a full charge: 0.7 s)
            as_bot(f"facing entity @e[distance=3.5..18,{exc},limit=1,sort=nearest] eyes run tp @s ^ ^ ^2.5")
            as_bot(f"facing entity @e[distance=..18,{exc},limit=1,sort=nearest] eyes run tp @s ~ ~ ~ ~ ~")
            click()
            if now_shots < 3:  # the AMBUSH title lives 2 s: a few shots back to back right after the goto
                shot(tools, shots / f"eg-6-floor7-fight-title-{now_shots}.png")
                now_shots += 1
            if len(pool_trace) < 60:  # a Zombie Knight's virtual pool (dungeon_hp / dungeon_hp_max) and entity health: hits to kill
                knight = ('@e[distance=..12,type=zombie,limit=1,sort=nearest,'
                          'nbt={BukkitValues:{"vampyuyusurvival:dungeon_mob":"zombie_knight"}}]')
                def knight_data(path):
                    return as_bot(f'data get entity {knight} {path}')
                last = lambda text: (_re.findall(r"(-?[0-9.]+)[df]?$", text.strip()) or ["-"])[0]
                pool_trace.append((round(time.time() - began, 1), last(knight_data('BukkitValues."vampyuyusurvival:dungeon_hp"')),
                                   last(knight_data('BukkitValues."vampyuyusurvival:dungeon_hp_max"')), last(knight_data("Health")),
                                   last(knight_data("UUID[0]"))))
            if strike:  # one full-charge Starcore hit on the nearest enemy in reach, like a player who lands every swing
                as_bot(f"damage @e[distance=..6,{exc},limit=1,sort=nearest] 100000 minecraft:player_attack by E2EBot")
            time.sleep(0.7)
            now = int(time.time() - began)
            series.append((now, alive()))
            h = hp()
            if h is not None:
                hp_low = min(hp_low, h)
                if h < 30 and now - last_heal >= 3:  # the bot is a poor dodger: a flask now and then (counted)
                    rcon.command("effect give E2EBot minecraft:instant_health 1 10 true")
                    last_heal = now
                    ghosts += 1
            if now in (1, 3, 10) and not (shots / f"eg-6-floor7-fight-{now}.png").exists():
                shot(tools, shots / f"eg-6-floor7-fight-{now}.png")
            if chat_has("Room cleared!"):
                cleared = True
                break
        stats("floor 7 ambush room (level 50), fighting bot",
              f"cleared={cleared} in {int(time.time() - began)} s, lowest health {hp_low:.0f}, heals {ghosts}, most alive at once {max([x[1] for x in series] or [0])}", "")
        stats("floor 7 fighting bot nearest enemy pool trace (s, dungeon_hp, dungeon_hp_max, entity health)", pool_trace)
        stats("floor 7 fighting bot enemies alive (s, n)", [x for x in series if x[0] % 10 == 0 or x[0] < 4][:40])
        shot(tools, shots / "eg-6-floor7-room.png")
        if not cleared:
            failures.append("floor 7 ambush room was not cleared by the fighting bot in 180 s")
        typed("dungeon admin goto BOSS", 0.5)
        time.sleep(8)
        shot(tools, shots / "eg-6-floor7-boss.png")
        if "Hollow Star" not in rcon.command("execute as E2EBot at @s run data get entity @e[type=wither_skeleton,limit=1,sort=nearest] CustomName"):
            failures.append("the floor 7 boss (The Hollow Star) did not appear")
        typed("dungeon leave", 1.0)

        # nothing may stay open
        rcon.command("breath stop")
        rcon.command("bastion admin abort all")
        rcon.command("dungeon admin abort all")
        rcon.command("gamemode creative E2EBot")

    rcon.command("op E2EBot")
    rcon.command("gamemode survival E2EBot")
    rcon.command("gamerule send_command_feedback false")
    rcon.command("gamerule log_admin_commands false")
    rcon.command("gamerule spawn_mobs false")
    rcon.command("difficulty hard")
    typed("menu skyblock")
    if not wait_world("sb_world", 150):
        failures.append("never reached sb_world")
        return
    time.sleep(6)
    rcon.command("clear E2EBot")
    if os.environ.get("E2E_EG_STEPS", "") == "floor7":
        # only the floor 7 step: the full Starcore set (no runes), the sword in the first hotbar slot
        for item in ("starcore_sword", "starcore_chestplate", "starcore_leggings", "starcore_helmet", "starcore_boots"):
            rcon.command(f"sbgive {item} 1 E2EBot")
        time.sleep(1)
        for slot_name, src in (("chest", "hotbar.1"), ("legs", "hotbar.2"), ("head", "hotbar.3"), ("feet", "hotbar.4")):
            rcon.command(f"item replace entity E2EBot armor.{slot_name} from entity E2EBot {src}")
        time.sleep(1)
        for src in ("hotbar.1", "hotbar.2", "hotbar.3", "hotbar.4"):
            rcon.command(f"item replace entity E2EBot {src} with minecraft:air")
        subprocess.run([xdo, "key", "1"], env=env)
        floor7()
        return

    # 1. Realm runes: engraved with real clicks in the inventory, then triggered by real hits and hits taken.
    for item in ("starcore_sword", "rune_blight", "rune_tempest", "rune_soulbind", "starcore_chestplate", "starcore_leggings",
                 "rune_rewind", "rune_undertow", "starcore_helmet", "starcore_boots"):
        rcon.command(f"sbgive {item} 1 E2EBot")
    time.sleep(1)
    subprocess.run([xdo, "key", "e"], env=env)
    time.sleep(1.5)
    slot = lambda i: 495 + 36 * i  # hotbar slot centres of the player inventory screen (1280x720, GUI scale 2)

    def engrave(src, dst):
        mouse(slot(src), 494)
        click()
        time.sleep(0.5)
        mouse(slot(dst), 494)
        click()
        time.sleep(0.8)

    engrave(1, 0)
    engrave(2, 0)
    engrave(3, 0)
    engrave(6, 4)
    engrave(7, 5)
    mouse(slot(0), 494)
    time.sleep(1.5)
    shot(tools, shots / "eg-1-sword-runes.png")
    mouse(slot(5), 494)
    time.sleep(1.5)
    shot(tools, shots / "eg-1-legs-undertow.png")
    subprocess.run([xdo, "key", "Escape"], env=env)
    time.sleep(1)

    def runes_of(path):
        return strip(rcon.command(f'data get entity E2EBot {path}.components."minecraft:custom_data".PublicBukkitValues."vampyuyu:runes"'))

    for path, want in (("Inventory[{Slot:0b}]", ("blight", "tempest", "soulbind")), ("Inventory[{Slot:4b}]", ("rewind",)),
                       ("Inventory[{Slot:5b}]", ("undertow",))):
        got = runes_of(path)
        stats("engraved " + path, got, ", ".join(want))
        for rune in want:
            if rune not in got:
                failures.append(f"{rune} was not engraved by clicking it onto the item ({got[:80]})")
    for slot_name, src in (("head", "hotbar.8"), ("chest", "hotbar.4"), ("legs", "hotbar.5"), ("feet", "inventory.0")):
        rcon.command(f"item replace entity E2EBot armor.{slot_name} from entity E2EBot {src}")
    time.sleep(2)
    for src in ("hotbar.4", "hotbar.5", "hotbar.8", "inventory.0"):
        rcon.command(f"item replace entity E2EBot {src} with minecraft:air")
    maxhp = _re.findall(r"is ([0-9.]+)", rcon.command("attribute E2EBot minecraft:max_health get"))
    stats("full Starcore set max health", maxhp, "96")
    subprocess.run([xdo, "key", "1"], env=env)

    # Rewind: a hit that leaves the player under 30 % heals 30 %
    rcon.command("effect clear E2EBot")
    seq = [hp()]
    for _ in range(30):
        rcon.command("effect give E2EBot minecraft:instant_damage 1 2 true")
        time.sleep(0.35)
        seq.append(hp())
        if seq[-1] is not None and len(seq) > 3 and seq[-1] > seq[-2]:
            break
    stats("Rewind health trace", [round(v, 1) for v in seq if v is not None][-6:])
    if not any(b > a + 10 for a, b in zip(seq, seq[1:]) if a is not None and b is not None):
        failures.append("Rewind did not heal after a hit under 30 %: " + str(seq))
    shot(tools, shots / "eg-3-rewind.png")
    rcon.command("effect clear E2EBot")
    rcon.command("effect give E2EBot minecraft:instant_health 1 10 true")
    time.sleep(1)

    # Surge (every 5th full hit): Blight's Wither, Tempest's chain, Soulbind's heal. Husks: they do not burn in daylight.
    nb = ('NoAI:1b,Silent:1b,PersistenceRequired:1b,attributes:[{id:"minecraft:max_health",base:1000}],Health:1000f')
    x, y, z = [float(v) for v in _re.findall(r"(-?[0-9.]+)d", rcon.command("data get entity E2EBot Pos"))[:3]]

    def target_hp(tag):
        found = _re.findall(r"([0-9.]+)f", rcon.command(f"data get entity @e[tag={tag},limit=1,sort=nearest] Health"))
        return float(found[0]) if found else None

    rcon.command("kill @e[tag=e2e_target]")
    as_bot(f"tp @s {x} {y} {z} 180 10")
    for tag, dx, dz in (("main", 0, -2), ("chain", 1.5, -3), ("chain", -1.5, -3)):
        as_bot(f'summon husk {x + dx} {y} {z + dz} {{{nb},Tags:["e2e_target","{tag}"]}}')
    # Resistance III: the Surge hit (about twice a plain one) must not kill a 1000 health target in one blow
    rcon.command("effect give @e[tag=e2e_target] minecraft:resistance 999 3 true")
    seen = {}
    for i in range(1, 6):
        as_bot(f"tp @e[tag=main] {x} {y} {z - 2}")
        rcon.command("execute as @e[tag=e2e_target] run data modify entity @s Health set value 1000f")
        time.sleep(1.3)  # a full charge
        before_chain = [target_hp("chain")]
        click()
        time.sleep(0.4)
        dealt = 1000 - (target_hp("main") or 1000)
        # asked for the one effect: the whole list is cut short by the reply of a long data get
        withered = "has the following entity data" in rcon.command('data get entity @e[tag=main,limit=1] active_effects[{id:"minecraft:wither"}].id')
        seen[i] = (dealt, "wither" if withered else "")
        if i == 5:
            shot(tools, shots / "eg-2-surge.png")
            chain_hp = rcon.command("execute as @e[tag=chain] run data get entity @s Health")
            chained = [float(v) for v in _re.findall(r"([0-9.]+)f", chain_hp)]
            seen["chain"] = chained
    stats("hit damage 1..5", {k: round(v[0]) for k, v in seen.items() if k != "chain"})
    if "wither" not in seen[5][1]:
        failures.append("Blight: no Wither on the target after the 5th hit: " + seen[5][1][:100])
    if "wither" in seen[1][1]:
        failures.append("Blight: Wither after a plain hit")
    if seen[5][0] < 1.5 * seen[1][0]:
        failures.append(f"the 5th hit is not a Surge: {seen[5][0]} vs {seen[1][0]}")
    chained = seen.get("chain", [])
    stats("Tempest chained husks (health)", chained)
    if len([c for c in chained if c < 1000]) < 2:
        failures.append(f"Tempest did not chain to both other husks: {chained}")
    rcon.command("kill @e[tag=e2e_target]")
    as_bot(f"tp @s {x} {y} {z} 180 10")
    for _ in range(6):  # about 6 each: well above the Rewind line, room left for the 8 % Soulbind heal
        rcon.command("effect give E2EBot minecraft:instant_damage 1 2 true")
        time.sleep(0.6)
    time.sleep(1)
    low = hp()
    as_bot(f'summon husk {x} {y} {z - 2} {{NoAI:1b,Silent:1b,PersistenceRequired:1b,Tags:["e2e_target"],Health:5f}}')
    time.sleep(1.4)
    click()
    time.sleep(0.8)
    after = hp()
    stats("Soulbind kill heal", f"{low} -> {after}", "+8 % of 96 = +7.7")
    if low is None or after is None or after - low < 6.5:
        failures.append(f"Soulbind did not heal on a kill: {low} -> {after}")

    # Undertow after damage that has no attacker (the bug found with this scenario: the cooldown was spent by it)
    rcon.command("kill @e[tag=e2e_target]")
    rcon.command("effect give E2EBot minecraft:instant_damage 1 0 true")  # no attacker
    time.sleep(0.5)
    as_bot(f'summon husk {x} {y} {z - 1.2} {{PersistenceRequired:1b,Tags:["e2e_target"],attributes:[{{id:"minecraft:attack_damage",base:60}},{{id:"minecraft:max_health",base:500}}],Health:500f}}')
    pushed = False
    for _ in range(40):
        time.sleep(0.2)
        found = _re.findall(r"(-?[0-9.]+)d", rcon.command("data get entity @e[tag=e2e_target,limit=1] Pos"))
        effects = rcon.command("data get entity @e[tag=e2e_target,limit=1] active_effects")
        if "slowness" in effects and found and float(found[2]) < z - 2.5:
            pushed = True
            break
    shot(tools, shots / "eg-3-undertow.png")
    stats("Undertow threw the attacker back after an attackerless hit", pushed)
    if not pushed:
        failures.append("Undertow did not trigger on a husk hit that came after damage without an attacker")
    rcon.command("kill @e[tag=e2e_target]")

    # 2. The Raid of the Devourer. A normal player first.
    if (hp() or 0) <= 0:  # the Rewind check can end with a death screen
        respawn_click()
    rcon.command("sbgive devourer_sigil 1 E2EBot")
    rcon.command("deop E2EBot")
    before = len(chat())
    typed("bastion enter devourer_raid", 1.5)
    shot(tools, shots / "eg-4-raid-refused.png")
    if not chat_has("has not cleared", before):
        failures.append("a player who cleared no Bastion was not refused by the Raid")
    # the stand-in for eight clears: empty requirement rows, reloaded
    db = sqlite3.connect(server / "plugins" / "VampyuyuSurvival" / "skyblock.db", timeout=20)
    db.execute("DELETE FROM setting_item WHERE setting_id='bastion.raid.requires'")
    db.execute("UPDATE setting SET origin='panel' WHERE id='bastion.raid.requires'")
    db.commit()
    db.close()
    rcon.command("bastion admin reload")
    rcon.command("op E2EBot")  # the bot teleports itself around the boss: a Bastion refuses that to a normal player
    rcon.command("gamemode survival E2EBot")
    typed("bastion enter devourer_raid", 2.0)
    if not wait_world("sb_bastion_", 30):
        failures.append("the Raid did not open an instance: " + dimension()[:80])
        return
    fought = False
    for _ in range(40):
        time.sleep(1)
        if "fighting" in strip(rcon.command("bastion admin list")):
            fought = True
            break
    shot(tools, shots / "eg-4-raid-wakes.png")
    if not fought:
        failures.append("the Devourer never woke")
        return
    # Dying inside the Raid must respawn the player in the arena (it has lives), not on the island
    rcon.command("kill E2EBot")
    time.sleep(2)
    shot(tools, shots / "eg-4-raid-death.png")
    respawn_click()
    in_arena = "sb_bastion_" in dimension()
    stats("respawn after a death in the Raid", dimension().strip()[-24:], "sb_bastion_N")
    if not in_arena:
        failures.append("a death inside the Raid respawned the player outside the arena: " + dimension().strip()[-30:])
        rcon.command("bastion admin abort all")
        return
    unprotected = []
    for _ in range(6):  # a bot that stands still in front of the Devourer: the damage it takes, unprotected
        p = _re.findall(r"(-?[0-9.]+)d", rcon.command("execute as E2EBot at @s run data get entity @e[type=warden,limit=1,sort=nearest] Pos"))
        if len(p) >= 3:
            as_bot(f"tp @s {p[0]} {p[1]} {float(p[2]) + 4} facing {p[0]} {float(p[1]) + 1.5} {p[2]}")
        time.sleep(1.5)
        unprotected.append(hp())
    stats("unprotected health beside the Devourer, every 1.5 s", [round(v) for v in unprotected if v is not None], "")
    # the kill, protected, with real sword hits
    rcon.command("effect give E2EBot minecraft:resistance 900 4 true")
    rcon.command("effect give E2EBot minecraft:regeneration 900 4 true")
    subprocess.run([xdo, "key", "1"], env=env)
    began = time.time()
    shot_at = {20: False}
    killed = False
    while time.time() - began < 480:  # the Raid pool is 7 Guardian pools (boss.settings.hp-factor.devourer)
        out = rcon.command("execute as E2EBot at @s run data get entity @e[type=warden,limit=1,sort=nearest] Pos")
        p = [float(v) for v in _re.findall(r"(-?[0-9.]+)d", out)[:3]]
        if len(p) < 3:
            killed = True
            break
        as_bot(f"tp @s {p[0]} {p[1]} {p[2] + 2.6} facing {p[0]} {p[1] + 1.5} {p[2]}")
        click()
        elapsed = int(time.time() - began)
        if elapsed >= 20 and not shot_at[20]:
            shot_at[20] = True
            shot(tools, shots / "eg-4-raid-phase.png")
        time.sleep(0.55)
    took = int(time.time() - began)
    time.sleep(3)
    shot(tools, shots / "eg-4-raid-cleared.png")
    stats("Devourer kill time with real hits (protected bot)", f"{took} s", "about 4 min, under the 9 min enrage")
    if not killed:
        failures.append("the Devourer was not killed in 480 s")
    for line in ("The Devourer defeated!", "First clear of the Raid: +4000 coins", "Raid of the Devourer cleared in"):
        if not chat_has(line):
            failures.append(f"the Raid kill did not show '{line}'")
    spoken = [l for l in chat() if l.startswith("The Devourer \u00bb ")]
    stats("Devourer voiced lines", len(spoken), "at least 3 (wake, phases, death)")
    if len(spoken) < 3:
        failures.append(f"the Devourer only spoke {len(spoken)} lines during the fight")
    if not chat_has("the arena is falling apart"):
        failures.append("the Raid never reached its last phase (the arena falling apart)")
    bad = [l for l in clog.read_text(errors="replace").splitlines() if "ERROR" in l and ("sound" in l.lower() or "Missing" in l)]
    if bad:
        failures.append("client sound errors: " + bad[0][:160])
    rcon.command("bastion admin abort all")
    time.sleep(3)
    rcon.command("effect clear E2EBot")

    # 3. A Celestial Breath Fracture, entered by a normal player in its Realm
    typed("menu skyblock") if "sb_" not in dimension() else None
    rcon.command("op E2EBot")
    rcon.command("breath start grove_fracture")
    time.sleep(2)
    shot(tools, shots / "eg-5-fracture-banner.png")
    rcon.command("gateadmin light E2EBot all")
    rcon.command("gateadmin travel E2EBot grove")
    if not wait_world("sb_grove", 40):
        failures.append("the Gate did not reach the Grove: " + dimension()[:80])
    else:
        time.sleep(5)
        rcon.command("deop E2EBot")
        time.sleep(1)
        typed("breath fracture", 1.5)
        if not wait_world("sb_dungeon", 90):
            failures.append("/breath fracture did not start the run: " + dimension()[:80])
        else:
            time.sleep(14)
            shot(tools, shots / "eg-5-fracture-run.png")
            say("endgame: Fracture run started in " + dimension().strip()[-24:])
            typed("dungeon leave", 2.0)
            time.sleep(5)
            say("endgame: after leaving the Fracture the bot is in " + dimension().strip()[-24:])
            if "sb_grove" not in dimension():
                failures.append("leaving the Fracture did not bring the bot back to the Grove: " + dimension().strip()[-30:])
    rcon.command("breath stop")
    rcon.command("dungeon admin abort all")
    time.sleep(2)

    # 4. Floor 7: the first ambush room (passive, then fighting), then the boss room
    floor7()


# Realm id -> its world (api/Realm, core/SkyblockWorlds); the Gate order of the Quai des Huit.
REALM_WORLDS = [("ashes", "sb_ashes"), ("grove", "sb_grove"), ("heights", "sb_heights"), ("citadel", "sb_citadel"),
                ("marshes", "sb_marsh"), ("archipelago", "sb_storm"), ("depths", "sb_depths"), ("necropolis", "sb_necropolis")]
HUB_Y = 100  # island.hub.y of the seed: the surface of the hub plaza and of the Quai


def realms_scenario(ctx):
    """Phase 2 to final with a real client: the Hub, the Quai des Huit (gates dark, then lit), a walk through the Ashes
    Gate, every Realm reached by its Gate (spawn, behind, above), the Astral Interiority and a Bastion with its Guardian.
    E2E_REALMS=ashes,grove limits the Realm tour; E2E_SKIP=astral,bastion,seal,reveal skips those parts (reveal = a Guardian clear wakes Gates, title, chat, the Quai sequence); E2E_SEAL=all|a,b picks the Realms whose
    seal the bot right-clicks (default ashes,grove); E2E_ARCH=all|a,b picks the
    Realms whose return arch the bot walks into (default ashes,grove,depths)."""
    import math, re as _re
    tools, env, shots, failures, server = (ctx[k] for k in ("tools", "env", "shots", "failures", "server"))
    xdo, rcon = tools["xdotool"], Rcon()
    wanted = {r for r in os.environ.get("E2E_REALMS", "").split(",") if r}
    skip = {r for r in os.environ.get("E2E_SKIP", "").split(",") if r}
    # Realms where the bot also walks into the return arch (E2E_ARCH=all for the eight): one per group of landings
    arch_env = os.environ.get("E2E_ARCH", "ashes,grove,depths")
    arch_walk = {r for r, _ in REALM_WORLDS} if arch_env == "all" else {r for r in arch_env.split(",") if r}
    strip = lambda text: _re.sub("§x(§[0-9a-f]){6}|§.", "", text).strip()

    def typed(cmd, wait=1.0):
        subprocess.run([xdo, "key", "t"], env=env)
        time.sleep(0.8)
        subprocess.run([xdo, "type", "--delay", "20", "/" + cmd], env=env)
        time.sleep(0.3)
        subprocess.run([xdo, "key", "Return"], env=env)
        time.sleep(wait)

    def as_bot(cmd):
        return rcon.command(f"execute as E2EBot at @s run {cmd}")

    def dimension():
        return rcon.command("data get entity E2EBot Dimension")

    def wait_world(name, seconds=60):
        deadline = time.time() + seconds
        while time.time() < deadline:
            if f'"minecraft:{name}' in dimension():
                return True
            time.sleep(1)
        return False

    def tail():
        lines = (server / "console.log").read_text(errors="replace").splitlines()
        return " | ".join(l for l in lines[-200:] if "WARN" in l or "ERROR" in l)[-600:]

    def on_ground(world):
        """Creative players do not fall: look for a block under the feet instead. From the console, not "as" the bot:
        with send_command_feedback off the bot's own test result never reaches RCON."""
        x, y, z = player_state(rcon)["pos"]
        return any("failed" in rcon.command(f"execute in minecraft:{world} if block {math.floor(x)} {math.floor(y) - d} "
                                            f"{math.floor(z)} minecraft:air").lower() for d in (1, 2))

    def perch(world, x, y, z, yaw, pitch):
        """A view from the air: a creative bot that is not flying falls before the shot, so it stands on a barrier block
        (invisible) for the time of the shot. Returns the undo."""
        bx, by, bz = math.floor(x), math.floor(y) - 1, math.floor(z)
        rcon.command(f"execute in minecraft:{world} run setblock {bx} {by} {bz} minecraft:barrier")
        as_bot(f"tp @s {x} {y} {z} {yaw} {pitch}")
        return lambda: rcon.command(f"execute in minecraft:{world} run setblock {bx} {by} {bz} minecraft:air")

    def items():
        """Occupied inventory slots, read from the player data (the reply of `execute if items` is not a stable format)."""
        return len(_re.findall(r"Slot: ?-?[0-9]+b", rcon.command("data get entity E2EBot Inventory")))

    rcon.command("op E2EBot")
    rcon.command("gamemode creative E2EBot")
    rcon.command("gamerule send_command_feedback false")
    typed("menu skyblock")
    if not wait_world("sb_world", 150):
        failures.append("never reached sb_world")
        return
    time.sleep(6)

    # 1. The Hub
    typed("hub")
    if not wait_world("sb_hub"):
        failures.append("/hub did not reach sb_hub: " + dimension()[:80])
        return
    time.sleep(5)
    shot(tools, shots / "rl-0-hub-landing.png")
    undo = perch("sb_hub", 0.5, HUB_Y + 18, 0.5, 180, 25)
    time.sleep(3)
    shot(tools, shots / "rl-0-hub-above.png")
    undo()
    if os.environ.get("E2E_HUB_VIEWS"):  # the Hub from inside, from outside, from under, by day and by night, no HUD
        views = [("spire", 0.5, HUB_Y + 1, 18.5, 180, -22), ("outside", 52.5, HUB_Y + 18, -40.5, 52, 12),
                 ("keel", -40.5, HUB_Y - 26, 30.5, -128, -26), ("north-road", 0.5, HUB_Y + 3, -40.5, 0, -10)]
        subprocess.run([xdo, "key", "F1"], env=env)
        for when in ("day", "night"):
            rcon.command(f"execute in minecraft:sb_hub run time set {'noon' if when == 'day' else 'midnight'}")
            for label, vx, vy, vz, yaw, pitch in views:
                if when == "night" and label in ("keel", "north-road"):
                    continue
                undo = perch("sb_hub", vx, vy, vz, yaw, pitch)
                time.sleep(4)
                shot(tools, shots / f"rl-0h-{label}-{when}.png")
                undo()
        rcon.command("execute in minecraft:sb_hub run time set noon")
        subprocess.run([xdo, "key", "F1"], env=env)

    # 2. The Quai des Huit, dark then lit (the lit look is client-side blocks, resent every second near the Quai)
    typed("quai")
    time.sleep(4)
    # high above the hub end of the bridge (lower, the ruined arch of the bridge fills the view), down on the ring
    undo = perch("sb_hub", 0.5, HUB_Y + 32, 46.5, 0, 51)  # inside the 48 blocks the Quai view is kept up in
    time.sleep(3)
    shot(tools, shots / "rl-1-quai-dark.png")
    rcon.command("gateadmin dark E2EBot all")
    lit = strip(rcon.command("gateadmin light E2EBot all"))
    say("realms: " + lit)
    time.sleep(16)  # the reveal: a 2 s wait, then one Gate per second (island.quai.reveal-*-ticks)
    subprocess.run([xdo, "key", "F1"], env=env)  # hide the HUD: eight "Gate lit" lines would cover the ring
    time.sleep(1)
    shot(tools, shots / "rl-1-quai-lit.png")
    subprocess.run([xdo, "key", "F1"], env=env)
    undo()

    # 2a. E2E_QUAI_VIEWS=1: the Quai from where players see it (arrival, the hub, outside, under the keel, a lit Gate and
    # its halo), by day and by night, without the HUD
    if os.environ.get("E2E_QUAI_VIEWS"):
        views = [("arrival", 0.5, HUB_Y + 1, 53.5, 0, -12), ("from-hub", 0.5, HUB_Y + 16, 16.5, 0, 14),
                 ("outside", -46.5, HUB_Y + 16, 36.5, -52, 14), ("keel", 26.5, HUB_Y - 18, 96.5, 135, -24),
                 ("gate-lit", 3.6, HUB_Y + 1, 65.1, -157.5, -32)]
        subprocess.run([xdo, "key", "F1"], env=env)
        for when in ("day", "night"):
            rcon.command(f"execute in minecraft:sb_hub run time set {'noon' if when == 'day' else 'midnight'}")
            for label, vx, vy, vz, yaw, pitch in views:
                if when == "night" and label in ("from-hub", "keel"):
                    continue
                undo = perch("sb_hub", vx, vy, vz, yaw, pitch)
                time.sleep(4)
                shot(tools, shots / f"rl-1q-{label}-{when}.png")
                undo()
        rcon.command("execute in minecraft:sb_hub run time set noon")
        subprocess.run([xdo, "key", "F1"], env=env)

    # 2b. A Guardian clear that wakes Gates: title and chat where the bot stands, then the Gates light one by one on its next
    # visit to the Quai (E2E_SKIP=reveal skips it). The Mycelial Queen lights five Gates; the bot is made to kill her with
    # damage commands, and when that does not credit the kill the five Gates are lit by hand (said in the log).
    if "reveal" not in skip:
        typed("hub")
        wait_world("sb_hub", 20)
        time.sleep(4)
        queen = ("heights", "citadel", "marshes", "archipelago", "depths")  # the gates of island.*.gate-boss = sylvan_matriarch
        for realm in queen:
            rcon.command(f"gateadmin dark E2EBot {realm}")
        time.sleep(1)
        x, y, z = player_state(rcon)["pos"]
        out = strip(rcon.command(f"boss spawn sylvan_matriarch {x + 6:.1f} {y:.1f} {z:.1f} sb_hub"))
        say("realms: reveal: spawn the Queen: " + out[:120])
        time.sleep(3)
        lit_now = 0
        for hit in range(12):
            reply = strip(rcon.command("execute as E2EBot at @s as @e[type=!minecraft:player,distance=..30] run damage @s "
                                       "100000 minecraft:player_attack by E2EBot"))
            if hit == 0:
                say("realms: reveal: damage reply: " + reply[:160])
            time.sleep(0.5)
            lit_now = strip(rcon.command("gateadmin list E2EBot")).count("  lit")
            if lit_now >= 8:
                break
        if lit_now < 8:
            say(f"realms: reveal: NOT VERIFIED with a real kill ({lit_now} Gates lit): waking the five with one command")
            rcon.command("gateadmin light E2EBot all")  # one command = one tick = one batch, like the Queen's five handlers
        else:
            say("realms: reveal: the Queen's kill woke the five Gates (real kill)")
        time.sleep(0.6)
        shot(tools, shots / "rl-1b-wake-title.png")
        time.sleep(1.5)
        shot(tools, shots / "rl-1b-wake-chat.png")
        rcon.command("boss kill all")
        typed("quai")  # what the chat line's click does
        undo = perch("sb_hub", 0.5, HUB_Y + 32, 46.5, 0, 51)
        t0 = time.time()
        for i, at in enumerate((1.0, 3.0, 4.2, 5.4, 6.6, 8.0, 11.0)):
            time.sleep(max(0, at - (time.time() - t0)))
            shot(tools, shots / f"rl-1b-reveal-{i}.png")
        undo()
        gates_file = next((server / "plugins").glob("*/island/gates.json"), None)
        seen = json.loads(gates_file.read_text()).get("seen", {}) if gates_file else {}
        if not any(set(queen) <= set(v) for v in seen.values()):
            failures.append(f"the reveal did not mark the five Gates seen: {seen}")
        else:
            say("realms: reveal: the five Gates are saved as seen")
        rcon.command("gateadmin light E2EBot all")  # the walk below needs the Ashes Gate
        time.sleep(1)

    # 3. Walk into the lit Ashes Gate (ordinal 0 on the ring of radius 15 around the Quai centre, QuaiPlan.buildGates)
    angle = math.radians(22.5)
    ox, oz = math.sin(angle), -math.cos(angle)
    gx, gz = round(15 * ox), 72 + round(15 * oz)
    yaw = math.degrees(math.atan2(-ox, oz))
    as_bot(f"tp @s {gx + 0.5 - 3 * ox:.2f} {HUB_Y + 1} {gz + 0.5 - 3 * oz:.2f} {yaw:.1f} 0")
    time.sleep(2)
    shot(tools, shots / "rl-2-ashes-gate-close.png")
    subprocess.run([xdo, "keydown", "w"], env=env)
    time.sleep(2.5)
    subprocess.run([xdo, "keyup", "w"], env=env)
    if wait_world("sb_ashes", 15):
        say("realms: walked through the Ashes Gate")
    else:
        failures.append("walking into the lit Ashes Gate did not reach sb_ashes: " + dimension()[:80] + " " + tail())

    # 4. Every Realm through its Gate (the command skips the walk, not the Gate rules). Night vision: several Realms are
    # dark on purpose, and the shots are there to show what was built.
    rcon.command("effect give E2EBot minecraft:night_vision 900 0 true")
    for realm, world in REALM_WORLDS:
        if wanted and realm not in wanted:
            continue
        reply = strip(rcon.command(f"gateadmin travel E2EBot {realm}"))
        if not wait_world(world, 30):
            failures.append(f"the {realm} Gate did not reach {world}: '{reply}', in {dimension().strip()[-30:]} {tail()}")
            shot(tools, shots / f"rl-3-{realm}-failed.png")
            continue
        time.sleep(4)  # the travel blindness, then the chunks
        x, y, z = player_state(rcon)["pos"]
        if not on_ground(world):
            failures.append(f"{realm}: the Gate spawn at {x} {y} {z} has nothing under the feet")
        say(f"realms: {realm} spawn {x} {y} {z}")
        shot(tools, shots / f"rl-3-{realm}-spawn.png")
        as_bot(f"tp @s {x} {y} {z} ~180 10")
        time.sleep(2)
        shot(tools, shots / f"rl-3-{realm}-behind.png")
        # straight above the landing: farther out, the Realm's edge rule carries the player back to it
        undo = perch(world, x, y + 14, z + 8, 180, 45)
        time.sleep(3)
        shot(tools, shots / f"rl-3-{realm}-above.png")
        undo()
        if os.environ.get("E2E_REALM_VIEWS"):  # the floating monument and its relic, from the landing and from afar
            subprocess.run([xdo, "key", "F1"], env=env)
            for label, dx, dy, dz, yaw, pitch in (("monument", 0, 0, 0, 180, -28), ("wide", 26, 18, 22, 140, -6)):
                undo = perch(world, x + dx, y + dy, z + dz, yaw, pitch)
                time.sleep(3)
                shot(tools, shots / f"rl-3-{realm}-{label}.png")
                undo()
            subprocess.run([xdo, "key", "F1"], env=env)

        # The return arch (ReturnArch): two blocks behind the arrival spot, veil on the south side. Stand 3 blocks from
        # the glass facing it (yaw 0 = south), take the shot, walk into it, and expect the Quai (the hub world).
        if realm in arch_walk:
            bx, bz = math.floor(x), math.floor(z)
            if realm == "grove":  # the Grove arch stands east of the landing (ReturnArch.side): its bridge leaves south
                as_bot(f"tp @s {bx + 0.5 - 1.0} {y} {bz + 0.5} -90 4")
            else:
                as_bot(f"tp @s {bx + 0.5} {y} {bz + 0.5 - 1.0} 0 4")
            time.sleep(3)
            shot(tools, shots / f"rl-3-{realm}-arch.png")
            if os.environ.get("E2E_ARCH_FAR"):  # the whole arch and the halo over it, from the far side of the landing
                if realm == "grove":
                    far_undo = perch(world, bx + 0.5 - 7.0, y + 1, bz + 0.5, -90, -14)
                else:
                    far_undo = perch(world, bx + 0.5, y + 1, bz + 0.5 - 7.0, 0, -14)
                time.sleep(3)
                subprocess.run([xdo, "key", "F1"], env=env)
                shot(tools, shots / f"rl-3-{realm}-arch-far.png")
                subprocess.run([xdo, "key", "F1"], env=env)
                far_undo()
                as_bot(f"tp @s {bx + 0.5 - (1.0 if realm == 'grove' else 0)} {y} {bz + 0.5 - (0 if realm == 'grove' else 1.0)} {-90 if realm == 'grove' else 0} 4")
                time.sleep(1)
            subprocess.run([xdo, "keydown", "w"], env=env)
            time.sleep(2.5)
            subprocess.run([xdo, "keyup", "w"], env=env)
            if wait_world("sb_hub", 15):
                time.sleep(3)
                say(f"realms: walked back from {realm} through its arch to the Quai")
                shot(tools, shots / f"rl-3-{realm}-arch-quai.png")
            else:
                failures.append(f"{realm}: walking into the return arch did not reach sb_hub: {dimension().strip()[-40:]} {tail()}")
                shot(tools, shots / f"rl-3-{realm}-arch-failed.png")

    # 4b. The seals (bastion.SealDoors): right-click the seal of a Realm's sealed door, first without the key (the Bastion's own
    # refusal names the key), then with it (the bot lands in an sb_bastion_ world), then abort. E2E_SEAL=all|a,b picks the
    # Realms (default ashes,grove); the seal coordinates come from `bastion admin seals`, so the script writes none.
    seal_env = os.environ.get("E2E_SEAL", "ashes,grove")
    seal_realms = [r for r, _ in REALM_WORLDS] if seal_env == "all" else [r for r in seal_env.split(",") if r]
    if seal_realms and "seal" not in skip:
        clog = WORK / "prism" / "instances" / INSTANCE / "minecraft" / "logs" / "latest.log"

        def chat():
            """The chat lines the client received (client log), without the command noise."""
            if not clog.exists():
                return []
            return [l.split("[CHAT] ", 1)[1] for l in clog.read_text(errors="replace").splitlines()
                    if "[CHAT] " in l and "Teleported" not in l]

        seals = {}
        for line in strip(rcon.command("bastion admin seals")).splitlines():
            m = _re.search(r"seal (\w+) (\w+) (-?\d+) (-?\d+) (-?\d+) face (-?\d+) (-?\d+) above (-?\d+) opens (\w+)", line.strip())
            if m:
                seals[m.group(1)] = (m.group(2), *map(int, m.group(3, 4, 5, 6, 7, 8)), m.group(9))
        say("realms: seals " + str(seals))
        if len(seals) != 8:
            failures.append(f"`bastion admin seals` listed {len(seals)} seals instead of 8: {list(seals)}")
        seal_blocks = {"ashes": "respawn_anchor", "grove": "shroomlight", "heights": "pearlescent_froglight", "citadel": "gold_block",
                       "marshes": "ochre_froglight", "archipelago": "lodestone", "depths": "sea_lantern", "necropolis": "verdant_froglight"}
        seal_keys = {"ashes": ("magma_core", "Ashes Bastion Key"), "grove": ("matriarch_egg", "Grove Bastion Key"),
                     "heights": ("frost_heart", "Heights Bastion Key"), "citadel": ("pharaoh_scarab", "Citadel Bastion Key"),
                     "marshes": ("mire_cyst", "Marsh Bastion Key"), "archipelago": ("tempest_key", "Tempest Bastion Key"),
                     "depths": ("leviathan_scale", "Abyss Bastion Key"), "necropolis": ("judge_seal", "Necropolis Bastion Key")}

        def stand_before(seal, dist, mode):
            world, sx, sy, sz, fx, fz, above, _ = seal  # above: the seal's height over the standing level
            px, py, pz = sx + 0.5 + fx * dist, sy - above, sz + 0.5 + fz * dist
            yaw = math.degrees(math.atan2(fx, -fz))
            pitch = -math.degrees(math.atan2((sy + 0.5) - (py + 1.62), dist))
            rcon.command(f"gamemode {mode} E2EBot")
            rcon.command(f"execute in minecraft:{world} run tp E2EBot {px:.2f} {py} {pz:.2f} {yaw:.1f} {pitch:.1f}")

        for realm in seal_realms:
            world = dict(REALM_WORLDS).get(realm)
            seal = seals.get(realm)
            if seal is None:
                failures.append(f"seal {realm}: `bastion admin seals` did not list it: {list(seals)}")
                continue
            key, key_name = seal_keys[realm]
            rcon.command("gamemode creative E2EBot")
            rcon.command("effect give E2EBot minecraft:night_vision 900 0 true")
            reply = strip(rcon.command(f"gateadmin travel E2EBot {realm}"))
            if not wait_world(world, 30):
                failures.append(f"seal {realm}: the Gate did not reach {world}: '{reply}' {tail()}")
                continue
            time.sleep(4)
            _, sx, sy, sz = seal[:4]
            if "failed" in rcon.command(f"execute in minecraft:{world} if block {sx} {sy} {sz} minecraft:{seal_blocks[realm]}").lower():
                failures.append(f"seal {realm}: no minecraft:{seal_blocks[realm]} at {sx} {sy} {sz} (the plan and the world differ)")
            stand_before(seal, 3 if realm == "marshes" else 5, "creative")  # the Marsh islet ends 3 blocks in front of its door
            time.sleep(3)
            shot(tools, shots / f"rl-6-seal-{realm}-door.png")
            rcon.command("clear E2EBot")
            rcon.command("effect give E2EBot minecraft:resistance 900 4 true")
            rcon.command("effect give E2EBot minecraft:regeneration 900 4 true")
            stand_before(seal, 2, "survival")
            time.sleep(2)
            shot(tools, shots / f"rl-6-seal-{realm}-close.png")
            before = len(chat())
            subprocess.run([xdo, "click", "3"], env=env)
            time.sleep(1.5)
            shot(tools, shots / f"rl-6-seal-{realm}-refused.png")
            refusal = f"You need a {key_name}"
            if not any(refusal in l for l in chat()[before:]):
                failures.append(f"seal {realm}: no refusal naming '{key_name}' in the chat: {chat()[before:][-4:]}")
            if f'"minecraft:{world}' not in dimension():
                failures.append(f"seal {realm}: the bot left {world} without a key: {dimension()[:80]}")
            rcon.command(f"sbgive {key} 1 E2EBot")
            time.sleep(1)
            stand_before(seal, 2, "survival")
            time.sleep(1)
            before = len(chat())
            subprocess.run([xdo, "click", "3"], env=env)
            if wait_world("sb_bastion_", 30):
                say(f"realms: seal {realm} opened a Bastion for the bot")
                time.sleep(2)
                shot(tools, shots / f"rl-6-seal-{realm}-opened.png")
                if any("This is a Bastion key" in l for l in chat()[before:]):
                    failures.append(f"seal {realm}: the key in the hand also drew the summon refusal: {chat()[before:][-4:]}")
            else:
                failures.append(f"seal {realm}: right-clicking it with {key} did not open a Bastion: {dimension()[:80]} {tail()}")
                shot(tools, shots / f"rl-6-seal-{realm}-no-bastion.png")
            rcon.command("bastion admin abort all")
            deadline = time.time() + 45
            while time.time() < deadline and "sb_bastion_" in dimension():
                time.sleep(2)
            if "sb_bastion_" in dimension():
                failures.append(f"seal {realm}: the bot was not taken out of the aborted Bastion")
            rcon.command("clear E2EBot")
        rcon.command("gamemode creative E2EBot")

    # 5. The Astral Interiority (the way in is the command for now)
    if "astral" not in skip:
        typed("astral go force", 2)
        if wait_world("sb_astral", 30):
            time.sleep(4)
            shot(tools, shots / "rl-4-astral-spawn.png")
            x, y, z = player_state(rcon)["pos"]
            undo = perch("sb_astral", x, y + 14, z + 8, 180, 45)
            time.sleep(3)
            shot(tools, shots / "rl-4-astral-above.png")
            undo()
            if os.environ.get("E2E_ASTRAL_VIEWS"):  # every region from where it reads best, no HUD
                subprocess.run([xdo, "key", "F1"], env=env)
                for label, vx, vy, vz, yaw, pitch in (("wound", 0.5, 102, 7.5, 180, -18), ("sea", 60.5, 126, -48.5, -24, 24),
                                                      ("tree", 160.5, 142, -32.5, -47, -4), ("singularity", 252.5, 206, -38.5, -52, 6),
                                                      ("throne", 308.5, 203, 7.5, -110, -14)):
                    undo = perch("sb_astral", vx, vy, vz, yaw, pitch)
                    time.sleep(10)  # a jump of a hundred blocks: let the chunks arrive
                    shot(tools, shots / f"rl-4a-{label}.png")
                    undo()
                subprocess.run([xdo, "key", "F1"], env=env)
        else:
            failures.append("/astral go force did not reach sb_astral: " + dimension()[:80] + " " + tail())
            shot(tools, shots / "rl-4-astral-failed.png")

    # 6. A Bastion: the key is spent when the countdown ends and the Guardian wakes
    if "bastion" not in skip:
        typed("hub")
        wait_world("sb_hub")
        time.sleep(3)
        rcon.command("clear E2EBot")
        rcon.command("gamemode survival E2EBot")
        rcon.command("effect give E2EBot minecraft:resistance 600 4 true")
        rcon.command("effect give E2EBot minecraft:regeneration 600 4 true")
        rcon.command("sbgive magma_core 1 E2EBot")
        time.sleep(1)
        if items() != 1:
            failures.append(f"sbgive magma_core: {items()} items in the inventory instead of the key")
        typed("bastion enter ashes_bastion", 2)
        if not wait_world("sb_bastion_", 30):
            failures.append("/bastion enter ashes_bastion did not open an instance: " + dimension()[:80] + " " + tail())
            shot(tools, shots / "rl-5-bastion-failed.png")
        else:
            time.sleep(2)
            shot(tools, shots / "rl-5-bastion-countdown.png")
            if items() != 1:
                failures.append(f"the Ashes Bastion key left the inventory before the countdown ended ({items()} items)")
            time.sleep(10)
            if items() != 0:  # the leader's key is spent when the countdown ends and the Guardian wakes
                failures.append(f"the Ashes Bastion key was not spent when the Guardian woke ({items()} items left)")
            active = strip(rcon.command("boss active"))
            say("realms: bastion boss: " + active[:120])
            if "magma_warden" not in active:
                failures.append("no Guardian in the Ashes Bastion: " + active[:120])
            entry = player_state(rcon)["pos"]
            as_bot(f"tp @s {entry[0]} {entry[1] + 2} {entry[2] + 4} 180 12")  # the entry faces the arena; step back
            time.sleep(2)
            shot(tools, shots / "rl-5-bastion-guardian.png")
            if os.environ.get("E2E_BASTION_VIEWS"):  # the arena itself (after the key check: the views take longer than the countdown)
                bworld = _re.search(r'"minecraft:(sb_bastion_[0-9]+)', dimension())
                if bworld:
                    subprocess.run([xdo, "key", "F1"], env=env)
                    for label, vx, vy, vz, yaw, pitch in (("over-entry", 0.5, 114, 34.5, 180, 24), ("outside", 52.5, 122, 52.5, 135, 18),
                                                          ("floor", 0.5, 101, 20.5, 180, -8)):
                        undo = perch(bworld.group(1), vx, vy, vz, yaw, pitch)
                        time.sleep(3)
                        shot(tools, shots / f"rl-5b-{label}.png")
                        undo()
                    subprocess.run([xdo, "key", "F1"], env=env)
            rcon.command("bastion admin abort all")
            time.sleep(4)
            shot(tools, shots / "rl-5-bastion-aborted.png")
            time.sleep(16)  # exit-delay-seconds (15) before the party is sent back
            shot(tools, shots / "rl-5-bastion-after.png")
            if "sb_bastion_" in dimension():
                failures.append("the bot was not taken out of the aborted Bastion")
        rcon.command("gamemode creative E2EBot")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--scenario", choices=["shader", "werewolf", "roles", "tooltip", "debughud", "showcase", "staffchat", "back", "skyblock", "quarry", "progress", "bosses", "swords", "armorwings", "tools", "imported", "dungeon", "start", "craft", "realms", "machines", "endgame", "valoblock", "vbhold", "sbhold", "worlds", "dev", "vfx", "hubs"], default="shader")
    ap.add_argument("--only", default="", help="roles scenario: comma-separated role names")
    ap.add_argument("--rules", default="", help="werewolf scenario: variants, e.g. voting=nominate,reveal=none,amulet=on,dice=on")
    ap.add_argument("--keep", action="store_true", help="leave the server and client running")
    ap.add_argument("--shots", default=str(WORK / "shots"))
    args = ap.parse_args()
    if args.scenario == "dev":
        return dev_server()
    global WITH_SURVIVAL, VALOBLOCK
    VALOBLOCK = args.scenario in ("valoblock", "vbhold")
    WITH_SURVIVAL = args.scenario in ("back", "skyblock", "quarry", "progress", "bosses", "swords", "armorwings", "tools", "imported", "dungeon", "start", "craft", "realms", "machines", "endgame", "sbhold", "vfx")
    if args.scenario == "hubs":
        os.environ.setdefault("E2E_SERVER_ID", "creative")  # WaitingHubs only builds on the creative server
    if not (PRISM / "instances" / INSTANCE).exists():
        sys.exit(f"No Prism instance {INSTANCE} under {PRISM}")
    WORK.mkdir(exist_ok=True)
    shots = pathlib.Path(args.shots)
    shots.mkdir(parents=True, exist_ok=True)
    tools = {"xdotool": tool("xdotool", "xdotool"), "import": None, "magick": None}
    magick_dir = pathlib.Path(tool("magick", "imagemagick")).parent
    tools["magick"] = str(magick_dir / "magick")
    tools["import"] = str(magick_dir / "import")

    # Two runs share the ports and .e2e/: queue behind any other run, whoever started it.
    import fcntl
    lock = open("/tmp/vampyuyu-e2e.lock", "w")
    say("waiting for the e2e lock (another run may be using the ports)")
    fcntl.flock(lock, fcntl.LOCK_EX)
    stop_stale()
    pack_dir = WORK / "pack"
    zip_path, sha1 = build_pack.build(pack_dir)
    say(f"pack {zip_path.name} sha1 {sha1}")
    http_server = serve_pack(pack_dir)
    procs, failures = [], []
    try:
        server_proc, server = start_server(zip_path, sha1, plugin=args.scenario in ("werewolf", "roles", "tooltip", "debughud", "showcase", "staffchat", "back", "skyblock", "quarry", "progress", "bosses", "swords", "armorwings", "tools", "imported", "dungeon", "start", "craft", "realms", "machines", "endgame", "valoblock", "vbhold", "sbhold", "worlds", "vfx", "hubs"))
        procs.append(server_proc)
        say("server up (creative flat, offline)")
        data, game = prepare_client(tools)
        xvfb = subprocess.Popen(["Xvfb", DISPLAY, "-screen", "0", "1280x720x24"],
                                stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        procs.append(xvfb)
        time.sleep(2)
        env = dict(os.environ, DISPLAY=DISPLAY, SDL_VIDEODRIVER="x11", QT_QPA_PLATFORM="xcb",
                   VK_ICD_FILENAMES="/run/opengl-driver/share/vulkan/icd.d/lvp_icd.x86_64.json")
        env.pop("WAYLAND_DISPLAY", None)
        client = subprocess.Popen(["prismlauncher", "-d", str(data), "-l", INSTANCE, "-o", "E2EBot",
                                   "-s", f"127.0.0.1:{MC_PORT}"], env=env,
                                  stdout=open(WORK / "prism.log", "w"), stderr=subprocess.STDOUT)
        procs.append(client)
        client_log = game / "logs" / "latest.log"
        say("client starting (software Vulkan, slow)")
        if not wait_for(client_log, "Reloading ResourceManager", 300, [client]):
            sys.exit("client never reached the resource manager; see .e2e/prism.log")
        # Accept the server pack prompt, if it shows up.
        accepted = False
        for _ in range(120):
            if (server / "console.log").read_text().count("E2EBot joined the game"):
                accepted = True
                break
            # "Proceed" of the required-pack prompt (left button of the 854x480 window).
            subprocess.run([tools["xdotool"], "mousemove", "485", "417", "click", "1"], env=env)
            time.sleep(2)
        if not wait_for(server / "console.log", "E2EBot joined the game", 120, [client]):
            shot(tools, shots / "join-failed.png")
            sys.exit("the bot never joined: see " + str(shots / "join-failed.png"))
        if args.scenario in ("werewolf", "roles", "tooltip", "debughud", "showcase", "staffchat", "back", "skyblock", "quarry", "progress", "bosses", "swords", "armorwings", "tools", "imported", "dungeon", "start", "craft", "realms", "machines", "endgame", "valoblock", "vbhold", "sbhold", "worlds", "vfx", "hubs"):
            # The network plugin offers the pack after the join: accept it, then wait for the reload.
            time.sleep(4)
            shot(tools, shots / "after-join.png")
            for i in range(60):
                if client_log.read_text(errors="replace").count("Reloading ResourceManager") >= 2:
                    break
                # The server's own message line pushes the buttons 28 px lower than in the config phase.
                subprocess.run([tools["xdotool"], "mousemove", "485", "445", "click", "1"], env=env)
                time.sleep(3)
            else:
                failures.append("the pack offered after the join was never loaded")
            time.sleep(5)
        say("bot joined with the server pack")
        time.sleep(15)
        text = client_log.read_text(errors="replace")
        for needle in FATAL:
            if needle in text:
                failures.append(f"client log contains '{needle}'")
        ctx = dict(tools=tools, env=env, shots=shots, client_log=client_log, server=server,
                   failures=failures, rules=args.rules, only=args.only)
        if args.scenario == "werewolf":
            werewolf_scenario(ctx)
        elif args.scenario == "roles":
            roles_scenario(ctx, args.only)
        elif args.scenario == "tooltip":
            tooltip_scenario(ctx)
        elif args.scenario == "debughud":
            debughud_scenario(ctx)
        elif args.scenario == "showcase":
            showcase_scenario(ctx)
        elif args.scenario == "staffchat":
            staffchat_scenario(ctx)
        elif args.scenario == "worlds":
            worlds_scenario(ctx)
        elif args.scenario == "hubs":
            hubs_scenario(ctx)
        elif args.scenario == "back":
            back_scenario(ctx)
        elif args.scenario == "skyblock":
            skyblock_scenario(ctx)
        elif args.scenario == "imported":
            imported_scenario(ctx)
        elif args.scenario == "quarry":
            quarry_scenario(ctx)
        elif args.scenario == "progress":
            progress_scenario(ctx)
        elif args.scenario == "bosses":
            bosses_scenario(ctx)
        elif args.scenario == "swords":
            swords_scenario(ctx)
        elif args.scenario == "armorwings":
            armorwings_scenario(ctx)
        elif args.scenario == "tools":
            tools_scenario(ctx)
        elif args.scenario == "dungeon":
            dungeon_scenario(ctx)
        elif args.scenario == "start":
            start_scenario(ctx)
        elif args.scenario == "craft":
            craft_scenario(ctx)
        elif args.scenario == "realms":
            realms_scenario(ctx)
        elif args.scenario == "machines":
            machines_scenario(ctx)
        elif args.scenario == "endgame":
            endgame_scenario(ctx)
        elif args.scenario == "vfx":
            vfx_scenario(ctx)
        elif args.scenario == "valoblock":
            valoblock_scenario(ctx)
        elif args.scenario in ("vbhold", "sbhold"):
            hold_scenario(ctx)
        else:
            shader_scenario(ctx)
    finally:
        if not args.keep:
            for p in reversed(procs):
                p.terminate()
            time.sleep(2)
            for p in procs:
                if p.poll() is None:
                    p.kill()
            stop_stale()
        http_server.shutdown()
    http_server.server_close()
    if args.keep:
        # Keep the optional pack available for a human joining the isolated server after the run.
        pack_log = open(WORK / "pack-server.log", "a")
        subprocess.Popen([sys.executable, "-m", "http.server", str(HTTP_PORT), "--bind", "127.0.0.1",
                          "--directory", str(pack_dir)], stdout=pack_log, stderr=subprocess.STDOUT,
                         start_new_session=True)
    if failures:
        sys.exit("E2E FAILED:\n  " + "\n  ".join(failures))
    say("E2E OK")


if __name__ == "__main__":
    main()
