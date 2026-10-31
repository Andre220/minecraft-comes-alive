# MCA Reborn: catálogo de addons

Data: 2026-10-30. Este é o catálogo do que o fork tem hoje, agrupado nas expansões que vão virar addons instaláveis, no estilo The Sims: cada uma traz mecânicas, itens e conversas novas que conversam com o que já existe.

Plano aprovado:
- **Fase 0 (este documento):** catálogo e lista de refinamento.
- **Fase 1:** API de addons no núcleo, ainda num jar só.
- **Fase 2:** migrar addon por addon.
- **Fase 3:** um jar por addon.

Decisões:
- a base se chama **MCA Reborn** e mantém o id `mca`, para os mundos continuarem compatíveis;
- os addons ganham ids próprios (`mca_<tema>`);
- os addons são pacotes temáticos grandes;
- a separação é feita em etapas.

## Visão geral

| Addon | Id previsto | Tamanho hoje | Ganchos no núcleo | Estado em jogo |
|---|---|---|---|---|
| Base: MCA Reborn | `mca` | — | — | partes testadas |
| Sentimentos | `mca_feelings` | traços, crises, luto, idoso | muitos, sem marca (`VillagerEntityMCA`, `Relationship`, `SchedulesMCA`) | **sem teste** |
| Vida Social | `mca_social` | conversas, rotinas, fala | 11 `speech` + 3 `talk` + parte de `minds` | balões testados; rotinas sim; painéis de conversa não |
| Fome e Sede | `mca_needs` | necessidades, bebidas, dieta | sem marca (`entity/ai/Needs*`) | testado |
| Talentos | `mca_talents` | atributos, habilidades, escola | parte dos 29 de `minds` | habilidades testadas (GameTest); atributos **sem teste** |
| Propriedade e Crime | `mca_crime` | posse, cadeados, furto | sem marca (`server/property`) | testado |
| Trabalho e Economia | `mca_work` | profissões, produção, obras | 6 `economy` + 5 `barter` + 4 `requests` + 2 `work orders` + 1 `tabletop` | testado (GameTests) |
| Prefeitura e Progresso | `mca_town` | prefeitura, painéis, estágios | 7 `town hall` + 5 `progression` | grade de moradores testada; o resto **sem teste** |
| Estilo | `mca_style` | guarda-roupa, espelhos, barbas | 21 `looks` + 5 `mirrors` | testado |
| Bandos e Estradas | `mca_roads` | facções, bandidos, caravanas | 9 `factions` + 5 `caravans` | **sem teste** |
| Integração Fresh Animations | `mca_fa` | pacote FA, boca, pupilas | parte de `looks` | testado |

Os sistemas **sem marca** `// expansion:` vieram do primeiro commit da expansão (`96d5abbdc`) e estão entranhados no núcleo. São os mais trabalhosos de separar: Fome e Sede, Propriedade e Crime e Sentimentos.

## Base: MCA Reborn (`mca`)

O MCA original mais a infraestrutura que todo addon usa:
- **IA e navegação:** `entity/ai/navigation/*`, camas (`BedPoiCompatibility`), abrigo (`SeekIndoorShelterTask`, `ShelterSleepTask`), portas e portões (`villagersInteractWithAnyDoor`, `villagersInteractWithFenceGates`), combate à distância (`RangedCombat*`), fila de ações (`entity/ai/queue`, `villagerActionQueue`, `villagerActionLabels`).
- **Vila:** scanner de prédios e cômodos (`server/world/data/Room*`, `Structure*`, `Floor*`), mapa do Blueprint (`client/gui/BlueprintMap*`), localizador de vilas (`expansion/VillageFinder`), árvore genealógica nova (`FamilyTree*`).
- **Visual:** modelos humanos sobre o villager vanilla (`VanillaVillagerLook`, `squidward*`), camadas 3D (`client/render/voxel`, `squidward3dLayers`, `player3dLayers`), pose de trabalho (`WorkAnimation`, `WorkAtJobSiteTask`).
- **Interface:** cartão de interação, menus radiais (`radialInteractMenu`), sprites e botões (`mcaButtonStyle`), editor com grades de prévia (`LookGrid`), livro-guia (o framework, em `ExpansionGuide`).
- **Outros:** Chat AI e destinos (`destiny*`).
- **A API de addons** (fase 1).

