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

package apps.util

import java.awt.Color

import apps.scalismoExtension.FormatConverter
import breeze.linalg.DenseVector
import scalismo.common.UnstructuredPoints.Create
import scalismo.common.{DiscreteField, UnstructuredPoints, UnstructuredPointsDomain, Vectorizer}
import scalismo.geometry.{EuclideanVector, Landmark, NDSpace, Point, _2D, _3D}
import scalismo.mesh.{LineMesh, LineMesh2D, LineMesh3D}
import scalismo.statisticalmodel.PointDistributionModel
import scalismo.ui.api._

object VisualizationHelper {

  def pointSetFromDenseVector[D: NDSpace](d: DenseVector[Double])(implicit vectorizer: Vectorizer[Point[D]], creator: Create[D]): UnstructuredPoints[D] = {
    val dim = vectorizer.dim
    val data = d.toArray.grouped(dim).map(e => vectorizer.unvectorize(DenseVector(e))).toIndexedSeq
    creator.create(data)
  }

  def vectorizePointSet[D](pointSet: UnstructuredPoints[D])(implicit vectorizer: Vectorizer[Point[D]]): DenseVector[Double] = {
    val dim = vectorizer.dim
    val fullDim = pointSet.numberOfPoints * dim
    val M = DenseVector.zeros[Double](fullDim)
    for (i <- pointSet.pointsWithId) {
      val m = vectorizer.vectorize(i._1)
      for (x <- 0 until dim) {
        M(i._2.id * dim + x) = m(x)
      }
    }
    M
  }

  def show2DLandmarks(ui: SimpleAPI, group: Group, landmarks: Seq[Landmark[_2D]], name: String): Seq[LandmarkView] = {
    val lm3D: Seq[Landmark[_3D]] = FormatConverter.landmark2Dto3D(landmarks)
    ui.show(group, lm3D, name)
  }

  def show2DLineMesh(ui: SimpleAPI, group: Group, mesh: LineMesh2D, name: String): LineMeshView = {
    val mesh3D: LineMesh3D = FormatConverter.lineMesh2Dto3D(mesh)
    ui.show(group, mesh3D, name)
  }

  def show2DLineMeshNormals(ui: SimpleAPI, group: Group, mesh: LineMesh2D, name: String): VectorFieldView = {
    val mesh3D = FormatConverter.lineMesh2Dto3D(mesh)
    val normalVectors: IndexedSeq[EuclideanVector[_3D]] =
      mesh.pointSet.pointIds.toIndexedSeq.map { id =>
        val p = mesh.pointSet.point(id)
        val n = mesh.vertexNormals(id)
        EuclideanVector.apply(x = n.x, y = n.y, z = 0) * 10
      }

    val vectorFields =
      DiscreteField[_3D, UnstructuredPointsDomain, EuclideanVector[_3D]](
        UnstructuredPointsDomain.Create.CreateUnstructuredPointsDomain3D.create(mesh3D.pointSet.points.toIndexedSeq),
        normalVectors
      )
    ui.show(group, vectorFields, name)
  }

  def visualizePCsamples(ui: ScalismoUI, model: PointDistributionModel[_2D, LineMesh], group: Group, maxNumberOfComponents: Int = 3): Unit = {
    (0 to math.min(maxNumberOfComponents, model.rank - 1)).foreach { pc =>
      (-3 to 3).foreach { i =>
        val coeff = DenseVector.zeros[Double](model.rank)
        coeff(pc) = i.toDouble
        val showing = show2DLineMesh(ui, group, model.instance(coeff), s"PC-${pc}_${i}")
        showing.lineWidth = 1
        if (i == 0) showing.color = Color.GREEN
        if (pc > 0) showing.opacity = 0f
      }
    }
  }
}
