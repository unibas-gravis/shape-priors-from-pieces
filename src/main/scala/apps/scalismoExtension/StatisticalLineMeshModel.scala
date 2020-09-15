/*
 * Copyright 2015 University of Basel, Graphics and Vision Research Group
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package scalismo.statisticalmodel

import breeze.linalg.svd.SVD
import breeze.linalg.{DenseMatrix, DenseVector}
import breeze.numerics.sqrt
import scalismo.common._
import scalismo.geometry.EuclideanVector._
import scalismo.geometry._
import scalismo.mesh._
import scalismo.numerics.PivotedCholesky
import scalismo.registration.RigidTransformation
import scalismo.statisticalmodel.DiscreteLowRankGaussianProcess.Eigenpair
import scalismo.statisticalmodel.dataset.DataLineCollection
import scalismo.utils.Random

import scala.util.{Failure, Success, Try}

/**
  * A StatisticalMeshModel is isomorphic to a [[DiscreteLowRankGaussianProcess]]. The difference is that while the DiscreteLowRankGaussianProcess
  * models defomation fields, the StatisticalMeshModel applies the deformation fields to a mesh, and warps the mesh with the deformation fields to
  * produce a new mesh.
  *
  * @see [[DiscreteLowRankGaussianProcess]]
  */
case class StatisticalLineMeshModel private (referenceMesh: LineMesh[_2D], gp: DiscreteLowRankGaussianProcess[_2D, UnstructuredPointsDomain[_2D], EuclideanVector[_2D]]) {
  /** @see [[scalismo.statisticalmodel.DiscreteLowRankGaussianProcess.rank]] */
  val rank = gp.rank

  /**
    * The mean shape
    * @see [[DiscreteLowRankGaussianProcess.mean]]
    */
  lazy val mean: LineMesh[_2D] = warpReference(gp.mean)

  /**
    * The covariance between two points of the  mesh with given point id.
    * @see [[DiscreteLowRankGaussianProcess.cov]]
    */
  def cov(ptId1: PointId, ptId2: PointId) = gp.cov(ptId1, ptId2)

  /**
    * draws a random shape.
    * @see [[DiscreteLowRankGaussianProcess.sample]]
    */
  def sample()(implicit rand: Random) = warpReference(gp.sample())

  /**
    * returns the probability density for an instance of the model
    * @param instanceCoefficients coefficients of the instance in the model. For shapes in correspondence, these can be obtained using the coefficients method
    *
    */
  def pdf(instanceCoefficients: DenseVector[Double]): Double = {
    val disVecField = gp.instance(instanceCoefficients)
    gp.pdf(disVecField)
  }

  /**
    * returns a shape that corresponds to a linear combination of the basis functions with the given coefficients c.
    *  @see [[DiscreteLowRankGaussianProcess.instance]]
    */
  def instance(c: DenseVector[Double]): LineMesh[_2D] = warpReference(gp.instance(c))

//  /**
//    *  Returns a marginal StatisticalLineMeshModel, modelling deformations only on the chosen points of the reference
//    *
//    *  This method proceeds by clipping the reference mesh to keep only the indicated point identifiers, and then marginalizing the
//    *  GP over those points. Notice that when clipping, not all indicated point ids will be part of the clipped mesh, as some points may not belong
//    *  to any cells anymore. Therefore 2 behaviours are supported by this method :
//    *
//    *  1- in case some of the indicated pointIds remain after clipping and do form a mesh, a marginal model is returned only for those points
//    *  2- in case none of the indicated points remain (they are not meshed), a reference mesh with all indicated point Ids and no cells is constructed and a marginal
//    *  over this new reference is returned
//    *
//    * @see [[DiscreteLowRankGaussianProcess.marginal]]
//    */
//  def marginal(ptIds: IndexedSeq[PointId]) = {
//    val clippedReference = referenceMesh.operations.clip(p => { !ptIds.contains(referenceMesh.pointSet.findClosestPoint(p).id) })
//    // not all of the ptIds remain in the reference after clipping, since their cells might disappear
//    val remainingPtIds = clippedReference.pointSet.points.map(p => referenceMesh.pointSet.findClosestPoint(p).id).toIndexedSeq
//    if (remainingPtIds.isEmpty) {
//      val newRef = TriangleMesh3D(UnstructuredPointsDomain(ptIds.map(id => referenceMesh.pointSet.point(id)).toIndexedSeq), TriangleList(IndexedSeq[TriangleCell]()))
//      val marginalGP = gp.marginal(ptIds.toIndexedSeq)
//      StatisticalLineMeshModel(newRef, marginalGP)
//    } else {
//      val marginalGP = gp.marginal(remainingPtIds)
//      StatisticalLineMeshModel(clippedReference, marginalGP)
//    }
//  }

  /**
    * Returns a reduced rank model, using only the leading basis functions.
    *
    * @param newRank: The rank of the new model.
    */
  def truncate(newRank: Int): StatisticalLineMeshModel = {
    new StatisticalLineMeshModel(referenceMesh, gp.truncate(newRank))
  }

  /**
    * @see [[DiscreteLowRankGaussianProcess.project]]
    */
  def project(mesh: LineMesh[_2D]) = {
    val displacements = referenceMesh.pointSet.points.zip(mesh.pointSet.points).map({ case (refPt, tgtPt) => tgtPt - refPt }).toIndexedSeq
    val dvf = DiscreteField[_2D, UnstructuredPointsDomain[_2D], EuclideanVector[_2D]](referenceMesh.pointSet, displacements)
    warpReference(gp.project(dvf))
  }

  /**
    * @see [[DiscreteLowRankGaussianProcess.coefficients]]
    */
  def coefficients(mesh: LineMesh[_2D]): DenseVector[Double] = {
    val displacements = referenceMesh.pointSet.points.zip(mesh.pointSet.points).map({ case (refPt, tgtPt) => tgtPt - refPt }).toIndexedSeq
    val dvf = DiscreteField[_2D, UnstructuredPointsDomain[_2D], EuclideanVector[_2D]](referenceMesh.pointSet, displacements)
    gp.coefficients(dvf)
  }

  /**
    * Similar to [[DiscreteLowRankGaussianProcess.posterior(Int, Point[_3D])], sigma2: Double)]], but the training data is defined by specifying the target point instead of the displacement vector
    */
  def posterior(trainingData: IndexedSeq[(PointId, Point[_2D])], sigma2: Double): StatisticalLineMeshModel = {
    val trainingDataWithDisplacements = trainingData.map { case (id, targetPoint) => (id, targetPoint - referenceMesh.pointSet.point(id)) }
    val posteriorGp = gp.posterior(trainingDataWithDisplacements, sigma2)
    new StatisticalLineMeshModel(referenceMesh, posteriorGp)
  }

  /**
    * Similar to [[DiscreteLowRankGaussianProcess.posterior(Int, Point[_3D], Double)]]], but the training data is defined by specifying the target point instead of the displacement vector
    */
  def posterior(trainingData: IndexedSeq[(PointId, Point[_2D], MultivariateNormalDistribution)]): StatisticalLineMeshModel = {
    val trainingDataWithDisplacements = trainingData.map { case (id, targetPoint, cov) => (id, targetPoint - referenceMesh.pointSet.point(id), cov) }
    val posteriorGp = gp.posterior(trainingDataWithDisplacements)
    new StatisticalLineMeshModel(referenceMesh, posteriorGp)
  }

  /**
    * transform the statistical mesh model using the given rigid transform.
    * The spanned shape space is not affected by this operations.
    */
  def transform(rigidTransform: RigidTransformation[_2D]): StatisticalLineMeshModel = {
    val newRef = referenceMesh.transform(rigidTransform)

    val newMean: DenseVector[Double] = {
      val newMeanVecs = for ((pt, meanAtPoint) <- gp.mean.pointsWithValues) yield {
        rigidTransform(pt + meanAtPoint) - rigidTransform(pt)
      }
      val data = newMeanVecs.flatMap(_.toArray).toArray //.flatten.toArray
      DenseVector(data)
    }

    val newBasisMat = DenseMatrix.zeros[Double](gp.basisMatrix.rows, gp.basisMatrix.cols)

    for ((Eigenpair(_, ithKlBasis), i) <- gp.klBasis.zipWithIndex) {
      val newIthBasis = for ((pt, basisAtPoint) <- ithKlBasis.pointsWithValues) yield {
        rigidTransform(pt + basisAtPoint) - rigidTransform(pt)
      }
      val data = newIthBasis.flatMap(_.toArray).toArray
      newBasisMat(::, i) := DenseVector(data)
    }
    val newGp = new DiscreteLowRankGaussianProcess[_2D, UnstructuredPointsDomain[_2D], EuclideanVector[_2D]](gp.domain.transform(rigidTransform), newMean, gp.variance, newBasisMat)

    new StatisticalLineMeshModel(newRef, newGp)

  }

  /**
    * Warps the reference mesh with the given transform. The space spanned by the model is not affected.
    */
  def changeReference(t: Point[_2D] => Point[_2D]): StatisticalLineMeshModel = {

    val newRef = referenceMesh.pointSet.transform(t)
    val newMean = gp.mean.pointsWithValues.map { case (refPt, meanVec) => (refPt - t(refPt)) + meanVec }
    val newMeanVec = DenseVector(newMean.flatMap(_.toArray).toArray)
    val newGp = new DiscreteLowRankGaussianProcess[_2D, UnstructuredPointsDomain[_2D], EuclideanVector[_2D]](newRef, newMeanVec, gp.variance, gp.basisMatrix)
    new StatisticalLineMeshModel(LineMesh2D(newRef, referenceMesh.topology), newGp)
  }

  private def warpReference(vectorPointData: DiscreteField[_2D, UnstructuredPointsDomain[_2D], EuclideanVector[_2D]]) = {
    val newPoints = vectorPointData.pointsWithValues.map { case (pt, v) => pt + v }
    LineMesh2D(UnstructuredPointsDomain(newPoints.toIndexedSeq), referenceMesh.topology)
  }

}