Flags: `archerArrowsIgnoreVillagers`, `autoDiscoverDestinyLocations`, `destinyDimensionBlacklist`, `destinyOverworldOnly`, `destinySpawnLocationBlacklist`, `squidwardGeneticHitbox`, `villagersChangeProfession`, `villagersInteractWithAnyDoor`, `villagersInteractWithFenceGates`, `whistleCallMakesVillagersFollow`, `villagerActionQueue`, `blueprintMaxSize`, `mcaButtonStyle`, `player3dLayers`, `radialInteractMenu`, `squidward3dLayers`, `squidwardBabySize`, `squidwardChildSize`, `squidwardHeightVariation`, `squidwardHumanLikeModels`, `squidwardTeenSize`, `squidwardToddlerSize`, `villagerTagBlacklist`, `villagerActionLabels`.

Capítulos do guia: Introdução, Aparência, Menu radial, Fila de ações, Agenda, Depuração, Outros mods e janelas.

## Sentimentos (`mca_feelings`)

**O que traz:**
- traços estilo The Sims, com 1, 3 e 5 espaços por idade, a personalidade fundida aos traços e buffs;
- traços do RimWorld, mais noturno, piromaníaco e sanguinário;
- crises mentais, que podem ser letais (opção);
- luto;
- a fase idoso: postura curvada, falas próprias, jeitos de andar e morte por velhice.

**Código:** `entity/ai/traits/*` (`Traits`, `PersonalityTraits`, `MentalBreaks`, `TraitBehaviors`), `entity/ai/Mourning`, `relationship/Personality`, mais os trechos em `VillagerEntityMCA`, `Relationship`, `SchedulesMCA` (noturno) e `VillagerEntityBaseModelMCA` (postura do idoso).

**Flags:** `traitSlotsToddler`, `traitSlotsChild`, `traitSlotsTeen`, `traitSlotsAdult`, `traitSlotsElder`, `villagerMentalBreaks`, `villagerMentalBreakLethal`, `villagerElderAfterTime`, `villagerOldAgeDeath`, `villagerOldAgeDeathAfterTime`, `villagerOldAgeDeathChance`, `enableMourning`.

**Conversa com:**
- Vida Social: opiniões e conversas mudam conforme os traços;
- Talentos: traços dão bônus de atributo;
- Fome e Sede: fome e sede baixam o humor;
- Propriedade e Crime: cleptomaníaco.

**Docs:** [tracos-sims.md](tracos-sims.md), [tracos-rimworld.md](tracos-rimworld.md).

**Cuidado:** o cérebro é montado no construtor, quando os traços ainda são nulos. Uma exceção nesse ponto apaga villagers do save; veja a memória `mca-brain-constructor-gotcha`.

## Vida Social (`mca_social`)

**O que traz:**
- NPCs conversando entre si e opiniões;
- balões de fala com digitação e som;
- painéis de informação revelados pelas perguntas da conversa ("Como você está?", "No que você é bom?"…);
- tempo livre por oportunidades (sentar, ler, admirar, tocar música, banquetes);
- persianas e lampiões abertos e acesos na hora certa.

**Código:** `relationship/Conversations`, `Opinions`, `expansion/speech/*`, `expansion/talk/TalkPanels`, `expansion/minds/routine/*` (`Opportunities`, `Seats`, `DailyToggles`, `Feasts`, `IdleRoutineTask`), `NeedsSocializeTask`. Dados: `data/mca/opportunities`, `data/mca/daily_toggles`.

**Flags:** `villagerRoutines`, `villagerInfoInTalk`, `villagerSpeechBubbles`, `speechBubbleSound`, `speechBubbleLettersPerSecond`.

**Conversa com:** Sentimentos, Fome e Sede (a necessidade social).

**Docs:** [interface-e-conversas.md](interface-e-conversas.md).

