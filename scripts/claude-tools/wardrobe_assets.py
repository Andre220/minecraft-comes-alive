"""Models, blockstates, items, loot, recipes, tags and names for wardrobes, dressers and outfits."""
import copy
import json
import subprocess
import sys
import tempfile
from pathlib import Path

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


def handle(x, y, w=0.5, h=2.5):
    return el((x, y, 16), (x + w, y + h, 16.5), "#handle")


def knob(x, y):
    return el((x, y, 16), (x + 1, y + 1, 16.5), "#handle")


# ---------------------------------------------------------------- shapes, for a piece facing south (front at +z)
JOINS = [(False, False), (True, False), (False, True), (True, True)]


def joined(left, right):
    return f"{int(left)}{int(right)}"


def wardrobe(row, left, right):
    """The body, open at the front: back, sides and shelves (three places, one above the other, 2 to 30 px). Joined
    to a neighbour, the plinth and crown run on; on an open side they stand out a little. The door is drawn apart."""
    xa = 0 if left else -0.5
    xb = 16 if right else 16.5
    if row == "bottom":
        return [
            el((xa, 0, 1), (xb, 2, 15.5), "#body"),
            el((0, 2, 1), (16, 16, 2), "#body"),
            el((0, 2, 2), (1, 16, 15), "#body"),
            el((15, 2, 2), (16, 16, 15), "#body"),
            el((1, 11, 2), (15, 11.5, 15), "#body"),
        ]
    return [
        el((0, 0, 1), (16, 14, 2), "#body"),
        el((0, 0, 2), (1, 14, 15), "#body"),
        el((15, 0, 2), (16, 14, 15), "#body"),
        el((1, 4.5, 2), (15, 5, 15), "#body"),
        el((1, 13, 2), (15, 14, 15), "#body"),
        el((xa, 14, 0.5), (xb, 16, 16), "#body"),
    ]


def wardrobe_door(left, right):
    """The door, both blocks tall, drawn from the bottom block. Joined, it reaches nearer that side; the handle is on
    the right, or the left on the last door of a row (the hinge is the other side)."""
    x0 = 0.5 if left else 1
    x1 = 15.5 if right else 15
    hx = 2.5 if left and not right else 13
    return [el((x0, 3, 15), (x1, 29, 16), "#front"), handle(hx, 16)]


# one block tall, so there is room to see over it: four drawers per block, eight in all, under a top board
DRESSER_BODY = [
    el((0, 0, 1), (16, 15, 2), "#body"),
    el((0, 0, 2), (1, 15, 15), "#body"),
    el((15, 0, 2), (16, 15, 15), "#body"),
    el((1, 0, 2), (15, 1, 15), "#body"),
    el((7.5, 1, 2), (8.5, 14, 15), "#body"),
    el((1, 7, 2), (15, 8, 15), "#body"),
    el((1, 14, 2), (15, 15, 15), "#body"),
    el((0, 15, 0.5), (16, 16, 15.5), "#body"),
]
# the bottom left drawer; the renderer moves it to the other places and slides it out
DRESSER_DRAWER = [
    el((1, 1, 15), (7.5, 7, 16), "#front"),
    el((1.5, 1, 3), (7, 1.5, 15), "#body"),
    el((1, 1, 3), (1.5, 6, 15), "#body"),
    el((7, 1, 3), (7.5, 6, 15), "#body"),
    el((1.5, 1.5, 3), (7, 6, 3.5), "#body"),
    knob(3.75, 3.5),
]


