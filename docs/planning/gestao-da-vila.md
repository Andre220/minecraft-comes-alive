# Gestão da vila: prefeitura, chefe, empregos e a questline do ancião (WIP)

> **Status: em andamento.** Planejado em 2026-10-28; parte já implementada (ver Estado). Os atributos, que dizem quem combina com cada posto, estão em [atributos-e-niveis.md](atributos-e-niveis.md).

## Estado (2026-10-28)
Feito, sem commit e sem teste em jogo:
- **Correção de segurança:** `SaveVillageMessage` confere o rank no servidor (impostos a partir de mercador, nascimentos a partir de nobre, casamentos a partir de prefeito).
- **Registros da Cidade** (`mca:city_records`, `expansion/townhall`):
  - base de madeira de 12 px com o livro animado da mesa de encantamento (`CityRecordsRenderer` usa o `BookModel` e a textura vanilla);
  - receita: livro e pena sobre um púlpito.
- **Prefeitura** (`building_types/town_hall.json`): sino + Registros da Cidade.
- **Painel em menu circular** (`TownHallScreen`, `radial_town_hall.json`):
  - Mapa, Moradores, Obras, Regras e impostos, Progresso, Prédios e Rank;
  - cada um abre a página do Blueprint (`new BlueprintScreen(page)`).
- **Mapa:** a área da vila aparece tracejada em dourado. No código ela é um **retângulo** (prédios + 48 blocos de margem, `Village.BORDER_MARGIN`), então o desenho segue esse retângulo e não um círculo.

Segunda rodada (2026-10-28), também sem teste em jogo:
- **Villager líder** (`VillageLeaders`, `TownHallData`):
  - o morador adulto com mais Sabedoria (ou inteligência, sem atributos), com forte preferência por idosos e um pouco de Social;
  - se ele morre ou se muda, outro é escolhido do mesmo jeito, com aviso aos jogadores na vila;
  - o jogador com rank prefeito ou acima também é chefe; o líder villager age por conta própria: uma vez por dia move um morador para um posto livre em que encaixe bem melhor (nunca um que o chefe fixou à mão).
- **Painel de Empregos** (`TownHallJobsScreen`, `JobAssignments`):
  - lista de moradores com profissão e aptidão (●●●○○), o líder primeiro;
  - ao escolher um, os postos da vila em ordem de encaixe;
  - o chefe clica e o villager vai para o posto e fica fixado; "Deixar o líder decidir" solta;
  - o servidor confere o rank.
- **Visão geral:** líder, se você é o chefe e **todos os impostos**:
  - taxa;
  - cobrança periódica (valor por estação, intervalo, próxima, bônus da biblioteca, aviso sem armazém) ou parte da produção real com a economia;
  - limites de nascimentos e casamentos;
  - botão para mudar as regras.
- **Prefeitura desde o início** (`villageTownHallAtStart`, ligado): cada vila ganha os Registros da Cidade ao lado do sino, uma vez, em chão livre.

Terceira rodada (2026-10-28), sem teste em jogo:
- **Placa de loja** no Village Furnishings (`sign/`):
  - `*_shop_sign` em 12 madeiras: um braço de ferro saindo da parede e a tábua pendurada de lado para a rua;
  - o ícone (qualquer item) aparece nas duas faces.
- **Seletor de ícone** (`ShopSignScreen`): clicar na placa abre a tela.
  - Sugestões pelos blocos de trabalho a até 6 blocos (`SignIcons.HINTS`), incluindo os do MCA (`mca:chopping_block` → machado, `mca:city_records` → sino…).
  - Emblemas comuns.
  - Busca em todos os itens.
  - "Em branco" limpa o ícone.
  - Agachado com um item na mão, o item vai direto para a placa.
  - A escolha vai ao servidor pelo pacote `SetSignIcon`, conferido pela distância.

Falta: a questline (adiada, abaixo); construtores pendurarem placas sozinhos.

## Decisões já tomadas
- **Chefe:** os dois. O jogador com rank (prefeito ou monarca) manda; sem jogador, um **villager líder** organiza a vila.
- **Pontos de nível:** quem distribui é o chefe, seja o jogador ou o líder.
- **Centro da vila:** a prefeitura **incorpora** o sino e não o substitui. O sino continua marcando o centro.
- **Placa de estabelecimento:** o bloco fica no Village Furnishings, e o MCA sugere o ícone pelo tipo de prédio.

## 1. Prefeitura e Registros da Cidade
- **Tipo de prédio novo:** `town_hall` (Prefeitura), em `data/mca/building_types/town_hall.json`.
  - Exige **sino + Registros da Cidade** dentro do prédio.
  - Junta-se ao `town_center` que já existe: a tag `#mca:town_center` hoje só tem `minecraft:bell`, e o tipo é `grouped` com `mergeRange` 32.
- **Bloco "Registros da Cidade"** (`mca:city_records`):
  - uma base como a do púlpito, com o **livro animado da mesa de encantamento** em cima;
  - o renderer do bloco-entidade usa o `BookModel` vanilla e a textura `entity/enchanting_table_book`;
  - o livro abre quando há um jogador perto e folheia, como na mesa de encantamento;
  - só a base precisa de arte.
- **Abertura:** usar o bloco abre a tela da prefeitura.
  - Hoje o Blueprint abre por `BlueprintItem.use()` → `OpenGuiRequest(Type.BLUEPRINT)` → `ClientHandlerImpl` → `new BlueprintScreen()`.
  - O bloco segue o mesmo caminho em `useWithoutItem`, com um tipo novo de GUI.
