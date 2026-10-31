# Traços do RimWorld e crise mental (WIP)

> **Status: em andamento.** Planejado em 2026-10-28; parte já implementada (ver Estado). Continua o sistema de [tracos-sims.md](tracos-sims.md): mesmo builder (`Traits.builder`), efeitos tipados (`TraitEffect`), condições (`TraitCondition`) e jeito de andar (`WalkStyle`).

## Estado (2026-10-28)
Feito, sem commit e sem teste em jogo:
- **Traços genéticos:**
  - Imunidade forte e Frágil (infecção ×0,5 / ×1,5).
- **Traços de personalidade:**
  - Brigão, Atirador cuidadoso e Gatilho rápido (tempo de puxar o arco e precisão);
  - Gosta de subsolo;
  - Bonito(a), Feio(a) e Voz irritante (afinidade em `TraitCompatibility`);
  - Psicopata (sem luto e sem perda de humor com mortes);
  - Trabalhador(a) e Dedicado(a) (velocidade de trabalho; Preguiçoso agora −15%);
  - Força de vontade e Volátil.
- **Crise mental:** `MentalBreaks`, flag `villagerMentalBreaks`.
  - Disparo: humor ≤ −9 (deslocado pela força de vontade) por 10 minutos.
  - Tipos de surto:
    - birra: larga itens;
    - emburrar: 2 minutos sem trabalhar;
    - comer tudo;
    - briga de socos com quem não gosta: para com 40% da vida (a não ser com `villagerMentalBreakLethal`); um guarda por perto separa; vítima e testemunhas perdem opinião.
  - Depois do surto: alívio (+6 de humor) e 1 dia de calma.

Segunda rodada:
- **Noturno:** sempre recebe a agenda noturna.
- **Piromaníaco:**
  - feliz perto de fogo;
  - acende fogueiras e velas apagadas por perto;
  - a birra dele vira um fogo em chão sem nada inflamável por perto, que se apaga sozinho.
- **Sanguinário:** feliz lutando, +10% de dano, sem luto.
- **Itens menores:**
  - conquista "Uma Vida Longa" (filho envelheceu);
  - metade dos homens sem barba ganha uma ao envelhecer;
  - a tela de debug (F3) mostra os efeitos dos traços e os atributos.

## Já cobertos pelos traços do Sims
| RimWorld | Traço atual |
|---|---|
| Kind | Bondoso (`good`) |
| Gourmand | Guloso (`glutton`) |
| Optimist / Sanguine | Alegre (`cheerful`) |
| Pessimist / Depressive | Melancólico (`gloomy`) |
| Greedy | Materialista (`materialistic`) |
| Tough | Durão (`tough`) |
| Jogger / Fast walker | Atlético (`athletic`), Ativo (`active`) |
| Lazy / Slothful | Preguiçoso (`lazy`) |
| Nervous | Ansioso (`anxious`) |
| Abrasive | Ranzinza (`mean`) |

## Candidatos novos, com gancho real
| Traço | Efeito | Gancho |
|---|---|---|
| **Noturno** | dorme de dia, trabalha à noite; humor + no escuro | `SchedulesMCA` (deslocar os slots) |
| **Imunidade forte / Frágil** | resiste ou é sensível à infecção de zumbi | `INFECTION_PROGRESS` |
| **Lutador** | guarda melhor no corpo a corpo, pior com arco | `AttackDamage`, guarda |
| **Atirador cuidadoso / Gatilho rápido** | arqueiro mais preciso / mais rápido | `BowTask` |
| **Gosta de subsolo** | humor + abaixo do nível do mar ou em caverna; minerador mais feliz | `TraitCondition` nova, minerador |
| **Bonito / Feio** | opinião dos outros + / − | `Opinions`, `Conversations` (fase 6) |
| **Voz irritante** | conversas com ele dão menos e rendem mais brigas | `Conversations.argumentChance` |
| **Psicopata** | sem luto e sem humor ruim com mortes; os outros gostam menos | luto, `Opinions` |
| **Sanguinário** | humor + ao lutar ou ver luta | guarda, caçador |
| **Piromaníaco** | de vez em quando acende fogo longe das casas | `TraitBehaviors` |
| **Escala de dedicação** (Preguiçoso → Trabalhador → Dedicado) | velocidade de trabalho | `Skills.speed` |
| **Força de vontade / Volátil** | limiar da crise mental (abaixo) | crise mental |

## Crise mental
Com o humor muito baixo por muito tempo, o villager tem um **surto**:
- **Birra:** quebra ou joga itens no chão (sem destruir blocos importantes).
- **Vagar sem rumo:** larga o trabalho e anda pela vila.
- **Comer escondido:** come do baú da casa ou do armazém.
- **Se trancar em casa:** recusa conversa e trabalho por um tempo.
- **Surto agressivo:** ataca outro villager, de preferência alguém de quem não gosta (`Opinions`).
  - Por padrão é uma **briga de socos não letal**, que para com pouca vida.
  - Os guardas intervêm.
  - A vítima e as testemunhas perdem opinião dele.
  - A config pode permitir que seja letal.
  - Esquentado e Volátil têm mais chance.

**Regras:**
- **Limiar:** humor abaixo de X por Y minutos. Força de vontade aumenta o limiar e Volátil diminui.
- **Depois do surto:** um pensamento "desabafou" melhora o humor por um tempo, como no RimWorld (*catharsis*).
- **Config:** liga e desliga o sistema e o surto letal.

## Perguntas em aberto
- O surto agressivo pode mirar o jogador?
- O Piromaníaco entra mesmo, já que o fogo destrói construções?
- A escala de dedicação substitui Preguiçoso/Ativo ou convive com eles?
