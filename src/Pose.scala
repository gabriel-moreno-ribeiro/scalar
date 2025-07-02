package scalar

/** A pinhole camera with square pixels and no distortion. */
final case class Camera(f: Double, cx: Double, cy: Double) {
  val K: Mat3 = Mat3(f, 0, cx, 0, f, cy, 0, 0, 1)

  def project(p: Vec3): Vec2 = Vec2(f * p.x / p.z + cx, f * p.y / p.z + cy)
}

object Camera {
  /** A reasonable guess when the camera is unknown: focal length equal to the image width. */
  def guess(width: Int, height: Int): Camera = Camera(width.toDouble, width / 2.0, height / 2.0)
}

/** Rigid transform from marker coordinates (marker of side 1 centred at the origin, z = 0) to camera coordinates. */
final case class Pose(r: Mat3, t: Vec3) {
  def transform(p: Vec3): Vec3 = r * p + t

  /** The unit direction, in marker coordinates, that points toward the camera. */
  def up: Vec3 = if (transform(Vec3(0, 0, 1)).z < t.z) Vec3(0, 0, 1) else Vec3(0, 0, -1)
}

object Pose {
  /** Marker corners in marker coordinates: top-left first, clockwise as seen when the marker faces the camera. */
  val markerCorners: IndexedSeq[Vec3] = IndexedSeq(Vec3(-0.5, -0.5, 0), Vec3(0.5, -0.5, 0), Vec3(0.5, 0.5, 0), Vec3(-0.5, 0.5, 0))

  /** Builds a pose from rotation angles (radians) around x, y and z, and a translation. */
  def fromAngles(rx: Double, ry: Double, rz: Double, t: Vec3): Pose = Pose(Mat3.rotZ(rz) * Mat3.rotY(ry) * Mat3.rotX(rx), t)

  /** Recovers the marker pose from its four image corners (planar homography decomposition). */
  def estimate(cam: Camera, corners: IndexedSeq[Vec2]): Pose = {
    val h = Homography.from4(markerCorners.map(p => Vec2(p.x, p.y)), corners)
    val m = cam.K.inverse * h
    var m1 = m.col(0)
    var m2 = m.col(1)
    var m3 = m.col(2)
    if (m3.z < 0) {
      m1 = m1 * -1
      m2 = m2 * -1
      m3 = m3 * -1
    }
    val lambda = 2.0 / (m1.norm + m2.norm)
    val r1 = m1.normalized
    val r2raw = m2 * lambda
    val r2 = (r2raw - r1 * r1.dot(r2raw)).normalized
    val r3 = r1.cross(r2)
    Pose(Mat3.fromCols(r1, r2, r3), m3 * lambda)
  }

  /** Reprojection error of a pose against observed corners, in pixels. */
  def reprojectionError(cam: Camera, pose: Pose, corners: IndexedSeq[Vec2]): Double =
    markerCorners.zip(corners).map { case (p, c) => cam.project(pose.transform(p)).dist(c) }.max
}
