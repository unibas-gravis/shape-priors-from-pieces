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

import scalismo.geometry.{Point, _2D}
import scalismo.mesh.{LineMesh, LineMesh2D}
import scalismo.registration.LandmarkRegistration


object LineMeshMetrics2D {

  def evaluateReconstruction2GroundTruthBoundaryAware(id: String, reconstruction: LineMesh2D, groundTruth: LineMesh2D): Unit = {
    val (avgDist2Surf) = avgDistance(reconstruction, groundTruth)

    println(s"ID: ${id} average2surface: ${avgDist2Surf} max: ${0}")
  }

  def avgDistance(m1: LineMesh[_2D], m2: LineMesh[_2D]): Double = {

    val dists = for (ptM1 <- m1.pointSet.points) yield {
      val cpM2 = m2.pointSet.findClosestPoint(ptM1).point
      (ptM1 - cpM2).norm
    }
    dists.sum / m1.pointSet.numberOfPoints
  }

  /**
   * Returns the Hausdorff distance between the two meshes
   */
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

  def procrustesDistance(m1: LineMesh[_2D], m2: LineMesh[_2D]): Double = {
    require(m1.pointSet.numberOfPoints == m2.pointSet.numberOfPoints)

    val landmarks = m1.pointSet.points.toIndexedSeq zip m2.pointSet.points.toIndexedSeq
    val t = LandmarkRegistration.rigid2DLandmarkRegistration(landmarks, Point(0, 0))
    val m1w = m1.transform(t)
    val dists = (m1w.pointSet.points.toIndexedSeq zip m2.pointSet.points.toIndexedSeq).map {
      case (m1wP, m2P) => (m1wP - m2P).norm
    }
    dists.sum / m1.pointSet.numberOfPoints
  }


}