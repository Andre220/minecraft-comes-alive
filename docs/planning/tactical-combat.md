# Tactical Combat: corte, concussão e perfuração (WIP)

> **Status: em andamento.** Mod separado, em jar e repositório próprios (`../tactical-combat`, `mod_id` `tactical_combat`), planejado em 2026-10-31. Fases 0 a 5 feitas (ver Estado). Cada golpe físico se divide em **corte**, **concussão** e **perfuração**. Armas, armaduras e mobs lidam com cada tipo de um jeito, e o **peso** de armas e armaduras entra na conta. O objetivo é um combate mais tático, que dê uso a armas e armaduras específicas. O MCA vai integrar depois, sem dependência de código.

## Estado (2026-11-01)
Fases 0 a 5 feitas, com commit no repositório do mod (sem remoto). Ainda não testado no cliente; os testes automáticos passam (30 GameTests, `./gradlew :fabric:runGameTest` no `../tactical-combat`).

- **Repositório:** esqueleto do Tabletop, só Fabric, MC 26.2.
  - O plano completo está em `docs/plan.md` do repositório do mod.
  - O jar fica em `fabric/dev-mods` do MCA, junto com Spell Engine, Spell Power e PlayerAnimationLib (ver [docs/dev-mods.md](../dev-mods.md)).
- **Cálculo do dano** (`damage/TypedDamage`): um mixin em `LivingEntity.hurtServer`.
  - Vale para golpes de jogador, de mob e de aldeão do MCA; fogo, queda e magia ficam como estão (tag de dano `tactical_combat:physical`).
  - A fonte de dano continua a do vanilla, então pontos de armadura, encantamentos e outros mods funcionam como antes.
- **Perfis em JSON**, recarregáveis com `/reload` e enviados ao cliente para o tooltip:
  - `data/<ns>/tactical_combat/weapon_profiles`, `armor_profiles` e `mob_profiles`;
  - gerados por `scripts/profiles.py` no repositório do mod: 19 de armas, 29 de armaduras e 20 de mobs;
  - um item citado pelo id vence uma tag; ids de mods ausentes são ignorados.
- **Peso** (`weight/Weight`):
  - atributo novo `tactical_combat:weight`, presente em toda entidade viva;
  - modificadores temporários, refeitos ao carregar a entidade e a cada troca de arma ou armadura;
  - constantes em `config/tactical_combat.json`.
- **Tooltip:** "Corte 60% · Perfuração 40%", o peso e o que ele faz ("Leve: +5% velocidade de ataque", "Pesada: +20% dano de concussão"); nas peças de armadura, as resistências e a velocidade perdida.

## Câmera segue a animação (2026-11-01)
Pedido do usuário depois de testar o Better Combat com o First Person Model.

**O problema:** no golpe, o corpo inteiro se inclina e a câmera fica parada. O cabelo aparece na frente da tela, e olhando para baixo aparecem o peito e os ombros.

**A solução:** em primeira pessoa, a câmera segue a cabeça animada pela PlayerAnimationLib.
- Posição: o olho gira com o corpo em torno do quadril, como a PAL desenha.
- Rotação: a do corpo mais a da cabeça.
- Inclinação lateral: aplicada onde o jogo faz o tremor de dano, porque a câmera vanilla não tem roll.

**Só a visão se move.** A mira continua a do mouse, então o acerto do Better Combat não muda. O efeito tático vem disso: errar com a maça faz olhar para o chão até o golpe terminar.

**Config:**
- `cameraFollowsAnimation` (liga e desliga);
- `cameraFollowStrength` (de 0 a 1).

**Ainda não testado no jogo.** Se algum eixo andar ao contrário, basta trocar o sinal em `AnimatedCamera`.

## Efeitos dos golpes (fase 3, 2026-11-01)
Só valem quando o golpe acerta de fato (`HitEffects`, no fim de `hurtServer`). A chance de cada marca cresce com a parcela do tipo no golpe e com o multiplicador do mob para esse tipo.

| Tipo | Marca | Chance (golpe 100% do tipo) | Detalhe |
|---|---|---|---|
| Corte | Sangramento (`spell_engine:bleed`) | 35% | 5 s; cada corte novo sobe um nível, até III; mobs `bloodless` não sangram |
| Concussão | Lentidão | 40% | 1,5 s |
| Concussão | **Fratura** (nossa) | 4% + 3% por ponto de peso acima de 4 (maça: 16%) | 2 min, −20% de movimento e −15% de velocidade de ataque por nível (até II); mobs `boneless` (slime, blaze, golem…) não fraturam |
| Concussão | Atordoamento (`spell_engine:stun`) | 6%, mais com arma pesada | 0,75 s |
| Perfuração | Crítico | 15% | +50% de dano (mais o crit damage do Critical Strike, se instalado) e atravessa metade da armadura; partículas e som de crítico |

