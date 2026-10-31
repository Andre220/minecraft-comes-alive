# Facções: saqueadores como gente, com bandeira e reputação (WIP)

> **Status: em andamento.** Planejado em 2026-10-29; fase 1 implementada (ver Estado). A inspiração é o RimWorld: tribos e facções com quem dá para conversar, negociar ou guerrear. O objetivo principal é **dar ao jogador um motivo para virar fora da lei**.

## Estado (2026-10-29)
Fases 1 a 3, território no mapa e guerras feitos, sem commit e sem teste em jogo, com a flag `villagerFactions` (ligada no cliente de desenvolvimento). Código em `expansion/faction`:
- **Facções do mundo** (`FactionData`, salvo em `mca_factions`): `factionCount` (5) nascem quando o mundo precisa delas.
  - Cada uma tem um nome, um lugar do gerador de nomes de vila num de 6 moldes: "Corvos de X", "Irmandade de X", "Lobos de X"…
  - E uma bandeira: cor base e 2 a 4 padrões de estandarte sorteados.
- **Bases:**
  - A cada 20 segundos, para cada jogador, o jogo procura a base de saqueadores mais próxima (até 6 chunks). Uma base nova vai para a facção que tem outra base a até 1200 blocos.
  - Se não houver, vai para uma facção ainda sem base. Se todas tiverem, nasce uma facção nova, até `factionMax` (8); senão, a menor facção leva.
  - Os estandartes da base viram a bandeira da facção.
- **Território:** `factionTerritoryRadius` (96 blocos) em volta de cada base. Ao entrar, aparece na barra de ação: "Território: Corvos de X · hostil".
- **Membros:** todo illager (saqueador, vindicador, evocador, bruxa, devastador) pertence à facção do território em que está, ou da base mais próxima a até 1200 blocos. A facção fica guardada numa tag.
- **Reputação por jogador e facção**, de −100 a 100, começando em `factionStartReputation` (−15):
  - matar um membro: −`factionKillPenalty` (15);
  - matar um villager: +3 com todas as facções;
  - matar um golem de ferro: +5 com todas as facções;
  - a mudança de faixa (hostil, neutro, amigo, aliado) é avisada no chat.
- **Trégua:** com reputação neutra ou melhor, os membros da facção não escolhem o jogador como alvo, a não ser que ele os ataque primeiro.

- **Bandidos (fase 2, `Bandits`):** em vez de uma entidade nova, o saqueador vira um villager do MCA com a profissão `mca:bandit`. Assim ele tem a mesma aparência, Fresh Animations, traços, humor e conversa dos outros.
  - O `SpawnQueue` troca todo saqueador que entra no mundo **fora de uma invasão** (as invasões continuam vanilla, porque a raid precisa deles). Flag `factionPillagersAsPeople` (ligada).
  - Recebe besta (70%) ou machado de ferro, a tag da facção e a de capitão se era líder de patrulha. Não some com a distância.
  - Com 8 bandidos a até 48 blocos, os saqueadores novos daquela base não são trocados (as bases continuam gerando saqueadores, e os bandidos não contam no limite de monstros).
  - Luta como o mercenário (ramo de combate do guarda). Uma vez por segundo, mira o jogador mais próximo a até 16 blocos com quem o bando é hostil, e larga o alvo quando a reputação deixa de ser hostil (a não ser que o jogador o tenha atacado).
  - Com jogador hostil, não conversa: "Ele te encara e não diz nada".
  - Nova pergunta "Quem são vocês?" (constraint `bandit`, ação `bandit_band`): diz o nome do bando e como ele vê o jogador.
  - Roupas: as do curtidor e do guarda.

- **Território no mapa (`FactionMapMessages`, `FactionMapOverlay`):**
  - o jogador "conhece" um posto quando o descobre ou anda no território dele;
  - o mapa da prefeitura desenha um anel tracejado na cor da bandeira em volta de cada posto conhecido, com o estandarte em cima;
  - um posto fora da área visível aparece com o estandarte na borda do mapa;
  - ao passar o mouse: o nome do bando, como ele vê o jogador e as coordenadas.
