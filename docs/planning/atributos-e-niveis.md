# Atributos e níveis dos villagers (WIP)

> **Status: em andamento.** Planejado em 2026-10-28; parte já implementada (ver Estado). A inspiração é o Fallout Shelter, mas com atributos próprios. O painel onde o chefe distribui os pontos está em [gestao-da-vila.md](gestao-da-vila.md).

## Estado (2026-10-28)
Feito, sem commit e sem teste em jogo, com a flag `villagerAttributes` (desligada por padrão):
- **Base:** `expansion/minds/VillagerAttributes`:
  - 6 atributos de 1 a 10, sorteados de 3 a 6 com bônus de genes, traços e idade;
  - nível até 30 e XP pelas mesmas chamadas de `Skills.gain`, mais o treino de arco;
  - 1 ponto por nível.
- **Aptidão por profissão:** a tabela `FITS`, com todas as profissões vanilla e do MCA. Ela multiplica a velocidade do trabalho (0,84 a 1,2) e o aprendizado.
- **Efeitos próprios de cada atributo:**
  - Força: dano;
  - Vigor: velocidade e cansaço;
  - Destreza: item extra;
  - Percepção: achados raros;
  - Sabedoria: aprendizado, XP e limite de habilidade;
  - Social: corações.
- **Pontos:** só o chefe (rank prefeito ou acima na vila do villager) gasta, clicando no atributo (`AttributeMessages.Spend`, conferido no servidor). Com 3 pontos acumulados, o villager gasta sozinho no atributo principal da profissão.
- **Interface:** faixa de atributos na tela de interação (nível, pontos, aptidão ●●●○○ e barra de XP). Com `villagerInfoInTalk`, a faixa fica atrás da pergunta "No que você é bom?".
- **Testes:** `VillagerAttributesTest`.

## O que já existe
- **Habilidade por profissão:** `expansion/minds/Skills.java`.
  - Vai de 0 a 100 e é salva em `"ProfessionSkills"`.
  - Sobe com `Skills.gain` em `WorkshopProductionTask`, `GathererTask`, `HunterTask`, `HaulerTask`, `WorkOrderTask` e na colheita do fazendeiro.
  - O limite é `40 + 60 × INTELLIGENCE`.
  - Deixa o trabalho até 2× mais rápido, dá até 50% de chance de item extra e até 3× de chance de raros.
  - Guarda e arqueiro não ganham habilidade.
- **Genes:** só o INTELLIGENCE afeta a jogabilidade. SIZE e WIDTH mudam a hitbox.
- **Escolha de emprego pela aptidão:** `AptitudeJobTask` pega a melhor habilidade, se for ≥ 5.

## Os 6 atributos (1 a 10)

| Atributo | Efeito geral (todo villager) |
|---|---|
| **Força** | dano corpo a corpo e com ferramentas, velocidade de quebrar blocos |
| **Percepção** | alcance de visão e detecção (monstros, creepers, alvos, POIs), menos penalidade no escuro, precisão com arco e equipamentos especiais, achar raros |
| **Vigor** | velocidade de movimento e de trabalho, duração do turno (cansa mais devagar) |
| **Destreza** | qualidade e precisão do trabalho fino: chance de item extra ou melhor, menos tropeços |
| **Sabedoria** | velocidade de aprender o ofício (ganho de XP e habilidade) e limite de habilidade; o gene INTELLIGENCE vira o valor inicial |
| **Social** | preços de troca, ganho de corações, opiniões e conversas, chance de virar líder |

Correspondência com o S.P.E.C.I.A.L.:
- Força volta.
- Vigor substitui Resistência e Agilidade.
- Percepção absorve parte do "ofício".
- Destreza substitui o ofício e parte do olhar.
- Sabedoria e Social equivalem a Inteligência e Carisma.

## Profissões
Cada profissão tem pelo menos 2 atributos (● principal, ○ secundário).

