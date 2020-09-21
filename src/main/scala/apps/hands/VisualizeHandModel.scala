package apps.hands

import apps.util.{Visualization2DHelper, myPaths}
import scalismo.io.StatisticalModelIO
import scalismo.ui.api.ScalismoUI
import scalismo.utils.Random.implicits.randomGenerator

object VisualizeHandModel {

  def main(args: Array[String]) {
    scalismo.initialize()

    val modelFile = myPaths.handsPath.listFiles(_.getName.endsWith(".h5")).head

    val model = StatisticalModelIO.readStatisticalLineMeshModel2D(modelFile).get

    val ui = ScalismoUI("Visualize hand model")
    val modelGroup = ui.createGroup("model")
    Visualization2DHelper.visualizePCsamples(ui, model, modelGroup)

    val randomGroup = ui.createGroup("random")
    (0 until 10).foreach{i =>
      val showing = Visualization2DHelper.show2DLineMesh(ui, randomGroup, model.sample(), s"${i}")
      showing.opacity = 0f
    }
  }
}
