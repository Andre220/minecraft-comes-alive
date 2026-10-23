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
:root{--bg:#f6f4ef;--fg:#1f2328;--muted:#5d6670;--card:#fff;--line:#d9d4c7;--accent:#2f7d4f;--warn:#a35a00}
@media (prefers-color-scheme:dark){:root{--bg:#16181b;--fg:#e6e6e6;--muted:#9aa3ad;--card:#1f2226;--line:#30343a;--accent:#5fbf83;--warn:#e0a050}}
*{box-sizing:border-box}body{margin:0;font:15px/1.55 system-ui,Segoe UI,sans-serif;background:var(--bg);color:var(--fg)}
nav{position:fixed;top:0;left:0;bottom:0;width:230px;overflow:auto;padding:18px;border-right:1px solid var(--line);background:var(--card)}
nav a{display:block;color:var(--fg);text-decoration:none;padding:3px 0}nav a:hover{color:var(--accent)}
nav h1{font-size:17px;margin:0 0 12px}main{margin-left:230px;padding:24px 36px;max-width:1300px}
h2{border-bottom:2px solid var(--line);padding-bottom:4px;margin-top:40px}h3{margin-top:26px}
table{border-collapse:collapse;width:100%;margin:10px 0;background:var(--card)}th,td{border:1px solid var(--line);padding:5px 8px;text-align:left;vertical-align:top}
th{background:rgba(127,127,127,.08)}td.y{color:var(--accent);font-weight:bold;text-align:center}td.n{color:var(--muted);text-align:center}
code{background:rgba(127,127,127,.15);padding:1px 4px;border-radius:3px}.muted{color:var(--muted)}
.chapter{background:var(--card);border:1px solid var(--line);border-radius:8px;padding:12px 16px;margin:10px 0}
.chapter p{margin:6px 0}.tag{font-size:12px;padding:1px 6px;border-radius:9px;background:rgba(127,127,127,.15)}
.grid{display:grid;grid-template-columns:repeat(auto-fill,minmax(96px,1fr));gap:8px}
.cell{background:var(--card);border:1px solid var(--line);border-radius:6px;padding:6px;text-align:center;font-size:11px;word-break:break-all}
.cell img{width:48px;height:48px;image-rendering:pixelated;object-fit:contain;display:block;margin:0 auto 4px}
#filter{width:100%;padding:8px;font-size:15px;margin:8px 0;border:1px solid var(--line);border-radius:6px;background:var(--card);color:var(--fg)}
.tabs button{margin:2px;padding:4px 10px;border:1px solid var(--line);border-radius:14px;background:var(--card);color:var(--fg);cursor:pointer}
.tabs button.on{background:var(--accent);color:#fff;border-color:var(--accent)}
@media (max-width:800px){nav{position:static;width:auto}main{margin:0;padding:16px}}
"""

JS = """
const cells=[...document.querySelectorAll('.cell')];let group='all';
function apply(){const q=document.getElementById('filter').value.toLowerCase();
cells.forEach(c=>{const ok=(group==='all'||c.dataset.group===group)&&c.dataset.name.includes(q);c.style.display=ok?'':'none';});}
document.getElementById('filter').addEventListener('input',apply);
document.querySelectorAll('.tabs button').forEach(b=>b.addEventListener('click',()=>{
document.querySelectorAll('.tabs button').forEach(x=>x.classList.remove('on'));b.classList.add('on');group=b.dataset.group;apply();}));
"""

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
<tr><td>WORK</td><td>expediente</td><td>Trabalhar na mesa (animação), produção da oficina, coletores, carregador, obras (construtor/mineiro), guardas patrulham.</td></tr>
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
<tr><td>Inspecionar</td><td>suportes de armadura, item frames, expositores do Items Displayed</td><td>lazer</td><td>Segura uma cópia do item exposto e vira de uma mão para a outra.</td></tr>
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
    ("squidwardHumanLikeModels", "Aldeões com corpo de jogador (visual)."),
    ("freshAnimationsIntegration", "Pacote MCA x Fresh Animations."),
    ("radialInteractMenu", "Menu radial de interação."),
]


def page(chapters, building_types, building_names, production, biome_blocks, groups, opportunities, toggles):
    out = []
    w = out.append
    w("<!doctype html><html lang='pt-BR'><head><meta charset='utf-8'><meta name='viewport' content='width=device-width,initial-scale=1'>")
    w("<title>MCA Dev Guide</title><style>" + CSS + "</style></head><body>")
    w("<nav><h1>MCA · guia do dev</h1>")
    for anchor, label in (("mca", "MCA original"), ("features", "Features da expansão"), ("behavior", "Comportamento"),
                          ("matrix", "Profissões × prédios × biomas"), ("buildings", "Tipos de prédio"), ("icons", "Ícones e balões")):
        w(f"<a href='#{anchor}'>{label}</a>")
    w("<p class='muted' style='font-size:12px;margin-top:20px'>Gerado por scripts/dev_docs.py</p></nav><main>")

    w("<h2 id='mca'>MCA original</h2>" + MCA_ORIGINAL)

    w("<h2 id='features'>Features da expansão</h2><p>Tudo vem desligado por padrão; ligue no <code>config/mca.json</code>.</p>")
    w("<table><tr><th>Flag</th><th>O que liga</th></tr>")
    for flag, text in FLAGS:
        w(f"<tr><td><code>{flag}</code></td><td>{html.escape(text)}</td></tr>")
    w("</table><h3>Pelo livro-guia (Vilas Vivas: Guia da Expansão)</h3>")
    for title, subtitle, texts in chapters:
        w(f"<div class='chapter'><b>{html.escape(title)}</b> {('· ' + html.escape(subtitle)) if subtitle else ''}")
        for text in texts:
            w(f"<p>{mc_format(text)}</p>")
        w("</div>")

    w("<h2 id='behavior'>Comportamento dos aldeões</h2>" + BEHAVIOR)

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

    w("<h2 id='matrix'>Profissões × prédios × biomas</h2>")
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
        w(f"<tr><td><b>{name}</b></td><td><span class='tag'>{group}</span></td><td>{html.escape(block or '—')}</td>"
          f"<td>{html.escape(', '.join(types) or '—')}</td><td>{html.escape(made)}</td><td>{html.escape(text)}</td>")
        for biome in BIOMES:
            if not block:
                w("<td class='n'>·</td>")
            elif block.startswith("mca:"):
                w("<td class='n'>colocar</td>")
            else:
                found = present(block, biome_blocks.get(biome, set()))
                w(f"<td class='{'y' if found else 'n'}'>{'✔' if found else '✘'}</td>")
        w("</tr>")
    w("</table>")
    beds = {b: sum(1 for x in biome_blocks.get(b, set()) if x.endswith("_bed")) for b in BIOMES}
    w("<p class='muted'>Cores de cama usadas por bioma (variedade das casas): " +
      ", ".join(f"{BIOME_NAMES[b]} {beds[b]}" for b in BIOMES) + ".</p>")

    w("<h2 id='buildings'>Tipos de prédio</h2><table><tr><th>Tipo</th><th>Nome</th><th>Visível</th><th>Prioridade</th><th>Blocos exigidos</th></tr>")
    for key, data in building_types.items():
        blocks = ", ".join(f"{v}× {k}" for k, v in data.get("blocks", {}).items()) or "—"
        w(f"<tr><td><code>{key}</code></td><td>{html.escape(building_names.get(key, ''))}</td><td>{'sim' if data.get('visible') else 'não'}</td>"
          f"<td>{data.get('priority', 0)}</td><td>{html.escape(blocks)}</td></tr>")
    w("</table>")

    w("<h2 id='icons'>Ícones e balões</h2>")
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
    w("</div>")
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
