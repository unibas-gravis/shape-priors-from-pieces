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

package api.sampling.proposals

import api.other.{DoubleProjection, IcpProjectionDirection, ModelSampling, TargetSampling}
import api.sampling.{ModelFittingParameters, ShapeParameters, SurfaceNoiseHelpers}
import apps.scalismoExtension.LineMeshOperator
import breeze.linalg.{DenseMatrix, DenseVector, diag}
import scalismo.common.{Field, NearestNeighborInterpolator, PointId}
import scalismo.geometry._
import scalismo.mesh.{LineMesh, LineMesh2D}
import scalismo.sampling.{ProposalGenerator, TransitionProbability}
import scalismo.statisticalmodel.{LowRankGaussianProcess, MultivariateNormalDistribution, StatisticalLineMeshModel, StatisticalMeshModel}
import scalismo.utils.Memoize

case class NonRigidIcpProposal(
                                model: StatisticalLineMeshModel,
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
  println(s"NonRigidICPProposal initializing, sampling: ${numOfSamplePoints} points")
  private val referenceMesh = model.referenceMesh
  private val cashedPosterior: Memoize[ModelFittingParameters, LowRankGaussianProcess[_2D, EuclideanVector[_2D]]] = Memoize(icpPosterior, 20)

  private lazy val interpolatedModel = model.gp.interpolate(NearestNeighborInterpolator())

//  private val targetMesh = TriangleMesh3D(LineMeshConverter.PointDomain2Dto3D(target.pointSet).points.toIndexedSeq, TriangleList(IndexedSeq()))

//  private val modelRefLine2D: LineMesh2D = model.referenceMesh//LineMeshConverter.pointCloudto2DLineMesh(model.referenceMesh.pointSet)
  private val modelIds: IndexedSeq[PointId] = scala.util.Random.shuffle(model.referenceMesh.pointSet.pointIds.toIndexedSeq).take(numOfSamplePoints)
//  private val targetPoints2D: IndexedSeq[Point[_2D]] = scala.util.Random.shuffle(target.pointSet.points.toIndexedSeq).take(numOfSamplePoints)
  private val targetPoints2Dids: IndexedSeq[PointId] = scala.util.Random.shuffle(target.pointSet.pointIds.toIndexedSeq).take(numOfSamplePoints)

  val commonNames = modelLMs.map(_.id) intersect targetLMs.map(_.id)
  val landmarksPairs = commonNames.map(name => (modelLMs.find(_.id == name).get, targetLMs.find(_.id == name).get))
  val correspondenceLandmarkPoints: IndexedSeq[(PointId, Point[_2D], MultivariateNormalDistribution, Boolean)] = landmarksPairs.map(f => (model.referenceMesh.pointSet.findClosestPoint(f._1.point).id, f._2.point,  MultivariateNormalDistribution(DenseVector.zeros[Double](2), diag(DenseVector.ones[Double](2))), false)).toIndexedSeq

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
// This is the right way to write the log transition - but doesn't work atm. for 2D linemeshes
//    val pos = cashedPosterior(from)
//    val posterior = StatisticalLineMeshModel.apply(referenceMesh, pos)
//
//    val compensatedTo = from.shapeParameters.parameters + (to.shapeParameters.parameters - from.shapeParameters.parameters) / stepLength
//    val toMesh = model.instance(compensatedTo)
//
//    val projectedTo = posterior.coefficients(toMesh)
//    pos.logpdf(projectedTo)
//
    val posterior = cashedPosterior(from)

    val compensatedTo = to.copy(shapeParameters = ShapeParameters(from.shapeParameters.parameters + (to.shapeParameters.parameters - from.shapeParameters.parameters) / stepLength))

    val pdf = posterior.logpdf(compensatedTo.shapeParameters.parameters)
    pdf
  }

  private def icpPosterior(theta: ModelFittingParameters): LowRankGaussianProcess[_2D, EuclideanVector[_2D]] = {
    def modelBasedDoubleProjectionClosestPointsEstimation(
                                                           currentMesh: LineMesh[_2D]//,
                                                           //inversePoseTransform: RigidTransformation[_2D]
                                                         ): IndexedSeq[(Point[_2D], EuclideanVector[_2D], MultivariateNormalDistribution)] = {

      var idSet: Set[PointId] = Set()

      val noisyCorrespondence = modelIds.map {
        idInit =>
          val currentMeshPoint = currentMesh.pointSet.point(idInit)
          val targetPoint = target.pointSet.findClosestPoint(currentMeshPoint)

          val id = currentMesh.pointSet.findClosestPoint(targetPoint.point).id
          val modelNormal2D = currentMesh.vertexNormals(id)
          val normlen = modelNormal2D.norm

          val targetNormalInit = target.vertexNormals(targetPoint.id)
          val targetNormal = if (LineMeshOperator(target).verifyNormalDirection(targetPoint.point, targetNormalInit)) targetNormalInit else targetNormalInit.*(-1.0)
          val isOnBoundary = idSet.contains(id) || (modelNormal2D dot targetNormal) <= 0 || (target.topology.adjacentPointsForPoint(targetPoint.id).length < 2)

//          val isOnBoundary = idSet.contains(id) || normlen == 0.0
          idSet += id

          val noiseDistribution = SurfaceNoiseHelpers.surfaceNormalDependantNoise2D(modelNormal2D, noiseAlongNormal, tangentialNoise)
          (id, targetPoint.point, noiseDistribution, isOnBoundary)
      }

      val correspondenceFilteredInit = if (boundaryAware) noisyCorrespondence.filter(!_._4) else noisyCorrespondence
      val correspondenceFiltered = if(useLandmarkCorrespondence) correspondenceFilteredInit ++ correspondenceLandmarkPoints else correspondenceFilteredInit
      //      println(s"ModelBasedClosestPoint, correspondence ${correspondenceFiltered.length}")

      for ((pointId, targetPoint, uncertainty, _) <- correspondenceFiltered) yield {
        val referencePoint = model.referenceMesh.pointSet.point(pointId)
        (referencePoint, targetPoint - referencePoint, uncertainty)
      }
    }

    def modelBasedClosestPointsEstimation(
                                           currentMesh: LineMesh[_2D]//,
                                           //inversePoseTransform: RigidTransformation[_2D]
                                         ): IndexedSeq[(Point[_2D], EuclideanVector[_2D], MultivariateNormalDistribution)] = {

      //      val modelPoints = UniformMeshSampler3D(model.referenceMesh, numOfSamplePoints).sample().map(_._1)
      //      val modelIds: IndexedSeq[PointId] = modelPoints.map(p => model.referenceMesh.pointSet.findClosestPoint(p).id)

      val noisyCorrespondence = modelIds.map {
        id =>
          val currentMeshPoint = currentMesh.pointSet.point(id)
          val targetPoint = target.pointSet.findClosestPoint(currentMeshPoint)
          val modelNormal2D = currentMesh.vertexNormals(id)
          // is on boundary or normals pointing opposite directions
          val targetNormalInit = target.vertexNormals(targetPoint.id)
          val targetNormal = if (LineMeshOperator(target).verifyNormalDirection(targetPoint.point, targetNormalInit)) targetNormalInit else targetNormalInit.*(-1.0)
          val isOnBoundary = (modelNormal2D dot targetNormal) <= 0 || (target.topology.adjacentPointsForPoint(targetPoint.id).length < 2) // false //target.operations.pointIsOnBoundary(targetPointId)

          val noiseDistribution = SurfaceNoiseHelpers.surfaceNormalDependantNoise2D(modelNormal2D, noiseAlongNormal, tangentialNoise)
          (id, targetPoint.point, noiseDistribution, isOnBoundary)
      }

      val correspondenceFilteredInit = if (boundaryAware) noisyCorrespondence.filter(!_._4) else noisyCorrespondence
      val correspondenceFiltered = if(useLandmarkCorrespondence)correspondenceFilteredInit ++ correspondenceLandmarkPoints else correspondenceFilteredInit

      for ((pointId, targetPoint, uncertainty, _) <- correspondenceFiltered) yield {
        val referencePoint = model.referenceMesh.pointSet.point(pointId)
        (referencePoint, targetPoint - referencePoint, uncertainty)
      }
    }


    def targetBasedClosestPointsEstimation(
                                            currentMesh: LineMesh[_2D]//,
                                            //inversePoseTransform: RigidTransformation[_2D]
                                          ): IndexedSeq[(Point[_2D], EuclideanVector[_2D], MultivariateNormalDistribution)] = {


      val noisyCorrespondence = targetPoints2Dids.map { targetPointID =>
        val targetPoint = target.pointSet.point(targetPointID)

        val id = currentMesh.pointSet.findClosestPoint(targetPoint).id

        val modelNormal2D = currentMesh.vertexNormals(id)
        val targetNormalInit = target.vertexNormals(targetPointID)
        val targetNormal = if (LineMeshOperator(target).verifyNormalDirection(targetPoint, targetNormalInit)) targetNormalInit else targetNormalInit.*(-1.0)
        val isOnBoundary = (modelNormal2D dot targetNormal) <= 0 || (target.topology.adjacentPointsForPoint(targetPointID).length < 2) //false //currentMesh.operations.pointIsOnBoundary(id)

        val noiseDistribution = SurfaceNoiseHelpers.surfaceNormalDependantNoise2D(modelNormal2D, noiseAlongNormal, tangentialNoise)
        (id, targetPoint, noiseDistribution, isOnBoundary)
      }

      val correspondenceFilteredInit = if (boundaryAware) noisyCorrespondence.filter(!_._4) else noisyCorrespondence
      val correspondenceFiltered = if(useLandmarkCorrespondence)correspondenceFilteredInit ++ correspondenceLandmarkPoints else correspondenceFilteredInit

      for ((pointId, targetPoint, uncertainty, _) <- correspondenceFiltered) yield {
        val referencePoint = model.referenceMesh.pointSet.point(pointId)
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
    def uncertainDisplacementEstimation(theta: ModelFittingParameters)
    : IndexedSeq[(Point[_2D], EuclideanVector[_2D], MultivariateNormalDistribution)] = {
      val currentMesh = model.instance(theta.shapeParameters.parameters) //ModelFittingParameters.transformedMesh(model, theta)
//      val inversePoseTransform = ModelFittingParameters.poseTransform(theta).inverse

      if (projectionDirection == DoubleProjection) {
        modelBasedDoubleProjectionClosestPointsEstimation(currentMesh)//, inversePoseTransform)
      }
      else if (projectionDirection == TargetSampling) {
        targetBasedClosestPointsEstimation(currentMesh)//, inversePoseTransform)
      } else {
        modelBasedClosestPointsEstimation(currentMesh)//, inversePoseTransform)
      }
    }

    val uncertainDisplacements: IndexedSeq[(Point[_2D], EuclideanVector[_2D], MultivariateNormalDistribution)] = uncertainDisplacementEstimation(theta)
    interpolatedModel.posterior(uncertainDisplacements)
  }

}