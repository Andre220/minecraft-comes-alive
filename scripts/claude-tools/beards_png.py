"""Draws the built-in beard textures: 128x128, MCA skin layout at 2x, grayscale (tinted with the hair colour)."""
import random
import sys
from pathlib import Path

from PIL import Image

out = Path(sys.argv[1]) / "common/src/main/resources/assets/mca/skins/beard"
out.mkdir(parents=True, exist_ok=True)
S = 2  # pixels per layout unit

# head faces in layout units: front (8,8), right side (0,8) with its front edge at x=7, left side (16,8) with its
# front edge at x=16, bottom (16,0)
FRONT = (8, 8)
RIGHT = (0, 8)
LEFT = (16, 8)
BOTTOM = (16, 0)


def draw(name, shapes, seed):
    rng = random.Random(seed)
    img = Image.new("RGBA", (128, 128), (0, 0, 0, 0))
    px = img.load()
    for (face, x0, y0, x1, y1, density, alpha) in shapes:
        ox, oy = face
        for y in range(int((oy + y0) * S), int((oy + y1) * S)):
            for x in range(int((ox + x0) * S), int((ox + x1) * S)):
                if rng.random() > density:
                    continue
                v = max(0, min(255, int(rng.gauss(210, 18))))
                px[x, y] = (v, v, v, alpha if isinstance(alpha, int) else rng.randint(*alpha))
    img.save(out / f"{name}.png")


# the face is tight: forehead rows 0-3, eyes rows 4-6 (columns 0-3 and 5-8), mouth row 6, chin row 7
MUSTACHE = [(FRONT, 1.5, 6.0, 6.5, 6.5, 1.0, 255), (FRONT, 1.5, 6.5, 2.0, 7.0, 1.0, 255), (FRONT, 6.0, 6.5, 6.5, 7.0, 1.0, 255)]
CHEEKS = [(RIGHT, 4, 4, 8, 8, 0.95, 255), (LEFT, 0, 4, 4, 8, 0.95, 255),
          (FRONT, 0, 6.5, 1.5, 8, 0.95, 255), (FRONT, 6.5, 6.5, 8, 8, 0.95, 255)]

draw("stubble", [(FRONT, 0, 6.5, 8, 8, 0.55, (130, 200)), (RIGHT, 5, 5, 8, 8, 0.4, (110, 180)), (LEFT, 0, 5, 3, 8, 0.4, (110, 180))], 1)
draw("mustache", MUSTACHE, 2)
draw("goatee", MUSTACHE + [(FRONT, 3, 7, 5, 8, 1.0, 255), (FRONT, 2.5, 7, 3, 7.5, 1.0, 255), (FRONT, 5, 7, 5.5, 7.5, 1.0, 255)], 3)
draw("full", [(FRONT, 0, 7, 8, 8, 0.97, 255)] + MUSTACHE + CHEEKS + [(RIGHT, 6, 3, 8, 4, 0.9, 255), (LEFT, 0, 3, 2, 4, 0.9, 255)], 4)
draw("long", [(FRONT, 0, 7, 8, 8, 0.98, 255), (BOTTOM, 0, 0, 8, 8, 0.98, 255)] + MUSTACHE + CHEEKS
     + [(RIGHT, 6, 2, 8, 4, 0.9, 255), (LEFT, 0, 2, 2, 4, 0.9, 255)], 5)
draw("sideburns", [(RIGHT, 6, 1, 8, 6, 0.95, 255), (LEFT, 0, 1, 2, 6, 0.95, 255), (FRONT, 0, 2, 0.5, 4, 0.95, 255), (FRONT, 7.5, 2, 8, 4, 0.95, 255)], 6)
print("ok", sorted(p.name for p in out.glob("*.png")))
