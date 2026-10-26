# Espelhos: estudo técnico

Pedido do Andre: dois espelhos, um **pequeno** (de pôr em cima de mesa, cômoda, bancada e afins) e um **grande** (de
chão, com dois blocos de altura). Com o botão direito, o espelho serve para **trocar a aparência do cabelo**. A
preocupação é não precisar de uma segunda câmera olhando de volta para o jogador, e não depender de shaders.

Nada disso está implementado. Data: 2026-10-24. Versão do jogo: 26.2.

---

## 1. Resumo e recomendação

Há quatro jeitos de fazer um espelho. Só um deles é a "segunda câmera" cara, e dá para evitá-lo:

| | Como funciona | Custo por quadro | Shader? | Veredito |
|---|---|---|---|---|
| **A. Espelho falso + tela** | O espelho no mundo é só uma textura prateada. O clique abre uma tela com o jogador refletido | Zero | Não | **Fase 1** |
| **B. Reflexo só de entidades** | Uma câmera virtual desenha **só o jogador** (e quem estiver perto) numa textura pequena, que vai no vidro. O mundo não é desenhado de novo | ~1–3 entidades a mais | Não | **Fase 2**, se a fase 1 agradar |
| C. Modelo espelhado no mundo | Desenha o jogador espelhado "atrás" do vidro, direto na cena | Baixo | Sim (recorte) | Descartado |
| D. Segunda câmera completa | Desenha o mundo inteiro de novo, do ponto de vista refletido (como o Immersive Portals) | Dobra o custo do mundo | Não, mas briga com o Sodium | Descartado |

**Recomendação:** começar pela **A**, que já entrega o objetivo (trocar o cabelo se vendo no espelho) quase só
reaproveitando código. Depois, se quiser o espelho "vivo" no mundo, fazer a **B**. Ela é tecnicamente uma câmera, mas
não renderiza o mundo: custa o mesmo que a prévia do jogador no inventário, que o próprio Minecraft já faz.

---

## 2. O que já existe no código

- **Trocar o cabelo já está pronto:** a `CombScreen` (`client/gui/CombScreen.java`) é o editor de aparência
  restrito ao cabelo (estilo, camadas, cor), aberto pelo pente (`item/CombItem.java` → `OpenGuiRequest.Type.COMB`).
  Ela já mostra o jogador e sincroniza com o servidor. O que ela faz a mais: gasta durabilidade do pente
  (`eventCallback` → `DamageItemMessage`).
- **Prévia de entidade numa tela:** a `OutfitChoiceScreen` (guarda-roupa) já desenha o jogador com a roupa nova.
- **Peças de dois blocos:** `FurnitureBlock` / `WardrobeBlock` já colocam, quebram e giram uma peça de dois blocos de
  altura. Hoje estão presas ao `ClosetBlockEntity`, mas a parte de "peça de vários blocos" separa fácil.
- **Gerador de modelos:** `scripts/claude-tools/wardrobe_assets.py` gera modelos, blockstates, loot, receitas e
  nomes para as 12 madeiras. Um `mirror_assets.py` seguiria o mesmo molde.
- **No Minecraft 26.2:**
  - `PictureInPictureRenderer` e `GuiEntityRenderer` desenham uma entidade numa textura própria (cor e
    profundidade). O truque é `RenderSystem.outputColorTextureOverride` / `outputDepthTextureOverride`: tudo o que
    for desenhado vai para essa textura, não para a tela.
  - `DepthStencilState` só tem profundidade, **sem stencil**. O recorte clássico de portal (desenhar só dentro da
    moldura via stencil) não existe na API de pipelines. Isso pesa contra a opção C.

---

## 3. Fase 1: espelho falso + tela do espelho (opção A)

### Blocos

- **Espelho pequeno** (`mirror_small`, um por madeira):
  - Moldura de madeira com pé, vidro de uns 8×10 px, levemente inclinado para trás.
  - Gira em 16 posições, como placa e banner (`ROTATION_16`), para ficar natural em cima de mesa.
  - **Apoio:** pode ser posto sobre qualquer bloco cujo topo tenha colisão. Assim funciona em mesas do *Tables &
    Chairs* (`tac:`), cômodas, bancadas e na mesa do Tabletop. Em lajes e mesas mais baixas, um estado `low` desce o
    modelo 8 px.
  - Sem bloco-entidade na fase 1.
