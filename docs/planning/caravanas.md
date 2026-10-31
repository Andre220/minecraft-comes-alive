# Caravanas: comércio de verdade entre as vilas (WIP)

> **Status: feito.** Planejado e implementado em 2026-10-29 (ver Estado); falta testar em jogo. Substitui o mercador ambulante vanilla (um villager e duas lhamas) por uma **caravana de 3 a 5 pessoas e alguns animais**, que viaja de fato entre as vilas trazendo mercadoria. Conversa com [faccoes.md](faccoes.md) (saques) e com a economia ([bountiful-producao-coletor.md](bountiful-producao-coletor.md)).

## Estado (2026-10-29)
Feito, sem commit e sem teste em jogo, com a flag `villagerCaravans` (ligada no cliente de desenvolvimento):
- **Formação** (`expansion/caravan/Caravans`, `CaravanData`):
  - numa vila conhecida (visitada por um jogador), com jogador nela, chance por minuto (`caravanChancePerMinute`, padrão 0,05) até `caravanMaxActive` caravanas (padrão 2);
  - aparece na borda da vila: 1 mercador (`mca:caravaneer`, com 6 trocas sorteadas de 17), 1 ou 2 guardas (`mca:caravan_guard`, espada ou besta e escudo) e até 2 carregadores;
  - 2 a 4 animais com baú (lhamas em montanha, neve, savana e deserto; mulas e burros no resto), puxados pela guia, com mercadoria do bioma e às vezes um item raro.
- **Andar de verdade:** o líder caminha por trechos de 24 blocos até o centro da vila e os outros seguem. Param à noite; guardas em luta são deixados lutar.
- **Na vila:** fica `caravanStayTime` (1 dia) e parte para outra vila conhecida a até `caravanMaxDistance` (2000 blocos), de preferência uma das três mais próximas. Sem destino, vai embora e some longe dos jogadores.
- **Fora de vista:** viaja pela estrada sem existir no mundo e reaparece na borda do destino com as mesmas pessoas (foto tirada a cada minuto), quando houver jogador lá. As cópias antigas, em chunks não carregados, somem ao serem vistas (geração na tag).
- **Avisos:** "A caravana de X chegou a Y", "parte para Z", "segue para terras distantes".
- **Mercador ambulante vanilla:** com caravanas ligadas, vem uma vez a cada `wanderingTraderRarity` (4).

Fases 3 a 5 (2026-10-29):
- **Armazém** (`CaravanTrade`): ao chegar, a caravana negocia de verdade com o armazém da vila. Até 4 vendas e 4 compras, a 1 esmeralda por 12 itens; o chat resume a troca.
  - Vende o que a vila tem pouco (menos de 16) pelas esmeraldas do armazém.
  - Compra o que sobra (mais de 128) com as esmeraldas que carrega.
  - Os itens passam entre os baús dos animais e os contêineres do armazém.
- **Estrada** (`CaravanRoad`):
  - **Emboscada:** em território de facção, uma vez por viagem, 2 ou 3 saqueadores da facção aparecem e atacam a caravana ("Os Corvos de X atacam a caravana!").
  - **Fora de vista:** uma estrada que cruza território de facção pode custar uma pessoa e um animal ("chega desfalcada").
  - **Escolta:** quem anda junto pelo menos 1 minuto da viagem ganha 10 corações na vila de chegada e 3 a 6 esmeraldas do mercador.
  - **Saque:** matar alguém da caravana custa 15 corações nas vilas da rota e dá +2 com as facções.
- **Conversa** (`CaravanTalk`, constraint `caravan`):
  - "De onde vocês vêm?": a última vila e a próxima.
  - "Alguma notícia?": boato de uma vila que ninguém achou ainda (nome, direção e distância aproximada), ou do território da facção mais próxima.

Falta: encomendas (pedir um item para a próxima viagem).

## O que já existe e será reaproveitado
- **Taverna:** `VillageInnManager` já faz aparecer viajantes (aventureiros, cultistas e o mercador ambulante, `innSpawnsWanderingTraders`). As caravanas podem pernoitar ali.
- **Economia:** cada vila tem armazém, oficinas e produção real, com teto por item (`economyStorageCap`). As sobras viram carga; as faltas, encomendas.
- **Pedidos e quadro de pedidos** (`expansion/requests`): base para "escoltar a caravana" e "levar uma encomenda".
- **Barganha** (`expansion/barter`): negociar preço com o villager.
- **Boatos** (`data/mca/dialogues/rumors.json`): a caravana traz notícias de outras vilas e das facções.
- **Gente do MCA:** nomes, aparência, traços, humor e opiniões valem para cada membro.

## 1. A caravana
- **Membros:**
  - **mercador-chefe:** negocia e decide a rota;
  - **1 ou 2 guardas:** armados, com escudo e besta ou espada;
  - **0 a 2 carregadores ou aprendizes:** cuidam dos animais.
