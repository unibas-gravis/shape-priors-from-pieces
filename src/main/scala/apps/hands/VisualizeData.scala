package apps.hands

import java.awt.Color
import java.io.File

import apps.scalismoExtension.LineMeshConverter
import apps.util.myPaths
import breeze.linalg.DenseVector
import scalismo.io.MeshIO
import scalismo.statisticalmodel.StatisticalLineMeshModel
import scalismo.ui.api.{Group, ScalismoUI}

object VisualizeData {

  def visualizePCsamples(model: StatisticalLineMeshModel, ui: ScalismoUI, group: Group, maxNumberOfComponents: Int = 3) = {
    (0 to math.min(maxNumberOfComponents, model.rank - 1)).foreach { pc =>
      (-3 to 3).foreach { i =>
        var coeff = DenseVector.zeros[Double](model.rank)
        coeff(pc) = i.toDouble
        val showing = ui.show(
          group,
          LineMeshConverter.lineMesh2Dto3D(model.instance(coeff)),
          s"PC-${pc}_${i}"
        )
        showing.opacity = 0f
        showing.lineWidth = 1
        if (i == 0) {
          showing.color = Color.GREEN
        }
        //        else{
        //          showing.color = Color.BLACK
        //        }
      }
    }
  }

  def main(args: Array[String]) {
    println("starting app...")
    scalismo.initialize()

    val ui = ScalismoUI()

    val dataGroup = ui.createGroup("data")

    val data = new File(myPaths.datapath, "aligned/mesh").listFiles(_.getName.endsWith(".vtk"))

    data.foreach { f =>
      val m = MeshIO.readLineMesh2D(f).get
      println(s"${f.getName}, ${m.pointSet.numberOfPoints}")
      ui.show(dataGroup, LineMeshConverter.lineMesh2Dto3D((m)), f.getName).color = Color.BLACK
    }

    val referenceMesh = MeshIO.readLineMesh2D(new File(myPaths.datapath, "aligned/reference-hand.vtk")).get
    val ref3D = LineMeshConverter.lineMesh2Dto3D(referenceMesh)

    val partialGroup = ui.createGroup("partial")
    ui.show(dataGroup, ref3D, "ref").color = Color.BLACK

    Seq(5, 10, 15, 20, 30, 40, 50, 60, 70, 80).par.foreach { p =>
      val targetNameSeq = Seq(s"hand-0_thumb_${p}", s"hand-1_index_${p}", s"hand-2_long_${p}", s"hand-3_ring_${p}", s"hand-5_small_${p}",
        s"hand-6_thumb_${p}", s"hand-7_index_${p}", s"hand-8_long_${p}", s"hand-10_ring_${p}", s"hand-11_small_${p}",
        s"hand-12_thumb_${p}", s"hand-16_index_${p}")

      targetNameSeq.foreach { targetName =>
        val targetFile = new File(myPaths.datapath, s"partial/mesh/${targetName}.vtk")

        val targetLinemesh2D = MeshIO.readLineMesh2D(targetFile).get
        val targetLineMesh3D = LineMeshConverter.lineMesh2Dto3D(targetLinemesh2D)

        ui.show(partialGroup, targetLineMesh3D, targetFile.getName).color = Color.BLACK
      }
    }
  }
}
