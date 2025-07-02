# scalar

Augmented reality from scratch in Scala: the program finds square fiducial
markers in an image, works out where the camera is relative to each one,
and draws a shaded 3D cube standing on the marker with the marker's
coordinate axes. Everything is implemented by hand: thresholding,
connected components, convex hulls, homographies, pose estimation, and a
tiny rasteriser. No dependencies beyond the JDK (munit for the tests).

```sh
scala-cli run --server=false . -- demo demo.png            # synthetic scene with three markers, detected and augmented
scala-cli run --server=false . -- marker 42 marker42.png   # printable marker with id 42
scala-cli run --server=false . -- detect photo.png out.png # detect markers in a photo, draw cubes, print poses
scala-cli run --server=false . -- synth 7 view.png 30 10 0 # synthetic camera view of marker 7
```

## Markers

A marker is a 6x6 grid of cells: a black border, four inner corner cells
that encode orientation (only the top-left one is white), and twelve cells
that carry a 12-bit id, so there are 4096 markers. Print one with the
`marker` command and leave a white margin around it.

## Pipeline

1. **Threshold** (`Image.scala`): an adaptive threshold using an integral
   image marks pixels darker than their neighbourhood, which copes with
   uneven lighting.
2. **Candidates** (`Detector.scala`): dark pixels are grouped into
   connected components by flood fill; the boundary pixels of each component
   go through Andrew's convex hull, and a hull is accepted as a
   quadrilateral when its two farthest points and the two points farthest
   from that diagonal explain every hull point.
3. **Decode**: a homography from the unit square to the quad lets the
   detector sample the 36 cell centres; the border must be black and
   exactly one corner cell white, which fixes the rotation and yields the
   id. The corners are reordered so the first one is the pattern's
   top-left.
4. **Pose** (`Pose.scala`): with a pinhole camera model (focal length
   guessed as the image width) the marker-to-image homography is decomposed
   into a rotation and translation (`H ~ K [r1 r2 t]`), orthonormalising
   the rotation. Reprojection error is reported per marker.
5. **Render** (`Render.scala`): a cube of the marker's size is projected
   with the painter's algorithm and drawn with scanline polygon fill and
   thick lines, along with the axes and the id.

`Synthetic.scala` renders camera views of markers at arbitrary poses by
inverse-mapping pixels through the homography (with supersampling, a
lighting gradient and noise). It doubles as the test oracle.

## Tests

`scala-cli test --server=false .` checks the linear algebra, homography round-trips,
convex hull and quad fitting, marker encoding/decoding in all rotations,
rejection of broken markers, the adaptive threshold under a strong
gradient, detection and pose recovery for several views (corners within
1.6 px, translation within a few percent), multiple markers in one scene,
false-positive resistance on noise and mirrored markers, the cube
rendering, and the command line.

## License

MIT
