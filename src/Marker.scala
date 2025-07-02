package scalar

/**
 * Square fiducial markers: a black border around a 4x4 grid. The four inner
 * corner cells encode orientation (only the top-left one is white) and the
 * remaining twelve cells carry a 12-bit id, so 4096 distinct markers exist.
 */
object Marker {
  val Size = 6
  val Bits = 12
  val Count: Int = 1 << Bits

  private def isCorner(r: Int, c: Int) = (r == 1 || r == 4) && (c == 1 || c == 4)

  /** The cell grid of a marker; `true` means black. */
  def pattern(id: Int): Array[Array[Boolean]] = {
    require(id >= 0 && id < Count, s"marker ids go from 0 to ${Count - 1}")
    val g = Array.fill(Size, Size)(true)
    var bit = Bits - 1
    var r = 1
    while (r <= 4) {
      var c = 1
      while (c <= 4) {
        if (isCorner(r, c)) g(r)(c) = !(r == 1 && c == 1)
        else {
          g(r)(c) = ((id >> bit) & 1) == 1
          bit -= 1
        }
        c += 1
      }
      r += 1
    }
    g
  }

  def rotateCW(g: Array[Array[Boolean]]): Array[Array[Boolean]] = {
    val n = g.length
    Array.tabulate(n, n)((i, j) => g(n - 1 - j)(i))
  }

  /** Decodes a sampled grid, returning the id and how many clockwise rotations were needed. */
  def decode(sampled: Array[Array[Boolean]]): Option[(Int, Int)] = {
    var i = 0
    while (i < Size) {
      if (!sampled(0)(i) || !sampled(Size - 1)(i) || !sampled(i)(0) || !sampled(i)(Size - 1)) return None
      i += 1
    }
    var grid = sampled
    var k = 0
    var result: Option[(Int, Int)] = None
    while (k < 4 && result.isEmpty) {
      val whiteCorners = Seq(grid(1)(1), grid(1)(4), grid(4)(1), grid(4)(4)).count(!_)
      if (whiteCorners == 1 && !grid(1)(1)) {
        var id = 0
        var r = 1
        while (r <= 4) {
          var c = 1
          while (c <= 4) {
            if (!isCorner(r, c)) id = (id << 1) | (if (grid(r)(c)) 1 else 0)
            c += 1
          }
          r += 1
        }
        result = Some((id, k))
      } else {
        grid = rotateCW(grid)
        k += 1
      }
    }
    result
  }

  /** A printable image of the marker with a white quiet zone around it. */
  def image(id: Int, cellPixels: Int = 40, margin: Int = 1): Gray = {
    val cells = Size + 2 * margin
    val px = cells * cellPixels
    val out = new Gray(px, px, Array.fill(px * px)(255))
    val g = pattern(id)
    for (r <- 0 until Size; c <- 0 until Size if g(r)(c)) {
      for (y <- 0 until cellPixels; x <- 0 until cellPixels) out((c + margin) * cellPixels + x, (r + margin) * cellPixels + y) = 0
    }
    out
  }
}
