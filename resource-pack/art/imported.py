"""The sprites made from concept sheets (import_textures.py): the procedural scripts must not overwrite them."""
import pathlib

_LIST = pathlib.Path(__file__).resolve().parent / "imported.txt"


def imported(model_id):
    """True when family/id (e.g. "ore/frostium") has an imported flat sprite."""
    return _LIST.exists() and model_id in _LIST.read_text().split()