- **Tala:** 2 gravetos + 1 couro rendem 2. Na aba Combate. Usar por 2 segundos tira a fratura.
- **Dormir:** acordar de manhã também cura a fratura.
- **Arte provisória:** a textura da Tala e o ícone da Fratura são gerados por `scripts/content_assets.py`.
- **Câmera:** ficou mais suave. Ela persegue a cabeça em 0,12 s (`cameraFollowSmoothing`) e a força padrão caiu para 0,7, porque 100% na hora podia dar enjoo.

## Regra principal: não repetir a série RPG
O usuário quer o mod conversando com a série RPG do ZsoltMolnarrr: Better Combat, Spell Engine, Archers, Wizards, Rogues & Warriors, Paladins & Priests, Arsenal, Forcemaster e Witcher. **Nada que ela já tem é refeito aqui.**

| Já existe (usamos o deles) | Lacuna (o que o Tactical Combat faz) |
|---|---|
| Sangramento: `spell_engine:bleed` | Tipos de dano físico, com resistência por tipo (na série, só a magia tem) |
| Atordoamento: `spell_engine:stun` | Perfil de armadura por tipo de dano |
| Crítico (chance e dano): Critical Strike | Peso de arma e de armadura |
| Alcance, área do golpe, combos e animações: Better Combat | Fratura, local do golpe e desmembramento |
| Tipos de arma: tags `rpg_series:weapon_type/*` | Reações por mob (creeper, slime, esqueleto…) |
| | Mobs usando o sistema (o Better Combat só vale para jogadores) |

- **Spell Engine** é dependência obrigatória. Só o id do efeito é usado, sem dependência de código.
- **Critical Strike** é opcional.
- **Recurso 4 reduzido ao peso:** alcance e animações de ataque ficam com o Better Combat.

## Armas

| Arma | Corte | Concussão | Perfuração | Peso |
|---|---|---|---|---|
| Espada | 60 | – | 40 | 3 |
| Lança | – | – | 100 | 4 |
| Machado | 60 | 40 | – | 5 |
| Picareta (ferramenta) | – | 50 | 50 | 5 |
| Enxada, a "foice" (ferramenta, dano baixo) | 50 | 50 | – | 2 |
| Pá | 20 | 80 | – | 4 |
| Maça (porrete) | – | 100 | – | 8 |
| Tridente, flecha, virote | – | – | 100 | 5 / 0 |
| Mão vazia | – | 100 | – | 0 |

- **Série RPG,** pelas tags `rpg_series:weapon_type/*`:

  | Arma | Divisão do dano | Peso |
  |---|---|---|
  | Adaga | perfuração 70 / corte 30 | 1 |
  | Foice curta (sickle) | corte 100 | 2 |
  | Machado duplo | corte 60 / concussão 40 | 7 |
  | Glaive | corte 50 / perfuração 50 | 6 |
  | Montante | corte 60 / concussão 40 | 7 |
  | Martelo | concussão 100 | 9 |
  | Maça | concussão 100 | 7 |
  | Lança | perfuração 100 | 4 |

- **Mobs sem arma** têm o próprio golpe: o zumbi soca (concussão), a aranha morde (perfuração), o lobo morde e arranha.

## Mobs

| Mob | Corte | Concussão | Perfuração | Reação planejada |
|---|---|---|---|---|
| Zumbis | 1,25 | 0,75 | 1,25 | Perdem membros, rastejam |
| Esqueletos (sem sangue) | 0,5 | 1,5 | 0,5 | Crítico de concussão explode em ossos |
| Slime | 1,25 | 0,25 | 1,25 | Sangrando, deixa poça grudenta |
| Magma cube | 1 | 0,25 | 1 | Poça de magma que queima |
| Creeper | 1,25 | 1,25 | 1 | Crítico de perfuração detona na hora |
| Aranhas | 1,25 | 1 | 1 | Perdem patas |
| Phantom | 1,25 | 1 | 1,5 | Asa rasgada, cai |
| Ghast | 1 | 0,5 | 2 | Perfuração estoura |
| Blaze (sem sangue) | 0,5 | 1,5 | 0,75 | Concussão quebra um bastão |
| Golem de ferro (sem sangue) | 0,25 | 1,5 | 0,5 | Racha antes |
| Breeze, shulker (sem sangue) | 0,5 | 1 | 0,5 | – |
| Silverfish, endermite | 1 | 1,5 | 0,75 | – |
| Wither, Warden, Dragão | 0,9 a 1,1 | | | Sem desmembramento |

