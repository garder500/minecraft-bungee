"""The sprites made from concept sheets (import_textures.py): the procedural scripts must not overwrite them."""
import json
import pathlib

_LIST = pathlib.Path(__file__).resolve().parent / "imported.txt"
_PACK = pathlib.Path(__file__).resolve().parent.parent / "pack/assets/vampyuyu"


def imported(model_id):
    """True when family/id (e.g. "ore/frostium") has an imported flat sprite."""
    return _LIST.exists() and model_id in _LIST.read_text().split()


def gui_model(model_id):
    """The model id of the flat GUI sprite of an item that is 3D in the hand. An item whose imported sprite was moved to
    textures/item/<id>_gui.png (committed, so the procedural scripts cannot overwrite it) gets its own flat model
    <id>_gui, written here; otherwise the flat model is the item's own id."""
    kind, name = model_id.split("/")
    if not (_PACK / "textures/item" / kind / f"{name}_gui.png").exists():
        return f"vampyuyu:item/{model_id}"
    parent = "minecraft:item/handheld" if kind in ("weapon", "tool") else "minecraft:item/generated"
    path = _PACK / "models/item" / kind / f"{name}_gui.json"
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps({"parent": parent, "textures": {"layer0": f"vampyuyu:item/{model_id}_gui"}}, indent=2) + "\n",
                    encoding="utf-8")
    return f"vampyuyu:item/{model_id}_gui"
