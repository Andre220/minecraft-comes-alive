# Interface: informação atrás das conversas e visual editável (WIP)

> **Status: em andamento.** Planejado em 2026-10-28; parte já implementada (ver Estado).

## Estado (2026-10-28)
Feito, sem commit e sem teste em jogo, com a flag `villagerInfoInTalk` (desligada por padrão):
- **Ação de diálogo nova:** `"show": "<painel>"`, em `expansion/talk/TalkPanels`, com o pacote `Reveal`.
- **Perguntas em `main.json`:**
  - "Como você está?" → humor e necessidades;
  - "Onde você trabalha?" → habilidade e faixa da agenda do dia;
  - "Me fale de você" → traços;
  - "Quem são seus amigos?" → amigos e rivais;
  - "No que você é bom?" → atributos.
- **Memória:** o que foi revelado fica guardado por villager enquanto o jogo está aberto.

Visual editável (2026-10-28):
- Os fundos e células da tela de interação viraram sprites nine-slice em `assets/mca/textures/gui/sprites/interact/`: `panel`, `cell`, `cell_empty` e `cell_locked`.
- Um resource pack troca esses PNGs (e o `.mcmeta`) e muda o visual sem compilar. O padrão imita o visual antigo e é gerado por `scripts/claude-tools/gui_sprites.py`.
- **Botões:** em toda tela do MCA, os botões usam os sprites `mca:widget/button`, `button_highlighted` e `button_disabled` no lugar do cinza vanilla. O `MixinAbstractButton` troca o sprite só quando a tela aberta é do MCA. Com a config do cliente `mcaButtonStyle` desligada, voltam ao vanilla e seguem os resource packs. Os PNGs vêm de `scripts/claude-tools/button_sprites.py`.
- Os tooltips já eram os vanilla.
- As telas novas da prefeitura usam botões e texto vanilla.

Reorganização (2026-10-28):
- **Cartão do villager** no alto à esquerda (nome, idade ou profissão, humor depois de contado), num painel.
- **Ícones** de `icons.json` numa linha logo abaixo: corações, casamento, pais, genes, humor/análise e presente. Antes eles ficavam por cima dos amigos e dos atributos.
- **Blocos empilhados** com espaçamento: traços, amigos (agora um painel; a lista completa no hover), atributos e agenda.
- **Ações:** continuam no menu circular, no centro.
- **Necessidades:** continuam embaixo à esquerda.

## Onde cada coisa se edita hoje

| O quê | Onde | Sem código? |
|---|---|---|
| Botões: quais aparecem, posição, condições | `assets/mca/api/gui/*.json` (`interact`, `main`, `work`, `professions`, `radial_*`, `icons`) | sim, até por resource pack |
| Textos | `assets/mca/lang`, `assets/mca_dialogue/lang` | sim |
| Perguntas e respostas da conversa | `data/mca/dialogues/*.json` | sim, por datapack |
| Painéis (necessidades, traços, opiniões, caixa de diálogo) | código, com `fill()` e cores fixas em `client/gui/InteractScreen.java` | não |

**Por que os mods e packs de UI não mudam o MCA?**
- A UI vanilla é feita de sprites, e os packs trocam esses sprites.
- Os botões do MCA já são vanilla e mudam junto.
- Os painéis próprios do MCA são desenhados com cores fixas, então nenhum pack os alcança.
- A dificuldade é só das telas do MCA.

## 1. Visual editável
- Trocar os `fill()` por `blitSprite`, **de preferência com sprites vanilla**: fundo do tooltip, `widget/slot`, fundos de `container/...`. Assim os packs e mods de UI reestilizam o MCA sozinhos.
- Onde não houver sprite vanilla equivalente, usar sprites próprios nine-slice em `assets/mca/textures/gui/sprites/` (painel, célula de traço, barra de necessidade), com `.mcmeta` de escala. Editáveis por PNG ou resource pack.
- **Esforço:** baixo a médio. É uma troca mecânica, tela por tela: `InteractScreen`, `TraitTooltips`, tooltips de opinião, caixa de diálogo, editor.

## 2. Informação atrás de perguntas
Hoje o `InteractScreen` mostra tudo sempre (`drawTextPopups`):
- nome, idade ou profissão com habilidade, humor;
- faixa de traços (`drawTraits`) e opiniões (`drawOpinions`);
- fila de ações (`drawQueue`);
- necessidades (`drawNeeds`);
- ícones de `icons.json`: casamento, corações, genes, pais, análise.

**Proposta:** a conversa revela os painéis.

| Pergunta | Revela |
|---|---|
| "Como você está?" | humor e necessidades |
| "Onde você trabalha?" | profissão, posto, habilidade e agenda do dia |
| "Me fale de você" | traços (e a voz) |
| "Quem são seus amigos?" | amigos e rivais |
| "No que você é bom?" | atributos e nível ([atributos-e-niveis.md](atributos-e-niveis.md)) |

**Como funciona:**
1. Uma ação de diálogo nova, `"show": "needs"`, registrada em `Actions.register` (`resources/data/dialogue/Actions.java`).
2. Ela manda um pacote S2C `InteractionDialogueShowPanel(panel)`.
3. Em `ClientHandlerImpl`, o pacote chama `InteractScreen.revealPanel(panel)`, que liga a flag que `drawTextPopups` confere antes de desenhar cada bloco.
4. A partir daí, cada pergunta nova é **só JSON**.

**Cuidados:**
- As respostas precisam de `next` (uma fala ou a volta para `main`). Sem `next`, o MCA põe `quit` e a conversa fecha logo depois de mostrar o painel.
- As perguntas mais íntimas podem exigir corações, pela `constraints`. Um estranho não conta quem são os amigos dele.
- A agenda precisa de um `ScheduleTimelineWidget` somente leitura. Hoje ele sempre pinta ao clicar; basta uma flag.
- Config "mostrar tudo sempre", para quem prefere o jeito atual.

## 3. Nova organização da tela
- **Esquerda:** cartão do villager (nome, idade, profissão, humor resumido).
- **Direita:** ações, no menu circular ou nos botões.
- **Centro / embaixo:** os painéis revelados pela conversa, que ficam abertos até sair da tela.
- **Ícones de casamento, corações e pais:** continuam no hover, como hoje.

## Perguntas em aberto
- Um painel revelado fica lembrado para aquele villager (memória por jogador) ou só até fechar a tela?
- O editor de villager (`VillagerEditorScreen`) também ganha o visual novo, ou só a interação?
