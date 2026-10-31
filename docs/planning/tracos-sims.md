# Traços estilo The Sims 4 para villagers do MCA

## Contexto

Hoje os traços (`entity/ai/Traits.java`) são quase só genéticos e visuais. Os poucos efeitos que eles têm não aparecem em lugar nenhum: o usuário só descobriu pela wiki que `athletic` dá +10% de velocidade e que `tough` dá ×1.5 de dano e ×0.75 de dano recebido. A UI mostra apenas "Traits: A, B, C". No hover aparecem descrições soltas, sem o nome do traço e sem números. O editor é uma lista de botões verdes e cinzas, sem tooltip.

A personalidade (`entity/ai/relationship/Personality.java`) é única por villager e quase só escolhe a "voz" dos diálogos.

Objetivo:
1. Traços inspirados no Sims 4 (Emotional, Hobby, Lifestyle, Social) que mudam necessidades, velocidade, humor, habilidades, rotina e relações.
2. **Slots por idade, configuráveis: criança 1, adolescente 3, adulto 5.** Os traços genéticos não ocupam slot.
3. **A personalidade é fundida nos traços.** Os saves antigos são migrados e a voz dos diálogos continua funcionando.
4. **Uma UI que mostra os buffs e debuffs de cada traço.**
5. **Novo estágio de idade: Idoso** (seção 1.4), com traços exclusivos dessa idade, como Sábio.

Plano aprovado em 2026-10-28. A implementação acontece fase a fase (seção 5).

---

## 1. Modelo de dados

### 1.1 `Trait` ganha metadados (`entity/ai/Traits.java`)

Hoje um `Trait` tem `id, chance, inherit, usableOnPlayer`. Ele passa a ser montado por um builder:

```java
Trait CHEERFUL = Traits.builder("cheerful")
    .category(Category.EMOTIONAL)        // GENETIC | EMOTIONAL | HOBBY | LIFESTYLE | SOCIAL
    .minAge(AgeState.CHILD)
    .conflicts("gloomy")
    .icon(Items.SUNFLOWER)               // ícone = item, sem precisar de arte nova
    .voice("upbeat")                     // prefixo de diálogo herdado da personalidade (opcional)
    .effect(TraitEffect.baseMood(+2))
    .register();
```

- **`Category.GENETIC`**: os traços atuais (albinismo, nanismo, canhoto, tough, weak, orientações, intolerâncias, sirben, electrified, no_aging). Continuam com `chance`, `inherit` e `Config.traitChance` e **não contam slot**.
- As outras quatro categorias são os **traços de personalidade**, limitados pelos slots.
- O armazenamento não muda: o `CompoundTag` sincronizado em `TRAITS` já guarda N ids. Nada muda na rede.
- Os addons continuam registrando pelo `ExtensibleTypeRegistry`.

### 1.2 `TraitEffect`: efeitos tipados e autodescritos

Novo `entity/ai/traits/TraitEffect.java`: um record ou uma sealed interface, em que cada tipo sabe **aplicar o efeito** e **se descrever** com uma linha traduzida, verde (buff) ou vermelha (debuff). É isso que gera os buffs e debuffs da UI sem texto escrito à mão.

| Tipo | Ponto de encaixe atual | Exemplo |
|---|---|---|
| `needRate(Need, mult)` | `Needs.tick()` (`entity/ai/Needs.java:295-339`), multiplica cada `add(...)` de queda | Guloso: fome ×1.4 |
| `needGain(Need, mult)` | `Needs.onAte`, sono, social, diversão | Preguiçoso: dormir rende ×1.2 |
| `speed(pct)` | modificadores em `VillagerLike.java:403-415`, ao lado do `athletic` | Ativo +10%, Preguiçoso −10% |
| `baseMood(n)` | novo pensamento fixo em `Needs.thoughts()` (`Needs.java:222`) | Alegre +2, Melancólico −2 |
| `conditionalThought(cond, key, mood)` | `Needs.thoughts()`; condições prontas: sozinho, ao ar livre, chuva, em casa, trabalhando | Solitário +2 sozinho / −2 em grupo |
| `skillGain(mult)` | `Skills.gain` (`expansion/minds/Skills.java:96`) | Gênio ×1.25, Perfeccionista ×0.8 com chance extra de qualidade |
| `opportunityAppeal(kind, mult)` | `expansion/minds/routine/Opportunities` (appeal do `Opportunity`) | Leitor ×3 em lectern/estante, Amante de música ×3 em jukebox/note block |
| `heartGain(mult)` | `Memories.modHearts` (jogador) | Bondoso ×1.2, Malvado ×0.8 |
| `socialSeek(mult)` | `NeedsSocializeTask` (limiar `LONELY` e cooldown) | Extrovertido procura mais |
| `damage(mult)` / `damageTaken(mult)` | `VillagerLike.java:421-426`, `VillagerEntityMCA.java:790` | Migra `tough` e `weak` para este formato |

