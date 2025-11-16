# Expression Textures (Eyebrows)

Este diretório contém as texturas de sobrancelhas que mudam baseado na emoção do NPC.

## Texturas Necessárias

Crie 6 texturas PNG (64x64 ou maior, múltiplos de 2):

1. **neutral.png** - Sobrancelhas relaxadas, estado padrão
2. **angry.png** - Sobrancelhas franzidas, ângulo para baixo (raiva)
3. **scared.png** - Sobrancelhas levantadas, ligeiramente curvadas (medo)
4. **sad.png** - Cantos internos levantados, caindo (tristeza)
5. **happy.png** - Sobrancelhas ligeiramente levantadas, curva suave (felicidade)
6. **focused.png** - Sobrancelhas ligeiramente abaixadas, concentrado (trabalhando)

## Quando cada expressão aparece:

- **neutral**: Estado padrão, quando relaxado
- **angry**: Quando foi atacado recentemente ou tem alvo de ataque
- **scared**: Em pânico ou com pouca vida (<30%)
- **sad**: Quando o humor (mood) está baixo (<30)
- **happy**: Quando o humor está alto (>70)
- **focused**: Quando está executando uma tarefa (chore)

## Formato das texturas:

- Resolução: 64x64 (ou 128x128, 256x256 para mais detalhes)
- Formato: PNG com transparência
- As sobrancelhas devem estar na posição correta da cabeça (mesma UV que o rosto)
- Fundo transparente
- Apenas as sobrancelhas devem ser visíveis

## Dicas de desenho:

### neutral.png
```
    ----        ----
  Relaxado e natural
```

### angry.png
```
  \    /
   ----  ----
  Franzido, raiva
```

### scared.png
```
   ----        ----
  ^             ^
  Levantado, medo
```

### sad.png
```
    /---    ---\
  Cantos internos subindo
```

### happy.png
```
   -----      -----
  Suave, ligeiramente levantado
```

### focused.png
```
  -----      -----
  Ligeiramente abaixado
```

## Sistema de renderização:

As texturas são renderizadas como **overlay** sobre a cabeça, igual ao FaceLayer.
- FaceLayer: Z offset 0.01F (olhos)
- ExpressionLayer: Z offset 0.02F (sobrancelhas, ligeiramente na frente)

Isso significa que as sobrancelhas aparecem na frente dos olhos, criando profundidade visual.
