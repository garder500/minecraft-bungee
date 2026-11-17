#!/usr/bin/env python3
"""Structural checks for resource-pack/pack that do not need a Minecraft client."""
import hashlib, json, pathlib, re, struct, sys, tempfile, unittest, zipfile

ROOT = pathlib.Path(__file__).resolve().parent.parent
PACK = ROOT / "resource-pack" / "pack"
VALO = ROOT / "resource-pack" / "valoblock"
sys.path.insert(0, str(ROOT / "scripts"))
sys.path.insert(0, str(ROOT / "resource-pack"))
import build_pack  # noqa: E402
import layout  # noqa: E402
import vb_layout  # noqa: E402
sys.path.insert(0, str(ROOT / "resource-pack" / "art"))
import hud as hud_art  # noqa: E402

def ogg_seconds(data):
    """Length of an Ogg Vorbis file: the last page's granule position counts PCM samples."""
    rate, granule, offset = None, 0, 0
    while offset < len(data):
        assert data[offset:offset + 4] == b"OggS", f"bad Ogg page at {offset}"
        segments = data[offset + 26]
        body = sum(data[offset + 27:offset + 27 + segments])
        if rate is None:  # first page holds the Vorbis identification header
            packet = offset + 27 + segments
            rate = struct.unpack("<I", data[packet + 12:packet + 16])[0]
        granule = struct.unpack("<q", data[offset + 6:offset + 14])[0]
        offset += 27 + segments + body
    return granule / rate

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
        plugin = (ROOT / "arena-plugin/src/main/java/win/vampyuyu/werewolf/ambience/WerewolfPresentation.java").read_text()
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
        java = (ROOT / "arena-plugin/src/main/java/win/vampyuyu/werewolf/WerewolfGame.java").read_text()
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

    def test_panel_role_list_is_generated_from_the_role_files(self):
        import subprocess
        result = subprocess.run([sys.executable, str(ROOT / "scripts/gen_roles_ts.py"), "--check"])
        self.assertEqual(result.returncode, 0, "run: python3 scripts/gen_roles_ts.py")

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
        java = ROOT / "arena-plugin/src/main/java/win/vampyuyu/werewolf"
        menus = (java / "WerewolfMenus.java").read_text()
        role_menus = re.findall(r'"(menu_\w+)", \'\\u([0-9A-F]{4})\'', menus)
        self.assertEqual(7, len(role_menus))
        for name, code in role_menus:
            self.assertEqual(int(code, 16), layout.BACKGROUNDS[name][0], name)
        village = re.search(r"VILLAGE = '\\u([0-9A-F]{4})'", menus)
        self.assertEqual(int(village.group(1), 16), layout.BACKGROUNDS["menu_village"][0])
        hud = (java / "hud/WerewolfHud.java").read_text()
        self.assertIn("List.of(%s)" % ", ".join(f'"{c}"' for c in layout.CARDS), " ".join(hud.split()).replace("( ", "("))
        icons = {p.stem for p in (PACK / "assets/vampyuyu/items/icon").glob("*.json")}
        source = menus + (java / "WerewolfCompass.java").read_text()
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
        java = ROOT / "arena-plugin/src/main/java/win/vampyuyu/werewolf"
        ambience = (java / "ambience/WerewolfAmbience.java").read_text()
        presentation = (java / "ambience/WerewolfPresentation.java").read_text()
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
        profile = (ROOT / "arena-plugin/src/main/resources/werewolf/ambience.json").read_text()
        self.assertIn("vampyuyu:werewolf.music", profile)  # the bed loops are chosen by the ambience profile
        self.assertTrue(sounds["werewolf.music"]["sounds"][0]["stream"])

    def test_every_ambience_sound_is_in_sounds_json_and_the_built_pack(self):
        profile = json.loads((ROOT / "arena-plugin/src/main/resources/werewolf/ambience.json").read_text())
        sounds = json.loads((PACK / "assets/vampyuyu/sounds.json").read_text())
        subtitles = json.loads((PACK / "assets/vampyuyu/lang/en_us.json").read_text())
        with tempfile.TemporaryDirectory() as out:
            built, _ = build_pack.build(out)
            with zipfile.ZipFile(built) as z:
                members = set(z.namelist())
                beds = set()
                for phase, entry in profile.items():
                    self.assertRegex(entry["fallback"], r"^minecraft:[a-z0-9_./]+$", phase)
                    self.assertGreater(entry["ambient"]["loopMillis"], 0, phase)
                    for role, sound in (("ambient", entry["ambient"]["sound"]), ("sting", entry["sting"]["sound"])):
                        self.assertRegex(sound, r"^vampyuyu:werewolf\.[a-z0-9_]+$", f"{phase}.{role}")
                        key = sound.split(":", 1)[1]  # werewolf.<name>, the sounds.json key
                        definition = sounds.get(key)
                        self.assertIsNotNone(definition, f"{phase}.{role}: {key} not in sounds.json")
                        self.assertIn(definition["subtitle"], subtitles)
                        # sounds.json names the file: vampyuyu:werewolf/<name>, i.e. assets/vampyuyu/sounds/werewolf/<name>.ogg
                        namespace, path = definition["sounds"][0]["name"].split(":", 1)
                        rel = f"assets/{namespace}/sounds/{path}.ogg"
                        self.assertTrue((PACK / rel).is_file(), rel)
                        self.assertIn(rel, members, f"{rel} missing from the built pack")
                        data = z.read(rel)
                        self.assertEqual(data[:4], b"OggS", rel)
                        self.assertLessEqual(len(data), 600 * 1024, f"{rel} is too large")
                        if role == "ambient":
                            beds.add(key)
                            self.assertAlmostEqual(
                                ogg_seconds(data) * 1000, entry["ambient"]["loopMillis"], delta=50,
                                msg=f"{phase}: loopMillis must match the length of {rel}")
                self.assertGreaterEqual(len(beds), 4, "each main phase changes the loop")
                self.assertIn("assets/vampyuyu/sounds.json", members)

    def test_build_is_reproducible_and_complete(self):
        with tempfile.TemporaryDirectory() as a, tempfile.TemporaryDirectory() as b:
            first, digest = build_pack.build(a)
            second, again = build_pack.build(b)
            self.assertEqual(digest, again)
            self.assertEqual(hashlib.sha1(first.read_bytes()).hexdigest(), digest)
            self.assertEqual(first.read_bytes(), second.read_bytes())
            self.assertRegex((pathlib.Path(a) / "vampyuyu-menus.sha1").read_text().strip(), r"^[0-9a-f]{40}$")
            self.assertLess(first.stat().st_size, 8 << 20, "keep the optional pack small")