object StatisticalLineMeshModel {

  /**
    * creates a StatisticalLineMeshModel by discretizing the given Gaussian Process on the points of the reference mesh.
    */
  def apply(referenceMesh: LineMesh[_2D], gp: LowRankGaussianProcess[_2D, EuclideanVector[_2D]]): StatisticalLineMeshModel = {
    val discreteGp = DiscreteLowRankGaussianProcess(referenceMesh.pointSet, gp)
    new StatisticalLineMeshModel(referenceMesh, discreteGp)
  }

  /**
    * creates a StatisticalLineMeshModel from vector/matrix representation of the mean, variance and basis matrix.
    *
    * @see [[DiscreteLowRankGaussianProcess.apply(FiniteDiscreteDomain, DenseVector[Double], DenseVector[Double], DenseMatrix[Double]]
    */
  private[scalismo] def apply(referenceMesh: LineMesh[_2D],
                              meanVector: DenseVector[Double],
                              variance: DenseVector[Double],
                              basisMatrix: DenseMatrix[Double]): StatisticalLineMeshModel = {
    val gp = new DiscreteLowRankGaussianProcess[_2D, UnstructuredPointsDomain[_2D], EuclideanVector[_2D]](referenceMesh.pointSet, meanVector, variance, basisMatrix)
    new StatisticalLineMeshModel(referenceMesh, gp)
  }

