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
import argparse, hashlib, http.server, os, pathlib, re, shutil, socket, struct, subprocess, sys
import threading, time, urllib.request

ROOT = pathlib.Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT / "scripts"))
import build_pack

WORK = ROOT / ".e2e"
PAPER = ("26.3", "218", "cdd56d33f27e77110e6f8b43cf6d9571c3854c9ae099986b24a3c18c5904cc11")
MC_PORT, RCON_PORT, HTTP_PORT, DISPLAY = 25599, 25598, 25597, ":99"
RCON_PASSWORD = "e2e-only"
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


def start_server(zip_path, sha1):
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
        f"resource-pack=http://127.0.0.1:{HTTP_PORT}/{zip_path.name}", f"resource-pack-sha1={sha1}",
        "require-resource-pack=true", "motd=e2e", "level-name=world", ""]))
    log = open(server / "console.log", "w")
    proc = subprocess.Popen([java25(), "-Xmx1G", "-jar", str(paper_jar()), "--nogui"], cwd=server,
                            stdout=log, stderr=subprocess.STDOUT, stdin=subprocess.DEVNULL)
    if not wait_for(server / "console.log", "Done (", 240, [proc]):
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


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--keep", action="store_true", help="leave the server and client running")
    ap.add_argument("--shots", default=str(WORK / "shots"))
    args = ap.parse_args()
    if not (PRISM / "instances" / INSTANCE).exists():
        sys.exit(f"No Prism instance {INSTANCE} under {PRISM}")
    WORK.mkdir(exist_ok=True)
    shots = pathlib.Path(args.shots)
    shots.mkdir(parents=True, exist_ok=True)
    tools = {"xdotool": tool("xdotool", "xdotool"), "import": None, "magick": None}
    magick_dir = pathlib.Path(tool("magick", "imagemagick")).parent
    tools["magick"] = str(magick_dir / "magick")
    tools["import"] = str(magick_dir / "import")

    stop_stale()
    pack_dir = WORK / "pack"
    zip_path, sha1 = build_pack.build(pack_dir)
    say(f"pack {zip_path.name} sha1 {sha1}")
    http_server = serve_pack(pack_dir)
    procs, failures = [], []
    try:
        server_proc, server = start_server(zip_path, sha1)
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
        say("bot joined with the server pack")
        time.sleep(15)
        text = client_log.read_text(errors="replace")
        for needle in FATAL:
            if needle in text:
                failures.append(f"client log contains '{needle}'")
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
        if not results["night"] < base * 0.75:
            failures.append("the night effect did not darken the screen")
        if abs(results["dawn"] - base) < 0.01 and abs(results["night"] - base) < 0.01:
            failures.append("no effect visible at all")
        text = client_log.read_text(errors="replace")
        for needle in FATAL:
            if needle in text and f"client log contains '{needle}'" not in failures:
                failures.append(f"client log contains '{needle}'")
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
