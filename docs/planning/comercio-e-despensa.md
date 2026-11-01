# Comércio e despensa: comida em casa, despensa da vila e mercado entre aldeões

Data: 2026-10-31. Pedido do Andre, depois de olhar como os aldeões conseguem comida:
1. **Família:** quem leva comida para casa guarda também para o cônjuge e os filhos; uma criança com fome pega do
   baú dos pais.
2. **Despensa da vila:** uma parte da comida produzida vai direto para o armazém, para quem não tem renda.
3. **Comércio:** caçadores vendem carne, o padeiro pão, os fazendeiros legumes; trocam ferramentas por recursos entre
   si.

**Decidido (2026-10-31):** mercado pelos desenhos **A** (armazém como mercado) e **B** (barracas no mercado); o
**C** (troca direta entre aldeões) fica de fora: é difícil de ver em jogo e caro de manter. Família e despensa
seguem como descritas.

Nada disso está implementado. Pertence aos addons **Fome e Sede** (1 e 2) e **Trabalho e Economia** (3), ver
[addons.md](addons.md).

---

## 0. O que já existe (base para tudo)

| Peça | Onde | O que faz hoje |
|---|---|---|
| Parte da vila na produção | `expansion/economy/ProductionSink` | O imposto (painel) decide quanto do que a oficina produz vai para o baú da oficina; o resto fica com o trabalhador |
| Levar para casa | `economy/task/StashTask` | Bolsos cheios: o trabalhador guarda tudo num baú de casa, menos ferramentas, 8 comidas e 16 sementes |
| Carregador | `economy/task/HaulerTask` | Leva da oficina para o armazém e traz os insumos de volta (sem carregador, o artesão busca os próprios) |
| Buscar comida | `entity/ai/brain/tasks/NeedsFetchFoodTask` | Com fome e sem comida: até 4 comidas de um baú **da própria casa** (prédio do quarto, ou 8 blocos da cama) |
| Despensa comunitária | `expansion/minds/Provisions` | Sem nada em casa: o armazém da vila, **só com `villagerSkills`** |
| Fazer pão | `NeedsBakeBreadTask` | Sem comida em lugar nenhum: trigo de casa ou do armazém, assado na mesa de trabalho |
| Troca vanilla | `TradeWithVillager` (nos encontros) | Quem tem comida sobrando joga um pouco para quem tem pouco |
| Esmeraldas | `addon/roads/caravan/CaravanTrade` | As caravanas compram e vendem com o armazém, 1 esmeralda por 12 itens |
| Barganha | `expansion/barter` | Só entre o jogador e um aldeão |
| Ferramenta nova | `Provisions` | Ferramenta quebrou: o trabalhador pega uma nova do armazém, de graça |

**O que falta:** quem não trabalha (crianças, desempregados, idosos) não tem renda; os pais não alimentam os filhos;
nenhum aldeão compra nada de outro; o armazém só serve de despensa com `villagerSkills`.

---

## 1. Família: a casa como despensa da família

**Ideia:** a comida de casa é da família, não só de quem a trouxe.

- **Guardar para a casa.** O `StashTask` passa a guardar comida a mais quando a casa tem pouca: o trabalhador conta
  quantas pessoas moram na casa (ele, cônjuge, filhos que dormem lá) e quanta comida há nos baús; se houver menos
  que **4 por pessoa**, deixa no baú também parte das 8 que levaria no bolso (fica com 2).
- **Achar a casa dos pais.** O `NeedsFetchFoodTask` de uma criança (ou adolescente) procura também nos baús da casa
  dos pais, quando a dela não tiver comida (os pais vêm de `Relationship`/árvore genealógica; a casa de cada um, da
  memória `HOME`).
- **Cônjuge.** Mesma regra: sem comida em casa, o cônjuge olha o baú da casa do outro, se morarem separados.
- **Propriedade.** Baús da família contam como próprios em `PropertyRules.canVillagerUse` (hoje só o dono e quem o
  dono permite), para não virar furto.

**Teste (GameTest):** casal com um filho; o pai traz 10 pães; a criança com fome os encontra no baú de casa, e na casa
dos pais quando mora em outra.

---

## 2. Despensa da vila: comida para quem não tem renda

**Ideia:** uma parte de toda comida produzida vai para o armazém, mesmo com o imposto baixo; quem não tem renda come de
lá.

- **Parte de comida.** Em `ProductionSink.villageShare`, a comida (itens comestíveis) usa
  `max(imposto, economyFoodShare)`; nova opção `economyFoodShare` (padrão **0,25**). O carregador já leva isso ao
  armazém.
- **Também a colheita.** Fazendeiro, pescador, coletor e caçador já entregam a parte da vila na oficina; vale a
  mesma regra.
- **A despensa sem `villagerSkills`.** `Provisions.storageWith` passa a depender de `villagerEconomy` (é economia,
  não talento). Com as duas desligadas, nada muda.
- **Ordem de quem come.** Quem não trabalha pode levar até 4 comidas; quem trabalha só pega do armazém se a casa e a
  família estiverem vazias, e leva 2 (ração). Evita que os trabalhadores esvaziem a despensa dos sem renda.
- **Painel.** A prefeitura mostra "Despensa: N refeições" e quantos estão sem comida em casa.

**Teste:** uma criança órfã sem casa com comida, um armazém com pão; ela come do armazém. Um açougueiro produzindo
com imposto 0 ainda manda 1 em cada 4 carnes ao armazém.

---

## 3. Comércio entre aldeões

### 3.1 Moeda

**Esmeraldas**, como as caravanas já usam. Cada aldeão tem as suas no inventário e no baú de casa (as "economias" dos
pedidos já são isso). Fica tudo de verdade, sem número escondido: o jogador pode ver, roubar, ganhar.

