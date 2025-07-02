package scalar

final case class Vec2(x: Double, y: Double) {
  def +(o: Vec2): Vec2 = Vec2(x + o.x, y + o.y)
  def -(o: Vec2): Vec2 = Vec2(x - o.x, y - o.y)
  def *(s: Double): Vec2 = Vec2(x * s, y * s)
  def dist(o: Vec2): Double = math.hypot(x - o.x, y - o.y)
  def norm: Double = math.hypot(x, y)
}

final case class Vec3(x: Double, y: Double, z: Double) {
  def +(o: Vec3): Vec3 = Vec3(x + o.x, y + o.y, z + o.z)
  def -(o: Vec3): Vec3 = Vec3(x - o.x, y - o.y, z - o.z)
  def *(s: Double): Vec3 = Vec3(x * s, y * s, z * s)
  def dot(o: Vec3): Double = x * o.x + y * o.y + z * o.z
  def cross(o: Vec3): Vec3 = Vec3(y * o.z - z * o.y, z * o.x - x * o.z, x * o.y - y * o.x)
  def norm: Double = math.sqrt(dot(this))
  def normalized: Vec3 = this * (1.0 / norm)
}

/** A row-major 3x3 matrix. */
final class Mat3(val m: Array[Double]) {
  require(m.length == 9)
  def apply(r: Int, c: Int): Double = m(r * 3 + c)

  def *(o: Mat3): Mat3 = {
    val out = new Array[Double](9)
    var r = 0
    while (r < 3) {
      var c = 0
      while (c < 3) {
        out(r * 3 + c) = apply(r, 0) * o(0, c) + apply(r, 1) * o(1, c) + apply(r, 2) * o(2, c)
        c += 1
      }
      r += 1
    }
    new Mat3(out)
  }

  def *(v: Vec3): Vec3 = Vec3(
    apply(0, 0) * v.x + apply(0, 1) * v.y + apply(0, 2) * v.z,
    apply(1, 0) * v.x + apply(1, 1) * v.y + apply(1, 2) * v.z,
    apply(2, 0) * v.x + apply(2, 1) * v.y + apply(2, 2) * v.z,
  )

  def scale(s: Double): Mat3 = new Mat3(m.map(_ * s))
  def col(c: Int): Vec3 = Vec3(apply(0, c), apply(1, c), apply(2, c))
  def transpose: Mat3 = Mat3(apply(0, 0), apply(1, 0), apply(2, 0), apply(0, 1), apply(1, 1), apply(2, 1), apply(0, 2), apply(1, 2), apply(2, 2))

  def det: Double =
    apply(0, 0) * (apply(1, 1) * apply(2, 2) - apply(1, 2) * apply(2, 1)) -
      apply(0, 1) * (apply(1, 0) * apply(2, 2) - apply(1, 2) * apply(2, 0)) +
      apply(0, 2) * (apply(1, 0) * apply(2, 1) - apply(1, 1) * apply(2, 0))

  def inverse: Mat3 = {
    val d = det
    require(math.abs(d) > 1e-12, "singular matrix")
    val a = m
    Mat3(
      (a(4) * a(8) - a(5) * a(7)) / d, (a(2) * a(7) - a(1) * a(8)) / d, (a(1) * a(5) - a(2) * a(4)) / d,
      (a(5) * a(6) - a(3) * a(8)) / d, (a(0) * a(8) - a(2) * a(6)) / d, (a(2) * a(3) - a(0) * a(5)) / d,
      (a(3) * a(7) - a(4) * a(6)) / d, (a(1) * a(6) - a(0) * a(7)) / d, (a(0) * a(4) - a(1) * a(3)) / d,
    )
  }

  override def toString: String = m.grouped(3).map(_.map(v => f"$v%9.4f").mkString(" ")).mkString("\n")
}

