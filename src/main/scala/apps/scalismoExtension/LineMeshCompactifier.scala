///*
// * Copyright 2016 University of Basel, Graphics and Vision Research Group
// *
// * Licensed under the Apache License, Version 2.0 (the "License");
// * you may not use this file except in compliance with the License.
// * You may obtain a copy of the License at
// *
// *     http://www.apache.org/licenses/LICENSE-2.0
// *
// * Unless required by applicable law or agreed to in writing, software
// * distributed under the License is distributed on an "AS IS" BASIS,
// * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// * See the License for the specific language governing permissions and
// * limitations under the License.
// */
//package scalismo.mesh
//
//import scalismo.common.PointId
//import scalismo.common.UnstructuredPointsDomain.Create.CreateUnstructuredPointsDomain2D
//import scalismo.geometry._2D
//
//trait LineMeshSurfaceCorrespondence{
//
//  /**
//    * get corresponding point on target surface
//    * @param lineId triangle on this surface
//    * @return corresponding lines on target surface
//    */
//  def correspondingPoint(lineId: LineId): (LineId)
//
//  /**
//    * lines of this surface
//    * @return
//    */
//  def lines: LineList
//
//  /**
//    * triangulation of target surface
//    * @return
//    */
//  def targetLines: LineList
//
//  def onSurface(lineId: LineId,
//  ): (TriangleId, BarycentricCoordinates) = correspondingPoint(lineId: LineId)
//}
//
///**
//  * a general operation on mesh, can also alter surface properties
//  */
//trait LineMeshManipulation {
//
//  /**
//    * get the transformed mesh
//    */
//  def transformedMesh: LineMesh[_2D]
//
//  /**
//    * apply operation to a surface property
//    * default implementation: warps old surface property (general but inefficient)
//    *
//    * @param property surface property to transform
//    */
//  def applyToSurfaceProperty[A](
//    property: MeshSurfaceProperty[A]
//  ): MeshSurfaceProperty[A] =
//    WarpedMeshSurfaceProperty(property, lineMeshSurfaceCorrespondence)
//
//  /**
//    * correspondence on new surface, returns old surface coordinates for each new point on surface
//    */
//  def lineMeshSurfaceCorrespondence: lineMeshSurfaceCorrespondence
//}
//
///**
//  * compact a mesh: remove unreferenced points and lines with invalid points, also respects external filters for points and lines
//  *
//  * @param mesh           mesh to compact
//  * @param pointFilter    filter to remove points, keeps on true
//  * @param lineFilter filter to remove lines, keeps on true
//  */
//class LineMeshCompactifier(mesh: LineMesh[_2D],
//                           pointFilter: PointId => Boolean,
//                           lineFilter: LineId => Boolean)
//    extends LineMeshManipulation {
//  private val invalidPoint = PointId(-1)
//
//  private val meshPoints: Int = mesh.pointSet.numberOfPoints
//
//  private val pointValidity: Array[Boolean] = {
//    mesh.pointSet.pointIds.map(pointFilter).toArray
//  }
//
//  @inline
//  private def isPointValid(pointId: PointId) =
//    pointId.id < meshPoints &&
//      pointId != invalidPoint &&
//      pointValidity(pointId.id)
//
//  @inline
//  private def isLineValid(lineId: LineId): Boolean = {
//    val t = mesh.topology.line(lineId)
//    lineId != LineId.invalid &&
//    lineFilter(lineId) &&
//    isPointValid(t.ptId1) &&
//    isPointValid(t.ptId2)
//  }
//
//  private val newLines: IndexedSeq[LineId] = {
//    mesh.topology.lineIds.filter(isLineValid)
//  }
//
//  // find valid points: points referenced by valid lines
//  private val newPoints: IndexedSeq[PointId] = {
//    newLines.iterator
//      .map { mesh.topology.line }
//      .flatMap { _.pointIds }
//      .toIndexedSeq
//      .distinct
//      .sortBy {
//        _.id
//      }
//  }
//
//  private val numberOfPoints = newPoints.size
//  assert(numberOfPoints <= mesh.pointSet.numberOfPoints)
//
//  private val fwdIndex = Array.fill(mesh.pointSet.numberOfPoints)(invalidPoint)
//  for (newId <- 0 until numberOfPoints) {
//    val oldId = newPoints(newId)
//    fwdIndex(oldId.id) = PointId(newId)
//  }
//
//  /** find new id for old point id */
//  def pointFwdMap(oldId: PointId): PointId = fwdIndex(oldId.id)
//
//  /** find old id for new point id */
//  def pointBackMap(newId: PointId): PointId = newPoints(newId.id)
//
//  /** find old id for new line id */
//  def lineBackMap(newId: LineId): LineId = newLines(newId.id)
//
//  override val transformedMesh: LineMesh[_2D] = {
//    val points = newPoints.map {
//      mesh.pointSet.point
//    }
//    val lines = newLines.map { tid =>
//      val t = mesh.topology.line(tid)
//      LineCell(pointFwdMap(t.ptId1), pointFwdMap(t.ptId2))
//    }
//    LineMesh2D(CreateUnstructuredPointsDomain2D.create(points), LineList(lines))
//  }
//
//  override def applyToSurfaceProperty[A](
//    property: MeshSurfaceProperty[A]
//  ): MeshSurfaceProperty[A] = {
//    require(
//      property.lines == mesh.topology,
//      "surface property is not compatible with mesh"
//    )
//    property match {
//      case trProp: LineProperty[A] =>
//        val newLineData = transformedMesh.topology.lineIds.map { tId =>
//          trProp.onSurface(lineBackMap(tId))
//        }
//        LineProperty(transformedMesh.topology, newLineData)
//      case ptProp: SurfacePointProperty[A] =>
//        val newPointData = transformedMesh.pointSet.pointIds.map { pId =>
//          ptProp.atPoint(pointBackMap(pId))
//        }.toIndexedSeq
//        SurfacePointProperty(transformedMesh.topology, newPointData)(
//          ptProp.interpolator
//        )
//      case _ =>
//        super.applyToSurfaceProperty(property) // inefficient default warping
//    }
//  }
//
//  /**
//    * new surface correspondence: maps new line id to old
//    */
//  def lineMeshSurfaceCorrespondence: MeshSurfaceCorrespondence =
//    new MeshSurfaceCorrespondence {
//      override def topology: LineList = transformedMesh.topology
//
//      override def targetTopology: LineList = mesh.topology
//
//      override def correspondingPoint(
//        lineId: LineId,
//        bcc: BarycentricCoordinates
//      ): (LineId, BarycentricCoordinates) =
//        (lineBackMap(lineId), bcc)
//    }
//}
//
//object LineMeshCompactifier {
//  def apply(mesh: LineMesh[_2D],
//            pointFilter: PointId => Boolean,
//            lineFilter: LineId => Boolean): LineMeshCompactifier =
//    new LineMeshCompactifier(mesh, pointFilter, lineFilter)
//}
