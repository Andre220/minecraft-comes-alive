"""Builds the developer guide at docs/dev/index.html.

It reads the repository (building types, production recipes, the guide book texts) and the
Minecraft client jar from the Gradle cache (village structures for the biome matrix, textures for
the icon grid). Textures are copied to docs/dev/_assets/, which is git-ignored: Mojang's textures
must not be redistributed with the fork.

usage: python -I scripts/dev_docs.py [path to minecraft-client.jar]
"""
import gzip
import html
import io
import json
import os
import shutil
import struct
import sys
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
RES = ROOT / "common/src/main/resources"
OUT = ROOT / "docs/dev"
ASSETS = OUT / "_assets"
DEFAULT_JAR = Path.home() / ".gradle/caches/fabric-loom/26.2/minecraft-client.jar"
# the village structures live in the server data, found in the merged jar next to it
DATA_JAR_NAMES = ("minecraft-merged.jar", "minecraft-server.jar", "minecraft-client.jar")

BIOMES = ["plains", "desert", "savanna", "taiga", "snowy"]
BIOME_NAMES = {"plains": "Planície", "desert": "Deserto", "savanna": "Savana", "taiga": "Taiga", "snowy": "Neve"}

# profession -> (group, job site block, what it does in this fork)
PROFESSIONS = [
    ("Fazendeiro", "vanilla", "minecraft:composter", "Planta e colhe; a vila recebe parte da colheita real."),
    ("Pescador", "vanilla", "minecraft:barrel", "Pesca de verdade na margem perto do barril."),
    ("Pastor", "vanilla", "minecraft:loom", "Produz lã."),
    ("Açougueiro", "vanilla", "minecraft:smoker", "Cozinha carne; na padaria faz pão."),
    ("Pedreiro", "vanilla", "minecraft:stonecutter", "Pedregulho em tijolos de pedra."),
    ("Ferramenteiro", "vanilla", "minecraft:smithing_table", "Pé de cabra, machado, pá."),
    ("Ferreiro de armas", "vanilla", "minecraft:grindstone", "Espada de ferro."),
    ("Armeiro", "vanilla", "minecraft:blast_furnace", "Capacete; às vezes diamante."),
    ("Flecheiro", "vanilla", "minecraft:fletching_table", "Flechas."),
    ("Coureiro", "vanilla", "minecraft:cauldron", "Couro de pele de coelho."),
    ("Bibliotecário", "vanilla", "minecraft:lectern", "Livros; ensina na escola."),
    ("Clérigo", "vanilla", "minecraft:brewing_stand", "Redstone; raro lápis e pérola."),
    ("Cartógrafo", "vanilla", "minecraft:cartography_table", "—"),
    ("Guarda", "MCA", "", "Patrulha e protege; recrutado entre os sem profissão importante."),
    ("Arqueiro", "MCA", "", "Guarda à distância."),
    ("Fora da lei", "MCA", "", "Expulso da vila."),
    ("Aventureiro", "MCA", "", "Visitante trazido pela taverna."),
    ("Mercenário", "MCA", "", "Contratado para lutar."),
    ("Cultista", "MCA", "", "Raro, ligado aos livros do culto."),
    ("Construtor", "expansão", "", "Ergue obras e blueprints com material do armazém."),
    ("Mineiro", "expansão", "", "Escava obras; sem obra, trabalha na Peneira."),
    ("Lenhador", "expansão", "mca:chopping_block", "Derruba árvores e replanta."),
    ("Caçador", "expansão", "mca:hunting_rack", "Caça animais selvagens."),
    ("Forrageiro", "expansão", "mca:forage_basket", "Colhe frutas, cogumelos, cana, bambu."),
    ("Carregador", "expansão", "", "Leva produtos e insumos entre oficinas e armazém."),
]
# job site blocks that come in variants (a cauldron with water is still the leatherworker's)
BLOCK_KEYS = {"minecraft:cauldron": {"minecraft:cauldron", "#minecraft:cauldrons"}}


def present(block, names):
    if block == "minecraft:cauldron":
        return any(n.endswith("cauldron") for n in names)
    return block in names


PROFESSION_IDS = {"Fazendeiro": "farmer", "Pescador": "fisherman", "Pastor": "shepherd", "Açougueiro": "butcher",
                  "Pedreiro": "mason", "Ferramenteiro": "toolsmith", "Ferreiro de armas": "weaponsmith", "Armeiro": "armorer",
                  "Flecheiro": "fletcher", "Coureiro": "leatherworker", "Bibliotecário": "librarian", "Clérigo": "cleric",
                  "Cartógrafo": "cartographer"}


# ---------------------------------------------------------------- NBT (just enough to read structure palettes)

def read_nbt(data):
    stream = io.BytesIO(data)

    def read(fmt, size):
        return struct.unpack(">" + fmt, stream.read(size))[0]

    def string():
        length = read("H", 2)
        return stream.read(length).decode("utf-8", "replace")

    def payload(tag):
        if tag == 1:
            return read("b", 1)
        if tag == 2:
            return read("h", 2)
        if tag == 3:
            return read("i", 4)
        if tag == 4:
            return read("q", 8)
        if tag == 5:
            return read("f", 4)
        if tag == 6:
            return read("d", 8)
        if tag == 7:
            return stream.read(read("i", 4))
        if tag == 8:
            return string()
        if tag == 9:
            inner = read("b", 1)
            return [payload(inner) for _ in range(read("i", 4))]
        if tag == 10:
            compound = {}
            while True:
                inner = read("b", 1)
                if inner == 0:
                    return compound
                name = string()
                compound[name] = payload(inner)
        if tag == 11:
            return [read("i", 4) for _ in range(read("i", 4))]
        if tag == 12:
            return [read("q", 8) for _ in range(read("i", 4))]
        raise ValueError(f"unknown tag {tag}")

    tag = read("b", 1)
    string()
    return payload(tag)