Os traços genéticos atuais passam a declarar os efeitos que já têm. Assim `tough` mostra na UI "+50% dano, −25% dano recebido" e `athletic` mostra "+10% velocidade".

### 1.3 Slots por idade

- Nova config em `CommonConfig`: `traitSlotsChild = 1`, `traitSlotsTeen = 3`, `traitSlotsAdult = 5`, `traitSlotsElder = 5`. Bebê e toddler têm 0.
- Ao virar idoso, há 50% de chance de **trocar** um traço comum por um traço exclusivo de idoso (`wise`), que tem `minAge(ELDER)`.
- `Traits.fillSlots(AgeState)`: sorteia traços de personalidade até completar o limite. Respeita `minAge`, conflitos e `isEnabled()`, e dá peso extra aos traços dos pais, usando o campo `inherit`.
- **Gancho:** `VillagerEntityMCA.onAgeStateChanged` (linhas 1723-1743), ao lado de `getVillagerBrain().randomize(state)`.
- Cuidado: `ageStateEventsEnabled` (linha 460) precisa estar ativo. Nos spawns como adulto, `randomize()` (`PlayerSaveData.java:121`, `VillagerLike.java:127`) chama `fillSlots` diretamente.
- **O primeiro traço da criança define a voz dos diálogos** (`voice`), como uma "personalidade de berço". Os traços seguintes só somam efeitos.

### 1.4 Novo estágio: IDOSO (`entity/ai/relationship/AgeState.java`)

**O problema:** a idade do MCA vai de `-villagerMaxAgeTime` até 0, e `getId` termina em ADULT. No vanilla, a idade **positiva** é o cooldown de reprodução (`AgeableMob`). Por isso o idoso **não pode** vir de `age > 0`.

**A solução:**
- **Enum:** `ELDER(1, 0.96F, 1, 1, 0.85f, 0.9f)` é adicionado **no fim** do `AgeState`. Assim os ids/ordinais salvos (ex.: `AGE_STATE` do zumbi) não mudam. Ele fica um pouco mais baixo e lento, com a voz mais grave.
- **Contador de tempo como adulto:** novo parâmetro sincronizado `AdultTicks`, salvo em NBT. Sobe a cada 20 ticks no servidor enquanto o villager é adulto, não está em `no_aging` e não tem a idade travada.
  - `VillagerEntityMCA.getAgeState()` (linhas 517-520): se o estado calculado é ADULT e `AdultTicks >= elderAfter`, devolve ELDER.
  - A mudança dispara o `onAgeStateChanged` que já existe (1723-1743): dimensões, cérebro, roupas e `fillSlots`.
