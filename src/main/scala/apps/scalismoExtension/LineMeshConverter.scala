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

import scalismo.common.UnstructuredPoints.Create.{CreateUnstructuredPoints2D, CreateUnstructuredPoints3D}
import scalismo.common.{PointId, UnstructuredPoints}
import scalismo.geometry._
import scalismo.mesh._

object LineMeshConverter {

  def PointDomain2Dto3D(
                         pd2D: UnstructuredPoints[_2D]
                       ): UnstructuredPoints[_3D] = {
    val p3d = {
      pd2D.points.toIndexedSeq.map(p => Point3D(x = p.x, y = p.y, z = 0))
    }
    CreateUnstructuredPoints3D.create(p3d)
  }

  def pointCloudto3DLineMesh(pd: UnstructuredPoints[_3D]): LineMesh3D = {
    lineMesh2Dto3D(pointCloudto2DLineMesh(pd))
  }

  def pointCloudto2DLineMesh(pd: UnstructuredPoints[_3D]): LineMesh2D = {

    val p2d = pd.points.toIndexedSeq.map(p => Point2D(x = p.x, y = p.y))
    val pd2d = CreateUnstructuredPoints2D.create(p2d)

    var linecells: Set[LineCell] = Set()
    pd2d.pointIds.toIndexedSeq.foreach { id =>
      val p = pd.point(id)
      //      val closest = pd.findNClosestPoints(p, 3).map(_.id).filter(i => i != id)
      //      linecells += LineCell(id, closest.head)
      //      linecells += LineCell(id, closest.last)
      val closest = pd.findNClosestPoints(p, 2).last.id
      linecells += LineCell(id, closest)
    }

    val lines: LineList = LineList(linecells.toIndexedSeq)

    val lm2d = LineMesh2D(pd2d, lines)
    lm2d.vertexNormals
    lm2d
  }

  def lineMesh2Dto3D(mesh: LineMesh2D): LineMesh3D = {

    val p3d = mesh.pointSet.points.toIndexedSeq
      .map(p => Point3D(x = p.x, y = p.y, z = 0.0))
    val pd3d = CreateUnstructuredPoints3D.create(p3d)

    LineMesh3D(pd3d, mesh.topology)
  }

  def lineMesh3Dto2D(mesh: LineMesh3D): LineMesh2D = {

    val p2d = mesh.pointSet.points.toIndexedSeq.map(p => Point2D(x = p.x, y = p.y))
    val pd2d = CreateUnstructuredPoints2D.create(p2d)

    LineMesh2D(pd2d, mesh.topology)
  }

  def landmark2Dto3D(lms: Seq[Landmark[_2D]]): Seq[Landmark[_3D]] = {
    lms.map(lm => Landmark[_3D](
      lm.id,
      Point3D(lm.point.x, lm.point.y, 0),
      lm.description,
      lm.uncertainty
    )
    )
  }

  def landmark3Dto2D(lms: Seq[Landmark[_3D]]): Seq[Landmark[_2D]] = {
    lms.map(lm => Landmark[_2D](
      lm.id,
      Point2D(lm.point.x, lm.point.y),
      lm.description,
      lm.uncertainty
    )
    )
  }

  def createPartialMesh(mesh: LineMesh2D,
                        pId: PointId,
                        area: Int): LineMesh[_2D] = {
    val p = mesh.pointSet.point(pId)
    val removeList: Seq[Point[_2D]] = mesh.pointSet.findNClosestPoints(p, area).map(_.point)
    createPartialMesh(mesh, removeList)
  }

  def createPartialMesh(mesh: LineMesh2D, removeList: Seq[Point[_2D]]): LineMesh[_2D] = {
    val meshPoints = mesh.pointSet.points.toIndexedSeq

    val remainingPoints = meshPoints.par.filter { p => !removeList.contains(p) }.zipWithIndex.toMap

    val remainingPointDoublets = mesh.cells.par
      .map { cell =>
        val points = cell.pointIds.map(pointId => meshPoints(pointId.id))
        (
          points,
          points.map(p => remainingPoints.get(p).isDefined).reduce(_ && _)
        )
      }
      .filter(_._2)
      .map(_._1)

    val points = remainingPointDoublets.flatten.distinct
    val pt2Id = points.zipWithIndex.toMap
    val cells = remainingPointDoublets.map {
      case vec => LineCell(PointId(pt2Id(vec(0))), PointId(pt2Id(vec(1))))
    }

    LineMesh2D(
      CreateUnstructuredPoints2D.create(points.toIndexedSeq),
      LineList(cells.toIndexedSeq)
    )
  }

}

case class LineMeshOperator(mesh: LineMesh2D) {
  val centerPoint: Point2D = Point2D(x = c.x, y = c.y)
  private val c = mesh.pointSet.points
    .map(_.toVector)
    .reduce(_ + _) * 1.0 / mesh.pointSet.numberOfPoints.toDouble

  def verifyNormalDirection(point: Point2D,
                            normal: EuclideanVector2D): Boolean = {
    val v: EuclideanVector2D = (point - centerPoint)
    //    (v dot normal) <= math.cos(45)
    false
  }

  def isInsideMesh(point: Point2D): Boolean = {
    false
  }
}
