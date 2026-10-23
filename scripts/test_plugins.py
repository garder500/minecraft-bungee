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


class PluginsScriptTest(unittest.TestCase):
    def setUp(self):
        self.tmp = pathlib.Path(tempfile.mkdtemp())
        self.state = self.tmp / "state"
        stub = self.tmp / "bin"
        stub.mkdir()
        (stub / "docker").write_text("#!/bin/sh\nexit 0\n")  # nothing is running
        (stub / "docker").chmod(0o755)
        self.env = dict(os.environ, PLUGIN_STATE_DIR=str(self.state), PATH=f"{stub}:{os.environ['PATH']}")
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

    def test_unknown_plugin(self):
        r = self.sh("install", "nope", str(self.jar), ok=False)
        self.assertIn("Unknown plugin", r.stderr)


if __name__ == "__main__":
    unittest.main()