object Mat3 {
  def apply(values: Double*): Mat3 = new Mat3(values.toArray)
  val identity: Mat3 = Mat3(1, 0, 0, 0, 1, 0, 0, 0, 1)
  def fromCols(a: Vec3, b: Vec3, c: Vec3): Mat3 = Mat3(a.x, b.x, c.x, a.y, b.y, c.y, a.z, b.z, c.z)
  def rotX(a: Double): Mat3 = Mat3(1, 0, 0, 0, math.cos(a), -math.sin(a), 0, math.sin(a), math.cos(a))
  def rotY(a: Double): Mat3 = Mat3(math.cos(a), 0, math.sin(a), 0, 1, 0, -math.sin(a), 0, math.cos(a))
  def rotZ(a: Double): Mat3 = Mat3(math.cos(a), -math.sin(a), 0, math.sin(a), math.cos(a), 0, 0, 0, 1)
}

object Linear {
  /** Solves a dense system given as an augmented matrix (n rows, n+1 columns) by Gaussian elimination. */
  def solve(a: Array[Array[Double]]): Array[Double] = {
    val n = a.length
    var col = 0
    while (col < n) {
      var pivot = col
      var r = col + 1
      while (r < n) {
        if (math.abs(a(r)(col)) > math.abs(a(pivot)(col))) pivot = r
        r += 1
      }
      require(math.abs(a(pivot)(col)) > 1e-12, "singular system")
      val tmp = a(col); a(col) = a(pivot); a(pivot) = tmp
      r = col + 1
      while (r < n) {
        val f = a(r)(col) / a(col)(col)
        var c = col
        while (c <= n) {
          a(r)(c) -= f * a(col)(c)
          c += 1
        }
        r += 1
      }
      col += 1
    }
    val x = new Array[Double](n)
    var r = n - 1
    while (r >= 0) {
      var s = a(r)(n)
      var c = r + 1
      while (c < n) {
        s -= a(r)(c) * x(c)
        c += 1
      }
      x(r) = s / a(r)(r)
      r -= 1
    }
    x
  }
}

object Homography {
  /** The homography mapping four source points onto four destination points (normalised so H(2,2) = 1). */
  def from4(src: Seq[Vec2], dst: Seq[Vec2]): Mat3 = {
    require(src.length == 4 && dst.length == 4)
    val a = Array.ofDim[Double](8, 9)
    var i = 0
    while (i < 4) {
      val (x, y) = (src(i).x, src(i).y)
      val (u, v) = (dst(i).x, dst(i).y)
      a(2 * i) = Array(x, y, 1, 0, 0, 0, -u * x, -u * y, u)
      a(2 * i + 1) = Array(0, 0, 0, x, y, 1, -v * x, -v * y, v)
      i += 1
    }
    val h = Linear.solve(a)
    Mat3(h(0), h(1), h(2), h(3), h(4), h(5), h(6), h(7), 1.0)
  }

  def apply(h: Mat3, p: Vec2): Vec2 = {
    val w = h(2, 0) * p.x + h(2, 1) * p.y + h(2, 2)
    Vec2((h(0, 0) * p.x + h(0, 1) * p.y + h(0, 2)) / w, (h(1, 0) * p.x + h(1, 1) * p.y + h(1, 2)) / w)
  }

  val unitSquare: IndexedSeq[Vec2] = IndexedSeq(Vec2(0, 0), Vec2(1, 0), Vec2(1, 1), Vec2(0, 1))
}

object ConvexHull {
  /** Andrew's monotone chain; returns the hull counter-clockwise in a y-up frame (clockwise on screen). */
  def apply(points: IndexedSeq[Vec2]): IndexedSeq[Vec2] = {
    val pts = points.distinct.sortBy(p => (p.x, p.y))
    if (pts.length < 3) return pts
    def cross(o: Vec2, a: Vec2, b: Vec2) = (a.x - o.x) * (b.y - o.y) - (a.y - o.y) * (b.x - o.x)
    val lower = scala.collection.mutable.ArrayBuffer[Vec2]()
    for (p <- pts) {
      while (lower.length >= 2 && cross(lower(lower.length - 2), lower.last, p) <= 0) lower.remove(lower.length - 1)
      lower += p
    }
    val upper = scala.collection.mutable.ArrayBuffer[Vec2]()
    for (p <- pts.reverse) {
      while (upper.length >= 2 && cross(upper(upper.length - 2), upper.last, p) <= 0) upper.remove(upper.length - 1)
      upper += p
    }
    (lower.dropRight(1) ++ upper.dropRight(1)).toIndexedSeq
  }
}
