"""Draws the interaction screen's panel sprites (assets/mca/textures/gui/sprites/interact): the panel behind each
block of information and the cell behind an icon. Both are nine-slice sprites, so a resource pack can replace the
PNGs (and their .mcmeta) to restyle the screen. The defaults look like the screen did before: a translucent dark panel
and a dark cell.

usage: python -I gui_sprites.py <repo root>
"""
import json
import sys
from pathlib import Path

from PIL import Image

out = Path(sys.argv[1]) / "common/src/main/resources/assets/mca/textures/gui/sprites/interact"
out.mkdir(parents=True, exist_ok=True)


def save(name, img, border):
    img.save(out / f"{name}.png")
    meta = {"gui": {"scaling": {"type": "nine_slice", "width": img.width, "height": img.height, "border": border}}}
    (out / f"{name}.png.mcmeta").write_text(json.dumps(meta, indent=2) + "\n", encoding="utf-8")


# panel: translucent black, a slightly stronger rim
panel = Image.new("RGBA", (16, 16), (0, 0, 0, 0x88))
px = panel.load()
for i in range(16):
    for edge in ((i, 0), (i, 15), (0, i), (15, i)):
        px[edge] = (0, 0, 0, 0xAA)
save("panel", panel, 2)

# cell: dark, with a soft bevel (darker top left, lighter bottom right)
cell = Image.new("RGBA", (18, 18), (0x1E, 0x1E, 0x1E, 0xFF))
px = cell.load()
for i in range(18):
    px[i, 0] = px[0, i] = (0x12, 0x12, 0x12, 0xFF)
    px[i, 17] = px[17, i] = (0x34, 0x34, 0x34, 0xFF)
save("cell", cell, 1)

# empty cell: a free slot, lighter
empty = Image.new("RGBA", (18, 18), (0x2E, 0x2E, 0x2E, 0xFF))
px = empty.load()
for i in range(18):
    px[i, 0] = px[0, i] = (0x22, 0x22, 0x22, 0xFF)
    px[i, 17] = px[17, i] = (0x3C, 0x3C, 0x3C, 0xFF)
save("cell_empty", empty, 1)

# locked cell: darker still
locked = Image.new("RGBA", (18, 18), (0x14, 0x14, 0x14, 0xFF))
save("cell_locked", locked, 1)
print("wrote", out)
