#!/usr/bin/env python3
"""Plugin hotswap on the local dev server: edit, `make hotswap PLUGIN=arena`, test, no restart.

    make dev-server               # Paper 26.3 + arena and network jars, JDWP on :25596 (see e2e_client.py)
    make hotswap PLUGIN=arena     # build the jar (no tests), redefine the classes that differ from the running ones
    make dev-stop

The classes come from the shaded jar, not target/classes: the shade plugin relocates gson, redis and
friends, and HotSpot refuses a redefinition whose signatures differ from the loaded class.
Method bodies swap live (commands, listeners, messages, game logic read per call). Adding or removing a
field or method, changing a signature or a static initializer cannot be redefined: the script names the
class that needs a restart (`make dev-server` again). Adding or removing a lambda counts as a method change. Classes the server has not loaded yet stay
pending (they run the old boot jar until swapped). Already-built objects keep their state and
constructors do not re-run. Resources inside the jar (roles/*.json, decor) are not refreshed. Lobby and
claims are not loaded by the dev server. The server is RCON-only: it does not require the menu pack, but no
client was checked against it. The dev server drops the e2e lock after boot, so any e2e run kills it. Production keeps scripts/plugins.sh: a hotswap there would mean
opening a debug port.
"""
import hashlib, json, os, pathlib, shutil, subprocess, sys, tempfile, zipfile

ROOT = pathlib.Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT / "scripts"))
import e2e_client as e2e

PLUGINS = {"arena": ("arena-plugin/target/nexus-arena-engine.jar", "nexus-arena-engine.jar"),
           "network": ("network-plugin/target/network-display.jar", "network-display.jar")}
STAMPS = e2e.WORK / "hotswap-stamps.json"


def class_hashes(jar):
    with zipfile.ZipFile(jar) as z:
        return {n: hashlib.sha256(z.read(n)).hexdigest() for n in z.namelist() if n.endswith(".class")}


def main():
    if len(sys.argv) != 2 or sys.argv[1] not in (*PLUGINS, "stop"):
        sys.exit(__doc__)
    if sys.argv[1] == "stop":
        e2e.stop_stale()
        STAMPS.unlink(missing_ok=True)
        return
    plugin = sys.argv[1]
    built, loaded = PLUGINS[plugin]
    subprocess.run([str(ROOT / "scripts/plugins.sh"), "build", plugin], check=True, env=dict(os.environ, FAST="1"))
    stamps = json.loads(STAMPS.read_text()) if STAMPS.exists() else {}
    # What runs now: the jar the server booted with, then whatever the previous swap pushed.
    running = stamps.get(plugin) or class_hashes(e2e.WORK / "server" / "plugins" / loaded)
    current = class_hashes(ROOT / built)
    changed = sorted(c for c, h in current.items() if running.get(c) != h)
    if not changed:
        print("nothing changed since the last swap")
        return
    with tempfile.TemporaryDirectory() as tmp:
        with zipfile.ZipFile(ROOT / built) as z:
            z.extractall(tmp, members=changed)
        result = subprocess.run([e2e.java25(), str(ROOT / "scripts/Redefine.java"), str(e2e.DEBUG_PORT), tmp, *changed],
                                capture_output=True, text=True)
    print("\n".join(l for l in result.stdout.splitlines() if not l.startswith("SKIPPED ")) or result.stderr.strip())
    skipped = [l.split(" ", 1)[1] for l in result.stdout.splitlines() if l.startswith("SKIPPED ")]
    if skipped:
        print(f"WARNING: {len(skipped)} changed class(es) are not loaded yet and still run the old code once "
              f"they load; swap again after using that feature (or restart): " + ", ".join(skipped[:8]))
    if result.returncode == 0:
        current = {c: h for c, h in current.items() if c not in skipped}  # keep them pending
        current.update({c: running[c] for c in skipped if c in running})
        stamps[plugin] = current
        e2e.WORK.mkdir(exist_ok=True)
        STAMPS.write_text(json.dumps(stamps))
    sys.exit(result.returncode)


if __name__ == "__main__":
    main()
