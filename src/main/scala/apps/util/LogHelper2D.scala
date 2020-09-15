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

import java.io.File

import api.sampling.ModelFittingParameters
import api.sampling.loggers.{JSONAcceptRejectLogger, jsonLogFormat}
import apps.scalismoExtension.LineMeshConverter
import scalismo.common.UnstructuredPoints.Create.CreateUnstructuredPoints2D
import scalismo.geometry.{EuclideanVector, _2D, _3D}
import scalismo.mesh.{LineMesh, LineMesh3D}
import scalismo.statisticalmodel.PointDistributionModel

case class LogHelper2D(file: File, model: PointDistributionModel[_2D, LineMesh], burnInPhase: Int = 200) {
  println(s"Reading log file: ${file}")
  val reference: LineMesh[_2D] = model.reference
  val logObj = new JSONAcceptRejectLogger[ModelFittingParameters](file)
  val log: IndexedSeq[jsonLogFormat] = logObj.loadLog()

  val firstAccept: Int = log.filter(f => f.status).head.index
  val burnIn: Int = math.max(firstAccept + 1, burnInPhase)

  println(s"Log length: ${log.length}, first accept: ${firstAccept}, burnInPhase: ${burnIn}")

  def sample(takeEveryN: Int = 50, total: Int = 100, randomize: Boolean = false): IndexedSeq[(jsonLogFormat, Int)] = {
    println("Log length: " + log.length)
    val indexes = (burnIn until math.min(log.length, total) by takeEveryN).map(i => getLogIndex(i))
    val filtered = indexes.map(i => (log(i), i))
    val samples = filtered.take(math.min(total, filtered.length))
    if (randomize) scala.util.Random.shuffle(samples)
    else samples
  }

  def sampleMeshes2D(takeEveryN: Int = 50, total: Int = 100, randomize: Boolean = false): IndexedSeq[LineMesh[_2D]] = {
    val filteredLogs = sample(takeEveryN, total, randomize)
    filteredLogs.map { l => logToMesh(l._1) }
  }

  def sampleMeshes3D(takeEveryN: Int = 50, total: Int = 1000000, randomize: Boolean = false): IndexedSeq[LineMesh[_3D]] = {
    val meshes = sampleMeshes2D(takeEveryN, total, randomize)
    meshes.map(m => LineMeshConverter.lineMesh2Dto3D(m))
  }

  def mapMesh3D(): LineMesh3D = {
    LineMeshConverter.lineMesh2Dto3D(mapMesh2D())
  }

  def mapMesh2D(): LineMesh[_2D] = {
    val map = model.instance(logObj.getBestFittingParsFromJSON.shapeParameters.parameters)
    map
  }

  def meanMesh2D(takeEveryN: Int = 1): LineMesh[_2D] = {
    val indexes = (burnIn until log.length by takeEveryN).map(i => getLogIndex(i))
    val filtered = indexes.map(i => (log(i)))

    val nMeshes = filtered.length
    println(s"Computing mean from: ${nMeshes}/${log.length - burnIn} in steps of ${takeEveryN}")

    val meanDeformations = reference.pointSet.pointIds.map(id => {
      var meanDeformationForId = EuclideanVector(0, 0)
      filtered.foreach(l => { // loop through meshes
        val mesh = logToMesh(l)
        val deformationAtId = mesh.pointSet.point(id) - reference.pointSet.point(id)
        meanDeformationForId += deformationAtId * (1.0 / nMeshes)
      })
      meanDeformationForId
    })
    println("mean computed")
    //    val meanDeformationField = DiscreteField[_2D, UnstructuredPointsDomain[_2D], EuclideanVector[_2D]](
    //      reference.pointSet,
    //      meanDeformations.toIndexedSeq
    //    )
    //    val continuousMeanDeformationField = meanDeformationField.interpolate(NearestNeighborInterpolator2D())
    //    val meanTransformation = Transformation((pt : Point[_2D]) => pt + continuousMeanDeformationField(pt))
    val meanPoints = (reference.pointSet.points.toIndexedSeq zip meanDeformations.toIndexedSeq).map { case (p, v) => (p + v) }
    val meanMesh = LineMesh[_2D](CreateUnstructuredPoints2D.create(meanPoints), reference.topology)
    println("finish")
    meanMesh
  }

  def meanMesh3D(takeEveryN: Int = 1): LineMesh3D = {
    LineMeshConverter.lineMesh2Dto3D(meanMesh2D(takeEveryN))
  }

  @scala.annotation.tailrec
  private def getLogIndex(i: Int): Int = {
    if (log(i).status) i
    else getLogIndex(i - 1)
  }

  private def logToMesh(l: jsonLogFormat): LineMesh[_2D] = {
    model.instance(JSONAcceptRejectLogger.sampleToModelParameters(l).shapeParameters.parameters)
  }
}
