package scalar

import scala.collection.mutable.ArrayBuffer

/** A marker found in an image. Corners are in the pattern's own order: top-left first, clockwise. */
final case class Detection(id: Int, corners: IndexedSeq[Vec2], homography: Mat3) {
  def center: Vec2 = Vec2(corners.map(_.x).sum / 4, corners.map(_.y).sum / 4)
}

object Detector {
  /** Finds every valid marker in a grayscale image. */
  def detect(img: Gray, minArea: Int = 80): Seq[Detection] = {
    val w = img.width
    val h = img.height
    val dark = img.adaptiveThreshold()
    val visited = new Array[Boolean](w * h)
    val queue = new Array[Int](w * h)
    val results = ArrayBuffer[Detection]()

    var start = 0
    while (start < w * h) {
      if (dark(start) && !visited(start)) {
        // flood fill one dark component, keeping its boundary pixels
        var head = 0
        var tail = 0
        queue(tail) = start
        tail += 1
        visited(start) = true
        val boundary = ArrayBuffer[Vec2]()
        var area = 0
        var minX = w
        var maxX = 0
        var minY = h
        var maxY = 0
        while (head < tail) {
          val p = queue(head)
          head += 1
          val x = p % w
          val y = p / w
          area += 1
          if (x < minX) minX = x
          if (x > maxX) maxX = x
          if (y < minY) minY = y
          if (y > maxY) maxY = y
          var isBoundary = x == 0 || y == 0 || x == w - 1 || y == h - 1
          def visit(nx: Int, ny: Int): Unit = {
            val q = ny * w + nx
            if (dark(q)) {
              if (!visited(q)) {
                visited(q) = true
                queue(tail) = q
                tail += 1
              }
            } else isBoundary = true
          }
          if (x > 0) visit(x - 1, y)
          if (x < w - 1) visit(x + 1, y)
          if (y > 0) visit(x, y - 1)
          if (y < h - 1) visit(x, y + 1)
          if (isBoundary) boundary += Vec2(x, y)
        }
        if (area >= minArea && maxX - minX >= 8 && maxY - minY >= 8 && minX > 0 && minY > 0 && maxX < w - 1 && maxY < h - 1) {
          quadFromHull(ConvexHull(boundary.toIndexedSeq), boundary.toIndexedSeq).foreach { quad =>
            read(img, quad).foreach(results += _)
          }
        }
      }
      start += 1
    }
    results.toSeq
  }

  /** Approximates a convex hull by a quadrilateral, if it really looks like one. */
  def quadFromHull(hull: IndexedSeq[Vec2], boundary: IndexedSeq[Vec2] = null): Option[IndexedSeq[Vec2]] = {
    if (hull.length < 4) return None
    // the two farthest points form a diagonal
    var best = 0.0
    var a = 0
    var b = 0
    var i = 0
    while (i < hull.length) {
      var j = i + 1
      while (j < hull.length) {
        val d = hull(i).dist(hull(j))
        if (d > best) {
          best = d
          a = i
          b = j
        }
        j += 1
      }
      i += 1
    }
    val p0 = hull(a)
    val p1 = hull(b)
    def signedDist(p: Vec2) = ((p1.x - p0.x) * (p.y - p0.y) - (p1.y - p0.y) * (p.x - p0.x)) / best
    // the other two corners are the farthest points on each side of the diagonal
    val left = hull.maxBy(signedDist)
    val right = hull.minBy(signedDist)
    if (signedDist(left) < 2.0 || signedDist(right) > -2.0) return None
    val quad = orderClockwise(IndexedSeq(p0, left, p1, right))
    if (quad.indices.exists(k => quad(k).dist(quad((k + 1) % 4)) < 6)) return None
    // every hull point must lie close to one of the four edges
    val perimeter = quad.indices.map(k => quad(k).dist(quad((k + 1) % 4))).sum
    val tolerance = math.max(2.5, perimeter * 0.03)
    val fits = hull.forall { p => quad.indices.map(k => segmentDistance(p, quad(k), quad((k + 1) % 4))).min <= tolerance }
    if (fits) Some(refineCorners(if (boundary == null) hull else boundary, quad)) else None
  }

