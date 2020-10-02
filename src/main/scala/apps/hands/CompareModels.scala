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

package apps.hands

import java.io._

import apps.scalismoExtension.LineModelMetrics
import scalismo.geometry._2D
import scalismo.io.{MeshIO, StatisticalModelIO}
import scalismo.mesh.LineMesh
import scalismo.statisticalmodel.PointDistributionModel
import scalismo.statisticalmodel.dataset.DataCollection
import scalismo.statisticalmodel.dataset.DataCollection.LineMeshDataCollection
import scalismo.utils.Random.implicits.randomGenerator
import spray.json.DefaultJsonProtocol._
import spray.json.{RootJsonFormat, _}

import scala.collection.mutable.ListBuffer

case class jsonModelComparisonFormatClass(datatype: String, model: String, path: String, compactness: Seq[Double], specificity: Seq[Double], generalization: Seq[Double])

object JsonModelComparisonFormat {
  implicit val myJsonModelComparisonLogger: RootJsonFormat[jsonModelComparisonFormatClass] = jsonFormat6(jsonModelComparisonFormatClass.apply)
}

object ComparePCAmodels {

  import JsonModelComparisonFormat._

  val experimentName = "allFingers15pMissing"

  val experimentPath = new File(Paths.handPath, s"shapemi/${experimentName}")
  val pcaPath = new File(experimentPath, s"pca")
  val pcaGTPath = new File(pcaPath, "gt")
  val pcaMAPPath = new File(pcaPath, "map")
  val pcaMEANPath = new File(pcaPath, "mean")
  val pcaSamplePath = new File(pcaPath, "sample")

  val resultPath = new File(Paths.handPath, "shapemi/out")
  resultPath.mkdir()
  val resultOutJsonLogFile = new File(resultPath, "experiments.json")

  val registeredPath = new File(Paths.handPath, s"registered/mesh")

  println(s"PCA path: ${pcaPath}, experiment path: ${experimentPath}, json output: ${resultOutJsonLogFile}")

  def computeCompactness(model: PointDistributionModel[_2D, LineMesh]): IndexedSeq[Double] = {
    (1 to model.rank).map { i =>
      model.gp.variance.data.take(i).sum
    }
  }

  // 10.000 samples used in the original paper
  // Minimal model specificity is important when newly generated objects need to be correct.
  // For shape analysis this is less important.
  def computeSpecificity(model: PointDistributionModel[_2D, LineMesh], trainingData: Seq[LineMesh[_2D]]): IndexedSeq[Double] = {
    (1 to model.rank).map { i =>
      LineModelMetrics.specificity(model.truncate(i), trainingData, 1000)
    }
  }

  def getGeneralization(gtDatasetFiles: Seq[File], modelPath: File, percentage: Option[Int]): IndexedSeq[Double] = {
    val fullMAPRes: Array[IndexedSeq[Double]] = gtDatasetFiles.map { f =>
      val name = f.getName.replace(".vtk", "")
      val cutSpecifier = if (percentage.nonEmpty) f"_${percentage.get}" else ""
      val modelFile = new File(modelPath, s"pca_keepOut_${name}${cutSpecifier}.h5")
      val fullModel = StatisticalModelIO.readStatisticalLineMeshModel2D(modelFile).get
      val keepOutMesh = MeshIO.readLineMesh2D(f).get
      computeGeneralization(fullModel, keepOutMesh)
    }.toArray
    reduceResults(fullMAPRes)
  }

  def computeGeneralization(model: PointDistributionModel[_2D, LineMesh], mesh: LineMesh[_2D]): IndexedSeq[Double] = {
    val dc: LineMeshDataCollection[_2D] = DataCollection.fromLineMeshSequence[_2D](model.reference, Seq(mesh))
    val res = (1 to model.rank).map { i =>
      LineModelMetrics.generalization(model.truncate(i), dc).get
    }
    res
  }

  private def reduceResults(results: Array[IndexedSeq[Double]]): IndexedSeq[Double] = {
    val res = results.reduce((a, b) => (a zip b).map { case (x, y) => x + y })
    res.map(f => f / results.length)
  }

