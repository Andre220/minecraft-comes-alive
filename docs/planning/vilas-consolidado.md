# Vilas: consolidado de planejamento

Documento de passagem para um chat único que vai juntar três temas ligados entre si:

1. **Evolução de prédios no MCA**: árvore de upgrades no estilo da tela de conquistas.
2. **Estudo do Tektopia**: feito em outro chat; as conclusões dele não estão aqui.
3. **Integração com o Bountiful**: também estudada em outro chat; aqui só entram os pontos de contato que encontrei.

Também registra o **Grounded Villages**, o mod de assentamento de vilas que já está implementado. Ele define como as vilas nascem, e é dessa vila inicial que a evolução parte.

Data: 2026-10-17. Autor: sessão `minecraft-comes-alive-dc`, com o Andre.

---

## 0. Preferências de trabalho do Andre (valem para o chat novo)

- Conversa em português; identificadores e código em inglês.
- **Nunca abrir o cliente do jogo.** O Andre abre sozinho; passe a ele o comando. Testes em servidor headless (`runServer`) estão liberados.
- Nunca compilar com o cliente aberto: isso derruba o jogo.
- Commit só quando ele pedir.
- Ele roda várias sessões em paralelo **no mesmo checkout do MCA**. Mexer em arquivos que outra sessão está editando atropela o trabalho dela; use worktree/branch próprio para trabalho novo.
- Ao terminar uma tarefa, a resposta final termina com `🔔 Tarefa concluída: <nome curto em português>` (um hook lê em voz alta).
- O modpack principal dele roda em **Fabric**: Tectonic, Lithostitched, Improved Village Placement (IVP), Better Villages, CTOV, MCA, Bountiful.

---

## 1. Grounded Villages (implementado, não commitado)

### 1.1 O que é

Mod separado do MCA que faz as vilas se assentarem no terreno de forma natural, sem exigir área plana:

- Cada casa é avaliada pelo terreno sob **toda a base**, não só pela porta. Até 1–2 blocos flutuando são aceitos.
- A casa pode **deslizar até 3 blocos ao longo da rua** para achar chão melhor.
- Ruas precisam ser **navegáveis**: no máximo 1 bloco de degrau entre vizinhos e sem atravessar água. Senão, o trecho é cortado e a rua termina numa ponta.
- **Slabs de terra** (11 tipos) e **degraus automáticos**: todo bloco de rua com vizinho de rua 1 bloco acima ganha um slab do mesmo material, e a rua fica contínua, sem pulos.

### 1.2 Onde está

- Repositório próprio: `C:\Users\andre\Downloads\other\grounded-villages` (git init, branch `main`, **nada commitado**).
- `mod_id` `grounded_villages`, pacote `io.github.andre220.groundedvillages`, MC 26.2, Java 25.
- Mesma divisão do MCA: lógica de jogo em `common/` (compila contra o vanilla via NeoForm), só o específico de loader em `fabric/`. NeoForge fica para depois.
- `fabric/dev-mods/` (fora do git) tem os jars do modpack para teste: cópia do `dev-mods` do MCA.
- Licença provisória `All-Rights-Reserved` (a definir).
- Precisa estar instalado no **cliente e no servidor**, por causa dos blocos novos.

### 1.3 Arquitetura

| Peça | Função |
|---|---|
| `placement/VillageContext` | Contexto por vila (thread local): reconhece a vila pelo template da peça central (`minecraft:village/`, `ctov:village/`), cache de alturas, estatísticas de debug |
| `placement/NoiseHeightSampler` | Lê alturas do worldgen **um chunk 16×16 por vez**, repetindo o laço do `doFill` vanilla |
| `placement/TerrainFit` | Custo de encaixe da casa e teste de navegabilidade da rua |
| `placement/ConnectorSolver` + `PlacementBackend` | Núcleo compartilhado: escolhe a peça de cada conector (custo, deslize, filtro de rua) |
| `placement/VillagePlacer` | Adaptador do jigsaw **vanilla** (`JigsawPlacement.Placer`) |
| `compat/LithostitchedPlacer` | Adaptador do jigsaw do **Lithostitched** (`AlternateJigsawGenerator`) |
| `placement/StreetSteps` | Slabs nos degraus das ruas, depois de `StructureStart.placeInChunk` |
| `block/*` | Slabs: terra, terra grossa, terra enraizada, grama (cor do bioma, neve), podzol, micélio, caminho de terra (7/16), lama, cascalho, areia, areia vermelha |
| `scripts/generate_slab_resources.py` | Gera blockstates, modelos, itens, loot, receitas, tags e traduções dos slabs |