## Fome e Sede (`mca_needs`)

**O que traz:**
- necessidades: fome, sede, energia, diversão, segurança e social;
- bebidas, limpas e sujas;
- água em caldeirões, pias e **poços** (2026-10-31): boca cercada por cercas ou muros, com água até 12 blocos abaixo (`Wells`, tag `mca:well_rims`);
- **fazer pão** (2026-10-31): com fome e sem comida, o villager pega trigo em casa e assa na mesa de trabalho (`NeedsBakeBreadTask`);
- buscar comida em casa;
- dieta e provisões;
- baús de casa;
- indicadores sobre a cabeça.

**Código:** `entity/ai/Needs`, `NeedIndicators`, `VillagerDrinks`, `tasks/Needs*Task`, `expansion/minds/Diet`, `Provisions`, `HomeQuality`, `expansion/HomeChests`.

**Flags:** `villagerNeeds`, `needsHungerPerDay`, `needsThirstPerDay`, `needsEnergyAwakeHours`, `needsFunWorkPerDay`, `needsSafetyPerMonster`, `needsSocialPerDay`, `needsStarvation`.

**Capítulo do guia:** Necessidades.

**Conversa com:** Sentimentos (humor), Trabalho e Economia (comida produzida), Vida Social.

## Talentos (`mca_talents`)

**O que traz:**
- seis atributos (Força, Percepção, Vigor, Destreza, Sabedoria e Social), de 1 a 50;
- níveis e pontos distribuídos pelo chefe ou pelo jogador;
- habilidades por profissão;
- aptidão para o emprego;
- escola das crianças e ensino do sábio;
- treino de arco;
- preço das trocas pelo atributo Social.

**Código:** `expansion/minds/VillagerAttributes`, `AttributeMessages`, `Skills`, `AptitudeJobTask`, `SchoolTask`, `ArcheryPracticeTask`, `ActionLabels`.

**Flags:** `villagerAttributes`, `villagerSkills`, `villagerArcheryPractice`.

**Capítulo do guia:** Aldeões mais espertos.

**Conversa com:** Prefeitura (painel de empregos e atributos), Trabalho e Economia (velocidade e qualidade), Sentimentos.

**Docs:** [atributos-e-niveis.md](atributos-e-niveis.md).

## Propriedade e Crime (`mca_crime`)

**O que traz:**
- donos de casas e baús;
- cadeados, pé de cabra e gazua;
- furto e flagra: testemunhas, corações perdidos e fora da lei.

**Código:** `server/property/*` (`PropertyRules`, `PropertyCrimes`, `PadlockDisplays`), `server/world/data/PropertyManager`, os itens `CROWBAR`, `LOCKPICK` e `PADLOCK`.

**Flags:** `villagerProperty`, `propertyFriendHearts`, `propertyOpsBypass`, `crimeOwnerHearts`, `crimeSightRange`, `crimeWitnessHearts`, `forceOpenSeconds`, `lockpickSeconds`.

**Capítulo do guia:** Propriedade e crimes.

**Conversa com:** Sentimentos, Bandos e Estradas (o caminho fora da lei), Trabalho e Economia (oficinas com dono).

## Trabalho e Economia (`mca_work`)

**O que traz:**
- lenhador, caçador, coletor e carregador;
- produção de verdade nas oficinas e armazém da vila;
- ordens de obra com planejamento RTS e construtores;
- barganha com o inventário do villager;
- pedidos e o quadro de pedidos;
- produção exposta na mesa.

**Código:** `expansion/economy/*`, `server/world/data/workorder/*`, `client/gui/workorder/*`, `tasks/workorder/WorkOrderTask`, `expansion/barter`, `expansion/requests`, `expansion/tabletop`. Dados: `data/mca/production`.

**Flags:** `villagerEconomy`, `economyStorageCap`, `economyWorkRadius`, `economyWorkshopsPerHauler`, `villagerWorkOrders`, `workOrderBlocksPerTick`, `workOrderMaxPerVillage`, `workOrderMaxVolume`, `workOrderMinRank`, `workOrderSpeedMultiplier`, `villagerBarter`, `villagerRequests`.