def village_blocks(jar):
    """Block ids used anywhere in each biome's village structures."""
    blocks = {biome: set() for biome in BIOMES}
    for name in jar.namelist():
        parts = name.split("/")
        if len(parts) > 5 and name.startswith("data/minecraft/structure/village/") and name.endswith(".nbt") and parts[4] in blocks:
            try:
                nbt = read_nbt(gzip.decompress(jar.read(name)))
            except (OSError, ValueError, struct.error):
                continue
            palettes = nbt.get("palettes") or [nbt.get("palette", [])]
            for palette in palettes:
                for entry in palette:
                    blocks[parts[4]].add(entry.get("Name", ""))
    return blocks


# ---------------------------------------------------------------- repository data

def load_json_dir(path):
    result = {}
    for file in sorted(path.glob("*.json")):
        with open(file, encoding="utf-8") as f:
            result[file.stem] = json.load(f)
    return result


def lang(path):
    with open(path, encoding="utf-8") as f:
        return json.load(f)


def guide_chapters(book_lang):
    """The guide book's chapters as (title, subtitle, pages), from its lang keys."""
    import re
    java = (ROOT / "common/src/main/java/net/conczin/mca/expansion/ExpansionGuide.java").read_text(encoding="utf-8")
    pages = [int(n) for n in re.search(r"PAGES = \{([^}]*)\}", java).group(1).split(",")]
    chapters, index = [], 0
    for c, count in enumerate(pages):
        title = book_lang.get(f"mca.books.expansion_guide.chapter.{c}", "")
        subtitle = book_lang.get(f"mca.books.expansion_guide.chapter.{c}.subtitle", "")
        texts = [book_lang.get(f"mca.books.expansion_guide.{index + i}", "") for i in range(count)]
        index += count
        chapters.append((title, subtitle, texts))
    return chapters


def mc_format(text):
    """Book text with § codes to HTML."""
    out = html.escape(text).replace("\n", "<br>")
    out = out.replace("§l", "<b>").replace("§r", "</b>")
    return out.replace("§f", "")


# ---------------------------------------------------------------- textures

def copy_textures(jar):
    """Copies the icon groups and returns {group: [relative paths]}."""
    if ASSETS.exists():
        shutil.rmtree(ASSETS)
    groups = {}

    def take(group, prefix, target):
        names = sorted(n for n in jar.namelist() if n.startswith(prefix) and n.endswith(".png"))
        for name in names:
            dest = ASSETS / target / name[len(prefix):]
            dest.parent.mkdir(parents=True, exist_ok=True)
            dest.write_bytes(jar.read(name))
        groups[group] = [f"_assets/{target}/{n[len(prefix):]}" for n in names]

    take("Itens (vanilla)", "assets/minecraft/textures/item/", "vanilla/item")
    take("Partículas (vanilla)", "assets/minecraft/textures/particle/", "vanilla/particle")
    take("Efeitos (vanilla)", "assets/minecraft/textures/mob_effect/", "vanilla/mob_effect")
    take("HUD e ícones (vanilla)", "assets/minecraft/textures/gui/sprites/hud/", "vanilla/hud")
    take("Ícones de interface (vanilla)", "assets/minecraft/textures/gui/sprites/icon/", "vanilla/icon")

    mca = RES / "assets/mca/textures"
    for group, sub in (("Itens (MCA)", "item"), ("Partículas (MCA)", "particle"), ("Interface (MCA)", "gui"), ("Blocos (MCA)", "block")):
        files = sorted((mca / sub).rglob("*.png"))
        for file in files:
            dest = ASSETS / "mca" / sub / file.relative_to(mca / sub)
            dest.parent.mkdir(parents=True, exist_ok=True)
            shutil.copyfile(file, dest)
        groups[group] = [f"_assets/mca/{sub}/{f.relative_to(mca / sub).as_posix()}" for f in files]
    return groups


# ---------------------------------------------------------------- page