## Armaduras
Cada conjunto completo soma as resistências abaixo. O peito conta 40%, as pernas 30%, o capacete e as botas 15% cada. Positivo deixa passar menos daquele tipo; negativo, mais.

| Conjunto | Corte | Concussão | Perfuração | Peso |
|---|---|---|---|---|
| Couro | – | +20% | −10% | 4 |
| Cobre | +5% | – | +5% | 9 |
| Cota de malha | +25% | −10% | – | 8 |
| Ferro | +10% | −5% | +15% | 12 |
| Ouro | +5% | – | – | 10 |
| Diamante | +10% | – | +10% | 10 |
| Netherite | +10% | – | +20% | 14 |

## Peso
Uma arma de peso 4 é neutra.

| Caso | Efeito | Exemplo |
|---|---|---|
| Arma leve de corte/perfuração | +5% de velocidade de ataque por ponto abaixo de 4 | Espada +5%, adaga +15% |
| Arma pesada com concussão | +5% de dano de concussão e +0,1 de repulsão por ponto acima de 4 | Maça +20% de dano e +0,4 de repulsão |
| Armadura, velocidade | −0,5% de movimento e −0,4% de velocidade de ataque por ponto (no máximo −50%) | Netherite −7% de movimento |
| Armadura, concussão | +0,5% de resistência a concussão por ponto | Netherite +7% |

- Picareta e enxada não ganham os bônus de arma leve ou pesada.
- Vale para mobs e aldeões também: um guarda de ferro anda mais devagar.

## Reações (fase 4)
Cada reação tem um id e entra no `reactions` do perfil do mob. Um id que ninguém registrou não faz nada, então os perfis já citam reações da fase 5. Outros mods registram as suas com `Reactions.register`.

| Reação | Quando | O que acontece |
|---|---|---|
| `creeper_detonate` | Crítico de perfuração | O creeper explode na hora, mesmo se o golpe o matar. Segue a regra `mobGriefing`. |
| `slime_puddle` / `magma_puddle` | Golpe de corte ou perfuração: 50% para um golpe só de corte; sempre no golpe que mata | Poça fina no chão: gruda como mel (velocidade ×0,4, pulo ×0,5) e seca num random tick, cerca de 1 min. Slimes grandes espalham mais. A de magma queima quem pisa sem agachar e brilha de leve. Não dá item. |
| `bone_burst` | Golpe de concussão que mata: 50% para concussão pura, mais com arma pesada | O esqueleto se desfaz: ossos inteiros (partícula `flying_item`) sobem girando e caem atravessando o chão, e o corpo some sem fumaça. Caem de 2 a 4 ossos reais (o loot é completado; Looting pode dar mais). |
| `ghast_pop` | Crítico de perfuração | O ghast estoura na hora (dano 1000, igual à bola de fogo devolvida). |
| `blaze_rod_break` | Golpe de concussão: 25% × 1,5 do multiplicador do blaze | Um bastão quebra: cai 1 pó de blaze e o blaze despenca um pouco. No máximo 2 por blaze (fica nas tags da entidade). |

Config: `puddleChance`, `boneBurstChance`, `blazeRodChance` e `maxBrokenRods`.

## Local do golpe e partes do corpo (fase 5)
**Local do golpe.** Vale para quem está na tag `#tactical_combat:humanoid`: jogador, zumbis, esqueletos, piglins, illagers, bruxa, aldeões e os aldeões do MCA.
- **Onde cai:**
  - o golpe do jogador cai onde ele olha;
  - o projétil cai onde o voo encontra o corpo;
  - o mob bate mais ou menos na própria altura, com variação: cerca de 16% na cabeça entre mobs do mesmo tamanho.
- **Divisão do corpo:** cabeça no quarto de cima; pernas abaixo de 37,5%; no meio, os braços ficam nas laterais.
- **Efeitos:**
  - cabeça: dano ×1,5;
  - pernas: 50% de chance de Lentidão II por 2 s;
  - braço: 50% de chance de Fraqueza por 3 s.

**Partes perdidas.** Ficam num attachment do Fabric, salvo com a entidade e enviado aos jogadores. O efeito no corpo vem de modificadores de atributo permanentes.

