"""Draws MCA's button sprites (assets/mca/textures/gui/sprites/widget): the button, hovered and disabled, used by
every button on MCA's own screens instead of the stone-grey vanilla one (client config mcaButtonStyle). Nine-slice,
so a resource pack can replace the PNGs. The look goes with the screens' dark panels: a dark slate face with rounded
corners, a light top edge, and a golden rim under the mouse.

usage: python -I button_sprites.py <repo root>
"""
import json
import sys
from pathlib import Path

from PIL import Image

out = Path(sys.argv[1]) / "common/src/main/resources/assets/mca/textures/gui/sprites/widget"
out.mkdir(parents=True, exist_ok=True)
W, H, BORDER = 64, 20, 4


def lerp(a, b, t):
    return tuple(round(x + (y - x) * t) for x, y in zip(a, b))


def button(top, bottom, rim, light, shade, alpha=255):
    img = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    px = img.load()
    for y in range(H):
        face = lerp(top, bottom, y / (H - 1))
        for x in range(W):
            px[x, y] = face + (alpha,)
    for x in range(W):
        px[x, 0] = px[x, H - 1] = rim + (alpha,)
    for y in range(H):
        px[0, y] = px[W - 1, y] = rim + (alpha,)
    for x in range(1, W - 1):
        px[x, 1] = light + (alpha,)  # light catches the top edge
        px[x, H - 2] = shade + (alpha,)  # and the bottom falls in shadow
    # rounded corners: the corner pixel goes, the rim steps in
    for cx, cy, dx, dy in ((0, 0, 1, 1), (W - 1, 0, -1, 1), (0, H - 1, 1, -1), (W - 1, H - 1, -1, -1)):
        px[cx, cy] = (0, 0, 0, 0)
        px[cx + dx, cy + dy] = rim + (alpha,)
    return img


def save(name, img):
    img.save(out / f"{name}.png")
    meta = {"gui": {"scaling": {"type": "nine_slice", "width": W, "height": H, "border": BORDER}}}
    (out / f"{name}.png.mcmeta").write_text(json.dumps(meta, indent=2) + "\n", encoding="utf-8")


save("button", button((0x3C, 0x3A, 0x46), (0x2A, 0x28, 0x32), (0x12, 0x11, 0x16), (0x5A, 0x57, 0x68), (0x1E, 0x1C, 0x24)))
save("button_highlighted", button((0x54, 0x4E, 0x62), (0x3A, 0x35, 0x46), (0xE0, 0xB8, 0x4A), (0x7A, 0x72, 0x8C), (0x2A, 0x26, 0x32)))
save("button_disabled", button((0x26, 0x25, 0x2A), (0x20, 0x1F, 0x24), (0x16, 0x15, 0x19), (0x2E, 0x2D, 0x33), (0x1A, 0x19, 0x1D), 0xD0))
print("wrote", out)
