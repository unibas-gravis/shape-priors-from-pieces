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

package apps.femur

import java.awt.Color
import java.io.File

import apps.util.LogHelper3D
import scalismo.io.MeshIO
import scalismo.ui.api.{ScalismoUI, ScalismoUIHeadless}
import scalismo.utils.Random


object DumpMeshesFromLog {
  implicit val random: Random = Random(1024)

  def main(args: Array[String]): Unit = {
    scalismo.initialize()

    val visualizeOutput = false

    val (model, _) = LoadData.model()

    val logFiles = new File(Paths.generalPath, "logs").listFiles(_.getName.endsWith(".json")).sorted

    val partialPath = new File(Paths.generalPath, "partialMeshes")
    val completePath = new File(Paths.generalPath, "aligned/meshes")

    val experimentPath = new File(Paths.generalPath, "shapemi")
    val randomPath = new File(experimentPath, s"samples")
    val meanPath = new File(experimentPath, s"mean")
    val mapPath = new File(experimentPath, s"map")
    val outPaths = Seq(experimentPath, randomPath, meanPath, mapPath)
    outPaths.foreach { p =>
      if (!p.exists()) {
        println(s"Creating path: ${p}")
        p.mkdir()
      }
    }

    logFiles.par.foreach { logFile =>
      println(s"Handling log file: ${logFile}")

      val jsonFileName = logFile.getName
      val jsonFileNameNoEnding = jsonFileName.replace("_log.json", "")

      val targetName = jsonFileName.split("_").head + ".stl"
      val completeFile = new File(completePath, targetName)
      val partialFile = new File(partialPath, targetName)

      val complete = MeshIO.readMesh(completeFile).get
      val partial = MeshIO.readMesh(partialFile).get

      val burnInPhase = 200
      val log = LogHelper3D(logFile, model, burnInPhase)

      val rndShapes = log.sampleMeshes(takeEveryN = 10, total = 100000, randomize = true)

      val best = log.mapMesh()
      val mean = log.meanMesh(takeEveryN = 100)

      MeshIO.writeMesh(mean, new File(meanPath, jsonFileNameNoEnding + "_mean.vtk"))
      MeshIO.writeMesh(best, new File(mapPath, jsonFileNameNoEnding + "_map.vtk"))

      rndShapes.take(100).zipWithIndex.foreach { case (m, i) =>
        MeshIO.writeMesh(m, new File(randomPath, jsonFileNameNoEnding + s"_${i}_.vtk"))
      }

      val ui = if (visualizeOutput) ScalismoUI(jsonFileName) else ScalismoUIHeadless()
      val bestGroups = ui.createGroup("best")
      val sampleGroup = ui.createGroup("best")
      val gtGroup = ui.createGroup("gt")

      ui.show(bestGroups, best, "best")
      ui.show(bestGroups, mean, "mean")
      ui.show(gtGroup, complete, "complete").color = Color.ORANGE
      ui.show(gtGroup, partial, "partial").color = Color.RED
      rndShapes.take(10).zipWithIndex.foreach { case (m, i) =>
        ui.show(sampleGroup, m, i.toString)
      }

    }
  }
}