- **Config:** `villagerElderAfterTime` (ticks de vida adulta; o padrão é ~5× `getStageDuration()`) e `0` desliga os idosos.
- **`setAgeState(ELDER)`** (1714-1721): define `AdultTicks = elderAfter` em vez de chamar `setAge`. `ADULT.getNext()` continua sendo ADULT, para o dente-de-leão dourado não pular para idoso. `ELDER.toAge()` devolve 0.
- **Spawn e saves antigos:** `AgeState.random()` passa a ter ~15% de chance de idoso. Os adultos que já existem recebem um `AdultTicks` aleatório na migração (`TraitsV2`), para a vila ter alguns idosos logo de cara.
- **`isAdult()`:** novo helper (`ADULT || ELDER`) que **substitui** as ~20 checagens `== AgeState.ADULT` / `!= ADULT`:
  - `Constraint.java:45-46`: "adult" passa a valer para idosos, e entram os novos `"elder"` e `"!elder"`
  - `SchedulesMCA.java:167`, `VillagerTasksMCA.java:189`
  - `VillageEconomyManager.java:32`, `Requests.java:222`
  - `Beards.java:77`, `AptitudeJobTask.java:38`, `SchoolTask.java:49`
  - `MatchmakersRingItem.java:28`, `VillagerEditorScreen.java:2083`
  - `PersonalityModule.java:33`
  - Os modelos que usam `AgeState.ADULT` só como dimensão padrão ficam como estão.
- **Jogabilidade do idoso:**
  - energia cai ×1.2 e velocidade 0.85
  - não procria (`Pregnancy.java:87`)
  - rotina com meio turno de trabalho (`SchedulesMCA`) e mais tempo livre e de socializar
  - habilidade cai ×0.8, mas o traço `wise` compensa
- **Visual:**
  - cabelo puxado para o grisalho conforme o tempo como idoso (em `VillagerVisuals.java:81-113`, misturando a cor do gene de cabelo)
  - mais chance de barba (`Beards`)
  - leve curvatura do tronco e da cabeça no `VillagerEntityBaseModelMCA`
  - bengala opcional na mão secundária, mais tarde
- **Morte por velhice (opcional, config `villagerOldAgeDeath = false`):** depois de X dias como idoso, uma chance diária de morrer dormindo. Isso aciona o túmulo e o luto que já existem (`TombstoneBlock`, mourning sites).
- **Textos e conteúdo:**
  - `enum.agestate.elder` em en/pt
  - aviso aos filhos jogadores ("seu pai/mãe envelheceu") ao lado de `notify.child.grownup`
  - advancement `grow_elder` opcional, seguindo `resources/data/mca/advancement/grow_adult.json`
  - diálogos: os idosos usam os de adulto, e linhas próprias de idoso podem vir depois pela constraint `elder`
- **Editor:** ganha a opção "Idoso" no seletor de idade. O `VillagerDebugInfo.java:267` já itera `AgeState.values()`.

**Traços exclusivos de idoso:**
| id | PT | Efeitos |
|---|---|---|
| wise | Sábio | ensinar na escola (`SchoolTask`) rende ×2 aos alunos; pensamentos de raiva e medo ×0.5; habilidade sem a penalidade de idoso |
| storyteller | Contador de histórias | diálogos `story.json` rendem mais; crianças por perto ganham diversão |

---

## 2. Fusão da personalidade

| Personalidade antiga | Traço novo | Voz de diálogo mantida |
|---|---|---|
| upbeat | cheerful (Alegre) | upbeat |
| gloomy | gloomy (Melancólico) | gloomy |
| flirty | romantic (Romântico) | flirty |
| playful | goofball (Brincalhão) | playful |
| odd | erratic (Excêntrico) | odd |
| crabby | mean (Ranzinza/Malvado) | crabby |
| greedy | materialistic (Materialista) | greedy |
| extroverted | outgoing (Extrovertido) | extroverted |
| introverted | loner (Solitário) | introverted |
| friendly | friendly (Amigável) | friendly |
| sensitive | sensitive (Sensível) | sensitive |
| anxious | anxious (Ansioso) | anxious |
| relaxed | relaxed (Tranquilo) | relaxed |
| peaceful | peaceful (Pacífico) | peaceful |

O que muda em cada lugar:

