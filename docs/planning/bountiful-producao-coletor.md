# Missões, produção e coletor: consolidado de planejamento

Resultado de uma conversa com o Andre sobre três temas ligados entre si:

1. **Integração com o Bountiful**: villagers dão missões, e completar a missão aumenta o relacionamento com quem pediu.
2. **Produção real das profissões**: o que é produzido vai em parte para a vila e em parte para o villager. Uma profissão nova, o **coletor**, faz a logística entre as oficinas e o armazém.
3. **Receitas da gazua e do pé de cabra.**

Nada disso está implementado. É o "estudo do Bountiful" citado em `vilas-consolidado.md`, que deve ser lido junto (seções 2 e 3).

Data: 2026-10-17.

---

## 0. O que já existe no código (base para tudo)

| Peça | Onde | Uso aqui |
|---|---|---|
| Propriedade de blocos: camas, postos de trabalho, containers, portas | `server/property/PropertyRules.java` | Baús dentro da casa de um villager (`Kind.CONTAINER`) pertencem aos moradores. São os "baús particulares". |
| Crimes contra propriedade, arrombamento | `server/property/PropertyCrimes.java` | Gazua (lenta, silenciosa, pode ser vista) e pé de cabra (barulhento, quebra o cadeado) |
| Corações por player | `entity/ai/Memories.java` (`modHearts`) | Recompensa de relacionamento das missões |
| Estoque da vila | `server/world/data/workorder/VillageStorage.java` | Contar, retirar, inserir e achar espaço nos baús dos prédios `storage` |
| Busca e entrega de material | `entity/ai/brain/tasks/workorder/WorkOrderTask.java` | Padrão de ida ao armazém e volta que o coletor pode reaproveitar |
| Impostos | `server/world/data/villageComponents/VillageTaxesManager.java` | Hoje gera itens do nada (`taxesMap`) e os põe no `storageBuffer`, que é descarregado nos baús do armazém |
| Prédios de profissão | `data/mca/building_types/*.json` | `bakery`, `blacksmith`, `fishermans_hut`, `mason` etc. Hoje só exigem a estação de trabalho e não têm efeito. |
| Profissões sem POI | `registry/ProfessionsMCA.java` | `builder` e `miner` usam `PoiType.NONE`, e o coletor seguiria o mesmo modelo |
| Itens de arrombamento | `registry/ItemsMCA.java` | `CROWBAR` (durabilidade 120), `LOCKPICK` (pilha de 16), `PADLOCK` (pilha de 16) |

---

## 1. Integração com o Bountiful

### 1.1 Arquitetura: núcleo próprio e adaptador opcional

**Risco:** o MCA tem a branch 1.21.1 e os ports `dev/26.1.2` e `dev/26.2`. Se o Bountiful não tiver build para todas essas versões, depender dele trava os ports.

Por isso a proposta tem duas camadas:

- **Núcleo de "pedidos" dentro do MCA**, que funciona sozinho. Guarda quem pediu (UUID), a recompensa reservada, os corações e o prazo. Sem o Bountiful, usa objetivos simples próprios: entregar X itens, matar Y mobs.
- **Adaptador Bountiful (soft dependency):** quando o mod está presente, o pedido vira um bounty de verdade, com o objetivo e a interface do Bountiful.

**A confirmar no jar do Bountiful** (ainda não foi inspecionado):
- se dá para criar um bounty por código, com recompensas de itens específicos;
- se existe evento de "bounty concluído" ou se será preciso um mixin no cash-in;
- como funciona o "worth" dos objetivos e das recompensas. Pelo que se sabe, cada entrada tem um valor e o gerador equilibra objetivo e recompensa.

### 1.2 Como o villager entrega a missão

- **Fase 1:** uma opção nova no menu de interação ("Precisa de ajuda?"). O villager entrega o item de bounty direto ao player, sem quadro. O UUID de quem pediu fica gravado no bounty.
- **Fase 2 (opcional):** o villager vai até um bounty board da vila e afixa o pedido. Exige IA de pathing. Pode virar um prédio da árvore de evolução ("mural"), como já sugerido em `vilas-consolidado.md`, seção 3.