- **Guerras (`FactionWars`, flag `factionWars`, ligada):**
  - cada par de facções tem uma relação de −100 a 100; abaixo de −40 é guerra. Uma facção nova já nasce com relações sorteadas com as outras;
  - uma vez por dia:
    - a relação volta devagar para −10;
    - territórios que se encostam pioram 5;
    - 10% de chance de uma briga (−15) ou de um inimigo comum (+10);
  - guerra declarada e paz são avisadas no chat;
  - cada par em guerra tem 35% de chance por dia de atacar o posto inimigo mais próximo dos seus:
    - longe dos jogadores, o resultado sai pela força (postos × 3 + sorte, e o defensor tem bônus); se o atacante vence, o posto muda de dono e os estandartes trocam quando alguém chegar perto. Aparece o boato "Boato: X tomaram um posto de Y";
    - com um jogador a até 160 blocos, a luta é de verdade: um grupo de 4 bandidos nasce a 40 blocos e marcha até o posto;
  - os bandidos atacam membros de facções em guerra com a deles que estejam à vista;
  - membros da mesma facção, ou de facções em paz, não se atacam (vale também para os saqueadores vanilla das invasões);
  - mortes entre facções pioram a relação em 6.
- **Ações (fase 3, `FactionDeals`):**
  - **negociar:** o botão de barganha já existente, agora com o que o bandido carrega: 2 a 4 itens roubados (pão, trigo, couro, ferro, ouro, flechas, sela…) e algumas esmeraldas;
  - **"Venha lutar por mim"** (só com reputação amigo ou melhor): 8 esmeraldas por 3 dias ou 16 por uma semana. O bandido sai do bando e vira mercenário;
  - **"Deixem a vila em paz"**: `factionTruceCost` (24) esmeraldas dão `factionTruceDays` (7) dias de trégua para a vila mais próxima do jogador (até 2000 blocos):
    - os membros do bando não atacam os villagers e golens dela;
    - as patrulhas do bando não aparecem até 64 blocos além da borda da vila;
  - os pedidos ("traga X") continuam pelo sistema de pedidos que já existe.

Falta: fase 4 (entrar na facção, caminho fora da lei), fase 5 (tipos), redenção, facções novas por racha e exílio.

## O que já existe e será reaproveitado
- **Villager zumbi do MCA:** `ZombieVillagerEntityMCA` estende o zumbi vanilla e implementa `VillagerLike`. É o molde para o **saqueador do MCA**: mesma genética, pele, roupa, cabelo, traços e diálogos.
- **Troca automática:** o `SpawnQueue` troca os villagers e zumbis vanilla pelos do MCA (`overwriteOriginalVillagers`, `overwriteOriginalZombieVillagers`). Os saqueadores ganham a mesma troca (`overwriteOriginalPillagers`).
- **Fora da lei:** já existem o rank `OUTLAW` (reputação baixa na vila), a profissão `outlaw` e as constraints `outlawed` / `!outlawed` nos diálogos.
- **Caçador de recompensas:** `VillageManager.spawnBountyHunter` já manda saqueadores atrás de quem a vila odeia, um gancho natural para "a facção te protege".
- **Nomes:** `data/mca/mca_names` (55 culturas) gera nomes de pessoas e vilas, e serve também para nomes de facção.
- **Postos de saqueadores:** a estrutura vanilla `minecraft:pillager_outpost`, que já está na lista de estruturas do MCA (`Config`).
- **Bandeira:** estandartes vanilla com padrões (`BannerPatternLayers`), dispensando arte nova.
- **Diálogos, traços, opiniões e humor:** valem para qualquer `VillagerLike`.

## 1. A facção
- **Geração:** algumas facções nascem com o mundo (config: quantidade). Cada uma recebe:
  - **nome**, de uma cultura de `mca_names` com um molde (por exemplo "Os Corvos de Vardholm", "Irmandade de X");
  - **bandeira**, um estandarte com cor base e 2 a 4 padrões sorteados, guardado como `BannerPatternLayers`;
  - **tipo** (seção 5; na fase 1 todas são "saqueadores");
  - **sede:** os postos de saqueadores que forem sendo achados.
