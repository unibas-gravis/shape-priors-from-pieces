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

package api.sampling2D.evaluators

import api.sampling2D.ModelFittingParameters
import breeze.stats.distributions.ContinuousDistr
import scalismo.common.PointId
import scalismo.geometry.{Point, _2D}
import scalismo.mesh.LineMesh
import scalismo.numerics.Sampler
import scalismo.sampling.DistributionEvaluator
import scalismo.statisticalmodel.PointDistributionModel

case class IndependentPointDistanceEvaluator(model: PointDistributionModel[_2D, LineMesh],
                                             targetMesh: LineMesh[_2D],
                                             likelihoodModel: ContinuousDistr[Double],
                                             evaluationMode: EvaluationMode,
                                             numberOfPointsForComparison: Int)
  extends DistributionEvaluator[ModelFittingParameters] with EvaluationCaching {

  // Make sure not to oversample if the numberOfPointsForComparison is set higher than the points in the target or the model
  private val randomPointsOnTarget: IndexedSeq[Point[_2D]] = getRandomPointsOnTarget
  private val randomPointIdsOnModel: IndexedSeq[PointId] = getRandomPointIdsOnModel

  case class DummySampler2D(mesh: LineMesh[_2D], numberOfPoints: Int) extends Sampler[_2D] {
    val pIds: IndexedSeq[PointId] = scala.util.Random.shuffle(mesh.pointSet.pointIds.toIndexedSeq).take(numberOfPoints)
    val p = 1.0

    def volumeOfSampleRegion: Double = 1.0

    def sample(): IndexedSeq[(Point[_2D], Double)] = {
      val points = pIds.map(id => mesh.pointSet.point(id)).toIndexedSeq
      points.map(point => (point, p))
    }
  }

  def getRandomPointsOnTarget: IndexedSeq[Point[_2D]] = {
    if (numberOfPointsForComparison >= targetMesh.pointSet.numberOfPoints) {
      targetMesh.pointSet.points.toIndexedSeq
    }
    else {
      DummySampler2D(targetMesh, numberOfPointsForComparison).sample().map(_._1)
    }
  }

  def getRandomPointIdsOnModel: IndexedSeq[PointId] = {
    if (numberOfPointsForComparison >= model.reference.pointSet.numberOfPoints) {
      model.reference.pointSet.pointIds.toIndexedSeq
    }
    else {
      DummySampler2D(model.reference, numberOfPointsForComparison).sample().map(_._1)
        .map(p => model.reference.pointSet.findClosestPoint(p).id)
    }
  }

  def computeLogValue(sample: ModelFittingParameters): Double = {

    val currentSample = model.instance(sample.shapeParameters.parameters) //ModelFittingParameters.transformedMesh(model, sample)
    val dist = evaluationMode match {
      case ModelToTargetEvaluation => distModelToTarget(currentSample)
      case TargetToModelEvaluation => distTargetToModel(currentSample)
      case SymmetricEvaluation => 0.5 * distModelToTarget(currentSample) + 0.5 * distTargetToModel(currentSample)
    }
    dist
  }

  def distModelToTarget(modelSample: LineMesh[_2D]): Double = {
    val pointsOnSample = randomPointIdsOnModel.map(modelSample.pointSet.point)
    val dists = for (pt <- pointsOnSample) yield {
      likelihoodModel.logPdf((targetMesh.pointSet.findClosestPoint(pt).point - pt).norm)
    }
    dists.sum
  }

  def distTargetToModel(modelSample: LineMesh[_2D]): Double = {
    val dists = for (pt <- randomPointsOnTarget) yield {
      likelihoodModel.logPdf((modelSample.pointSet.findClosestPoint(pt).point - pt).norm)
    }
    dists.sum
  }
}



