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

package api.sampling2D

import api.other.{DoubleProjection, IcpProjectionDirection, ModelAndTargetSampling, ModelSampling, TargetSampling}
import api.sampling2D.proposals._
import scalismo.geometry.{Landmark, _2D}
import scalismo.mesh.{LineMesh, LineMesh2D, TriangleMesh3D}
import scalismo.sampling.proposals.MixtureProposal
import scalismo.sampling.proposals.MixtureProposal.ProposalGeneratorWithTransition
import scalismo.sampling.proposals.MixtureProposal.implicits._
import scalismo.statisticalmodel.PointDistributionModel
import scalismo.utils.Random.implicits._

object MixedProposalDistributions {
  TriangleMesh3D

  def mixedProposalRandom(
                           model: PointDistributionModel[_2D, LineMesh]
                         ): ProposalGeneratorWithTransition[ModelFittingParameters] = {
    val mixproposal = MixtureProposal(
      1.0 *: RandomShapeUpdateProposal(model, 0.01, generatedBy = "RandomShape-0.01")
    )
    mixproposal
  }

  def mixedProposalICP(
                        model: PointDistributionModel[_2D, LineMesh],
                        target: LineMesh2D,
                        modelLMs: Seq[Landmark[_2D]],
                        targetLMs: Seq[Landmark[_2D]],
                        numOfSamplePoints: Int,
                        projectionDirection: IcpProjectionDirection = ModelAndTargetSampling,
                        tangentialNoise: Double = 100.0,
                        noiseAlongNormal: Double = 3.0,
                        stepLength: Double = 0.1,
                        boundaryAware: Boolean = true,
                        useLandmarkCorrespondence: Boolean = false): ProposalGeneratorWithTransition[ModelFittingParameters] = {

    val rate = 0.5

    val modelSamplingProposals: Seq[(Double, NonRigidIcpProposal)] = Seq(
      (
        rate,
        NonRigidIcpProposal(
          model,
          target,
          modelLMs,
          targetLMs,
          stepLength,
          tangentialNoise = tangentialNoise,
          noiseAlongNormal = noiseAlongNormal,
          numOfSamplePoints,
          projectionDirection = ModelSampling,
          boundaryAware,
          useLandmarkCorrespondence,
          generatedBy = s"IcpProposal-ModelSampling-${stepLength}Step"
        )
      )
    )

    val targetSamplingProposals: Seq[(Double, NonRigidIcpProposal)] = Seq(
      (
        rate,
        NonRigidIcpProposal(
          model,
          target,
          modelLMs,
          targetLMs,
          stepLength,
          tangentialNoise = tangentialNoise,
          noiseAlongNormal = noiseAlongNormal,
          numOfSamplePoints,
          projectionDirection = TargetSampling,
          boundaryAware,
          useLandmarkCorrespondence,
          generatedBy = s"IcpProposal-TargetSampling-${stepLength}Step"
        )
      )
    )

    val doubleProjectionProposals: Seq[(Double, NonRigidIcpProposal)] = Seq(
      (
        rate,
        NonRigidIcpProposal(
          model,
          target,
          modelLMs,
          targetLMs,
          stepLength,
          tangentialNoise = tangentialNoise,
          noiseAlongNormal = noiseAlongNormal,
          numOfSamplePoints,
          projectionDirection = DoubleProjection,
          boundaryAware,
          generatedBy =
            s"IcpProposal-DoubleProjectionSampling-${stepLength}Step"
        )
      )
    )

    def proposals: Seq[(Double, NonRigidIcpProposal)] = {
      if (projectionDirection == DoubleProjection) {
        doubleProjectionProposals
      } else if (projectionDirection == TargetSampling) {
        targetSamplingProposals
      } else if (projectionDirection == ModelSampling) {
        modelSamplingProposals
      } else {
        targetSamplingProposals ++ modelSamplingProposals
      }
    }

    MixtureProposal.fromProposalsWithTransition(proposals: _*)
  }

}
