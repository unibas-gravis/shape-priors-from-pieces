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
import scalismo.common.UnstructuredPointsDomain.Create.CreateUnstructuredPointsDomain3D
import scalismo.geometry.{EuclideanVector, _3D}
import scalismo.mesh.{TriangleMesh, TriangleMesh3D}
import scalismo.statisticalmodel.StatisticalMeshModel

case class LogHelper3D(file: File, model: StatisticalMeshModel, burnInPhase: Int = 200) {
  println(s"Reading log file: ${file}")
  val reference: TriangleMesh[_3D] = model.referenceMesh
  val logObj = new JSONAcceptRejectLogger[ModelFittingParameters](file)
  val log: IndexedSeq[jsonLogFormat] = logObj.loadLog()

  val firstAccept: Int = log.filter(f => f.status).head.index
  val burnIn: Int = math.max(firstAccept + 1, burnInPhase)

  println(s"Log length: ${log.length}, first accept: ${firstAccept}, burnInPhase: ${burnIn}")


  @scala.annotation.tailrec
  private def getLogIndex(i: Int): Int = {
    if (log(i).status) i
    else getLogIndex(i - 1)
  }

  private def logToMesh(l: jsonLogFormat): TriangleMesh[_3D] = {
    model.instance(JSONAcceptRejectLogger.sampleToModelParameters(l).shapeParameters.parameters)
  }

  def sample(takeEveryN: Int = 50, total: Int = 100, randomize: Boolean = false): IndexedSeq[(jsonLogFormat, Int)] = {
    println("Log length: " + log.length)
    val indexes = (burnIn until math.min(log.length, total) by takeEveryN).map(i => getLogIndex(i))
    val filtered = indexes.map(i => (log(i), i))
    val samples = filtered.take(math.min(total, filtered.length))
    if (randomize) scala.util.Random.shuffle(samples)
    else samples
  }

  def sampleMeshes(takeEveryN: Int = 50, total: Int = 100, randomize: Boolean = false): IndexedSeq[TriangleMesh[_3D]] = {
    val filteredLogs = sample(takeEveryN, total, randomize)
    filteredLogs.map { l => logToMesh(l._1) }
  }


  def mapMesh(): TriangleMesh[_3D] = {
    val map = model.instance(logObj.getBestFittingParsFromJSON.shapeParameters.parameters)
    map
  }


  def meanMesh(takeEveryN: Int = 1): TriangleMesh[_3D] = {
    val indexes = (burnIn until log.length by takeEveryN).map(i => getLogIndex(i))
    val filtered = indexes.map(i => (log(i)))

    val nMeshes = filtered.length
    println(s"Computing mean from: ${nMeshes}/${log.length - burnIn} in steps of ${takeEveryN}")

    val meanDeformations = reference.pointSet.pointIds.map(id => {
      var meanDeformationForId = EuclideanVector(0, 0, 0)
      filtered.foreach(l => { // loop through meshes
        val mesh = logToMesh(l)
        val deformationAtId = mesh.pointSet.point(id) - reference.pointSet.point(id)
        meanDeformationForId += deformationAtId * (1.0 / nMeshes)
      })
      meanDeformationForId
    })
    val meanPoints = (reference.pointSet.points.toIndexedSeq zip meanDeformations.toIndexedSeq).map { case (p, v) => (p + v) }
    val meanMesh = TriangleMesh3D(CreateUnstructuredPointsDomain3D.create(meanPoints).pointSet, reference.triangulation)
    meanMesh
  }

}