- **`VillagerBrain`** (`PERSONALITY`, linhas 38, 166, 209-218): o parâmetro continua, renomeado na prática para **voz**. `getPersonality()` passa a ser derivado do primeiro traço que tem `voice`. Quem lê a personalidade não muda: `Messenger.java:86-90` (flag `#E`), `DialogueType.java:104-108`, `VillagerBrain.java:412` (sensitive dobra a penalidade) e `PersonalityModule` (chat AI).
- **Migração de save:** no load, se `Personality != UNASSIGNED` e a flag `TraitsV2` não existe, o villager ganha o traço mapeado, `fillSlots(idadeAtual)` completa o resto e a flag é gravada. Fica em `VillagerEntityMCA.java:1923`, onde já se trata `UNASSIGNED`, ou em um fix novo em `datafix/fixes/`, seguindo o `MourningSiteMemoryFix`.
- **Dados:** o predicado `"personality"` (`GiftPredicate.java:68-75`) continua aceito e vira um alias de `"trait"` pelo mapa acima. Assim os 20 JSONs de diálogo e presente continuam funcionando sem edição. Novos conteúdos usam `"trait"`.
- **Editor:** a aba "personality" de `VillagerEditorScreen` (linhas 683-699, `getPages` 1544) é removida. A escolha de voz vira o "traço de voz", marcado na aba de traços.
- `Personality.java` continua existindo só como registro de vozes, para as traduções e os addons.

---

## 3. Lista de traços

**Critério:** não é para portar o Sims 4 inteiro. Um traço só entra se tiver **gancho real no Minecraft ou no MCA**: necessidades, dieta, `HomeQuality`, habilidades, opportunities, festas (`Feasts`), túmulos e luto, guarda-roupa, boatos, prática de arco, animais vanilla, corações e casamento.

Ficam de fora os traços que dependem de sistemas inexistentes:
- **Sistemas do Sims sem equivalente:** Ambitious (carreira), Geek, Dance Machine, Insider (clubes), Bro (esportes na TV) e Self-Absorbed (fama).
- **Packs e cenários do Sims:** Child of the Islands/Ocean/Village, Recycle Disciple, Freegan, Green Fiend e Chased by Death.
- **Mecânicas de ocultos e de balance:** Mystical, Disruptive e Skeptical.
- **Sem gancho claro hoje:** High Maintenance, Heart on Your Sleeve, Lovebug, Shady e Idealist.

A idade mínima de todos é criança, exceto onde a tabela indica outra.

### Emocionais
| id | PT | Efeitos | Conflita |
|---|---|---|---|
| cheerful | Alegre | humor +2; pensamentos ruins −1 | gloomy |
| gloomy | Melancólico | humor −2; chuva +1; habilidade ×1.1 quando triste | cheerful |
| hot_headed | Esquentado | humor despenca ao ser atacado; brigas na fase 6 | relaxed, peaceful |
| self_assured | Confiante | segurança cai ×0.7; corações ×1.1 | anxious |
| creative | Criativo | habilidade ×1.15 em profissões de ofício; atração por bancadas | — |
| genius | Gênio | habilidade ×1.25; atração por lectern e livros | — |
| goofball | Brincalhão | diversão ×1.3 nas atividades livres; piadas rendem mais (dialogue `trait`) | — |
| romantic (teen+) | Romântico | flerte ×1.5; humor −2 sem contato romântico por alguns dias | unflirty |
| unflirty (teen+) | Reservado | flerte ×0.5; incômodo perto de quem é romantic | romantic |
| erratic | Excêntrico | humor oscila ±2 aleatoriamente | — |
| sensitive / anxious / relaxed / friendly / peaceful | (vindos da personalidade) | efeitos atuais e mais um simples cada (anxious: segurança ×1.5; relaxed: ×0.7) | anxious↔relaxed |

### Hobbies
| id | PT | Efeitos |
|---|---|---|
| bookworm | Leitor | estante e lectern ×3 de atração; diversão ao ler |
| music_lover | Amante da música | jukebox e note block ×3; diversão ×1.5 perto de música |
| art_lover | Amante da arte | pinturas e espelhos ×3; casa com pinturas dá pensamento + |
| foodie | Gourmet | bônus de variedade do `Diet` ×2; comida repetida −2 |
| loves_outdoors | Ama a natureza | humor + sob o céu aberto; atração por bancos e lagos |
| maker | Artesão | habilidade ×1.2 em ofícios de produção |
| rancher | Fazendeiro | humor + perto de animais da vila |
| perfectionist (teen+) | Perfeccionista | trabalho ×0.85 mais lento; chance extra de qualidade (`Skills.rollExtra`) |

