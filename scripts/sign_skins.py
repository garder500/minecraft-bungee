#!/usr/bin/env python3
"""Signs the ValoBlock agent skins with MineSkin and stores the result in the agent data files.

    python3 scripts/sign_skins.py            # signs every agent whose skin.value is empty
    python3 scripts/sign_skins.py --force    # re-signs every agent
    python3 scripts/sign_skins.py vesper     # only these agents

A player skin shown by the server must carry a Mojang signature, which a plugin cannot make. MineSkin uploads the PNG
to a Minecraft account and returns the signed textures property (value + signature). This script reads
resource-pack/valoblock/assets/vampyuyu/textures/entity/valoblock/skin_<agent>.png, uploads it to
POST https://api.mineskin.org/v2/generate (multipart, visibility "unlisted", variant "classic") and writes
skin.value and skin.signature into arena-plugin/src/main/resources/valoblock/agents/<agent>.json.

The API key is read from secrets/mineskin.key (git-ignored) of the main checkout, or from MINESKIN_KEY_FILE.
Stdlib only. Waits between requests as the rate-limit headers say.
"""
import json
import os
import pathlib
import sys
import time
import urllib.error
import urllib.request
import uuid

ROOT = pathlib.Path(__file__).resolve().parent.parent
AGENTS = ROOT / "arena-plugin/src/main/resources/valoblock/agents"
SKINS = ROOT / "resource-pack/valoblock/assets/vampyuyu/textures/entity/valoblock"
API = "https://api.mineskin.org/v2/generate"
USER_AGENT = "VampyuyuNetwork-SkinSigner/1.0"


def key_file():
    env = os.environ.get("MINESKIN_KEY_FILE")
    if env:
        return pathlib.Path(env)
    # Worktrees live under <main>/.claude/worktrees/<name>: the secret stays in the main checkout.
    for base in [ROOT, *ROOT.parents]:
        candidate = base / "secrets" / "mineskin.key"
        if candidate.exists():
            return candidate
    sys.exit("No MineSkin key: put it in secrets/mineskin.key or set MINESKIN_KEY_FILE")


def multipart(fields, file_field, filename, data):
    boundary = "----vampyuyu" + uuid.uuid4().hex
    out = bytearray()
    for name, value in fields.items():
        out += f"--{boundary}\r\nContent-Disposition: form-data; name=\"{name}\"\r\n\r\n{value}\r\n".encode()
    out += (f"--{boundary}\r\nContent-Disposition: form-data; name=\"{file_field}\"; filename=\"{filename}\"\r\n"
            "Content-Type: image/png\r\n\r\n").encode()
    out += data + b"\r\n" + f"--{boundary}--\r\n".encode()
    return bytes(out), f"multipart/form-data; boundary={boundary}"


def sign(key, agent, png):
    body, ctype = multipart({"variant": "classic", "visibility": "unlisted", "name": f"ValoBlock {agent}"[:48]},
                            "file", f"skin_{agent}.png", png)
    for attempt in range(5):
        req = urllib.request.Request(API, data=body, method="POST", headers={
            "Authorization": f"Bearer {key}", "User-Agent": USER_AGENT, "Content-Type": ctype,
            "Accept": "application/json"})
        try:
            with urllib.request.urlopen(req, timeout=120) as res:
                payload = json.load(res)
                wait = res.headers.get("X-RateLimit-Remaining")
                if wait == "0":
                    reset = res.headers.get("X-RateLimit-Reset")
                    if reset and reset.isdigit():
                        time.sleep(max(0, int(reset) / (1000 if int(reset) > 1e11 else 1) - time.time()) + 1)
                data = payload["skin"]["texture"]["data"]
                return data["value"], data["signature"], payload["skin"].get("uuid")
        except urllib.error.HTTPError as e:
            text = e.read().decode(errors="replace")[:400]
            if e.code == 429:
                delay = int(e.headers.get("Retry-After") or 10)
                print(f"  rate limited, waiting {delay}s")
                time.sleep(delay + 1)
                continue
            sys.exit(f"MineSkin refused {agent}: HTTP {e.code}: {text}")
    sys.exit(f"MineSkin kept rate-limiting {agent}")


def main():
    args = [a for a in sys.argv[1:] if not a.startswith("--")]
    force = "--force" in sys.argv
    key = key_file().read_text().strip()
    names = args or sorted(p.stem for p in AGENTS.glob("*.json") if p.stem != "index")
    for agent in names:
        path = AGENTS / f"{agent}.json"
        png = SKINS / f"skin_{agent}.png"
        if not path.exists() or not png.exists():
            sys.exit(f"Missing data or skin for {agent}: {path} / {png}")
        doc = json.loads(path.read_text())
        skin = doc.setdefault("skin", {})
        if skin.get("value") and not force:
            print(f"{agent}: already signed, skipped")
            continue
        print(f"{agent}: uploading {png.name}")
        value, signature, skin_uuid = sign(key, agent, png.read_bytes())
        skin["value"] = value
        skin["signature"] = signature
        skin.pop("_todo", None)
        if skin_uuid:
            skin["mineskin"] = skin_uuid
        path.write_text(json.dumps(doc, indent=2, ensure_ascii=False) + "\n")
        print(f"{agent}: signed")


if __name__ == "__main__":
    main()
