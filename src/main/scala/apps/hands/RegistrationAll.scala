package apps.hands

import java.io.File

import apps.util.myPaths
import scalismo.geometry._
import scalismo.io.{LandmarkIO, StatisticalModelIO}
import scalismo.ui.api.{ScalismoUI, ScalismoUIHeadless}

object RegistrationAll extends App{

    println("starting app...")
    scalismo.initialize()

    val modelFile = new File(myPaths.handsPath, "hand2D_gp_s25_s50_s120_per.h5")
    val modelLineMesh = StatisticalModelIO.readStatisticalLineMeshModel2D(modelFile).get
    val modelLMFile = new File(myPaths.handsPath, "reference-hand.json")
    val modelLM: Seq[Landmark[_2D]] = LandmarkIO.readLandmarksJson2D(modelLMFile).get

    val targetFiles = new File(myPaths.handsPath, s"aligned/mesh")
//    val fingers = Seq("thumb", "index", "long", "ring", "small")
//    val cutPercentages = Seq(5, 10, 15, 20, 30, 40, 50, 60, 70, 80, 90)
    val fingers = Seq("thumb")
    val cutPercentages = Seq(15)

    val logPath = myPaths.handsLogPath
    logPath.mkdir()

    targetFiles.listFiles(_.getName.endsWith(".vtk")).sorted.par.foreach{targetGTFile =>
        val targetName = targetGTFile.getName.replace(".vtk", "")
        fingers.foreach{finger =>
          cutPercentages.foreach{cutPercentage =>
              val partialName = s"${targetName}_${finger}_${cutPercentage}"
              val targetFile = new File(myPaths.handsPath, s"partial/mesh/${partialName}.vtk")
              val targetLMFile = new File(myPaths.handsPath, s"partial/landmarks/${partialName}.json")

              val log = new File(logPath, s"${partialName}.json")

              val ui = ScalismoUIHeadless()

              val reg = HandRegistration(modelLineMesh, modelLM, targetGTFile, targetFile, targetLMFile, log)
              reg.run(ui = ui, numOfSamples = 1000)
          }
        }
    }
}
