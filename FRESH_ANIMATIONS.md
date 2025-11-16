# Fresh Animations Integration for MCA

Este documento explica como as animações do Fresh Animations foram integradas ao Minecraft Comes Alive.

## 📁 Estrutura de Arquivos

```
common/src/main/java/net/conczin/mca/client/model/animation/
├── AnimationMath.java              # Funções matemáticas para animações
├── VillagerAnimationState.java    # Estado e variáveis de animação
└── FreshVillagerAnimations.java   # Aplicação das animações
```

## 🎬 Como Funciona

### 1. **AnimationMath.java**
Classe utilitária com funções matemáticas inspiradas no sistema CEM do OptiFine:

- `toRad()` / `toDeg()` - Conversão entre graus e radianos
- `clamp()` - Limita valores entre min/max
- `between()` - Verifica se valor está em intervalo
- `smoothstep()` - Interpolação suave (ease-in-out)
- `pulse()` - Cria pulsos periódicos (0→1→0)
- `wave()` / `cosWave()` - Ondulações senoidais
- `normalizedLimbSwing()` - Normaliza balanço de pernas
- `walkIntensity()` / `runIntensity()` - Calcula intensidade de movimento
- `legSwing()` / `armSwing()` - Balanço natural de membros

### 2. **VillagerAnimationState.java**
Armazena o estado atual da entidade e calcula variáveis de animação:

**Variáveis Calculadas (baseadas em Fresh Animations):**
```java
public final float r;      // Offset aleatório único por entidade
public final float b;      // Fase de "respiração" baseada em idade
public final float ls;     // Limb swing normalizado
public final float swim;   // Fase de natação
public final float walk;   // Intensidade de caminhada (0-1)
public final float run;    // Intensidade de corrida (0-1)
public final float scared; // Estado assustado (quando correndo rápido)
```

**Métodos Úteis:**
- `isPanicking()` - Verifica se está em pânico (usa VillagerBrain do MCA)
- `getHurtMultiplier()` - Multiplicador de animação de dano
- `getIdleLookIntensity()` - Intensidade de olhar ao redor quando idle
- `hasRandomBehavior(min, max)` - Verifica comportamento aleatório único

### 3. **FreshVillagerAnimations.java**
Aplica as animações matemáticas aos model parts:

**Método Principal:**
```java
FreshVillagerAnimations.applyAnimations(
    animState,           // Estado da animação
    head, hat,          // Partes da cabeça
    body,               // Corpo
    rightArm, leftArm,  // Braços
    rightLeg, leftLeg   // Pernas
);
```

**Animações Implementadas:**

#### 🧠 Cabeça (`animateHead`)
- Rotação baseada em `headPitch` e `headYaw`
- Animação de natação (inclinação + ondulação)
- Estado assustado (cabeça para baixo)
- Balanço ao caminhar
- Animação de dano (chacoalhar)
- Respiração quando idle
- Olhar ao redor quando parado

#### 🎽 Corpo (`animateBody`)
- Inclinação ao nadar
- Inclinação ao correr
- Rotação baseada em direção da cabeça
- Balanço ao caminhar
- Movimento de respiração
- Reação ao dano

#### 💪 Braços (`animateArms`)
- Balanço oposto às pernas ao caminhar
- Amplitude maior ao correr
- Posição de natação
- Rotação acompanhando corpo
- Movimento único baseado em ID da entidade
- Reação ao dano

#### 🦵 Pernas (`animateLegs`)
- Balanço natural usando `asin(sin())` para suavizar
- Transição suave entre caminhada e corrida
- Stance width ajustável baseado em direção
- Balanço de equilíbrio (rotação Z)
- Pose de idle levemente relaxada

#### 😱 Pânico (`applyPanicAnimation`)
- Braços levantados e balançando
- **Sobrescreve animações normais dos braços**
- Preserva comportamento original do MCA

## 🎮 Integração no VillagerEntityBaseModelMCA

O método `setupAnim()` foi modificado para aplicar as animações:

```java
@Override
public void setupAnim(T villager, float limbAngle, float limbDistance, 
                      float animationProgress, float headYaw, float headPitch) {
    // 1. Ajustes para bebês
    if (villager.getAgeState() == AgeState.BABY && !villager.isPassenger()) {
        limbDistance = (float) Math.sin(villager.tickCount / 12F);
        limbAngle = (float) Math.cos(villager.tickCount / 9F) * 3;
        headYaw += (float) Math.sin(villager.tickCount / 2F);
    }
    
    // 2. Normalização de limb angle
    if (villager.isBaby()) {
        limbAngle /= 3.0f;
    }
    limbAngle /= (0.2f + villager.getRawVerticalScaleFactor());
    
    // 3. Setup base (HumanoidModel)
    super.setupAnim(villager, limbAngle, limbDistance, animationProgress, headYaw, headPitch);
    
    // 4. Criar estado de animação
    VillagerAnimationState<T> animState = new VillagerAnimationState<>(
        villager, limbAngle, limbDistance, animationProgress, headYaw, headPitch
    );
    
    // 5. Aplicar Fresh Animations
    FreshVillagerAnimations.applyAnimations(
        animState, head, hat, body, rightArm, leftArm, rightLeg, leftLeg
    );
    
    // 6. Override com animação de pânico se necessário
    if (villager.getVillagerBrain().isPanicking()) {
        FreshVillagerAnimations.applyPanicAnimation(animationProgress, leftArm, rightArm);
    }
    
    // 7. Aplicar dimensões do aldeão
    applyVillagerDimensions(villager, villager.isCrouching());
}
```

