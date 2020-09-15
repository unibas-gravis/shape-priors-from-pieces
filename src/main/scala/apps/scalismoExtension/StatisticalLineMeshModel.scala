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

import breeze.linalg.{DenseMatrix, DenseVector}
import scalismo.common._
import scalismo.geometry.EuclideanVector._
import scalismo.geometry._
import scalismo.mesh._
import scalismo.numerics.PivotedCholesky
import scalismo.statisticalmodel.dataset.DataCollection.LineMeshDataCollection
import scalismo.transformations.TranslationAfterRotation
import scalismo.utils.Random

import scala.util.Try

/**
 * A StatisticalMeshModel is isomorphic to a [[DiscreteLowRankGaussianProcess]]. The difference is that while the DiscreteLowRankGaussianProcess
 * models defomation fields, the StatisticalMeshModel applies the deformation fields to a mesh, and warps the mesh with the deformation fields to
 * produce a new mesh.
 *
 * @see [[DiscreteLowRankGaussianProcess]]
 */
case class StatisticalLineMeshModel private(referenceMesh: LineMesh[_2D], gp: DiscreteLowRankGaussianProcess[_2D, LineMesh, EuclideanVector[_2D]]) {
  /**
   * The mean shape
   *
   * @see [[DiscreteLowRankGaussianProcess.mean]]
   */
  lazy val mean: LineMesh[_2D] = pdm.mean
  /** @see [[scalismo.statisticalmodel.DiscreteLowRankGaussianProcess.rank]]*/

  val pdm: PointDistributionModel[_2D, LineMesh] = PointDistributionModel[_2D, LineMesh](gp)
  val rank = gp.rank

  /**
   * The covariance between two points of the  mesh with given point id.
   *
   * @see [[DiscreteLowRankGaussianProcess.cov]]
   */
  def cov(ptId1: PointId, ptId2: PointId): DenseMatrix[Double] = pdm.cov(ptId1, ptId2)

  /**
   * draws a random shape.
   *
   * @see [[DiscreteLowRankGaussianProcess.sample]]
   */
  def sample()(implicit rand: Random): LineMesh2D = pdm.sample()

  /**
   * returns the probability density for an instance of the model
   *
   * @param instanceCoefficients coefficients of the instance in the model. For shapes in correspondence, these can be obtained using the coefficients method
   *
   */
  def pdf(instanceCoefficients: DenseVector[Double]): Double = pdm.pdf(instanceCoefficients)

  /**
   * returns a shape that corresponds to a linear combination of the basis functions with the given coefficients c.
   *
   * @see [[DiscreteLowRankGaussianProcess.instance]]
   */
  def instance(c: DenseVector[Double]): LineMesh[_2D] = pdm.instance(c)

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
  //  def marginal(ptIds: IndexedSeq[PointId]): StatisticalMeshModel = {
  //    val newRef: LineMesh[_2D] = referenceMesh.operations.maskPoints(f => ptIds.contains(f)).transformedMesh
  //    val marginalModel = pdm.newReference(newRef, NearestNeighborInterpolator2D())
  //    new StatisticalMeshModel(marginalModel.reference, marginalModel.gp)
  //  }

  /**
   * Returns a reduced rank model, using only the leading basis functions.
   *
   * @param newRank : The rank of the new model.
   */
  def truncate(newRank: Int): StatisticalLineMeshModel = {
    val truncate = pdm.truncate(newRank)
    new StatisticalLineMeshModel(truncate.reference, truncate.gp)
  }

  /**
   * @see [[DiscreteLowRankGaussianProcess.project]]
   */
  def project(mesh: LineMesh[_2D]): LineMesh2D = pdm.project(mesh)


  /**
   * @see [[DiscreteLowRankGaussianProcess.coefficients]]
   */
  def coefficients(mesh: LineMesh[_2D]): DenseVector[Double] = pdm.coefficients(mesh)


  /**
   * Similar to [[DiscreteLowRankGaussianProcess.posterior(Int, Point[_3D])], sigma2: Double)]], but the training data is defined by specifying the target point instead of the displacement vector
   */
  def posterior(trainingData: IndexedSeq[(PointId, Point[_2D])], sigma2: Double): StatisticalLineMeshModel = {
    val posterior = pdm.posterior(trainingData, sigma2)
    new StatisticalLineMeshModel(posterior.reference, posterior.gp)
  }

  /**
   * Similar to [[DiscreteLowRankGaussianProcess.posterior(Int, Point[_3D], Double)]]], but the training data is defined by specifying the target point instead of the displacement vector
   */
  def posterior(
                 trainingData: IndexedSeq[(PointId, Point[_2D], MultivariateNormalDistribution)]
               ): StatisticalLineMeshModel = {
    val posterior = pdm.posterior(trainingData)
    new StatisticalLineMeshModel(posterior.reference, posterior.gp)
  }

  /**
   * transform the statistical mesh model using the given rigid transform.
   * The spanned shape space is not affected by this operations.
   */
  def transform(rigidTransform: TranslationAfterRotation[_2D]): StatisticalLineMeshModel = {
    val transformModel = pdm.transform(rigidTransform)
    new StatisticalLineMeshModel(transformModel.reference, transformModel.gp)
  }

  /**
   * Warps the reference mesh with the given transform. The space spanned by the model is not affected.
   */
  def changeReference(t: Point[_2D] => Point[_2D]): StatisticalLineMeshModel = {
    val changeRef = pdm.changeReference(t)
    new StatisticalLineMeshModel(changeRef.reference, changeRef.gp)
  }

}

