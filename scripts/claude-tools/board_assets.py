"""Writes the request board's assets, recipe, loot table and tags."""
import json
import os
import sys

RES = sys.argv[1] + "/common/src/main/resources"


def w(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        json.dump(obj, f, indent=2)
        f.write("\n")


A = RES + "/assets/mca/"
D = RES + "/data/mca/"
# placeholder look from vanilla textures, to be replaced by a Blockbench model
w(A + "models/block/request_board.json", {"parent": "minecraft:block/cube_column", "textures": {
    "end": "minecraft:block/oak_planks", "side": "minecraft:block/cartography_table_side1"}})
w(A + "blockstates/request_board.json", {"variants": {"": {"model": "mca:block/request_board"}}})
w(A + "items/request_board.json", {"model": {"type": "minecraft:model", "model": "mca:block/request_board"}})
w(D + "loot_table/blocks/request_board.json", {"type": "minecraft:block", "pools": [{"rolls": 1, "entries": [
    {"type": "minecraft:item", "name": "mca:request_board"}], "conditions": [{"condition": "minecraft:survives_explosion"}]}]})
w(D + "recipe/request_board.json", {"type": "minecraft:crafting_shaped", "pattern": ["PPP", "WWW"],
    "key": {"P": "minecraft:paper", "W": "#minecraft:planks"}, "result": {"id": "mca:request_board"}})

axe = RES + "/data/minecraft/tags/block/mineable/axe.json"
with open(axe, encoding="utf-8") as f:
    tag = json.load(f)
if "mca:request_board" not in tag["values"]:
    tag["values"].append("mca:request_board")
w(axe, tag)
print("ok")
