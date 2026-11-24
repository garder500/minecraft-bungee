"""The pixel fonts of the custom HUD and the rules that lay a line of text out.

The HUD's words are not painted in sprites: the plugin writes them one glyph per character, in the player's
language (arena-plugin lang/*.yml). Three fonts, all capitals, 1 texel per GUI pixel:
  h  headline, 9 px tall, 2 px stems: the phase label of the top plate
  b  bold, 7 px tall: the role name, the head count, the vote caption
  m  regular 7 px in a muted tint: the team line and the captions
plus the 11 px clock digits (CLOCK). Every glyph of a font is drawn in a cell of the same size (layout.HUD_FONT_CELLS)
with its ink at the left; the plugin moves the cells by each character's advance, so the text is proportional.

Ported in CustomHud.java (normalise, width, fit, the offsets): keep the two in step, scripts/test_hud_sheet.py checks
the Python side against sequences derived by hand from the Java.
"""
import pathlib, sys, unicodedata

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent.parent))
import layout  # noqa: E402

# Regular font, 5 x 7, one string of rows per glyph (columns are trimmed to the ink: the font is proportional).
REGULAR = {
    "A": "01110 10001 10001 11111 10001 10001 10001", "B": "11110 10001 10001 11110 10001 10001 11110",
    "C": "01110 10001 10000 10000 10000 10001 01110", "D": "11100 10010 10001 10001 10001 10010 11100",
    "E": "11111 10000 10000 11110 10000 10000 11111", "F": "11111 10000 10000 11110 10000 10000 10000",
    "G": "01110 10001 10000 10111 10001 10001 01111", "H": "10001 10001 10001 11111 10001 10001 10001",
    "I": "01110 00100 00100 00100 00100 00100 01110", "J": "00111 00010 00010 00010 00010 10010 01100",
    "K": "10001 10010 10100 11000 10100 10010 10001", "L": "10000 10000 10000 10000 10000 10000 11111",
    "M": "10001 11011 10101 10101 10001 10001 10001", "N": "10001 11001 10101 10011 10001 10001 10001",
    "O": "01110 10001 10001 10001 10001 10001 01110", "P": "11110 10001 10001 11110 10000 10000 10000",
    "Q": "01110 10001 10001 10001 10101 10010 01101", "R": "11110 10001 10001 11110 10100 10010 10001",
    "S": "01111 10000 10000 01110 00001 00001 11110", "T": "11111 00100 00100 00100 00100 00100 00100",
    "U": "10001 10001 10001 10001 10001 10001 01110", "V": "10001 10001 10001 10001 10001 01010 00100",
    "W": "10001 10001 10001 10101 10101 10101 01010", "X": "10001 10001 01010 00100 01010 10001 10001",
    "Y": "10001 10001 01010 00100 00100 00100 00100", "Z": "11111 00001 00010 00100 01000 10000 11111",
    "0": "01110 10001 10011 10101 11001 10001 01110", "1": "00100 01100 00100 00100 00100 00100 01110",
    "2": "01110 10001 00001 00010 00100 01000 11111", "3": "11110 00001 00001 01110 00001 00001 11110",
    "4": "00010 00110 01010 10010 11111 00010 00010", "5": "11111 10000 11110 00001 00001 10001 01110",
    "6": "00110 01000 10000 11110 10001 10001 01110", "7": "11111 00001 00010 00100 01000 01000 01000",
    "8": "01110 10001 10001 01110 10001 10001 01110", "9": "01110 10001 10001 01111 00001 00010 01100",
    ":": "00000 00100 00000 00000 00000 00100 00000", ".": "00000 00000 00000 00000 00000 00000 00100",
    "/": "00001 00010 00010 00100 01000 01000 10000", "-": "00000 00000 00000 01110 00000 00000 00000",
    "'": "00100 00100 00000 00000 00000 00000 00000", ",": "00000 00000 00000 00000 00000 00100 01000",
    "!": "00100 00100 00100 00100 00100 00000 00100", "?": "01110 10001 00001 00010 00100 00000 00100",
    "&": "01100 10010 10100 01000 10101 10010 01101", "+": "00000 00100 00100 11111 00100 00100 00000",
}

# Bold glyphs that a 1 px smear of the regular one would fill in.
BOLD = {
    "M": "1100011 1110111 1111111 1101011 1100011 1100011 1100011",
    "G": "011110 110011 110000 110000 110111 110011 011110",
    "N": "110011 111011 111011 111111 110111 110111 110011",
    "W": "1100011 1100011 1100011 1101011 1101011 1111111 0110110",
    "X": "110011 110011 011110 001100 011110 110011 110011",
    "K": "110011 110110 111100 111000 111100 110110 110011",
    "V": "110011 110011 110011 110011 110011 011110 001100",
    "Y": "110011 110011 011110 001100 001100 001100 001100",
    "4": "000110 001110 011110 110110 111111 000110 000110",
    "/": "00011 00011 00110 00110 01100 11000 11000",
    "&": "011100 110110 110100 011000 110101 110010 011101",
}