- **Progressão:** o estágio `hamlet` hoje pede `town_center`. A prefeitura pode entrar em `village` ou `thriving_village` (`data/mca/village_stages`).

## 2. Painel da prefeitura (menu circular)
- **A tela:** uma subclasse de `AbstractDynamicScreen` com `useRadialLayout() = true`, usando `addRadialRing` e `RadialEntry`. O menu circular já mora na classe base, não só na tela do villager.
  - O layout fica em `assets/mca/api/gui/radial_town_hall.json`.
  - `RadialIcons` é package-private e precisa ficar público, ou a tela fica em `client.gui`.
- **Fatias:**

  | Fatia | O que abre |
  |---|---|
  | Mapa e prédios | página `map` / `advanced` do `BlueprintScreen` |
  | Moradores | `villagers`, com ficha (atributos, profissão, posto) |
  | Empregos | novo: postos de trabalho e quem está em cada um (seção 3) |
  | Obras | `WorkPlanningScreen` (work orders) |
  | Regras e impostos | `rules`: impostos e limites de nascimento e casamento |
  | Progresso | `ProgressPage` |
  | Missões | a questline (seção 5) |
  | Rank | `rank` |

- **Fase 1:** cada fatia abre a página do `BlueprintScreen` que já existe, com o mesmo `GetVillageRequest` / `GetVillageResponse`. Depois, as páginas ganham layout próprio.
- **Mapa:** desenhar um **círculo ou contorno com a área da vila**, a mesma borda que o `VillageGrowth` usa para decidir quem está "dentro da vila".
- **Segurança (corrigir já):** `network/c2s/SaveVillageMessage.handleServer` aceita impostos e limites de quem mandar o pacote. O rank só é conferido na GUI, então é preciso validar no servidor.

## 3. Empregos: o chefe escolhe o posto
- **Hoje:**
  - O jogador com rank monarca troca só as profissões do MCA (`VillagerCommandHandler`, case `"profession"`): guarda, arqueiro, construtor, minerador e carregador.
  - O comando `setworkplace` (`Residency.setWorkplace(player)`) pega o POI livre mais próximo.
  - Os empregos vanilla são escolhidos sozinhos pelo `AptitudeJobTask` e pelas tasks vanilla de POI.
- **Novo:**
  - API "prender a um posto": uma variante de `Residency.setWorkplace` que recebe um `BlockPos`, valida o POI, ajusta a profissão e grava o `JOB_SITE`;
  - painel de Empregos com a lista de villagers (profissão, posto e **aptidão** pelo atributo, ●●●○○);
  - o chefe escolhe o villager e clica num posto livre no mapa, como arrastar o morador para uma sala no Fallout Shelter.
- **Villager líder (sem jogador com rank):**
  - escolha: idoso ou adulto com a maior soma de opinião dos outros e Social alto;
  - ele redistribui os postos de tempos em tempos, pela aptidão (estende `AptitudeJobTask`);
  - ele gasta os pontos de nível dos moradores.

## 4. Placa de estabelecimento
O bloco fica no **Village Furnishings**:
- pendurada na parede ou numa viga, como a placa suspensa vanilla;
- o ícone escolhido numa tela é um **emblema** (pão, martelo, livro, peixe, espada…) ou **qualquer item**;
- o item é desenhado pelo modelo do item, como no Tabletop.

O que o MCA faz:
- num prédio reconhecido, ele sugere o emblema pelo `BuildingType` (padaria → pão, ferreiro → bigorna…);
- os construtores podem pendurar a placa ao terminar um prédio.

## 5. Questline: "O ancião passa o posto"
- **A história:** o líder atual é um idoso, o que aproveita o `AgeState.ELDER`, e ele quer passar o posto a alguém mais jovem. Ele conversa com o jogador e orienta os próximos passos.
- **Os passos vêm da progressão que já existe:**
  - `VillageProgress.evaluate(...).next()` dá o próximo estágio (camp → hamlet → village → thriving_village → town → city) e o que falta (`Line(label, have, need)`);
  - cada passo ganha falas próprias, com uma pergunta nova no diálogo: "O que a vila precisa?";
  - os avisos de `VillageGrowth.check` ("a vila virou…") viram marcos da missão;
  - uma conquista nova (`advancement`) por marco.
- **Estado:** em `ProgressData` (SavedData por vila): passo atual, ancião, sucessor.
- **Fim:** o ancião escolhe o sucessor (o jogador vira prefeito ou um jovem assume) e se aposenta, com mais tempo livre e falas de idoso.
- **Recompensas:** reputação, plantas de prédios (Blueprint) e itens do baú do ancião, reaproveitando o *escrow* dos pedidos (`expansion/requests`).
- O sistema de pedidos só suporta "entregar" e "matar". A questline precisa de estado próprio, mas reaproveita as recompensas e o quadro.

## Decisões (2026-10-28)
- **Líder:** jogador ou NPC. Se o NPC morre, outro NPC é escolhido pela Sabedoria, com preferência por idosos.
- **Impostos:** todos aparecem no painel.
- **Prefeitura:** existe desde o início (Registros ao lado do sino). Se não existir, o ancião orienta a construir na questline.
- **Questline:** **adiada**, ainda vamos definir.
  - Liga por config.
  - O jogador pode tê-la ativa e fazer tudo à mão, sem falar com o ancião.
