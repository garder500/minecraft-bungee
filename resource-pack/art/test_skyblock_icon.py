#!/usr/bin/env python3
"""The committed Skyblock icon must be exactly what skyblock_icon.py draws (so `make art` cannot leave it stale)."""
import hashlib, json, pathlib, sys, unittest

HERE = pathlib.Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
import skyblock_icon  # noqa: E402

# sha256 of the 32x32 PNG; change it together with the drawing, on purpose.
EXPECTED_SHA256 = "e9fd63aa13925f83b3f2fe7a8b58201a5dfce97df3eb568a7adf3d46bd3d982a"


class SkyblockIconTest(unittest.TestCase):
    def test_png_is_up_to_date_and_pinned(self):
        generated = skyblock_icon.png(skyblock_icon.ROWS)
        committed = (skyblock_icon.PACK / "textures/item/icon/skyblock.png").read_bytes()
        self.assertEqual(generated, committed, "run: python3 resource-pack/art/skyblock_icon.py")
        self.assertEqual(hashlib.sha256(generated).hexdigest(), EXPECTED_SHA256)

    def test_model_and_item_definition_point_at_the_texture(self):
        model = json.loads((skyblock_icon.PACK / "models/item/icon/skyblock.json").read_text())
        item = json.loads((skyblock_icon.PACK / "items/icon/skyblock.json").read_text())
        self.assertEqual(model["textures"]["layer0"], "vampyuyu:item/icon/skyblock")
        self.assertEqual(item["model"]["model"], "vampyuyu:item/icon/skyblock")

    def test_preview_is_a_sharp_enlargement(self):
        self.assertTrue(skyblock_icon.preview(4).startswith(b"\x89PNG"))


if __name__ == "__main__":
    unittest.main()