CSS = """
:root{--bg:#f4f1ea;--bg2:#ebe5d8;--fg:#1d2126;--muted:#5d6670;--card:#fffdf8;--line:#ddd5c4;--accent:#2f9e5b;
--grass:#3fa046;--sky:#2f86d0;--gold:#c98d0c;--rose:#d23f72;--violet:#7f51cf;--teal:#0f9898;--ember:#d6591f;
--shadow:0 1px 2px rgba(40,30,10,.06),0 6px 20px rgba(40,30,10,.07);--c:var(--accent)}
@media (prefers-color-scheme:dark){:root{--bg:#121417;--bg2:#181b20;--fg:#e8e6e1;--muted:#9aa3ad;--card:#1c2025;--line:#2d3239;
--accent:#5fd08a;--grass:#6fd474;--sky:#62aef0;--gold:#f0bf4c;--rose:#f07099;--violet:#a98af0;--teal:#3fcaca;--ember:#f28a52;
--shadow:0 1px 2px rgba(0,0,0,.3),0 8px 24px rgba(0,0,0,.35)}}
*{box-sizing:border-box}html{scroll-behavior:smooth;scroll-padding-top:16px}
body{margin:0;font:15px/1.6 system-ui,Segoe UI,sans-serif;color:var(--fg);background:var(--bg);
background-image:radial-gradient(circle at 1px 1px,rgba(127,127,127,.13) 1px,transparent 0);background-size:22px 22px}
#progress{position:fixed;top:0;left:0;height:3px;width:0;z-index:10;background:linear-gradient(90deg,var(--grass),var(--sky),var(--violet),var(--rose),var(--gold))}
nav{position:fixed;top:0;left:0;bottom:0;width:250px;overflow:auto;padding:22px 14px;background:var(--card);border-right:1px solid var(--line)}
nav h1{font-size:18px;margin:0 6px 4px;letter-spacing:-.01em}
nav h1 span{background:linear-gradient(90deg,var(--grass),var(--sky));-webkit-background-clip:text;background-clip:text;color:transparent}
nav .sub{margin:0 6px 18px;font-size:12px;color:var(--muted)}
nav a{display:flex;align-items:center;gap:10px;color:var(--fg);text-decoration:none;padding:7px 10px;margin:2px 0;border-radius:10px;
border-left:3px solid transparent;transition:background .2s,border-color .2s,transform .2s}
nav a .ic{display:grid;place-items:center;width:28px;height:28px;border-radius:8px;font-size:15px;flex:none;
background:color-mix(in srgb,var(--c) 16%,transparent)}
nav a:hover{background:color-mix(in srgb,var(--c) 10%,transparent);transform:translateX(3px)}
nav a.on{background:color-mix(in srgb,var(--c) 16%,transparent);border-left-color:var(--c);font-weight:600}
main{margin-left:250px;padding:28px 40px 80px;max-width:1340px}
.hero{position:relative;overflow:hidden;border-radius:20px;padding:34px 36px;margin-bottom:28px;color:#fff;box-shadow:var(--shadow);
background:linear-gradient(120deg,#2b8a50,#1d6fa8,#6a43b8,#b8365f);background-size:300% 300%;animation:flow 16s ease infinite}
.hero h1{margin:0 0 6px;font-size:34px;letter-spacing:-.02em}.hero p{margin:0;max-width:720px;opacity:.95;font-size:16px}
.hero .chips{margin-top:16px;display:flex;flex-wrap:wrap;gap:8px}
.hero .chips span{background:rgba(255,255,255,.18);border:1px solid rgba(255,255,255,.3);padding:3px 12px;border-radius:99px;font-size:13px}
.hero .cube{position:absolute;right:30px;top:50%;width:90px;height:90px;margin-top:-45px;border-radius:14px;opacity:.22;
background:conic-gradient(#fff 25%,transparent 0 50%,#fff 0 75%,transparent 0) 0 0/30px 30px;animation:bob 7s ease-in-out infinite}
@keyframes flow{0%,100%{background-position:0% 50%}50%{background-position:100% 50%}}
@keyframes bob{0%,100%{transform:rotate(12deg) translateY(-6px)}50%{transform:rotate(20deg) translateY(8px)}}
section.sec{position:relative;background:var(--card);border:1px solid var(--line);border-radius:18px;padding:6px 28px 24px;margin:0 0 28px;
box-shadow:var(--shadow);border-top:5px solid var(--c)}
.js section.sec{opacity:0;transform:translateY(24px);transition:opacity .6s ease,transform .6s ease}
.js section.sec.in{opacity:1;transform:none}
h2{display:flex;align-items:center;gap:12px;font-size:25px;margin:22px 0 12px;letter-spacing:-.01em}
h2 .ic{display:grid;place-items:center;width:44px;height:44px;border-radius:12px;font-size:22px;flex:none;
background:color-mix(in srgb,var(--c) 18%,transparent);box-shadow:inset 0 0 0 1px color-mix(in srgb,var(--c) 35%,transparent)}
h2:after{content:"";flex:1;height:3px;border-radius:3px;margin-left:6px;background:linear-gradient(90deg,var(--c),transparent)}
h3{margin:28px 0 8px;font-size:18px;padding-left:12px;border-left:4px solid var(--c)}
.tw{overflow-x:auto;margin:12px 0;border-radius:12px;border:1px solid var(--line)}
table{border-collapse:collapse;width:100%;background:var(--card);font-size:14px}
th,td{padding:8px 11px;text-align:left;vertical-align:top;border-bottom:1px solid var(--line)}
th{background:color-mix(in srgb,var(--c) 14%,var(--card));color:color-mix(in srgb,var(--c) 60%,var(--fg));
font-size:12px;text-transform:uppercase;letter-spacing:.05em;white-space:nowrap}
tr:nth-child(even) td{background:color-mix(in srgb,var(--c) 4%,transparent)}
tr:last-child td{border-bottom:0}
tr:hover td{background:color-mix(in srgb,var(--c) 10%,transparent)}
td:first-child{font-weight:600}
td.y,td.n{text-align:center;color:var(--muted)}
td.y span{display:inline-grid;place-items:center;width:24px;height:24px;border-radius:7px;color:#fff;background:var(--grass);font-size:13px}
code{font:13px ui-monospace,Consolas,monospace;background:color-mix(in srgb,var(--c) 11%,transparent);color:color-mix(in srgb,var(--c) 60%,var(--fg));
padding:1px 6px;border-radius:5px}
.muted{color:var(--muted)}
ul li{margin:6px 0}ul li::marker{color:var(--c)}
.chapters{display:grid;grid-template-columns:repeat(auto-fill,minmax(320px,1fr));gap:14px;margin-top:12px}
.chapter{background:var(--bg2);border:1px solid var(--line);border-radius:14px;padding:14px 18px 10px;
border-left:5px solid var(--k);transition:transform .2s,box-shadow .2s}
.chapter:hover{transform:translateY(-3px);box-shadow:var(--shadow)}
.chapter>b{display:block;font-size:16px;color:var(--k)}.chapter .st{font-size:13px;color:var(--muted)}
.chapter p{margin:7px 0;font-size:14px}
.tag{font-size:12px;font-weight:600;padding:2px 9px;border-radius:99px;white-space:nowrap;background:rgba(127,127,127,.15)}
.tag[data-g=vanilla]{background:color-mix(in srgb,var(--grass) 20%,transparent);color:color-mix(in srgb,var(--grass) 70%,var(--fg))}
.tag[data-g=MCA]{background:color-mix(in srgb,var(--rose) 20%,transparent);color:color-mix(in srgb,var(--rose) 70%,var(--fg))}
.tag[data-g=expansão]{background:color-mix(in srgb,var(--violet) 22%,transparent);color:color-mix(in srgb,var(--violet) 70%,var(--fg))}
.grid{display:grid;grid-template-columns:repeat(auto-fill,minmax(100px,1fr));gap:10px;margin-top:14px}
.cell{background:var(--bg2);border:1px solid var(--line);border-radius:12px;padding:8px 6px;text-align:center;font-size:11px;word-break:break-all;
transition:transform .15s,border-color .15s,box-shadow .15s}
.cell:hover{transform:translateY(-3px) scale(1.04);border-color:var(--c);box-shadow:var(--shadow)}
.cell img{width:48px;height:48px;image-rendering:pixelated;object-fit:contain;display:block;margin:0 auto 6px;transition:transform .2s}
.cell:hover img{transform:scale(1.25) rotate(-4deg)}
#filter{width:100%;padding:11px 14px;font:inherit;margin:10px 0;border:2px solid var(--line);border-radius:12px;background:var(--bg2);color:var(--fg);outline:none;transition:border-color .2s}
#filter:focus{border-color:var(--c)}
.tabs{display:flex;flex-wrap:wrap;gap:6px}
.tabs button{padding:5px 13px;border:1px solid var(--line);border-radius:99px;background:var(--bg2);color:var(--fg);cursor:pointer;font:inherit;font-size:13px;transition:all .15s}
.tabs button:hover{border-color:var(--c)}
.tabs button.on{background:var(--c);color:#fff;border-color:var(--c);box-shadow:0 3px 10px color-mix(in srgb,var(--c) 40%,transparent)}
@media (max-width:860px){nav{position:static;width:auto;border-right:0;border-bottom:1px solid var(--line)}main{margin:0;padding:16px}
section.sec{padding:4px 16px 18px}.hero{padding:24px 20px}.hero h1{font-size:26px}.hero .cube{display:none}}
@media (prefers-reduced-motion:reduce){*,*:before,*:after{animation:none!important;transition:none!important}html{scroll-behavior:auto}
.js section.sec{opacity:1;transform:none}}
"""

