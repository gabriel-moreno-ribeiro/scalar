package scalar

/** Draws virtual objects registered to a marker pose. */
object Render {
  private val faceColors = Array(0x4a90d9, 0x3a78c0, 0x5da8ff, 0x2f65a8, 0x78b8ff, 0x6fa3e0)

  /** A shaded cube standing on the marker, plus the marker's coordinate axes. */
  def cube(img: Rgb, cam: Camera, pose: Pose, size: Double = 0.7): Unit = {
    val s = size / 2
    val up = pose.up * size
    val base = Seq(Vec3(-s, -s, 0), Vec3(s, -s, 0), Vec3(s, s, 0), Vec3(-s, s, 0))
    val verts = base ++ base.map(_ + up)
    val camPts = verts.map(pose.transform)
    val pts = camPts.map(cam.project)
    val faces = Seq(Seq(0, 1, 2, 3), Seq(4, 5, 6, 7), Seq(0, 1, 5, 4), Seq(1, 2, 6, 5), Seq(2, 3, 7, 6), Seq(3, 0, 4, 7))
    // painter's algorithm: far faces first
    val ordered = faces.zipWithIndex.sortBy { case (f, _) => -f.map(camPts(_).z).sum }
    for ((face, idx) <- ordered) img.fillPolygon(face.map(pts), faceColors(idx), 0.75)
    val edges = Seq((0, 1), (1, 2), (2, 3), (3, 0), (4, 5), (5, 6), (6, 7), (7, 4), (0, 4), (1, 5), (2, 6), (3, 7))
    for ((a, b) <- edges) img.drawLine(pts(a), pts(b), 0x102040, 2)
    axes(img, cam, pose)
  }

  /** The marker frame: x red, y green, z (towards the camera) blue. */
  def axes(img: Rgb, cam: Camera, pose: Pose, length: Double = 0.5): Unit = {
    val origin = cam.project(pose.transform(Vec3(0, 0, 0)))
    img.drawLine(origin, cam.project(pose.transform(Vec3(length, 0, 0))), 0xe53935, 3)
    img.drawLine(origin, cam.project(pose.transform(Vec3(0, length, 0))), 0x43a047, 3)
    img.drawLine(origin, cam.project(pose.transform(pose.up * length)), 0x1e88e5, 3)
  }

  /** Outlines a detection and marks its first (top-left) corner. */
  def outline(img: Rgb, d: Detection, color: Int = 0x00e676): Unit = {
    for (i <- 0 until 4) img.drawLine(d.corners(i), d.corners((i + 1) % 4), color, 2)
    img.fillCircle(d.corners(0), 4, 0xff1744)
  }

  /** Draws the marker id near the detection as 5x7 pixel digits. */
  def label(img: Rgb, d: Detection, color: Int = 0xffffff, scale: Int = 2): Unit = {
    val text = d.id.toString
    val c = d.center
    val width = text.length * 6 * scale
    val x0 = (c.x - width / 2).toInt
    val y0 = (c.y - 3.5 * scale).toInt
    val box = Seq(Vec2(x0 - 3, y0 - 3), Vec2(x0 + width + 3, y0 - 3), Vec2(x0 + width + 3, y0 + 7 * scale + 3), Vec2(x0 - 3, y0 + 7 * scale + 3))
    img.fillPolygon(box, 0x000000, 0.6)
    for ((ch, k) <- text.zipWithIndex) Glyphs.draw(img, ch, x0 + k * 6 * scale, y0, scale, color)
  }
}

/** A tiny 5x7 bitmap font for digits. */
object Glyphs {
  private val digits = Array(
    "01110 10001 10011 10101 11001 10001 01110",
    "00100 01100 00100 00100 00100 00100 01110",
    "01110 10001 00001 00010 00100 01000 11111",
    "11111 00010 00100 00010 00001 10001 01110",
    "00010 00110 01010 10010 11111 00010 00010",
    "11111 10000 11110 00001 00001 10001 01110",
    "00110 01000 10000 11110 10001 10001 01110",
    "11111 00001 00010 00100 01000 01000 01000",
    "01110 10001 10001 01110 10001 10001 01110",
    "01110 10001 10001 01111 00001 00010 01100",
  )

  def draw(img: Rgb, ch: Char, x0: Int, y0: Int, scale: Int, color: Int): Unit = {
    if (!ch.isDigit) return
    val rows = digits(ch - '0').split(' ')
    for (r <- rows.indices; c <- 0 until 5 if rows(r)(c) == '1'; dy <- 0 until scale; dx <- 0 until scale)
      img.blend(x0 + c * scale + dx, y0 + r * scale + dy, color, 1.0)
  }
}
