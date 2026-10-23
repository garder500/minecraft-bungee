#!/usr/bin/env python3
"""Plugin hotfix checks that need neither Docker nor a Minecraft server.

* infra/start.sh: overrides replace image jars, and a cleared override never keeps running even though
  the image skips files that are newer in /data/plugins.
* scripts/plugins.sh: install/status/clear against a temporary state directory, with a stub `docker`
  that reports every server as stopped.
"""
import os, pathlib, subprocess, tempfile, time, unittest

ROOT = pathlib.Path(__file__).resolve().parent.parent
START = (ROOT / "infra" / "start.sh").read_text()


def override_block():
    """The plugin section of start.sh, from the hotfix comment up to the image entrypoint."""
    lines = START.splitlines()
    begin = next(i for i, l in enumerate(lines) if "Plugin hotfixes" in l)
    end = next(i for i, l in enumerate(lines) if "exec /image/scripts/start" in l)
    return "\n".join(lines[begin:end])


class StartScriptTest(unittest.TestCase):
    def setUp(self):
        self.tmp = pathlib.Path(tempfile.mkdtemp())
        self.image, self.data, self.overrides = (self.tmp / n for n in ("plugins", "data-plugins", "overrides"))
        for d in (self.image, self.data, self.overrides):
            d.mkdir()
        (self.image / "nexus-arena-engine.jar").write_text("image")

    def run_block(self):
        env = dict(os.environ, IMAGE_PLUGINS=str(self.image), DATA_PLUGINS=str(self.data),
                   PLUGIN_OVERRIDES=str(self.overrides))
        return subprocess.run(["sh", "-c", "set -eu\n" + override_block()], env=env, check=True,
                              capture_output=True, text=True)

    def test_override_replaces_the_image_jar_and_is_logged(self):
        (self.overrides / "nexus-arena-engine.jar").write_text("hotfix")
        result = self.run_block()
        self.assertEqual((self.image / "nexus-arena-engine.jar").read_text(), "hotfix")
        self.assertIn("PLUGIN OVERRIDE ACTIVE: nexus-arena-engine.jar", result.stderr)

    def test_stale_newer_copy_in_data_is_removed_so_the_image_wins(self):
        stale = self.data / "nexus-arena-engine.jar"
        stale.write_text("old hotfix")
        future = time.time() + 3600
        os.utime(stale, (future, future))
        (self.data / "SomeoneElses.jar").write_text("keep")
        self.run_block()
        self.assertFalse(stale.exists(), "a newer copy would be skipped by the image sync")
        self.assertTrue((self.data / "SomeoneElses.jar").exists(), "only jars shipped in /plugins are touched")

    def test_without_overrides_nothing_is_logged(self):
        self.assertNotIn("OVERRIDE", self.run_block().stderr)

    def test_overrides_apply_after_the_claims_copy(self):
        self.assertLess(START.index("cp /opt/claims/SimpleClaimSystem.jar"), START.index("Plugin hotfixes"),
                        "the claims copy would otherwise overwrite a claims override")


DOCKER_STUB = """#!/bin/sh
# docker compose -f F ps ... | exec -T S rcon-cli ... | restart ...
log="$STUB_LOG"; echo "$*" >> "$log"
case "$*" in
 *" ps "*) [ -n "$RUNNING" ] && echo "$RUNNING" ;;
 *" exec "*"arena list"*) [ "$ARENAS" = FAIL ] && exit 1; echo "$ARENAS" ;;
esac
exit 0
"""