### 1.3 Recompensa tirada dos baús particulares

Recomendado: **escrow na criação da missão.**

1. O villager soma o valor do que tem nos baús da casa.
2. Escolhe itens até um orçamento, **tira os itens do baú** e grava esses itens como recompensa no bounty.
3. A dificuldade do objetivo acompanha o valor da recompensa. Quem tem diamante pede coisas perigosas; quem só tem pão pede coisas triviais.
4. Se a missão expirar ou for abandonada, os itens voltam ao baú. Se o baú sumiu, vão para o inventário do villager.

A alternativa é checar o baú só na hora de pagar. Ela é pior porque o item pode ter sido usado ou roubado. Efeito colateral curioso dela: roubar o baú antes é roubar a própria recompensa, e isso já conta como crime.

### 1.4 Itens nos baús particulares

- **Preenchimento inicial:** na primeira vez que um baú é reconhecido como do villager, ele recebe itens de uma loot table por profissão (`mca:homes/<profissão>`). Uma flag no `PropertyManager.Entry` garante que isso aconteça uma vez só. Itens valiosos entram com peso baixo.
- **Reposição contínua:** vem da parte do villager na produção (seção 2.2). Assim os baús se enchem com trabalho real, e oferecer missões não os esvazia para sempre.
- **Config:** chance de item valioso e orçamento máximo por missão.
- **Exploit conhecido:** o player pode pôr os próprios itens no baú do villager e recebê-los de volta como recompensa. Isso só devolve o que já era dele, e os corações dependem da dificuldade, não do item, então o risco é baixo. Se quiser fechar a brecha, dá para marcar os itens que são "do villager".

### 1.5 Relacionamento

- Concluir a missão dá corações a quem pediu, proporcionais à dificuldade (por exemplo +5 numa trivial, +25 numa perigosa).
- Missão que expira ou é abandonada tira alguns corações.
- Missões mais valiosas só aparecem a partir de certo nível de corações.
- Opcional: um bônus menor para os outros moradores da casa.

---

## 2. Produção real das profissões

### 2.1 Modelo

Quando o villager está no turno WORK, perto do posto de trabalho, cada sessão de trabalho executa uma receita de produção definida em datapack: `data/mca/production/<profissão>.json` (entradas, saídas, tempo).

| Tipo | Profissões | Funcionamento |
|---|---|---|
| **Primária** (sem insumo) | farmer, fisherman, shepherd, miner | Matéria-prima: trigo, peixe, lã, pedra/minério |
| **Transformação** (com insumo) | baker, butcher, toolsmith, armorer, weaponsmith, fletcher, leatherworker, mason, cleric, librarian, cartographer | Consome insumos do baú da oficina e gera o produto (3 trigo → pão, ferro → ferramenta, pedra → stone bricks) |
| **Rara** | librarian, cleric, armorer | Chance baixa de item valioso (livro encantado, ender pearl, diamante). Isso alimenta os baús particulares e as recompensas das missões. |

Regras:

- **Teto de demanda (obrigatório):** a oficina para de produzir um item quando o baú dela está cheio ou quando o armazém já tem mais que X unidades dele. Sem isso, a vila vira uma fábrica infinita.
- **Sem simulação offline:** só produz quem está carregado e trabalhando.
- **Sinergia com as work orders:** o mason produz stone bricks, o coletor leva para o armazém e os builders usam na obra.
- **Sinergia com as necessidades** (`vilas-consolidado.md`, seção 2.2): padaria e açougue passam a produzir comida, o que preenche a lacuna "nenhum prédio produz comida".

### 2.2 Divisão vila × villager

Recomendado: usar o **slider de impostos que já existe**. Com imposto em 30%, 30% vai para o baú da oficina (vila) e 70% fica com o villager.

- A parte da vila fica no baú da oficina até o coletor levar.
- A parte do villager vai para o inventário dele e, no fim do turno, para o baú particular da casa.
- Imposto alto deixa a vila rica, mas os villagers pobres e infelizes, o que combina com o mood que os impostos já afetam.

### 2.3 Baú da oficina (trancado)

