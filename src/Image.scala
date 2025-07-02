package scalar

import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

/** An 8-bit grayscale image. */
final class Gray(val width: Int, val height: Int, val data: Array[Int]) {
  require(data.length == width * height)

  def apply(x: Int, y: Int): Int = data(y * width + x)
  def update(x: Int, y: Int, v: Int): Unit = data(y * width + x) = v

  /** Bilinear sample with clamping at the borders. */
  def sample(x: Double, y: Double): Double = {
    val cx = math.max(0.0, math.min(width - 1.001, x))
    val cy = math.max(0.0, math.min(height - 1.001, y))
    val x0 = cx.toInt
    val y0 = cy.toInt
    val fx = cx - x0
    val fy = cy - y0
    val a = apply(x0, y0)
    val b = apply(x0 + 1, y0)
    val c = apply(x0, y0 + 1)
    val d = apply(x0 + 1, y0 + 1)
    (a * (1 - fx) + b * fx) * (1 - fy) + (c * (1 - fx) + d * fx) * fy
  }

  /** Summed-area table with an extra zero row and column. */
  def integral: Array[Long] = {
    val w = width + 1
    val s = new Array[Long](w * (height + 1))
    var y = 1
    while (y <= height) {
      var row = 0L
      var x = 1
      while (x <= width) {
        row += apply(x - 1, y - 1)
        s(y * w + x) = s((y - 1) * w + x) + row
        x += 1
      }
      y += 1
    }
    s
  }

  /** Adaptive threshold: a pixel is "dark" when it is below the local mean minus `c`. */
  def adaptiveThreshold(window: Int = 0, c: Int = 7): Array[Boolean] = {
    val win = if (window > 0) window else math.max(15, math.min(width, height) / 12) | 1
    val r = win / 2
    val s = integral
    val w = width + 1
    val out = new Array[Boolean](width * height)
    var y = 0
    while (y < height) {
      val y0 = math.max(0, y - r)
      val y1 = math.min(height, y + r + 1)
      var x = 0
      while (x < width) {
        val x0 = math.max(0, x - r)
        val x1 = math.min(width, x + r + 1)
        val sum = s(y1 * w + x1) - s(y0 * w + x1) - s(y1 * w + x0) + s(y0 * w + x0)
        val mean = sum.toDouble / ((y1 - y0) * (x1 - x0))
        out(y * width + x) = apply(x, y) < mean - c
        x += 1
      }
      y += 1
    }
    out
  }

  def toRgb: Rgb = new Rgb(width, height, data.map(v => (v << 16) | (v << 8) | v))

  def save(path: String): Unit = toRgb.save(path)
}

/** A packed 0xRRGGBB colour image with simple drawing primitives. */
final class Rgb(val width: Int, val height: Int, val data: Array[Int]) {
  require(data.length == width * height)

  def apply(x: Int, y: Int): Int = data(y * width + x)
  def update(x: Int, y: Int, v: Int): Unit = data(y * width + x) = v
  def copy: Rgb = new Rgb(width, height, data.clone())

  def toGray: Gray = new Gray(width, height, data.map { p =>
    val r = (p >> 16) & 255
    val g = (p >> 8) & 255
    val b = p & 255
    (r * 299 + g * 587 + b * 114) / 1000
  })

  def blend(x: Int, y: Int, color: Int, alpha: Double): Unit = {
    if (x < 0 || y < 0 || x >= width || y >= height) return
    val p = apply(x, y)
    def mix(shift: Int) = {
      val a = (p >> shift) & 255
      val b = (color >> shift) & 255
      math.round(a * (1 - alpha) + b * alpha).toInt & 255
    }
    update(x, y, (mix(16) << 16) | (mix(8) << 8) | mix(0))
  }

  def drawLine(p: Vec2, q: Vec2, color: Int, thickness: Int = 1): Unit = {
    val steps = math.max(1, math.ceil(math.max(math.abs(q.x - p.x), math.abs(q.y - p.y))).toInt)
    val r = thickness / 2
    var i = 0
    while (i <= steps) {
      val t = i.toDouble / steps
      val x = math.round(p.x + (q.x - p.x) * t).toInt
      val y = math.round(p.y + (q.y - p.y) * t).toInt
      var dy = -r
      while (dy <= r) {
        var dx = -r
        while (dx <= r) {
          blend(x + dx, y + dy, color, 1.0)
          dx += 1
        }
        dy += 1
      }
      i += 1
    }
  }

  /** Scanline fill of a simple polygon with alpha blending. */
  def fillPolygon(pts: Seq[Vec2], color: Int, alpha: Double): Unit = {
    if (pts.length < 3) return
    val minY = math.max(0, math.floor(pts.map(_.y).min).toInt)
    val maxY = math.min(height - 1, math.ceil(pts.map(_.y).max).toInt)
    var y = minY
    while (y <= maxY) {
      val sy = y + 0.5
      val xs = scala.collection.mutable.ArrayBuffer[Double]()
      var i = 0
      while (i < pts.length) {
        val a = pts(i)
        val b = pts((i + 1) % pts.length)
        if ((a.y <= sy && b.y > sy) || (b.y <= sy && a.y > sy)) {
          xs += a.x + (sy - a.y) / (b.y - a.y) * (b.x - a.x)
        }
        i += 1
      }
      val sorted = xs.sorted
      var k = 0
      while (k + 1 < sorted.length) {
        var x = math.max(0, math.ceil(sorted(k) - 0.5).toInt)
        val xEnd = math.min(width - 1, math.floor(sorted(k + 1) - 0.5).toInt)
        while (x <= xEnd) {
          blend(x, y, color, alpha)
          x += 1
        }
        k += 2
      }
      y += 1
    }
  }

  def fillCircle(c: Vec2, radius: Double, color: Int): Unit = {
    var y = math.floor(c.y - radius).toInt
    while (y <= c.y + radius) {
      var x = math.floor(c.x - radius).toInt
      while (x <= c.x + radius) {
        if (math.hypot(x - c.x, y - c.y) <= radius) blend(x, y, color, 1.0)
        x += 1
      }
      y += 1
    }
  }

  def toBuffered: BufferedImage = {
    val img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
    img.setRGB(0, 0, width, height, data, 0, width)
    img
  }

  def save(path: String): Unit = {
    val format = if (path.toLowerCase.endsWith(".jpg") || path.toLowerCase.endsWith(".jpeg")) "jpg" else "png"
    ImageIO.write(toBuffered, format, new File(path))
  }
}

object Rgb {
  def apply(width: Int, height: Int, fill: Int = 0xffffff): Rgb = new Rgb(width, height, Array.fill(width * height)(fill))

  def load(path: String): Rgb = {
    val img = ImageIO.read(new File(path))
    require(img != null, s"cannot decode image $path")
    val data = new Array[Int](img.getWidth * img.getHeight)
    img.getRGB(0, 0, img.getWidth, img.getHeight, data, 0, img.getWidth)
    new Rgb(img.getWidth, img.getHeight, data.map(_ & 0xffffff))
  }
}
