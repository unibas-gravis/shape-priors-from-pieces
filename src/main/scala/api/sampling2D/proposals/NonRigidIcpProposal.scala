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

package api.sampling2D.proposals

import api.other.{DoubleProjection, IcpProjectionDirection, ModelSampling, TargetSampling}
import api.sampling2D.{ModelFittingParameters, ShapeParameters, SurfaceNoiseHelpers}
import breeze.linalg.{DenseVector, diag}
import scalismo.common.interpolation.NearestNeighborInterpolator
import scalismo.common.{Field, PointId}
import scalismo.geometry._
import scalismo.mesh.{LineMesh, LineMesh2D}
import scalismo.sampling.{ProposalGenerator, TransitionProbability}
import scalismo.statisticalmodel.{LowRankGaussianProcess, MultivariateNormalDistribution, PointDistributionModel}
import scalismo.utils.Memoize

case class NonRigidIcpProposal(
                                model: PointDistributionModel[_2D, LineMesh],
                                target: LineMesh2D,
                                modelLMs: Seq[Landmark[_2D]],
                                targetLMs: Seq[Landmark[_2D]],
                                stepLength: Double,
                                tangentialNoise: Double,
                                noiseAlongNormal: Double,
                                numOfSamplePoints: Int,
                                projectionDirection: IcpProjectionDirection = ModelSampling,
                                boundaryAware: Boolean = true,
                                useLandmarkCorrespondence: Boolean = false,
                                generatedBy: String = "ShapeIcpProposal"
                              )(
                                implicit rand: scalismo.utils.Random
                              ) extends ProposalGenerator[ModelFittingParameters]
  with TransitionProbability[ModelFittingParameters] {

  private lazy val interpolatedModel = model.gp.interpolate(NearestNeighborInterpolator())
  private val commonNames: Seq[String] = modelLMs.map(_.id) intersect targetLMs.map(_.id)
  private val landmarksPairs: Seq[(Landmark[_2D], Landmark[_2D])] = commonNames.map(name => (modelLMs.find(_.id == name).get, targetLMs.find(_.id == name).get))

  private val correspondenceLandmarkPoints: IndexedSeq[(PointId, Point[_2D], MultivariateNormalDistribution, Boolean, Double)] = landmarksPairs.map(f => (model.reference.pointSet.findClosestPoint(f._1.point).id, f._2.point, f._1.uncertainty.getOrElse(MultivariateNormalDistribution(DenseVector.zeros[Double](2), diag(DenseVector.ones[Double](2)))), false, 0.0)).toIndexedSeq
  private val referenceMesh = model.reference
  private val cashedPosterior: Memoize[ModelFittingParameters, LowRankGaussianProcess[_2D, EuclideanVector[_2D]]] = Memoize(icpPosterior, 20)
  private val modelIds: IndexedSeq[PointId] = scala.util.Random.shuffle(model.reference.pointSet.pointIds.toIndexedSeq).take(numOfSamplePoints)
  private val targetPoints2Dids: IndexedSeq[PointId] = scala.util.Random.shuffle(target.pointSet.pointIds.toIndexedSeq).take(numOfSamplePoints)

  override def propose(theta: ModelFittingParameters): ModelFittingParameters = {
    val posterior = cashedPosterior(theta)
    val proposed: Field[_2D, EuclideanVector[_2D]] = posterior.sample()

    def f(pt: Point[_2D]): Point[_2D] = pt + proposed(pt)

    val newCoefficients = model.coefficients(referenceMesh.transform(f))

    val currentShapeCoefficients = theta.shapeParameters.parameters
    val newShapeCoefficients = currentShapeCoefficients + (newCoefficients - currentShapeCoefficients) * stepLength

    theta.copy(
      shapeParameters = ShapeParameters(newShapeCoefficients),
      generatedBy = generatedBy
    )
  }


  override def logTransitionProbability(from: ModelFittingParameters, to: ModelFittingParameters): Double = {
    val pos = cashedPosterior(from)
    val posterior = PointDistributionModel(referenceMesh, pos)

    val compensatedTo = from.shapeParameters.parameters + ((to.shapeParameters.parameters - from.shapeParameters.parameters) / stepLength)
    val toMesh = model.instance(compensatedTo)

    val projectedTo = posterior.coefficients(toMesh)
    val pdf = pos.logpdf(projectedTo)
    println(s"LogTransition ${pdf}")
    pdf
  }


  private def icpPosterior(theta: ModelFittingParameters): LowRankGaussianProcess[_2D, EuclideanVector[_2D]] = {
    def modelBasedDoubleProjectionClosestPointsEstimation(
                                                           currentMesh: LineMesh[_2D] //,
                                                           //inversePoseTransform: RigidTransformation[_2D]
                                                         ): IndexedSeq[(Point[_2D], EuclideanVector[_2D], MultivariateNormalDistribution)] = {

      var idSet: Set[PointId] = Set()

      val noisyCorrespondence = modelIds.map {
        idInit =>
          val currentMeshPoint = currentMesh.pointSet.point(idInit)
          val targetPoint = target.pointSet.findClosestPoint(currentMeshPoint)

          val distance = (currentMeshPoint - targetPoint.point).norm2

          val id = currentMesh.pointSet.findClosestPoint(targetPoint.point).id
          val modelNormal2D = currentMesh.vertexNormals(id)

          val targetNormal = target.vertexNormals(targetPoint.id)
          val isOnBoundary = idSet.contains(id) || (modelNormal2D dot targetNormal) <= 0 || (target.topology.adjacentPointsForPoint(targetPoint.id).length < 2)

          idSet += id

          val noiseDistribution = SurfaceNoiseHelpers.surfaceNormalDependantNoise2D(modelNormal2D, noiseAlongNormal, tangentialNoise)
          (id, targetPoint.point, noiseDistribution, isOnBoundary, distance)
      }

      val correspondenceFilteredInit = if (boundaryAware) noisyCorrespondence.filter(!_._4) else noisyCorrespondence
      val correspondenceFiltered = if (useLandmarkCorrespondence) correspondenceFilteredInit ++ correspondenceLandmarkPoints else correspondenceFilteredInit

      for ((pointId, targetPoint, uncertainty, _, _) <- correspondenceFiltered) yield {
        val referencePoint = model.reference.pointSet.point(pointId)
        (referencePoint, targetPoint - referencePoint, uncertainty)
      }
    }

    def modelBasedClosestPointsEstimation(currentMesh: LineMesh[_2D]): IndexedSeq[(Point[_2D], EuclideanVector[_2D], MultivariateNormalDistribution)] = {

      val noisyCorrespondence = modelIds.map {
        id =>
          val currentMeshPoint = currentMesh.pointSet.point(id)
          val targetPoint = target.pointSet.findClosestPoint(currentMeshPoint)

          val distance = (currentMeshPoint - targetPoint.point).norm2

          val modelNormal2D = currentMesh.vertexNormals(id)
          // is on boundary or normals pointing opposite directions
          val targetNormal = target.vertexNormals(targetPoint.id)
          val isOnBoundary = (modelNormal2D dot targetNormal) <= 0 || (target.topology.adjacentPointsForPoint(targetPoint.id).length < 2)

          val noiseDistribution = SurfaceNoiseHelpers.surfaceNormalDependantNoise2D(modelNormal2D, noiseAlongNormal, tangentialNoise)
          (id, targetPoint.point, noiseDistribution, isOnBoundary, distance)
      }

      val correspondenceFilteredInit = if (boundaryAware) noisyCorrespondence.filter(!_._4) else noisyCorrespondence
      val correspondenceFiltered = if (useLandmarkCorrespondence) correspondenceFilteredInit ++ correspondenceLandmarkPoints else correspondenceFilteredInit

      for ((pointId, targetPoint, uncertainty, _, _) <- correspondenceFiltered) yield {
        val referencePoint = model.reference.pointSet.point(pointId)
        (referencePoint, targetPoint - referencePoint, uncertainty)
      }
    }


    def targetBasedClosestPointsEstimation(currentMesh: LineMesh[_2D]): IndexedSeq[(Point[_2D], EuclideanVector[_2D], MultivariateNormalDistribution)] = {
      val noisyCorrespondence = targetPoints2Dids.map { targetPointID =>
        val targetPoint = target.pointSet.point(targetPointID)

        val id = currentMesh.pointSet.findClosestPoint(targetPoint).id

        val distance = (targetPoint - currentMesh.pointSet.point(id)).norm2

        val modelNormal2D = currentMesh.vertexNormals(id)
        val targetNormal = target.vertexNormals(targetPointID)
        val isOnBoundary = (modelNormal2D dot targetNormal) <= 0 || (target.topology.adjacentPointsForPoint(targetPointID).length < 2)

        val noiseDistribution = SurfaceNoiseHelpers.surfaceNormalDependantNoise2D(modelNormal2D, noiseAlongNormal, tangentialNoise)
        (id, targetPoint, noiseDistribution, isOnBoundary, distance)
      }

      val correspondencesAll = if (boundaryAware) noisyCorrespondence.filter(!_._4) else noisyCorrespondence
      val correspondenceWithLM = if (useLandmarkCorrespondence) correspondencesAll ++ correspondenceLandmarkPoints else correspondencesAll


      val distinctPointIds = correspondenceWithLM.map(_._1).distinct
      val avgDist = correspondenceWithLM.map(_._5).sum/correspondenceWithLM.length

      val disCorrFilter = distinctPointIds.map(id => correspondenceWithLM.filter(_._1==id).minBy(_._5)).filter(_._5 <= avgDist)

      println(s"Corr points used: ${disCorrFilter.length}")

      for ((pointId, targetPoint, uncertainty, _, _) <- disCorrFilter) yield {
        val referencePoint = model.reference.pointSet.point(pointId)
        // (reference point, deformation vector in model space starting from reference, usually zero-mean observation uncertainty)
        (referencePoint, targetPoint - referencePoint, uncertainty)
      }
    }

    /**
     * Estimate where points should move to together with a surface normal dependant noise.
     *
     * @param theta Current fitting parameters
     * @return List of points, with associated deformation and normal dependant surface noise.
     */
    def uncertainDisplacementEstimation(theta: ModelFittingParameters): IndexedSeq[(Point[_2D], EuclideanVector[_2D], MultivariateNormalDistribution)] = {
      val currentMesh = ModelFittingParameters.transformedMesh(model, theta)
      if (projectionDirection == DoubleProjection) {
        modelBasedDoubleProjectionClosestPointsEstimation(currentMesh)
      }
      else if (projectionDirection == TargetSampling) {
        targetBasedClosestPointsEstimation(currentMesh)
      } else {
        modelBasedClosestPointsEstimation(currentMesh)
      }
    }

    val uncertainDisplacements: IndexedSeq[(Point[_2D], EuclideanVector[_2D], MultivariateNormalDistribution)] = uncertainDisplacementEstimation(theta)
    interpolatedModel.posterior(uncertainDisplacements)
  }
}