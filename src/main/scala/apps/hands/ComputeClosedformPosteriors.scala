package apps.hands

import java.awt.Color
import java.io.File

import apps.hands.preprocessing.CreateHandReference.{getLmPoint, getPointsAroundLM}
import apps.scalismoExtension.LineMeshConverter
import apps.util.myPaths
import scalismo.geometry._
import scalismo.io.{LandmarkIO, MeshIO, StatisticalLineModelIO}
import scalismo.mesh._
import scalismo.ui.api.ScalismoUI
import scalismo.utils.Random.implicits._

object ComputeClosedformPosteriors {
  val fingerSeq = Seq("thumb", "index", "long", "ring", "small")

  def main(args: Array[String]) {
    println("starting app...")
    scalismo.initialize()

    val percentageCut = 20
    val modelFile = new File(myPaths.datapath, "hand2D_gp_test.h5") // Define which model to use
    val targetname = "hand-0"
    val middlePointId = s"finger.${fingerSeq(0)}.tip"

    // load model
    val modelLineMesh = StatisticalLineModelIO.readStatisticalLineMeshModel(modelFile).get

    // load target data
    val targetFile = new File(myPaths.datapath, s"registered/mesh/${targetname}.vtk")
    val targetFull2D = MeshIO.readLineMesh2D(targetFile).get
    val targetFull3D = LineMeshConverter.lineMesh2Dto3D(targetFull2D)
    val alignedLMFiles = new File(myPaths.datapath, "registered/landmarks").listFiles(_.getName.endsWith(".json"))
    val targetLMFile = alignedLMFiles.find(f => f.getName == targetname + ".json").get
    val targetLM3D = LandmarkIO.readLandmarksJson[_3D](targetLMFile).get
    val targetLM2D = targetLM3D.map(lm => Landmark[_2D](lm.id, Point2D(lm.point.x, lm.point.y), lm.description, lm.uncertainty))

    // cut target mesh
    val middlePoint = getLmPoint[_2D](targetLM2D, middlePointId)
    val numberOfPointsToCut: Int = targetFull2D.pointSet.numberOfPoints * percentageCut / 100
    val points = getPointsAroundLM(targetFull2D, middlePoint, numberOfPointsToCut)
    val refCut2Dopen = LineMeshConverter.createPartialMesh(targetFull2D, points)
    val m2Dboundary = refCut2Dopen.pointSet.pointIds.toIndexedSeq.filter(id => refCut2Dopen.topology.adjacentPointsForPoint(id).length < 2)
    val closedLineList2 = LineList(refCut2Dopen.topology.lines ++ IndexedSeq(LineCell(m2Dboundary.last, m2Dboundary.head)))
    val refCut2D = LineMesh2D(refCut2Dopen.pointSet, closedLineList2)
    val refCut3D = LineMeshConverter.lineMesh2Dto3D(refCut2D)

    // establish correspondence and compute posterior model
    val corrPoint = refCut2D.pointSet.points.toIndexedSeq.map{p => (targetFull2D.pointSet.findClosestPoint(p).id, p)}
    val posModel = modelLineMesh.posterior(corrPoint, 2.0)

    // visualization of target mesh
    val ui = ScalismoUI()
    val stuffGroup = ui.createGroup("stuff")
    val showTarget = ui.show(stuffGroup, targetFull3D, "targetFull")
    ui.show(stuffGroup, refCut3D, "targetCut").opacity = 0
    showTarget.opacity = 1
    showTarget.color = Color.YELLOW

    // visualize random samples
    val sampleGroup = ui.createGroup("samples")
    (1 to 20).foreach(i => {
      val sample = posModel.sample()
      ui.show(sampleGroup,LineMeshConverter.lineMesh2Dto3D(sample),s"sample$i").opacity = 0
    })

    // visualize first X principal components
    val pcGroup = ui.createGroup("PCs")
    VisualizeData.visualizePCsamples(posModel, ui, pcGroup, maxNumberOfComponents = 3)
  }
}
