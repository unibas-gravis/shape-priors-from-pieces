package apps.hands.preprocessing

import java.io.File

import apps.scalismoExtension.LineMeshConverter
import apps.util.myPaths
import scalismo.geometry.{Landmark, Point2D, _2D, _3D}
import scalismo.io.{LandmarkIO, MeshIO}
import scalismo.mesh.LineMesh

object PartialDataFromLMs {

  private val referenceFile = new File(myPaths.datapath, "registered/mesh/mean.vtk")
  private val ref = MeshIO.readLineMesh2D(referenceFile).get
  private val referenceLmFile = new File(myPaths.datapath, "mean.json")
  private val lm3D = LandmarkIO
    .readLandmarksJson[_3D](referenceLmFile)
    .get
    .filter(_.id.startsWith("cut"))
  private val lm2D = lm3D.map(
    lm =>
      Landmark[_2D](
        id = lm.id,
        point = Point2D(lm.point.x, lm.point.y),
        description = lm.description,
        uncertainty = lm.uncertainty
    )
  )
  private val lmIds = lm2D.map(id => ref.pointSet.findClosestPoint(id.point).id)

  // target file, ground-truth 2D, partial 2D, partial 3D
  def getPartialData(
    index: Int,
    partial: Int
  ): (File, LineMesh[_2D], LineMesh[_2D], LineMesh[_3D]) = {
    val dataPath = new File(myPaths.datapath, "registered/mesh")
    val dataFiles = dataPath
      .listFiles(
        f => f.getName.endsWith(".vtk") && f.getName.startsWith("hand")
      )
      .sorted
    val targetFile = dataFiles(index)

    val targetLinemesh2DInit: LineMesh[_2D] =
      MeshIO.readLineMesh2D(targetFile).get

    val targetLinemesh2D: LineMesh[_2D] = LineMeshConverter.createPartialMesh(
      targetLinemesh2DInit,
      lmIds(partial % lmIds.length),
      500
    )
    val targetLineMesh3D = LineMeshConverter.lineMesh2Dto3D(targetLinemesh2D)
    (targetFile, targetLinemesh2DInit, targetLinemesh2D, targetLineMesh3D)
  }
}
