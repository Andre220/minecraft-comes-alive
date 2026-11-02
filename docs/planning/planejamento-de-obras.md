# Planejamento de obras 2: rascunho, catálogo, seções de blueprint e formas

Data: 2026-11-01. Pedido do Andre, depois de usar a tela de planejamento (Cavar / Construir / Blueprint / Capturar):

1. **Cavar:** marcar várias áreas livremente sem que nada aconteça; um botão confirma e manda cavar. Sair da
   ferramenta limpa as áreas.
2. **Construir:** catálogo de blocos no estilo do inventário criativo (tecla E), com abas, num painel lateral;
   planejar blocos que não estão na mão.
3. **Fantasmas:** em vez dos cubos coloridos, o próprio bloco que vai surgir, quase transparente (~5%).
4. **Blueprint abaixo do chão:** rolagem para afundar o blueprint, para prédios com porão.
5. **Movimento livre e confirmar:** o que foi posto (blueprint ou construção) fica como rascunho; dá para voar em volta,
   clicar numa peça para pegá-la de novo e editar, e só então confirmar.
6. **Navegador de blueprints:** grade com abas no topo (estilo Fallout 76), no lado esquerdo.
7. **Seções:** criar seções próprias (com ícone) para peças reutilizáveis (janela com floreira, cama montada...) e
   escolher a seção ao capturar.
8. **Captura com confirmação:** ajustar a caixa olhando em volta antes de salvar, para não esquecer bloco.
9. **Formas de construção:** ferramentas de construção em massa mais finas: linha, repetir arrastando. Hoje escadas saem viradas
   para o lado errado. Também um **pincel** (arrastar pintando blocos) e **extrudar** o traço com a rolagem, para
   erguer muralhas em volta da vila rapidamente.
10. **Textos e legendas:** botão para mostrar/esconder a ajuda; cores diferentes para a tecla e para o que ela faz.
11. **Excluir blueprint:** um botão para apagar; uma captura errada fica para sempre.
12. **Demolir tudo:** opção para cavar também baús, estantes, sinos (e o que tiver dentro).

**Estado (2026-11-01):** fases 1 a 5 implementadas e compiladas, ainda sem teste em jogo; a fase 6 (fantasmas com o
modelo do bloco) não foi começada. Fica no recurso de obras (`villagerWorkOrders`), código em
`client/gui/workorder` e `server/world/data/workorder`.

O que ficou diferente do desenho abaixo:
- Parede/Piso não viraram formas próprias: uma Caixa de espessura 1 faz o mesmo.
- Renomear blueprint pelo menu não entrou (o id é o nome do arquivo; renomear seria capturar de novo).
- O rascunho e a barra de confirmar ficam no canto superior direito, abaixo de Obras/Pronto.
- Peças pegadas de volta giram em torno do canto, não do centro.
- Seções ficam em `generated/mca/blueprint_sections.json`.
- Depois da primeira avaliação: cartões com miniatura isométrica (`BlueprintThumbnails`, cor de mapa de cada bloco,
  três faces sombreadas) e materiais numa grade tipo inventário abaixo da biblioteca (`MaterialGrid`): quantidade
  necessária no canto, verde se o armazém tem, vermelho se falta; com Construir mostra o rascunho inteiro.
- Teclas novas: H ajuda, F inverter, T bloco no topo do traço, N novo traço, [ ] tamanho do pincel, Alt+arrastar
  apaga, Del remove a peça, Page Up/Down sobe/desce.

---

## 0. O que já existe

| Peça | Onde | O que faz hoje |
|---|---|---|
| Tela | `WorkPlanningScreen` (1130 linhas) | Câmera livre, 4 ferramentas, painel de obras (Tab), lista de blueprints à esquerda |
| Seleção | `PlanningSelection` | **Uma** caixa: clique, arrasta, rolagem estica para cima/baixo; o segundo clique já manda a obra |
| Desenho no mundo | `WorkOrderOverlay` | Gizmos (cubos coloridos) refeitos a cada tick; até 6000 fantasmas roxos |
| Construir | `CreateWorkOrderMessage` | Enche uma caixa com **um** bloco, **sempre no estado padrão** (`defaultBlockState`): é por isso que escadas saem viradas |
| Paleta | `rebuildPalette` | Até 12 blocos: escolhidos com o botão do meio, inventário, armazém, 6 fixos do Editor |
| Blueprint | `PlaceBlueprintMessage` | Clique já cria a obra; rolagem sobe/afunda **±16** (já existe, mas o fantasma some dentro da terra) |
| Ordem de blueprint | `WorkOrder.plan` | Mapa posição → estado, derivado do id do blueprint; construtores limpam e erguem |
| Biblioteca | `BlueprintLibrary` | Arquivos `.nbt` (`mca:blueprints/<nome>`) no `generated/` do mundo + 4 do mod; sem apagar, sem categorias |
| Cavar | `WorkOrderBlocks.needsDigging` | Nunca cava bloco com block entity (baú, cama, placa) nem segura lava |
| Criativo | `CreativeWork`, `placeNow` | Tudo é feito na hora, aos pedaços por tick |