# Headline font, 9 rows, 2 px stems.
HEADLINE = {
    "A": "011110 110011 110011 110011 111111 110011 110011 110011 110011",
    "B": "111110 110011 110011 110011 111110 110011 110011 110011 111110",
    "C": "011110 110011 110000 110000 110000 110000 110000 110011 011110",
    "D": "111100 110110 110011 110011 110011 110011 110011 110110 111100",
    "E": "111111 110000 110000 110000 111110 110000 110000 110000 111111",
    "F": "111111 110000 110000 110000 111110 110000 110000 110000 110000",
    "G": "011110 110011 110000 110000 110111 110011 110011 110011 011110",
    "H": "110011 110011 110011 110011 111111 110011 110011 110011 110011",
    "I": "1111 0110 0110 0110 0110 0110 0110 0110 1111",
    "J": "000011 000011 000011 000011 000011 000011 110011 110011 011110",
    "K": "110011 110011 110110 111100 111000 111100 110110 110011 110011",
    "L": "110000 110000 110000 110000 110000 110000 110000 110000 111111",
    "M": "11000011 11100111 11111111 11011011 11011011 11000011 11000011 11000011 11000011",
    "N": "1100011 1110011 1110011 1111011 1101111 1100111 1100111 1100011 1100011",
    "O": "011110 110011 110011 110011 110011 110011 110011 110011 011110",
    "P": "111110 110011 110011 110011 111110 110000 110000 110000 110000",
    "Q": "011110 110011 110011 110011 110011 110011 110111 110110 011011",
    "R": "111110 110011 110011 110011 111110 110110 110011 110011 110011",
    "S": "011110 110011 110000 110000 011110 000011 000011 110011 011110",
    "T": "111111 001100 001100 001100 001100 001100 001100 001100 001100",
    "U": "110011 110011 110011 110011 110011 110011 110011 110011 011110",
    "V": "110011 110011 110011 110011 110011 110011 011110 011110 001100",
    "W": "11000011 11000011 11000011 11000011 11011011 11011011 11111111 11100111 11000011",
    "X": "110011 110011 110011 011110 001100 011110 110011 110011 110011",
    "Y": "110011 110011 110011 011110 001100 001100 001100 001100 001100",
    "Z": "111111 000011 000011 000110 001100 011000 110000 110000 111111",
    "0": "011110 110011 110011 110111 111011 110011 110011 110011 011110",
    "1": "0011 0111 1111 0011 0011 0011 0011 0011 0011",
    "2": "011110 110011 000011 000011 000110 001100 011000 110000 111111",
    "3": "011110 110011 000011 000011 001110 000011 000011 110011 011110",
    "4": "000111 001111 011011 110011 110011 111111 000011 000011 000011",
    "5": "111111 110000 110000 111110 000011 000011 000011 110011 011110",
    "6": "011110 110011 110000 110000 111110 110011 110011 110011 011110",
    "7": "111111 000011 000011 000110 000110 001100 001100 001100 001100",
    "8": "011110 110011 110011 110011 011110 110011 110011 110011 011110",
    "9": "011110 110011 110011 110011 011111 000011 000011 110011 011110",
    ":": "00 00 11 11 00 00 11 11 00", ".": "00 00 00 00 00 00 00 11 11",
    "/": "000011 000011 000110 000110 001100 011000 011000 110000 110000",
    "-": "0000 0000 0000 0000 1111 0000 0000 0000 0000",
    "'": "11 11 01 00 00 00 00 00 00", ",": "00 00 00 00 00 00 00 11 01",
    "!": "11 11 11 11 11 11 00 11 11",
    "?": "011110 110011 000011 000110 001100 001100 000000 001100 001100",
    "&": "0111000 1101100 1101100 0111000 1101101 1100110 1100110 1100110 0111011",
    "+": "000000 000000 001100 001100 111111 001100 001100 000000 000000",
}

# Clock digits (the timer and the ballots against you), 7 x 11, 2 px strokes.
CLOCK = {
    "0": "0111110 1111111 1100011 1100011 1100011 1100011 1100011 1100011 1100011 1111111 0111110",
    "1": "0001100 0011100 0111100 0001100 0001100 0001100 0001100 0001100 0001100 0111111 0111111",
    "2": "0111110 1111111 1100011 0000011 0000111 0001110 0011100 0111000 1110000 1111111 1111111",
    "3": "0111110 1111111 1100011 0000011 0011110 0011110 0000011 0000011 1100011 1111111 0111110",
    "4": "0000111 0001111 0011011 0110011 1100011 1100011 1111111 1111111 0000011 0000011 0000011",
    "5": "1111111 1111111 1100000 1100000 1111110 1111111 0000011 0000011 1100011 1111111 0111110",
    "6": "0111110 1111111 1100011 1100000 1111110 1111111 1100011 1100011 1100011 1111111 0111110",
    "7": "1111111 1111111 0000011 0000110 0000110 0001100 0001100 0011000 0011000 0011000 0011000",
    "8": "0111110 1111111 1100011 1100011 0111110 0111110 1100011 1100011 1100011 1111111 0111110",
    "9": "0111110 1111111 1100011 1100011 1100011 1111111 0111111 0000011 1100011 1111111 0111110",
    ":": "00 00 00 11 11 00 00 11 11 00 00",
}

