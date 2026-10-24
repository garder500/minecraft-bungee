#!/usr/bin/env python3
"""Starts a throw-away stack to look at the panel's inventory view with real data:

    python3 scripts/e2e_panel.py            # prints the URL, stays up until Ctrl-C / SIGTERM

A creative Paper 26.3 with the network plugin and two offline players whose inventories are written
here (one has a real Mojang UUID, so its skin comes from Mojang), the real Go panel, a small fake
controller that forwards the game routes to the plugin, and the Vite dev server. Sign in as
admin / the password printed below. Everything lives in .e2e/ and the ports of e2e_client.py.
"""
import gzip, http.server, json, os, pathlib, shutil, signal, socketserver, struct, subprocess, sys, threading, time, urllib.request

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
import e2e_client as e2e  # noqa: E402

ROOT, WORK = e2e.ROOT, e2e.WORK
PANEL_PORT, CONTROLLER_PORT, VITE_PORT = 8080, 18081, 5173
PASSWORD = "Correct-Horse-Battery-9"
JEB = "853c80ef-3c37-49fd-aa49-938b674adae6"
OTHER = "069a79f4-44e9-4726-a5be-fca90e38aaf5"


# ------------------------------------------------------------------ NBT writer
def _name(s):
    b = s.encode()
    return struct.pack(">H", len(b)) + b


def tag(kind, value):
    if kind == "byte": return 1, struct.pack(">b", value)
    if kind == "int": return 3, struct.pack(">i", value)
    if kind == "float": return 5, struct.pack(">f", value)
    if kind == "double": return 6, struct.pack(">d", value)
    if kind == "string": return 8, _name(value)
    if kind == "list":
        inner = value[0][0] if value else 0
        return 9, struct.pack(">bi", inner, len(value)) + b"".join(p for _, p in value)
    if kind == "compound":
        out = b""
        for key, (t, payload) in value.items():
            out += struct.pack(">b", t) + _name(key) + payload
        return 10, out + b"\x00"
    raise ValueError(kind)


def item(slot, id, count=1, components=None):
    fields = {"Slot": tag("byte", slot), "id": tag("string", id), "count": tag("int", count)}
    if components:
        fields["components"] = tag("compound", components)
    return tag("compound", fields)


def enchants(**levels):
    return tag("compound", {f"minecraft:{k}": tag("int", v) for k, v in levels.items()})


def player(level, items):
    root = {
        "DataVersion": tag("int", 4800), "Inventory": tag("list", items), "EnderItems": tag("list", []),
        "XpLevel": tag("int", level), "playerGameType": tag("int", 0), "Health": tag("float", 20.0),
        "foodLevel": tag("int", 20), "Dimension": tag("string", "minecraft:overworld"),
        "Pos": tag("list", [tag("double", 0.5), tag("double", -60.0), tag("double", 0.5)]),
    }
    t, payload = tag("compound", root)
    return gzip.compress(struct.pack(">b", t) + _name("") + payload)


def inventories():
    blade = {
        "minecraft:enchantments": enchants(sharpness=5, unbreaking=3, mending=1, fire_aspect=2),
        "minecraft:custom_name": tag("string", json.dumps({"text": "Vampyuyu Blade", "color": "gold", "italic": False})),
        "minecraft:lore": tag("list", [tag("string", json.dumps({"text": "Forged in the moonlight", "color": "dark_purple"})),
                                       tag("string", json.dumps({"text": "Owner: jeremy", "color": "gray"}))]),
        "minecraft:custom_data": tag("compound", {"owner": tag("string", "jeremy"), "tier": tag("int", 3)}),
        "minecraft:damage": tag("int", 120),
    }
    return {
        JEB: player(15, [
            item(0, "minecraft:diamond_sword", 1, blade),
            item(1, "minecraft:bow", 1, {"minecraft:enchantments": enchants(power=5, infinity=1, punch=2)}),
            item(2, "minecraft:diamond_pickaxe", 1, {"minecraft:enchantments": enchants(efficiency=5, fortune=3, unbreaking=3)}),
            item(3, "minecraft:oak_log", 7), item(4, "minecraft:bread", 13), item(5, "minecraft:white_wool", 11),
            item(9, "minecraft:golden_apple", 5), item(10, "minecraft:coal", 29),
            item(11, "minecraft:enchanted_book", 1, {"minecraft:stored_enchantments": enchants(protection=4)}),
            item(100, "minecraft:diamond_boots", 1, {"minecraft:enchantments": enchants(feather_falling=4, protection=4)}),
            item(101, "minecraft:diamond_leggings"), item(102, "minecraft:diamond_chestplate", 1, {"minecraft:enchantments": enchants(protection=4, thorns=3)}),
            item(103, "minecraft:diamond_helmet", 1, {"minecraft:enchantments": enchants(respiration=3, aqua_affinity=1, binding_curse=1)}),
            item(-106, "minecraft:shield"),
        ]),
        OTHER: player(3, [item(0, "minecraft:stone_sword"), item(1, "minecraft:torch", 32)]),
    }


