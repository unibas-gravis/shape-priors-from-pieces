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

package api.sampling.evaluators

import api.sampling.ModelFittingParameters
import scalismo.common.PointId
import scalismo.geometry.{Point, _2D}
import scalismo.mesh.LineMesh
import scalismo.sampling.DistributionEvaluator
import scalismo.statisticalmodel.PointDistributionModel

case class CorrespondenceEvaluator(model: PointDistributionModel[_2D, LineMesh],
                                   correspondences: Seq[(PointId, Point[_2D])], uncertainty: Double)
  extends DistributionEvaluator[ModelFittingParameters] {

  val likelihoodIndependent = breeze.stats.distributions.Gaussian(0, uncertainty)


  override def logValue(sample: ModelFittingParameters): Double = {

    //    val transform = ModelFittingParameters.poseTransform(sample)
    val currModelInstance = model.instance(sample.shapeParameters.parameters) //.transform(transform)

    val likelihoods = correspondences.map(correspondence => {
      val (id, targetPoint) = correspondence
      val modelInstancePoint = currModelInstance.pointSet.point(id)
      val observedDeformation = (targetPoint - modelInstancePoint).norm

      likelihoodIndependent.logPdf(observedDeformation)
      //      uncertainty.logpdf(observedDeformation.toBreezeVector)
    })

    val loglikelihood = likelihoods.sum
    loglikelihood
  }
}
