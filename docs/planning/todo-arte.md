# TODO de arte: modelos e texturas

Este TODO cobre tudo o que foi adicionado desde o começo da expansão do MCA (commit `96d5abbdc`, "Add the village expansion"), mais os mods irmãos, e o que os planos novos vão pedir.

**Status:**
- **provisório:** usa textura vanilla e não tem modelo próprio;
- **programador:** arte gerada por script, funcional mas sem acabamento;
- **revisar:** a arte existe, mas vale melhorar;
- **planejado:** ainda não existe.

## MCA: blocos de trabalho (estações das profissões novas)
Hoje são todos um **cubo simples com textura vanilla**.

| Bloco | Profissão | Hoje | Status |
|---|---|---|---|
| `chopping_block` (cepo) | lenhador | `cube_column` com tronco de carvalho | provisório: modelo (cepo com machado) + textura |
| `forage_basket` (cesto) | coletor | `cube_bottom_top` com bambu e musgo | provisório: modelo (cesto) + textura |
| `hunting_rack` (varal de caça) | caçador | `cube_bottom_top` com abeto | provisório: modelo (varal com peles) + textura |
| `sifting_sieve` (peneira) | minerador (POI) | `cube_bottom_top` com andaime | provisório: modelo (peneira) + textura |
| `request_board` (quadro de pedidos) | pedidos | `cube_column`, lateral da mesa de cartografia | provisório: modelo (quadro) + textura (`board_assets.py` gera uma parte) |

## MCA: itens
| Item | Hoje | Status |
|---|---|---|
| `book_expansion_guide` (livro-guia) | reaproveita o modelo `book_blueprint` | provisório: textura própria |
| `crowbar` (pé de cabra) | textura própria | programador |
| `lockpick` (gazua) | textura própria | programador |
| `padlock` (cadeado) | textura própria | programador |
| `outfit` (traje) | renderer próprio (partícula de lã branca) | revisar o ícone no inventário |

## MCA: móveis e visual gerados por script
| O quê | Script | Status |
|---|---|---|
| Guarda-roupa, cômoda e espelho de pé, em 12 madeiras | `scripts/claude-tools/wardrobe_assets.py` | revisar: geometria gerada, madeira vanilla, puxadores de bloco de ferro |
| Espelho pequeno e de pé (vidro) | `mirror_assets.py` → `textures/block/mirror_glass.png` | programador |
| Barbas | `beards_png.py` | programador |
| Quadro de pedidos | `board_assets.py` | programador |

## Village Furnishings (`../village-furnishings`)
| O quê | Hoje | Status |
|---|---|---|
| Poltrona, cadeira, mesa, mesa de pedestal, prateleira e trono (12 madeiras cada) | `scripts/furniture_assets.py`, madeira vanilla | revisar |
| Venezianas e janelas de batente (12 madeiras) | gerado, madeira vanilla | revisar |
| Pias de cobre, ferro e ouro | texturas próprias (`*_sink_top/side/inner/bottom`) | programador |

## Fresh Animations (o caminho do usuário)
| O quê | Status |
|---|---|
| Boca e língua (`mca_mouth_inside`, `mca_tongue`), olhos fechados | modelos já nos `villager*.jem` |
| Olho 6×6 na textura do rosto | revisar: os rostos ainda usam o olho 3×3 ampliado; vale desenhar olhos detalhados |
| Zumbi-villager (`zombie_villager.jem`) | revisar, se for usar |

## Planejado (planos novos)
| O quê | Plano | Observação |
|---|---|---|
| **Registros da Cidade** (`city_records`): a base | [gestao-da-vila.md](gestao-da-vila.md) | provisório: hoje é um pedestal de 3 caixas com texturas do púlpito; o livro animado é o vanilla |
| **Emblemas da placa** | [gestao-da-vila.md](gestao-da-vila.md) | dispensado por enquanto: a placa mostra qualquer item, e os emblemas são itens vanilla; um atlas próprio fica opcional |
| **Placa de loja** (`*_shop_sign`) | [gestao-da-vila.md](gestao-da-vila.md) | programador: modelo de 5 caixas (`scripts/sign_assets.py` no Village Furnishings), tábua de madeira vanilla, braço com a textura da bigorna |
| **Ícones dos 6 atributos** (Força, Percepção, Vigor, Destreza, Sabedoria, Social) | [atributos-e-niveis.md](atributos-e-niveis.md) | 16×16 para a ficha e o painel |
| **Sprites da interface** (`gui/sprites/interact/panel`, `cell`, `cell_empty`, `cell_locked`) | [interface-e-conversas.md](interface-e-conversas.md) | programador: gerados por `gui_sprites.py` imitando o visual antigo; troque os PNGs para dar estilo |
| **Botões** (`gui/sprites/widget/button`, `button_highlighted`, `button_disabled`) | [interface-e-conversas.md](interface-e-conversas.md) | programador: gerados por `button_sprites.py` (ardósia escura, borda dourada no hover); troque os PNGs para dar estilo |
| **Ícones de traço próprios** | [tracos-sims.md](tracos-sims.md) | opcional: hoje cada traço usa um item vanilla |
| **Bengala do idoso** | [tracos-sims.md](tracos-sims.md) | opcional, ainda não decidida |
