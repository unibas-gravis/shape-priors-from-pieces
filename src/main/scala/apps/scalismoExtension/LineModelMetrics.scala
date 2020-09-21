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

package apps.scalismoExtension

import scalismo.common.DomainWarp
import scalismo.geometry.{EuclideanVector, _2D}
import scalismo.mesh.LineMesh
import scalismo.statisticalmodel.PointDistributionModel
import scalismo.statisticalmodel.dataset.DataCollection
import scalismo.utils.Random

import scala.collection.parallel.immutable.ParVector
import scala.util.{Failure, Success, Try}

/**
 * Implements utility functions for evaluating the quality of a [[StatisticalLineMeshModel]]
 */
object LineModelMetrics {

  /**
   * Returns the specificity metric of the Statistical Mesh Model, that is how close the model remains to the category of shapes it is supposed to represent
   *
   * @param pcaModel  model to be evaluated
   * @param data      test data to verify specificity against
   * @param nbSamples number of samples drawn to compute the average
   *
   *                  The implementation of this metric is inspired from :
   *                  Styner, Martin A., et al. "Evaluation of 3D correspondence methods for model building." Information processing in medical imaging. Springer Berlin Heidelberg, 2003.
   *
   *                  The general idea is as follows :
   *
   *
   *                  1 - sample a shape from the mesh model
   *
   *
   *                  2-  compute the average mesh distance (see [[scalismo.mesh.MeshMetrics]]) of the sample to all elements of the given sequence of meshes and select the minimum distance
   *
   *                  These steps are then repeated nbSamples times and the average value is returned.
   *
   */
  def specificity(pcaModel: PointDistributionModel[_2D, LineMesh], data: Iterable[LineMesh[_2D]], nbSamples: Int)(
    implicit rng: Random
  ): Double = {

    ParVector.range(0, nbSamples).map { _ =>
      val sample = pcaModel.sample
      data.map { m => LineMeshMetrics2D.avgDistance(m, sample)}.min
    }.sum * (1.0 / nbSamples)
  }

  /**
   * Returns the generalization metric of the Statistical Mesh Model, that is how well the model can represent unseen data
   *
   * @param pcaModel Statistical Mesh Model to be evaluated
   * @param dc       test data collection that is in correspondence with the model reference
   *
   *                 The implementation of this metric is inspired from :
   *                 Styner, Martin A., et al. "Evaluation of 3D correspondence methods for model building." Information processing in medical imaging. Springer Berlin Heidelberg, 2003.
   *
   *                 For every mesh in the test data, we project the mesh into the model (that is find the closest shape in the model space to the given mesh) and compute the average mesh distance (see [[scalismo.mesh.MeshMetrics]]) between the mesh and the projection.
   *                 To be able to perform the projection, it is important that the data collection is in correspondence with the model.
   *                 The returned value is a scala.util.Try containing the average over all test data in case of success, or an Exception otherwise
   */
  def generalization[D](
                         pcaModel: PointDistributionModel[_2D, LineMesh],
                         dc: DataCollection[_2D, LineMesh, EuclideanVector[_2D]]
                       )(implicit warper: DomainWarp[_2D, LineMesh]): Try[Double] = {

    if (pcaModel.reference == dc.reference) Success {
      new ParVector(dc.dataItems.toVector).map { item =>
        val mesh = warper.transformWithField(dc.reference, item)
        val projection = pcaModel.project(mesh)
        LineMeshMetrics2D.avgDistance(projection, mesh)
      }.sum / dc.size.toDouble
    } else Failure(new Exception("pca model and test data collection must have the same reference"))
  }

}
