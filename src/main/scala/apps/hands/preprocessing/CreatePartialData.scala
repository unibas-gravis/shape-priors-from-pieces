package apps.hands.preprocessing

import java.io.File

import apps.hands.preprocessing.CreateHandReference.{getLmPoint, getPointsAroundLM}
import apps.scalismoExtension.LineMeshConverter
import apps.util.myPaths
import scalismo.common.{DiscreteField, UnstructuredPointsDomain}
import scalismo.geometry._
import scalismo.io.{LandmarkIO, MeshIO}
import scalismo.mesh.{LineCell, LineList, LineMesh2D}
import scalismo.ui.api.ScalismoUIHeadless

object CreatePartialData {

  def main(args: Array[String]) {
    scalismo.initialize()

    val alignedFiles = new File(myPaths.datapath, "aligned/mesh").listFiles(_.getName.endsWith(".vtk"))
    val alignedLMFiles = new File(myPaths.datapath, "aligned/landmarks").listFiles(_.getName.endsWith(".json"))

    val targetNameSeq = Seq("test-0-normal", "test-1-normal", "test-11-normal") //, "test-0-smaller", "test-0-smallest", "test-0-bigger", "test-0-biggest")
    //    val targetNameSeq = Seq("test-11", "test-11-smaller", "test-11-smallest", "test-11-bigger", "test-11-biggest")
    //    val targetNameSeq = Seq("hand-0", "hand-1", "hand-2", "hand-3", "hand-5", "hand-6", "hand-7", "hand-8", "hand-10", "hand-11", "hand-12", "hand-16", "mean", "mean-bigger", "xray")
    targetNameSeq.foreach { targetname =>
      //    val targetname = "hand-0"

      val targetFile = alignedFiles.find(f => f.getName == targetname + ".vtk").get
      val targetLMFile = alignedLMFiles.find(f => f.getName == targetname + ".json").get

      println(targetFile)
      println(targetLMFile)

      val targetFull = MeshIO.readLineMesh2D(targetFile).get
      val targetLM3D = LandmarkIO.readLandmarksJson[_3D](targetLMFile).get
      val targetLM2D = targetLM3D.map(lm => Landmark[_2D](lm.id, Point2D(lm.point.x, lm.point.y), lm.description, lm.uncertainty))

      val fingerSeq = Seq("thumb", "index", "long", "ring", "small")
      fingerSeq.foreach { finger =>

        val middlePointId = s"finger.${finger}.tip"
        val middlePoint = getLmPoint[_2D](targetLM2D, middlePointId)


        val percentageCutSeq = Seq(5, 10, 15, 20, 30, 40, 50, 60, 70, 80, 90)
        percentageCutSeq.foreach { percentageCut =>

          val numberOfPointsToCut: Int = targetFull.pointSet.numberOfPoints * percentageCut / 100
          val points = getPointsAroundLM(targetFull, middlePoint, numberOfPointsToCut)
          val refCut2Dopen = LineMeshConverter.createPartialMesh(targetFull, points)

          val m2Dboundary = refCut2Dopen.pointSet.pointIds.toIndexedSeq.filter(id => refCut2Dopen.topology.adjacentPointsForPoint(id).length < 2)
          val closedLineList2 = LineList(refCut2Dopen.topology.lines ++ IndexedSeq(LineCell(m2Dboundary.last, m2Dboundary.head)))
          val refCut2D = LineMesh2D(refCut2Dopen.pointSet, closedLineList2)

          println(s"Number of boundary landmarks ${m2Dboundary.length}")

          val ref3D = LineMeshConverter.lineMesh2Dto3D(refCut2D)
          val points3D = points.map(p => Point3D(p.x, p.y, 0.0))

          val normalVectorsModel: IndexedSeq[EuclideanVector[_3D]] =
            refCut2D.pointSet.pointIds.toIndexedSeq.map { id =>
              val n = refCut2D.vertexNormals(id)
              EuclideanVector.apply(x = n.x, y = n.y, z = 0) * 10
            }
          val normalVectorsModelFields =
            DiscreteField[_3D, UnstructuredPointsDomain, EuclideanVector[_3D]]( // DiscreteField[_3D, UnstructuredPointsDomain, EuclideanVector[_3D]]
              UnstructuredPointsDomain.Create.CreateUnstructuredPointsDomain3D.create(ref3D.pointSet.points.toIndexedSeq),
              normalVectorsModel
            )

          //          val ui = ScalismoUI(s"${targetname}_${finger}_${percentageCut}")
          val ui = ScalismoUIHeadless()
          ui.show(ref3D, "cut")
          ui.show(normalVectorsModelFields, "normals")

          MeshIO.writeLineMesh(refCut2D, new File(myPaths.datapath, s"partial/mesh/${targetname}_${finger}_${percentageCut}.vtk"))

          val filteredTargetLMs = targetLM3D.filter {
            f =>
              val p2D = Point2D(f.point.x, f.point.y)
              (refCut2D.pointSet.findClosestPoint(p2D).point - p2D).norm < 2.0
          }
          LandmarkIO.writeLandmarksJson[_3D](filteredTargetLMs, new File(myPaths.datapath, s"partial/landmarks/${targetname}_${finger}_${percentageCut}.json"))
          println(s"Number of retained landmarks ${filteredTargetLMs.length}/${targetLM3D.length}")
          filteredTargetLMs.foreach(f => println(f.id))
        }
      }
    }
  }
}
