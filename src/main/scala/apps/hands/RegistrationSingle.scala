package apps.hands

import java.io.File
import apps.util.myPaths
import scalismo.geometry._
import scalismo.io.{LandmarkIO, StatisticalModelIO}
import scalismo.ui.api.ScalismoUI

object RegistrationSingle extends App{
    scalismo.initialize()

    val modelFile = new File(myPaths.handsPath, "hand2D_gp_s25_s50_s120_per.h5")
    val modelLineMesh = StatisticalModelIO.readStatisticalLineMeshModel2D(modelFile).get
    val modelLMFile = new File(myPaths.handsPath, "reference-hand.json")
    val modelLM: Seq[Landmark[_2D]] = LandmarkIO.readLandmarksJson2D(modelLMFile).get

    val targetName = "hand-1"
    val finger = "thumb"
    val cutPercentage = 15

    val targetGTFile = new File(myPaths.handsPath, s"aligned/mesh/${targetName}.vtk")
    val partialName = s"${targetName}_${finger}_${cutPercentage}"
    val targetFile = new File(myPaths.handsPath, s"partial/mesh/${partialName}.vtk")
    val targetLMFile = new File(myPaths.handsPath, s"partial/landmarks/${partialName}.json")

    val logPath = myPaths.handsLogPath
    logPath.mkdir()
    val log = new File(logPath, s"${partialName}.json")

    val ui = ScalismoUI(partialName)

    val reg = HandRegistration(modelLineMesh, modelLM, targetGTFile, targetFile, targetLMFile, log)
    reg.run(ui = ui, numOfSamples = 10000, initialParameters = None, showNormals = true)
}
