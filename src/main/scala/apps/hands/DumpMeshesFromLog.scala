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

import apps.util.LogHelper2D
import scalismo.io.{MeshIO, StatisticalModelIO}

import scala.collection.parallel.ForkJoinTaskSupport

object DumpMeshesFromLog {


  def main(args: Array[String]) {
    scalismo.initialize()

    val logPath = Paths.handLogPath

    val modelFile = new File(Paths.handPath, "hand2D_gp_s25_s50_s120_per.h5")
    val model = StatisticalModelIO.readStatisticalLineMeshModel2D(modelFile).get

    val experimentPath = new File(Paths.handPath, s"shapemi/allFingers15pMissing")
    experimentPath.mkdirs()

    println(s"ExperimentPATH: ${experimentPath}")
    val randomPath = new File(experimentPath, s"samples/")
    val meanPath = new File(experimentPath, s"mean/")
    val mapPath = new File(experimentPath, s"map/")
    val outPaths = Seq(randomPath, meanPath, mapPath)
    outPaths.foreach(_.mkdir())

    val percentageCuts = Seq(15)
    val fingerSeq = Seq("thumb", "index", "long", "ring", "small")

    val idSeq = 0 to 11
    println("Targets: ")
    val targetNames = idSeq.map(i => s"hand-${i}_${fingerSeq(i % fingerSeq.length)}_${percentageCuts(i % percentageCuts.length)}").sorted

    targetNames.foreach(f => println(s" - ${f}"))

    targetNames.zipWithIndex.par.foreach { case (targetpartialname, sleepTime) =>

      Thread.sleep(sleepTime * 100)

      val burnInPhase = 200
      val logFile = new File(logPath, targetpartialname + ".json")
      val log = LogHelper2D(logFile, model, burnInPhase)

      val rndShapes = log.sampleMeshes2D(takeEveryN = 20, total = 10000, randomize = true)

      val best = log.mapMesh2D()
      val mean = log.meanMesh2D(takeEveryN = 50)

      MeshIO.writeLineMesh(mean, new File(meanPath, targetpartialname + "_mean.vtk"))
//      MeshIO.writeLineMesh(best, new File(mapPath, targetpartialname + "_map.vtk"))

//      rndShapes.take(10).zipWithIndex.foreach { case (m, i) =>
//        MeshIO.writeLineMesh(m, new File(randomPath, targetpartialname + s"_${i}.vtk"))
//      }
    }
  }
}