**Capítulos do guia:** Obras da vila, Coletores, Oficinas, Carregador, Pedidos.

**Conversa com:** Talentos, Prefeitura, Bandos e Estradas (as caravanas negociam com o armazém), Fome e Sede.

**Pergunta aberta:** as ordens de obra ficam na base, para ter construtores sem a economia?

## Prefeitura e Progresso (`mca_town`)

**O que traz:**
- prefeitura com os Registros da Cidade e menu radial próprio;
- líder (jogador ou NPC, com sucessão pela sabedoria);
- todos os impostos no painel;
- grade de moradores com inspetor e atribuição de emprego;
- estágios de crescimento da vila;
- a questline do ancião, que foi adiada.

**Código:** `expansion/townhall/*`, `expansion/progression/*`, `client/gui/TownHallScreen`, `TownHallJobsScreen`, `ResidentsScreen`, `ProgressPage`.

**Flags:** `villageProgression`, `villageTownHallAtStart`.

**Capítulo do guia:** A vila cresce.

**Conversa com:** Talentos, Trabalho e Economia, Bandos e Estradas (território no mapa).

**Docs:** [gestao-da-vila.md](gestao-da-vila.md).

## Estilo (`mca_style`)

**O que traz:**
- guarda-roupa, cômoda e trajes que os villagers vestem;
- espelhos (pequeno e de pé) com reflexo ao vivo;
- barbas;
- olhos 6×6 com pupilas pintadas e piscar.

**Código:** `expansion/wardrobe/*`, `expansion/mirror/*`, `expansion/looks/Beards`, `client/resources/EyeTextureLayers`.

**Flags:** `villagerWardrobes`, `villagerMirrors`, `mirrorReflections`, `villagerBeards`, `mcaEyeBlinking`.

**Conversa com:** Integração FA, Vida Social (vestir-se faz parte da rotina).

**Docs:** [espelho-estudo.md](espelho-estudo.md).

## Bandos e Estradas (`mca_roads`)

**O que traz:**
- facções com nome, bandeira, bases e território;
- reputação por jogador;
- bandidos: saqueadores que viram gente do MCA;
- guerras entre facções;
- tréguas e contratação;
- caravanas de 3 a 5 pessoas com animais, que comerciam entre vilas, sofrem emboscadas e aceitam escolta;
- o vendedor ambulante mais raro.

**Código (já migrado, fase 2):** `addon/roads/RoadsAddon` (o `McaAddon`), `addon/roads/faction/*`, `addon/roads/caravan/*`, `addon/roads/client/FactionMapOverlay`, os mixins em `mixin/addon/roads/`. O núcleo não chama mais nenhuma classe do addon.

**Flags:** `villagerFactions`, `factionCount`, `factionMax`, `factionTerritoryRadius`, `factionStartReputation`, `factionKillPenalty`, `factionPillagersAsPeople`, `factionWars`, `factionTruceCost`, `factionTruceDays`, `villagerCaravans`, `caravanMaxActive`, `caravanChancePerMinute`, `caravanStayTime`, `caravanMaxDistance`, `wanderingTraderRarity`.

**Conversa com:** Propriedade e Crime (fora da lei), Trabalho e Economia (o armazém), Prefeitura (o mapa).

**Docs:** [faccoes.md](faccoes.md), [caravanas.md](caravanas.md).

## Integração Fresh Animations (`mca_fa`)

Compatibilidade, não um pacote de conteúdo:
- o pacote de recursos `mca_fresh_animations` (`villager.jem`, `villager2.jem`, `zombie_villager.jem`);
- boca que fala (variável EMF `mca_talk`);
- pupilas móveis do FA;
- posturas de trabalho sobre o FA.

**Código:** `client/FreshAnimationsIntegration`, os trechos de `DynamicSkinCache` (`freshAnimationsPupils`), `McaVillagerFeatureSlots`, `VillagerParts.poseNow` (EMF).

