package apps.hands

import java.awt.Color
import java.io.File

import api.sampling.ModelFittingParameters
import api.sampling.loggers.{JSONAcceptRejectLogger, jsonLogFormat}
import apps.scalismoExtension.LineMeshConverter
import apps.util.{LogHelper2D, myPaths}
import scalismo.common.PointId
import scalismo.geometry._
import scalismo.io.{MeshIO, StatisticalLineModelIO}
import scalismo.mesh._
import scalismo.ui.api.ScalismoUI

object VisualizeModelComponents {

  def main(args: Array[String]) {
    println("starting app...")
    scalismo.initialize()

    val logPath = new File(myPaths.datapath, "log_overlap/")
    val registeredPath = new File(myPaths.datapath, "registered_overlap/")

    val ui = ScalismoUI()
    val gtModelFile = new File(myPaths.datapath, s"pca_overlap/gt/pca_full.h5")
    val gtModel = StatisticalLineModelIO.readStatisticalLineMeshModel(gtModelFile).get
    val modelGroup = ui.createGroup("GT")
    VisualizeData.visualizePCsamples(gtModel, ui, modelGroup)


    //    Seq(5).foreach { p =>
    Seq(5, 10, 15, 20, 30, 40, 50, 60, 70, 80).foreach { p =>
      val modelFile = new File(myPaths.datapath, s"pca_overlap/sample/pca_full_${p}.h5")

      val model = StatisticalLineModelIO.readStatisticalLineMeshModel(modelFile).get

      val modelGroup = ui.createGroup(s"sample-${p}")
      VisualizeData.visualizePCsamples(model, ui, modelGroup)

      println("All done!")
    }
  }
}
