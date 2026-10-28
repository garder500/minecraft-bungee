#!/usr/bin/env python3
"""End-to-end check of the resource pack: a creative-only Paper 26.3 server, a headless vanilla
26.3 client (Prism offline, Xvfb, Mesa lavapipe) and the real pack served over HTTP.

    make e2e-pack          # or: python3 scripts/e2e_client.py [--keep] [--shots DIR]

It fails when the client reports "Resource reload failed", a shader pipeline that does not compile
or a missing glyph, and it checks on screenshots that the night effect really darkens the screen.
Everything lives in the gitignored .e2e/ folder; nothing of the real stack is touched. Needs: java 25
(JAVA25), Prism Launcher with an installed 26.3 instance (PRISM_DIR, PRISM_INSTANCE), Xvfb, and
xdotool and ImageMagick (found on PATH or built through nix). EULA=TRUE must be set in .env.
"""
import argparse, shlex, hashlib, http.server, json, os, pathlib, re, shutil, socket, struct, subprocess, sys
import threading, time, urllib.request, uuid

ROOT = pathlib.Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT / "scripts"))
import build_pack

WORK = ROOT / ".e2e"
PAPER = ("26.3", "223", "f8e684a4fef2a8b46d03aac049b49d59275e9ce5367dba7c6075cb5780f38470")
MC_PORT, RCON_PORT, HTTP_PORT, DISPLAY = 25599, 25598, 25597, ":99"
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
    for pattern in ("[.]e2e/paper", "[p]rismlauncher -d", "[X]vfb " + DISPLAY):
        subprocess.run(["pkill", "-9", "-f", pattern], check=False)
    time.sleep(1)


ARENA_JAR = ROOT / "arena-plugin/target/nexus-arena-engine.jar"
NETWORK_JAR = ROOT / "network-plugin/target/network-display.jar"
SURVIVAL_JAR = ROOT / "survival-plugin/target/survival-plugin.jar"
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


def start_server(zip_path, sha1, plugin=False, pack_required=True):
    server = WORK / "server"
    shutil.rmtree(server, ignore_errors=True)
    server.mkdir(parents=True)
    (server / "eula.txt").write_text("eula=true\n")
    (server / "server.properties").write_text("\n".join([
        "online-mode=false", "white-list=false", "enforce-whitelist=false", "gamemode=creative", "force-gamemode=true", "level-type=minecraft:flat",
        "generate-structures=false",
        'generator-settings={"biome":"minecraft:plains","layers":[{"block":"minecraft:bedrock","height":1},{"block":"minecraft:dirt","height":2},{"block":"minecraft:grass_block","height":1}]}', "spawn-monsters=false", "spawn-protection=0", "view-distance=4",
        f"server-port={MC_PORT}", "enable-rcon=true", f"rcon.port={RCON_PORT}",
        f"rcon.password={RCON_PASSWORD}", "enforce-secure-profile=false",
        *([] if plugin else [
            f"resource-pack=http://127.0.0.1:{HTTP_PORT}/{zip_path.name}",
            f"resource-pack-sha1={sha1}", "require-resource-pack=true"]),
        "motd=e2e", "level-name=world", ""]))
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
        (data / "arenas" / "e2e.json").write_text(json.dumps(ARENA, indent=1))
        (server / "token").write_text(API_TOKEN)
        env.update(ROLE_BRIDGE_TOKEN_FILE=str(server / "token"), SERVER_ID="survival",
                   MENU_PACK_BASE_URL=f"http://127.0.0.1:{HTTP_PORT}", MENU_PACK_REQUIRED="true" if pack_required else "false",
                   MENU_PACK_SHA1_FILE=str(WORK / "pack" / "vampyuyu-menus.sha1"),
                   ROLE_BRIDGE_URL="http://127.0.0.1:9", ARENA_REDIS_HOST="127.0.0.1",
                   WEREWOLF_LOG_CONSOLE="false")
    log = open(server / "console.log", "w")
    # DEBUG_PORT: JDWP for scripts/hotswap.py (class redefinition without a restart).
    debug = [f"-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=127.0.0.1:{DEBUG_PORT}"] if DEBUG else []
    command = [java25(), "-Xmx1G", *debug, "-jar", str(paper_jar()), "--nogui"]
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
    game = dst / "minecraft"
    game.mkdir()
    (game / "options.txt").write_text("\n".join([
        "lang:en_us", "onboardAccessibility:false", "tutorialStep:none", "joinedFirstServer:true",
        "fullscreen:false", "maxFps:30", "renderDistance:4", "pauseOnLostFocus:false",
        "skipMultiplayerWarning:true", "narrator:0", "hideServerAddress:true", ""]))
    return data, game


def shot(tools, path):
    env = dict(os.environ, DISPLAY=DISPLAY)
    subprocess.run([tools["import"], "-window", "root", str(path)], env=env, check=True)


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
        time.sleep(2.5)

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


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--scenario", choices=["shader", "werewolf", "roles", "tooltip", "debughud", "showcase", "staffchat", "back", "dev"], default="shader")
    ap.add_argument("--only", default="", help="roles scenario: comma-separated role names")
    ap.add_argument("--rules", default="", help="werewolf scenario: variants, e.g. voting=nominate,reveal=none,amulet=on,dice=on")
    ap.add_argument("--keep", action="store_true", help="leave the server and client running")
    ap.add_argument("--shots", default=str(WORK / "shots"))
    args = ap.parse_args()
    if args.scenario == "dev":
        return dev_server()
    global WITH_SURVIVAL
    WITH_SURVIVAL = args.scenario == "back"
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
        server_proc, server = start_server(zip_path, sha1, plugin=args.scenario in ("werewolf", "roles", "tooltip", "debughud", "showcase", "staffchat", "back"))
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
        if args.scenario in ("werewolf", "roles", "tooltip", "debughud", "showcase", "staffchat", "back"):
            # The network plugin offers the pack after the join: accept it, then wait for the reload.
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
                   failures=failures, rules=args.rules)
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
        elif args.scenario == "back":
            back_scenario(ctx)
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
    if failures:
        sys.exit("E2E FAILED:\n  " + "\n  ".join(failures))
    say("E2E OK")


if __name__ == "__main__":
    main()
