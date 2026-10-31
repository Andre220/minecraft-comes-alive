"""Builds MCA's zombie_villager.jem for Fresh Animations from FA's own: same parts and ids (so FA's
zombie_villager_animations.jpm still drives it), but player-shaped boxes with the player texture layout MCA's skins
use, outer layers included, and the face planes on MCA's feature slots (copied from MCA's villager.jem).

usage: zombie_jem.py <Fresh Animations folder (extracted)>
"""
import copy
import json
import sys
from pathlib import Path

FA = Path(sys.argv[1]) / "assets/minecraft/optifine/cem"
MCA = Path(__file__).resolve().parents[2]
CEM = MCA / "common/src/main/resources/resourcepacks/mca_fresh_animations/assets/minecraft/optifine/cem"

zombie = json.loads((FA / "zombie_villager.jem").read_text(encoding="utf-8"))
villager = json.loads((CEM / "villager.jem").read_text(encoding="utf-8"))


def find(models, name):
    for m in models:
        if (m.get("part") or m.get("id")) == name:
            return m
        found = find(m.get("submodels", []), name)
        if found is not None:
            return found
    return None


def box(coordinates, uv, outer=False):
    b = {"coordinates": coordinates, "textureOffset": uv}
    if outer:
        b["sizeAdd"] = 0.25
    return b


# the head: player head and hat; no villager nose; the face from MCA's villager model, without its talking and
# sleeping extras (they need MCA's animation variables, which only the villager model sets up)
head = find(zombie["models"], "headwear")
head["boxes"] = [box([-4, 24, -4, 8, 8, 8], [0, 0]), {**box([-4, 24, -4, 8, 8, 8], [32, 0]), "sizeAdd": 0.5}]
face = copy.deepcopy(find(villager["models"], "face"))
extras = {"mca_mouth_inside", "mca_tongue", "mca_r_eye_closed", "mca_l_eye_closed"}


def strip(model):
    model["submodels"] = [strip(s) for s in model.get("submodels", []) if s.get("id") not in extras]
    if not model["submodels"]:
        del model["submodels"]
    return model


strip(face)
head["submodels"] = [face if s.get("id") == "face" else s for s in head.get("submodels", [])]
for name in ("nose", "rotation"):
    part = find(head["submodels"], name)
    if part is not None:
        part.pop("boxes", None)

# body, arms and legs: where FA puts them, with the player's texture regions and their outer layers
LAYOUT = {
    "body": ([-4, 12, -2, 8, 12, 4], [16, 16], [16, 32]),
    "right_arm": ([4, 12, -2, 4, 12, 4], [40, 16], [40, 32]),
    "left_arm": ([-8, 12, -2, 4, 12, 4], [32, 48], [48, 48]),
    "right_leg": ([0, 0, -2, 4, 12, 4], [0, 16], [0, 32]),
    "left_leg": ([-4, 0, -2, 4, 12, 4], [16, 48], [0, 48]),
}
for name, (coordinates, inner, outer) in LAYOUT.items():
    part = find(zombie["models"], name)
    part["boxes"] = [box(coordinates, inner), box(coordinates, outer, True)]

out = CEM / "zombie_villager.jem"
out.write_text(json.dumps(zombie, indent=2) + "\n", encoding="utf-8", newline="\n")
print("wrote", out)