### 1.4 Aprendizados técnicos (difíceis de redescobrir)

- **O vanilla escolhe a primeira peça que não colide, não a melhor.** Para escolher a melhor foi preciso assumir o laço de candidatos do `Placer`.
- **A caixa de colisão da rua já inclui os lotes das casas.** O conector da casa conta como "dentro" da rua (`terrain -> rigid inside`, horizontal). As conexões "inside up" são decorações em cima da rua.
- **O Lithostitched substitui a montagem de todas as estruturas jigsaw**: faz um `@Redirect` em `JigsawStructure.findGenerationPoint` e chama o próprio `AlternateJigsawGenerator`. Como IVP e Tectonic dependem dele, no modpack o código vanilla nunca roda. Os mixins do Lithostitched são opcionais, ativados por um plugin que checa se a classe existe.
- **`getFirstFreeHeight` cria um `NoiseChunk` com aquífero para cada coluna.** O vanilla chama uma vez por conector e não sente o custo. Para amostrar a base inteira das casas com o Tectonic, passava de 60 s e derrubava o servidor. Lendo por chunk, cai para 1–4 s por vila, **mais rápido que o próprio Lithostitched**.
- As ruas `terrain_matching` são assentadas por `GravityProcessor(WORLD_SURFACE_WG, -1)` durante a geração e por `WORLD_SURFACE` no `/place`.
- O "**small village**" é do **IVP** (`generate_small_villages: true` por padrão): profundidade 4 e raio de 40 blocos. Com os mercados grandes do Better Villages, sobra quase só o mercado.
- O **Better Villages** não tem mixins; ele substitui os templates NBT vanilla usando os mesmos nomes.
- Materiais das ruas (camada de baixo dos templates):
  - vanilla: caminho de terra e arenito liso;
  - Better Villages: caminho, grama, terra, areia, terra grossa e cascalho;
  - CTOV: pedregulho, tijolo de pedra e tábuas.

### 1.5 Como testar (headless)

- `./gradlew :fabric:runServer` com comandos no stdin.
- `-Dgroundedvillages.debug=true` (já está nas runs) liga as estatísticas por vila.
- `JAVA_TOOL_OPTIONS=-Dgroundedvillages.vanilla=true` dá a referência vanilla para comparar.
- O servidor **reescreve o `server.properties`**: edite as chaves que já existem (`max-tick-time=-1`, `pause-when-empty-seconds=-1`) em vez de acrescentar linhas.
- O `/place structure` ignora o bioma (testar nas coordenadas do `/locate`) e precisa de `forceload add` antes.
- Mundos de comparação prontos em `fabric/run/saves/`: `GV grounded`, `GV vanilla`, `GV modpack grounded`, `GV modpack vanilla`, `GV slabs`.

### 1.6 Resultados (modpack, 6 vilas reais)

| | Geração padrão | Grounded Villages |
|---|---|---|
| Peças por vila | 70–131 | 78–165 |
| Casas (deslizadas) | não medido | 22–43 (9–26 deslizadas) |
| Ruas cortadas | não se aplica | 0–5, quase só por água |
| Tempo por vila | 2,1–7,6 s | 1,1–4,1 s |
| Slabs de rua | não se aplica | 820 nas 6 vilas |

### 1.7 Pendências

- **Conferir no jogo:** modelos e texturas dos slabs (borda e cor da grama, podzol, micélio, caminho), ruas contínuas, cantos em "L".
- Rigidez do degrau das ruas: (a) manter 1 bloco ou (b) tolerar alguns degraus de 2. Com o IVP quase não importa.
- O filtro de ruas testa a **linha central da caixa**. Ruas curvas ou largas do Better Villages podem precisar seguir os conectores da peça.
- Fase 3: aterrar buracos de caverna (carvers) sob as ruas.
- Fase 4: aplanar terreno pelo `Beardifier` (fallback para vilas pequenas demais e para degraus de 2).
- Fase 5: arquivo de config. NeoForge.

---

## 2. Evolução de prédios no MCA (em discussão)

### 2.1 Como o MCA reconhece prédios hoje

`data/mca/building_types/*.json`: um prédio vira de um tipo quando tem os blocos exigidos, e o tipo de maior prioridade vence (`Building.java`, `RoomTypeResolver.java`). São 25 tipos.

