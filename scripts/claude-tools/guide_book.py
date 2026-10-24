"""Writes the expansion guide book texts into MCA's lang files and prints the pages per chapter."""
import json
import sys

ROOT = sys.argv[1]  # common/src/main/resources
B = "§l"  # bold
R = "§r"
NAME = "expansion_guide"

# (title, subtitle, pages) per chapter; chapter 0 is the introduction (no chapter title page)
PT = [
    ("Introdução", "", [
        "Este guia explica tudo que a expansão acrescenta ao MCA: aldeões com necessidades, donos e agenda, obras planejadas, uma economia de verdade e mais.",
        "Cada sistema pode ser ligado ou desligado no mca.json: villagerNeeds, villagerActionQueue, villagerProperty, villagerWorkOrders e villagerEconomy. O visual tem opções próprias.",
    ]),
    ("Capítulo 1", "Aparência", [
        f"{B}Aldeões humanos{R}\nCom os modelos squidward, a opção squidwardHumanLikeModels dá aos aldeões o corpo do jogador, com a pele, o rosto, a roupa e o cabelo do MCA.",
        f"{B}Camadas 3D{R}\nsquidward3dLayers transforma chapéu, jaqueta, mangas e calça em pequenos cubos quando o aldeão está perto. player3dLayers faz o mesmo com jogadores.",
        f"{B}Altura{R}\nO gene de altura muda o tamanho de cada aldeão (squidwardHeightVariation) e as crianças crescem por fases. Com Fresh Animations, ligue freshAnimationsIntegration.",
        f"{B}Trabalho{R}\nNo horário de trabalho, o aldeão vai até a mesa de trabalho e trabalha nela um tempo, com a animação da profissão. Depois descansa, passeia e conversa.",
    ]),
    ("Capítulo 2", "Menu radial", [
        "Clique com o botão direito num aldeão: os botões aparecem num círculo, com ícones. Conversar e Interagir são os dois caminhos principais.",
        f"{B}Submenus{R}\nEm Interagir: presente, troca, inventário, árvore da família, Comandos (seguir, ficar, cavalgar), Pedidos e Profissões. radialInteractMenu desligado volta ao menu clássico.",
    ]),
    ("Capítulo 3", "Necessidades", [
        "Seis medidores de 0 a 100 caem com o tempo: Fome, Sede, Energia, Social, Lazer e Segurança. Medidores baixos viram pensamentos que pesam no humor.",
        f"{B}Fome{R}\nO aldeão come do inventário ou de um baú em casa: dê pão, carne ou outra comida. Trabalhar dá mais fome. Faminto, perde vida até meio coração, mas não morre.",
        f"{B}Sede{R}\nBebe parado ao lado de um caldeirão com água: coloque caldeirões na vila.\n{B}Energia{R}\nRecupera dormindo na cama à noite. Sem cama, cochila onde estiver.",
        f"{B}Social{R}\nSobe conversando com você, com outros aldeões e no ponto de encontro do sino.\n{B}Lazer{R}\nTempo livre e prédios favoritos. O trabalho cansa.",
        f"{B}Segurança{R}\nCai quando monstros chegam perto. Recupera em casa, perto de guardas ou golens. Um medidor crítico faz o aldeão largar a rotina para resolver.",
        "Os medidores aparecem na tela de interação: passe o mouse para ver como atender cada um. Casais que dormem juntos ficam mais felizes.",
    ]),
    ("Capítulo 4", "Fila de ações", [
        "Como no The Sims, cada aldeão tem uma fila com o que a agenda, as necessidades e os seus pedidos mandam. Ele faz primeiro o mais urgente. A fila aparece sobre a cabeça.",
        f"{B}Pedidos{R}\nNo menu Pedidos: comer, beber, dormir, ir para casa, trabalhar, socializar ou descansar. Cada item mostra o motivo e a urgência; clique num item para cancelar.",
    ]),
    ("Capítulo 5", "Agenda", [
        "Com o Editor de Aldeão, a aba Agenda mostra o dia do aldeão: Livre, Trabalho, Encontro, Dormir e Brincar. Escolha um modelo ou clique e arraste na linha do tempo para pintar os horários.",
        "A agenda personalizada é salva em Concluído; Restaurar volta à original. Avisos mostram o que falta: sem mesa de trabalho ou sem ponto de encontro, ele fica livre.",
    ]),
    ("Capítulo 6", "Propriedade e crimes", [
        "Camas, mesas de trabalho, baús, portas e alçapões têm dono. O aldeão é dono da cama, da mesa de trabalho e da casa, com o que há dentro. Você é dono do que coloca.",
        f"{B}Quem pode entrar{R}\nCônjuge, família, o prefeito e amigos com 50 corações ou mais (menos na cama). A porta de casa tranca quando todos os moradores estão dentro.",
        f"{B}Cadeado{R}\nReceita: 3 pepitas em arco sobre 3 lingotes. Clique com ele num baú ou porta seu para trancar. Agachado e de mão vazia, você o retira.",
        f"{B}Forçar{R}\nAgachado e de mão vazia, segure usar por 3 segundos.\n{B}Gazua{R}\n2 pepitas na diagonal e 1 graveto. Leva 5 segundos, é silenciosa e gasta uma quando abre.",
        f"{B}Pé de cabra{R}\n4 lingotes em forma de gancho. Abre na hora e destrói o cadeado, mas faz barulho: quem ouve vem ver. O aldeão troca o cadeado depois de um dia.",
        f"{B}Testemunhas{R}\nAldeões veem crimes a até 24 blocos (metade se você está agachado, metade de novo no escuro). Quem vê perde 10 corações com você; o dono, 25.",
    ]),
    ("Capítulo 7", "Obras da vila", [
        "Faça um Blueprint (papel em volta de corante azul) e use-o numa vila. O botão Planejar Obras abre uma câmera livre sobre a vila.",
        f"{B}Controles{R}\nWASD, Espaço e Shift voam. Arraste com o botão direito para olhar. A rolagem muda a altitude. Teclas 1 a 4: ferramentas. Tab: lista de obras.",
        f"{B}Escavar [1]{R}\nMineiros e construtores tiram tudo da área e guardam no depósito.\n{B}Construir [2]{R}\nConstrutores enchem a área com o bloco escolhido, tirado do depósito.",
        f"{B}Quem pode{R}\nNobres ou acima, ou quem segura um Editor de Aldeão (aí sem material). Escolha Construtor ou Mineiro no menu Profissões do aldeão.",
        f"{B}Blueprints{R}\nCapturar [4]: arraste uma caixa em volta do que você construiu e dê um nome. Blueprint [3]: escolha um salvo, gire com R e posicione. Já vêm 4 prontos.",
        "Os construtores limpam o caminho, buscam cada material no depósito e, ao terminar, a obra passa a contar como prédio da vila. Também há o comando /mca workorder.",
    ]),
    ("Capítulo 8", "Coletores", [
        "Com villagerEconomy ligado, a vila produz de verdade. Profissões novas coletam recursos: um aldeão desempregado pega o emprego quando acha a estação livre.",
        f"{B}Estações{R}\nCepo de Corte: sílex sobre tronco. Suporte de Caça: graveto, couro, graveto sobre 3 tábuas. Cesto de Coleta: trigo em forma de U.",
        f"{B}Lenhador{R}\nDerruba árvores perto do cepo, junta maçãs e gravetos e replanta as mudas.\n{B}Caçador{R}\nCaça animais selvagens; poupa os com nome, na corda, cercados e os últimos da espécie.",
        f"{B}Forrageiro{R}\nColhe frutas silvestres sem tirar o arbusto, topos de cana e bambu, cogumelos e flores onde há muitos, e abóboras e melancias selvagens.",
        f"{B}Pescador{R}\nPesca na margem perto do barril.\n{B}Mineiro{R}\nSem obras, tira areia, cascalho e argila da superfície em volta de uma Peneira (tábua, linha, tábua sobre duas tábuas).",
        "Os coletores trabalham até 24 blocos da estação e param de coletar um item quando o depósito da vila já tem 128 dele.",
    ]),
    ("Capítulo 9", "Oficinas", [
        "Aldeões na mesa de trabalho produzem por lotes, com insumos do baú da oficina. A padaria (do açougueiro) faz pão com trigo; o pedreiro faz tijolos com pedregulho.",
        "O ferramenteiro faz pé de cabra, machado e pá; o armeiro faz capacetes e às vezes acha diamante; o clérigo faz redstone com carne podre. As receitas ficam em data/mca/production.",
        f"{B}Divisão{R}\nA parte da vila segue o imposto: com 30%, 30% vai para o baú da oficina e o resto fica com o aldeão. Sem baú, fica tudo com ele. Os impostos antigos deixam de existir.",
        f"{B}Baú da oficina{R}\nÉ de quem trabalha ali e dos carregadores: abrir sem ser dono é roubo. Com o inventário cheio, o aldeão leva a parte dele para um baú da própria casa.",
        f"{B}Fazendeiro{R}\nNão tem receita: a vila recebe a parte dela da colheita de verdade, num baú da oficina ou ao lado do composteiro.",
    ]),
    ("Capítulo 10", "Carregador", [
        "O Carregador leva o que as oficinas fizeram para o depósito e abastece cada oficina com insumos para 5 lotes, aproveitando a mesma viagem.",
        "Vilas com depósito e duas oficinas ou mais ganham um carregador entre os desempregados, e mais um a cada 5 oficinas. Também dá para escolher no menu Profissões.",
        "Sem carregador, cada artesão busca sozinho um lote de insumos no depósito a cada dois dias.",
    ]),
    ("Capítulo 11", "Depuração", [
        "Segurando o Editor de Aldeão, o último aldeão na mira mostra sobre a cabeça o visual e a IA, o caminho e os lugares que lembra: cama, mesa de trabalho, ponto de encontro e casa.",
        "A tecla K congela ou descongela a IA dele. No editor, a aba Inspecionar mostra genes, social, visual, IA e locais, e a aba Agenda edita o dia dele.",
    ]),
    ("Capítulo 12", "Aldeões mais espertos", [
        "Com villagerSkills ligado, cada aldeão melhora na profissão trabalhando, de 0 a 100. A habilidade aparece ao lado da profissão na tela de interação.",
        f"{B}Habilidade{R}\nQuem é bom trabalha até duas vezes mais rápido, às vezes faz um a mais e acha coisas raras com mais frequência. Ferreiros habilidosos às vezes encantam o que fazem.",
        f"{B}Inteligência{R}\nUm gene herdado define até onde (de 40 a 100) e quão rápido o aldeão aprende. Crianças ficam mais espertas na Escola: atril, 4 escadas de madeira e 4 estantes.",
        f"{B}Comida variada{R}\nComer sempre a mesma coisa enche menos e entedia; variar alegra. O aldeão escolhe sozinho a comida que comeu menos.",
        f"{B}Casa{R}\nEspaço por morador, luz de tochas ou lanternas, decoração (vasos, tapetes, velas, estantes) e um baú deixam a casa aconchegante. Casa apertada e escura entristece.",
        f"{B}Decisões{R}\nO desempregado procura o emprego em que é melhor. Sem ferramenta, o coletor pega uma no depósito. Com fome e nada em casa, busca no depósito. Oficinas fazem primeiro o que falta.",
        f"{B}Tempo livre{R}\n" + "Com villagerRoutines, o aldeão usa o que há por perto: senta em escadas e lajes, conversa com amigos, lê na biblioteca, ouve música, olha itens expostos, faz carinho no gato. Sozinho, fica perto dos outros.",
    ]),
    ("Capítulo 13", "Pedidos", [
        "Com villagerRequests ligado, aparece no menu radial a opção \"Precisa de ajuda?\". O aldeão pede uma entrega e promete uma recompensa tirada dos baús da casa dele.",
        f"{B}Recompensa{R}\n" + "O aldeão separa a recompensa na hora do pedido: ela sai do baú e fica guardada até você entregar. Quanto mais corações vocês têm, mais valioso ele aceita oferecer.",
        f"{B}Entregar{R}\n" + "Traga os itens e escolha \"Precisa de ajuda?\" de novo: você ganha a recompensa e corações pela dificuldade. Em 3 dias o pedido vence: a recompensa volta para casa e você perde 3 corações.",
        f"{B}Economias{R}\n" + "No primeiro pedido, os baús da casa recebem as economias do aldeão, conforme a profissão. Depois elas crescem com a parte dele no trabalho. Pedidos e valores ficam em data/mca/requests.",
        f"{B}Caça{R}\n" + "Às vezes o pedido é derrotar monstros: zumbis, aranhas, esqueletos, creepers, saqueadores. O aldeão conta pelas suas estatísticas; volte a falar com ele quando terminar.",
        f"{B}Mural{R}\n" + "O Mural de Pedidos (3 papéis sobre 3 tábuas) traz um pedido de alguém da vila que precisa de ajuda. Agachado, ele lista os seus pedidos abertos. Até 3 por vila.",
        f"{B}Negociar{R}\n" + "Com villagerBarter, qualquer aldeão vende o que carrega por esmeraldas e compra comida e o que a oficina usa. Amigos pagam menos. \"O que você carrega?\" mostra o inventário.",
    ]),
    ("Capítulo 14", "A vila cresce", [
        "Com villageProgression ligado, a vila segue um caminho: Acampamento, Aldeia, Vila, Vila Próspera, Cidade e Capital. A aba Evolução do Blueprint mostra a etapa e o que falta.",
        f"{B}O caminho{R}\n" + "Cada etapa pede prédios, casas melhores, oficinas e moradores. Nada fica travado: qualquer prédio funciona antes da hora. O caminho só sugere a ordem e avisa quando a vila sobe.",
        f"{B}Casas{R}\n" + "Casa Pequena: uma cama. Comum: 2 camas, um baú e chão de 30. Grande: 4 camas, 2 baús, fornalha e chão de 80. Enorme: 6 camas, 3 baús, 6 decorações e chão de 140.",
        f"{B}Oficinas{R}\n" + "Cada mesa de trabalho é um lugar para um profissional. Uma padaria com 3 defumadores e espaço (15 de chão por mesa) é nível III e trabalha com 3 açougueiros.",
    ]),
    ("Capítulo 15", "Outros mods e janelas", [
        f"{B}Integrações{R}\n" + "Com villagerRoutines, os aldeões usam coisas de outros mods. Cada integração é um arquivo em data/mca/opportunities: sem o mod, o arquivo é ignorado e nada quebra.",
        f"{B}Música{R}\n" + "Com Immersive Melodies, dê um instrumento de presente: o aldeão toca na folga, mais na taverna e na loja de música. Os vizinhos param para ouvir, e quem chega toca junto, no mesmo compasso.",
        f"{B}Cozinha{R}\n" + "Com Farmer's Delight, um açougueiro na taverna cozinha ensopados, sopas e banquetes. Um banquete posto na mesa reúne os aldeões: cada um pega uma porção. Armários contam como baús.",
        f"{B}Janelas{R}\n" + "Venezianas: um alçapão e um graveto dão duas. Ponha na parede ao lado da janela; fechada, ela cobre a janela. Os aldeões abrem de manhã, fecham à noite e acendem as luminárias do Beautify.",
        f"{B}Itens postos{R}\n" + "Com Tabletop, V põe o item da mão sobre um bloco. Os aldeões pegam o instrumento, tocam e devolvem, comem a comida e olham o resto. Oficinas expõem o que fazem nas mesas.",
    ]),
]