| Profissão | Atributos |
|---|---|
| Guarda | ● Força ○ Vigor |
| Arqueiro | ● Percepção ○ Destreza ○ Vigor |
| Mercenário | ● Força ou Percepção, conforme a arma ○ Vigor |
| Caçador | ● Percepção ○ Destreza ○ Vigor |
| Lenhador | ● Vigor ○ Força |
| Minerador | ● Força ○ Vigor |
| Construtor | ● Força ○ Vigor ○ Sabedoria |
| Carregador (leva itens entre oficinas e armazém) | ● Vigor ○ Força |
| Coletor | ● Percepção ○ Destreza |
| Pescador | ● Percepção ○ Destreza |
| Cartógrafo | ● Percepção ○ Destreza ○ Sabedoria |
| Curtidor | ● Destreza ○ Sabedoria |
| Ferreiros (armas, armaduras, ferramentas) | ● Força ○ Destreza |
| Pedreiro | ● Força ○ Vigor |
| Flecheiro | ● Destreza ○ Percepção |
| Padeiro | ● Destreza ○ Vigor |
| Açougueiro | ● Vigor ○ Força |
| Pastor | ● Destreza ○ Vigor |
| Fazendeiro | ● Vigor ○ Destreza |
| Clérigo | ● Sabedoria ○ Destreza ○ Social |
| Bibliotecário / professor | ● Sabedoria ○ Social |

- **Aptidão para o posto:** média ponderada (principal 60%, secundários dividem 40%).
  - Ela multiplica a velocidade de trabalho e o ganho de habilidade nessa profissão.
  - Aparece como ●●●○○ no painel de Empregos e na ficha.
- O `AptitudeJobTask` e o villager líder passam a usar a aptidão, e não só a habilidade.

## Recomendações
- **Manter os 6.** São distintos, e a tabela por profissão segura a complexidade.
- **Cortar do pedido:**
  - a altura do pulo, que atrapalha o pathfinding;
  - "detectar armadilhas", que vira "detectar monstros e creepers", porque o Minecraft não tem armadilhas.
- **Vigor se espalha por muitas profissões:** limitar o efeito dele ao trabalho físico e ao cansaço, para não virar o "melhor atributo".

## XP e níveis
- O XP vem das mesmas chamadas de `Skills.gain`, que já pesam cada trabalho (`GAIN_BATCH`, `GAIN_GATHER`…). Guarda e arqueiro passam a ganhar XP por patrulha, treino de arco e combate.
- Cada nível dá **1 ponto**, distribuído pelo chefe (jogador com rank) ou, sem ele, pelo villager líder.
- **Valores iniciais:**
  - genes: INTELLIGENCE → Sabedoria;
  - traços: Atlético → Vigor, Durão → Força, Fraco → −Força, Desastrado → −Destreza, Gênio → Sabedoria, Extrovertido → Social…;
  - idade: o idoso perde Vigor e ganha Sabedoria; a criança começa baixa.
- **Treino:** escola e biblioteca treinam Sabedoria (`SchoolTask`); a prática de arco treina Percepção; o trabalho pesado treina Força e Vigor.

## Interface
- **Ficha:** atrás da pergunta "No que você é bom?" na conversa (ver [interface-e-conversas.md](interface-e-conversas.md)) e no painel de Moradores da prefeitura.
- **Ícones:** um por atributo (ver o TODO de arte).

## Decisões (2026-10-28)
- **Nível máximo:** 50, com 1 ponto por nível.
- **Social:** vale para todos.
  - Corações com qualquer villager.
  - Preço de troca de qualquer villager que negocie: −4% por ponto acima da média, +4% abaixo (`VillagerAttributes.adjustPrices`, chamado em `VillagerEntityMCA.openTrade`).

Ainda em aberto: os atributos substituem o gene INTELLIGENCE ou ele continua só como valor inicial (hoje é só o valor inicial).