| Mob | Quando | O que acontece |
|---|---|---|
| Zumbis (reação `dismember`) | Corte no braço ou nas pernas: 25% num golpe só de corte | Braço: −40% de dano por braço e o item daquela mão cai (se cairia na morte). Pernas: rasteja deitado, −70% de velocidade e 40% da altura. |
| Zumbis | Crítico de perfuração na cabeça; ou golpe de corte que mata, na cabeça (50%) | Morte na hora e a cabeça sai voando |
| Aranhas (`spider_leg`) | Corte: 25% × parcela × 1,25 | Perde uma pata: −10% de velocidade por pata, no máximo 4. Com 3 ou mais, não escala paredes. |
| Phantom (`phantom_wing`) | Corte: 25% × parcela × 1,25 | Asa rasgada: cai e não voa mais |
| Golem de ferro (`golem_crack`) | Concussão: 30% × parcela × 1,5 | Uma rachadura, até 3: aparece na hora no modelo e cada uma deixa passar +15% de dano. Consertado até a vida cheia, fica inteiro de novo. |

- **Membro decepado** (entidade `tactical_combat:severed_limb`):
  - é a própria parte do modelo do mob, com a textura dele;
  - voa para longe de quem cortou, cai, fica no chão por 30 s e não é salvo;
  - com Fresh Animations, uma parte que o EMF trocou por outra não aparece solta.
- **Variáveis do EMF**, para o Fresh Animations esconder as partes nos modelos dele:
  - `tc_arm_left_lost`, `tc_arm_right_lost`, `tc_head_lost`, `tc_legs_lost` e `tc_crawling`;
  - `tc_spider_legs_lost` e `tc_spider_leg_0_lost` a `tc_spider_leg_7_lost`;
  - `tc_wing_left_lost` e `tc_wing_right_lost`;
  - `tc_cracks`.
- **Aldeões zumbis do MCA:** eles herdam de `Zombie`, então também perdem partes. Só que as camadas do MCA (pele, roupa, cabelo) ligam de novo todas as partes do modelo, então o membro perdido ainda aparece nelas. Esconder o membro nessas camadas fica para a fase 6.
- **Config:** `headMultiplier`, `legSlowChance`, `armWeaknessChance`, `dismemberChance`, `decapitateChance`, `lostArmDamage`, `crawlSpeedPenalty`, `spiderLegChance`, `maxSpiderLegs`, `spiderLegsToStopClimbing`, `lostLegSpeed`, `phantomWingChance`, `golemCrackChance`, `crackDamageBonus` e `limbLifetimeTicks`.

## Roda de habilidades (2026-11-02)
**O problema:** com uma arma que tem habilidades, o Spell Engine pegava os números 1 a 9 para elas. Os números deixavam de trocar o item da barra.

**A solução:** segurar **Z** abre um anel com as habilidades da arma.
- O mouse aponta uma habilidade em vez de girar a cabeça. Ao soltar, ela vai para o **clique direito**, que é o primeiro slot do Spell Engine.
- Os números nunca lançam habilidades por padrão. Quem quiser teclas próprias associa "Spell Hotbar Slot N" nos controles.
- **Com escudo ou arco na mão**, o clique direito fica com o item. A habilidade escolhida vai para o "Spell Hotbar Slot 1", que precisa de uma tecla própria.
- **Ainda não testado no jogo.**

## Fases

| Fase | Conteúdo | Estado |
|---|---|---|
| 0 | Repositório, Spell Engine em dev-mods, plano | feito |
| 1 | Perfis, gancho de dano, tooltip, GameTests | feito |
| 2 | Peso: atributo, modificadores, config | feito |
| 2b | Câmera segue a animação em primeira pessoa | feito, sem teste em jogo |
| 3 | Efeitos: sangramento e atordoamento do Spell Engine; Fratura e Tala (nossos); crítico de perfuração com Critical Strike opcional | feito, sem teste em jogo |
| 4 | Reações: creeper, poças de slime e magma, explosão de ossos, ghast, blaze | feito, sem teste em jogo |
| 5 | Local do golpe, desmembramento, zumbi rastejando, pata de aranha, asa de phantom, golem rachado, variáveis EMF para o Fresh Animations | feito, sem teste em jogo |
| 6 | Integração com o MCA e com o Forsaken Adventurers | outra conversa |

## Integração com o MCA (outra conversa)
Sem dependência de código, como no Tabletop e no Village Furnishings. O MCA só cita ids e efeitos.
- Guardas e aldeões já atacam pelo mesmo caminho, então o sistema vale para eles sem nada novo.
- **Ideias:**
  - guardas escolhem a arma pelo inimigo (maça contra ataque de esqueletos);
  - o clérigo trata sangramento e põe tala na fratura;
  - traços com bônus por tipo (um "Brutamontes" com concussão);
  - bandidos e facções usam o mesmo sistema.
- **Forsaken Adventurers / ANARCHY RPG** só existe até a 1.21.1, então essa integração depende de um port.