# Accent marks, two rows above the capital: (thin, heavy). The cedilla is one row under the baseline.
MARKS = {
    "\u0300": (["10", "01"], ["110", "011"]),             # grave
    "\u0301": (["01", "10"], ["011", "110"]),             # acute
    "\u0302": (["010", "101"], ["0110", "1001"]),         # circumflex
    "\u0308": (["000", "101"], ["00000", "11011"]),       # diaeresis
}
CEDILLA = "\u0327"
SPACE = {"m": 4, "b": 5, "h": 6}     # advance of a space
HEIGHT = {"m": 7, "b": 7, "h": 9}    # capital height
ABOVE = 2                            # rows kept above the capitals for the accents


def trim(rows):
    """Drops the empty columns left and right of a glyph."""
    cols = [i for i in range(len(rows[0])) if any(r[i] == "1" for r in rows)]
    return [r[cols[0]:cols[-1] + 1] for r in rows] if cols else rows


def smear(rows):
    """Bold: every pixel also lights the one on its right."""
    return ["".join("1" if "1" in (r + "0")[max(0, i - 1):i + 1] else "0" for i in range(len(r) + 1)) for r in rows]


def base_rows(font, ch):
    """Rows of a plain (unaccented) glyph of a font, trimmed; None if the font has no such glyph."""
    if font == "h":
        return trim(HEADLINE[ch].split()) if ch in HEADLINE else None
    if ch not in REGULAR:
        return None
    if font == "b":
        return trim(BOLD[ch].split()) if ch in BOLD else smear(trim(REGULAR[ch].split()))
    return trim(REGULAR[ch].split())


def glyph(font, ch):
    """(above rows, capital rows, below row) of a character, all the same width; None if it cannot be drawn."""
    parts = unicodedata.normalize("NFD", ch)
    rows = base_rows(font, parts[0])
    if rows is None:
        return None
    width = len(rows[0])
    above, below = ["0" * width] * ABOVE, "0" * width
    for mark in parts[1:]:
        if mark == CEDILLA:
            x = width // 2 - (0 if font == "m" else 1)
            below = "".join("1" if x <= i < x + (1 if font == "m" else 2) else "0" for i in range(width))
            continue
        if mark not in MARKS:
            return None
        pattern = MARKS[mark][0 if font == "m" else 1]
        wide = max(width, len(pattern[0]))
        pad = (wide - width) // 2
        rows = ["0" * pad + r + "0" * (wide - width - pad) for r in rows]
        below = "0" * pad + below + "0" * (wide - width - pad)
        width = wide
        off = (width - len(pattern[0])) // 2
        above = ["0" * off + p + "0" * (width - off - len(pattern[0])) for p in pattern]
    return above, rows, below


def sprite(font, ch):
    """Sprite name of a character: the file name must be [a-z0-9_], so the code point in hex."""
    return f"f{font}_{ord(ch):04x}"


def advance(font, ch):
    """How far a character moves the pen: its ink plus one pixel (the outlines of two letters share a column)."""
    return SPACE[font] if ch == " " else len(glyph(font, ch)[1][0]) + 1


def normalise(text):
    """What the HUD can write of a text: capitals, the accents of layout.HUD_CHARS, '?' for anything else.
    CustomHud.normalise is the same."""
    out = []
    upper = unicodedata.normalize("NFC", text).upper().replace("\u2019", "'").replace("\u0152", "OE").replace("\u00c6", "AE")
    for ch in " ".join(upper.split()):
        if ch == " " or ch in layout.HUD_CHARS:
            out.append(ch)
            continue
        plain = unicodedata.normalize("NFD", ch)[0]
        out.append(plain if plain in layout.HUD_CHARS else "?")
    return "".join(out)


def width(font, text):
    """Pixel width of an already normalised line (the outline of the cells is not counted)."""
    return max(0, sum(advance(font, ch) for ch in text) - 1) if text else 0


def fit(font, text, limit):
    """The line cut to `limit` pixels, ending with a dot when something was dropped. A net for override data:
    the shipped words all fit (scripts/test_hud_sheet.py)."""
    if width(font, text) <= limit:
        return text
    while text and width(font, text.rstrip() + ".") > limit:
        text = text[:-1]
    return text.rstrip() + "."


def offsets(font, text, align, limit):
    """[(character, x)] of a normalised line that fits: x is where its cell starts, from the element's origin."""
    x = {"left": 0, "right": limit - width(font, text), "centre": (limit - width(font, text)) // 2}[align]
    out = []
    for ch in text:
        if ch != " ":
            out.append((ch, x))
        x += advance(font, ch)
    return out
