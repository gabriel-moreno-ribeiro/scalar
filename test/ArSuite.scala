package scalar

class ArSuite extends munit.FunSuite {
  private val rad = math.Pi / 180

  private def assertNear(actual: Double, expected: Double, tol: Double, clue: String = ""): Unit =
    assert(math.abs(actual - expected) <= tol, s"$clue expected $expected +- $tol, got $actual")

  test("linear solver and 3x3 inverse") {
    val x = Linear.solve(Array(Array(2.0, 1.0, -1.0, 8.0), Array(-3.0, -1.0, 2.0, -11.0), Array(-2.0, 1.0, 2.0, -3.0)))
    assertNear(x(0), 2, 1e-9)
    assertNear(x(1), 3, 1e-9)
    assertNear(x(2), -1, 1e-9)
    val m = Mat3(2, 0, 1, 1, 3, 0, 0, 1, 4)
    val id = m * m.inverse
    for (r <- 0 until 3; c <- 0 until 3) assertNear(id(r, c), if (r == c) 1 else 0, 1e-9, s"($r,$c)")
    val rot = Mat3.rotZ(0.7) * Mat3.rotX(0.3)
    assertNear(rot.det, 1.0, 1e-9, "rotation determinant")
  }

  test("homography maps the four points and interpolates the rest") {
    val dst = IndexedSeq(Vec2(100, 120), Vec2(300, 90), Vec2(320, 310), Vec2(80, 280))
    val h = Homography.from4(Homography.unitSquare, dst)
    for ((s, d) <- Homography.unitSquare.zip(dst)) {
      val p = Homography(h, s)
      assertNear(p.x, d.x, 1e-6)
      assertNear(p.y, d.y, 1e-6)
    }
    // the centre of the square maps to the intersection of the diagonals
    val c = Homography(h, Vec2(0.5, 0.5))
    val back = Homography(h.inverse, c)
    assertNear(back.x, 0.5, 1e-6)
    assertNear(back.y, 0.5, 1e-6)
  }

  test("convex hull of a noisy square is its four corners") {
    val rng = new scala.util.Random(3)
    val inside = (1 to 200).map(_ => Vec2(rng.nextInt(99) + 1, rng.nextInt(99) + 1))
    val corners = IndexedSeq(Vec2(0, 0), Vec2(100, 0), Vec2(100, 100), Vec2(0, 100))
    val hull = ConvexHull(inside ++ corners)
    assertEquals(hull.toSet, corners.toSet)
    assertEquals(Detector.quadFromHull(hull).map(_.toSet), Some(corners.toSet))
    // a triangle is not a quad
    assertEquals(Detector.quadFromHull(IndexedSeq(Vec2(0, 0), Vec2(100, 0), Vec2(50, 80))), None)
  }

  test("marker patterns round-trip through decoding in every rotation") {
    for (id <- Seq(0, 1, 7, 1234, 2048, 4095)) {
      var g = Marker.pattern(id)
      for (k <- 0 until 4) {
        assertEquals(Marker.decode(g), Some((id, (4 - k) % 4)), s"id $id rotated $k")
        g = Marker.rotateCW(g)
      }
    }
    val distinct = (0 until Marker.Count).map(id => Marker.pattern(id).map(_.toSeq).toSeq).toSet
    assertEquals(distinct.size, Marker.Count, "all ids have distinct patterns")
  }

  test("decoding rejects broken borders and ambiguous orientation") {
    val broken = Marker.pattern(42)
    broken(0)(2) = false
    assertEquals(Marker.decode(broken), None)
    val ambiguous = Marker.pattern(42)
    ambiguous(4)(4) = false // a second white corner
    assertEquals(Marker.decode(ambiguous), None)
    val printable = Marker.image(5, 10)
    assertEquals(printable.width, 80)
    assertEquals(printable(15, 15), 0, "border cell is black")
    assertEquals(printable(5, 5), 255, "quiet zone is white")
  }

  test("adaptive threshold separates a marker from a strong illumination gradient") {
    val cam = Camera.guess(320, 240)
    val pose = Pose.fromAngles(-20 * rad, 0, 0, Vec3(0, 0, 3))
    val img = Synthetic.render(320, 240, cam, Seq(Synthetic.Placed(99, pose)), gradient = true)
    val dark = img.adaptiveThreshold()
    val centre = cam.project(pose.transform(Vec3(-0.5 + 0.5 / 6, -0.5 + 0.5 / 6, 0))) // centre of the top-left border cell
    assert(dark(centre.y.toInt * img.width + centre.x.toInt), "border cell is classified as dark")
    val right = dark(120 * img.width + 300)
    val left = dark(120 * img.width + 10)
    assert(!right && !left, "background is not dark on either side of the gradient")
  }