### Estilo de vida
| id | PT | Efeitos | Conflita |
|---|---|---|---|
| active | Ativo | velocidade +10%; energia ×0.85 | lazy |
| lazy | Preguiçoso | velocidade −10%; energia ×1.2; diversão no trabalho ×1.5 de queda; dormir ×1.2 | active |
| glutton | Guloso | fome ×1.4; come qualquer coisa; fome baixa pesa ×2 no humor | vegetarian |
| neat | Organizado | `HomeQuality` pesa ×2 | slob |
| slob | Bagunceiro | ignora a `HomeQuality` negativa | neat |
| kleptomaniac (teen+) | Cleptomaníaco | furta itens de baús (fase 7); tenso se fica sem furtar | — |
| materialistic | Materialista | presentes valiosos ×1.5; humor + com emeralds no inventário | — |
| adventurous | Aventureiro | diversão + ao explorar; segurança ×0.8 | — |
| squeamish | Sensível a sangue | segurança cai ×1.5 ao ver mortes e monstros | — |
| clumsy | Desastrado | chance de tropeçar e derrubar item | — |
| vegetarian (genetic→lifestyle) | Vegetariano | **implementa o TODO**: carne dá humor −3, o `Diet.pickFood` evita carne | glutton |

### Sociais
| id | PT | Efeitos | Conflita |
|---|---|---|---|
| outgoing | Extrovertido | social ×1.5 de queda; socializar rende ×1.5; procura gente | loner |
| loner | Solitário | social ×0.5; humor + sozinho, − na reunião | outgoing |
| good | Bondoso | corações ×1.2; humor + ao dar presente ou ajudar | mean |
| mean | Malvado/Ranzinza | corações ×0.8; insultos rendem (dialogue) | good, friendly |
| family_oriented (teen+) | Família | humor + perto do cônjuge e dos filhos; corações com a família ×1.5 | hates_children |
| hates_children (teen+) | Não gosta de crianças | humor − perto de crianças; menor chance de querer filhos | family_oriented |
| jealous (teen+) | Ciumento | humor − quando o cônjuge interage com outros (fase 6) | — |
| loyal | Leal | relações decaem ×0.5 (fase 6) | — |
| noncommittal (adulto) | Descompromissado | demora a aceitar casar; humor − depois de muitos dias casado | family_oriented |
| generous | Generoso | humor + ao entregar comida ou itens a vizinhos necessitados (economia/hauler); corações ×1.1 | materialistic |
| nosy (teen+) | Fofoqueiro | diálogos de boatos (`rumors.json`) rendem mais; procura conversas | — |
| proper (teen+) | Formal | humor + com roupa formal do guarda-roupa; incômodo com interações rudes | slob, goofball |
| snob (teen+) | Esnobe | `HomeQuality` alta dá ×2 de humor, baixa dá −; corações ×0.9 com pobres | — |
| socially_awkward | Tímido | socializar rende ×0.7, mas amizade fechada dá pensamento + forte | outgoing |
| party_animal (teen+) | Festeiro | atração ×3 pelas festas (`Feasts`); diversão ×1.5 em grupo | loner |
| competitive | Competitivo | atração pela prática de arco (`ArcheryPracticeTask`); humor ± conforme o desempenho | — |

### Hobbies extras (Minecraft puro)
| id | PT | Efeitos |
|---|---|---|
| animal_lover | Amante dos animais | humor + perto de gatos, cachorros e cavalos; atração por fazer carinho; une Animal Enthusiast, Cat/Dog/Horse Lover e Rancher |
| plant_lover | Amante das plantas | humor + perto de flores e plantações; agricultor ganha habilidade ×1.15 |
| macabre (teen+) | Macabro | não sofre o luto pesado; humor + perto de túmulos (mourning sites do MCA) |

### Traços de toddler (opcional, fase 5b)
O MCA tem os estágios BABY e TODDLER. Um slot extra de **toddler**, que **é substituído** ao virar criança como no Sims, renderia:
- wild (Agitado, velocidade +)
- clingy (Grudento, humor + perto dos pais)
- inquisitive (Curioso)
- silly (Bobinho)
- independent (Independente)
- angelic (Angelical)