EN = [
    ("Introduction", "", [
        "This guide explains everything the expansion adds to MCA: villagers with needs, belongings and schedules, planned construction, a real economy and more.",
        "Each system can be turned on or off in mca.json: villagerNeeds, villagerActionQueue, villagerProperty, villagerWorkOrders and villagerEconomy. The visuals have their own options.",
    ]),
    ("Chapter 1", "Looks", [
        f"{B}Human-like villagers{R}\nWith squidward models, the squidwardHumanLikeModels option gives villagers the player's body, with MCA's skin, face, clothes and hair.",
        f"{B}3D layers{R}\nsquidward3dLayers turns hat, jacket, sleeves and pants into small cubes when a villager is close. player3dLayers does the same for players.",
        f"{B}Height{R}\nThe height gene sets each villager's size (squidwardHeightVariation) and children grow in stages. With Fresh Animations, turn on freshAnimationsIntegration.",
        f"{B}Work{R}\nDuring work hours a villager walks to their job site and works at it for a while, playing their profession's animation, then rests, strolls and chats.",
    ]),
    ("Chapter 2", "Radial menu", [
        "Right-click a villager: the buttons appear in a circle, with icons. Talk and Interact are the two main paths.",
        f"{B}Submenus{R}\nInteract has gift, trade, inventory, family tree, Commands (follow, stay, ride), Orders and Professions. Turning off radialInteractMenu brings back the classic menu.",
    ]),
    ("Chapter 3", "Needs", [
        "Six meters from 0 to 100 drop over time: Hunger, Thirst, Energy, Social, Fun and Safety. Low meters become thoughts that weigh on the mood.",
        f"{B}Hunger{R}\nVillagers eat from their inventory or a chest at home: give bread, meat or other food. Working makes them hungrier. Starving costs health down to half a heart, never death.",
        f"{B}Thirst{R}\nThey drink standing next to a cauldron with water: put cauldrons in the village.\n{B}Energy{R}\nRefills by sleeping in bed at night. Without a bed they nap anywhere.",
        f"{B}Social{R}\nRises when talking to you, to other villagers and at the bell's meeting point.\n{B}Fun{R}\nFree time and favorite buildings. Work is tiring.",
        f"{B}Safety{R}\nDrops when monsters come close. Recovers at home, near guards or golems. A critical meter makes a villager drop their routine to deal with it.",
        "The meters show on the interaction screen: hover one to see how to fill it. Couples who sleep together are happier.",
    ]),
    ("Chapter 4", "Action queue", [
        "Like in The Sims, every villager has a queue of what their schedule, needs and your orders ask for, and does the most urgent first. The queue shows above their head.",
        f"{B}Orders{R}\nThe Orders menu: eat, drink, sleep, go home, work, socialize or relax. Each entry shows its reason and urgency; click an entry to cancel it.",
    ]),
    ("Chapter 5", "Schedule", [
        "With the Villager Editor, the Schedule tab shows a villager's day: Idle, Work, Meet, Rest and Play. Pick a preset, or click and drag on the timeline to paint the hours.",
        "A custom schedule is saved with Done; Reset brings back the original. Warnings show what is missing: without a job site or meeting point, the villager stays idle.",
    ]),
    ("Chapter 6", "Property and crime", [
        "Beds, job sites, chests, doors and trapdoors belong to someone. Villagers own their bed, job site and home, with everything inside. You own what you place.",
        f"{B}Who may enter{R}\nSpouse, family, the mayor and friends with 50 hearts or more (except for beds). A home's door locks while all of its residents are inside.",
        f"{B}Padlock{R}\nRecipe: 3 nuggets in an arc over 3 ingots. Use it on your own chest or door to lock it. Sneaking with an empty hand takes it back.",
        f"{B}Forcing{R}\nSneak with an empty hand and hold use for 3 seconds.\n{B}Lockpick{R}\n2 nuggets diagonally and 1 stick. Takes 5 seconds, is silent and one is used up when it opens.",
        f"{B}Crowbar{R}\n4 ingots shaped as a hook. Opens at once and destroys the padlock, but it is loud: whoever hears it comes to look. Villagers replace the padlock after a day.",
        f"{B}Witnesses{R}\nVillagers see crimes up to 24 blocks away (half if you sneak, half again in the dark). A witness loses 10 hearts with you, the owner 25.",
    ]),
    ("Chapter 7", "Village construction", [
        "Craft a Blueprint (paper around blue dye) and use it in a village. The Plan Work button opens a free camera above the village.",
        f"{B}Controls{R}\nWASD, Space and Shift fly. Drag with the right button to look. Scroll changes the altitude. Keys 1 to 4: tools. Tab: list of jobs.",
        f"{B}Dig [1]{R}\nMiners and builders clear the area and store what they dig.\n{B}Build [2]{R}\nBuilders fill the area with the chosen block, taken from the storage.",
        f"{B}Who may plan{R}\nNobles or higher, or anyone holding a Villager Editor (then without materials). Pick Builder or Miner in a villager's Professions menu.",
        f"{B}Blueprints{R}\nCapture [4]: drag a box around something you built and name it. Blueprint [3]: pick a saved one, turn it with R and place it. Four come ready.",
        "Builders clear the way, fetch every material from the storage and, once done, the building counts as one of the village's. There is also the /mca workorder command.",
    ]),
    ("Chapter 8", "Gatherers", [
        "With villagerEconomy on, the village really produces. New professions gather resources: a jobless villager takes the job on finding a free station.",
        f"{B}Stations{R}\nChopping Block: flint over a log. Hunting Rack: stick, leather, stick over 3 planks. Forage Basket: wheat in a U shape.",
        f"{B}Lumberjack{R}\nFells trees near the block, picks up apples and sticks and replants saplings.\n{B}Hunter{R}\nHunts wild animals; spares named, leashed and penned ones and the last of a kind.",
        f"{B}Forager{R}\nPicks wild berries without removing the bush, the tops of sugar cane and bamboo, mushrooms and flowers where there are many, and wild pumpkins and melons.",
        f"{B}Fisherman{R}\nFishes from the shore near the barrel.\n{B}Miner{R}\nWithout jobs, takes sand, gravel and clay off the surface around a Sifting Sieve (plank, string, plank over two planks).",
        "Gatherers work up to 24 blocks from their station and stop gathering an item once the village storage holds 128 of it.",
    ]),
    ("Chapter 9", "Workshops", [
        "Villagers at their job site produce in batches, with inputs from the workshop chest. The bakery (a butcher's) bakes bread from wheat; the mason makes bricks from cobblestone.",
        "The toolsmith makes crowbars, axes and shovels; the armorer makes helmets and sometimes finds a diamond; the cleric makes redstone from rotten flesh. Recipes live in data/mca/production.",
        f"{B}The split{R}\nThe village's share follows the tax: at 30%, 30% goes to the workshop chest and the rest stays with the villager. Without a chest it all stays. Old item taxes are gone.",
        f"{B}Workshop chest{R}\nIt belongs to the workers and the haulers: opening it otherwise is theft. With full pockets, a villager takes their share to a chest in their own home.",
        f"{B}Farmer{R}\nHas no recipe: the village gets its share of the real harvest, in a workshop chest or one next to the composter.",
    ]),
    ("Chapter 10", "Hauler", [
        "The Hauler brings what workshops made to the storage and stocks each workshop with inputs for 5 batches, combining both on the same trip.",
        "Villages with a storage and two or more workshops get a hauler from their jobless villagers, and one more for every 5 workshops. It can also be picked in the Professions menu.",
        "Without a hauler, each craftsman fetches one batch of inputs from the storage every two days.",
    ]),
    ("Chapter 11", "Debugging", [
        "Holding the Villager Editor, the last villager under the crosshair shows their look and AI above their head, their path and the places they remember: bed, job site, meeting point, home.",
        "The K key freezes or unfreezes their AI. In the editor, the Inspect tab shows genes, social, look, AI and places, and the Schedule tab edits their day.",
    ]),
    ("Chapter 12", "Smarter villagers", [
        "With villagerSkills on, every villager gets better at their profession by working, from 0 to 100. The skill shows next to the profession on the interaction screen.",
        f"{B}Skill{R}\nA skilled hand works up to twice as fast, sometimes makes one more and finds rare things more often. Skilled smiths sometimes enchant what they make.",
        f"{B}Intelligence{R}\nAn inherited gene sets how far (40 to 100) and how fast a villager learns. Children get smarter at School: a lectern, 4 wooden stairs and 4 bookshelves.",
        f"{B}Varied food{R}\nThe same food again and again fills less and gets boring; variety cheers up. Villagers pick the food they ate least lately on their own.",
        f"{B}Home{R}\nRoom per resident, light from torches or lanterns, decoration (pots, carpets, candles, bookshelves) and a chest make a home cozy. A cramped, dark home gets them down.",
        f"{B}Decisions{R}\nThe jobless look for what they are best at. Gatherers without a tool take one from the storage. Hungry with nothing at home, they eat from it. Workshops make what is missing first.",
        f"{B}Free time{R}\n" + "With villagerRoutines, villagers use what is around: they sit on stairs and slabs, chat with friends, read in the library, listen to music, look at items on display, pet the cat. Alone, they stay near others.",
    ]),
    ("Chapter 13", "Requests", [
        "With villagerRequests on, the radial menu gets \"Need any help?\". The villager asks for a delivery and promises a reward taken from the chests in their home.",
        f"{B}Reward{R}\n" + "The villager sets the reward aside right when asking: it leaves the chest and is kept until you deliver. The more hearts you share, the more valuable an offer they make.",
        f"{B}Delivering{R}\n" + "Bring the items and pick \"Need any help?\" again: you get the reward and hearts by difficulty. After 3 days the request runs out: the reward goes back home and you lose 3 hearts.",
        f"{B}Savings{R}\n" + "On the first request, the home chests get the villager's savings, by profession. Later they grow with their share of the work. Requests and values live in data/mca/requests.",
        f"{B}Hunts{R}\n" + "Sometimes the request is to defeat monsters: zombies, spiders, skeletons, creepers, pillagers. The villager counts by your statistics; talk to them again when done.",
        f"{B}Board{R}\n" + "The Request Board (3 paper over 3 planks) brings a request from someone in the village who needs help. Sneaking lists your open requests. Up to 3 per village.",
        f"{B}Haggling{R}\n" + "With villagerBarter, any villager sells what it carries for emeralds and buys food and what its workshop uses. Friends pay less. \"What do you carry?\" shows its inventory.",
    ]),
    ("Chapter 14", "The village grows", [
        "With villageProgression on, the village follows a path: Camp, Hamlet, Village, Thriving Village, Town and City. The Blueprint's Progress tab shows the stage and what is missing.",
        f"{B}The path{R}\n" + "Each stage asks for buildings, better houses, workshops and residents. Nothing is locked: any building works early. The path only suggests the order and tells when the village grows.",
        f"{B}Houses{R}\n" + "Small House: one bed. Common: 2 beds, a chest and 30 floor. Big: 4 beds, 2 chests, a furnace and 80 floor. Huge: 6 beds, 3 chests, 6 decorations and 140 floor.",
        f"{B}Workshops{R}\n" + "Each work station is a place for one worker. A bakery with 3 smokers and room (15 floor per station) is level III and works with 3 butchers.",
    ]),
    ("Chapter 15", "Other mods and windows", [
        f"{B}Integrations{R}\n" + "With villagerRoutines, villagers use things from other mods. Each integration is a file in data/mca/opportunities: without the mod the file is skipped and nothing breaks.",
        f"{B}Music{R}\n" + "With Immersive Melodies, gift an instrument: the villager plays in free time, more so at the inn and the music store. Neighbors stop to listen, and others join in, in time.",
        f"{B}Kitchen{R}\n" + "With Farmer's Delight, a butcher at the inn cooks stews, soups and feasts. A feast set on the table gathers villagers: each takes a serving. Cabinets count as chests.",
        f"{B}Windows{R}\n" + "Shutters: a trapdoor and a stick make two. Put them on the wall beside the window; closed, they cover it. Villagers open them in the morning, close them at night and light Beautify lamps.",
        f"{B}Placed items{R}\n" + "With Tabletop, V puts the held item down on a block. Villagers pick up instruments, play and put them back, eat the food and look at the rest. Workshops show their wares on tables.",
    ]),
]