**Flags:** `freshAnimationsIntegration`, `villagerMouthAnimation`.

**Conversa com:** Estilo, Vida Social (a boca fala junto com o balão).

## Integrações com outros mods

Hoje toda integração é por **arquivo de dados com `"optional": true`**: o arquivo é ignorado quando o mod não está instalado. A exceção são os nossos próprios mods, chamados por código.

**Regra na separação:** cada arquivo de compatibilidade vai com o addon cuja mecânica ele usa.

**Legenda:** ✅ feito · 💡 possível.

| Mod | Addon(s) | Estado |
|---|---|---|
| Farmer's Delight | Trabalho e Economia, Vida Social, Fome e Sede | ✅ 7 receitas da cozinha da taverna (`production/compat_farmers_delight_*`), banquetes e fogão (`opportunities/compat_farmers_delight_*`); 💡 pratos do FD como comida nas necessidades |
| Storage Delight | Trabalho e Economia, Vida Social | ✅ baús na tag `chests`, ler nos armários |
| Tough As Nails | Fome e Sede | ✅ bebidas na sede (`VillagerDrinks`, `tags/item/villager_*drinks`); 💡 temperatura levando a abrigo e agasalho |
| Beautify | Vida Social | ✅ persianas e lâmpadas (`daily_toggles`), admirar, ler, botânico |
| Tables & Chairs 2 | Vida Social | ✅ sentar em cadeiras e bancos |
| Items Displayed | Vida Social; 💡 Trabalho e Economia | ✅ olhar itens expostos; 💡 vitrine de loja |
| Immersive Melodies | Vida Social, Sentimentos | ✅ tocar no tempo livre, banda, instrumento como presente; 💡 música nos banquetes e humor |
| Bountiful | Trabalho e Economia | 💡 agora tem versão 26.2: pedidos dos villagers nos quadros do Bountiful |
| Visual Workbench | Trabalho e Economia | 💡 itens visíveis na mesa de trabalho durante o trabalho |
| Animated Doors | base | 💡 conferir as portas abertas por villagers |
| Diagonal Walls | base | 💡 o scanner de prédios reconhecer as paredes diagonais |
| Village Furnishings (nosso) | Vida Social, Fome e Sede, Prefeitura e Economia | ✅ persianas e janelas, pias como água potável, placa de estabelecimento com ícone sugerido |
| Tabletop (nosso) | Trabalho e Economia, Vida Social | ✅ produção exposta, itens em mesas (`ProductionSink`, `ContainerOpportunities`, `PlacedItems`) |
| Grounded Villages (nosso) | base, Prefeitura | 💡 a prefeitura usar as ruas e a praça |
| Better Village, Improved Village Placement, Tectonic, TerraBlender, Lithostitched | base | 💡 conferir o scanner e o localizador nas vilas desses mods |
| Fresh Animations | Integração FA | ✅ |
| First Person | base | ✅ a config esconde os braços |
| Not Enough Animations | base | 💡 conferir o conflito com o modelo de jogador do MCA |
| Sodium, Voxy | base | só desempenho |
| Fabric API, Kotlin, Cloth Config, Forge Config API Port, GlitchCore, PuzzlesLib, LibraryFerret | — | bibliotecas dos outros mods |

## Ganchos no núcleo por addon

As marcas `// expansion: <tema>` mostram onde o núcleo chama cada addon. Na fase 2, cada uma vira um evento ou um registro da API.

| Marca | Vezes | Addon |
|---|---|---|
| `minds` | 29 | Talentos, Vida Social (rotinas) e Sentimentos |
| `looks` | 21 | Estilo e Integração FA |
| `speech` | 11 | Vida Social |
| `factions` | 9 | Bandos e Estradas |
| `town hall` | 7 | Prefeitura e Progresso |
| `economy` | 6 | Trabalho e Economia |
| `progression`, `mirrors`, `caravans`, `barter` | 5 cada | Prefeitura, Estilo, Bandos, Trabalho |
| `requests` | 4 | Trabalho e Economia |
| `all` | 4 | `ExpansionContent.bootstrap` em `BlocksMCA`, `ItemsMCA` e `ProfessionsMCA`; `ExpansionHooks.villageTick` em `Village`: viram a API |
| `talk` | 3 | Vida Social |
| `work orders` | 2 | Trabalho e Economia |
| `village finding`, `tabletop` | 1 cada | base, Trabalho |