JS = """
const cells=[...document.querySelectorAll('.cell')];let group='all';
function apply(){const q=document.getElementById('filter').value.toLowerCase();
cells.forEach(c=>{const ok=(group==='all'||c.dataset.group===group)&&c.dataset.name.includes(q);c.style.display=ok?'':'none';});}
document.getElementById('filter').addEventListener('input',apply);
document.querySelectorAll('.tabs button').forEach(b=>b.addEventListener('click',()=>{
document.querySelectorAll('.tabs button').forEach(x=>x.classList.remove('on'));b.classList.add('on');group=b.dataset.group;apply();}));
document.querySelectorAll('table').forEach(t=>{const d=document.createElement('div');d.className='tw';t.before(d);d.append(t);});
const secs=[...document.querySelectorAll('section.sec')],links=[...document.querySelectorAll('nav a')],bar=document.getElementById('progress');
document.documentElement.classList.add('js');
if('IntersectionObserver' in window){
const reveal=new IntersectionObserver(es=>es.forEach(e=>{if(e.isIntersecting){e.target.classList.add('in');reveal.unobserve(e.target);}}),{threshold:.02});
secs.forEach(s=>reveal.observe(s));}else secs.forEach(s=>s.classList.add('in'));
function onScroll(){const h=document.documentElement;bar.style.width=(h.scrollTop/((h.scrollHeight-h.clientHeight)||1)*100)+'%';
let cur=secs[0];secs.forEach(s=>{if(s.getBoundingClientRect().top<innerHeight*.3)cur=s;});
links.forEach(a=>a.classList.toggle('on',a.hash==='#'+cur.id));}
addEventListener('scroll',onScroll,{passive:true});onScroll();
"""

# anchor, nav label, icon, section color
SECTIONS = [
    ("mca", "MCA original", "🏡", "var(--grass)"),
    ("features", "Features da expansão", "✨", "var(--violet)"),
    ("behavior", "Comportamento", "🧠", "var(--sky)"),
    ("looks", "Aparência, fala e blocos", "🎭", "var(--teal)"),
    ("matrix", "Profissões × prédios × biomas", "⚒️", "var(--ember)"),
    ("buildings", "Tipos de prédio", "🏛️", "var(--gold)"),
    ("icons", "Ícones e balões", "💬", "var(--rose)"),
]
CHAPTER_COLORS = ["var(--grass)", "var(--sky)", "var(--violet)", "var(--rose)", "var(--gold)", "var(--teal)", "var(--ember)"]

MCA_ORIGINAL = """
<p>O <b>Minecraft Comes Alive</b> troca os aldeões vanilla por pessoas: cada um tem nome, gênero, genética (altura, pele, cabelo,
olhos), personalidade, humor e memória de cada jogador (corações). Este fork mantém tudo isso e acrescenta a expansão.</p>
<table><tr><th>Sistema</th><th>Resumo</th></tr>
<tr><td>Relacionamentos</td><td>Corações por jogador sobem com conversa, presentes e ajuda. Namoro, noivado (anel de noivado), casamento (aliança), divórcio.</td></tr>
<tr><td>Família</td><td>Casais têm filhos (bebê como item que cresce), árvore genealógica, herança genética e traços (canhoto, daltônico, albinismo...).</td></tr>
<tr><td>Vila</td><td>A vila é definida pelo sino. Prédios são reconhecidos pelos blocos (tipos em <code>data/mca/building_types</code>), mostrados no Blueprint.</td></tr>
<tr><td>Patentes</td><td>Pela contribuição na vila: fora da lei, camponês, mercador, nobre, prefeito, monarca. Liberam impostos, regras e obras.</td></tr>
<tr><td>Impostos</td><td>Com armazém, a vila paga impostos periódicos (na expansão, a produção real substitui os itens do nada).</td></tr>
<tr><td>Tarefas (chores)</td><td>O jogador manda qualquer aldeão cortar árvore, colher, caçar ou pescar.</td></tr>
<tr><td>Guardas</td><td>Aldeões sem profissão importante são recrutados como guardas e arqueiros; ferreiro e arsenal melhoram o equipamento.</td></tr>
<tr><td>Morte e luto</td><td>Lápides, cemitério, aldeões que choram seus mortos, o Ceifador e o Bastão da Vida (ressurreição).</td></tr>
<tr><td>Infecção</td><td>Zumbis infectam aldeões; a enfermaria reduz a chance.</td></tr>
<tr><td>Livros</td><td>Livros de lore (morte, romance, família, ouro rosa, infecção, construção, culto) dados por conquistas; o registro civil e cartas.</td></tr>
<tr><td>Itens</td><td>Anéis, bebês, Editor de Aldeão, Bastão da Vida, apito, pente, agulha e linha, Blueprint, lápides, ouro rosa.</td></tr>
</table>
"""