- **Dados do mundo:** SavedData `FactionData`, com as facções, a reputação de cada jogador com cada facção, os postos e a relação com cada vila.
- **Posto de saqueadores:**
  - quando um posto é gerado ou visto pela primeira vez, ele **ganha a facção** (a mais próxima ou uma nova);
  - o estandarte ominoso do posto vira a **bandeira da facção**;
  - quem nasce ali pertence à facção.

## 2. Saqueadores do MCA (`PillagerEntityMCA`)
- **A entidade:** estende o `Pillager` vanilla, implementando `VillagerLike` como o zumbi do MCA.
- **Visual:**
  - modelo humano, genética, pele, roupa (roupas de saqueador no catálogo de roupas) e cabelo;
  - o estandarte nas costas do capitão.
- **Por dentro:** traços, humor, opiniões e nomes.
- **Membros:** cada saqueador pertence a uma facção (parâmetro sincronizado) e tem um **cargo**: batedor, guerreiro, capitão, líder da facção.
- **Comportamento:**
  - **hostil** (como hoje) com quem tem reputação baixa na facção;
  - **neutro** com quem tem reputação média: não ataca e aceita conversa;
  - **aliado** com quem tem reputação alta: luta junto e protege dos caçadores de recompensa.
- **Ataques a vilas:** os raids e patrulhas vanilla continuam, agora com saqueadores do MCA.

## 3. Reputação com a facção
- **Como sobe:**
  - roubar ou atacar vilas (a mesma ação que derruba o rank da vila);
  - ajudar a facção: entregar itens pedidos, matar golens e caçadores de recompensa;
  - presentes ao líder.
- **Como cai:** matar membros, ajudar vilas contra raids, ser herói da vila.
- **Faixas:** hostil → desconfiado → neutro → amigo → membro.
- **O jogo dos dois lados:** reputação alta com a facção e baixa com as vilas é o caminho do **fora da lei**. Vilas fecham as portas e mandam caçadores; a facção vira casa.

## 4. Conversa e interação
- **Interação:** a mesma tela dos villagers (menu circular, conversa, presentes, traços, perguntas da conversa), com respostas e falas próprias de saqueador (`DialogueType.PILLAGER`, com volta para adulto).
- **Ações de facção** (dependem da faixa):
  - **negociar:** troca com itens roubados (o sistema de barganha da expansão);
  - **pedidos:** "traga X" ou "derrube Y", reaproveitando o sistema de pedidos;
  - **recrutar** um saqueador como mercenário (`mca:mercenary`);
  - **entrar para a facção:** ganha a bandeira, a armadura da facção e o rank fora da lei nas vilas;
  - **pedir trégua:** pagar tributo para parar os ataques a uma vila.
- **Fora da lei:** quem entra pode morar no posto, levar saqueadores a um raid e tirar a parte de um saque.

## 5. Tipos de facção (depois)
| Tipo | Armas e aliados | Gancho |
|---|---|---|
| **Tribal** | arco, machado, montaria de devastador (o "rinoceronte") | postos mais selvagens, rituais em fogueiras |
| **Religiosa** | seguidores do warden, do End ou do Nether; magia dos evocadores | evocadores e vex, altares |
| **Tecnológica / militar** | besta, TNT, espadas, armadura de ferro | fortes, catapultas de TNT |

Cada tipo é um arquivo de dados (`data/mca/faction_types/*.json`): armas, roupas, estruturas e falas.

## Fases
1. **Facção básica:** `FactionData`, nome e bandeira gerados, postos ganham facção, reputação por facção, saqueadores neutros ou hostis conforme a reputação.
2. **Saqueador do MCA:** `PillagerEntityMCA` com o visual e a interação do MCA, troca automática e falas de saqueador.
3. **Ações de facção:** negociar, pedidos, recrutar, trégua.
4. **Entrar para a facção:** caminho fora da lei, morar no posto, raids com o jogador.
5. **Tipos de facção:** tribal, religiosa, tecnológica.

