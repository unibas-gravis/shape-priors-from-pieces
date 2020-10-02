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

import api.sampling2D.ModelFittingParameters
import api.sampling2D.loggers.{JSONAcceptRejectLogger, jsonLogFormat}
import apps.util.VisualizationHelper.{pointSetFromDenseVector, vectorizePointSet}
import scalismo.common.UnstructuredPoints.Create.CreateUnstructuredPoints2D
import scalismo.geometry._2D
import scalismo.mesh.LineMesh
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

  def mapMesh2D(): LineMesh[_2D] = {
    val map = ModelFittingParameters.transformedMesh(model, logObj.getBestFittingParsFromJSON)
    map
  }

  def meanMesh2D(takeEveryN: Int = 1): LineMesh[_2D] = {
    val indexes = (burnIn until log.length by takeEveryN).map(i => getLogIndex(i))
    val filtered = indexes.map(i => (log(i)))
    val nMeshes = filtered.length
    println(s"Computing mean from: ${nMeshes}/${log.length - burnIn} in steps of ${takeEveryN}")
    val vectorizedPoints = filtered.map { l =>
      val mesh = logToMesh(l)
      vectorizePointSet(mesh.pointSet)
    }
    val meanVector = vectorizedPoints.reduce(_ + _) * (1.0 / nMeshes)
    val meanPoints = pointSetFromDenseVector[_2D](meanVector)
    val meanMesh = LineMesh[_2D](meanPoints, reference.topology)
    meanMesh
  }

  @scala.annotation.tailrec
  private def getLogIndex(i: Int): Int = {
    if (log(i).status) i
    else getLogIndex(i - 1)
  }

  private def logToMesh(l: jsonLogFormat): LineMesh[_2D] = {
    ModelFittingParameters.transformedMesh(model, JSONAcceptRejectLogger.sampleToModelParameters(l))
  }
}