**Prédios com efeito real no jogo** (conferido no código):

| Tipo | Requisito | Efeito | Onde no código |
|---|---|---|---|
| `town_center` | `#mca:town_center` | define a vila | `VillageModule` |
| `house` | 1 cama | moradia, abrigo | `SeekIndoorShelterTask`, `Building` |
| `big_house` | 4 camas | moradia maior (substitui `house`) | `Building` |
| `inn` | 4 camas, toca-discos, defumador | gera aventureiros e mercadores; favorito de guarda e arqueiro; destino de quem está triste ou deprimido | `VillageInnManager`, `ProfessionsMCA`, `MoodGroup`, `VillagerTasksMCA` |
| `music_store` | toca-discos, 2 blocos musicais, baú | destino de quem está infeliz | `MoodGroup` |
| `library` | 32 estantes, atril | impostos ×1,5; favorito de bibliotecário, cartógrafo, clérigo | `VillageTaxesManager`, `ProfessionsMCA` |
| `storage` | atril, 4 baús | habilita os impostos e guarda o que é coletado | `Village`, `VillageTaxesManager`, `workorder/VillageStorage` |
| `armory` | mesa de ferraria, rebolo, bigorna | +1 nível no equipamento dos guardas | `VillageGuardsManager` |
| `blacksmith` | rebolo, 2 altos-fornos, bigorna | +1 nível no equipamento dos guardas | `VillageGuardsManager` |
| `infirmary` | suporte de poções, caldeirão, cama, vaso | metade da chance de infecção por zumbi | `VillagerEntityMCA` |
| `graveyard` | 3 lápides | onde os villagers choram seus mortos | `Mourning` |
| `prison` | 8 grades, porta de ferro, cama | **reconhecida, sem efeito** | nenhum uso |

**Tipos invisíveis de profissão** (só a estação de trabalho; sem efeito): armeiro, padaria, açougue, contador, cartógrafo, cabana de pescador, flecheiro, curtidor, pedreiro, ferramenteiro, tecelagem. Também existem `building` (genérico) e `blocked`.

### 2.2 Necessidades dos NPCs (`entity/ai/Needs.java`, estilo RimWorld)

São seis: `HUNGER`, `THIRST`, `ENERGY`, `SOCIAL`, `FUN`, `SAFETY`. Medidores de 0 a 100 que caem com o tempo e viram pensamentos que afetam o humor. Quando ficam críticos, sobrescrevem a rotina (dormir quando exausto, se esconder quando com medo). O `onInFavoredBuilding()` recompensa o tempo livre passado no prédio favorito.

| Necessidade | Quem atende hoje | Lacuna |
|---|---|---|
| Fome | baú com comida em casa (`NeedsFetchFoodTask`) | **nenhum prédio produz comida** (falta fazenda; padaria e açougue não fazem nada) |
| Sede | caldeirão com água (`NeedsDrinkTask`) | **não existe poço** |
| Energia | cama, casa, casa grande | ok |
| Social | conversa (`NeedsSocializeTask`), estalagem | falta praça ou mercado |
| Diversão | loja de música, estalagem, biblioteca | pouco variado |
| Segurança | guardas, arsenal, ferreiro | **sem muralha ou torre**; prisão sem função |

Outros sistemas da vila: impostos (armazém, biblioteca), saúde (enfermaria), luto (cemitério), comércio (estalagem). Também faltam **escola** (há crianças, mas nada para elas), **templo** (o clérigo usa a biblioteca) e **mercado**.

### 2.3 Proposta: árvore em três camadas

**1. Níveis da vila (o tronco).** Cada nível exige alguns prédios e libera os próximos (`*` = tipo novo):

```
ACAMPAMENTO ──► ALDEIA ──────────► VILA ───────────► CIDADE
centro         estalagem           biblioteca        prisão (com função)
casas          armazém (impostos)  enfermaria        muralha/torre*
poço*          ferreiro            arsenal           escola*
fazenda*       cemitério           loja de música    templo*
               oficinas            casa grande       mercado*
```

**2. Evolução de cada prédio (os galhos).** O próprio prédio sobe de tipo quando ganha os blocos certos:
- casa → casa grande → estalagem;
- oficina → ferreiro → arsenal;
- atril → armazém → biblioteca.

A detecção por blocos que já existe confirma quando a evolução foi concluída.

