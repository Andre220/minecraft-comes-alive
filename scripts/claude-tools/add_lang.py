"""Adds (or replaces) keys in MCA's en_us/pt_br lang files without reformatting them.

usage: add_lang.py <lang dir> <json file with {"en_us": {...}, "pt_br": {...}}>
"""
import json
import sys

lang_dir, spec = sys.argv[1], sys.argv[2]
with open(spec, encoding="utf-8") as f:
    additions = json.load(f)

for lang, entries in additions.items():
    path = f"{lang_dir}/{lang}.json"
    with open(path, encoding="utf-8", newline="") as f:
        text = f.read()
    nl = "\r\n" if "\r\n" in text else "\n"
    lines = text.rstrip().split(nl)
    keys = {json.dumps(k, ensure_ascii=False) for k in entries}
    lines = [line for line in lines if line.strip().split(":")[0] not in keys]
    body = nl.join(lines).rstrip()
    assert body.endswith("}")
    body = body[:-1].rstrip().rstrip(",")
    added = "".join("," + nl + "  " + json.dumps(k, ensure_ascii=False) + ": " + json.dumps(v, ensure_ascii=False)
                    for k, v in entries.items())
    result = body + added + nl + "}" + nl
    json.loads(result)
    with open(path, "w", encoding="utf-8", newline="") as f:
        f.write(result)
    print(lang, len(entries))