class PluginsScriptTest(unittest.TestCase):
    def setUp(self):
        self.tmp = pathlib.Path(tempfile.mkdtemp())
        self.state = self.tmp / "state"
        stub = self.tmp / "bin"
        stub.mkdir()
        (stub / "docker").write_text(DOCKER_STUB)
        (stub / "docker").chmod(0o755)
        (stub / "sleep").write_text("#!/bin/sh\nexit 0\n")
        (stub / "sleep").chmod(0o755)
        (stub / "ssh").write_text('#!/bin/sh\nfor last; do :; done; printf %s "$last" > "$SSH_LOG"\n')
        (stub / "ssh").chmod(0o755)
        self.log = self.tmp / "docker.log"
        self.env = dict(os.environ, PLUGIN_STATE_DIR=str(self.state), PATH=f"{stub}:{os.environ['PATH']}",
                        STUB_LOG=str(self.log), SSH_LOG=str(self.tmp / "ssh.log"), RUNNING="", ARENAS="")
        self.jar = self.tmp / "built.jar"
        self.jar.write_text("jar")

    def sh(self, *args, ok=True):
        r = subprocess.run([str(ROOT / "scripts" / "plugins.sh"), *args], env=self.env, capture_output=True, text=True)
        if ok:
            self.assertEqual(r.returncode, 0, r.stderr)
        return r

    def test_install_targets_every_server_carrying_the_plugin(self):
        self.sh("install", "arena", str(self.jar))
        for server in ("lobby", "creative", "survival"):
            self.assertTrue((self.state / server / "plugin-overrides/nexus-arena-engine.jar").is_file())
        self.sh("install", "claims", str(self.jar))
        self.assertTrue((self.state / "creative/plugin-overrides/SimpleClaimSystem.jar").is_file())
        self.assertFalse((self.state / "lobby/plugin-overrides/SimpleClaimSystem.jar").exists())

    def test_refuses_a_server_that_does_not_carry_the_plugin(self):
        r = self.sh("install", "lobby", str(self.jar), "creative", ok=False)
        self.assertNotEqual(r.returncode, 0)
        self.assertIn("does not run on creative", r.stderr)

    def test_status_then_clear(self):
        self.assertIn("No plugin overrides", self.sh("status").stdout)
        self.sh("install", "network", str(self.jar), "survival")
        self.assertIn("network-display.jar", self.sh("status").stdout)
        self.sh("clear")
        self.assertIn("No plugin overrides", self.sh("status").stdout)

    def test_running_server_is_warned_and_restarted_when_arenas_wait(self):
        self.env.update(RUNNING="creative", ARENAS="Arenas: moonlit (WAITING)")
        self.sh("install", "claims", str(self.jar))
        log = self.log.read_text()
        self.assertIn("rcon-cli say", log)
        self.assertIn("restart --timeout 75 creative", log)

    def test_live_arena_game_blocks_the_restart_unless_forced(self):
        self.env.update(RUNNING="creative", ARENAS="Arenas: moonlit (NIGHT), hub (WAITING)")
        r = self.sh("install", "claims", str(self.jar), ok=False)
        self.assertIn("moonlit (NIGHT)", r.stderr)
        self.assertFalse((self.state / "creative/plugin-overrides/SimpleClaimSystem.jar").exists(),
                         "nothing is installed when refused")
        self.env["FORCE"] = "1"
        self.sh("install", "claims", str(self.jar))

    def test_unreachable_arena_listing_counts_as_busy(self):
        self.env.update(RUNNING="creative", ARENAS="FAIL")
        r = self.sh("install", "claims", str(self.jar), ok=False)
        self.assertIn("did not answer", r.stderr)

    def test_remote_clear_without_servers_passes_no_empty_argument(self):
        remote = self.tmp / "remote.env"
        remote.write_text('PROD_HOST=user@example.test\nPROD_PATH="/srv/minecraft bungee"\n')
        self.env["PLUGINS_REMOTE_ENV"] = str(remote)
        self.sh("remote", "clear")
        command = (self.tmp / "ssh.log").read_text()
        self.assertTrue(command.rstrip().endswith("scripts/plugins.sh clear"), command)
        self.assertIn("cd /srv/minecraft\\ bungee", command)
        self.assertIn("COMPOSE=compose.prod.yaml", command)

    def test_unknown_plugin(self):
        r = self.sh("install", "nope", str(self.jar), ok=False)
        self.assertIn("Unknown plugin", r.stderr)


if __name__ == "__main__":
    unittest.main()
