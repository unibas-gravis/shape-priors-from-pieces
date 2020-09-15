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

import api.other.LineMeshMetrics2D
import api.sampling.ModelFittingParameters
import breeze.stats.distributions.ContinuousDistr
import scalismo.mesh.LineMesh2D
import scalismo.sampling.DistributionEvaluator
import scalismo.statisticalmodel.StatisticalLineMeshModel

case class HausdorffDistanceEvaluator(model: StatisticalLineMeshModel,
                                      targetMesh: LineMesh2D,
                                      likelihoodModel: ContinuousDistr[Double]
                                     )
  extends DistributionEvaluator[ModelFittingParameters] with EvaluationCaching {

  def computeLogValue(sample: ModelFittingParameters): Double = {
    val currentSample = model.instance(sample.shapeParameters.parameters) //ModelFittingParameters.transformedMesh(model, sample)
    val hd = LineMeshMetrics2D.hausdorffDistance(currentSample, targetMesh)
    likelihoodModel.logPdf(hd)
  }
}