BEHAVIOR = """
<p>Cada aldeão tem um <b>cérebro</b> (sistema de comportamento do Minecraft) com atividades. A <b>agenda</b> (editável na aba Agenda do
Editor de Aldeão) diz qual atividade vale em cada hora; a CORE roda sempre.</p>
<table><tr><th>Atividade</th><th>Quando</th><th>O que faz (pacote em <code>VillagerTasksMCA</code>)</th></tr>
<tr><td>CORE</td><td>sempre</td><td>Abrir portas, andar/teleportar até o destino, validar cama e mesa, procurar emprego (com aptidão), olhar e conversar, autodefesa.</td></tr>
<tr><td>IDLE</td><td>tempo livre</td><td>Prédio favorito, conversar, presentes, trocas, luto. Com <code>villagerRoutines</code>: rotina por oportunidades (abaixo) no lugar do passeio aleatório.</td></tr>
<tr><td>WORK</td><td>expediente</td><td>Trabalhar na mesa (animação), produção da oficina, coletores, carregador, obras (construtor/mineiro), guardas patrulham; com <code>villagerArcheryPractice</code>, arqueiros treinam no alvo.</td></tr>
<tr><td>MEET</td><td>encontro</td><td>Ir ao sino e socializar.</td></tr>
<tr><td>REST</td><td>noite</td><td>Ir para casa e dormir; sem cama, abrigo.</td></tr>
<tr><td>PLAY</td><td>crianças</td><td>Brincar de pega-pega; com <code>villagerSkills</code>, ir à escola de dia.</td></tr>
<tr><td>PANIC / HIDE / RAID</td><td>perigo</td><td>Fugir, se esconder, tocar o sino; guardas lutam.</td></tr>
<tr><td>GRIEVE</td><td>luto</td><td>Visitar a lápide de quem morreu.</td></tr>
</table>
<h3>Necessidades e fila de ações</h3>
<p>Com <code>villagerNeeds</code>: fome, sede, energia, social, lazer e segurança caem com o tempo e viram pensamentos no humor; quando críticas,
sobrepõem a agenda (comer, beber, dormir, se esconder). Com <code>villagerActionQueue</code>, o que a agenda, as necessidades e os pedidos do
jogador mandam entra numa fila mostrada sobre a cabeça (o mais urgente primeiro).</p>
<h3>Rotina por oportunidades (<code>villagerRoutines</code>)</h3>
<p>O mundo anuncia o que dá para fazer; o aldeão escolhe pela necessidade mais baixa, pela distância e pela variedade (evita repetir), com um
pouco de acaso. Código em <code>expansion/minds/routine</code>.</p>
<table><tr><th>Oportunidade</th><th>Onde</th><th>Necessidade</th><th>O que se vê</th></tr>
<tr><td>Sentar</td><td>escadas (metade de baixo) e lajes inferiores</td><td>energia</td><td>Senta de verdade (assento invisível) e olha quem passa.</td></tr>
<tr><td>Conversar</td><td>outro aldeão ocioso (cônjuge vale o dobro)</td><td>social</td><td>Vão um até o outro, se olham, partícula de conversa.</td></tr>
<tr><td>Praça</td><td>o sino</td><td>social</td><td>Fica na praça olhando o sino.</td></tr>
<tr><td>Ler</td><td>atril e estantes (melhor na biblioteca e na escola)</td><td>lazer</td><td>Segura um livro diante da estante.</td></tr>
<tr><td>Ouvir música</td><td>toca-discos tocando, bloco musical</td><td>lazer</td><td>Olha o toca-discos, notas musicais.</td></tr>
<tr><td>Aquecer-se</td><td>fogueira acesa</td><td>segurança</td><td>Fica junto ao fogo.</td></tr>
<tr><td>Carinho</td><td>gatos, cães e animais</td><td>lazer</td><td>Acompanha o animal, corações.</td></tr>
<tr><td>Inspecionar</td><td>suportes de armadura, item frames, expositores do Items Displayed, itens largados com o Tabletop</td><td>lazer</td><td>Segura uma cópia do item exposto e vira de uma mão para a outra.</td></tr>
<tr><td>Sentar (mods)</td><td>cadeiras e bancos do Tables &amp; Chairs 2</td><td>energia</td><td>Monta na cadeira como o jogador.</td></tr>\n<tr><td>Tocar</td><td>instrumento carregado ou exposto (Immersive Melodies)</td><td>lazer</td><td>O mod toca sozinho; quem chega perto de alguém tocando copia a música e toca junto. Vizinhos viram plateia (Ouvir).</td></tr>\n<tr><td>Banquete</td><td>blocos com porções (servings) ou mordidas (bites): banquetes e tortas do Farmer's Delight</td><td>fome</td><td>Pega uma porção e come em companhia.</td></tr>
<tr><td>Apreciar</td><td>flores, plantação madura</td><td>lazer</td><td>Olha a flor ou a plantação.</td></tr>
<tr><td>Água</td><td>beira d'água</td><td>lazer</td><td>Olha a água.</td></tr>
<tr><td>Em casa</td><td>junto da própria cama</td><td>segurança</td><td>Fica em casa.</td></tr>
<tr><td>Perto dos outros</td><td>o aldeão mais próximo</td><td>social</td><td>No pior caso, para junto de alguém.</td></tr>
</table>
<h3>Trabalho na economia (<code>villagerEconomy</code>)</h3>
<p>Coletores (lenhador, caçador, forrageiro, pescador, mineiro na peneira) trabalham num raio da estação e entregam a parte da vila no baú
da oficina; oficinas produzem por receitas de datapack; o carregador leva produtos ao armazém e insumos às oficinas. Com
<code>villagerSkills</code>, tudo isso dá habilidade, que acelera o trabalho.</p>
<h3>Pechincha (<code>villagerBarter</code>)</h3>
<p>Qualquer aldeão, amigo ou não, vende o que carrega por esmeraldas (mais barato para amigos) e, com esmeraldas próprias, compra comida e o
que a oficina dele precisa. Guarda um pouco de comida para si. Qualquer um pode perguntar o que ele carrega. Código em <code>expansion/barter</code>.</p>
"""

