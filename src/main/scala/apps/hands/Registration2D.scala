package apps.hands

import java.awt.Color
import java.io.File

import api.other.{RegistrationComparison, TargetSampling}
import api.sampling.evaluators.TargetToModelEvaluation
import api.sampling.{MixedProposalDistributions, ModelFittingParameters, ProductEvaluators, SamplingRegistration}
import apps.hands.preprocessing.{Create2dGPmodel, PartialDataFromLMs}
import apps.scalismoExtension.{LineMeshConverter, LineMeshOperator}
import apps.util.myPaths
import scalismo.common.{DiscreteField, UnstructuredPointsDomain}
import scalismo.geometry._
import scalismo.io.{MeshIO, StatisticalLineModelIO}
import scalismo.mesh._
import scalismo.sampling.DistributionEvaluator
import scalismo.sampling.proposals.MixtureProposal
import scalismo.sampling.proposals.MixtureProposal.ProposalGeneratorWithTransition
import scalismo.statisticalmodel.StatisticalLineMeshModel
import scalismo.ui.api.ScalismoUI
import scalismo.utils.Random.implicits._

object Registration2D {

  def main(args: Array[String]) {
    println("starting app...")
    scalismo.initialize()

    val modelFile = new File(myPaths.datapath, "gpLineMeshModel2d.h5")

    //    val model = StatisticalModelIO.readStatisticalMeshModel(new File("/home/madden00/workspace/uni/icp/icp-sampling-registration_cvpr/data/femur/femur_gp_model_50-components.h5")).get
    //    val targetMesh = MeshIO.readMesh(new File("/home/madden00/workspace/uni/icp/icp-sampling-registration_cvpr/data/femur/femur_target.stl")).get

    // read a mesh from file
    //    val model = StatisticalModelIO.readStatisticalMeshModel(new File(myPaths.datapath, "gpModel.h5")).get // Doesn't work as reference is not triangulated
    val referenceFile = new File(myPaths.datapath, "registered/mesh/mean.vtk")
    val referenceLineMesh2D = MeshIO.readLineMesh2D(referenceFile).get
    val referenceLineMesh3D =
      LineMeshConverter.lineMesh2Dto3D(referenceLineMesh2D)

    val modelLineMesh =
      StatisticalLineModelIO.readStatisticalLineMeshModel(modelFile).getOrElse {
        println(
          s"LineMesh model does not exist. Creating: ${modelFile.toString}"
        )
        val modelLineMesh =
          Create2dGPmodel.createModel(referenceLineMesh2D, 200, 100)
        StatisticalLineModelIO
          .writeStatisticalLineMeshModel(modelLineMesh, modelFile)
        modelLineMesh
      }

    println("---------- ---------- ---------- ---------- ----------")
    println("                    Model created                     ")
    println("---------- ---------- ---------- ---------- ----------")

    //    val dataPath = new File("data/partial/mesh")
    //    val dataPath = new File(myPaths.datapath, "registered/mesh")
    //    val dataFiles = dataPath
    //      .listFiles(
    //        f => f.getName.endsWith(".vtk") && f.getName.startsWith("hand")
    //      )
    //      .sorted

    (0 until 1).foreach { index =>
      //    (0 to 3).foreach { i =>
      //    (4 to 7).foreach { i =>
      //    (8 to 12).foreach { i =>
      println(s"Index ${index}")

      //      val targetFile = dataFiles(index)
      //      val targetLinemesh2DInit = MeshIO.readLineMesh2D(targetFile).get
      //      val targetLinemesh2D = targetLinemesh2DInit
      //      val targetLineMesh3D = LineMeshConverter.lineMesh2Dto3D(targetLinemesh2D)
      val (
        targetFile,
        targetLinemesh2DInit,
        targetLinemesh2D,
        targetLineMesh3D
        ) = PartialDataFromLMs.getPartialData(index, index)

      val m2Dboundary = targetLinemesh2D.pointSet.pointIds.toIndexedSeq.filter(
        id => targetLinemesh2D.topology.adjacentPointsForPoint(id).length < 2
      )
      val boundaryLMs = m2Dboundary.map { id =>
        val p2 = targetLinemesh2D.pointSet.point(id)
        Landmark(
          id = id.id.toString,
          point = Point3D(x = p2.x, y = p2.y, z = 0)
        )
      }

      val normalVectors: IndexedSeq[EuclideanVector[_3D]] =
        targetLinemesh2D.pointSet.pointIds.toIndexedSeq.map { id =>
          val p = targetLinemesh2D.pointSet.point(id)
          val nInit = targetLinemesh2D.vertexNormals(id)
          val n =
            if (LineMeshOperator(targetLinemesh2D)
              .verifyNormalDirection(p, nInit)) nInit
            else nInit.*(-1.0)
          EuclideanVector.apply(x = n.x, y = n.y, z = 0) * 10
        }
      val normalVectorsFields =
        DiscreteField[_3D, UnstructuredPointsDomain, EuclideanVector[_3D]](
          UnstructuredPointsDomain.Create.CreateUnstructuredPointsDomain3D.create(targetLineMesh3D.pointSet.points.toIndexedSeq),
          normalVectors
        )

      val normalVectorsModel: IndexedSeq[EuclideanVector[_3D]] =
        referenceLineMesh2D.pointSet.pointIds.toIndexedSeq.map { id =>
          val n = referenceLineMesh2D.vertexNormals(id)
          EuclideanVector.apply(x = n.x, y = n.y, z = 0) * 10
        }
      val normalVectorsModelFields =
        DiscreteField[_3D, UnstructuredPointsDomain, EuclideanVector[_3D]](
          UnstructuredPointsDomain.Create.CreateUnstructuredPointsDomain3D.create(referenceLineMesh3D.pointSet.points.toIndexedSeq),
          normalVectorsModel
        )

      println(
        s"${targetFile.getName}, ${targetLinemesh2D.pointSet.numberOfPoints}"
      )

      val ui: ScalismoUI = ScalismoUI()
      //      val ui: ScalismoUIHeadless = ScalismoUIHeadless()

      val modelGroup = ui.createGroup("model")
      //    val showModel = ui.show(modelGroup, modelLineMesh, "model")
      ui.show(modelGroup, referenceLineMesh3D, "ref")
      ui.show(modelGroup, normalVectorsModelFields, "normals").opacity = 0.0

      val targetGroup = ui.createGroup("target")
      val showTarget =
        ui.show(targetGroup, targetLineMesh3D, targetFile.getName)
      showTarget.color = Color.YELLOW
      ui.show(targetGroup, normalVectorsFields, "normals").opacity = 0.0
      ui.show(targetGroup, boundaryLMs, "landmarks")
      //    ui.show(targetGroup, df, "vectors")
      //    ui.show(targetGroup, lms, "lms")

      val finalGroup = ui.createGroup("finalGroup")

      val numOfEvaluatorPoints = 1000 //modelLineMesh.referenceMesh.pointSet.numberOfPoints / 10 // Used for the likelihood evaluator
      val numOfICPPointSamples = numOfEvaluatorPoints // Used for the ICP proposal
      val numOfSamples = 1000000 // Length of Markov Chain

      val proposalICP: ProposalGeneratorWithTransition[ModelFittingParameters] =
        MixedProposalDistributions.mixedProposalICP(
          modelLineMesh,
          targetLinemesh2D,
          Seq(),
          Seq(),
          numOfICPPointSamples,
          projectionDirection = TargetSampling,
          tangentialNoise = 10.0,
          noiseAlongNormal = 3.0,
          stepLength = 0.1
        )

      val proposalRND: ProposalGeneratorWithTransition[ModelFittingParameters] =
        MixedProposalDistributions.mixedProposalRandom(modelLineMesh)

      val proposal: ProposalGeneratorWithTransition[ModelFittingParameters] =
        MixtureProposal(Seq((0.1, proposalICP), (0.9, proposalRND)))

      //      val evaluator        model, =
      //        ProductEvaluators.proximityAndCollectiveHausdorffBoundaryAware(
      //          modelLineMesh,
      //          targetLinemesh2D,
      //          TargetToModelEvaluation,
      //          uncertaintyAvg = 1.0,
      //          numberOfEvaluationPoints = numOfEvaluatorPoints
      //        )
      val evaluator = ProductEvaluators.proximityAndIndependent(
        modelLineMesh,
        targetLinemesh2D,
        TargetToModelEvaluation,
        uncertainty = 1.0,
        numberOfEvaluationPoints = numOfEvaluatorPoints
      )

      val bestRegistration = fitting(
        modelLineMesh,
        targetLinemesh2D,
        evaluator,
        proposal,
        numOfSamples,
        Option(ui),
        new File(
          myPaths.datapath, s"log/icpProposalRegistration_${targetFile.getName.replace("aligned_1M.vtk", "")}.json"
        )
      )
      ui.show(
        finalGroup,
        LineMeshConverter.lineMesh2Dto3D(bestRegistration),
        "best-fit"
      )
      RegistrationComparison.evaluateReconstruction2GroundTruth(
        "SAMPLE",
        bestRegistration,
        targetLinemesh2D
      )
      //      MeshIO.writeLineMesh[_2D](
      //        bestRegistration,
      //        new File(myPaths.datapath, s"registered/mesh/${targetFile.getName}")
      //      )

    }
  }

  def fitting(
               model: StatisticalLineMeshModel,
               targetMesh: LineMesh2D,
               evaluator: Map[String, DistributionEvaluator[ModelFittingParameters]],
               proposal: ProposalGeneratorWithTransition[ModelFittingParameters],
               numOfIterations: Int,
               showModel: Option[ScalismoUI],
               log: File,
               initialParameters: Option[ModelFittingParameters] = None
             ): LineMesh[_2D] = {

    val samplingRegistration = new SamplingRegistration(
      model,
      targetMesh,
      showModel,
      modelUiUpdateInterval = 100,
      acceptInfoPrintInterval = 500
    )
    val t0 = System.currentTimeMillis()

    val best = samplingRegistration.runfitting(
      evaluator,
      proposal,
      numOfIterations,
      initialModelParameters = initialParameters,
      jsonName = log
    )

    val t1 = System.currentTimeMillis()
    println(s"ICP-Timing: ${(t1 - t0) / 1000.0} sec")
    //    ModelFittingParameters.transformedMesh(model, best)
    model.instance(best.shapeParameters.parameters)
  }
}
