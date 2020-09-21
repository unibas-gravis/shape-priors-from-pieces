package apps.hands

import java.io.File
import apps.util.{LogHelper2D, myPaths}
import scalismo.io.{MeshIO, StatisticalModelIO}

import scala.collection.parallel.ForkJoinTaskSupport

object Logs2Meshes {


  def main(args: Array[String]) {
    scalismo.initialize()

    val logPath = myPaths.handsLogPath

    val modelFile = new File(myPaths.handsPath, "hand2D_gp_s25_s50_s120_per.h5")
    val model = StatisticalModelIO.readStatisticalLineMeshModel2D(modelFile).get

    val experimentPath = new File(myPaths.handsPath, s"experiments/shapemi/allFingers15pMissing")
    experimentPath.mkdirs()

    println(s"ExperimentPATH: ${experimentPath}")

    val percentageCuts = Seq(15)
    val fingerSeq = Seq("thumb", "index", "long", "ring", "small")

    val idSeq = 0 to 11
    val targetNames = idSeq.map(i => s"hand-${i}_${fingerSeq(i%fingerSeq.length)}_${percentageCuts(i%percentageCuts.length)}").par

    targetNames.foreach(println(_))

    targetNames.tasksupport = new ForkJoinTaskSupport(new java.util.concurrent.ForkJoinPool(10))

    targetNames.zipWithIndex.foreach { case (targetpartialname, sleepTime) =>

      Thread.sleep(sleepTime * 100)

      val burnInPhase = 200
      val logFile = new File(logPath, targetpartialname + ".json")
      val log = LogHelper2D(logFile, model, burnInPhase)

      val rndShapes = log.sampleMeshes2D(takeEveryN = 20, total = 10000, randomize = true)

      val best = log.mapMesh2D()
      val mean = log.meanMesh2D(takeEveryN = 50)

      println(s"Output folders will be created under: ${experimentPath}")
      val randomPath = new File(experimentPath, s"samples/")
      val meanPath = new File(experimentPath, s"mean/")
      val mapPath = new File(experimentPath, s"map/")
      val outPaths = Seq(randomPath, meanPath, mapPath)
      outPaths.foreach(_.mkdir())

      MeshIO.writeLineMesh(mean, new File(meanPath, targetpartialname + "_mean.vtk"))
      MeshIO.writeLineMesh(best, new File(mapPath, targetpartialname + "_map.vtk"))

      rndShapes.take(50).zipWithIndex.foreach { case (m, i) =>
        MeshIO.writeLineMesh(m, new File(randomPath, targetpartialname + s"_${i}.vtk"))
      }
    }
  }
}