A config `traitSlotsToddler` fica com padrão 0, para não fugir do 1/3/5 pedido. Os traços de infant ficam de fora, porque o bebê do MCA é um item e não tem comportamento.

---

## 4. UI

### 4.1 Tela de interação (`client/gui/InteractScreen.java`)
- **Substituir** as linhas de personalidade (228-233) e de traços (236-254) por uma **faixa de ícones de traço**, com o mesmo estilo do painel `drawNeeds` (494-535): fundo `0x88000000`, células de 22px, ícone de item 16×16 e borda colorida pela categoria.
  - Slots vazios aparecem como cadeado: "Libera aos 5 traços quando adulto".
  - Os traços genéticos ficam numa segunda faixa menor, ou depois de um separador.
- **Tooltip por traço**:
  - nome em negrito
  - categoria em cinza
  - descrição
  - efeitos gerados pelo `TraitEffect`: "▲ +10% velocidade" em verde, "▼ fome cai 40% mais rápido" em vermelho
  - para o traço de voz: "Voz: alegre"
- Os helpers são reutilizados: `AbstractDynamicScreen.drawTooltip` e `hoveringOver`, além de `WidgetUtils.drawTooltip`.

### 4.2 Editor (`client/gui/VillagerEditorScreen.java`, case traits 701-726)
- **Abas por categoria**: Genético, Emocional, Hobby, Estilo de vida, Social. São botões em linha, com a aba ativa desativada, no mesmo padrão das páginas (369-376).
- **Grade de `IconButtonWidget`** (ícone, nome e `.highlighted()` quando ativo), com tooltip de efeitos idêntico ao da tela de interação.
- O cabeçalho mostra **"Traços 3/5"**.
- Traços em conflito ou abaixo da idade mínima aparecem cinza, com o motivo no tooltip ("Conflita com Alegre", "A partir de adolescente").
- Botão "Sortear de novo", que chama `fillSlots`, e marcação do traço de voz com estrela.
- A aba "personality" é removida.
- Corrige o bug do label do meio, que faz `traitPage++` sem limite (linha 705).

### 4.3 Debug
- `VillagerDebugInfo.java:71`: lista os traços com categoria e efeitos ativos (F3).

Os novos rótulos e descrições entram em `assets/mca/lang/en_us.json` (252-297) e `pt_br.json`: `trait.<id>`, `traitDescription.<id>`, `trait.category.<c>` e `trait.effect.<tipo>`, com parâmetros.

---

## Estado (2026-10-28)

Implementado, sem commit, com compilação e testes passando (`TraitsTest`, 9 testes):
- **Fase 1:**
  - `TraitCategory`, `TraitEffect` (Speed, AttackDamage, DamageTaken, NeedRate, Mood, Thought, SkillGain, HeartGain, HeartLoss, Appeal, Note) e `TraitCondition`.
  - O builder em `Traits`.
  - athletic, tough e weak migrados.
- **Fase 2:**
  - slots 1/3/5/5 na config (`traitSlots*`) e `fillSlots`, chamado no spawn e no crescimento;
  - os traços de personalidade dos pais viram "tendências" (`TraitLeanings`, peso ×4).
- **Fase 2b:**
  - `AgeState.ELDER` e `isAdult()`, aplicado em todas as checagens de adulto;
  - `AdultTicks` com `villagerElderAfterTime` (padrão 480000, `0` desliga);
  - o idoso fica mais lento e mais baixo, cansa mais rápido, aprende ×0.8 e não procria;
  - cabelo grisalho, botão "Idoso" no editor e traços `wise` e `storyteller`, que podem entrar no lugar de outro traço ao envelhecer.
- **Fase 3:**
  - a voz vem dos traços (`Trait.voice`, `VillagerBrain.syncVoice`), e a aba de personalidade saiu do editor;
  - migração uma vez por villager, no primeiro tick do servidor (`TraitsVersion`);
  - o predicado `"personality"` aceita o traço que dá a voz.
- **Fase 4:**
  - faixa de ícones com tooltip de efeitos na tela de interação;
  - editor com categorias, grade, motivo do bloqueio, voz e "Sortear de novo";
  - textos em en_us e pt_br.