LIMIT = 260


def entries(chapters, author):
    out = {f"mca.books.{NAME}.author": author}
    index = 0
    for c, (title, subtitle, pages) in enumerate(chapters):
        out[f"mca.books.{NAME}.chapter.{c}"] = title
        out[f"mca.books.{NAME}.chapter.{c}.subtitle"] = subtitle
        for page in pages:
            if len(page) > LIMIT:
                raise SystemExit(f"page too long ({len(page)}): {page[:60]}")
            out[f"mca.books.{NAME}.{index}"] = page
            index += 1
    return out


def merge(path, new):
    """Appends the entries (replacing an earlier run's block) without reformatting the rest."""
    with open(path, encoding="utf-8", newline="") as f:
        text = f.read()
    nl = "\r\n" if "\r\n" in text else "\n"
    lines = text.rstrip().split(nl)
    prefix = '  "mca.books.' + NAME + '.'
    lines = [line for line in lines if not line.startswith(prefix)]
    body = nl.join(lines).rstrip()
    assert body.endswith("}")
    body = body[:-1].rstrip().rstrip(",")
    added = "".join("," + nl + "  " + json.dumps(k, ensure_ascii=False) + ": " + json.dumps(v, ensure_ascii=False) for k, v in new.items())
    result = body + added + nl + "}" + nl
    json.loads(result)
    with open(path, "w", encoding="utf-8", newline="") as f:
        f.write(result)


assert [len(c[2]) for c in PT] == [len(c[2]) for c in EN], "languages must have the same pages"
merge(f"{ROOT}/assets/mca_books/lang/pt_br.json", entries(PT, "Os cronistas da vila"))
merge(f"{ROOT}/assets/mca_books/lang/en_us.json", entries(EN, "The village chroniclers"))
print([len(c[2]) for c in PT])