  private def checkDetection(id: Int, pose: Pose, cam: Camera, img: Gray, clue: String): Unit = {
    val found = Detector.detect(img)
    assertEquals(found.map(_.id), Seq(id), s"$clue: detections")
    val d = found.head
    val expected = Pose.markerCorners.map(p => cam.project(pose.transform(p)))
    for (i <- 0 until 4) assert(d.corners(i).dist(expected(i)) < 1.6, s"$clue: corner $i ${d.corners(i)} vs ${expected(i)}")
    val est = Pose.estimate(cam, d.corners)
    assertNear(est.t.x, pose.t.x, 0.03 * pose.t.z, s"$clue: tx")
    assertNear(est.t.y, pose.t.y, 0.03 * pose.t.z, s"$clue: ty")
    assertNear(est.t.z, pose.t.z, 0.04 * pose.t.z, s"$clue: tz")
    for (c <- 0 until 2) assert(est.r.col(c).dot(pose.r.col(c)) > 0.995, s"$clue: rotation column $c")
    assert(Pose.reprojectionError(cam, est, d.corners) < 1.5, s"$clue: reprojection")
  }

  test("markers are detected and their pose recovered under several views") {
    val cam = Camera.guess(640, 480)
    val views = Seq(
      (0, Pose.fromAngles(0, 0, 0, Vec3(0, 0, 3))),
      (1234, Pose.fromAngles(-40 * rad, 15 * rad, 25 * rad, Vec3(-0.6, 0.3, 3.5))),
      (4095, Pose.fromAngles(-55 * rad, -30 * rad, -100 * rad, Vec3(0.3, 0.2, 3.4))),
      (77, Pose.fromAngles(10 * rad, 45 * rad, 170 * rad, Vec3(0.3, 0.6, 4.5))),
    )
    for ((id, pose) <- views) {
      val img = Synthetic.render(640, 480, cam, Seq(Synthetic.Placed(id, pose)), seed = id)
      checkDetection(id, pose, cam, img, s"marker $id")
    }
  }

  test("several markers in one image, with a gradient background") {
    val (cam, placed) = Main.demoScene()
    val img = Synthetic.render(640, 480, cam, placed, gradient = true)
    val found = Detector.detect(img)
    assertEquals(found.map(_.id).sorted, placed.map(_.id).sorted)
    for (p <- placed) {
      val d = found.find(_.id == p.id).get
      val expected = Pose.markerCorners.map(c => cam.project(p.pose.transform(c)))
      for (i <- 0 until 4) assert(d.corners(i).dist(expected(i)) < 2.0, s"marker ${p.id} corner $i")
    }
  }

  test("an image without markers yields nothing, and mirrored markers are rejected") {
    val rng = new scala.util.Random(9)
    val noise = new Gray(200, 200, Array.fill(40000)(rng.nextInt(256)))
    assertEquals(Detector.detect(noise), Nil)
    val cam = Camera.guess(320, 240)
    val img = Synthetic.render(320, 240, cam, Seq(Synthetic.Placed(300, Pose.fromAngles(-15 * rad, 0, 0, Vec3(0, 0, 3)))))
    val mirrored = new Gray(img.width, img.height, Array.tabulate(img.width * img.height)(i => img(img.width - 1 - i % img.width, i / img.width)))
    // a mirrored pattern still decodes to some id (the geometry is unchanged) but never to the original one
    assert(!Detector.detect(mirrored).exists(_.id == 300))
  }

  test("cube rendering draws on top of the marker") {
    val cam = Camera.guess(320, 240)
    val pose = Pose.fromAngles(-30 * rad, 10 * rad, 0, Vec3(0, 0, 3))
    val gray = Synthetic.render(320, 240, cam, Seq(Synthetic.Placed(8, pose)), noise = 0)
    val img = gray.toRgb
    val before = img.copy
    val found = Main.augment(img)
    assertEquals(found.map(_.id), Seq(8))
    val changed = (0 until img.width * img.height).count(i => img.data(i) != before.data(i))
    assert(changed > 1500, s"cube and axes should change many pixels, changed $changed")
    // the cube stands towards the camera: its top face centre is closer than the marker centre
    assert(pose.transform(pose.up * 0.7).z < pose.t.z)
    val top = cam.project(pose.transform(pose.up * 0.7))
    assert(img(top.x.toInt, top.y.toInt) != before(top.x.toInt, top.y.toInt), "top face is painted")
  }

  test("command line demo writes a PNG that decodes back") {
    val out = java.io.File.createTempFile("scalar-demo", ".png")
    Main.main(Array("demo", out.getPath))
    val img = Rgb.load(out.getPath)
    assertEquals((img.width, img.height), (640, 480))
    val marker = java.io.File.createTempFile("scalar-marker", ".png")
    Main.main(Array("marker", "321", marker.getPath, "12"))
    val decoded = Detector.detect(Rgb.load(marker.getPath).toGray)
    assertEquals(decoded.map(_.id), Seq(321), "a printed marker is detected in its own image")
    out.delete()
    marker.delete()
  }
}