## 🎨 Animações por Estado

### Estados Detectados Automaticamente:
| Estado | Condição | Animações Afetadas |
|--------|----------|-------------------|
| **Idle** | `limbSpeed < 0.1` | Respiração, olhar ao redor, balanço suave |
| **Caminhando** | `limbSpeed 0.1-0.75` | Balanço natural de membros |
| **Correndo** | `limbSpeed > 0.75` | Amplitude aumentada, inclinação do corpo |
| **Nadando** | `!onGround && inWater` | Movimentos de nado, corpo inclinado |
| **Assustado** | `limbSpeed > 0.9` | Cabeça baixa, respiração acelerada |
| **Ferido** | `hurtTime > 0` | Chacoalhar, recuo |
| **Pânico** | `brain.isPanicking()` | Braços erguidos, movimentos erráticos |
| **Criança** | `isBaby()` | Movimentos mais enérgicos |

## 🔧 Personalização

### Adicionar Nova Animação

1. **Adicionar variável no `VillagerAnimationState`:**
```java
public final float minhaNova;

// No construtor:
this.minhaNova = calcularMinhaAnimacao(...);
```

2. **Criar método de animação em `FreshVillagerAnimations`:**
```java
private static <T extends LivingEntity & VillagerLike<T>> void animateMinhaCustom(
        VillagerAnimationState<T> state, ModelPart parte) {
    
    float rotacao = state.minhaNova * Mth.sin(state.ageInTicks / 10.0f);
    parte.xRot = toRad(rotacao);
}
```

3. **Chamar no `applyAnimations()`:**
```java
animateMinhaCustom(state, parte);
```

### Ajustar Intensidade de Animação

Modifique os multiplicadores nas expressões:

```java
// Exemplo: aumentar balanço de cabeça ao caminhar
baseRotX += Mth.sin(state.ls * 2.0f) / 20.0f * state.getLimbSpeed();
//                                     ^^^^^ reduzir para menos balanço
//                                     ^^^^^ aumentar para mais balanço
```

### Adicionar Comportamento Único por Aldeão

Use `state.randomFactor` ou `state.hasRandomBehavior()`:

```java
if (state.hasRandomBehavior(0.3f, 0.5f)) {
    // Apenas 20% dos aldeões fazem isso
    body.xRot += toRad(5) * state.walk;
}
```

## 📊 Comparação com Fresh Animations Original

### ✅ Implementado
- ✅ Animações de caminhada/corrida
- ✅ Animações de natação
- ✅ Estado assustado (scared)
- ✅ Animações de ferimento (hurt)
- ✅ Respiração e idle
- ✅ Variação por aldeão (random offset)
- ✅ Diferenciação criança/adulto
- ✅ Integração com MCA brain (pânico)

### ❌ Não Implementado (modelo diferente)
- ❌ Animações faciais detalhadas (olhos, sobrancelhas, boca)
  - *Motivo:* MCA usa PlayerModel, não VillagerModel com nariz
- ❌ Animação de braços cruzados
  - *Motivo:* PlayerModel tem braços separados
- ❌ Dança específica de aldeões
  - *Motivo:* Pode ser adicionado se necessário

### 🔄 Adaptado
- 🔄 Animações ajustadas para PlayerModel humanóide
- 🔄 Integração com sistemas únicos do MCA (AgeState, VillagerBrain)
- 🔄 Suporte para camadas extras (sleeves, pants) via `VillagerEntityModelMCA`

## 🐛 Troubleshooting

### Animações muito rápidas/lentas
Ajuste os divisores de tempo nas expressões:
```java
// Mais lento: aumentar divisor
Mth.sin(ageInTicks / 20.0f)  // era 10.0f

// Mais rápido: diminuir divisor
Mth.sin(ageInTicks / 5.0f)   // era 10.0f
```

### Membros em posições estranhas
Verifique se `super.setupAnim()` é chamado **antes** das animações Fresh.

### Animações não aparecem
Certifique-se de que:
1. `VillagerEntityModelMCA.setupAnim()` chama `super.setupAnim()`
2. As camadas extras (sleeves, pants) copiam as transformações no `VillagerEntityModelMCA`

## 📚 Referências

- **Fresh Animations** by FreshLX: [CurseForge](https://www.curseforge.com/minecraft/texture-packs/fresh-animations)
- **OptiFine CEM Documentation**: [OptiFine Docs](https://github.com/sp614x/optifine/blob/master/OptiFineDoc/doc/cem.md)
- **OptiFine CEM Animation**: [OptiFine Docs](https://github.com/sp614x/optifine/blob/master/OptiFineDoc/doc/cem_animation.md)

---

**Créditos:**
- Animações originais: FreshLX (Fresh Animations)
- Implementação em Java: Adaptado para MCA Reborn
- Conversão de expressões matemáticas: Baseado em `villager_animations.jpm`
