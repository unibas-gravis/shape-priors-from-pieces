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

import java.io.File

import apps.util.FileUtils
import scalismo.io.MeshIO

object RegistrationAll {

  def main(args: Array[String]): Unit = {
    scalismo.initialize()

    println(s"Starting Metropolis Hastings registrations with ICP-proposal!")

    val groundTruthPath = new File(Paths.generalPath, "aligned/meshes/")
    val partialPath = new File(Paths.generalPath, "partialMeshes/")
    val logPath = new File(Paths.generalPath, "logs")
    logPath.mkdirs()

    val uncertainty = 4

    partialPath.listFiles(_.getName.endsWith(".stl")).sorted.foreach { partialFile =>
      val name = FileUtils.basename(partialFile)
      val targetMesh = MeshIO.readMesh(partialFile).get
      val completeTargetMesh = MeshIO.readMesh(new File(groundTruthPath, partialFile.getName)).get

      val log = new File(logPath, s"${name}_${uncertainty}_log.json")

      RegistrationSingle.loadTargetAndFit(targetMesh, completeTargetMesh, uncertainty, log, showUI = false)
    }
  }
}