- **Animais:** 2 a 4, de acordo com o bioma de origem: lhamas e mulas com baú, camelos no deserto, cavalos de carga nas planícies.
  - Os baús dos animais são a carga de verdade: dá para ver e, para um fora da lei, roubar.
- **Nome:** "Caravana de Rakel": o nome vem do mercador-chefe. A caravana **não tem vila de origem**: vive na estrada.
- **Profissão nova:** `mca:caravaneer` (mercador de caravana), com trocas próprias. Os guardas reaproveitam o `mca:guard`.
- **Mercador ambulante vanilla:** continua no jogo, só que mais raro.

## 2. Rotas e viagem
- **A rota:** a caravana **vaga de vila em vila**, só entre as vilas que o jogador já conhece. O próximo destino é, de preferência, uma vila que precisa do que ela carrega ou que tem sobra do que ela procura.
- **Viagem real:** com o caminho carregado perto do jogador, a caravana anda de verdade, em fila, com os animais presos pela guia. De noite, ela para numa taverna ou acampa com fogueira.
- **Viagem simulada:** em chunks descarregados, ela "avança" pela rota sem existir no mundo e reaparece onde estiver quando o jogador chegar perto ou quando chegar ao destino. Assim não pesa no servidor.
- **Na vila:** fica alguns dias.
  - vende e compra com o jogador e com o armazém da vila;
  - dorme na taverna;
  - parte para o próximo destino. Não há volta para casa.
- **Frequência:** config. Por exemplo, uma caravana viva por vila com taverna, e uma nova a cada X dias de jogo.

## 3. Mercadoria
- **Da estrada:** o que ela comprou nas últimas vilas, como pão, tijolos ou lã, além de produtos típicos dos biomas por onde passou (deserto: areia, cacto e cerâmica; neve: gelo e peles; selva: cacau e bambu). A carga muda a cada parada.
- **Raros:** um ou dois itens especiais por viagem, como mapas de estrutura, encantamentos, mudas e sementes de outros biomas.
- **Troca com a vila:** a caravana compra a sobra do armazém e vende o que a vila não produz. As esmeraldas vão para o cofre da vila e são taxadas como produção. Isso dá à economia um motivo para ter sobra.
- **Encomendas:** o jogador (ou o chefe da vila, pelos Registros da Cidade) pode encomendar um item a uma caravana, que o traz na próxima viagem.

## 4. Riscos: escolta e saque
- **Ataques:** facções e monstros podem atacar caravanas na estrada.
  - Os guardas defendem; o jogador pode ajudar.
  - Se a caravana chegar inteira, a reputação sobe com a vila de onde ela saiu e com a de destino.
- **Escolta:** um pedido no quadro. "Leve a caravana de Rakel em segurança até Dirtwich" paga esmeraldas e reputação.
- **Saque:** um fora da lei, ou o jogador de uma facção, pode atacar a caravana.
  - Cada baú roubado sobe a reputação com a facção e derruba a das vilas da rota.
  - Os sobreviventes espalham a notícia (boato com o nome do jogador).
- **Simulado:** sem o jogador por perto, o risco é um sorteio pela força da facção da região contra os guardas.

## 5. Conversa
- **Interação:** a mesma tela dos villagers, com perguntas próprias para a caravana: "De onde vocês vêm?", "Para onde vão?", "O que trazem?" e "Alguma notícia?".
- **Boatos:**
  - de outras vilas, inclusive as que o jogador **ainda não conhece**: o nome e uma pista de onde ficam ("Dizem que tem uma vila grande ao norte, depois do deserto, chamada Kerrow"). A caravana não vai até lá enquanto o jogador não a achar;
  - das vilas conhecidas: estágio, o que falta, quem casou ou morreu;
  - das facções: território e guerras.
- **Membros:** cada um tem traços, humor e opiniões; um guarda Brigão e um mercador Materialista mudam a conversa.

## Fases
1. **Caravana básica:** grupo de 3 a 5 villagers do MCA mais animais com baú; o mercador ambulante fica mais raro (config); viagem real entre duas vilas conhecidas próximas; trocas com o jogador.
2. **Viagem simulada e rotas:** caravanas atravessam o mapa descarregado, a frequência fica por vila e as caravanas pernoitam na taverna.
3. **Comércio com a vila:** compra e venda com o armazém, mercadoria por bioma, encomendas.
4. **Riscos:** ataques, escolta como pedido, saque e reputação (depende das facções).
5. **Conversa:** perguntas e boatos.

## Decisões (2026-10-29)
- **Rotas:** só entre vilas que o jogador **já conhece**, mas a caravana **fala** das que ele ainda não conhece (boatos com nome e direção).
- **Mercador ambulante vanilla:** continua existindo, **mais raro**. Config: `wanderingTraderRarity`, que multiplica a chance vanilla; o padrão divide por 4.
- **Sem vila de origem:** a caravana vaga de vila em vila, sem casa.
- **Transporte:** animais com baú, sem carroças.
