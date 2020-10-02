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

object VisualizeLog extends App {
  scalismo.initialize()

  val logPath = Paths.handLogPath

  val modelFile = new File(Paths.handPath, "hand2D_gp_s25_s50_s120_per.h5")
  val model = StatisticalModelIO.readStatisticalLineMeshModel2D(modelFile).get

  val targetName = "hand-0"
  val finger = "thumb"
  val cutPercentage = 15

  val targetGTFile = new File(Paths.handPath, s"aligned/mesh/${targetName}.vtk")
  val partialName = s"${targetName}_${finger}_${cutPercentage}"
  val targetFile = new File(Paths.handPath, s"partial/mesh/${partialName}.vtk")

  val targetMesh = MeshIO.readLineMesh2D(targetFile).get
  val targetGTMesh = MeshIO.readLineMesh2D(targetGTFile).get

  val ui = ScalismoUI("Visualize log file")
  val modelGroup = ui.createGroup("model")
  val targetGroup = ui.createGroup("target")
  val regGroup = ui.createGroup("registrations")

  Visualization2DHelper.show2DLineMesh(ui, modelGroup, model.reference, "reference").opacity = 0f
  Visualization2DHelper.show2DLineMesh(ui, targetGroup, targetMesh, "target").color = Color.RED
  Visualization2DHelper.show2DLineMesh(ui, targetGroup, targetGTMesh, "grount-truth").opacity = 0f

  val burnInPhase = 200
  val logFile = new File(logPath, partialName + ".json")
  val log = LogHelper2D(logFile, model, burnInPhase)

  val rndShapes = log.sampleMeshes2D(takeEveryN = 20, total = 10000, randomize = true)

  val best = log.mapMesh2D()
  Visualization2DHelper.show2DLineMesh(ui, regGroup, best, "MAP")

  rndShapes.take(50).zipWithIndex.foreach { case (m, i) =>
    Visualization2DHelper.show2DLineMesh(ui, regGroup, m, s"sample-${i}")
  }
  val mean = log.meanMesh2D(takeEveryN = 50)
  Visualization2DHelper.show2DLineMesh(ui, regGroup, mean, "MEAN")
}
