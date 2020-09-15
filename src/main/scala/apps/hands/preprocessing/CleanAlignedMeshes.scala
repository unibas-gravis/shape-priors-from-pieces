package apps.hands.preprocessing

import java.io.File

import apps.scalismoExtension.LineMeshConverter
import scalismo.common.PointId
import scalismo.common.UnstructuredPointsDomain.Create.CreateUnstructuredPointsDomain3D
import scalismo.geometry._
import scalismo.io.{LandmarkIO, MeshIO}
import scalismo.mesh.{LineCell, LineList, LineMesh2D}
import scalismo.ui.api.ScalismoUI
import apps.hands.preprocessing.CreateHandReference.{getLmPoint, getPointsBetweenLMs}
import apps.util.myPaths

object CleanAlignedMeshes {

  def main(args: Array[String]): Unit = {
    scalismo.initialize()

    val target = "xray"
    val refMeshFile = new File(myPaths.datapath, s"aligned/mesh_init/${target}.vtk")
    val refMeshOutFile = new File(myPaths.datapath, s"aligned/mesh/${target}.vtk")

    val refLmFile = new File(myPaths.datapath, s"aligned/landmarks_init/${target}.json")
    val refLmFileOutput = new File(myPaths.datapath, s"aligned/landmarks/${target}.json")

    val refMesh = MeshIO.readLineMesh2D(refMeshFile).get
    val refLMs3D = LandmarkIO.readLandmarksJson[_3D](refLmFile).get
    val refLMs2D = refLMs3D.map(lm => Landmark[_2D](lm.id, Point2D(lm.point.x, lm.point.y), lm.description, lm.uncertainty))

    val middlePoint = getLmPoint[_2D](refLMs2D, "middle")
    val endPoint1 = getLmPoint[_2D](refLMs2D, "finger.ulnar.middle")
    val endPoint2 = getLmPoint[_2D](refLMs2D, "finger.radial.middle")

    val points = getPointsBetweenLMs(refMesh, middlePoint, Seq(endPoint1, endPoint2))
    val refCut2Dopen = LineMeshConverter.createPartialMesh(refMesh, points)

    val closedLineList = LineList(refCut2Dopen.topology.lines ++ IndexedSeq(LineCell(refCut2Dopen.pointSet.findClosestPoint(endPoint1).id, refCut2Dopen.pointSet.findClosestPoint(endPoint2).id)))
    val refCut2D = LineMesh2D(refCut2Dopen.pointSet, closedLineList)

    val m2Dboundary = refCut2D.pointSet.pointIds.toIndexedSeq.filter(
      id => refCut2D.topology.adjacentPointsForPoint(id).length < 2
    )

    val boundaryLMs = m2Dboundary.map { id =>
      val p2 = refCut2D.pointSet.point(id)
      Landmark(id = id.id.toString, point = Point3D(x = p2.x, y = p2.y, z = 0))
    }
    println(s"Number of boundary landmarks ${boundaryLMs.length}")

    // check lines

    val ref3D = LineMeshConverter.lineMesh2Dto3D(refCut2D)
    val ui = ScalismoUI()
    ui.show(ref3D, "ref")
//    ui.show(CreateUnstructuredPointsDomain3D.create(points3D), "cut")
    ui.show(boundaryLMs, "boundary points")

    MeshIO.writeLineMesh[_2D](refCut2D, refMeshOutFile)

    val filteredTargetLMs = refLMs3D.filter{
      f =>
        val p2D = Point2D(f.point.x, f.point.y)
        (refCut2D.pointSet.findClosestPoint(p2D).point-p2D).norm < 2.0
    }

    println(s"Number of initial landmarks ${refLMs3D.length}")
    println(s"Number of final landmarks ${filteredTargetLMs.length}")

    println(s"Number of points ${refCut2D.pointSet.numberOfPoints}")
    println(s"Number of normals ${refCut2D.vertexNormals.pointData.length}")

    LandmarkIO.writeLandmarksJson[_3D](filteredTargetLMs, refLmFileOutput)
    ui.show(filteredTargetLMs, "lms")
    println("DONE!!!")

  }
}