### 3.2 Quem vende o quê

| Profissão | Vende | Compra |
|---|---|---|
| Caçador | carne crua, couro, penas | flechas, arco, comida pronta |
| Açougueiro (padaria e açougue) | pão, carne assada | trigo, carne crua, carvão |
| Fazendeiro | trigo, cenoura, batata, beterraba, abóbora | enxada, farinha de osso, pão |
| Pescador | peixe | vara de pesca, pão |
| Coletor | frutas, cogumelos, cana | comida pronta |
| Lenhador | toras, gravetos | machado, comida |
| Minerador | carvão, ferro, pedra | picareta, tocha, comida |
| Ferramenteiro | ferramentas | ferro, gravetos, carvão |
| Pastor, curtidor, flecheiro… | o que a oficina produz (`data/mca/production`) | os insumos da receita |

A tabela não precisa ser escrita à mão: **vende o que a receita produz, compra o que a receita consome**, mais comida e
a ferramenta da profissão (que quebra com o uso).

### 3.3 Preços

- Um preço base por item em dados (`data/mca/prices/*.json`), com uma regra de reserva: itens sem preço valem pelo
  custo da receita (insumos + tempo).
- **Oferta e procura:** o preço sobe quando a vila tem pouco do item (armazém + barracas), cai quando tem muito. As
  caravanas já usam "a vila tem pouco" (menos de 16): mesma medida.
- Os mesmos preços valem para o jogador, na barganha e no mercado.

### 3.4 Onde se compra: três desenhos

| | Como funciona | Visível? | Custo |
|---|---|---|---|
| **A. Armazém como mercado** | Ao guardar a sua parte, o trabalhador **vende** ao armazém e recebe esmeraldas; quem precisa **compra** do armazém pagando. O armazém é o caixa da vila | pouco: só idas ao armazém | baixo: usa armazém, carregador e `Provisions` |
| **B. Barracas no mercado** | Um prédio novo `market` (tipo `building_types/market.json`) com **barracas** (barril + placa ou um bloco próprio). Cada vendedor tem a sua, abastece com o que produz e fica lá em parte do dia; os compradores vão lá, pagam e levam | muito: o mercado tem movimento | médio: prédio, bloco, horário na agenda, rotina de compras |
| **C. Troca direta** | Nos encontros, quem precisa de algo procura quem tem (o caçador com carne no bolso) e troca, com ou sem esmeralda | médio | alto: casar ofertas e procuras entre pessoas que andam |

**Decidido:** **A** primeiro (rápido, já dá economia de verdade e fecha o ciclo do dinheiro) e **B** em cima dele (as
barracas viram uma "vitrine" do armazém, com o dono por perto). **C** descartado: difícil de ver e caro de ter; o
`TradeWithVillager` vanilla continua como está.

### 3.5 O ciclo do dinheiro (desenho A)

1. **Produz:** o trabalhador produz na oficina; a parte da vila (imposto) vai para o armazém como hoje.
2. **Vende:** a parte dele, em vez de ir só para o baú de casa, pode ser **vendida** ao armazém: ele guarda o que a
   casa precisa (seção 1) e vende o resto. Recebe esmeraldas, que leva para casa.
3. **Compra:** com fome, sem comida em casa, e com esmeraldas: compra no armazém. Sem esmeraldas: a despensa (seção 2).
4. **Ferramentas:** a ferramenta quebrou: compra uma no armazém (hoje é de graça). O ferramenteiro compra ferro e
   gravetos do armazém para produzir; o minerador e o lenhador vendem os seus. É a "troca de ferramentas por recursos",
   com a esmeralda no meio.
5. **O caixa da vila:** o armazém paga com as esmeraldas que tem (dos impostos, das caravanas, das vendas). Sem
   esmeraldas no caixa, compra fiado (anota) ou não compra; o painel da prefeitura mostra o caixa.

**Sem dinheiro no mundo, nada trava:** a despensa (seção 2) alimenta quem não tem esmeraldas.

### 3.6 Ligações com o resto

- **Atributo Social** (Talentos): melhora o preço, como já faz na barganha com o jogador.
- **Traços** (Sentimentos): avarento guarda esmeraldas e compra pouco; generoso dá comida à família; cleptomaníaco
  furta da barraca (Propriedade e Crime).
- **Caravanas:** continuam comprando e vendendo com o armazém, agora pelos mesmos preços.
- **Pedidos:** a recompensa sai das esmeraldas do aldeão.
- **Prefeitura:** caixa, despensa, preços e quem está sem comida.

---

## 4. Ordem sugerida

| Fase | O quê | Depende de | Tamanho |
|---|---|---|---|
| 1 | Família (seção 1) | — | pequeno |
| 2 | Despensa da vila (seção 2) | — | pequeno |
| 3 | Preços e o armazém como mercado (3.1 a 3.5, desenho A) | 2 | médio |
| 4 | Ferramentas compradas, insumos do ferramenteiro | 3 | pequeno |
| 5 | Mercado com barracas (desenho B) | 3 | médio a grande |

Cada fase atrás de uma opção (padrão desligada) e com GameTests; o livro-guia ganha um capítulo "Comércio".

## Perguntas em aberto

1. **Moeda:** esmeraldas mesmo, ou um item próprio (moedas de cobre, prata, ouro)?
2. ~~**Mercado:**~~ decidido: A e depois B; C descartado.
3. **Imposto:** com vendas ao armazém, o imposto continua sendo uma parte das mercadorias ou passa a ser uma parte
   das vendas em esmeraldas?
4. **Caixa vazio:** o armazém compra fiado ou deixa de comprar?
