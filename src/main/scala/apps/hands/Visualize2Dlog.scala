package apps.hands

import java.io.File

import apps.scalismoExtension.LineMeshConverter
import apps.util.{LogHelper2D, myPaths}
import scalismo.io.{MeshIO, StatisticalLineModelIO}
import scalismo.ui.api.ScalismoUI

import scala.collection.parallel.{ForkJoinTaskSupport, ParSeq}

object Visualize2Dlog {
  val fingerSeq = Seq("thumb", "index", "long", "ring", "small")

  def main(args: Array[String]) {
    println("starting app...")
    scalismo.initialize()

    val localExperimentPath = myPaths.experimentPath //new File("/home/madden00/tmpexperiment")


    println(s"ExperimentPATH: ${localExperimentPath}")

    //    val experimentPath = new File(myPaths.datapath, s"experiments/shapemi_scale_06_25")
    val experimentPath = new File(myPaths.datapath, s"experiments/shapemi_gauss_per_06_25")
    //    val experimentPath = new File(myPaths.datapath, s"experiments/shapemi_skel_ablation_07_03")


    val logPath = new File(experimentPath, "log")
    val partialPath = new File(myPaths.datapath, s"partial/mesh")
    val alignedPath = new File(myPaths.datapath, s"aligned/mesh")

    val modelFile = new File(myPaths.datapath, "hand2D_gp_s25_s50_s120_per.h5") // Define which model to use
    //    val modelFile = new File(myPaths.datapath, "hand2D_gp_s25_s50_s120_ablation.h5") // Define which model to use

    println("Visualize2D log and dump files!")
    scala.io.StdIn.readLine(s"Log files path: ${logPath}, Dump files under: ${localExperimentPath}, Model file: ${modelFile}, continue?")


    val percentageCuts = Seq(20) //Seq(5, 10, 15, 20, 30, 40, 50, 60, 70, 80, 90)
    //    val percentageCuts = Seq(15)//, 50, 60, 70, 80, 90)

    val index = 0
    //    val targetnameSeq = Seq(s"test-${index}-normal")//,s"test-${index}-bigger", s"test-${index}-biggest", s"test-${index}-smaller", s"test-${index}-smallest")
    //    val targetnameSeq = Seq(s"test-${index}-normal")

    val targetnameSeq = Seq(s"hand-0")

    //    val targetnameSeq = Seq( s"hand-0", s"hand-1", s"hand-2", s"hand-3", s"hand-5",
    //      s"hand-6", s"hand-7", s"hand-8", s"hand-10", s"hand-11",
    //      s"hand-12", s"hand-16")

    val fingers = Seq(fingerSeq(2))
    //    val middlePointId = s"finger.${finger}.tip"

    val modelLineMesh = StatisticalLineModelIO.readStatisticalLineMeshModel(modelFile).get

    val targetNames: ParSeq[String] = targetnameSeq.flatMap { tn =>
      fingers.flatMap { finger =>
        percentageCuts.map { cut =>
          tn + "_" + finger + "_" + cut
        }
      }
    }.par

    targetNames.tasksupport = new ForkJoinTaskSupport(new java.util.concurrent.ForkJoinPool(50))

    //    val targetNames = Seq(targetname + "_" + finger + "_" + percentageCut)
    //    val targetNames = Seq(targetname + "_" + finger + "_" + percentageCut)

    targetNames.zipWithIndex.foreach { case (targetpartialname, sleepTime) =>

      Thread.sleep(sleepTime * 100)

      val targetname = targetpartialname.split("_").head
      val logname = targetpartialname + "_partial.json"
      println(s"Target name: ${targetname}, partial: ${targetpartialname}, log: ${logname}")

      val percentageCut = targetpartialname.split("_").last.toInt

      val targetMesh2D = MeshIO.readLineMesh2D(new File(partialPath, targetpartialname + ".vtk")).get
      val targetMesh3D = LineMeshConverter.lineMesh2Dto3D(targetMesh2D)
      val groundTruthMesh2D = MeshIO.readLineMesh2D(new File(alignedPath, targetname + ".vtk")).get
      val groundTruthMesh3D = LineMeshConverter.lineMesh2Dto3D(groundTruthMesh2D)

      val burnInPhase = 200
      val logFile = new File(logPath, logname)
      val log = LogHelper2D(logFile, modelLineMesh, burnInPhase)

      val rndShapes = log.sampleMeshes2D(takeEveryN = 50, total = 10000, randomize = true)

      val ui = ScalismoUI(s"Cut-${percentageCut}")
      //      val ui = ScalismoUIHeadless()
      val targetGroup = ui.createGroup("target")
      val showPartial = ui.show(targetGroup, targetMesh3D, "partial")
      val showGT = ui.show(targetGroup, groundTruthMesh3D, "gt")

      val bestGroup = ui.createGroup("best")
      val sampleGroup = ui.createGroup("samples-LOG")

      val best = log.mapMesh2D()
      //      val mean = log.meanMesh2D(takeEveryN = 100)
      ui.show(bestGroup, LineMeshConverter.lineMesh2Dto3D(best), "best")
      //      ui.show(bestGroup, LineMeshConverter.lineMesh2Dto3D(mean), "mean")

      //      println(s"Output folders will be created under: ${localExperimentPath}")
      ////      scala.io.StdIn.readLine(s"Output folders will be created under: ${experimentPath}, continue?")
      //      val randomPath = new File(localExperimentPath, s"samples/${percentageCut}")
      //      val meanPath = new File(localExperimentPath, s"mean/${percentageCut}")
      //      val mapPath = new File(localExperimentPath, s"map/${percentageCut}")
      //      val outPaths = Seq(randomPath, meanPath, mapPath)
      //      outPaths.foreach { p =>
      //        if (!p.exists()) {
      //          println(s"Creating path: ${p}")
      //          p.mkdir()
      //        }
      //      }

      //      MeshIO.writeLineMesh(mean, new File(meanPath, targetpartialname + "_mean.vtk"))
      //      MeshIO.writeLineMesh(best, new File(mapPath, targetpartialname + "_map.vtk"))

      rndShapes.take(50).zipWithIndex.foreach { case (m, i) =>
        ui.show(sampleGroup, LineMeshConverter.lineMesh2Dto3D(m), i.toString)
        //        MeshIO.writeLineMesh(m, new File(randomPath, targetpartialname + s"_${i}.vtk"))
      }
    }
    println("All done!")
  }
}