FLAGS = [
    ("villagerNeeds", "Necessidades: fome, sede, energia, social, lazer, segurança."),
    ("villagerActionQueue", "Fila de ações estilo The Sims sobre a cabeça."),
    ("villagerProperty", "Propriedade, cadeado, gazua, pé de cabra, crimes e testemunhas."),
    ("villagerWorkOrders", "Obras planejadas (câmera livre), construtores e mineiros, blueprints."),
    ("villagerEconomy", "Coletores, produção nas oficinas, carregador; impostos viram produção."),
    ("villagerSkills", "Habilidade por profissão, inteligência e escola, dieta, qualidade da casa, decisões."),
    ("villagerRequests", "Pedidos com recompensa em escrow, caça, Mural de Pedidos."),
    ("villageProgression", "Níveis das casas e oficinas, etapas da vila, aba Evolução."),
    ("villagerRoutines", "Tempo livre por oportunidades, sentar de verdade."),
    ("villagerBarter", "Pechincha: qualquer aldeão vende o que carrega e compra o que precisa (amigos pagam menos)."),
    ("villagerActionLabels", "Uma linha sobre a cabeça dizendo o que o aldeão está fazendo, vista de perto."),
    ("villagerArcheryPractice", "Arqueiros treinam em alvos de dia, entre as patrulhas."),
    ("villagerSpeechBubbles", "Falas num balão sobre a cabeça, letra por letra, com som (bipe, voz do MCA ou nada)."),
    ("villagerMouthAnimation", "Com Fresh Animations, a boca abre nas vogais e a língua mexe junto do balão."),
    ("villagerBeards", "Barbas na cor do cabelo em parte dos homens adultos; escolha no Editor de Aldeão."),
    ("squidwardHumanLikeModels", "Aldeões com corpo de jogador (visual)."),
    ("squidward3dLayers", "Camadas externas (chapéu, jaqueta, mangas, calça) em cubinhos 3D quando perto."),
    ("player3dLayers", "O mesmo efeito 3D nas camadas de roupa e cabelo dos jogadores."),
    ("squidwardGeneticHitbox", "A hitbox segue o tamanho genético e a idade do aldeão."),
    ("freshAnimationsIntegration", "Pacote MCA x Fresh Animations."),
    ("radialInteractMenu", "Menu radial de interação."),
]

LOOKS = """
<p>O que muda na cara e no jeito dos aldeões. Os visuais com corpo de jogador vêm de <code>useSquidwardModels</code> +
<code>squidwardHumanLikeModels</code>; com <code>freshAnimationsIntegration</code> quem anima é o Fresh Animations.</p>
<table><tr><th>Sistema</th><th>Flag</th><th>Como funciona</th></tr>
<tr><td>Balões de fala</td><td><code>villagerSpeechBubbles</code></td><td>A fala aparece letra por letra (<code>speechBubbleLettersPerSecond</code>), com o som de
<code>speechBubbleSound</code>; o aldeão para enquanto fala com você. Continua indo para o chat. Código em <code>expansion/speech</code>.</td></tr>
<tr><td>Boca falante</td><td><code>villagerMouthAnimation</code></td><td>Boca e língua no modelo do Fresh Animations, animadas pela variável EMF <code>mca_talk</code> no ritmo do balão.</td></tr>
<tr><td>Barbas</td><td><code>villagerBeards</code></td><td>40% dos homens nascem com barba, que aparece na vida adulta, tingida com a cor do cabelo. Linha própria e página de grade no Editor.</td></tr>
<tr><td>Olhos 6×6</td><td>sempre</td><td>A parte branca dos olhos ganhou espaço 6×6 na textura: rostos podem desenhar olhos detalhados; senão o olho 3×3 é ampliado.
<code>mcaEyeBlinking</code> desliga a piscada do MCA quando outro mod anima os olhos.</td></tr>
<tr><td>Camadas 3D</td><td><code>squidward3dLayers</code>, <code>player3dLayers</code></td><td>Roupa e cabelo viram cubinhos por pixel de perto, como os mods de skin 3D.</td></tr>
<tr><td>Tamanho por idade</td><td><code>squidward*Size</code>, <code>squidwardHeightVariation</code></td><td>Ajuste do tamanho de bebês, crianças e adolescentes e de quanto o gene de altura varia.</td></tr>
<tr><td>Animações de trabalho</td><td>sempre</td><td>Clipes em JSON (<code>assets/mca/animations/work</code>): martelar, cortar, mexer, escrever, tecer, agachar, remexer, carregar.
O lenhador corta, o construtor martela, o carregador leva um baú, o caçador atira com arco. Teste com <code>/mca-admin workAnimation</code>.</td></tr>
<tr><td>O que está fazendo</td><td><code>villagerActionLabels</code></td><td>Uma linha curta sobre a cabeça: dormindo, lendo, cortando árvore, indo para casa...</td></tr>
</table>
<h3>Blocos novos</h3>
<table><tr><th>Bloco</th><th>O que é</th></tr>
<tr><td>Venezianas (<code>mca:&lt;madeira&gt;_shutter</code>)</td><td>Uma por madeira, feitas do alçapão dela; abrem com animação. Os aldeões abrem de dia e fecham à noite (<code>daily_toggles/shutters.json</code>).</td></tr>
<tr><td>Janelas de batente</td><td>Duas folhas que abrem para fora como um portão.</td></tr>
<tr><td>Mural de Pedidos</td><td>Mostra os pedidos dos aldeões da vila (<code>villagerRequests</code>).</td></tr>
<tr><td>Cepo, cavalete de caça, cesto de forrageiro</td><td>Mesas de trabalho do lenhador, do caçador e do forrageiro (<code>villagerEconomy</code>).</td></tr>
</table>
<h3>Outros mods (só arquivos de dados)</h3>
<p>Nenhuma integração precisa de jar à parte: sem o mod, os ids não existem e o arquivo é ignorado. Feitas: Items Displayed, Tables &amp; Chairs 2,
Immersive Melodies (bandas tocam juntas), Farmer's Delight (banquetes, fogão, receitas da taverna) e Storage Delight, Beautify (persianas, luminárias,
livros, quadros) e Tabletop (itens largados em mesas: aldeões olham e mexem no que está em cima).</p>
"""