  /**
    * Creates a new DiscreteLowRankGaussianProcess, where the mean and covariance matrix are estimated from the given transformations.
    *
    */
  def createUsingPCA(referenceMesh: LineMesh[_2D], fields: Seq[Field[_2D, EuclideanVector[_2D]]]): StatisticalLineMeshModel = {
    val dgp: DiscreteLowRankGaussianProcess[_2D, UnstructuredPointsDomain[_2D], EuclideanVector[_2D]] = DiscreteLowRankGaussianProcess.createUsingPCA(referenceMesh.pointSet, fields, PivotedCholesky.RelativeTolerance(1e-8))
    new StatisticalLineMeshModel(referenceMesh, dgp)
  }

  /**
    *  Adds a bias model to the given statistical shape model
    */

  def augmentModel(model: StatisticalLineMeshModel, biasModel: LowRankGaussianProcess[_2D, EuclideanVector[_2D]]): StatisticalLineMeshModel = {

    val discretizedBiasModel = biasModel.discretize(model.referenceMesh.pointSet)
    val eigenvalues = DenseVector.vertcat(model.gp.variance, discretizedBiasModel.variance).map(sqrt(_))
    val eigenvectors = DenseMatrix.horzcat(model.gp.basisMatrix, discretizedBiasModel.basisMatrix)

    for (i <- 0 until eigenvalues.length) {
      eigenvectors(::, i) :*= eigenvalues(i)
    }

    val l: DenseMatrix[Double] = eigenvectors.t * eigenvectors
    val SVD(v, _, _) = breeze.linalg.svd(l)
    val U: DenseMatrix[Double] = eigenvectors * v
    val d: DenseVector[Double] = DenseVector.zeros(U.cols)
    for (i <- 0 until U.cols) {
      d(i) = breeze.linalg.norm(U(::, i))
      U(::, i) := U(::, i) * (1.0 / d(i))
    }

    val r = model.gp.copy[_2D, UnstructuredPointsDomain[_2D], EuclideanVector[_2D]](meanVector = model.gp.meanVector + discretizedBiasModel.meanVector, variance = breeze.numerics.pow(d, 2), basisMatrix = U)
    StatisticalLineMeshModel(model.referenceMesh, r)
  }

  /**
    * Returns a PCA model with given reference mesh and a set of items in correspondence.
    * All points of the reference mesh are considered for computing the PCA
    */
  def createUsingPCA(dc: DataLineCollection): Try[StatisticalLineMeshModel] = {
    if (dc.size < 3) return Failure(new Throwable(s"A data collection with at least 3 transformations is required to build a PCA Model (only ${dc.size} were provided)"))

    val fields = dc.dataItems.map { i =>
      Field[_2D, EuclideanVector[_2D]](i.transformation.domain, p => i.transformation(p) - p)
    }
    Success(createUsingPCA(dc.reference, fields))
  }

}