**Sem marca**, a mapear na fase 2:
- Fome e Sede: `Needs` e as tarefas de necessidade;
- Propriedade e Crime: `PropertyRules`;
- Sentimentos: traços e luto;
- Vida Social: conversas e opiniões.

## Lista de refinamento: testar em jogo antes de separar

Tudo de 28 a 30/10 está **sem teste em jogo**:
- [ ] **Sentimentos:** traços estilo Sims (espaços, buffs), idoso (postura, falas, jeito de andar), traços do RimWorld, crises mentais, noturno, piromaníaco.
- [ ] **Talentos:** ficha de atributos ("No que você é bom?"), pontos por nível, preços pelo Social, ensino do sábio.
- [ ] **Prefeitura:** registros colocados junto ao sino, menu radial, visão geral com líder e impostos, painel de empregos, sucessão do líder.
- [ ] **Vida Social:** painéis revelados pelas perguntas da conversa.
- [ ] **Bandos e Estradas:**
  - caravanas: chegada, comércio com o armazém, emboscada e escolta;
  - facções: bandeiras nas bases, território na barra de ação e no mapa, bandidos no lugar dos saqueadores, "Quem são vocês?", contratar (confirmar que a fala não mostra a chave crua), trégua, guerras e grupos de guerra.
- [ ] **Interface:** botões escuros do MCA, editor com abas de ícones e grades de prévia, prévia 3D sincronizada, pupila do jogador centralizada.

Bugs conhecidos e abertos: nenhum além dos itens acima.

## A API de addons (fase 1, feita em 2026-10-31)

Pacote `net.conczin.mca.api.addon`:
- **`McaAddon`:** `id()`, `enabled()` e ganchos opcionais:
  - `bootstrap()` (registros, ações e restrições de diálogo);
  - `clientBootstrap()`;
  - `registerMessages()`;
  - `levelTick()`, `villageTick()` e `villagerTick()` (uma vez por segundo);
  - `refusesInteraction()`;
  - `replacesSpawn()` e `replaceSpawn()` (trocar uma entidade que entra no mundo);
  - `fightsLikeMercenary()`.
- **`Addons`:**
  - a lista de addons, com `register()`;
  - as chamadas que o núcleo faz em todos;
  - `Addons.data(villager, id)`, o `CompoundTag` do addon no villager. Ele é gravado na chave `Addons` dos dados do MCA e é **mantido mesmo sem o addon instalado**.
- **`Constraint.register(id, teste)`:** o `Constraint` deixou de ser enum e agora aceita restrições de diálogo dos addons.
- **Cliente:**
  - `MapOverlay`, uma camada sobre o mapa da vila, com `MapView`;
  - `DialogueIcons`, o ícone de uma resposta no menu radial.
- **`addon/BuiltInAddons`:** a lista dos addons que ainda vêm dentro do jar do MCA.
- **`AddonBoundaryTest`:** falha se alguma classe fora de `addon/<id>` (ou de `mixin/addon/<id>`) usar o addon.

Ainda falta na API, e entra quando o addon que precisa for migrado:
- abas do editor;
- capítulos do livro-guia;
- camadas de render;
- fontes de humor;
- config por addon (hoje cada addon lê as flags que já existem).

## Próximos passos

1. ~~Fase 0: catálogo.~~ ~~Fase 1: API.~~ Padrões adotados: as rotinas ficam em Vida Social e as ordens de obra em Trabalho e Economia.
2. Fase 2: ~~Bandos e Estradas~~ (migrado) → Prefeitura → Estilo → Talentos → Trabalho e Economia → Vida Social → Fome e Sede → Propriedade e Crime → Sentimentos → Integração FA.
3. Fase 3: um jar por addon (`addons/<id>` no Gradle).
