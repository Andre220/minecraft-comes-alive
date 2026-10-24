"""Appends a chapter (pt and en) to guide_book.py.

usage: add_chapter.py <guide_book.py> <chapter json: {"pt": [title, subtitle, [pages]], "en": [...]}>
Pages may start with "**Heading** " to get a bold heading line.
"""
import json
import sys

path, spec = sys.argv[1], sys.argv[2]
with open(spec, encoding="utf-8") as f:
    chapter = json.load(f)


def render(title, subtitle, pages):
    lines = [f"    ({json.dumps(title, ensure_ascii=False)}, {json.dumps(subtitle, ensure_ascii=False)}, ["]
    for page in pages:
        if page.startswith("**"):
            end = page.index("**", 2)
            heading, body = page[2:end], page[end + 2:].lstrip()
            lines.append(f"        f\"{{B}}{heading}{{R}}\\n\" + {json.dumps(body, ensure_ascii=False)},")
        else:
            lines.append(f"        {json.dumps(page, ensure_ascii=False)},")
    lines.append("    ]),")
    return "\n".join(lines) + "\n"


with open(path, encoding="utf-8", newline="") as f:
    text = f.read()
pt_end = text.index("\n]\n\nEN = [")
text = text[:pt_end + 1] + render(*chapter["pt"]) + text[pt_end + 1:]
en_end = text.index("\n]\n\nLIMIT")
text = text[:en_end + 1] + render(*chapter["en"]) + text[en_end + 1:]
with open(path, "w", encoding="utf-8", newline="") as f:
    f.write(text)
print("added", chapter["pt"][0])
