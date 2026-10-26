"""Models, blockstates, items, loot, recipes, tags, names and the glass texture for the mirrors."""
import copy
import json
import subprocess
import sys
import tempfile
from pathlib import Path

from PIL import Image

MCA = Path(__file__).resolve().parents[2]
RES = MCA / "common/src/main/resources"
ASSETS = RES / "assets/mca"
DATA = RES / "data/mca"
SCRATCH = Path(__file__).parent

WOODS = ["oak", "spruce", "birch", "jungle", "acacia", "dark_oak", "mangrove", "cherry", "pale_oak", "bamboo", "crimson", "warped"]
ROT = {"south": 0, "west": 90, "north": 180, "east": 270}


def write_json(path: Path, obj):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(obj, indent=2, ensure_ascii=False) + "\n", encoding="utf-8", newline="\n")


def body_texture(wood):
    if wood == "bamboo":
        return "minecraft:block/stripped_bamboo_block"
    if wood in ("crimson", "warped"):
        return f"minecraft:block/stripped_{wood}_stem"
    return f"minecraft:block/stripped_{wood}_log"


def el(frm, to, tex, faces=("north", "south", "east", "west", "up", "down")):
    return {"from": list(frm), "to": list(to), "faces": {f: {"texture": tex} for f in faces}}


def shifted(elements, dx=0.0, dy=0.0):
    out = copy.deepcopy(elements)
    for e in out:
        e["from"] = [e["from"][0] + dx, e["from"][1] + dy, e["from"][2]]
        e["to"] = [e["to"][0] + dx, e["to"][1] + dy, e["to"][2]]
    return out

# ---------------------------------------------------------------- shapes, for a mirror facing south (front at +z)
# the glass's front is at z 8.25: MirrorBlock.glass() and the renderer's reflection rely on it


def small_mirror(dy=0.0):
    return shifted([
        el((4, 0, 6), (12, 1.5, 10), "#body"),
        el((7, 1.5, 7), (9, 2, 8.5), "#body"),
        el((3, 2, 7), (13, 14, 8), "#body"),
        el((3, 2, 8), (4, 14, 8.75), "#body"),
        el((12, 2, 8), (13, 14, 8.75), "#body"),
        el((4, 13, 8), (12, 14, 8.75), "#body"),
        el((4, 2, 8), (12, 3, 8.75), "#body"),
        el((4, 3, 8), (12, 13, 8.25), "#glass", faces=("south",)),
    ], dy=dy)


STANDING_LOWER = [
    el((0.5, 0, 4), (3, 1, 12), "#body"),
    el((13, 0, 4), (15.5, 1, 12), "#body"),
    el((1, 1, 7), (2.5, 16, 9), "#body"),
    el((13.5, 1, 7), (15, 16, 9), "#body"),
    el((2.5, 2, 7), (13.5, 16, 7.75), "#body"),
    el((2.5, 2, 7.75), (13.5, 3, 8.75), "#body"),
    el((2.5, 3, 7.75), (13.5, 16, 8.25), "#glass", faces=("south",)),
]
STANDING_UPPER = [
    el((1, 0, 7), (2.5, 14, 9), "#body"),
    el((13.5, 0, 7), (15, 14, 9), "#body"),
    el((1, 14, 7), (15, 15.5, 9), "#body"),
    el((7, 15.5, 7.5), (9, 16.5, 8.5), "#body"),
    el((2.5, 0, 7), (13.5, 14, 7.75), "#body"),
    el((2.5, 0, 7.75), (13.5, 14, 8.25), "#glass", faces=("south",)),
]

TEXTURES = {"particle": "#body"}
ITEM_DISPLAY = {
    "gui": {"rotation": [30, 225, 0], "translation": [0, 0, 0], "scale": [0.625, 0.625, 0.625]},
    "ground": {"rotation": [0, 0, 0], "translation": [0, 3, 0], "scale": [0.25, 0.25, 0.25]},
    "fixed": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [0.5, 0.5, 0.5]},
    "thirdperson_righthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0], "scale": [0.375, 0.375, 0.375]},
    "firstperson_righthand": {"rotation": [0, 45, 0], "translation": [0, 0, 0], "scale": [0.4, 0.4, 0.4]},
    "firstperson_lefthand": {"rotation": [0, 225, 0], "translation": [0, 0, 0], "scale": [0.4, 0.4, 0.4]},
}
STANDING_DISPLAY = {k: {**v, "scale": [s * 0.6 for s in v["scale"]]} for k, v in ITEM_DISPLAY.items()}

models = ASSETS / "models/block"
templates = {
    "template_mirror": small_mirror(),
    "template_mirror_low": small_mirror(-8),
    "template_standing_mirror_lower": STANDING_LOWER,
    "template_standing_mirror_upper": STANDING_UPPER,
}
for name, elements in templates.items():
    write_json(models / f"{name}.json", {"parent": "minecraft:block/block", "textures": TEXTURES, "elements": elements})
write_json(models / "template_mirror_item.json", {
    "parent": "minecraft:block/block", "textures": TEXTURES, "display": ITEM_DISPLAY, "elements": small_mirror()})
write_json(models / "template_standing_mirror_item.json", {
    "parent": "minecraft:block/block", "textures": TEXTURES, "display": STANDING_DISPLAY,
    "elements": shifted(STANDING_LOWER, dy=-8) + shifted(STANDING_UPPER, dy=8)})