- **Fase 5 (parcial):** 42 traços de personalidade em `entity/ai/traits/PersonalityTraits.java`, mais vegetarian (agora em estilo de vida). Foodie, neat, slob e vegetarian estão ligados à dieta e à casa.

Segunda rodada (2026-10-28), também compilada e testada (`TraitsTest`, 12 testes):
- **Postura do idoso:** o tronco se inclina cerca de 11° a partir do quadril (`client/model/ElderPosture`).
  - No modelo do MCA, isso fica em `VillagerEntityBaseModelMCA.setupAnim`.
  - No Fresh Animations, a variável EMF `mca_elder` é usada por um segundo bloco de animação nos `villager*.jem`. Os braços humanos giram de volta e ficam pendurados na vertical.
- **Fase 6:**
  - `Opinions`, de −100 a 100 por villager: sincronizado, até 16 lembrados, enfraquece 1 ponto por dia (Leal enfraquece pela metade);
  - `TraitCompatibility`: afinidade de cada traço e pares que combinam ou brigam;
  - `Conversations`: conversa boa ou briga, mudanças de humor e ciúme do cônjuge;
  - tudo isso ligado à socialização por necessidade e ao CHAT da rotina;
  - a escolha de parceiro prefere amigos e evita inimigos, e o casamento entre villagers pesa as opiniões e a compatibilidade;
  - na tela de interação, uma linha "Amigos / Rivais" com a lista completa no hover;
  - pensamentos "amigo por perto" (+1) e "inimigo por perto" (−2);
  - traços novos: `loyal` e `jealous`.
- **Idoso:**
  - meio turno: depois do meio-dia, trabalho vira tempo livre, a menos que haja horário personalizado;
  - aviso "%s envelheceu" aos filhos jogadores;
  - morte por velhice opcional (`villagerOldAgeDeath`, desligada; chance por noite, dormindo).
- **Dieta:** celíaco (tag `mca:gluten`) e diabetes (`mca:confectionaries`) evitam esses alimentos e perdem humor se forem obrigados a comer, como o vegetariano.
- **Toddler:** `traitSlotsToddler` (padrão 0), 6 traços com `maxAge(TODDLER)`, que saem ao crescer (`dropOutgrown`).
- **Fase 7:**
  - cleptomaníaco: furta 1 item de contêiner alheio a até 2,5 blocos no tempo livre, e quem vê baixa a opinião dele em −10;
  - desastrado: tropeça e pode derrubar um item (`TraitBehaviors`).

Terceira rodada (2026-10-28):
- **Falas de idoso:** `DialogueType.ELDER` (com fallback para ADULT) e 56 linhas `elder.dialogue.*` em `mca_dialogue/lang` (en_us e pt_br): main, manhã, tarde, noite, primeiro encontro, cumprimento, abraço, conversa, história e aperto de mão.
- **Jeito de andar:**
  - `WalkStyle` (SHUFFLE, STOMP, MOPEY, TIMID, SLUGGISH, BOUNCY, SWAGGER, BRISK), escolhido por traço com `Builder.walk()`; o primeiro da lista vence e o idoso sempre arrasta os passos;
  - `WalkPosture` aplica o estilo sobre o andar vanilla: passada, balanço dos braços, pulinho, giro dos ombros, cabeça e inclinação;
  - no Fresh Animations, as variáveis EMF `mca_walk_*` entram no segundo bloco de animação dos `villager*.jem`;
  - o tooltip do traço mostra "Anda: …".
- **Sábio na escola:** a aula rende 1× sozinha, +1 com um bibliotecário e +1 com um Sábio (`SchoolTask.teaching`); a nota `trait.effect.teaches` aparece no tooltip.

Pendente:
- teste em jogo de tudo (o sinal de `body.ry` no balanço dos ombros do FA foi deduzido, não visto).

## 5. Fases e esforço