- **Espelho grande** (`mirror_standing`, um por madeira):
  - Dois blocos de altura, moldura no chão, vidro de 12×28 px, 4 direções.
  - Reaproveita a lógica de peça do `FurnitureBlock`: separar uma classe base `MultiBlockPiece` (partes, colocar,
    quebrar inteiro, girar), da qual derivam `FurnitureBlock` e `MirrorBlock`.
- **Vidro:**
  - Textura prateada com um gradiente e um reflexo diagonal claro, desenhada em Python como as das barbas.
  - Render type `translucent`, e `emissive` fraco para "brilhar" um pouco no escuro.

### Interação

- **Botão direito:** abre a **`MirrorScreen`**, uma `CombScreen` que não gasta pente (sobrescreve `eventCallback`).
  - A prévia mostra o jogador **de frente e espelhado** (escala x = −1), como se olha num espelho de verdade.
  - Fundo com a moldura do espelho, em vez do fundo padrão do editor.
- **Distância:** o servidor confere se o jogador está a no máximo 4 blocos e na frente do vidro (produto escalar com
  a normal > 0). Mesmo esquema do guarda-roupa: mensagem `MirrorMessages.Open`.
- **Aldeões (opcional, depois):** um aldeão com espelho em casa pode usá-lo na rotina da manhã (`daily_toggles`) e,
  de vez em quando, trocar de penteado. Encaixa no `villagerRoutines`.

### Config

- `mirrors` (padrão `false`), seguindo a regra da expansão: tudo novo em `net.conczin.mca.expansion.mirror`.
- Os ganchos no núcleo do MCA ficam curtos e marcados com `// expansion: mirrors`.

### Custo

- **Desempenho:** nenhum. É um bloco estático.
- **Tamanho:**
  - Código: uns 400 a 600 linhas, contando a separação do `MultiBlockPiece`.
  - Assets gerados por script.
  - GameTests: colocar e quebrar inteiro; apoio em laje/mesa; a mensagem `Open` respeita a distância.

---

## 4. Fase 2: reflexo só de entidades (opção B)

A ideia: o vidro mostra uma **textura viva** com o jogador refletido. Só entidades são desenhadas; o "cenário" atrás
delas é falso. É o mesmo mecanismo da prévia do inventário, mas com uma câmera de perspectiva posta no reflexo do
olho do jogador.

### Como funciona, passo a passo

1. **Antes de desenhar o mundo** (gancho no início de `GameRenderer.renderLevel`, um mixin em `common`):
   - Para cada espelho visível a até 8 blocos, com o jogador na frente do vidro, atualiza a textura dele.
   - **Limites:** no máximo 2 espelhos por quadro, e cada um atualiza a 20–30 quadros por segundo, não a cada quadro.
2. **Olho refletido:** reflete a posição do olho no plano do vidro: `e' = e − 2·((e − p₀)·n)·n`, sendo `p₀` um
   ponto do vidro e `n` a normal.
3. **Projeção fora do eixo** (off-axis), com a matriz de projeção generalizada de Kooima:
   - A partir dos cantos do vidro `pa`, `pb`, `pc` e do olho refletido, monta um *frustum* assimétrico
     (`Matrix4f.frustum` do JOML) que enquadra **exatamente** o retângulo do vidro.
   - Com isso, a textura cai 1:1 no vidro, sem esticar.
   - O plano próximo coincide com o plano do vidro. Então tudo o que está **atrás** do espelho é cortado de graça,
     sem stencil e sem shader.
4. **Desenho:**
   - Aponta `outputColorTextureOverride` / `outputDepthTextureOverride` para a textura do espelho, que é pequena:
     128×160 para o pequeno, 128×288 para o grande.
   - Limpa a textura com a cor de fundo e desenha, pelo `EntityRenderDispatcher`, o jogador e até 3 entidades na
     frente do espelho (aldeões, por exemplo), como o `GuiEntityRenderer` faz.
   - Como o jogador é desenhado pelo renderer do MCA, ele sai com cabelo, roupa e rosto exatamente como no jogo.
5. **No vidro:** o renderer do bloco-entidade desenha um quad com essa textura e **inverte o u** (a imagem da câmera
   virtual precisa do giro horizontal para virar reflexo; confirmar no protótipo). Por cima vai a camada prateada
   translúcida da fase 1, bem fraca, para parecer vidro.
6. **Atraso de um quadro:** a textura usada é a do quadro anterior. Não se percebe.

### O cenário falso atrás do jogador

Sem desenhar o mundo, o fundo precisa ser inventado. Opções, da mais barata para a mais cara:

1. **Cor ambiente:** gradiente vertical tirado da luz do bloco e do céu na posição do espelho (escuro de noite, quente
   perto de tochas), com uma vinheta. Barato e convincente num vidro levemente embaçado. **Sugerida.**
2. **"Sala" em miniatura:** um modelo simplificado (só as faces visíveis) dos blocos num raio de ~5 blocos na frente
   do espelho, montado uma vez e guardado num buffer.
   - Só é refeito quando um bloco ali muda.
   - Dá a ilusão de "ver o quarto" no espelho.
   - Mais trabalho, e é um pedaço da "segunda câmera". Deixar para uma fase 3, se a fase 2 agradar.

### Riscos e compatibilidade

- **Sodium:** não mexe na renderização de entidades. Deve funcionar.
- **Iris com shaderpack:** é o risco maior.
  - O Iris troca os programas de entidade por os do shaderpack, e desenhar entidade numa textura própria no meio do
    quadro pode sair errado (ou ser desenhado na passada de sombra).
  - **Mitigação:** detectar shaderpack ativo pela API do Iris (por reflexão, sem dependência de compilação) e voltar
    ao espelho da fase 1.
- **Fresh Animations / EMF:** o jogador já não passa pelo EMF (ver a correção das pupilas de 2026-10-24). Aldeões no
  reflexo passam, e devem sair animados normalmente.
- **Desempenho:** cada atualização desenha poucas entidades numa textura de ~40 mil pixels. Com o teto de 2
  espelhos a 30 Hz, o custo deve ficar bem abaixo de 1 ms por quadro. Medir no protótipo com o F3.
- **Imagem invertida e lados:** a regra "inverter o u" e a ordem dos cantos (`pa`, `pb`, `pc`) precisam ser
  conferidas no jogo. É o tipo de detalhe que só se vê rodando.

### Config

- `mirrorReflections` (cliente, padrão `false`), para ligar ou desligar o reflexo vivo.
- `mirrorReflectionDistance` (padrão 8).

### Tamanho

- Mais uns 300 a 500 linhas: bloco-entidade, renderer, o gancho de pré-render, a matemática da projeção e a
  detecção do Iris.
- Protótipo sugerido antes de fechar o design: só o espelho grande, só o jogador, fundo de cor sólida.

---

## 5. Por que não as outras

- **C, modelo espelhado no mundo:**
  - Desenhar o jogador espelhado atrás do vidro esbarra em duas coisas:
    - Atrás do espelho normalmente há parede, e a profundidade esconde o reflexo.
    - Num espelho sobre a mesa, o reflexo vaza para fora da moldura quando visto de lado.
  - Recortar isso exige stencil, que o 26.2 não tem na API de pipelines, ou um shader próprio que descarta pixels
    fora do retângulo. Esse é um "core shader" do mod, não um shaderpack, mas é justamente o que conflita com o Iris.
  - Além disso, espelhar o modelo inverte a ordem dos vértices (é preciso trocar o culling).
- **D, segunda câmera do mundo:**
  - Redesenhar o mundo a partir do ponto refletido custa visibilidade de chunks e desenho de terreno de novo.
  - Com o Sodium, que controla a renderização de chunks, isso vira um projeto à parte.
  - É o caminho do Immersive Portals. Não compensa para um móvel.

---

## 6. Ordem sugerida

1. **Fase 1 (opção A):**
   - Separar `MultiBlockPiece` do `FurnitureBlock`.
   - Criar os dois blocos, as texturas e a `MirrorScreen` (uma `CombScreen` sem gasto e com prévia espelhada).
   - Ligar ao `mirrors`.
2. **Protótipo da fase 2:**
   - Espelho grande, só o jogador, fundo sólido.
   - Medir o custo e conferir a orientação.
3. **Fase 2 completa:**
   - Espelho pequeno também, fundo de cor ambiente, entidades próximas, detecção do Iris.
4. **Opcional:**
   - Aldeões usando o espelho na rotina.
   - A "sala em miniatura" no fundo.

## Perguntas em aberto

- **Escopo da tela:** a `MirrorScreen` mexe só no cabelo (como o pente) ou também na barba e nas cores? A barba já
  tem página no editor (`beard`), então incluir é barato.
- **Receita:**
  - Espelho pequeno: moldura de madeira + vidro + barra de ferro?
  - Espelho grande: duas tábuas + painel de vidro + barras?
- **Quem pode usar:** qualquer jogador, ou o espelho pertence a uma casa, como a futura posse dos guarda-roupas?