def page(chapters, building_types, building_names, production, biome_blocks, groups, opportunities, toggles):
    out = []
    w = out.append
    w("<!doctype html><html lang='pt-BR'><head><meta charset='utf-8'><meta name='viewport' content='width=device-width,initial-scale=1'>")
    w("<title>MCA Dev Guide</title><style>" + CSS + "</style></head><body><div id='progress'></div>")
    w("<nav><h1><span>MCA</span> · guia do dev</h1><p class='sub'>Gerado por scripts/dev_docs.py</p>")
    for anchor, label, icon, color in SECTIONS:
        w(f"<a href='#{anchor}' style='--c:{color}'><span class='ic'>{icon}</span>{label}</a>")
    w("</nav><main>")
    w("<header class='hero'><div class='cube'></div><h1>Minecraft Comes Alive · guia do dev</h1>"
      "<p>O que o MCA original já faz, o que a expansão acrescenta e como os aldeões pensam, trabalham e enfeitam a vila.</p>"
      f"<div class='chips'><span>{len(FLAGS)} flags</span><span>{len(PROFESSIONS)} profissões</span>"
      f"<span>{len(building_types)} tipos de prédio</span><span>{len(opportunities)} oportunidades</span>"
      f"<span>{sum(len(v) for v in groups.values())} ícones</span></div></header>")
    styles = {anchor: (icon, color) for anchor, _, icon, color in SECTIONS}

    def section(anchor, title):
        icon, color = styles[anchor]
        if anchor != SECTIONS[0][0]:
            w("</section>")
        w(f"<section class='sec' id='{anchor}' style='--c:{color}'><h2><span class='ic'>{icon}</span>{title}</h2>")

    section("mca", "MCA original")
    w(MCA_ORIGINAL)

    section("features", "Features da expansão")
    w("<p>Tudo vem desligado por padrão; ligue no <code>config/mca.json</code>.</p>")
    w("<table><tr><th>Flag</th><th>O que liga</th></tr>")
    for flag, text in FLAGS:
        w(f"<tr><td><code>{flag}</code></td><td>{html.escape(text)}</td></tr>")
    w("</table><h3>Pelo livro-guia (Vilas Vivas: Guia da Expansão)</h3><div class='chapters'>")
    for i, (title, subtitle, texts) in enumerate(chapters):
        w(f"<div class='chapter' style='--k:{CHAPTER_COLORS[i % len(CHAPTER_COLORS)]}'><b>{html.escape(title)}</b>"
          f"{('<span class=st>' + html.escape(subtitle) + '</span>') if subtitle else ''}")
        for text in texts:
            w(f"<p>{mc_format(text)}</p>")
        w("</div>")
    w("</div>")

    section("behavior", "Comportamento dos aldeões")
    w(BEHAVIOR)

    w("<h3>Oportunidades por datapack (<code>data/mca/opportunities</code>)</h3><p>Cada arquivo diz o que um bloco ou entidade oferece. "
      "Integrações com outros mods são só arquivos assim: sem o mod, os ids não existem e o arquivo é ignorado. Integrações que precisam "
      "de código usam <code>Opportunities.register</code> num jar à parte.</p>")
    w("<table><tr><th>Arquivo</th><th>Tipo</th><th>Blocos / entidades</th><th>Atração</th><th>Extras</th></tr>")
    for key, data in opportunities.items():
        blocks = data.get("blocks", [])
        if len(blocks) > 6:
            blocks = blocks[:5] + [f"… +{len(blocks) - 5}"]
        what = ", ".join(blocks + [e["type"] + (" " + "+".join(e.get("tags", [])) if e.get("tags") else "") for e in data.get("entities", [])]
                         + ["item " + i for i in data.get("items", [])])
        extras = ", ".join(f"{k}: {v}" for k, v in data.items() if k in ("properties", "rooms", "professions", "hold", "particle"))
        w(f"<tr><td><code>{key}</code></td><td>{data['kind']}</td><td>{html.escape(what)}</td><td>{data.get('appeal', 1.0)}</td><td>{html.escape(extras)}</td></tr>")
    w("</table>")
    w("<h3>Trocas do dia (<code>data/mca/daily_toggles</code>)</h3><p>Aldeões que passam perto (3 blocos, só em prédios da vila) "
      "deixam o bloco no estado da hora: venezianas e persianas abertas de dia, luminárias acesas à noite. Tempo em ticks do dia (0 = 6h, 12000 = 18h).</p>")
    w("<table><tr><th>Arquivo</th><th>Blocos</th><th>Propriedade</th><th>Entre from e to</th><th>Resto do dia</th></tr>")
    for key, data in toggles.items():
        blocks = data["blocks"]
        shown = ", ".join(blocks[:4] + ([f"… +{len(blocks) - 4}"] if len(blocks) > 4 else []))
        w(f"<tr><td><code>{key}</code></td><td>{html.escape(shown)}</td><td>{data['property']}</td>"
          f"<td>{data['active']} ({data['from']}–{data['to']})</td><td>{data['inactive']}</td></tr>")
    w("</table>")

    section("looks", "Aparência, fala e blocos")
    w(LOOKS)

    section("matrix", "Profissões × prédios × biomas")
    w("<p>Presença nos biomas = a mesa de trabalho aparece nas estruturas da vila vanilla daquele bioma (lido do jar do jogo). "
      "Profissões sem mesa (MCA e algumas da expansão) não dependem do bioma.</p>")
    w("<table><tr><th>Profissão</th><th>Origem</th><th>Mesa</th><th>Prédio</th><th>Produção</th><th>O que faz</th>")
    for biome in BIOMES:
        w(f"<th>{BIOME_NAMES[biome]}</th>")
    w("</tr>")
    for name, group, block, text in PROFESSIONS:
        keys = BLOCK_KEYS.get(block, {block})
        types = [building_names.get(t, t) for t, d in building_types.items() if block and keys & set(d.get("blocks", {}))]
        pid = PROFESSION_IDS.get(name, "")
        recipes = [r for r in production.values() if pid and r.get("profession", "").endswith(":" + pid)]
        made = ", ".join(sorted({o["item"].split(":")[1] for r in recipes for o in r.get("outputs", [])})) or "—"
        w(f"<tr><td><b>{name}</b></td><td><span class='tag' data-g='{group}'>{group}</span></td><td>{html.escape(block or '—')}</td>"
          f"<td>{html.escape(', '.join(types) or '—')}</td><td>{html.escape(made)}</td><td>{html.escape(text)}</td>")
        for biome in BIOMES:
            if not block:
                w("<td class='n'>·</td>")
            elif block.startswith("mca:"):
                w("<td class='n'>colocar</td>")
            else:
                found = present(block, biome_blocks.get(biome, set()))
                w("<td class='y'><span>✔</span></td>" if found else "<td class='n'>✘</td>")
        w("</tr>")
    w("</table>")
    beds = {b: sum(1 for x in biome_blocks.get(b, set()) if x.endswith("_bed")) for b in BIOMES}
    w("<p class='muted'>Cores de cama usadas por bioma (variedade das casas): " +
      ", ".join(f"{BIOME_NAMES[b]} {beds[b]}" for b in BIOMES) + ".</p>")

    section("buildings", "Tipos de prédio")
    w("<table><tr><th>Tipo</th><th>Nome</th><th>Visível</th><th>Prioridade</th><th>Blocos exigidos</th></tr>")
    for key, data in building_types.items():
        blocks = ", ".join(f"{v}× {k}" for k, v in data.get("blocks", {}).items()) or "—"
        w(f"<tr><td><code>{key}</code></td><td>{html.escape(building_names.get(key, ''))}</td><td>{'sim' if data.get('visible') else 'não'}</td>"
          f"<td>{data.get('priority', 0)}</td><td>{html.escape(blocks)}</td></tr>")
    w("</table>")

    section("icons", "Ícones e balões")
    w("<p>Dois jeitos de mostrar algo sobre a cabeça de um aldeão:</p><ul>"
      "<li><b>Ícone de item</b>: qualquer item do jogo vira um balão (é um <code>ItemDisplay</code> montado no aldeão, como em "
      "<code>NeedIndicators</code> e <code>ActionBubble</code>). Use o nome do arquivo como id, ex. <code>minecraft:bread</code>.</li>"
      "<li><b>Partícula</b>: as partículas vanilla e as do MCA (<code>mca:pos_interaction</code>, <code>mca:neg_interaction</code>) aparecem pelo nome.</li></ul>")
    total = sum(len(v) for v in groups.values())
    w(f"<input id='filter' placeholder='Filtrar por nome ({total} imagens)'><div class='tabs'><button class='on' data-group='all'>Tudo</button>")
    for group in groups:
        w(f"<button data-group='{html.escape(group)}'>{html.escape(group)} ({len(groups[group])})</button>")
    w("</div><div class='grid'>")
    for group, files in groups.items():
        for file in files:
            label = file.rsplit("/", 1)[-1][:-4]
            w(f"<div class='cell' data-group='{html.escape(group)}' data-name='{html.escape(label.lower())}'>"
              f"<img loading='lazy' src='{html.escape(file)}' alt=''>{html.escape(label)}</div>")
    w("</div></section>")
    w("<script>" + JS + "</script></main></body></html>")
    return "\n".join(out)


