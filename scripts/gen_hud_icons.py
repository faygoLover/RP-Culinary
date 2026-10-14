"""Иконки HUD Culinary (textures/gui/hud_icons.png, 32×16): окорочок сытости 9×9 в (0,0), капля воды 7×7 в (9,0).
Перерисовывает окорочок; остальное в файле (капля) остаётся как есть.

    python scripts/gen_hud_icons.py
"""
import os
from PIL import Image

OUT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "assets", "rpculinary", "textures", "gui", "hud_icons.png")
PAL = {"O": (59, 29, 14), "M": (176, 92, 36), "L": (222, 140, 64), "H": (246, 196, 120), "B": (236, 228, 212), "b": (168, 158, 142),
       "W": (24, 52, 120), "w": (60, 140, 224), "s": (170, 214, 255)}
# Окорочок как у ванильной сытости: мясо вверху справа, кость внизу слева.
DRUMSTICK = [
    ".....OOO.",
    "....OHLMO",
    "...OHLLMO",
    "...OLLMMO",
    "..OMLMMO.",
    ".OBOMMO..",
    "OBbOOO...",
    "ObO......",
    ".O.......",
]
DROP = [
    "...W...",
    "..WwW..",
    ".WwwwW.",
    "WwswwwW",
    "WswwwwW",
    ".WwwwW.",
    "..WWW..",
]


def put(img, rows, ox, oy):
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch != ".":
                img.putpixel((ox + x, oy + y), PAL[ch] + (255,))


img = Image.open(OUT).convert("RGBA") if os.path.exists(OUT) else Image.new("RGBA", (32, 16), (0, 0, 0, 0))
for y in range(9):
    for x in range(9):
        img.putpixel((x, y), (0, 0, 0, 0))
put(img, DRUMSTICK, 0, 0)
if not os.path.exists(OUT):
    put(img, DROP, 9, 0)
img.save(OUT)
print("ok", OUT)
