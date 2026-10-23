# Próximos passos da expansão

Data: 2026-10-18. Continua `vilas-consolidado.md` e `bountiful-producao-coletor.md`. As fases 0 a 3 da economia
(coletores, produção, carregador) estão prontas. Veja a memória `village-economy-plan`.

## Ordem decidida com o Andre

| # | Item | Prioridade | Situação |
|---|---|---|---|
| 0 | Livro-guia de tudo que a branch adicionou | antes de tudo | **pronto** (14 capítulos) |
| 1 | NPCs mais inteligentes (ideias do Tektopia) | **foco**, o quanto antes | **pronto** (`villagerSkills`, 6 GameTests) |
| 2 | Missões com o Bountiful | ideal | **núcleo pronto** (`villagerRequests`, 4 GameTests); Bountiful sem versão 26.2 |
| 3 | Migrar as features antigas para `expansion.*` | ideal, não obrigatório | aos poucos |
| 4 | Árvore de evolução de prédios e níveis da vila | depois | falta modelar |

Regras de sempre:
- **Código novo:** fica em `net.conczin.mca.expansion.<tema>`. O core só recebe ganchos curtos marcados
  `// expansion: <tema>`.
- **Config:** cada sistema tem uma flag, desligada por padrão.
- **Profissões:** cruzar qualquer profissão nova com o vanilla e o MCA antes de criar, para não duplicar.
- **Verificação:** GameTest no NeoForge para cada mecânica. O client só é aberto pelo Andre.

---

## 0. Livro-guia

Um livro único, `mca:book_expansion_guide`, dividido em capítulos, em pt_br e en_us:
- **Capítulos:** aparência, menu radial, necessidades, fila de ações, agenda, propriedade e crimes, obras da vila,
  coletores, oficinas, carregador e ferramentas de depuração.
- **Como se obtém:** pela conquista raiz do MCA (chegar a uma vila) e por receita (livro + esmeralda).
- **Manutenção:** o livro precisa ser atualizado a cada sistema novo. Isso vale como item do checklist de cada
  entrega.

---

## 1. NPCs mais inteligentes (foco)

Objetivo: villagers que melhoram com o trabalho, aprendem, comem e moram melhor, e tomam decisões úteis sozinhos.
Flag: `villagerSkills`. Pacote: `expansion.minds`.

### 1a. Habilidade por profissão
- Cada villager guarda XP por profissão, de 0 a 100. O dado fica no `VillagerEntityMCA`, no mesmo padrão de dados
  rastreados do `Residency`.
- O XP sobe com trabalho real: árvore derrubada, lote produzido, pesca, entrega do carregador, obra concluída.
- O nível altera:
  - a velocidade (ticks de trabalho);
  - o rendimento, com chance de item extra;
  - a chance das receitas raras;
  - a qualidade, com chance de ferramenta encantada nas oficinas de ferramenta, arma e armadura.
- Aparece no Inspect do Editor e no tooltip de profissão da tela de interação.
- Trocar de profissão mantém o XP das profissões anteriores.

### 1b. Inteligência e escola
- O gene `intelligence` (conferir se já existe em `Genetics`; se não existir, entra como gene herdável) define:
  - o teto de habilidade: um villager pouco inteligente para em cerca de 60;
  - o ritmo de aprendizado.
- Escola: tipo de prédio novo, `school`, com atril + estantes + carteiras. O tipo exato de carteira ainda vai ser
  decidido.
- Crianças e adolescentes com o horário PLAY vão à escola e ganham inteligência. Isso cria a ligação com a família:
  filhos de pais inteligentes começam melhor.
- Professor: um bibliotecário trabalhando na escola. A profissão é vanilla, então não cria duplicata.

### 1c. Variedade de comida
- A fome do `Needs` passa a lembrar as últimas 8 comidas. Comida repetida mata menos a fome.
- Dieta variada dá um pensamento positivo, que melhora o humor. Comer a mesma coisa sempre dá "enjoado".
- Isso cria demanda por padaria, açougue, forrageiro e pescador, fechando o ciclo com a economia.

### 1d. Qualidade da casa
- Uma pontuação por casa, calculada a partir de:
  - área por morador;
  - camas;
  - luz;
  - decoração: vasos, quadros, tapetes e estantes, numa tag `mca:decoration`;
  - baús.
- Ela afeta a energia recuperada no sono e o humor. A casa ruim gera um pensamento "casa apertada".
- Base reaproveitada: o `BuildingRoomScanner` e o `PropertyRules.homeOf`.

### 1e. Decisões inteligentes
- **Emprego por aptidão:** o desempregado escolhe a estação livre em que tem mais XP. Se não tiver, usa a afinidade
  por traits.
- **Pedido de ferramenta:** quando a ferramenta quebra, o villager pede uma ao armazém. O carregador entrega e, se o
  armazém não tiver, a oficina de ferramentas recebe uma encomenda.
- **Produção por demanda:**
  - as oficinas priorizam o produto que está em falta no armazém;
  - os coletores priorizam o recurso que alguma oficina está esperando.
- **Ida ao armazém:** o villager com fome ou sede, sem comida em casa, busca no armazém. Isso respeita a propriedade:
  o armazém é da vila.

Ordem de implementação: **1a → 1c → 1e → 1d → 1b**. A escola fica por último porque depende de modelar um prédio
novo.

---

## 2. Missões com o Bountiful

