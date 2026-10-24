"""Simulates Minecraft's book word wrap (114 px, 14 lines) for the guide pages."""
import json
import sys

ROOT = sys.argv[1]
# glyph advance in px (default font), including the 1 px spacing
NARROW = {"i": 2, "l": 3, "!": 2, "'": 2, ".": 2, ",": 2, ":": 2, ";": 2, "|": 2, "t": 4, "I": 4, " ": 4,
          "f": 5, "k": 5, "(": 4, ")": 4, "[": 4, "]": 4, "\"": 4, "*": 4, "<": 5, ">": 5, "{": 4, "}": 4,
          "@": 7, "~": 7}


def width(text, bold):
    return sum(NARROW.get(c, 6) + (1 if bold else 0) for c in text)


def lines(page):
    count = 0
    for paragraph in page.split("\n"):
        bold = False
        line = 0
        for word in paragraph.split(" "):
            plain = word.replace("§l", "").replace("§r", "")
            if "§l" in word:
                bold = True
            w = width(plain, bold)
            add = w if line == 0 else w + 4
            if line and line + add > 114:
                count += 1
                line = w
            else:
                line += add
            if "§r" in word:
                bold = False
        count += 1
    return count


worst = 0
for lang in ("pt_br", "en_us"):
    data = json.load(open(f"{ROOT}/assets/mca_books/lang/{lang}.json", encoding="utf-8"))
    for key, value in data.items():
        if key.startswith("mca.books.expansion_guide.") and key.split(".")[-1].isdigit():
            n = lines(value)
            worst = max(worst, n)
            if n > 14:
                print(lang, key, n, "lines:", value[:50])
print("worst", worst)
