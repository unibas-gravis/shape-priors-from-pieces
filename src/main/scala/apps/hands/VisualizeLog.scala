/*
 *  Copyright University of Basel, Graphics and Vision Research Group
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package apps.hands

import java.awt.Color
import java.io.File

import apps.util.{LogHelper2D, Visualization2DHelper}
import scalismo.io.{MeshIO, StatisticalModelIO}
import scalismo.ui.api.ScalismoUI
import scalismo.utils.Random.implicits.randomGenerator

object VisualizeLog {

  def main(args: Array[String]) {
    scalismo.initialize()

    val logPath = Paths.handLogPath
    val targetpartialname = "hand-1_thumb_15"


    val modelFile = Paths.handPath.listFiles(_.getName.endsWith(".h5")).head

    val model = StatisticalModelIO.readStatisticalLineMeshModel2D(modelFile).get

    val targetFile = new File("")
    val gtFile = new File("")
    val targetMesh = MeshIO.readLineMesh2D(targetFile).get
    val gtMesh = MeshIO.readLineMesh2D(gtFile).get

    val ui = ScalismoUI("Visualize log file")
    val modelGroup = ui.createGroup("model")
    val targetGroup = ui.createGroup("target")
    val regGroup = ui.createGroup("registrations")

    Visualization2DHelper.show2DLineMesh(ui, modelGroup, model.reference, "reference").opacity = 0f
    Visualization2DHelper.show2DLineMesh(ui, targetGroup, targetMesh, "target").color = Color.RED
    Visualization2DHelper.show2DLineMesh(ui, targetGroup, gtMesh, "grount-truth").opacity = 0f

    val burnInPhase = 200
    val logFile = new File(logPath, targetpartialname + ".json")
    val log = LogHelper2D(logFile, model, burnInPhase)

    val rndShapes = log.sampleMeshes2D(takeEveryN = 20, total = 10000, randomize = true)

    val best = log.mapMesh2D()
    val mean = log.meanMesh2D(takeEveryN = 50)
    Visualization2DHelper.show2DLineMesh(ui, regGroup, best, "MAP")
    Visualization2DHelper.show2DLineMesh(ui, regGroup, mean, "MEAN")

    rndShapes.take(50).zipWithIndex.foreach { case (m, i) =>
      Visualization2DHelper.show2DLineMesh(ui, regGroup, m, s"sample-${i}")
    }
  }
}