def dresser_closed():
    return DRESSER_BODY + [e for i in range(4) for e in shifted(DRESSER_DRAWER, (i % 2) * 7.5, (i // 2) * 7)]


def shifted(elements, dx=0.0, dy=0.0):
    out = copy.deepcopy(elements)
    for e in out:
        e["from"] = [e["from"][0] + dx, e["from"][1] + dy, e["from"][2]]
        e["to"] = [e["to"][0] + dx, e["to"][1] + dy, e["to"][2]]
    return out


TEMPLATE_TEXTURES = {"particle": "#body"}
ITEM_DISPLAY = {
    "gui": {"rotation": [30, 225, 0], "translation": [0, 0, 0], "scale": [0.4, 0.4, 0.4]},
    "ground": {"rotation": [0, 0, 0], "translation": [0, 3, 0], "scale": [0.15, 0.15, 0.15]},
    "fixed": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [0.35, 0.35, 0.35]},
    "thirdperson_righthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0], "scale": [0.25, 0.25, 0.25]},
    "firstperson_righthand": {"rotation": [0, 45, 0], "translation": [0, 0, 0], "scale": [0.25, 0.25, 0.25]},
    "firstperson_lefthand": {"rotation": [0, 225, 0], "translation": [0, 0, 0], "scale": [0.25, 0.25, 0.25]},
}

models = ASSETS / "models/block"
templates = {
    **{f"template_wardrobe_{row}_{joined(l, r)}": wardrobe(row, l, r) for row in ("bottom", "top") for l, r in JOINS},
    **{f"template_wardrobe_door_{joined(l, r)}": wardrobe_door(l, r) for l, r in JOINS},
    "template_dresser_body": DRESSER_BODY,
    "template_dresser_drawer": DRESSER_DRAWER,
}
for name, elements in templates.items():
    write_json(models / f"{name}.json", {"parent": "minecraft:block/block", "textures": TEMPLATE_TEXTURES, "elements": elements})
# whole pieces for the items, shut, centred on the block
write_json(models / "template_wardrobe_item.json", {
    "parent": "minecraft:block/block", "textures": TEMPLATE_TEXTURES, "display": ITEM_DISPLAY,
    "elements": shifted(wardrobe("bottom", False, False) + wardrobe_door(False, False), dy=-8) + shifted(wardrobe("top", False, False), dy=8)})
write_json(models / "template_dresser_item.json", {
    "parent": "minecraft:block/block", "textures": TEMPLATE_TEXTURES, "display": ITEM_DISPLAY,
    "elements": shifted(dresser_closed(), -8) + shifted(dresser_closed(), 8)})

for wood in WOODS:
    textures = {"body": body_texture(wood), "front": f"minecraft:block/{wood}_planks", "handle": "minecraft:block/iron_block"}
    for kind in ("wardrobe", "dresser"):
        name = f"{wood}_{kind}"
        if kind == "wardrobe":
            pieces = [f"{p}_{joined(l, r)}" for p in ("bottom", "top", "door") for l, r in JOINS]
        else:
            pieces = ["body", "drawer"]
        for piece in pieces:
            write_json(models / f"{name}_{piece}.json", {"parent": f"mca:block/template_{kind}_{piece}", "textures": textures})
        write_json(models / f"{name}_item.json", {"parent": f"mca:block/template_{kind}_item", "textures": textures})
        variants = {}
        for facing, y in ROT.items():
            rot = {"y": y} if y else {}
            if kind == "wardrobe":
                for part, row in (("bottom_left", "bottom"), ("top_left", "top")):
                    for l, r in JOINS:
                        base = f"facing={facing},left={str(l).lower()},part={part}"
                        variants[f"{base},piece=body,right={str(r).lower()}"] = {"model": f"mca:block/{name}_{row}_{joined(l, r)}", **rot}
                        # the door is only drawn by the renderer, from the bottom block
                        variants[f"{base},piece=door,right={str(r).lower()}"] = {"model": f"mca:block/{name}_door_{joined(l, r)}", **rot}
            else:
                for part in ("bottom_left", "bottom_right"):
                    variants[f"facing={facing},part={part},piece=body"] = {"model": f"mca:block/{name}_body", **rot}
                    variants[f"facing={facing},part={part},piece=drawer"] = {"model": f"mca:block/{name}_drawer", **rot}
        write_json(ASSETS / f"blockstates/{name}.json", {"variants": variants})
        write_json(ASSETS / f"items/{name}.json", {"model": {"type": "minecraft:model", "model": f"mca:block/{name}_item"}})
        write_json(DATA / f"loot_table/blocks/{name}.json", {
            "type": "minecraft:block",
            "pools": [{"rolls": 1, "entries": [{"type": "minecraft:item", "name": f"mca:{name}"}],
                       "conditions": [{"condition": "minecraft:survives_explosion"},
                                      {"condition": "minecraft:block_state_property", "block": f"mca:{name}",
                                       "properties": {"part": "bottom_left"}}]}],
        })
    planks = f"minecraft:{wood}_planks"
    write_json(DATA / f"recipe/{wood}_wardrobe.json", {
        "type": "minecraft:crafting_shaped", "category": "misc",
        "pattern": ["PPP", "PCP", "PPP"], "key": {"P": planks, "C": "minecraft:chest"},
        "result": {"id": f"mca:{wood}_wardrobe", "count": 1}})
    write_json(DATA / f"recipe/{wood}_dresser.json", {
        "type": "minecraft:crafting_shaped", "category": "misc",
        "pattern": ["PPP", "PCP"], "key": {"P": planks, "C": "minecraft:chest"},
        "result": {"id": f"mca:{wood}_dresser", "count": 1}})

# ---------------------------------------------------------------- the outfit item
write_json(ASSETS / "items/outfit.json", {"model": {"type": "minecraft:special", "base": "mca:item/outfit", "model": {"type": "mca:outfit"}}})
write_json(ASSETS / "models/item/outfit.json", {
    "textures": {"particle": "minecraft:block/white_wool"},
    "gui_light": "front",
    "display": {
        # folded and lying down: seen from above at a slant
        "gui": {"rotation": [50, 0, 0], "translation": [0, 0, 0], "scale": [1.1, 1.1, 1.1]},
        "ground": {"rotation": [0, 0, 0], "translation": [0, -2, 0], "scale": [0.5, 0.5, 0.5]},
        # face out of a frame; laid on a table (which turns flat items over), face up
        "fixed": {"rotation": [-90, 0, 0], "translation": [0, 0, 0], "scale": [1, 1, 1]},
        "head": {"rotation": [0, 180, 0], "translation": [0, 13, 7], "scale": [1, 1, 1]},
        "thirdperson_righthand": {"rotation": [75, 0, 0], "translation": [0, 2, 1], "scale": [0.55, 0.55, 0.55]},
        "firstperson_righthand": {"rotation": [30, -30, 0], "translation": [1.13, 3.2, 1.13], "scale": [0.68, 0.68, 0.68]},
    },
})
write_json(DATA / "recipe/outfit.json", {
    "type": "minecraft:crafting_shapeless", "category": "misc",
    "ingredients": ["#minecraft:wool", "#minecraft:wool", "minecraft:string"],
    "result": {"id": "mca:outfit", "count": 1}})

# ---------------------------------------------------------------- tags
furniture = [f"mca:{w}_{k}" for k in ("wardrobe", "dresser") for w in WOODS]
write_json(DATA / "tags/block/wardrobes.json", {"replace": False, "values": furniture})
axe = RES / "data/minecraft/tags/block/mineable/axe.json"
data = json.loads(axe.read_text(encoding="utf-8"))
data["values"] += [v for v in furniture if v not in data["values"]]
write_json(axe, data)

# ---------------------------------------------------------------- names
WOOD_EN = {"oak": "Oak", "spruce": "Spruce", "birch": "Birch", "jungle": "Jungle", "acacia": "Acacia", "dark_oak": "Dark Oak",
           "mangrove": "Mangrove", "cherry": "Cherry", "pale_oak": "Pale Oak", "bamboo": "Bamboo", "crimson": "Crimson", "warped": "Warped"}
vf_pt = json.loads(((MCA.parent / "village-furnishings/common/src/main/resources/assets/village_furnishings/lang/pt_br.json"))
                   .read_text(encoding="utf-8"))
WOOD_PT = {w: vf_pt[f"block.village_furnishings.{w}_shutter"].split(" de ", 1)[1] for w in WOODS}
en = {"item.mca.outfit": "Outfit", "item.mca.outfit.desc": "Wear it at a wardrobe or dresser"}
pt = {"item.mca.outfit": "Roupa", "item.mca.outfit.desc": "Vista num guarda-roupa ou numa cômoda"}
for w in WOODS:
    en[f"block.mca.{w}_wardrobe"] = f"{WOOD_EN[w]} Wardrobe"
    en[f"block.mca.{w}_dresser"] = f"{WOOD_EN[w]} Dresser"
    pt[f"block.mca.{w}_wardrobe"] = f"Guarda-roupa de {WOOD_PT[w]}"
    pt[f"block.mca.{w}_dresser"] = f"Cômoda de {WOOD_PT[w]}"
pt.update({"block.mca.crimson_wardrobe": "Guarda-roupa Carmesim", "block.mca.crimson_dresser": "Cômoda Carmesim",
           "block.mca.warped_wardrobe": "Guarda-roupa Distorcido", "block.mca.warped_dresser": "Cômoda Distorcida"})
spec = Path(tempfile.gettempdir()) / "wardrobe_lang.json"
write_json(spec, {"en_us": en, "pt_br": pt})
subprocess.run([sys.executable, str(SCRATCH / "add_lang.py"), str(ASSETS / "lang"), str(spec)], check=True)
print("ok")
