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

package api.sampling2D.loggers

import java.io._
import java.text.SimpleDateFormat
import java.util.Calendar

import spray.json.DefaultJsonProtocol._
import spray.json._

import scala.collection.mutable.ListBuffer
import scala.io.Source

case class jsonCPDExperimentFormat(index: Int, modelPath: String, targetPath: String, coeffCPD: Seq[Double],
                                   cpd: Map[String, Double], datetime: String, comment: String)


object JsonCPDExperimentProtocol {
  implicit val myJsonFormatExperiment: RootJsonFormat[jsonCPDExperimentFormat] = jsonFormat7(jsonCPDExperimentFormat.apply)
}

case class JSONCPDExperimentLogger(filePath: File, modelPath: String = "") {

  import JsonCPDExperimentProtocol._

  val experiments: ListBuffer[jsonCPDExperimentFormat] = new ListBuffer[jsonCPDExperimentFormat]

  if (!filePath.getParentFile.exists()) {
    throw new IOException(s"JSON log path does not exist: ${filePath.getParentFile.toString}!")
  }
  else if (filePath.exists() && !filePath.canWrite) {
    throw new IOException(s"JSON file exist and cannot be overwritten: ${filePath.toString}!")
  }
  else if (!filePath.exists()) {
    try {
      filePath.createNewFile()
      filePath.delete()
    }
    catch {
      case e: Exception => throw new IOException(s"JSON file path cannot be written to: ${filePath.toString}!")
    }
  }

  filePath.setReadable(true, false)
  filePath.setExecutable(true, false)
  filePath.setWritable(true, false)
  private val datetimeFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss")

  def append(index: Int, targetPath: String = "", coeffCPD: Seq[Double], cpd: Map[String, Double], comment: String): Unit = {
    experiments += jsonCPDExperimentFormat(index = index, modelPath = modelPath, targetPath = targetPath, coeffCPD = coeffCPD,
      cpd = cpd, datetime = datetimeFormat.format(Calendar.getInstance().getTime()), comment = comment)
  }

  def writeLog(): Unit = {
    val content = experiments.toIndexedSeq
    try {
      val writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(filePath.toString)))
      writer.write(content.toList.toJson.prettyPrint)
      writer.close()
    } catch {
      case e: Exception => throw new IOException("Writing JSON log file failed!")
    }
    println("Log written to: " + filePath.toString)
  }

  def loadLog(): IndexedSeq[jsonCPDExperimentFormat] = {
    println(s"Loading JSON log file: ${filePath.toString}")
    Source.fromFile(filePath.toString).mkString.parseJson.convertTo[IndexedSeq[jsonCPDExperimentFormat]]
  }
}
