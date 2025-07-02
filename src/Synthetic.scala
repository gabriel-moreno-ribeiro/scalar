package scalar

/** Renders synthetic camera views of markers so the pipeline can be tested without a camera. */
object Synthetic {
  final case class Placed(id: Int, pose: Pose)

  /**
   * Draws the markers into a grayscale image by inverse mapping every pixel through the
   * marker homographies, with 4x4 supersampling, an illumination gradient and noise.
   */
  def render(width: Int, height: Int, cam: Camera, markers: Seq[Placed], seed: Long = 1, gradient: Boolean = false, noise: Int = 6): Gray = {
    val rng = new scala.util.Random(seed)
    val n = Marker.Size
    val prepared = markers.map { m =>
      val corners = Pose.markerCorners.map(p => cam.project(m.pose.transform(p)))
      val h = Homography.from4(Pose.markerCorners.map(p => Vec2(p.x, p.y)), corners)
      (Marker.pattern(m.id), h.inverse)
    }
    val out = new Gray(width, height, new Array[Int](width * height))
    val samples = Seq(0.125, 0.375, 0.625, 0.875) // 4x4 supersampling
    var y = 0
    while (y < height) {
      var x = 0
      while (x < width) {
        var acc = 0.0
        for (sy <- samples; sx <- samples) {
          val px = x + sx
          val py = y + sy
          val shade = if (gradient) 0.6 + 0.4 * px / width else 1.0
          var cell = Double.NaN
          var quiet = false
          var i = 0
          while (i < prepared.length) {
            val (pattern, hinv) = prepared(i)
            val m = Homography(hinv, Vec2(px, py))
            if (math.abs(m.x) <= 0.5 && math.abs(m.y) <= 0.5) {
              val c = math.min(n - 1, ((m.x + 0.5) * n).toInt)
              val r = math.min(n - 1, ((m.y + 0.5) * n).toInt)
              cell = (if (pattern(r)(c)) 25.0 else 235.0) * shade
            } else if (math.abs(m.x) <= 0.75 && math.abs(m.y) <= 0.75) {
              quiet = true // white quiet zone around the marker
            }
            i += 1
          }
          // marker cells win over any other marker's quiet zone, which wins over the background
          acc += (if (!cell.isNaN) cell else if (quiet) 235.0 * shade else if (gradient) 60.0 + 160.0 * px / width else 150.0)
        }
        val v = acc / (samples.length * samples.length) + (if (noise > 0) rng.nextInt(2 * noise + 1) - noise else 0)
        out(x, y) = math.max(0, math.min(255, math.round(v).toInt))
        x += 1
      }
      y += 1
    }
    out
  }
}