Detalhes em `bountiful-producao-coletor.md`, seção 1. Resumo:
- **Primeiro passo:** baixar o jar do Bountiful para 26.2 e confirmar três coisas:
  - se dá para criar um bounty por código;
  - se existe evento de conclusão;
  - como funciona o "worth" dos objetivos e das recompensas.
- **Estrutura:** um núcleo de pedidos próprio, que funciona sozinho, com um adaptador opcional para o Bountiful
  (soft dependency).
- **Recompensa:** fica em escrow, tirada dos baús particulares do villager no momento em que a missão é criada.
- **Baús particulares:**
  - recebem itens uma única vez, por loot table de profissão (`mca:homes/<profissão>`);
  - depois são repostos pela parte do villager na produção, que já existe pelo `StashTask`.
- **Corações:** concluir a missão dá corações proporcionais à dificuldade. Expirar tira alguns.
- **Entrega da missão:** começa pela opção "Precisa de ajuda?" no menu radial. O mural de missões vem depois.

Decisões ainda abertas:
- versões de Minecraft que precisam ser suportadas;
- se o mural de missões vira um prédio da árvore de evolução.

---

## 3. Migração para `expansion.*`

Mover, um sistema por vez, quando nenhuma outra sessão estiver mexendo nele:

| Sistema | Pacote alvo | Ganchos que ficam no core |
|---|---|---|
| Needs + tarefas de necessidade | `expansion.needs` | tick do villager, pensamentos do humor |
| Property + crimes | `expansion.property` | uso e quebra de bloco (mixins), dono no editor |
| Fila de ações | `expansion.queue` | tela de interação |
| Work orders + blueprints | `expansion.workorders` | registro de profissões e pacotes de cérebro, comando |
| Visual (human-like, 3D, Fresh Animations) | `expansion.visuals` | renderer e camadas |

Como fazer cada migração:
- mover a classe e trocar os imports;
- trocar a chamada direta no core por um gancho comentado;
- rodar a suíte de GameTests do sistema.

Quando tudo estiver migrado, avaliar o jar separado `mca_expansions`.

---

## 4. Árvore de evolução de prédios (depois)

Ainda depende de modelagem; detalhes em `vilas-consolidado.md`, seção 2. Antes de implementar, decidir:
- os novos tipos de prédio e o efeito de cada um nas necessidades: poço, fazenda, mercado, escola, templo,
  muralha/torre;
- quem faz o upgrade (recomendado: o jogador constrói e o sistema detecta);
- em que nível a vila gerada começa.

Os prédios da economia (acampamento de lenhador, cabanas e oficinas) entram como nós da árvore. A escola vem
do item 1b.

---

## Andamento (18/10/2026)

**1. NPCs mais inteligentes: pronto.** Código em `expansion/minds`, flag `villagerSkills`.
- **1a. Habilidade:** `Skills`.
  - Velocidade, item extra, chance de raros e encantamento nas oficinas de ferramenta, arma e armadura.
  - Ganha XP em coleta, produção, entregas e obras.
  - Aparece na tela de interação e no Inspect.
- **1b. Inteligência:** gene herdável `Intelligence`, com teto de habilidade de 40 a 100.
  - Prédio `school` (atril, 4 escadas de madeira, 4 estantes).
  - `SchoolTask` para crianças de dia; um bibliotecário por perto dobra o ganho.
- **1c. Dieta:** `Diet`. Comida repetida rende menos, com pensamentos "comida variada" e "enjoado". O villager escolhe
  a comida que comeu menos.
- **1d. Casa:** `HomeQuality` dá uma nota de 0 a 100 (chão por morador, luz de blocos, tag `mca:decoration`, baú),
  com pensamentos "aconchegante" e "apertada".
- **1e. Decisões:**
  - `AptitudeJobTask`: emprego pela maior habilidade;
  - busca de ferramenta no armazém (coletores);
  - armazém como despensa (`Provisions`);
  - oficina produz primeiro o que falta no armazém.

**2. Pedidos: núcleo pronto.** Código em `expansion/requests`, flag `villagerRequests`.
- **Bountiful:** não tem build para 26.2 (o mais novo no Modrinth é 8.0.0-beta.2, para 1.21.1). O ponto de encaixe é
  `Requests.RequestBoard`.
- **Como funciona:**
  - botão "Precisa de ajuda?" no menu radial;
  - recompensa em escrow, tirada dos baús da casa;
  - entrega dá corações pela dificuldade;
  - o pedido vence em 3 dias, a recompensa volta para casa e o jogador perde 3 corações;
  - as economias iniciais vêm da loot table `mca:homes/<profissão>`.
- **Datapack:** valores e objetivos ficam em `data/mca/requests`.
- **Caça:** objetivos de derrotar mobs, contados pelas estatísticas do jogador.
- **Mural de Pedidos:** bloco `mca:request_board`. Usar traz um pedido de um morador livre; agachado, lista os
  pedidos abertos. Até 3 por jogador por vila.

**3. Migração:** não iniciada, porque outra sessão está mexendo nos mesmos arquivos (animações, Needs). Os ganchos novos
foram reunidos em `expansion/ExpansionHooks` (tick da vila) e em `expansion/ExpansionContent` (registro de blocos,
itens e profissões).

**Testes:** todas as suítes da expansão passam (Economy 8, Minds 6, Requests 6).
- A suíte completa do MCA travou num teste de navegação (`mca_navigation_unloaded_chunk`) e encheu o disco de log.
- Rodado sozinho, esse teste passa, assim como o de lápide que falhou junto.
- Para rodar a suíte completa, use `timeout` e acompanhe o tamanho de `neoforge/run-gametest/logs/latest.log`.
