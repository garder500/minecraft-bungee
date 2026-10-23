#!/usr/bin/env python3
"""Compiles the werewolf core shaders (SPIR-V, Vulkan semantics like the 26.x client) for every
pipeline variant of vanilla's text shader, with vanilla's own includes.

    GLSLANG=/path/to/glslang MC_SHADER_INCLUDES=/path/to/assets/minecraft/shaders/include \
        python3 scripts/test_shaders.py

Skipped when either is missing (Mojang's include files are not stored in this repository: extract
assets/minecraft/shaders/include from the 26.3 client jar).
"""
import os, pathlib, re, shutil, subprocess, tempfile, unittest

ROOT = pathlib.Path(__file__).resolve().parent.parent
CORE = pathlib.Path(os.environ.get("SHADER_CORE") or ROOT / "resource-pack/pack/assets/minecraft/shaders/core")
GLSLANG = os.environ.get("GLSLANG") or shutil.which("glslang") or shutil.which("glslangValidator")
INCLUDES = os.environ.get("MC_SHADER_INCLUDES")
OIT = ["OIT", "OIT_COEFF_COUNT=8", "OIT_COEFF_ATTACHMENT_COUNT=2", "OIT_WAVELET_RANK=3"]
VARIANTS = [[], ["IS_GUI"], ["IS_SEE_THROUGH"], ["IS_GRAYSCALE"], ["IS_GUI", "IS_GRAYSCALE"],
            OIT + ["OIT_ACCUMULATE"], OIT + ["OIT_ACCUMULATE", "OIT_ADDITIVE"],
            OIT + ["OIT_TRANSMITTANCE", "OIT_ALPHA_ONLY"],
            OIT + ["OIT_TRANSMITTANCE", "OIT_ALPHA_ONLY", "IS_GRAYSCALE"],
            OIT + ["OIT_DEPTH_BOUNDS", "OIT_ALPHA_ONLY"]]

def inline(text, seen=None):
    seen = set() if seen is None else seen
    def sub(m):
        name = m.group(1).split(":")[-1]
        if name in seen and False:
            return ""
        return inline((pathlib.Path(INCLUDES) / name).read_text(), seen)
    return re.sub(r"^[ \t]*#[ \t]*(?:include|moj_import) <([^>]+)>", sub, text, flags=re.M)

@unittest.skipUnless(GLSLANG and INCLUDES, "needs GLSLANG and MC_SHADER_INCLUDES")
class ShaderCompile(unittest.TestCase):
    def compile(self, name, stage, defines):
        source = inline((CORE / name).read_text()).replace("#version 330", "#version 450", 1)
        with tempfile.TemporaryDirectory() as tmp:
            path = pathlib.Path(tmp) / f"t.{stage}"
            path.write_text(source)
            cmd = [GLSLANG, "-V", "--amb", "--aml", "-S", stage, str(path), "-o", os.devnull]
            cmd += [f"-D{d}" for d in defines]
            done = subprocess.run(cmd, capture_output=True, text=True)
        self.assertEqual(done.returncode, 0, f"{name} {defines}\n{done.stdout}{done.stderr}")

    def test_every_text_pipeline_compiles(self):
        for defines in VARIANTS:
            self.compile("text.vsh", "vert", defines)
            self.compile("text.fsh", "frag", defines)

if __name__ == "__main__":
    unittest.main()