---

## 1. A ideia central: o **rascunho**

Hoje cada ferramenta manda a obra no segundo clique. Passa a existir um **rascunho** do lado do cliente, comum a todas
as ferramentas, que só vai ao servidor no botão **Confirmar obra** (ícone: livro e pena, Enter também confirma).

```
Rascunho
├── áreas de escavação      (caixas; podem se sobrepor)
└── peças                   (cada uma: mapa posição → estado de bloco + de onde veio)
    ├── preenchimento       (caixa cheia de um bloco, com orientação)
    ├── blueprint           (id + origem + giro; editável como um todo)
    └── forma               (linha, repetição, cópia...)
```

- **Cavar (#1):** cada clique-arrasta-clique adiciona uma área ao rascunho (vermelha tracejada). Clique direito numa área
  a remove. Trocar de ferramenta pergunta nada e **descarta só as áreas de escavação** (como pedido); peças continuam.
- **Peças (#5):** colocar um blueprint ou uma caixa cria uma peça. Clicar numa peça já posta a **pega de volta**: ela
  segue o cursor de novo, gira com R, sobe/desce com a rolagem, Delete a apaga. Clicar de novo a solta.
- **Barra do rascunho** (canto inferior direito): "Rascunho: 2 áreas, 3 peças · 412 blocos" + **Confirmar** +
  **Descartar**. Fechar a tela com rascunho pergunta "Descartar o rascunho?".
- Conflitos: peça sobre peça → a última vence naquela posição (aviso em laranja). Área de escavação sob uma peça → a
  peça vence.

### Servidor: ordem com plano próprio

Novo `CommitPlanMessage(áreas, peças)`. Cada peça vira uma `WorkOrder` do tipo `BLUEPRINT` com **plano embutido**
(palette + posições, salvo no NBT da ordem) em vez de só o id. Isso resolve as escadas: o estado completo (direção,
metade, formato) viaja e é construído como está. As áreas viram ordens `DIG` como hoje. Limites: `workOrderMaxVolume`
por peça e um teto total por confirmação. Criativo continua fazendo tudo na hora.

---

## 2. Construir com catálogo (#2)

Painel do lado **direito** (o esquerdo fica com os blueprints), cerca de 9×7 espaços, rolável:

- **Abas no topo** com os ícones das abas criativas do jogo (`CreativeModeTabs`), mais três próprias à frente:
  **Na vila** (armazém + inventário, com quantidade no canto), **Recentes**, **Busca** (caixa de texto).
- Qualquer bloco pode ser planejado. Fora do criativo, o espaço mostra em amarelo quanto o armazém tem; os construtores
  esperam o material como já fazem hoje com blueprints.
- Botão do meio no mundo continua escolhendo o bloco (vai para Recentes).
- **Orientação:** o bloco é posto como um jogador o poria, olhando da câmera para a face clicada
  (`getStateForPlacement` com um contexto que usa o giro da câmera). R gira, F inverte em cima/embaixo (escadas,
  lajes). O fantasma mostra o resultado antes.

## 3. Fantasmas de verdade (#3)

Desenhar o modelo do próprio bloco, translúcido, no lugar onde vai surgir. Opacidade padrão **5%** como pedido, com
um controle deslizante 5–60% na tela (e na config), porque 5% some contra o céu claro. Blocos que precisam sair antes
continuam com contorno vermelho.

- Precisa de um gancho de renderização do mundo, que o mod ainda não tem: evento de fim de renderização translúcida no
  Fabric e `RenderLevelStageEvent` no NeoForge, chamando um `GhostBlockRenderer` comum. É a parte de maior risco
  (API de render do 26.2 é nova); se não der, volta para os gizmos coloridos sem quebrar nada.
- Teto de blocos por quadro (como os 6000 de hoje) e cache da malha: só refaz quando o rascunho muda.

## 4. Abaixo do chão (#4)

A rolagem para afundar **já existe** (±16). O problema real é que o fantasma fica escondido dentro da terra. Mudanças:

- Limite passa a ser a altura do blueprint + 16 para baixo.
- A parte enterrada é desenhada **através do terreno** (só contorno, sem teste de profundidade) e um plano fino marca o
  nível do chão.
- Texto "Porão: 4 camadas abaixo do chão" e quantos blocos de terra serão escavados.
- Page Up / Page Down também sobem e descem, para quem usa a rolagem para voar.

## 5. Navegador de blueprints (#6) e seções (#7)

Painel do lado **esquerdo**, no lugar da lista de botões:

```
[🏠 Prédios][🪟 Peças][🛏 Móveis][★ Todos][ + ]      ← abas (ícone, nome no tooltip)
┌──────┬──────┬──────┐
│ ícone│ ícone│ ícone│   cartões: ícone, nome, 12×9×11
│ nome │ nome │ nome │   clique: escolhe · botão direito: menu
└──────┴──────┴──────┘   (mover para seção, renomear, excluir)
[🔍 busca...........]
```

- **Seção** = nome + ícone (um item escolhido no catálogo do #2) + ordem. Guardadas por mundo em
  `generated/mca/blueprints/sections.json`, junto com a seção de cada blueprint. Os do mod caem em "Prédios".
- **+** cria uma seção (nome + ícone). Ao capturar, o aviso de nome ganha um seletor de seção.
- Seção vazia pode ser apagada; os blueprints dela voltam para "Todos".

## 6. Captura com confirmação (#8)

A caixa da captura vira rascunho também: depois do arrasto ela **fica**, dá para voar em volta, e

- passar o mouse numa face a destaca; a rolagem empurra/puxa **aquela face** (hoje só estica a altura);
- blocos sólidos **encostados do lado de fora** da caixa piscam em amarelo ("3 blocos encostam na caixa por fora"):
  é exatamente o bloco esquecido;
- **Salvar blueprint** abre o nome + seção; Esc descarta.

## 7. Formas de construção (#9)

Não são itens: são botões da ferramenta Construir, na segunda fileira da tela de planejamento.

Sub-ferramentas dentro de Construir (segunda fileira), todas gerando **peças** do rascunho, com orientação completa:

| Forma | Uso |
|---|---|
| Caixa | O de hoje: enche a caixa |
| Linha | Clique no início e no fim; segue o eixo dominante ou diagonal |
| Parede / Piso | Caixa de espessura 1 no plano escolhido |
| Caixa oca | Só as faces da caixa |
| **Repetir** | Seleciona blocos (do mundo ou do rascunho) e arrasta: a cópia se repete lado a lado, com os estados iguais. Resolve o telhado de escadas: faz um degrau certo e arrasta |
| Espelhar | Cópia espelhada (escadas e portas espelham junto) |
| **Pincel** | Segura o clique esquerdo e arrasta: pinta blocos onde o cursor passa, na camada em que começou (sem pular para o terreno). Tamanho 1×1 ou 3×3 com [ e ]. Clique direito arrastando apaga do traço |
| **Extrudar** | Depois do traço do pincel (ou de uma linha), a rolagem sobe ou desce **o traço inteiro** camada por camada, como uma extrusão: muralhas em volta da vila saem de um traço só. Só no topo pode trocar o bloco (ameias, laje) |

Inspirado em mods de varinhas de construção, sem depender de nenhum. Exemplo de muralha: Pincel de pedregulho contornando a
vila no chão → rolagem +6 → troca o bloco e pinta ameias no topo → Confirmar.

## 8. Ajuda com cores e botão (#10)

- Botão **?** (e tecla H) mostra/esconde as linhas de ajuda; a escolha fica salva.
- Cada dica vira pares *tecla → ação*: a tecla em **amarelo** dentro de colchetes (`[Clique esq.]`, `[Rolagem]`,
  `[R]`), a ação em cinza claro. As linhas de ajuda passam a ser montadas de pares de chaves de tradução, não de uma
  frase só.

## 9. Excluir blueprint (#11)

No menu do cartão (botão direito) ou com o cartão escolhido: **Excluir** com confirmação ("Excluir 'Cuty hall 1'?
Não dá para desfazer"). Apaga o `.nbt` do `generated/` e tira do cache do jogo. Os que vêm do mod não podem ser apagados,
só **ocultados** (fica na lista de ocultos, com botão para mostrar de novo). Mesma permissão da captura.

## 10. Demolir tudo (#12)

Interruptor na ferramenta Cavar: **Demolir tudo** (ícone TNT, vermelho quando ligado). Ligado, a escavação inclui
blocos com block entity (baús, estantes, sinos, placas, camas). O conteúdo **é apagado**, como pedido; o aviso mostra
quantos recipientes com itens serão destruídos antes de confirmar. Só para quem tem permissão de planejar; fora do
criativo, mineiros fazem o trabalho como em qualquer escavação. Continua respeitando lava e blocos inquebráveis.

---

## Fases

| Fase | Itens | Risco | Comentário |
|---|---|---|---|
| 1 | #10 ajuda, #11 excluir, #12 demolir, #4 porão | Baixo | Pequenas, independentes, dá para testar logo |
| 2 | #1, #5, #8 rascunho + `CommitPlanMessage` + plano embutido | Médio | Mexe no fluxo inteiro da tela; base de tudo que vem depois |
| 3 | #2 catálogo + orientação | Médio | Abas criativas no cliente; resolve parte das escadas |
| 4 | #6, #7 navegador e seções | Baixo | UI + um JSON por mundo |
| 5 | #9 formas | Médio | Pincel + extrudar primeiro (muralhas), depois linha, oca, repetir, espelhar |
| 6 | #3 fantasmas de verdade | Alto | Gancho de render por plataforma; fallback nos gizmos |

A tela atual tem 1130 linhas; antes da fase 2 ela é dividida (rascunho, painéis, ajuda, entrada) para não passar de
2000 com tudo isso.