object StatisticalLineMeshModel {

  /**
   * creates a StatisticalLineMeshModel by discretizing the given Gaussian Process on the points of the reference mesh.
   */
  def apply(referenceMesh: LineMesh[_2D], gp: LowRankGaussianProcess[_2D, EuclideanVector[_2D]]): StatisticalLineMeshModel = {
    val discreteGp = DiscreteLowRankGaussianProcess(referenceMesh, gp)
    new StatisticalLineMeshModel(referenceMesh, discreteGp)
  }

  /**
   * Adds a bias model to the given statistical shape model
   */
  def augmentModel(model: StatisticalLineMeshModel,
                   biasModel: LowRankGaussianProcess[_2D, EuclideanVector[_2D]]): StatisticalLineMeshModel = {
    val pdModel = PointDistributionModel(model.gp)
    val augmentModel = PointDistributionModel.augmentModel(pdModel, biasModel)
    new StatisticalLineMeshModel(augmentModel.reference, augmentModel.gp)
  }

  /**
   * Returns a PCA model with given reference mesh and a set of items in correspondence.
   * All points of the reference mesh are considered for computing the PCA
   *
   * Per default, the resulting mesh model will have rank (i.e. number of principal components) corresponding to
   * the number of linearly independent fields. By providing an explicit stopping criterion, one can, however,
   * compute only the leading principal components. See PivotedCholesky.StoppingCriterion for more details.
   */
  def createUsingPCA(
                      dc: LineMeshDataCollection[_2D],
                      stoppingCriterion: PivotedCholesky.StoppingCriterion = PivotedCholesky.RelativeTolerance(0)
                    ): Try[StatisticalLineMeshModel] = {
    Try {
      val pdm = PointDistributionModel.createUsingPCA(dc, stoppingCriterion)
      new StatisticalLineMeshModel(pdm.reference, pdm.gp)
    }
  }

  /**
   * Creates a new Statistical mesh model, with its mean and covariance matrix estimated from the given fields.
   *
   * Per default, the resulting mesh model will have rank (i.e. number of principal components) corresponding to
   * the number of linearly independent fields. By providing an explicit stopping criterion, one can, however,
   * compute only the leading principal components. See PivotedCholesky.StoppingCriterion for more details.
   *
   */
  def createUsingPCA(referenceMesh: LineMesh[_2D],
                     fields: Seq[Field[_2D, EuclideanVector[_2D]]],
                     stoppingCriterion: PivotedCholesky.StoppingCriterion): StatisticalLineMeshModel = {
    val pdm = PointDistributionModel.createUsingPCA(referenceMesh, fields, stoppingCriterion)
    new StatisticalLineMeshModel(pdm.reference, pdm.gp)
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
    val pdModel = PointDistributionModel(referenceMesh, meanVector, variance, basisMatrix)
    new StatisticalLineMeshModel(pdModel.reference, pdModel.gp)
  }

}

