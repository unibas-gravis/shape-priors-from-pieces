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
import apps.hands.preprocessing.Create2dGPmodel.DummySampler2D
import breeze.stats.distributions.ContinuousDistr
import scalismo.common.PointId
import scalismo.geometry.{Point, _2D}
import scalismo.mesh.LineMesh
import scalismo.sampling.DistributionEvaluator
import scalismo.statisticalmodel.StatisticalLineMeshModel
import scalismo.utils.Random

case class CollectiveAverageHausdorffDistanceBoundaryAwareEvaluator(model: StatisticalLineMeshModel,
                                                                    targetMesh: LineMesh[_2D],
                                                                    likelihoodModelAvg: ContinuousDistr[Double],
                                                                    likelihoodModelMax: ContinuousDistr[Double],
                                                                    evaluationMode: EvaluationMode,
                                                                    numberOfPointsForComparison: Int)(implicit random: Random)
  extends DistributionEvaluator[ModelFittingParameters] with EvaluationCaching {

  private val randomPointIdsOnModel = getRandomPointIdsOnModel
  private val randomPointsOnTarget = getRandomPointsOnTarget

  def getRandomPointIdsOnModel: IndexedSeq[PointId] = {
    if (numberOfPointsForComparison >= model.referenceMesh.pointSet.numberOfPoints) {
      model.referenceMesh.pointSet.pointIds.toIndexedSeq
    }
    else {
      DummySampler2D(model.referenceMesh, numberOfPointsForComparison).sample().map(_._1)
        .map(p => model.referenceMesh.pointSet.findClosestPoint(p).id)
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

  def hausdorffDistance(m1: LineMesh[_2D], m2: LineMesh[_2D]): Double = {
    def allDistsBetweenMeshes(mm1: LineMesh[_2D], mm2: LineMesh[_2D]): Iterator[Double] = {
      for (ptM1 <- mm1.pointSet.points) yield {
        val cpM2 = mm2.pointSet.findClosestPoint(ptM1).point
        (ptM1 - cpM2).norm
      }
    }

    val d1 = allDistsBetweenMeshes(m1, m2)
    val d2 = allDistsBetweenMeshes(m2, m1)

    Math.max(d1.max, d2.max)
  }

  def computeLogValue(sample: ModelFittingParameters): Double = {
    val currentSample = model.instance(sample.shapeParameters.parameters) //ModelFittingParameters.transformedMesh(model, sample)
    val dist = evaluationMode match {
      case ModelToTargetEvaluation => avgDistModelToTarget(currentSample, targetMesh)
      case TargetToModelEvaluation => avgDistTargetToModel(currentSample, targetMesh)
      case SymmetricEvaluation => {
        val m2t = avgDistModelToTarget(currentSample, targetMesh)
        val t2m = avgDistTargetToModel(currentSample, targetMesh)
        (0.5 * m2t._1 + 0.5 * t2m._1, math.max(m2t._2, t2m._2))
      }
    }
    likelihoodModelAvg.logPdf(dist._1) + likelihoodModelMax.logPdf(dist._2)
  }

  def avgDistModelToTarget(modelSample: LineMesh[_2D], targetMesh: LineMesh[_2D]): (Double, Double) = {

    val pointsOnSample = randomPointIdsOnModel.map(modelSample.pointSet.point)
    val dists = for (p <- pointsOnSample) yield {
      val pTarget = targetMesh.pointSet.findClosestPoint(p).point
      //      val pTargetId = targetMesh.pointSet.findClosestPoint(pTarget).id
      //      if (targetMesh.operations.pointIsOnBoundary(pTargetId)) -1.0
      //      else
      (pTarget - p).norm
    }
    val filteredDists = dists.toIndexedSeq.filter(f => f > -1.0)
    (filteredDists.sum / filteredDists.size, filteredDists.max)
  }

  def avgDistTargetToModel(modelSample: LineMesh[_2D], targetMesh: LineMesh[_2D]): (Double, Double) = {

    val dists = for (p <- randomPointsOnTarget) yield {
      val pSample = modelSample.pointSet.findClosestPoint(p).point
      //      val pTargetId = modelSample.pointSet.findClosestPoint(pSample).id
      //      if (targetMesh.operations.pointIsOnBoundary(pTargetId)) -1.0
      //      else
      (pSample - p).norm
    }
    val filteredDists = dists.toIndexedSeq.filter(f => f > -1.0)
    (filteredDists.sum / filteredDists.size, filteredDists.max)
  }
}