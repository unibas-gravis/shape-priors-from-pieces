package apps.util

import java.io.File

import apps.hands.VisualizeData
import apps.scalismoExtension.LineMeshConverter
import scalismo.common.PointId
import scalismo.geometry.{Landmark, Point, _3D}
import scalismo.io.{MeshIO, StatisticalLineModelIO}
import scalismo.mesh.TriangleMesh3D
import scalismo.ui.api.ScalismoUI

object test extends App{
  scalismo.initialize()

  val ui = ScalismoUI()

//  val alignedPath = new File(myPaths.datapath, "aligned/mesh").listFiles(f => f.getName.contains("test-0-"))
  val registeredPath = new File(myPaths.datapath, "aligned/mesh").listFiles(f => f.getName.contains("test-0-"))
//  val alignedGroup = ui.createGroup("aligned")
  val registeredGroup = ui.createGroup("registered")
//  alignedPath.foreach{f =>
//    val m = LineMeshConverter.lineMesh2Dto3D(MeshIO.readLineMesh2D(f).get)
//    ui.show(alignedGroup, m, f.getName)
//  }
  registeredPath.foreach{f =>
    val m = LineMeshConverter.lineMesh2Dto3D(MeshIO.readLineMesh2D(f).get)
    ui.show(registeredGroup, m, f.getName)
  }
//  val model = StatisticalLineModelIO.readStatisticalLineMeshModel(new File("/export/skulls/projects/lousy-hands/data/hands/experiments/shapemi_scale_06_25/pca/sample/pca_full_20.h5")).get
//  val modelGroup = ui.createGroup("model")
//  VisualizeData.visualizePCsamples(model, ui, modelGroup)

//  val lala: TriangleMesh3D = ???
//  val lm: Landmark[_3D] = ???
//  val cutIds: Seq[PointId] = lala.pointSet.findNClosestPoints(lm.point, 100).map(_.id)
//  val mask = lala.operations.maskPoints(id => cutIds.contains(id))
//  mask.
//  val clippedMesh = mask.transformedMesh

}
