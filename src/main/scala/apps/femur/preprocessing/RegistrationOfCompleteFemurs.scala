package apps.femur.preprocessing

import java.io.File

import api.other.{IcpBasedSurfaceFitting, ModelAndTargetSampling}
import api.sampling._
import apps.femur.{LoadData, Paths}
import scalismo.geometry._3D
import scalismo.io.MeshIO
import scalismo.mesh.{TriangleMesh, TriangleMesh3D}
import scalismo.statisticalmodel.StatisticalMeshModel
import scalismo.ui.api.StatisticalMeshModelViewControls

object RegistrationOfCompleteFemurs {

  def fitting(model: StatisticalMeshModel, targetMesh: TriangleMesh3D, numOfSamplePoints: Int, numOfIterations: Int, showModel: Option[StatisticalMeshModelViewControls], initialParameters: Option[ModelFittingParameters] = None): TriangleMesh[_3D] = {

    val initPars =
      if (initialParameters.isDefined) {
        Option(initialParameters.get.shapeParameters.parameters, ModelFittingParameters.poseTransform(initialParameters.get))
      }
      else {
        None
      }

    val icpFitting = IcpBasedSurfaceFitting(model, targetMesh, numOfSamplePoints, projectionDirection = ModelAndTargetSampling, showModel = showModel)
    val t0 = System.currentTimeMillis()

    val best = icpFitting.runfitting(numOfIterations, iterationSeq = Seq(1e-15), initialModelParameters = initPars)
    val t1 = System.currentTimeMillis()
    println(s"ICP-Timing: ${(t1 - t0) / 1000.0} sec")
    best
  }

  def main(args: Array[String]): Unit = {
    scalismo.initialize()

    println(s"Starting Standard NonRigid ICP registrations!")

    val (model, _) = LoadData.model()

    val inputDir = new File(Paths.generalPath, "aligned/meshes")
    val outputDir = new File(Paths.generalPath, "registered/meshes")
    outputDir.mkdirs()

    val numOfEvaluatorPoints = model.referenceMesh.pointSet.numberOfPoints // Used for the evaluation
    val numOfIterations = 200 // Number of ICP iterations

    inputDir.listFiles(_.getName.endsWith(".stl")).sorted.zipWithIndex.foreach {
      case (targetFile, i) => {
        println(s"Registering complete aligned target: ${targetFile}")
        val targetMesh = MeshIO.readMesh(targetFile).get
        val bestRegistration = fitting(model, targetMesh, numOfEvaluatorPoints, numOfIterations = numOfIterations, showModel = None)
        MeshIO.writeMesh(bestRegistration, new File(outputDir, targetFile.getName.replace(".stl", ".vtk")))
      }
    }
  }
}
