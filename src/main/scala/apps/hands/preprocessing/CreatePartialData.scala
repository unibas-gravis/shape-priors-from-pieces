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

package apps.hands.preprocessing

import java.io.File

import apps.util.myPaths
import scalismo.common.PointId
import scalismo.common.UnstructuredPoints.Create.CreateUnstructuredPoints2D
import scalismo.geometry._
import scalismo.io.{LandmarkIO, MeshIO}
import scalismo.mesh.{LineCell, LineList, LineMesh, LineMesh2D}

import scala.annotation.tailrec

object CreatePartialData {

  @tailrec
  def walkPoints(mesh: LineMesh2D, currentIds: IndexedSeq[PointId], numOfPoints: Int): IndexedSeq[PointId] = {
    val nearby = currentIds.flatMap(id => mesh.topology.adjacentPointsForPoint(id))
    val all = nearby.toSet.union(currentIds.toSet).toIndexedSeq
    if (all.length >= numOfPoints) all
    else walkPoints(mesh, all, numOfPoints)
  }


  def getPointsAroundLM(mesh: LineMesh2D, middlePoint: Point2D, numOfPoints: Int): IndexedSeq[Point[_2D]] = {
    val points = IndexedSeq(mesh.pointSet.findClosestPoint(middlePoint).id)
    walkPoints(mesh, points, numOfPoints).map(id => mesh.pointSet.point(id))
  }

  def getLmPoint[A](lmSeq: Seq[Landmark[A]], name: String): Point[A] = {
    lmSeq.find(_.id == name).get.point
  }

  def createPartialMesh(mesh: LineMesh2D, pId: PointId, area: Int): LineMesh[_2D] = {
    val p = mesh.pointSet.point(pId)
    val removeList: Seq[Point[_2D]] = mesh.pointSet.findNClosestPoints(p, area).map(_.point)
    createPartialMesh(mesh, removeList)
  }

  def createPartialMesh(mesh: LineMesh2D, removeList: Seq[Point[_2D]]): LineMesh[_2D] = {
    val meshPoints = mesh.pointSet.points.toIndexedSeq

    val remainingPoints = meshPoints.par.filter { p => !removeList.contains(p) }.zipWithIndex.toMap

    val remainingPointDoublets = mesh.cells.par.map { cell =>
      val points = cell.pointIds.map(pointId => meshPoints(pointId.id))
      (points, points.map(p => remainingPoints.contains(p)).reduce(_ && _))
    }.filter(_._2).map(_._1)

    val points = remainingPointDoublets.flatten.distinct
    val pt2Id = points.zipWithIndex.toMap
    val cells = remainingPointDoublets.map {
      vec => LineCell(PointId(pt2Id(vec(0))), PointId(pt2Id(vec(1))))
    }

    LineMesh2D(
      CreateUnstructuredPoints2D.create(points.toIndexedSeq),
      LineList(cells.toIndexedSeq)
    )
  }


  def main(args: Array[String]) {
    scalismo.initialize()

    val alignedFiles = new File(myPaths.handsPath, "aligned/mesh").listFiles(_.getName.endsWith(".vtk")).sorted
    val alignedLMFiles = new File(myPaths.handsPath, "aligned/landmarks").listFiles(_.getName.endsWith(".json"))

    alignedFiles.foreach { targetFile =>
      val targetname = targetFile.getName.replace(".vtk", "")
      val targetLMFile = alignedLMFiles.find(f => f.getName == targetname + ".json").get

      println(targetFile)
      println(targetLMFile)

      val targetFull = MeshIO.readLineMesh2D(targetFile).get
      val targetLM = LandmarkIO.readLandmarksJson[_2D](targetLMFile).get

      val fingerSeq = Seq("thumb", "index", "long", "ring", "small")
      fingerSeq.foreach { finger =>

        val middlePointId = s"finger.${finger}.tip"
        val middlePoint = getLmPoint[_2D](targetLM, middlePointId)

        val percentageCutSeq = Seq(5, 10, 15, 20, 30, 40, 50, 60, 70, 80, 90)
        percentageCutSeq.foreach { percentageCut =>

          val outputPathLM = new File(myPaths.handsPath, s"partial/landmarks")
          outputPathLM.mkdirs()
          val outputPathMesh = new File(myPaths.handsPath, s"partial/mesh")
          outputPathMesh.mkdirs()
          val outputFileLM = new File(outputPathLM, s"${targetname}_${finger}_${percentageCut}.json")
          val outputFileMesh = new File(outputPathMesh, s"${targetname}_${finger}_${percentageCut}.vtk")

          val numberOfPointsToCut: Int = targetFull.pointSet.numberOfPoints * percentageCut / 100
          val points = getPointsAroundLM(targetFull, middlePoint, numberOfPointsToCut)
          val refCut2Dopen = createPartialMesh(targetFull, points)

          val m2Dboundary = refCut2Dopen.pointSet.pointIds.toIndexedSeq.filter(id => refCut2Dopen.topology.adjacentPointsForPoint(id).length < 2)
          val closedLineList2 = LineList(refCut2Dopen.topology.lines ++ IndexedSeq(LineCell(m2Dboundary.last, m2Dboundary.head)))
          val refCut2D = LineMesh2D(refCut2Dopen.pointSet, closedLineList2)

          println(s"Number of boundary landmarks ${m2Dboundary.length}")

          MeshIO.writeLineMesh(refCut2D, outputFileMesh)

          val filteredTargetLMs = targetLM.filter(lm => (refCut2D.pointSet.findClosestPoint(lm.point).point - lm.point).norm < 2.0)

          LandmarkIO.writeLandmarksJson[_2D](filteredTargetLMs, outputFileLM)
          println(s"Number of retained landmarks ${filteredTargetLMs.length}/${targetLM.length}")
        }
      }
    }
  }
}