## Decisões (2026-10-29)
- **Quantidade:** 5 facções por mundo por padrão, ajustável na config do MCA (`factionCount`).
- **Facções novas com o tempo:** sim, se der para fazer bem. A proposta está na seção 6.
- **Postos:** uma facção tem **vários postos**.
- **Território:** cada posto marca uma área em volta dele (o que a facção "vê"). Ela pode ser vista no mapa (seção 7).
- **Outros illagers:** entram. Vindicadores, evocadores e bruxas viram gente do MCA; o devastador fica como montaria (bicho), com a bandeira da facção.
- **Guerras:** as facções brigam entre si (seção 8).
- **Redenção:** se der para implementar, o fora da lei pode voltar a ser bem-vindo nas vilas (seção 9).

## 6. Facções novas com o tempo
Quatro jeitos, todos com config e com um teto de facções vivas:
- **Racha:** quando o líder de uma facção grande (muitos postos ou membros) morre, os capitães disputam o posto. Às vezes um posto se separa com o capitão mais ambicioso e vira uma facção nova, nascida inimiga da antiga.
- **Posto sem dono:** um posto de saqueadores gerado longe de qualquer território vira uma facção nova, se houver vaga no teto. Senão, a facção mais próxima o anexa.
- **Exilados:** villagers expulsos ou fora da lei (profissão `outlaw`) que se juntam em grupo formam um bando. O bando cresce até virar facção, com um acampamento.
- **Fundada pelo jogador:** o fora da lei com reputação alta pode fundar a **própria facção**, com nome e bandeira escolhidos, e recrutar saqueadores e villagers fora da lei.
- **Fim de uma facção:** uma facção sem postos e sem membros desaparece, e os postos dela viram território livre.

## 7. Território
- **Área:** um raio em volta de cada posto (config, por exemplo 64 blocos). A soma dos raios é o território da facção.
- **Ao entrar:** aparece um título discreto, como ao entrar numa vila: "Território dos Corvos de Vardholm (hostil)".
- **No mapa:**
  - o mapa da prefeitura e o do Blueprint desenham o território com a cor da bandeira, tracejado como a borda da vila;
  - o ícone da bandeira fica sobre cada posto conhecido;
  - só aparecem os postos que o jogador já viu.
- **Vigilância:** dentro do território, os batedores notam o jogador de mais longe e avisam o posto. Com reputação baixa vêm patrulhas, com reputação alta vêm guias.

## 8. Guerras entre facções
- **Relações:** cada par de facções tem uma relação, de guerra até aliança. Ela muda com incidentes: território sobreposto, membros mortos, saques no mesmo alvo.
- **Simulação:** a guerra é simulada em chunks descarregados, para não pesar. De tempos em tempos, um posto em guerra ataca outro e o resultado é sorteado pela força de cada facção (membros, postos, tipo). O perdedor pode perder o posto.
- **Lutas de verdade:** perto do jogador, a luta é real, com saqueadores de bandeiras diferentes se enfrentando.
- **As vilas:** são um alvo a mais. Facções em paz com uma vila (por trégua ou tributo) deixam ela em paz.
- **O jogador entra no jogo:** pode tomar partido, levar recados, trair, ou pagar uma facção para atacar outra.

## 9. Redenção do fora da lei
- **Multa:** paga nos Registros da Cidade da vila, em esmeraldas, proporcional ao mal feito. Volta a reputação para neutra.
- **Pena:** cumprir tempo na prisão da vila (o tipo de prédio `prison` do MCA). O jogador fica preso alguns dias de jogo, com a porta trancada pelos guardas, e sai com a reputação de volta.
- **Serviço:** pedidos da vila "pagam" a dívida aos poucos, com o sistema de pedidos que já existe.
- **Do lado da facção:** voltar para a vila derruba a reputação com a facção, que passa a ver o jogador como traidor.

Perguntas que ainda ficam:
- O jogador fora da lei vê o território de todas as facções, ou só das que conhece?
- Na pena de prisão, o jogador pode fugir (e o que acontece)?
