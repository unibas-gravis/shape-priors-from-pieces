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

import java.awt.Color
import java.io.File

import api.other.{ModelAndTargetSampling, ModelSampling, RegistrationComparison, TargetSampling}
import api.sampling2D.evaluators.{ModelToTargetEvaluation, TargetToModelEvaluation}
import api.sampling2D.loggers.{JSONAcceptRejectLogger, jsonLogFormat}
import api.sampling2D.{MixedProposalDistributions, ModelFittingParameters, ProductEvaluators, SamplingRegistration}
import apps.scalismoExtension.FormatConverter
import apps.util.{LogHelper2D, Visualization2DHelper}
import breeze.linalg.DenseVector
import scalismo.geometry._
import scalismo.io.{LandmarkIO, MeshIO}
import scalismo.mesh._
import scalismo.sampling.proposals.MixtureProposal
import scalismo.sampling.proposals.MixtureProposal.ProposalGeneratorWithTransition
import scalismo.statisticalmodel.PointDistributionModel
import scalismo.ui.api.SimpleAPI
import scalismo.utils.Random.implicits._

case class HandRegistration(model: PointDistributionModel[_2D, LineMesh], modelLM: Seq[Landmark[_2D]], targetGTFile: File, targetFile: File, targetLMFile: File, logPath: File) {

  println(s"Registration of target mesh: ${targetFile.getName}")
  private val referenceLineMesh = model.reference
  private val referenceLineMesh3D = FormatConverter.lineMesh2Dto3D(referenceLineMesh)

  private val targetGTMesh = MeshIO.readLineMesh2D(targetGTFile).get
  private val targetMesh = MeshIO.readLineMesh2D(targetFile).get

  private val targetLM = LandmarkIO.readLandmarksJson2D(targetLMFile).get

  private val numOfEvaluatorPoints = model.reference.pointSet.numberOfPoints/2 // Used for the likelihood evaluator
  private val numOfICPPointSamples = math.min(model.rank*2, model.reference.pointSet.numberOfPoints/2) // Used for the ICP proposal

//  private val proposalLm: ProposalGeneratorWithTransition[ModelFittingParameters] =
//    MixedProposalDistributions.mixedProposalICP(
//      model,
//      targetMesh,
//      modelLM,
//      targetLM,
//      0,
//      projectionDirection = TargetSampling,
//      tangentialNoise = 5.0,
//      noiseAlongNormal = 5.0,
//      stepLength = 0.5,
//      boundaryAware = true,
//      useLandmarkCorrespondence = true
//    )
//  private val proposalICP: ProposalGeneratorWithTransition[ModelFittingParameters] =
//    MixedProposalDistributions.mixedProposalICP(
//      model,
//      targetMesh,
//      modelLM,
//      targetLM,
//      numOfICPPointSamples,
//      projectionDirection = TargetSampling,
//      tangentialNoise = 10.0,
//      noiseAlongNormal = 2.0,
//      stepLength = 0.5,
//      boundaryAware = true,
//      useLandmarkCorrespondence = false
//    )
  private val proposalICPLast: ProposalGeneratorWithTransition[ModelFittingParameters] =
    MixedProposalDistributions.mixedProposalICP(
      model,
      targetMesh,
      modelLM,
      targetLM,
      numOfICPPointSamples,
      projectionDirection = TargetSampling,
      tangentialNoise = 4.0,
      noiseAlongNormal = 4.0,
      stepLength = 0.1,
      boundaryAware = true,
      useLandmarkCorrespondence = false
    )
//  private val proposalRND: ProposalGeneratorWithTransition[ModelFittingParameters] = MixedProposalDistributions.mixedProposalRandom(model)
//  private val proposal: ProposalGeneratorWithTransition[ModelFittingParameters] = MixtureProposal(Seq((0.8, proposalICP), (0.2, proposalRND)))

//  private val evaluatorInitial = ProductEvaluators.proximityAndIndependent(
//    model,
//    targetMesh,
//    TargetToModelEvaluation,
//    uncertainty = 2.0,
//    numberOfEvaluationPoints = numOfEvaluatorPoints
//  )
//
//  private val evaluator = ProductEvaluators.proximityAndIndependent(
//    model,
//    targetMesh,
//    TargetToModelEvaluation,
//    uncertainty = 4.0,
//    numberOfEvaluationPoints = numOfEvaluatorPoints
//  )
  private val evaluatorLast = ProductEvaluators.proximityAndIndependent(
    model,
    targetMesh,
    TargetToModelEvaluation,
    uncertainty = 4.0,
    numberOfEvaluationPoints = numOfEvaluatorPoints
  )

  def run(ui: SimpleAPI, numOfSamples: Int = 1000, initialParameters: Option[ModelFittingParameters] = None, showNormals: Boolean = false): Unit = {


    val modelGroup = ui.createGroup("model")
    ui.show(modelGroup, referenceLineMesh3D, "model-reference")

    val targetGroup = ui.createGroup("target")
    val showGT = Visualization2DHelper.show2DLineMesh(ui, targetGroup, targetGTMesh, "Ground-truth")
    val showTarget = Visualization2DHelper.show2DLineMesh(ui, targetGroup, targetMesh, targetFile.getName)
    showGT.color = Color.ORANGE
    showTarget.color = Color.RED

    if(showNormals){
      Visualization2DHelper.show2DLineMeshNormals(ui, modelGroup, model.reference, "normals")
      Visualization2DHelper.show2DLineMeshNormals(ui, targetGroup, targetMesh, "normals")
      Visualization2DHelper.show2DLineMeshNormals(ui, targetGroup, targetGTMesh, "normals")
    }

    val finalGroup = ui.createGroup("finalGroup")


    val logObj = new JSONAcceptRejectLogger[ModelFittingParameters](logPath)
    val bestPars = logObj.getBestFittingParsFromJSON

    val samplingRegistration = new SamplingRegistration(
      model,
      targetMesh,
      Option(ui),
      modelUiUpdateInterval = 10,
      acceptInfoPrintInterval = 20
    )
    val t0 = System.currentTimeMillis()

//    val lmFit = samplingRegistration.runfitting(
//      evaluatorInitial,
//      proposalLm,
//      50,
//      initialModelParameters = initialParameters,
//      jsonName = new File("tmp.json")
//    )
//    val bestInit = samplingRegistration.runfitting(
//      evaluator,
//      proposal,
//      numOfSamples,
//      initialModelParameters = Option(lmFit),
//      jsonName = new File("tmp.json")
//    )
    val best = samplingRegistration.runfitting(
      evaluatorLast,
      proposalICPLast,
      numOfSamples,
      initialModelParameters = Option(bestPars),
      jsonName = new File("tmp.json")
    )

    val t1 = System.currentTimeMillis()
    println(s"ICP-Timing: ${(t1 - t0) / 1000.0} sec")
    val bestMesh = ModelFittingParameters.transformedMesh(model, best)

    Visualization2DHelper.show2DLineMesh(ui, finalGroup, bestMesh, "best-fit")
    RegistrationComparison.evaluateReconstruction2GroundTruth("SAMPLE", targetMesh, bestMesh)

  }

}