**3. Interface no estilo das conquistas.** Um nó por tipo de prédio, com linhas de pré-requisito. Cada nó mostra:
- estado: bloqueado / disponível / construído ×N;
- requisitos: blocos que faltam, nível da vila;
- benefício: qual necessidade ou sistema atende.

Encaixaria como uma aba nova do `BlueprintScreen`, a tela de gestão da vila do MCA.

### 2.4 Decisões em aberto

1. **Quem executa o upgrade?**
   - (a) o jogador constrói e o sistema detecta;
   - (b) os villagers constroem, via work orders;
   - (c) instantâneo: paga esmeraldas e o prédio é trocado por um template maior.

   Recomendação: começar por (a), que já existe, e plugar (b) depois. O (c) é o mais caro e pode destruir construções dos jogadores.
2. **Escopo:** só prédios, só níveis da vila, ou os dois (recomendado)?
3. **Tipos novos:** quais entram (poço, fazenda, mercado, escola, templo, muralha/torre)? A prisão ganha função?
4. **Tipos de profissão invisíveis:** viram nós visíveis (oficinas) ou continuam escondidos?
5. **Vila gerada:** as vilas vanilla já nascem com casas e estações de trabalho. Começam no nível "Aldeia" ou precisam conquistar os níveis?

---

## 3. Conexões entre os temas

- **Grounded Villages → evolução:** define a vila inicial (quais e quantas casas, como ficam no terreno). Os templates do Better Villages e do CTOV mudam essa vila inicial, então a árvore não deve depender de templates específicos. Detecção por blocos funciona com qualquer um.
- **Work orders (planejamento estilo RTS) → evolução:** "fazer o upgrade" pode virar uma ordem de construção para os villagers construtores. Esse trabalho está no worktree `C:\Users\andre\Downloads\other\mca-workorders`, branch `feature/villager-work-orders`, e foi feito em outra sessão. Lá já existem `VillageStorage` e o overlay de work orders.
- **Bountiful → evolução e vila:**
  - O Better Villages já traz um gancho de compatibilidade com o Bountiful (`data/bettervillage/bettervillage_compat/bountiful.json`): pools por bioma para inserir peças do Bountiful nas vilas, **desligado** (`enabled: false`) e com pools vazios.
  - Ideia a avaliar com o estudo do outro chat: o quadro de recompensas como prédio da árvore (por exemplo, "mural" no nível Aldeia) e as recompensas pedindo o que a vila precisa para o próximo upgrade.
- **Tektopia → evolução:** o estudo está no outro chat. Ao juntar os temas, compare o modelo de crescimento dele com os três níveis propostos aqui.

---

## 4. Relatos pendentes do MCA (casas, cadeados, sono)

Feitos em teste pelo Andre e ainda não tratados. Não são deste trabalho, mas estão nas mesmas áreas (casa, cama, vila):

1. **Porta ou baú trancado** toca a animação e o som de abrir antes do aviso de "trancado" (visto na porta). Desejado: não animar. Pista: o bloqueio é no servidor (`PropertyRules`, mensagens `property.locked_home` / `property.denied`, ~linha 420), e o cliente prevê a abertura antes da resposta.
2. **Casa com duas camas e um solteiro não tranca.** Desejado: trancar. Pista: `PropertyRules.isLocked` (~linha 229): "locked while every resident is loaded, alive and inside it".
3. **Bônus para casal em camas encostadas** (lado a lado, mesma orientação, como cama de casal). O tipo de bônus está em aberto; encaixa com a evolução (casa → casa de casal?).
4. **Villagers que não dormem:**
   - Ryo: cama no segundo andar, acesso por escada de blocos; gizmo mostra "Walk target none / no path" no `rest (timeline)`; ele vai até perto da escada mas não sobe.
   - Moe: para ao lado da cama e nunca deita; gizmo mostra `rest`, `MoveToTargetTask`/teleporte, alvo `-245, 79, -1833` com `path 3`, "Shelter bed: White Bed" ao lado.

   As capturas de tela estão só na conversa original.

---

## 5. Próximos passos sugeridos para o chat unificado

1. Trazer as conclusões do estudo do Tektopia e do Bountiful para a seção 3.
2. Fechar as decisões da seção 2.4.
3. Definir os tipos novos (seção 2.2) e o efeito de cada um nas necessidades.
4. Desenhar os nós e as conexões da árvore (prédios, níveis, pré-requisitos).
5. Só então implementar, num worktree próprio, começando pela tela no estilo das conquistas sobre a detecção que já existe.