for wood in WOODS:
    textures = {"body": body_texture(wood), "glass": "mca:block/mirror_glass"}
    for kind, pieces in (("mirror", ["", "_low"]), ("standing_mirror", ["_lower", "_upper"])):
        name = f"{wood}_{kind}"
        for piece in pieces:
            write_json(models / f"{name}{piece}.json", {"parent": f"mca:block/template_{kind}{piece}", "textures": textures})
        write_json(models / f"{name}_item.json", {"parent": f"mca:block/template_{kind}_item", "textures": textures})
        variants = {}
        for facing, y in ROT.items():
            rot = {"y": y} if y else {}
            if kind == "mirror":
                variants[f"facing={facing},low=false"] = {"model": f"mca:block/{name}", **rot}
                variants[f"facing={facing},low=true"] = {"model": f"mca:block/{name}_low", **rot}
            else:
                variants[f"facing={facing},half=lower"] = {"model": f"mca:block/{name}_lower", **rot}
                variants[f"facing={facing},half=upper"] = {"model": f"mca:block/{name}_upper", **rot}
        write_json(ASSETS / f"blockstates/{name}.json", {"variants": variants})
        write_json(ASSETS / f"items/{name}.json", {"model": {"type": "minecraft:model", "model": f"mca:block/{name}_item"}})
        conditions = [{"condition": "minecraft:survives_explosion"}]
        if kind == "standing_mirror":
            conditions.append({"condition": "minecraft:block_state_property", "block": f"mca:{name}", "properties": {"half": "lower"}})
        write_json(DATA / f"loot_table/blocks/{name}.json", {
            "type": "minecraft:block",
            "pools": [{"rolls": 1, "entries": [{"type": "minecraft:item", "name": f"mca:{name}"}], "conditions": conditions}],
        })
    planks = f"minecraft:{wood}_planks"
    write_json(DATA / f"recipe/{wood}_mirror.json", {
        "type": "minecraft:crafting_shaped", "category": "misc",
        "pattern": ["PGP", " P "], "key": {"P": planks, "G": "minecraft:glass_pane"},
        "result": {"id": f"mca:{wood}_mirror", "count": 1}})
    write_json(DATA / f"recipe/{wood}_standing_mirror.json", {
        "type": "minecraft:crafting_shaped", "category": "misc",
        "pattern": ["PGP", "PGP", "P P"], "key": {"P": planks, "G": "minecraft:glass_pane"},
        "result": {"id": f"mca:{wood}_standing_mirror", "count": 1}})

# ---------------------------------------------------------------- the glass: silver, lighter at the top, two glints
glass = Image.new("RGBA", (16, 16))
top, bottom = (201, 211, 218), (142, 155, 166)
for y in range(16):
    t = y / 15
    row = tuple(round(top[i] + (bottom[i] - top[i]) * t) for i in range(3))
    for x in range(16):
        glass.putpixel((x, y), (*row, 255))
for x, y in [(x, 11 - x) for x in range(12)] + [(x, 15 - x) for x in range(4, 16)] + [(x, 12 - x) for x in range(1, 12)]:
    if 0 <= y < 16:
        r, g, b, a = glass.getpixel((x, y))
        glass.putpixel((x, y), (min(255, r + 28), min(255, g + 26), min(255, b + 24), 255))
glass.save(ASSETS / "textures/block/mirror_glass.png")

# ---------------------------------------------------------------- tags
mirrors = [f"mca:{w}_{k}" for k in ("mirror", "standing_mirror") for w in WOODS]
axe = RES / "data/minecraft/tags/block/mineable/axe.json"
data = json.loads(axe.read_text(encoding="utf-8"))
data["values"] += [v for v in mirrors if v not in data["values"]]
write_json(axe, data)

# ---------------------------------------------------------------- names
WOOD_EN = {"oak": "Oak", "spruce": "Spruce", "birch": "Birch", "jungle": "Jungle", "acacia": "Acacia", "dark_oak": "Dark Oak",
           "mangrove": "Mangrove", "cherry": "Cherry", "pale_oak": "Pale Oak", "bamboo": "Bamboo", "crimson": "Crimson", "warped": "Warped"}
vf_pt = json.loads(((MCA.parent / "village-furnishings/common/src/main/resources/assets/village_furnishings/lang/pt_br.json"))
                   .read_text(encoding="utf-8"))
WOOD_PT = {w: vf_pt[f"block.village_furnishings.{w}_shutter"].split(" de ", 1)[1] for w in WOODS}
en = {}
pt = {}
for w in WOODS:
    en[f"block.mca.{w}_mirror"] = f"{WOOD_EN[w]} Mirror"
    en[f"block.mca.{w}_standing_mirror"] = f"{WOOD_EN[w]} Standing Mirror"
    pt[f"block.mca.{w}_mirror"] = f"Espelho de {WOOD_PT[w]}"
    pt[f"block.mca.{w}_standing_mirror"] = f"Espelho de Chão de {WOOD_PT[w]}"
pt.update({"block.mca.crimson_mirror": "Espelho Carmesim", "block.mca.crimson_standing_mirror": "Espelho de Chão Carmesim",
           "block.mca.warped_mirror": "Espelho Distorcido", "block.mca.warped_standing_mirror": "Espelho de Chão Distorcido"})
spec = Path(tempfile.gettempdir()) / "mirror_lang.json"
write_json(spec, {"en_us": en, "pt_br": pt})
subprocess.run([sys.executable, str(SCRATCH / "add_lang.py"), str(ASSETS / "lang"), str(spec)], check=True)
print("ok")
