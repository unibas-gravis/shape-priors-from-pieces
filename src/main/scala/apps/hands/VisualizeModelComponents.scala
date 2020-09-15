package apps.hands

import java.io.File

import apps.util.myPaths
import scalismo.io.StatisticalModelIO
import scalismo.ui.api.ScalismoUI

object VisualizeModelComponents {

  def main(args: Array[String]) {
    println("starting app...")
    scalismo.initialize()

    val logPath = new File(myPaths.datapath, "log_overlap/")
    val registeredPath = new File(myPaths.datapath, "registered_overlap/")

    val ui = ScalismoUI()
    val gtModelFile = new File(myPaths.datapath, s"pca_overlap/gt/pca_full.h5")
    val gtModel = StatisticalModelIO.readStatisticalLineMeshModel2D(gtModelFile).get
    val modelGroup = ui.createGroup("GT")
    VisualizeData.visualizePCsamples(gtModel, ui, modelGroup)


    //    Seq(5).foreach { p =>
    Seq(5, 10, 15, 20, 30, 40, 50, 60, 70, 80).foreach { p =>
      val modelFile = new File(myPaths.datapath, s"pca_overlap/sample/pca_full_${p}.h5")

      val model = StatisticalModelIO.readStatisticalLineMeshModel2D(modelFile).get

      val modelGroup = ui.createGroup(s"sample-${p}")
      VisualizeData.visualizePCsamples(model, ui, modelGroup)

      println("All done!")
    }
  }
}
