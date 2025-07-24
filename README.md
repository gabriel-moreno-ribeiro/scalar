# scalar

> 🇺🇸 [English version below](#english)

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

## English

Augmented reality from scratch, in Scala: the program finds square markers in an image, computes where the camera is relative to each one, and draws a shaded 3D cube on top of the marker with its axes. Thresholding, connected components, convex hull, homography, pose estimation and a tiny rasterizer, all made by hand. JDK only (munit for the tests).

I wanted to understand what an AR app does before asking ARKit for it. Answer: a lot of high-school geometry and a matrix decomposition that looks like witchcraft until you derive it.

```sh
scala-cli run --server=false . -- demo demo.png            # synthetic scene with three markers, detected and augmented
scala-cli run --server=false . -- marker 42 marker42.png   # printable marker with id 42
scala-cli run --server=false . -- detect photo.png out.png  # detects in a photo, draws the cubes, prints the poses
scala-cli run --server=false . -- synth 7 view.png 30 10 0 # synthetic view of marker 7
```

## Markers

A marker is a 6x6 grid: black border, four corner cells that encode orientation (only the top-left one is white) and twelve cells with a 12-bit id, so there are 4096 of them. Print one with the `marker` command and leave a white margin around it.

## Pipeline

1. **Threshold** (`Image.scala`): adaptive, with an integral image, so it works under uneven lighting.
2. **Candidates** (`Detector.scala`): dark pixels become components through flood fill; each component's border goes through Andrew's convex hull, and a hull becomes a quadrilateral when the two farthest points and the two farthest from that diagonal explain all the others. Corners are refined to sub-pixel by fitting a line to the pixels of each edge and intersecting neighbouring lines.
3. **Decoding**: a homography from the unit square to the quadrilateral samples the 36 cell centers; the border has to be black and exactly one corner white, which fixes the rotation and gives the id.
4. **Pose** (`Pose.scala`): with a pinhole camera model (focal length guessed as the image width), the marker→image homography is decomposed into rotation and translation (`H ~ K [r1 r2 t]`), with the rotation orthonormalized. The reprojection error is reported per marker.
5. **Render** (`Render.scala`): a cube the size of the marker projected with the painter's algorithm, polygons filled by scanline, the axes and the id.

`Synthetic.scala` renders camera views of markers in arbitrary poses by mapping every pixel back through the homography (with supersampling, a light gradient and noise). It's the test image generator, and it's why detection and pose can be tested without a camera.

Tests: `scala-cli test --server=false .` (linear algebra, homography round trip, hull and quad fitting, encoding/decoding in every rotation, rejection of broken markers, the threshold under a strong gradient, detection and pose in several views with corners within 1.6 px and translation within a few percent, several markers in one scene, resistance to false positives in noise and mirrored markers, the cube, and the command line).

MIT.