  def computeModelMetrics(): ListBuffer[jsonModelComparisonFormatClass] = {
    val logger: ListBuffer[jsonModelComparisonFormatClass] = new ListBuffer[jsonModelComparisonFormatClass]

    val gtDatasetFiles = registeredPath.listFiles(f => f.getName.endsWith(".vtk"))
    val gtDataset = gtDatasetFiles.map(f => MeshIO.readLineMesh2D(f).get).toIndexedSeq

    println(" - Model from complete data")
    val gtModelFile = new File(pcaGTPath, "pca_full.h5")
    val fullModel = StatisticalModelIO.readStatisticalLineMeshModel2D(gtModelFile).get

    logger += jsonModelComparisonFormatClass(
      "gt",
      gtModelFile.getName.replace(".h5", "_0"),
      gtModelFile.getPath,
      computeCompactness(fullModel),
      computeSpecificity(fullModel, gtDataset),
      getGeneralization(gtDatasetFiles, pcaGTPath, None)
    )

    println(" - MAP models")
    val MAPdatasetFiles = pcaMAPPath.listFiles(f => f.getName.endsWith(".h5") && f.getName.startsWith("pca_full_")).sorted
    MAPdatasetFiles.par.foreach { f =>
      val cleanModelName = f.getName.replace(".h5", "")
      val percentage = cleanModelName.split("_").last.toInt
      val model = StatisticalModelIO.readStatisticalLineMeshModel2D(f).get
      println(s"Model: ${f.getName} with ${percentage}%")

      logger += jsonModelComparisonFormatClass(
        "map",
        cleanModelName,
        f.getPath,
        computeCompactness(model),
        computeSpecificity(model, gtDataset),
        getGeneralization(gtDatasetFiles, pcaMAPPath, Option(percentage))
      )
    }

    println(" - MEAN models")
    val MEANdatasetFiles = pcaMEANPath.listFiles(f => f.getName.endsWith(".h5") && f.getName.startsWith("pca_full_")).sorted
    MEANdatasetFiles.par.foreach { f =>
      val cleanModelName = f.getName.replace(".h5", "")
      val percentage = cleanModelName.split("_").last.toInt
      val model = StatisticalModelIO.readStatisticalLineMeshModel2D(f).get
      println(s"Model: ${f.getName} with ${percentage}%")

      logger += jsonModelComparisonFormatClass(
        "mean",
        cleanModelName,
        f.getPath,
        computeCompactness(model),
        computeSpecificity(model, gtDataset),
        getGeneralization(gtDatasetFiles, pcaMEANPath, Option(percentage))
      )
    }

    println(" - Sample models")
    val SAMPLEdatasetFiles = pcaSamplePath.listFiles(f => f.getName.endsWith(".h5") && f.getName.startsWith("pca_full_")).sorted
    SAMPLEdatasetFiles.par.foreach { f =>
      val cleanModelName = f.getName.replace(".h5", "")
      val percentage = cleanModelName.split("_").last.toInt
      val model = StatisticalModelIO.readStatisticalLineMeshModel2D(f).get
      println(s"Model: ${f.getName} with ${percentage}%")

      logger += jsonModelComparisonFormatClass(
        "sample",
        cleanModelName,
        f.getPath,
        computeCompactness(model),
        computeSpecificity(model, gtDataset),
        getGeneralization(gtDatasetFiles, pcaSamplePath, Option(percentage))
      )
    }
    logger
  }

  def writeLog(logger: ListBuffer[jsonModelComparisonFormatClass], outFile: File): Unit = {
    println(s"Writing log file to ${outFile}")
    val content = logger.toIndexedSeq
    try {
      val writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(outFile.toString)))
      writer.write(content.toList.toJson.prettyPrint)
      writer.close()
    } catch {
      case e: Exception => throw new IOException("Writing JSON log file failed!")
    }
    println("Log written to: " + outFile.toString)
  }

  def main(args: Array[String]) {
    scalismo.initialize()
    println("Compare Statistical PDM models...")
    val log = computeModelMetrics()
    writeLog(log, resultOutJsonLogFile)
  }
}
