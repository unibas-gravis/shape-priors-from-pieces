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

import java.awt.Color
import java.io.File

import api.other.TargetSampling
import api.sampling._
import api.sampling.evaluators.TargetToModelEvaluation
import scalismo.geometry._3D
import scalismo.io.MeshIO
import scalismo.mesh.{TriangleMesh, TriangleMesh3D}
import scalismo.sampling.DistributionEvaluator
import scalismo.sampling.proposals.MixtureProposal.ProposalGeneratorWithTransition
import scalismo.statisticalmodel.StatisticalMeshModel
import scalismo.ui.api.{ScalismoUI, ScalismoUIHeadless, StatisticalMeshModelViewControls}

object RegistrationSingle {

  def fitting(model: StatisticalMeshModel, targetMesh: TriangleMesh3D, evaluator: Map[String, DistributionEvaluator[ModelFittingParameters]], proposal: ProposalGeneratorWithTransition[ModelFittingParameters], numOfIterations: Int, showModel: Option[StatisticalMeshModelViewControls], log: File, initialParameters: Option[ModelFittingParameters] = None): TriangleMesh[_3D] = {

    val samplingRegistration = new SamplingRegistration(model, targetMesh, showModel, modelUiUpdateInterval = 10, acceptInfoPrintInterval = 100)
    val t0 = System.currentTimeMillis()

    val best = samplingRegistration.runfitting(evaluator, proposal, numOfIterations, initialModelParameters = initialParameters, jsonName = log)

    val t1 = System.currentTimeMillis()
    println(s"ICP-Timing: ${(t1 - t0) / 1000.0} sec")
    ModelFittingParameters.transformedMesh(model, best)
  }


  def loadTargetAndFit(targetMesh: TriangleMesh3D, completeTarget: TriangleMesh3D, likelihoodUncertainty: Double, log: File, showUI: Boolean, numOfSamples: Int = 10000): Unit = {
    val (model, _) = LoadData.model()

    val numOfEvaluatorPoints = math.min(model.referenceMesh.pointSet.numberOfPoints / 2, targetMesh.pointSet.numberOfPoints / 2) // Used for the likelihood evaluator
    val numOfICPPointSamples = model.rank * 2 // Used for the ICP proposal

    val evaluator = ProductEvaluators.proximityAndIndependent(model, targetMesh, TargetToModelEvaluation, uncertainty = likelihoodUncertainty, numberOfEvaluationPoints = numOfEvaluatorPoints)
    val proposalIcp = MixedProposalDistributions.mixedProposalICP(model, targetMesh, numOfICPPointSamples, projectionDirection = TargetSampling, stepLength = 0.5)

    val ui = if (showUI) ScalismoUI() else ScalismoUIHeadless()
    val modelGroup = ui.createGroup("model")
    val showModel = ui.show(modelGroup, model, "model")

    val targetGroup = ui.createGroup("target")
    ui.show(targetGroup, targetMesh, "targetMesh").color = Color.RED
    ui.show(targetGroup, completeTarget, "completeTarget").color = Color.ORANGE
    val bestGroup = ui.createGroup("best")
    val best = fitting(model, targetMesh, evaluator, proposalIcp, numOfSamples, Option(showModel), log)
    ui.show(bestGroup, best, "best")
  }

  def main(args: Array[String]): Unit = {
    scalismo.initialize()

    println(s"Starting Metropolis Hastings registrations with ICP-proposal!")

    val groundTruthPath = new File(Paths.generalPath, "aligned/meshes/")
    val partialPath = new File(Paths.generalPath, "partialMeshes/")
    val logPath = new File(Paths.generalPath, "logs")
    logPath.mkdirs()

    val partialIndex = 0
    val uncertainty = 4

    val targetMesh = MeshIO.readMesh(new File(partialPath, s"${partialIndex}.stl")).get
    val completeTargetMesh = MeshIO.readMesh(new File(groundTruthPath, s"${partialIndex}.stl")).get

    val log = new File(logPath, s"${partialIndex}_${uncertainty}_log.json")

    loadTargetAndFit(targetMesh, completeTargetMesh, uncertainty, log, showUI = true)
  }
}
