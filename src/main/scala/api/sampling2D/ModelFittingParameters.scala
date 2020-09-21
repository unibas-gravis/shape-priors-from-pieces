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

import breeze.linalg.DenseVector
import scalismo.common.PointId
import scalismo.common.interpolation.NearestNeighborInterpolator
import scalismo.geometry.{EuclideanVector, Point, _2D}
import scalismo.mesh.LineMesh
import scalismo.statisticalmodel.PointDistributionModel
import scalismo.transformations._

case class ScaleParameter(s: Double) {
  def parameters: DenseVector[Double] = DenseVector(s)
}

case class PoseParameters(translation: EuclideanVector[_2D], rotation: Double, rotationCenter: Point[_2D]) {
  def parameters: DenseVector[Double] = {
    DenseVector.vertcat(translation.toBreezeVector, DenseVector[Double](rotation)
      , DenseVector[Double](rotationCenter.x, rotationCenter.y))
  }
}

object PoseParameters {
  def createFromRigidTransform(r: TranslationAfterRotation[_2D]): PoseParameters = {
    val rotParams = r.rotation.parameters
    PoseParameters(r.translation.t, rotParams(0), r.rotation.center)
  }
}

case class ShapeParameters(parameters: DenseVector[Double])

case class ModelFittingParameters(scalaParameter: ScaleParameter, poseParameters: PoseParameters, shapeParameters: ShapeParameters, generatedBy: String = "Anonymous") {


  private val allParameters = DenseVector.vertcat(scalaParameter.parameters, poseParameters.parameters, shapeParameters.parameters)

  override def equals(that: Any): Boolean = {
    that match {
      case that: ModelFittingParameters => that.canEqual(this) && this.hashCode == that.hashCode
      case _ => false
    }
  }

  def canEqual(other: Any): Boolean = {
    other.isInstanceOf[ModelFittingParameters]
  }

  override def hashCode(): Int = allParameters.hashCode()

}


object ModelFittingParameters {

  /**
   * Create ModelFittingParameters using pose and shape only (scale is fixed to 1)
   */
  def apply(poseParameters: PoseParameters, shapeParameters: ShapeParameters): ModelFittingParameters = {
    ModelFittingParameters(ScaleParameter(1.0), poseParameters, shapeParameters)
  }

  def poseAndShapeTransform(model: PointDistributionModel[_2D, LineMesh], parameters: ModelFittingParameters): Transformation[_2D] = {
    Transformation(poseTransform(parameters).compose(shapeTransform(model, parameters)))
  }

  def poseTransform(parameter: ModelFittingParameters): TranslationAfterRotation[_2D] = {
    val poseParameters = parameter.poseParameters
    val translation = Translation[_2D](poseParameters.translation)
    val (phi) = poseParameters.rotation
    val center = poseParameters.rotationCenter
    val rotation = Rotation(phi, center)
    TranslationAfterRotation[_2D](translation, rotation)
  }

  def shapeTransform(model: PointDistributionModel[_2D, LineMesh], parameters: ModelFittingParameters): Transformation[_2D] = {
    val coeffs = parameters.shapeParameters.parameters
    val gpModel = model.gp.interpolate(NearestNeighborInterpolator())
    val instance = gpModel.instance(coeffs)
    Transformation((pt: Point[_2D]) => pt + instance(pt))
  }

  def transformedMesh(model: PointDistributionModel[_2D, LineMesh], parameters: ModelFittingParameters): LineMesh[_2D] = {
    model.reference.transform(fullTransform(model, parameters))
  }

  def fullTransform(model: PointDistributionModel[_2D, LineMesh], parameters: ModelFittingParameters): Transformation[_2D] = {
    Transformation(scaleTransform(parameters).compose(poseTransform(parameters).compose(shapeTransform(model, parameters))))
  }

  def scaleTransform(parameters: ModelFittingParameters): Scaling[_2D] = {
    Scaling(parameters.scalaParameter.s)
  }

  def transformedPoints(model: PointDistributionModel[_2D, LineMesh], parameters: ModelFittingParameters, points: Seq[Point[_2D]]): Seq[Point[_2D]] = {
    val t = fullTransform(model, parameters)
    points.map(t)
  }

  def transformedPointsWithIds(model: PointDistributionModel[_2D, LineMesh], parameters: ModelFittingParameters, pointIds: Seq[PointId]): Seq[Point[_2D]] = {
    val t = fullTransform(model, parameters)
    val ps = model.reference.pointSet
    pointIds.map(id => t(ps.point(id)))
  }


}