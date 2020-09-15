package apps.scalismoExtension

import java.io.File

import apps.hands.preprocessing.Create2dGPmodel
import apps.util.myPaths
import scalismo.io.{MeshIO, StatisticalLineModelIO}
import scalismo.utils.Random

object LineModelSaveLoadTest extends App {
  implicit val Random: Random = scalismo.utils.Random(1024)

  println("starting test...")
  scalismo.initialize()

  val mesh2d = new File(myPaths.datapath, "registered/mesh/mean.vtk")
  val ref = MeshIO.readLineMesh2D(mesh2d).get
  val model = Create2dGPmodel.createModel(ref, 200, 100)

  val modelFile = new File("/tmp/test.h5")
  StatisticalLineModelIO.writeStatisticalLineMeshModel(model, modelFile)
  val readModel = StatisticalLineModelIO.readStatisticalLineMeshModel(modelFile)
}