def main():
    jar_path = Path(sys.argv[1]) if len(sys.argv) > 1 else DEFAULT_JAR
    if not jar_path.exists():
        sys.exit(f"Minecraft client jar not found: {jar_path} (run a Fabric build first or pass its path)")
    OUT.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(jar_path) as jar:
        groups = copy_textures(jar)
    biome_blocks = {biome: set() for biome in BIOMES}
    for name in DATA_JAR_NAMES:
        data_jar = jar_path.parent / name
        if data_jar.exists():
            with zipfile.ZipFile(data_jar) as jar:
                biome_blocks = village_blocks(jar)
            if any(biome_blocks.values()):
                break
    pt = lang(RES / "assets/mca/lang/pt_br.json")
    building_names = {k[len("buildingType."):]: v for k, v in pt.items() if k.startswith("buildingType.") and k.count(".") == 1}
    html_text = page(guide_chapters(lang(RES / "assets/mca_books/lang/pt_br.json")),
                     load_json_dir(RES / "data/mca/building_types"), building_names,
                     load_json_dir(RES / "data/mca/production"), biome_blocks, groups,
                     load_json_dir(RES / "data/mca/opportunities"), load_json_dir(RES / "data/mca/daily_toggles"))
    (OUT / "index.html").write_text(html_text, encoding="utf-8")
    print(f"wrote {OUT / 'index.html'}: {sum(len(v) for v in groups.values())} images,",
          ", ".join(f"{b} {len(biome_blocks[b])} blocks" for b in BIOMES))


if __name__ == "__main__":
    main()
