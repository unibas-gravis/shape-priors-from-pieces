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

import java.io.File

import scalismo.geometry._
import scalismo.io.{LandmarkIO, StatisticalModelIO}
import scalismo.ui.api.ScalismoUIHeadless

object RegistrationAll extends App {

  println("starting app...")
  scalismo.initialize()

  val modelFile = new File(Paths.handPath, "hand2D_gp_s25_s50_s120_per.h5")
  val model = StatisticalModelIO.readStatisticalLineMeshModel2D(modelFile).get
  val modelLMFile = new File(Paths.handPath, "reference-hand.json")
  val modelLM: Seq[Landmark[_2D]] = LandmarkIO.readLandmarksJson2D(modelLMFile).get

  val targetFiles = new File(Paths.handPath, s"aligned/mesh")
  val fingers = Seq("thumb", "index", "long", "ring", "small")

  val cutPercentages = Seq(5, 10, 15, 20, 30, 40, 50, 60, 70, 80, 90)

  val logPath = Paths.handLogPath
  logPath.mkdir()

  targetFiles.listFiles(_.getName.endsWith(".vtk")).sorted.par.foreach { targetGTFile =>
    val targetName = targetGTFile.getName.replace(".vtk", "")
    fingers.foreach { finger =>
      cutPercentages.foreach { cutPercentage =>
        val partialName = s"${targetName}_${finger}_${cutPercentage}"
        val targetFile = new File(Paths.handPath, s"partial/mesh/${partialName}.vtk")
        val targetLMFile = new File(Paths.handPath, s"partial/landmarks/${partialName}.json")

        val log = new File(logPath, s"${partialName}.json")

        val ui = ScalismoUIHeadless()

        val reg = HandRegistration(model, modelLM, targetGTFile, targetFile, targetLMFile, log)
        reg.run(ui = ui, numOfSamples = 1000)
      }
    }
  }
}