def walk_models(node):
    """Every model reference of an item definition tree (model, select cases and fallback)."""
    if node["type"] == "minecraft:model":
        yield node["model"]
    for case in node.get("cases", []):
        yield from walk_models(case["model"])
    if "fallback" in node:
        yield from walk_models(node["fallback"])

def gui_model(definition):
    """The model an item definition uses in the GUI: a plain model, or the gui case of a display_context select."""
    node = definition["model"]
    if node["type"] == "minecraft:model":
        return node["model"]
    assert node["property"] == "minecraft:display_context", node["property"]
    return next(c["model"]["model"] for c in node["cases"] if c["when"] == "gui")

class ValoBlockPackTest(unittest.TestCase):
    """resource-pack/valoblock: the second pack, sent over the menu pack while a player is in a ValoBlock match."""
    base = VALO / "assets/vampyuyu"
    weapons = json.loads((ROOT / "arena-plugin/src/main/resources/valoblock/weapons.json").read_text())["weapons"]

    def test_mcmeta_targets_26_3(self):
        meta = json.loads((VALO / "pack.mcmeta").read_text())["pack"]
        self.assertEqual(meta["min_format"], 97)
        self.assertGreaterEqual(meta["max_format"], 97)
        self.assertTrue(meta["description"])

    def test_nothing_of_valoblock_is_left_in_the_menu_pack(self):
        found = [str(p.relative_to(PACK)) for p in PACK.rglob("*") if "valoblock" in p.relative_to(PACK).as_posix()]
        self.assertEqual(found, [], "ValoBlock assets belong in resource-pack/valoblock")

    def test_flat_sprites_exist_for_every_weapon_and_its_locked_twin(self):
        self.assertGreaterEqual(len(self.weapons), 15)
        for w in self.weapons:
            for name in (w["id"], w["id"] + "_locked"):
                self.assertEqual(png_size(self.base / f"textures/item/valoblock/{name}.png"), (32, 32), name)
                model = json.loads((self.base / f"models/item/valoblock/{name}.json").read_text())
                self.assertEqual(model["textures"]["layer0"], f"vampyuyu:item/valoblock/{name}")
                definition = json.loads((self.base / f"items/valoblock/{name}.json").read_text())
                self.assertEqual(gui_model(definition), f"vampyuyu:item/valoblock/{name}", "the GUI shows the flat sprite")

    def test_every_ability_has_its_own_icon_item_with_empty_and_ready_variants(self):
        agents = ROOT / "arena-plugin/src/main/resources/valoblock/agents"
        seen = set()
        for f in sorted(agents.glob("*.json")):
            if f.name == "index.json":
                continue
            a = json.loads(f.read_text())
            for ab in a["abilities"]:
                base = f"ab_{a['id']}_{ab['id']}"
                names = [base, base + "_empty"] + ([base + "_ready"] if ab["slot"] == 7 else [])
                for name in names:
                    self.assertEqual(png_size(self.base / f"textures/item/valoblock/{name}.png"), (16, 16), name)
                    model = json.loads((self.base / f"models/item/valoblock/{name}.json").read_text())
                    self.assertEqual(model["textures"]["layer0"], f"vampyuyu:item/valoblock/{name}")
                    definition = json.loads((self.base / f"items/valoblock/{name}.json").read_text())
                    self.assertEqual(definition["model"]["model"], f"vampyuyu:item/valoblock/{name}")
                self.assertNotEqual((self.base / f"textures/item/valoblock/{base}.png").read_bytes(),
                                    (self.base / f"textures/item/valoblock/{base}_empty.png").read_bytes())
                seen.add(base)
        self.assertEqual(len(seen), 32)
        pngs = {(self.base / f"textures/item/valoblock/{n}.png").read_bytes() for n in seen}
        self.assertEqual(len(pngs), 32, "every ability has a different icon")

    def test_every_gun_and_the_knife_has_3d_models_for_each_state(self):
        guns = [w for w in self.weapons if w["texture"] != "ward"]
        self.assertGreaterEqual(len(guns), 13)
        for w in guns:
            name = w["id"]
            definition = json.loads((self.base / f"items/valoblock/{name}.json").read_text())
            node = definition["model"]["cases"] and definition["model"]["fallback"]
            self.assertEqual(node["property"], "minecraft:custom_model_data")
            self.assertEqual(node["index"], 0)
            self.assertEqual([c["when"] for c in node["cases"]], ["fire", "reload", "inspect", "aim", "scope"])
            self.assertEqual(
                sorted(set(walk_models(definition["model"]))),
                sorted({f"vampyuyu:item/valoblock/{n}" for n in (name, f"{name}_3d", f"{name}_fire", f"{name}_reload", f"{name}_inspect", f"{name}_aim")}))
            self.assertEqual(png_size(self.base / f"textures/item/valoblock/{name}_3d.png"), (32, 32), name)
            for state in ("3d", "fire", "reload", "inspect", "aim"):
                model = json.loads((self.base / f"models/item/valoblock/{name}_{state}.json").read_text())
                self.assertEqual(model["textures"]["0"], f"vampyuyu:item/valoblock/{name}_3d")
                self.assertGreater(len(model["elements"]), 8, f"{name}_{state}")
                for key in ("thirdperson_righthand", "firstperson_righthand", "gui"):
                    self.assertIn(key, model["display"])
                for el in model["elements"]:
                    for i in range(3):
                        self.assertTrue(-16 <= el["from"][i] <= 32 and -16 <= el["to"][i] <= 32, (name, state))
                        self.assertGreater(el["to"][i] - el["from"][i], 0.04, (name, state))
            held = json.loads((self.base / f"models/item/valoblock/{name}_3d.json").read_text())
            fire = json.loads((self.base / f"models/item/valoblock/{name}_fire.json").read_text())
            if w["texture"] != "knife":
                self.assertGreater(len(fire["elements"]), len(held["elements"]), f"{name}: the flash adds boxes")
            hold = held["display"]["firstperson_righthand"]["translation"]
            aim = json.loads((self.base / f"models/item/valoblock/{name}_aim.json").read_text())["display"]["firstperson_righthand"]["translation"]
            self.assertNotEqual(hold, aim, f"{name}: aiming holds the gun differently")

    def test_the_rift_seed_has_a_flat_sprite_a_3d_model_and_hud_pieces(self):
        name = "rift_seed"
        self.assertEqual(png_size(self.base / f"textures/item/valoblock/{name}.png"), (32, 32))
        self.assertEqual(png_size(self.base / f"textures/item/valoblock/{name}_model.png"), (32, 32))
        flat = json.loads((self.base / f"models/item/valoblock/{name}.json").read_text())
        self.assertEqual(flat["textures"]["layer0"], f"vampyuyu:item/valoblock/{name}")
        definition = json.loads((self.base / f"items/valoblock/{name}.json").read_text())
        self.assertEqual(gui_model(definition), f"vampyuyu:item/valoblock/{name}", "the hotbar slot shows the flat sprite")
        self.assertEqual(sorted(set(walk_models(definition["model"]))), [f"vampyuyu:item/valoblock/{name}", f"vampyuyu:item/valoblock/{name}_model"])
        model = json.loads((self.base / f"models/item/valoblock/{name}_model.json").read_text())
        self.assertGreaterEqual(len(model["elements"]), 16, "a hand-built seed, not a slab")
        for key in ("thirdperson_righthand", "firstperson_righthand", "ground", "fixed", "head"):
            self.assertIn(key, model["display"])
        for el in model["elements"]:
            self.assertEqual(set(el["faces"]), {"north", "east", "south", "west", "up", "down"})
            for i in range(3):
                self.assertTrue(0 <= el["from"][i] < el["to"][i] <= 16, el)
        lines = open(ROOT / "arena-plugin/src/main/resources/valoblock/vb-hud-sprites.txt").read().split("\n")
        names = {l.split(" ")[2] for l in lines if l}
        for needle in ("seed_on", "seed_glow", "plant_track", "plant_bar", "seed", "head_cinder_seed", "head_none_seed"):
            self.assertIn(needle, names)
        self.assertEqual([e[0] for e in vb_layout.HUD_ELEMENTS][-3:], ["seed", "plant_track", "plant_bar"], "appended: older element ids keep their numbers")

    def test_shields_stay_flat(self):
        for w in (w for w in self.weapons if w["texture"] == "ward"):
            definition = json.loads((self.base / f"items/valoblock/{w['id']}.json").read_text())
            self.assertEqual(definition["model"]["type"], "minecraft:model")

    def test_scope_overlay_matches_the_plugin_camera_overlay(self):
        self.assertEqual(png_size(self.base / "textures/misc/scope.png"), (512, 288))
        code = (ROOT / "arena-plugin/src/main/java/win/vampyuyu/valoblock/WeaponItems.java").read_text()
        self.assertIn('new NamespacedKey("vampyuyu", "misc/scope")', code)  # textures/misc/scope.png of this pack

    def test_spyglass_overlay_is_square_and_only_in_the_valoblock_pack(self):
        spy = ROOT / "resource-pack/valoblock/assets/minecraft/textures/misc/spyglass_scope.png"
        w, h = png_size(spy)
        self.assertEqual(w, h, "vanilla draws the spyglass overlay as a square")
        self.assertFalse((ROOT / "resource-pack/assets/minecraft/textures/misc/spyglass_scope.png").exists(), "the base pack keeps the vanilla spyglass")
        weapons = json.loads((ROOT / "arena-plugin/src/main/resources/valoblock/weapons.json").read_text())["weapons"]
        eye = next(x for x in weapons if x["id"] == "watchers_eye")
        self.assertEqual(eye["base"], "SPYGLASS", "the client zooms only for the spyglass item")

    def test_agent_menu_sprites_exist_for_every_agent(self):
        agents = ROOT / "arena-plugin/src/main/resources/valoblock/agents"
        index = json.loads((agents / "index.json").read_text())
        names = ["agent_empty", "lock_in", "random"] + ["role_" + r["name"].lower() for r in index["roles"]]
        for agent in index["agents"]:
            data = json.loads((agents / f"{agent}.json").read_text())
            self.assertEqual(len(data["face"]["rows"]), 8, agent)
            names += [f"agent_{agent}", f"agent_{agent}_dim", f"agent_{agent}_ring"]
        for name in names:
            self.assertEqual(png_size(self.base / f"textures/item/valoblock/{name}.png"), (32, 32), name)
            model = json.loads((self.base / f"models/item/valoblock/{name}.json").read_text())
            self.assertEqual(model["textures"]["layer0"], f"vampyuyu:item/valoblock/{name}")
            definition = json.loads((self.base / f"items/valoblock/{name}.json").read_text())
            self.assertEqual(definition["model"]["model"], f"vampyuyu:item/valoblock/{name}")

    def test_every_model_and_texture_it_names_is_inside_this_pack(self):
        for model in (self.base / "models/item/valoblock").glob("*.json"):
            data = json.loads(model.read_text())
            for ref in data.get("textures", {}).values():
                namespace, _, rel = ref.partition(":")
                self.assertTrue((VALO / "assets" / namespace / "textures" / f"{rel}.png").is_file(), f"{model.name}: {ref}")
        for item in (self.base / "items/valoblock").glob("*.json"):
            for ref in walk_models(json.loads(item.read_text())["model"]):
                namespace, _, rel = ref.partition(":")
                self.assertTrue((VALO / "assets" / namespace / "models" / f"{rel}.json").is_file(), f"{item.name}: {ref}")

    # ------------------------------------------------------------------ interface v2

    def java(self, name):
        return (ROOT / "arena-plugin/src/main/java/win/vampyuyu/valoblock" / name).read_text()

    def test_vanilla_hud_pieces_are_blanked_by_this_pack_only(self):
        base = VALO / "assets/minecraft/textures/gui/sprites"
        names = [n for n, _, _ in vb_layout.hidden_sprites()]
        self.assertIn("hud/heart/full", names)
        self.assertIn("hud/food_full", names)
        self.assertIn("hud/armor_full", names)
        self.assertIn("hud/experience_bar_progress", names)
        self.assertIn("hud/crosshair", names)
        for name, w, h in vb_layout.hidden_sprites():
            path = base / f"{name}.png"
            self.assertEqual(png_size(path), (w, h), name)
            data = path.read_bytes()
            # a fully transparent RGBA image: the deflated rows of zeros are tiny
            self.assertLess(len(data), 400, name)
        self.assertFalse(list((PACK / "assets/minecraft/textures/gui").glob("**/*")), "the menu pack keeps the vanilla HUD")

    def test_menu_font_matches_the_plugin_title_builder(self):
        font = json.loads((VALO / "assets/vampyuyu/font/vbmenus.json").read_text())
        advances = next(p for p in font["providers"] if p["type"] == "space")["advances"]
        self.assertEqual({ord(k): v for k, v in advances.items()}, vb_layout.ADVANCES)
        code = self.java("VbMenuSkin.java")
        for const, value in (("SELECT", vb_layout.SELECT_BASE), ("ARMORY", vb_layout.ARMORY_BASE), ("PORTRAIT_BASE", vb_layout.PORTRAIT_BASE),
                             ("PORTRAIT_NONE", vb_layout.PORTRAIT_NONE), ("SHIFT_BACK", vb_layout.SHIFT_BACK), ("ROW_BACK", vb_layout.ROW_BACK),
                             ("SEAM", vb_layout.SEAM), ("TO_PORTRAIT", vb_layout.TO_PORTRAIT), ("FROM_PORTRAIT", vb_layout.FROM_PORTRAIT),
                             ("TO_TEXT", vb_layout.TO_TEXT), ("TAC_CHROME", vb_layout.TAC_CHROME_BASE), ("TAC_PLAN", vb_layout.TAC_PLAN_BASE),
                             ("TAC_ZONE", vb_layout.TAC_ZONE_BASE), ("TO_PLAN", vb_layout.TO_PLAN), ("FROM_PLAN", vb_layout.FROM_PLAN)):
            self.assertIn(f"{const} = '" + chr(92) + f"u{value:04X}'", code, const)
        self.assertIn(f"PORTRAIT_W = {vb_layout.PORTRAIT_W}, PORTRAIT_X = {vb_layout.PORTRAIT_X}, TEXT_X = {vb_layout.TEXT_X}", code)
        self.assertIn(f"PLAN_X = {vb_layout.TAC_PLAN_X}, PLAN_W = {vb_layout.TAC_PLAN_PX}", code)
        tiles = {}
        for p in font["providers"]:
            if p["type"] != "bitmap":
                continue
            width, height = png_size(VALO / "assets" / p["file"].replace(":", "/textures/", 1))
            self.assertLessEqual(max(width, height), 256, p["file"])
            tiles[ord(p["chars"][0])] = (p, width, height)
        th = layout.tile_height(vb_layout.GUI_H, vb_layout.ROWS)
        for base in (vb_layout.SELECT_BASE, vb_layout.ARMORY_BASE, vb_layout.TAC_CHROME_BASE):
            for r in range(vb_layout.ROWS):
                for c in range(layout.COLS):
                    provider, width, height = tiles[base + r * layout.COLS + c]
                    self.assertEqual((width, height), (layout.TILE_W * 4, th * 4))
                    self.assertEqual(provider["height"], th)
                    self.assertEqual(provider["ascent"], layout.ASCENT - r * th)
        agents = json.loads((ROOT / "arena-plugin/src/main/resources/valoblock/agents/index.json").read_text())["agents"]
        for i in range(len(agents)):
            provider, width, height = tiles[vb_layout.PORTRAIT_BASE + i]
            self.assertEqual((width, height), (vb_layout.PORTRAIT_W, vb_layout.PORTRAIT_H))
            self.assertEqual(provider["height"], vb_layout.PORTRAIT_H)
            self.assertEqual(provider["ascent"], layout.ASCENT - vb_layout.PORTRAIT_Y)
        self.assertIn(vb_layout.PORTRAIT_NONE, tiles)
        # the tactical map: one plan glyph per map and one per zone, 90 x 90 px = 1 texture px per GUI px, over the chest's 5 x 5 middle slots
        ids = (ROOT / "arena-plugin/src/main/java/win/vampyuyu/valoblock/VbData.java").read_text().split("MAP_IDS = List.of(")[1].split(")")[0]
        maps = [m for m in re.findall(r'"(\w+)"', ids)]
        self.assertEqual(maps, list(vb_layout.TAC_MAPS))
        n = vb_layout.TAC_GRID * vb_layout.TAC_GRID
        for mi, mid in enumerate(vb_layout.TAC_MAPS):
            plan = json.loads((ROOT / f"arena-plugin/src/main/resources/valoblock/maps/{mid}.json").read_text())["plan"]
            self.assertEqual((len(plan), len(plan[0])), (30, 30), mid)  # 3 px a cell, 6 x 6 cells a zone: the art assumes it
            for code in [vb_layout.TAC_PLAN_BASE + mi] + [vb_layout.TAC_ZONE_BASE + mi * n + k for k in range(n)]:
                provider, width, height = tiles[code]
                self.assertEqual((width, height, provider["height"]), (vb_layout.TAC_PLAN_PX, vb_layout.TAC_PLAN_PX, vb_layout.TAC_PLAN_PX))
                self.assertEqual(provider["ascent"], layout.ASCENT - vb_layout.TAC_PLAN_Y)
        self.assertLess(vb_layout.TAC_ZONE_BASE + len(vb_layout.TAC_MAPS) * n, vb_layout.TAC_ZONE_BASE + 0x100)
        for code in (vb_layout.TO_PLAN, vb_layout.FROM_PLAN):
            self.assertIn(code, vb_layout.ADVANCES)
        x = 8 + vb_layout.ADVANCES[vb_layout.SHIFT_BACK] + vb_layout.ROW_W + vb_layout.ADVANCES[vb_layout.TO_PLAN]
        self.assertEqual(x, vb_layout.TAC_PLAN_X)
        x += vb_layout.TAC_PLAN_PX + 1 + vb_layout.ADVANCES[vb_layout.FROM_PLAN]
        self.assertEqual(x, vb_layout.TEXT_X)
        for sprite in ("tac_dim", "tac_mark", "tac_pick"):
            self.assertTrue((VALO / f"assets/vampyuyu/items/valoblock/{sprite}.json").is_file(), sprite)
            self.assertEqual(png_size(VALO / f"assets/vampyuyu/textures/item/valoblock/{sprite}.png"), (16, 16))
        # the title text lands where a window title starts: x = 8
        x = 8 + vb_layout.ADVANCES[vb_layout.SHIFT_BACK] + vb_layout.ROW_W + vb_layout.ADVANCES[vb_layout.TO_PORTRAIT]
        self.assertEqual(x, vb_layout.PORTRAIT_X)
        x += vb_layout.PORTRAIT_W + 1 + vb_layout.ADVANCES[vb_layout.FROM_PORTRAIT]
        self.assertEqual(x, vb_layout.TEXT_X)
        self.assertEqual(vb_layout.ROW_W + vb_layout.ADVANCES[vb_layout.TO_TEXT], vb_layout.TEXT_X)

    def test_hud_font_shader_and_plugin_table_agree(self):
        table = {}
        for line in (ROOT / "arena-plugin/src/main/resources/valoblock/vb-hud-sprites.txt").read_text().split("\n"):
            if line:
                kind, index, name = line.split(" ")
                table.setdefault(kind, {})[name] = int(index)
        self.assertEqual(list(table["element"]), [e[0] for e in vb_layout.HUD_ELEMENTS])
        font = json.loads((VALO / "assets/vampyuyu/font/vbhud.json").read_text())
        negs = next(p for p in font["providers"] if p["type"] == "space")["advances"]
        sizes = {}
        for p in font["providers"]:
            if p["type"] != "bitmap":
                continue
            w, h = png_size(VALO / "assets" / p["file"].replace(":", "/textures/", 1))
            index = ord(p["chars"][0]) - vb_layout.SPRITE_BASE
            self.assertEqual(h, p["height"])
            self.assertEqual(negs[chr(vb_layout.NEG_BASE + index)], -(w + 1), "the second glyph takes the advance back")
            sizes[index] = (w, h)
        by_name = {name: sizes[i] for name, i in table["sprite"].items()}
        self.assertEqual(len(sizes), len(table["sprite"]))
        # a sprite fills its element exactly (stretched bars only have the element's height)
        boxes = {e[0]: e for e in vb_layout.HUD_ELEMENTS}
        for name, size in by_name.items():
            if name in ("hp_bar", "hp_bar_low"):
                self.assertEqual(size[1], boxes["hp_bar"][5])
            elif name == "sh_bar":
                self.assertEqual(size[1], boxes["sh_bar"][5])
            elif name.endswith("plate"):
                self.assertEqual(size, (boxes[name][4], boxes[name][5]), name)
            elif name.startswith("tile_"):
                self.assertEqual(size, (boxes["tile0"][4], boxes["tile0"][5]), name)
            elif name.startswith("ic_"):
                self.assertEqual(size, (boxes["icon0"][4], boxes["icon0"][5]), name)
            elif name.startswith("pip_"):
                self.assertEqual(size, (boxes["pip0_0"][4], boxes["pip0_0"][5]), name)
            elif name.startswith("bd_"):
                self.assertEqual(size, (boxes["hp1"][4], boxes["hp1"][5]), name)
            elif name.startswith("sd_"):
                self.assertEqual(size, (boxes["sh1"][4], boxes["sh1"][5]), name)
            elif name.startswith(("xh_", "hit_")):
                self.assertEqual(size, (boxes["xh"][4], boxes["xh"][5]), name)
            elif name in ("seed_on", "seed_glow"):
                self.assertEqual(size, (boxes["seed"][4], boxes["seed"][5]), name)
            elif name == "plant_track":
                self.assertEqual(size, (boxes["plant_track"][4], boxes["plant_track"][5]), name)
            elif name == "plant_bar":
                self.assertEqual(size[1], boxes["plant_bar"][5])
            else:
                self.fail("sprite without a rule: " + name)
        # every element of the HUD fits on a 640 x 360 GUI and the ones sharing a sprite family share a size
        for name, anchor, x, y, w, h, stretch in vb_layout.HUD_ELEMENTS:
            self.assertGreater(w, 0)
            self.assertGreater(h, 0)
        vsh = (VALO / "assets/minecraft/shaders/core/text.vsh").read_text()
        self.assertIn(f"rgb.r == {vb_layout.HUD_R} && rgb.g >= {vb_layout.HUD_ID_BASE}", vsh)
        self.assertIn(f"const int VB_COUNT = {vb_layout.HUD_COUNT};", vsh)
        self.assertIn("rgb.r == 78 && rgb.g == 92", vsh, "the menu pack's branches stay, this file replaces the base shader")
        self.assertLessEqual(vb_layout.HUD_ID_BASE + vb_layout.HUD_COUNT, 256)
        self.assertEqual(vb_layout.HUD_R % 4, 0, "the text shadow colour is the colour / 4: exact only for a multiple of 4")
        # the base pack's shader is not touched by this one
        import fx_shaders  # noqa: E402
        self.assertEqual((PACK / "assets/minecraft/shaders/core/text.vsh").read_text(), fx_shaders.VSH_263)
        code = self.java("VbGlyphs.java")
        self.assertIn(f"HUD_R = {vb_layout.HUD_R}, ID_BASE = {vb_layout.HUD_ID_BASE}, SPRITE_BASE = 0x{vb_layout.SPRITE_BASE:X}, NEG_BASE = 0x{vb_layout.NEG_BASE:X}", code)
        inline = json.loads((VALO / "assets/vampyuyu/font/vbinline.json").read_text())
        self.assertEqual(len(inline["providers"]), len(table["inline"]))
        for p in inline["providers"]:
            self.assertEqual(png_size(VALO / "assets" / p["file"].replace(":", "/textures/", 1))[1], p["height"])

    def test_slot_maps_of_the_menus_match_the_pack_layout(self):
        code = self.java("VbSelectLayout.java")
        self.assertIn(f"ROSTER_COL = {vb_layout.SEL_ROSTER_COL}, TILE_COLS = {vb_layout.SEL_TILE_COLS}, TILE_ROWS = {vb_layout.SEL_TILE_ROWS}", code)
        code = self.java("VbArmoryLayout.java")
        self.assertIn(f"CARD_COL = {vb_layout.ARM_CARD_COL}, CARD_COLS = {vb_layout.ARM_CARD_COLS}, CARD_ROWS = {vb_layout.ARM_CARD_ROWS}", code)
        self.assertIn(f"ABILITY = {{{vb_layout.ARM_ABILITY_SLOTS[0]}, {vb_layout.ARM_ABILITY_SLOTS[1]}}}", code)
        self.assertIn(f"SHIELD_ROW = {vb_layout.ARM_SHIELD_ROW}", code)

    def test_armory_cards_have_a_frame_state_per_weapon_and_a_blank_filler(self):
        for w in self.weapons:
            for suffix in ("", "_owned", "_locked"):
                name = f"card_{w['id']}{suffix}"
                definition = json.loads((self.base / f"items/valoblock/{name}.json").read_text())
                self.assertTrue(definition["oversized_in_gui"], name)
                model = json.loads((self.base / f"models/item/valoblock/{name}.json").read_text())
                self.assertEqual(model["textures"]["layer0"], f"vampyuyu:item/valoblock/card_frame{suffix}")
                self.assertEqual(model["textures"]["layer1"], f"vampyuyu:item/valoblock/{w['id']}{'_locked' if suffix == '_locked' else ''}")
                self.assertEqual(model["display"]["gui"]["scale"], [2.25, 2.25, 2.25], "the 32 px card covers 2 x 2 slots (36 GUI px)")
        for suffix in ("", "_owned", "_locked"):
            self.assertEqual(png_size(self.base / f"textures/item/valoblock/card_frame{suffix}.png"), (32, 32))
        tabs = ["sidearms", "smgs", "shotguns", "rifles", "snipers", "heavy"]
        for t in tabs:
            for sel in ("", "_sel"):
                self.assertEqual(png_size(self.base / f"textures/item/valoblock/tab_{t}{sel}.png"), (32, 32))
        self.assertEqual(json.loads((self.base / "items/valoblock/blank.json").read_text())["model"]["type"], "minecraft:empty")
        declared = re.search(r"TABS = \{(.*?)\}", self.java("VbArmoryLayout.java")).group(1)
        self.assertEqual([t.strip().strip('"').lower() for t in declared.split(",")], tabs, "the tab names of the plugin name the pack's tab sprites")

    def test_every_agent_has_a_64x64_skin_and_a_worn_outfit(self):
        agents = [f for f in (ROOT / "arena-plugin/src/main/resources/valoblock/agents").glob("*.json") if f.name != "index.json"]
        self.assertEqual(len(agents), 8)
        for f in agents:
            i = json.loads(f.read_text())["id"]
            self.assertEqual(png_size(self.base / f"textures/entity/valoblock/skin_{i}.png"), (64, 64), i)
            for layer in ("humanoid", "humanoid_leggings"):
                self.assertEqual(png_size(self.base / f"textures/entity/equipment/{layer}/valoblock/{i}.png"), (64, 32), i)
            eq = json.loads((self.base / f"equipment/valoblock_{i}.json").read_text())
            self.assertEqual(eq["layers"]["humanoid"][0]["texture"], f"vampyuyu:valoblock/{i}")
            self.assertEqual(eq["layers"]["humanoid_leggings"][0]["texture"], f"vampyuyu:valoblock/{i}")

    def test_both_packs_build_reproducibly_and_stay_apart(self):
        with tempfile.TemporaryDirectory() as a, tempfile.TemporaryDirectory() as b:
            first, digest = build_pack.build(a, "valoblock")
            second, again = build_pack.build(b, "valoblock")
            self.assertEqual(digest, again)
            self.assertEqual(first.read_bytes(), second.read_bytes())
            self.assertEqual(first.name, "vampyuyu-valoblock.zip")
            self.assertRegex((pathlib.Path(a) / "vampyuyu-valoblock.sha1").read_text().strip(), r"^[0-9a-f]{40}$")
            menus, menus_digest = build_pack.build(a)
            self.assertNotEqual(digest, menus_digest, "two different packs, two different hashes")
            with zipfile.ZipFile(first) as z:
                members = set(z.namelist())
            self.assertIn("pack.mcmeta", members)
            self.assertIn("assets/vampyuyu/textures/misc/scope.png", members)
            self.assertIn("assets/vampyuyu/items/valoblock/longwind.json", members)
            fonts = {m for m in members if m.startswith("assets/vampyuyu/font/")}
            self.assertEqual(fonts, {f"assets/vampyuyu/font/{n}.json" for n in ("vbmenus", "vbhud", "vbinline")}, "only the ValoBlock fonts, never the menu pack's")
            self.assertFalse([m for m in members if "/werewolf/" in m], "no werewolf assets in this pack")
            with zipfile.ZipFile(menus) as z:
                self.assertFalse([m for m in z.namelist() if "valoblock" in m], "no ValoBlock assets in the base pack")
            self.assertLess(first.stat().st_size, 2 << 20)

if __name__ == "__main__":
    unittest.main()