| # | Fase | Esforço |
|---|---|---|
| 1 | Builder de `Trait`, categorias, conflitos, ícones, `TraitEffect` com aplicação e descrição, efeitos atuais migrados (athletic, tough, weak) | 1–1,5 dia |
| 2 | Slots por idade: config, `fillSlots`, gancho no crescimento, herança | 0,5–1 dia |
| 2b | **Idoso**: `ELDER`, `AdultTicks`, `isAdult()` nas ~20 checagens, rotina, fertilidade, config, lang, editor; visual de cabelo grisalho e postura; traços `wise` e `storyteller` | 2–3 dias (+1 para a morte por velhice opcional) |
| 3 | Fusão da personalidade: voz, migração de save, alias do predicado `personality`, remoção da aba | 1–1,5 dia |
| 4 | **UI**: faixa de ícones e tooltips na interação, editor com abas e grade, lang en/pt | 1,5–2 dias |
| 5 | ~35 traços com os efeitos de necessidades, humor, habilidade, opportunities, dieta e corações; vegetarian, coeliac e diabetes implementados | 2–3 dias |
| 6 | Relações villager↔villager: afinidade por par, compatibilidade de traços (Esquentado×Esquentado briga, Bondoso×Malvado esfria), conversa dá certo ou vira briga em `NeedsSocializeTask`, ciúme, lealdade, escolha de amigos e desafetos, casamento entre villagers ponderado | 4–6 dias |
| 7 | Comportamentos próprios: cleptomaníaco furtando baús (`HomeChests`), desastrado tropeçando, andar diferente por traço (clip no sistema de animações por JSON) | 0,5–1 dia por item |

As fases 1 a 5 entregam o sistema completo menos as relações, em cerca de 7–9 dias. A fase 6 é a que muda de verdade "como se relaciona com outros".

Onde o código fica:
- **Núcleo**: `Traits`, `TraitEffect`, slots, UI e migração.
- **Expansão** (`expansion/minds`): os efeitos que dependem de `Skills`, `Diet`, `HomeQuality` e `Opportunities`, chamados pelos ganchos do núcleo, como hoje em `Needs.thoughts()`.

---

## 6. Arquivos principais
- `entity/ai/Traits.java`, novo `entity/ai/traits/TraitEffect.java` e `TraitCategory.java`
- `entity/ai/relationship/Personality.java`, `entity/ai/brain/VillagerBrain.java`, `entity/ai/Messenger.java`, `entity/ai/DialogueType.java`
- `entity/VillagerEntityMCA.java` (onAgeStateChanged, migração), `entity/VillagerLike.java` (atributos)
- `entity/ai/Needs.java`, `entity/ai/brain/tasks/NeedsSocializeTask.java`
- `expansion/minds/Skills.java`, `Diet.java`, `HomeQuality.java`, `routine/Opportunities.java`
- `entity/interaction/gifts/GiftPredicate.java`
- `client/gui/InteractScreen.java`, `client/gui/VillagerEditorScreen.java`, `client/debug/VillagerDebugInfo.java`
- `CommonConfig.java` / `Config.java`, `assets/mca/lang/en_us.json` e `pt_br.json`

## 7. Verificação (a cada fase)
- Compilar com o cliente **fechado** (memória: dev-client-workflow). O usuário abre o cliente.
- Mundo de teste com um save antigo: os villagers com personalidade ganham o traço mapeado, os diálogos continuam na mesma voz e não há erro no log.
- Ovo de villager adulto: tem 5 traços sem conflito. Criança: 1. Forçar o crescimento (`setAgeState` no editor ou `/mca`) e conferir 1→3→5.
- Idoso: com `villagerElderAfterTime` baixo na config de teste, um adulto vira idoso. Conferir que muda o visual, fica mais lento, ainda passa nas constraints "adult", não procria, recebe o aviso e pode ganhar `wise`. Um save antigo carrega com alguns idosos e nenhuma checagem `== ADULT` quebra (grep por `AgeState.ADULT` revisado).
- Na UI: hover em cada traço mostra efeitos com números que batem com o código. O editor bloqueia conflitos e respeita a idade.
- Efeitos: F3 (debug) mostra os multiplicadores. Comparar a queda de fome entre um Guloso e um villager comum ao longo de um dia (`/time add`) e a velocidade de um Ativo contra um Preguiçoso.
- `gradle build` com testes, se houver para traços e datafix.