# ------------------------------------------------------------------- controller
def serve_controller(token, network_token):
    class Handler(http.server.BaseHTTPRequestHandler):
        def log_message(self, *a): pass

        def do(self, method):
            if self.headers.get("Authorization") != f"Bearer {token}":
                self.send_response(401); self.end_headers(); return
            path = self.path
            length = int(self.headers.get("Content-Length") or 0)
            body = self.rfile.read(length) if length else None
            if path == "/servers":
                data, status = json.dumps([{"id": "creative", "state": "running", "health": "healthy", "commands": True}]).encode(), 200
            elif path.startswith("/servers/creative/game/"):
                upstream = "/" + path[len("/servers/creative/game/"):]
                request = urllib.request.Request("http://127.0.0.1:8084" + upstream, data=body, method=method,
                                                 headers={"Authorization": f"Bearer {network_token}", "Content-Type": "application/json"})
                try:
                    with urllib.request.urlopen(request, timeout=20) as r:
                        data, status = r.read(), r.status
                except urllib.error.HTTPError as e:
                    data, status = e.read(), e.code
            elif path.endswith("/plugins") or path.endswith("/worlds"):
                data, status = b"[]", 200
            else:
                data, status = b"{}", 200
            self.send_response(status)
            self.send_header("Content-Type", "application/json")
            self.send_header("Content-Length", str(len(data)))
            self.end_headers()
            self.wfile.write(data)

        do_GET = lambda self: self.do("GET")
        do_POST = lambda self: self.do("POST")
        do_PUT = lambda self: self.do("PUT")

    class Server(socketserver.ThreadingMixIn, http.server.HTTPServer):
        daemon_threads = True

    server = Server(("127.0.0.1", CONTROLLER_PORT), Handler)
    threading.Thread(target=server.serve_forever, daemon=True).start()
    return server


def main():
    import fcntl
    lock = open("/tmp/vampyuyu-e2e.lock", "w")
    print("[panel] waiting for the e2e lock…", flush=True)
    fcntl.flock(lock, fcntl.LOCK_EX)
    e2e.stop_stale()
    subprocess.run(["pkill", "-9", "-f", "[.]e2e/panel-bin"], check=False)
    subprocess.run(["pkill", "-9", "-f", "[.]e2e/vite"], check=False)
    WORK.mkdir(exist_ok=True)
    zip_path, sha1 = e2e.build_pack.build(WORK / "pack")
    procs = []
    try:
        server_proc, server = e2e.start_server(zip_path, sha1, plugin=True)
        procs.append(server_proc)
        print("[panel] server up", flush=True)
        # Players are written while the server runs: it reads offline players from the file on demand.
        playerdata = server / "world" / "playerdata"
        playerdata.mkdir(parents=True, exist_ok=True)
        for uuid, data in inventories().items():
            (playerdata / f"{uuid}.dat").write_bytes(data)
        # The panel and its fake controller (secrets in a private /run, like the other plugins).
        go = shutil.which("go")
        nixrun = ["nix", "--extra-experimental-features", "nix-command flakes", "run", "nixpkgs#go", "--"]
        binary = WORK / "panel-bin"
        subprocess.run((nixrun if not go else [go]) + ["build", "-o", str(binary), "./cmd/panel"], cwd=ROOT, check=True)
        state = WORK / "panel"
        shutil.rmtree(state, ignore_errors=True)
        state.mkdir()
        controller_token = "c" * 40
        network_token = e2e.API_TOKEN
        serve_controller(controller_token, network_token)
        store_bin = os.path.dirname(os.path.realpath(shutil.which("mkdir")))
        env = dict(os.environ, DB_PATH=str(state / "panel.db"), PUBLIC_ORIGIN=f"http://localhost:{VITE_PORT}",
                   CONTROLLER_URL=f"http://127.0.0.1:{CONTROLLER_PORT}", MC_VERSION="26.3", EULA="TRUE")
        import shlex
        script = (f'mount -t tmpfs tmpfs /run && {store_bin}/mkdir -p /run/secrets && '
                  f'printf %s {controller_token} > /run/secrets/controller_token && '
                  f'printf %s {network_token} > /run/secrets/role_bridge_token && ')
        subprocess.run(["unshare", "-rm", "bash", "-c", script + f'printf %s {shlex.quote(PASSWORD)} | {shlex.quote(str(binary))} admin admin'],
                       env=env, check=True)
        panel = subprocess.Popen(["unshare", "-rm", "bash", "-c", script + f"exec {shlex.quote(str(binary))}"], env=env,
                                 stdout=open(WORK / "panel.log", "w"), stderr=subprocess.STDOUT)
        procs.append(panel)
        time.sleep(3)
        # Register the two players in the panel's directory, as the plugin does when they join.
        for uuid, name in ((JEB, "jeb_"), (OTHER, "Notch")):
            request = urllib.request.Request("http://127.0.0.1:8083/bridge/register", method="POST",
                data=json.dumps({"server": "creative", "players": [{"uuid": uuid, "name": name}]}).encode(),
                headers={"Authorization": f"Bearer {network_token}", "Content-Type": "application/json"})
            try:
                urllib.request.urlopen(request, timeout=10).read()
            except Exception as e:
                print("[panel] could not register", name, e, flush=True)
        web = ROOT / "web"
        config = web / "vite.e2e.config.mjs"  # removed again below; beside the sources so imports resolve
        config.write_text(f'''import react from "@vitejs/plugin-react";
export default {{ plugins: [react()],
  server: {{ port: {VITE_PORT}, strictPort: true, host: "127.0.0.1", proxy: {{ "/api": {{ target: "http://127.0.0.1:{PANEL_PORT}", changeOrigin: false }} }} }} }};
''')
        vite = subprocess.Popen([str(web / "node_modules/.bin/vite"), "--config", config.name], cwd=web,
                                stdout=open(WORK / "vite.log", "w"), stderr=subprocess.STDOUT)
        procs.append(vite)
        print(f"[panel] READY http://127.0.0.1:{VITE_PORT}  user admin  password {PASSWORD}", flush=True)
        signal.signal(signal.SIGTERM, lambda *a: sys.exit(0))
        while True:
            time.sleep(5)
    finally:
        (ROOT / "web" / "vite.e2e.config.mjs").unlink(missing_ok=True)
        for p in reversed(procs):
            p.terminate()
        time.sleep(2)
        for p in procs:
            if p.poll() is None:
                p.kill()
        e2e.stop_stale()


if __name__ == "__main__":
    main()
