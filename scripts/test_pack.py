#!/usr/bin/env python3
"""Structural checks for resource-pack/pack that do not need a Minecraft client."""
import hashlib, json, pathlib, re, struct, sys, tempfile, unittest

ROOT = pathlib.Path(__file__).resolve().parent.parent
PACK = ROOT / "resource-pack" / "pack"
sys.path.insert(0, str(ROOT / "scripts"))
sys.path.insert(0, str(ROOT / "resource-pack"))
import build_pack  # noqa: E402
import layout  # noqa: E402
sys.path.insert(0, str(ROOT / "resource-pack" / "art"))
import hud as hud_art  # noqa: E402

def png_size(path):
    data = path.read_bytes()
    assert data[:8] == b"\x89PNG\r\n\x1a\n", f"{path} is not a PNG"
    return struct.unpack(">II", data[16:24])

class PackTest(unittest.TestCase):
    def test_mcmeta_targets_26_3(self):
        meta = json.loads((PACK / "pack.mcmeta").read_text())["pack"]
        self.assertEqual(meta["min_format"], 97)
        self.assertGreaterEqual(meta["max_format"], 97, "must cover 26.3")
        self.assertTrue(meta["description"])

    def test_font_tiles_fit_the_glyph_atlas_and_tile_each_window_exactly(self):
        font = json.loads((PACK / "assets/vampyuyu/font/menus.json").read_text())
        advances = next(p for p in font["providers"] if p["type"] == "space")["advances"]
        self.assertEqual({ord(k): v for k, v in advances.items()}, layout.ADVANCES)
        self.assertEqual(layout.ADVANCES[layout.SHIFT_BACK], -8, "title origin is x=8")
        self.assertEqual(layout.ADVANCES[layout.SHIFT_FORWARD], -(176 + 1 - 44), "dynamic text starts at x=44")
        # each bitmap glyph advances width+1; SEAM cancels it between tiles and ROW_BACK rewinds a row
        row_width = layout.COLS * (layout.TILE_W + 1) + (layout.COLS - 1) * layout.ADVANCES[layout.SEAM]
        self.assertEqual(row_width, -layout.ADVANCES[layout.ROW_BACK])
        tiles = {}
        for provider in font["providers"]:
            if provider["type"] != "bitmap":
                continue
            namespace, _, rel = provider["file"].partition(":")
            texture = PACK / "assets" / namespace / "textures" / rel
            self.assertTrue(texture.is_file(), texture)
            width, height = png_size(texture)
            # Minecraft stitches font glyphs into 256x256 pages: a larger glyph renders as missing.
            self.assertLessEqual(max(width, height), 256, texture.name)
            self.assertEqual(width, layout.TILE_W * layout.PX_PER_GUI, "tile is exactly 44 GUI px wide")
            self.assertEqual(height, provider["height"] * layout.PX_PER_GUI, "texture px match the GUI scale")
            self.assertEqual(len(provider["chars"]), 1)
            tiles[ord(provider["chars"][0])] = provider
        for name, (base, gui_h, rows) in layout.BACKGROUNDS.items():
            th = layout.tile_height(gui_h, rows)
            for r in range(rows):
                for c in range(layout.COLS):
                    provider = tiles[base + r * layout.COLS + c]
                    self.assertEqual(provider["height"], th)
                    self.assertEqual(provider["ascent"], layout.ASCENT - r * th, "row r sits r tile-heights below the top")
                    self.assertEqual(provider["file"], f"vampyuyu:font/{layout.tile_file(name, r, c)}.png")
            self.assertEqual(rows * th, gui_h)
        self.assertEqual(len(tiles), sum(r * layout.COLS for _, _, r in layout.BACKGROUNDS.values()))

    def test_screen_effect_glyph_and_shader_agree_with_the_plugin(self):
        font = json.loads((PACK / "assets/vampyuyu/font/fx.json").read_text())
        self.assertEqual(font["providers"][0]["chars"], ["\uf100"])
        self.assertTrue((PACK / "assets/vampyuyu/textures/font/fx.png").is_file())
        vsh = (PACK / "assets/minecraft/shaders/core/text.vsh").read_text()
        self.assertIn("rgb.r == 78 && rgb.g == 92", vsh)
        self.assertTrue((PACK / "assets/minecraft/shaders/core/text.fsh").is_file())
        plugin = (ROOT / "arena-plugin/src/main/java/win/vampyuyu/arena/WerewolfPresentation.java").read_text()
        self.assertIn('FX_GLYPH = "\\uF100"', plugin)
        self.assertIn("78 << 16 | 92 << 8", plugin)

    def test_icons_font_matches_layout_and_the_plugin(self):
        font = json.loads((PACK / "assets/vampyuyu/font/icons.json").read_text())
        self.assertEqual(len(font["providers"]), len(layout.ICONS))
        for index, (name, provider) in enumerate(zip(layout.ICONS, font["providers"])):
            self.assertEqual(provider["chars"], [chr(layout.ICON_BASE + index)], name)
            self.assertEqual(provider["file"], f"vampyuyu:font/icon/{name}.png")
            png = PACK / "assets/vampyuyu/textures/font/icon" / f"{name}.png"
            self.assertEqual(png_size(png), (layout.ICON_PX, layout.ICON_PX), name)
        names = (ROOT / "arena-plugin/src/main/resources/icon-names.txt").read_text().split()
        self.assertEqual(names, layout.ICONS)

    def test_every_role_is_described_and_its_words_fit_the_hud(self):
        java = (ROOT / "arena-plugin/src/main/java/win/vampyuyu/arena/WerewolfGame.java").read_text()
        enum = re.search(r"public enum Role \{(.*?)\n  \}", java, re.S).group(1)
        names = re.findall(r"^\s+([A-Z_]+),?\s*(?://.*)?$", enum, re.M)
        roles = layout.roles()
        self.assertEqual(sorted(names), sorted(roles), "one resources/roles/<ROLE>.json per Role")
        phases = set()
        for role, info in roles.items():
            for field in ("name", "team", "impact", "color", "palette", "icon", "word", "help"):
                self.assertIn(field, info, f"{role}.{field}")
            self.assertIn(info["team"], ("village", "wolves", "vampires", "cult", "solo"), role)
            self.assertIn(info["palette"], layout.HUD_PALETTES, role)
            self.assertRegex(info["color"], "^[0-9a-f]{6}$", role)
            # plabel / pteam are 68 px boxes of 1x text (6 px a letter plus the 2 px outline); the
            # phase label is 136 px of 2x text
            self.assertLessEqual((len(info["word"]) - 1) * 6 + 5 + 2, 68, f"{role} word '{info['word']}'")
            self.assertLessEqual((len(layout.team_word(info)) - 1) * 6 + 5 + 2, 68, f"{role} team word")
            for ch in info["word"] + layout.team_word(info):
                self.assertIn(ch, hud_art.FONT, f"{role}: no glyph for {ch!r}")
            if "call" in info:
                call = info["call"]
                self.assertLessEqual(((len(call["label"]) - 1) * 6 + 5) * 2 + 2, 136, f"{role} label")
                self.assertNotIn(call["phase"], phases, f"two roles own the call {call['phase']}")
                phases.add(call["phase"])
                for field in ("label", "title", "subtitle", "instruction"):
                    self.assertTrue(call.get(field), f"{role}.call.{field}")

    def test_every_role_has_an_icon_svg(self):
        for role, info in layout.roles().items():
            self.assertTrue((ROOT / "resource-pack/art/icons" / f"{info['icon']}.svg").is_file(),
                            f"{role}: icon art/icons/{info['icon']}.svg is missing")

    def test_custom_hud_sprites_font_and_plugin_table_agree(self):
        sprites = layout.hud_sprites()
        font = json.loads((PACK / "assets/vampyuyu/font/hud2.json").read_text())
        bitmaps = [p for p in font["providers"] if p["type"] == "bitmap"]
        space = next(p for p in font["providers"] if p["type"] == "space")["advances"]
        self.assertEqual(len(bitmaps), len(sprites))
        for index, ((name, w, h), provider) in enumerate(zip(sprites, bitmaps)):
            self.assertEqual(provider["chars"], [chr(layout.HUD_SPRITE_BASE + index)], name)
            png = PACK / "assets/vampyuyu/textures/font/hud" / f"{name}.png"
            self.assertEqual(png_size(png), (w, h), name)
            self.assertEqual(space[chr(layout.HUD_NEG_BASE + index)], -(w + 1), name)
        table = (ROOT / "arena-plugin/src/main/resources/hud-sprites.txt").read_text().split("\n")
        self.assertEqual([l.split()[2] for l in table if l.startswith("sprite")], [n for n, _, _ in sprites])
        self.assertEqual([l.split()[2] for l in table if l.startswith("element")], [e[0] for e in layout.HUD_ELEMENTS])
        vsh = (PACK / "assets/minecraft/shaders/core/text.vsh").read_text()
        self.assertIn(f"const int HUD_COUNT = {len(layout.HUD_ELEMENTS)};", vsh)

    def test_inventory_label_is_blank_in_every_shipped_language(self):
        langs = sorted((PACK / "assets/minecraft/lang").glob("*.json"))
        self.assertIn("en_us", [p.stem for p in langs])
        for lang in langs:
            self.assertEqual(json.loads(lang.read_text()), {"container.inventory": ""}, lang.name)

    def test_every_icon_has_texture_model_and_definition(self):
        icons = sorted(p.stem for p in (PACK / "assets/vampyuyu/textures/item/icon").glob("*.png"))
        self.assertGreaterEqual(len(icons), 20)
        for name in icons:
            definition = json.loads((PACK / f"assets/vampyuyu/items/icon/{name}.json").read_text())
            self.assertEqual(definition["model"]["model"], f"vampyuyu:item/icon/{name}")
            model = json.loads((PACK / f"assets/vampyuyu/models/item/icon/{name}.json").read_text())
            self.assertEqual(model["textures"]["layer0"], f"vampyuyu:item/icon/{name}")

    def test_role_cards_are_hud_glyphs_with_dead_versions(self):
        font = json.loads((PACK / "assets/vampyuyu/font/hud.json").read_text())
        glyphs = {ord(p["chars"][0]): p for p in font["providers"]}
        for i, role in enumerate(layout.CARDS):
            for code, suffix in [(layout.CARD_BASE + i, "")] + ([] if role == "back" else [(layout.CARD_BASE + layout.CARD_DEAD + i, "-dead")]):
                provider = glyphs.pop(code)
                self.assertEqual(provider["file"], f"vampyuyu:font/card/{role}{suffix}.png")
                self.assertEqual((provider["height"], provider["ascent"]), (layout.CARD_HEIGHT, layout.CARD_ASCENT))
                width, height = png_size(PACK / "assets/vampyuyu/textures/font/card" / f"{role}{suffix}.png")
                self.assertEqual(width, height)
                self.assertLessEqual(width, 256)
            self.assertTrue((PACK / f"assets/vampyuyu/items/icon/card-{role}.json").is_file(), role)
        self.assertEqual(glyphs, {})

    def test_plugin_uses_the_pack_codepoints_and_icons(self):
        java = ROOT / "arena-plugin/src/main/java/win/vampyuyu/arena"
        menus = (java / "WerewolfMenus.java").read_text()
        role_menus = re.findall(r'"(menu_\w+)", \'\\u([0-9A-F]{4})\'', menus)
        self.assertEqual(7, len(role_menus))
        for name, code in role_menus:
            self.assertEqual(int(code, 16), layout.BACKGROUNDS[name][0], name)
        village = re.search(r"VILLAGE = '\\u([0-9A-F]{4})'", menus)
        self.assertEqual(int(village.group(1), 16), layout.BACKGROUNDS["menu_village"][0])
        hud = (java / "WerewolfHud.java").read_text()
        self.assertIn("List.of(%s)" % ", ".join(f'"{c}"' for c in layout.CARDS), " ".join(hud.split()).replace("( ", "("))
        icons = {p.stem for p in (PACK / "assets/vampyuyu/items/icon").glob("*.json")}
        source = menus + (java / "CompassGUI.java").read_text()
        for icon in set(re.findall(r'"((?:potion-|mark-|card-)[a-z-]*|target|heart|shield|moon|lock|timer|skip)"', source)):
            if icon.endswith("-"):
                continue  # a prefix completed at run time (card- + role)
            self.assertIn(icon, icons)

    def test_invisible_item_model_chain(self):
        item = json.loads((PACK / "assets/vampyuyu/items/blank.json").read_text())
        self.assertEqual(item["model"]["model"], "vampyuyu:item/blank")
        model = json.loads((PACK / "assets/vampyuyu/models/item/blank.json").read_text())
        self.assertEqual(model["textures"]["layer0"], "vampyuyu:item/blank")
        self.assertTrue((PACK / "assets/vampyuyu/textures/item/blank.png").is_file())

    def test_every_werewolf_sound_the_plugin_plays_is_in_the_pack(self):
        sounds = json.loads((PACK / "assets/vampyuyu/sounds.json").read_text())
        subtitles = json.loads((PACK / "assets/vampyuyu/lang/en_us.json").read_text())
        java = ROOT / "arena-plugin/src/main/java/win/vampyuyu/arena"
        ambience = (java / "WerewolfAmbience.java").read_text()
        presentation = (java / "WerewolfPresentation.java").read_text()
        clicks = presentation[presentation.index("static String actionSound"):]
        clicks = clicks[: clicks.index("\n  }\n")]
        played = set(re.findall(r'cue\(\s*\w+,\s*"(\w+)"', ambience)) | set(re.findall(r'"(\w+)"\s*[:;]', clicks))
        played |= {"music"}
        self.assertGreaterEqual(len(played), 13, played)
        for name in played:
            entry = sounds.get(f"werewolf.{name}")
            self.assertIsNotNone(entry, name)
            self.assertIn(entry["subtitle"], subtitles)
            for sound in entry["sounds"]:
                path = PACK / "assets/vampyuyu/sounds" / (sound["name"].split(":", 1)[1] + ".ogg")
                self.assertTrue(path.is_file(), path)
                self.assertEqual(path.read_bytes()[:4], b"OggS", path)
        self.assertIn('Key.key("vampyuyu", "werewolf.music")', ambience)
        self.assertTrue(sounds["werewolf.music"]["sounds"][0]["stream"])

    def test_build_is_reproducible_and_complete(self):
        with tempfile.TemporaryDirectory() as a, tempfile.TemporaryDirectory() as b:
            first, digest = build_pack.build(a)
            second, again = build_pack.build(b)
            self.assertEqual(digest, again)
            self.assertEqual(hashlib.sha1(first.read_bytes()).hexdigest(), digest)
            self.assertEqual(first.read_bytes(), second.read_bytes())
            self.assertRegex((pathlib.Path(a) / "vampyuyu-menus.sha1").read_text().strip(), r"^[0-9a-f]{40}$")
            self.assertLess(first.stat().st_size, 8 << 20, "keep the optional pack small")

if __name__ == "__main__":
    unittest.main()
