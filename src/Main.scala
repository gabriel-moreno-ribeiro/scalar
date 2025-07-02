package scalar

object Main {
  private def usage(): Unit = {
    System.err.println(
      """usage:
        |  scalar detect <image> <out.png>            find markers, print ids and poses, draw cubes
        |  scalar marker <id> <out.png> [cellPixels]   render a printable marker
        |  scalar synth <id> <out.png> [rx ry rz]      render a synthetic camera view (angles in degrees)
        |  scalar demo <out.png>                       synthetic scene with three markers, detected and augmented""".stripMargin,
    )
  }

  /** Runs detection and augmentation on a colour image; returns the detections. */
  def augment(img: Rgb): Seq[Detection] = {
    val cam = Camera.guess(img.width, img.height)
    val detections = Detector.detect(img.toGray)
    for (d <- detections) {
      val pose = Pose.estimate(cam, d.corners)
      Render.outline(img, d)
      Render.cube(img, cam, pose)
      Render.label(img, d)
    }
    detections
  }

  def report(img: Rgb, detections: Seq[Detection]): Unit = {
    val cam = Camera.guess(img.width, img.height)
    if (detections.isEmpty) println("no markers found")
    for (d <- detections) {
      val pose = Pose.estimate(cam, d.corners)
      val corners = d.corners.map(c => f"(${c.x}%.1f, ${c.y}%.1f)").mkString(" ")
      println(f"marker ${d.id}%4d  corners $corners  t = (${pose.t.x}%.2f, ${pose.t.y}%.2f, ${pose.t.z}%.2f)  reprojection ${Pose.reprojectionError(cam, pose, d.corners)}%.2f px")
    }
  }

  def demoScene(width: Int = 640, height: Int = 480): (Camera, Seq[Synthetic.Placed]) = {
    val cam = Camera.guess(width, height)
    val rad = math.Pi / 180
    val placed = Seq(
      Synthetic.Placed(7, Pose.fromAngles(-35 * rad, 10 * rad, 15 * rad, Vec3(-0.75, 0.25, 3.8))),
      Synthetic.Placed(1234, Pose.fromAngles(-50 * rad, -20 * rad, -30 * rad, Vec3(0.55, 0.35, 3.4))),
      Synthetic.Placed(4095, Pose.fromAngles(-20 * rad, 25 * rad, 80 * rad, Vec3(0.1, -0.9, 4.2))),
    )
    (cam, placed)
  }

  def main(args: Array[String]): Unit = {
    args.toList match {
      case "detect" :: in :: out :: Nil =>
        val img = Rgb.load(in)
        val found = augment(img)
        report(img, found)
        img.save(out)
        println(s"wrote $out")
      case "marker" :: id :: out :: rest =>
        val cell = rest.headOption.map(_.toInt).getOrElse(40)
        Marker.image(id.toInt, cell).save(out)
        println(s"wrote marker ${id.toInt} to $out")
      case "synth" :: id :: out :: rest =>
        val angles = rest.map(_.toDouble * math.Pi / 180).padTo(3, 0.0)
        val cam = Camera.guess(640, 480)
        val pose = Pose.fromAngles(angles(0) - 0.5, angles(1), angles(2), Vec3(0, 0, 3.0))
        Synthetic.render(640, 480, cam, Seq(Synthetic.Placed(id.toInt, pose)), gradient = true).save(out)
        println(s"wrote synthetic view of marker ${id.toInt} to $out")
      case "demo" :: out :: Nil =>
        val (cam, placed) = demoScene()
        val img = Synthetic.render(640, 480, cam, placed, gradient = true).toRgb
        val found = augment(img)
        report(img, found)
        img.save(out)
        println(s"wrote $out")
      case _ =>
        usage()
        sys.exit(2)
    }
  }
}
