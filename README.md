# scalar

Realidade aumentada do zero, em Scala: o programa acha marcadores quadrados numa imagem, calcula onde a câmera está em relação a cada um, e desenha um cubo 3D sombreado em cima do marcador com os eixos dele. Threshold, componentes conexos, casco convexo, homografia, estimativa de pose e um rasterizador minúsculo, tudo feito à mão. Só o JDK (munit pros testes).

Eu queria entender o que um app de AR faz antes de pedir pra ARKit. Resposta: muita geometria de ensino médio e uma decomposição de matriz que parece bruxaria até você derivar.

```sh
scala-cli run --server=false . -- demo demo.png            # cena sintética com três marcadores, detectada e aumentada
scala-cli run --server=false . -- marker 42 marker42.png   # marcador imprimível com id 42
scala-cli run --server=false . -- detect foto.png saida.png # detecta numa foto, desenha os cubos, imprime as poses
scala-cli run --server=false . -- synth 7 view.png 30 10 0 # vista sintética do marcador 7
```

## Marcadores

Um marcador é uma grade 6x6: borda preta, quatro células de canto que codificam orientação (só a de cima à esquerda é branca) e doze células com um id de 12 bits, então existem 4096. Imprime com o comando `marker` e deixa uma margem branca em volta.

## Pipeline

1. **Threshold** (`Image.scala`): adaptativo, com imagem integral, então funciona com iluminação desigual.
2. **Candidatos** (`Detector.scala`): pixels escuros viram componentes por flood fill; a borda de cada componente passa pelo casco convexo de Andrew, e um casco vira quadrilátero quando os dois pontos mais distantes e os dois mais longe dessa diagonal explicam todos os outros. Os cantos são refinados em sub-pixel ajustando uma reta aos pixels de cada aresta e cruzando as retas vizinhas.
3. **Decodificação**: uma homografia do quadrado unitário pro quadrilátero amostra os 36 centros de célula; a borda tem que ser preta e exatamente um canto branco, o que fixa a rotação e dá o id.
4. **Pose** (`Pose.scala`): com um modelo de câmera pinhole (foco chutado como a largura da imagem), a homografia marcador→imagem é decomposta em rotação e translação (`H ~ K [r1 r2 t]`), com a rotação ortonormalizada. O erro de reprojeção é reportado por marcador.
5. **Render** (`Render.scala`): um cubo do tamanho do marcador projetado com o algoritmo do pintor, polígonos preenchidos por scanline, os eixos e o id.

`Synthetic.scala` renderiza vistas de câmera de marcadores em poses arbitrárias mapeando cada pixel de volta pela homografia (com supersampling, gradiente de luz e ruído). É o gerador de imagens de teste, e é por isso que dá pra testar detecção e pose sem câmera.

Testes: `scala-cli test --server=false .` (álgebra linear, homografia de ida e volta, casco e ajuste de quadrilátero, codificação/decodificação em todas as rotações, rejeição de marcadores quebrados, o threshold sob gradiente forte, detecção e pose em várias vistas com cantos a menos de 1.6 px e translação dentro de poucos por cento, vários marcadores numa cena, resistência a falso positivo em ruído e marcadores espelhados, o cubo, e a linha de comando).

---

**EN:** augmented reality from scratch in Scala: adaptive thresholding, connected components, convex hulls, quad fitting with sub-pixel corner refinement, homography-based marker decoding (4096 ids), pose estimation by homography decomposition, and a software rasterizer that draws a shaded cube on each marker. A synthetic renderer generates the test images, so detection and pose are verified without a camera. MIT.