- **Propriedade:** hoje o `PropertyRules.ownersAt` só atribui container aos moradores da casa. Falta um caso novo: um baú dentro de um prédio de profissão pertence a quem trabalha ali, mais o coletor. Abrir sem permissão é crime, e gazua e pé de cabra funcionam nele.
- **Um baú só:** guarda insumos e produtos. O coletor sabe o que é cada coisa pela tabela de produção.
- **Sem baú:** a oficina produz mesmo assim, mas tudo vai para o villager. Colocar o baú é o que liga a logística da vila. Não exigir `#mca:chests` nos building types, para não descaracterizar prédios de vilas existentes.

---

## 3. Profissão Coletor

Registrada com `PoiType.NONE`, como `builder` e `miner`, com o armazém como base. No turno WORK:

1. **Recolher:** visita as oficinas cujo baú tem produtos acima de um limite, da mais próxima para a mais distante, até encher o inventário. Descarrega no armazém (`VillageStorage.findSpaceFor` e `insert`).
2. **Abastecer:** para cada oficina, calcula o que falta de insumo para manter um estoque-alvo (por exemplo, 5 ciclos de produção), retira do armazém e entrega.
3. **Viagem combinada:** sai levando insumo e volta trazendo produto.

Escala: cerca de **1 coletor para cada 5 oficinas**. Sem coletor, os artesãos buscam o próprio insumo de vez em quando, com baixa eficiência, para a vila não travar.

---

## 4. Gazua e pé de cabra

Já existem receitas em `common/src/main/resources/data/mca/recipe/` (`crowbar.json`, `lockpick.json`, `padlock.json`), como arquivos novos ainda não commitados.

| Item | Receita atual | Avaliação | Proposta |
|---|---|---|---|
| Pé de cabra | 4 lingotes de ferro em gancho (` II` / ` I ` / `I  `) | Durabilidade 120, gasta 10 por uso (12 arrombamentos), barulhento, quebra o cadeado. Razoável. | Manter |
| Gazua | 2 pepitas de ferro, sem forma, rende 2 | **Barata demais** para roubo silencioso; só é consumida quando o arrombamento dá certo | 2 pepitas na diagonal + 1 graveto, rende **1** |
| Cadeado | 3 pepitas em arco + 3 lingotes | — | Manter |

Receita proposta para a gazua:

```
  N      N = iron_nugget
 N       S = stick
S
```

Extra que combina com a economia: o outlaw vende gazuas, e o toolsmith produz pés de cabra na oficina.

---

## 5. Decisões em aberto

1. **Bountiful:** soft dependency com núcleo próprio (recomendado) ou integração só com o Bountiful?
2. **Versões:** em quais versões de Minecraft a feature precisa funcionar? Isso define se o Bountiful pode ser obrigatório.
3. **Pagamento da missão:** escrow na criação (recomendado) ou checar o baú na hora de pagar?
4. **Baús particulares:** só o preenchimento inicial, ou também reposição pela produção (recomendado)?
5. **Divisão da produção:** slider de impostos (recomendado) ou porcentagem fixa no config?
6. **Impostos atuais:** substituir pela produção real quando a feature estiver ligada, ou somar as duas fontes?
7. **Coletor:** surge sozinho quando a vila tem armazém e oficinas suficientes, ou só quando é atribuído pelo player ou pelo editor?
8. **Gazua:** trocar para a receita com graveto, rendendo 1?

---

## 6. Próximos passos sugeridos

1. Baixar o jar do Bountiful para a versão-alvo e confirmar os pontos de integração (seção 1.1).
2. Fechar as decisões da seção 5.
3. Ordem de implementação sugerida, cada etapa usável sozinha:
   1. receitas da gazua e do pé de cabra (trivial);
   2. baú da oficina com propriedade e produção por datapack (seções 2.1 a 2.3);
   3. coletor (seção 3);
   4. preenchimento dos baús particulares (seção 1.4);
   5. núcleo de pedidos e adaptador Bountiful (seções 1.1 a 1.3 e 1.5).
4. Trabalhar num worktree próprio: o checkout principal é compartilhado por várias sessões.
