package apps.hands.preprocessing

import java.io.File

import apps.scalismoExtension.{LineMeshConverter, LineMeshOperator}
import apps.util.myPaths
import breeze.linalg.DenseMatrix
import scalismo.common.UnstructuredPointsDomain.Create.CreateUnstructuredPointsDomain3D
import scalismo.common.{Domain, PointId, PointWithId, RealSpace, VectorField}
import scalismo.geometry._
import scalismo.io.{LandmarkIO, MeshIO}
import scalismo.kernels.{GaussianKernel, MatrixValuedPDKernel}
import scalismo.mesh.{LineCell, LineList, LineMesh, LineMesh2D, TriangleMesh3D}
import scalismo.numerics.Sampler
import scalismo.statisticalmodel.{GaussianProcess, LowRankGaussianProcess}
import scalismo.ui.api.ScalismoUI
import scalismo.utils.Random

object CreateHandReference {

  def walk(mesh: LineMesh2D, stopIds: IndexedSeq[PointId], currentIds: IndexedSeq[PointId]): IndexedSeq[PointId] = {
    val nearby = currentIds.flatMap{
      id =>
        mesh.topology.adjacentPointsForPoint(id)
    }
    val all = nearby.toSet.union(currentIds.toSet).toIndexedSeq

    if(all.contains(stopIds.head) && all.contains(stopIds.last)) all
    else walk(mesh, stopIds, all.diff(stopIds))
  }

  def walkPoints(mesh: LineMesh2D, currentIds: IndexedSeq[PointId], numOfPoints: Int): IndexedSeq[PointId] = {
    val nearby = currentIds.flatMap{
      id =>
        mesh.topology.adjacentPointsForPoint(id)
    }
    val all = nearby.toSet.union(currentIds.toSet).toIndexedSeq

    if(all.length >= numOfPoints) all
    else walkPoints(mesh, all,numOfPoints)
  }

  def getIDsBetweenLMs(mesh: LineMesh2D, middlePoint: Point2D, endPoints: Seq[Point2D]): IndexedSeq[PointId] ={
    require(endPoints.length == 2)
    val endIds: IndexedSeq[PointId] = endPoints.map(p => mesh.pointSet.findClosestPoint(p).id).toIndexedSeq
    val mid: PointId = mesh.pointSet.findClosestPoint(middlePoint).id
    walk(mesh, endIds, IndexedSeq(mid))
  }

  def getPointsBetweenLMs(mesh: LineMesh2D, middlePoint: Point2D, endPoints: Seq[Point2D]): IndexedSeq[Point[_2D]] = {
    getIDsBetweenLMs(mesh, middlePoint, endPoints).map(id => mesh.pointSet.point(id))
  }

  def getPointsAroundLM(mesh: LineMesh2D, middlePoint: Point2D, numOfPoints: Int): IndexedSeq[Point[_2D]] = {
    val points = IndexedSeq(mesh.pointSet.findClosestPoint(middlePoint).id)
    walkPoints(mesh, points, numOfPoints).map(id => mesh.pointSet.point(id))
  }

    def getLmPoint[A](lmSeq: Seq[Landmark[A]], name: String): Point[A] = {
    lmSeq.find(_.id == name).get.point
  }


  def main(args: Array[String]): Unit = {
    scalismo.initialize()

    val refMeshFile = new File(myPaths.datapath, "aligned/reference-hand_full.vtk")
    val refMeshOutFile = new File(myPaths.datapath, "aligned/reference-hand.vtk")

    val refLmFile = new File(myPaths.datapath, "aligned/reference-hand.json")

    val refMesh = MeshIO.readLineMesh2D(refMeshFile).get
    val refLMs3D = LandmarkIO.readLandmarksJson[_3D](refLmFile).get
    val refLMs2D = refLMs3D.map(lm => Landmark[_2D](lm.id, Point2D(lm.point.x, lm.point.y), lm.description, lm.uncertainty))

    val middlePoint = getLmPoint[_2D](refLMs2D, "middle")
    val endPoint1 = getLmPoint[_2D](refLMs2D, "finger.ulnar.middle")
    val endPoint2 = getLmPoint[_2D](refLMs2D, "finger.radial.middle")

    val points = getPointsBetweenLMs(refMesh, middlePoint, Seq(endPoint1, endPoint2))
    val refCut2Dopen = LineMeshConverter.createPartialMesh(refMesh, points)

    val closedLineList = LineList(refCut2Dopen.topology.lines ++ IndexedSeq(LineCell(refCut2Dopen.pointSet.findClosestPoint(endPoint1).id, refCut2Dopen.pointSet.findClosestPoint(endPoint2).id)))
    val refCut2D = LineMesh2D(refCut2Dopen.pointSet, closedLineList)
    val points3D = points.map(p => Point3D(p.x, p.y, 0.0))

    val ref3D = LineMeshConverter.lineMesh2Dto3D(refCut2D)
    val ui = ScalismoUI()
    ui.show(ref3D, "ref")
    ui.show(refLMs3D, "lms")
    ui.show(CreateUnstructuredPointsDomain3D.create(points3D), "cut")

    MeshIO.writeLineMesh[_2D](refCut2D, refMeshOutFile)
  }
}
