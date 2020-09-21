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

import api.sampling2D.evaluators._
import scalismo.geometry._2D
import scalismo.mesh.LineMesh
import scalismo.sampling.DistributionEvaluator
import scalismo.sampling.evaluators.ProductEvaluator
import scalismo.statisticalmodel.PointDistributionModel

object ProductEvaluators {

  def proximityAndIndependent(model: PointDistributionModel[_2D, LineMesh], target: LineMesh[_2D], evaluationMode: EvaluationMode, uncertainty: Double = 1.0, numberOfEvaluationPoints: Int = 100): Map[String, DistributionEvaluator[ModelFittingParameters]] = {
    val likelihoodIndependent = breeze.stats.distributions.Gaussian(0, uncertainty)

    val indepPointEval = IndependentPointDistanceEvaluator(model, target, likelihoodIndependent, evaluationMode, numberOfEvaluationPoints)
    val evalProximity = ModelPriorEvaluator(model)

    val evaluator = ProductEvaluator(
      evalProximity,
      indepPointEval
    )

    val evaluatorMap: Map[String, DistributionEvaluator[ModelFittingParameters]] = Map(
      "product" -> evaluator,
      "prior" -> evalProximity,
      "distance" -> indepPointEval
    )
    evaluatorMap
  }
}