  /** Sub-pixel corners: fit a line through the boundary pixels of each edge and intersect adjacent lines. */
  def refineCorners(boundary: IndexedSeq[Vec2], quad: IndexedSeq[Vec2]): IndexedSeq[Vec2] = {
    val cx = quad.map(_.x).sum / 4
    val cy = quad.map(_.y).sum / 4
    val perimeter = quad.indices.map(k => quad(k).dist(quad((k + 1) % 4))).sum
    val near = math.max(1.5, perimeter * 0.01)
    val cornerZone = math.max(3.0, perimeter * 0.02) // pixels near a corner are chamfered and skipped
    // each line as (point, direction), shifted half a pixel outwards because boundary points are centres of dark pixels
    val lines = quad.indices.map { k =>
      val a = quad(k)
      val b = quad((k + 1) % 4)
      val pts = boundary.filter(p => segmentDistance(p, a, b) <= near && p.dist(a) > cornerZone && p.dist(b) > cornerZone)
      if (pts.length < 3) (a, (b - a) * (1.0 / a.dist(b)))
      else {
        val mx = pts.map(_.x).sum / pts.length
        val my = pts.map(_.y).sum / pts.length
        var sxx = 0.0; var syy = 0.0; var sxy = 0.0
        for (p <- pts) {
          sxx += (p.x - mx) * (p.x - mx); syy += (p.y - my) * (p.y - my); sxy += (p.x - mx) * (p.y - my)
        }
        val theta = 0.5 * math.atan2(2 * sxy, sxx - syy)
        val d = Vec2(math.cos(theta), math.sin(theta))
        var n = Vec2(-d.y, d.x)
        if (n.x * (mx - cx) + n.y * (my - cy) < 0) n = n * -1
        (Vec2(mx, my) + n * 0.5, d)
      }
    }
    def intersect(l1: (Vec2, Vec2), l2: (Vec2, Vec2), fallback: Vec2): Vec2 = {
      val (p1, d1) = l1
      val (p2, d2) = l2
      val cross = d1.x * d2.y - d1.y * d2.x
      if (math.abs(cross) < 1e-9) fallback
      else {
        val t = ((p2.x - p1.x) * d2.y - (p2.y - p1.y) * d2.x) / cross
        p1 + d1 * t
      }
    }
    quad.indices.map(k => intersect(lines((k + 3) % 4), lines(k), quad(k)))
  }

  private def segmentDistance(p: Vec2, a: Vec2, b: Vec2): Double = {
    val ab = b - a
    val len2 = ab.x * ab.x + ab.y * ab.y
    val t = if (len2 == 0) 0.0 else math.max(0.0, math.min(1.0, ((p - a).x * ab.x + (p - a).y * ab.y) / len2))
    p.dist(a + ab * t)
  }

  /** Orders four points clockwise on screen (y grows downwards). */
  def orderClockwise(pts: IndexedSeq[Vec2]): IndexedSeq[Vec2] = {
    val cx = pts.map(_.x).sum / pts.length
    val cy = pts.map(_.y).sum / pts.length
    val sorted = pts.sortBy(p => math.atan2(p.y - cy, p.x - cx))
    // start from the point closest to the top-left so the order is stable
    val startIdx = sorted.indices.minBy(i => sorted(i).x + sorted(i).y)
    (0 until 4).map(i => sorted((startIdx + i) % 4))
  }

  /** Samples the cell grid inside a quad and decodes it. */
  def read(img: Gray, quad: IndexedSeq[Vec2]): Option[Detection] = {
    val h = Homography.from4(Homography.unitSquare, quad)
    val n = Marker.Size
    val values = Array.ofDim[Double](n, n)
    val offsets = Seq((0.0, 0.0), (0.2, 0.0), (-0.2, 0.0), (0.0, 0.2), (0.0, -0.2))
    for (r <- 0 until n; c <- 0 until n) {
      values(r)(c) = offsets.map { case (dx, dy) =>
        val p = Homography(h, Vec2((c + 0.5 + dx) / n, (r + 0.5 + dy) / n))
        img.sample(p.x, p.y)
      }.sum / offsets.length
    }
    val flat = values.flatten
    val lo = flat.min
    val hi = flat.max
    if (hi - lo < 40) return None
    val threshold = (lo + hi) / 2
    val grid = values.map(_.map(_ < threshold))
    Marker.decode(grid).map { case (id, k) =>
      // after k clockwise rotations of the grid, the pattern's top-left corner is quad((3k + i) mod 4)
      val canonical = (0 until 4).map(i => quad((3 * k + i) % 4))
      Detection(id, canonical, Homography.from4(Homography.unitSquare, canonical))
    }
  }
}
