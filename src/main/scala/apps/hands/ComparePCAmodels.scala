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
import apps.util.myPaths
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

  //  val registeredTargetStartWith = "test-0-"
  val registeredTargetStartWith = "hand-"

  //  val experimentName = "shapemi_scale_06_25"
  val experimentName = "shapemi_gauss_per_06_25"
  //    val experimentName = "shapemi_skel_ablation_07_03"

  val experimentPath = new File(myPaths.handsPath, s"experiments/${experimentName}")

  val pcaPath = new File(experimentPath, s"pca_random-finger")


  val resultOutJsonLogFile = new File(experimentPath, s"out/experimentLog_${experimentName}_random-finger.json")
  val registeredPath = new File(myPaths.handsPath, s"registered/")

  println(s"PCA path: ${pcaPath}, experiment path: ${experimentPath}, json output: ${resultOutJsonLogFile}")

  //  val pSeq = Seq(5, 10, 15, 20, 30, 40, 50, 60, 70, 80, 90)
  val pSeq = Seq(20)

  def printModelGeneralization: Unit = {
    println("Generalization:")
    val gtDatasetFiles = new File(myPaths.handsPath, "registered/mesh").listFiles(_.getName.endsWith(".vtk"))

    println("Full model")
    val fullRes: Array[IndexedSeq[Double]] = gtDatasetFiles.map { f =>
      val name = f.getName.replace(".vtk", "")
      val modelFile = new File(pcaPath, s"gt/pca_keepOut_${name}.h5")
      val fullModel = StatisticalModelIO.readStatisticalLineMeshModel2D(modelFile).get
      val keepOutMesh = MeshIO.readLineMesh2D(f).get
      val res: IndexedSeq[Double] = computeGeneralization(fullModel, keepOutMesh)
      //      println(s"Model: ${modelFile}, PC: ${fullModel.rank}, keepOut: ${name}")
      //      println(res)
      res
    }
    println(reduceResults(fullRes))

    println("MAP models")
    pSeq.foreach { p =>
      println(s"Percentage cut off: ${p}")
      val fullMAPRes: Array[IndexedSeq[Double]] = gtDatasetFiles.map { f =>
        val name = f.getName.replace(".vtk", "")
        val modelFile = new File(pcaPath, s"map/pca_keepOut_${name}_${p}.h5")
        val fullModel = StatisticalModelIO.readStatisticalLineMeshModel2D(modelFile).get
        val keepOutMesh = MeshIO.readLineMesh2D(f).get
        val res: IndexedSeq[Double] = computeGeneralization(fullModel, keepOutMesh)
        //      println(s"Model: ${modelFile}, PC: ${fullModel.rank}, keepOut: ${name}")
        //      println(res)
        res
      }
      println(reduceResults(fullMAPRes))
    }

    println("MEAN models")
    pSeq.foreach { p =>
      println(s"Percentage cut off: ${p}")
      val fullMAPRes: Array[IndexedSeq[Double]] = gtDatasetFiles.map { f =>
        val name = f.getName.replace(".vtk", "")
        val modelFile = new File(pcaPath, s"mean/pca_keepOut_${name}_${p}.h5")
        val fullModel = StatisticalModelIO.readStatisticalLineMeshModel2D(modelFile).get
        val keepOutMesh = MeshIO.readLineMesh2D(f).get
        val res: IndexedSeq[Double] = computeGeneralization(fullModel, keepOutMesh)
        //      println(s"Model: ${modelFile}, PC: ${fullModel.rank}, keepOut: ${name}")
        //      println(res)
        res
      }
      println(reduceResults(fullMAPRes))
    }

    println("SAMPLES models")
    pSeq.foreach { p =>
      println(s"Percentage cut off: ${p}")
      val fullSAMPLERes: Array[IndexedSeq[Double]] = gtDatasetFiles.map { f =>
        val name = f.getName.replace(".vtk", "")
        val modelFile = new File(pcaPath, s"sample/pca_keepOut_${name}_${p}.h5")
        val fullModel = StatisticalModelIO.readStatisticalLineMeshModel2D(modelFile).get
        val keepOutMesh = MeshIO.readLineMesh2D(f).get
        val res: IndexedSeq[Double] = computeGeneralization(fullModel, keepOutMesh)
        //      println(s"Model: ${modelFile}, PC: ${fullModel.rank}, keepOut: ${name}")
        //      println(res)
        res
      }
      println(reduceResults(fullSAMPLERes))
    }


  }

  def printModelSpecificity: Unit = {
    println("Specificity:")
    val gtDatasetFiles = new File(registeredPath, "mesh").listFiles(f => f.getName.endsWith(".vtk") && f.getName.startsWith(registeredTargetStartWith))
    val gtDataset = gtDatasetFiles.map(f => MeshIO.readLineMesh2D(f).get).toIndexedSeq

    println("Full model")
    val fullModel = StatisticalModelIO.readStatisticalLineMeshModel2D(new File(pcaPath, "gt/pca_full.h5")).get
    println(computeSpecificity(fullModel, gtDataset))

    println("MAP models")
    val MAPdatasetFiles = new File(pcaPath, "map").listFiles(f => f.getName.endsWith(".h5") && f.getName.startsWith("pca_full_")).sorted
    MAPdatasetFiles.foreach { f =>
      println(s"Model: ${f.getName}")
      val model = StatisticalModelIO.readStatisticalLineMeshModel2D(f).get
      println(computeSpecificity(model, gtDataset))
    }
    println("MEAN models")
    val MEANdatasetFiles = new File(pcaPath, "map").listFiles(f => f.getName.endsWith(".h5") && f.getName.startsWith("pca_full_")).sorted
    MEANdatasetFiles.foreach { f =>
      println(s"Model: ${f.getName}")
      val model = StatisticalModelIO.readStatisticalLineMeshModel2D(f).get
      println(computeSpecificity(model, gtDataset))
    }
    println("Sample models")
    val SAMPLEdatasetFiles = new File(pcaPath, "sample").listFiles(f => f.getName.endsWith(".h5") && f.getName.startsWith("pca_full_")).sorted
    SAMPLEdatasetFiles.foreach { f =>
      println(s"Model: ${f.getName}")
      val model = StatisticalModelIO.readStatisticalLineMeshModel2D(f).get
      println(computeSpecificity(model, gtDataset))
    }
  }

  def printModelCompactness = {
    println("Compactness:")
    val gtDatasetFiles = new File(registeredPath, "mesh").listFiles(f => f.getName.endsWith(".vtk") && f.getName.startsWith(registeredTargetStartWith))
    val gtDataset = gtDatasetFiles.map(f => MeshIO.readLineMesh2D(f).get).toIndexedSeq

    println("Full model")
    val fullModel = StatisticalModelIO.readStatisticalLineMeshModel2D(new File(pcaPath, "gt/pca_full.h5")).get
    println(computeCompactness(fullModel))

    println("MAP models")
    val MAPdatasetFiles = new File(pcaPath, "map").listFiles(f => f.getName.endsWith(".h5") && f.getName.startsWith("pca_full_")).sorted
    MAPdatasetFiles.foreach { f =>
      println(s"Model: ${f.getName}")
      val model = StatisticalModelIO.readStatisticalLineMeshModel2D(f).get
      println(computeCompactness(model))
    }
    println("MEAN models")
    val MEANdatasetFiles = new File(pcaPath, "mean").listFiles(f => f.getName.endsWith(".h5") && f.getName.startsWith("pca_full_")).sorted
    MEANdatasetFiles.foreach { f =>
      println(s"Model: ${f.getName}")
      val model = StatisticalModelIO.readStatisticalLineMeshModel2D(f).get
      println(computeCompactness(model))
    }
    println("Sample models")
    val SAMPLEdatasetFiles = new File(pcaPath, "sample").listFiles(f => f.getName.endsWith(".h5") && f.getName.startsWith("pca_full_")).sorted
    SAMPLEdatasetFiles.foreach { f =>
      println(s"Model: ${f.getName}")
      val model = StatisticalModelIO.readStatisticalLineMeshModel2D(f).get
      println(computeCompactness(model))
    }
  }

  def main(args: Array[String]) {
    println("Compare PCAs...")
    scalismo.initialize()

    val log = doSomeLogging
    writeLog(log, resultOutJsonLogFile)
    //    printModelSpecificity
    //    printModelGeneralization
  }

  def doSomeLogging: ListBuffer[jsonModelComparisonFormatClass] = {
    val logger: ListBuffer[jsonModelComparisonFormatClass] = new ListBuffer[jsonModelComparisonFormatClass]

    val gtDatasetFiles = new File(registeredPath, "mesh").listFiles(f => f.getName.endsWith(".vtk") && f.getName.startsWith(registeredTargetStartWith))
    val gtDataset = gtDatasetFiles.map(f => MeshIO.readLineMesh2D(f).get).toIndexedSeq

    println("GT model")
    val gtModelFile = new File(pcaPath, "gt/pca_full.h5")
    val fullModel = StatisticalModelIO.readStatisticalLineMeshModel2D(gtModelFile).get

    logger += jsonModelComparisonFormatClass(
      "gt",
      gtModelFile.getName.replace(".h5", "_0"),
      gtModelFile.getPath.toString,
      computeCompactness(fullModel),
      computeSpecificity(fullModel, gtDataset),
      getGTModelGeneralization(gtDatasetFiles)
    )

    println("MAP models")
    val MAPdatasetFiles = new File(pcaPath, "map").listFiles(f => f.getName.endsWith(".h5") && f.getName.startsWith("pca_full_")).sorted
    MAPdatasetFiles.par.foreach { f =>
      val cleanModelName = f.getName.replace(".h5", "")
      val percentage = cleanModelName.split("_").last.toInt
      val model = StatisticalModelIO.readStatisticalLineMeshModel2D(f).get
      println(s"Model: ${f.getName} with ${percentage}%")

      logger += jsonModelComparisonFormatClass(
        "map",
        cleanModelName,
        f.getPath.toString,
        computeCompactness(model),
        computeSpecificity(model, gtDataset),
        getMEANMAPModelGeneralization(gtDatasetFiles, percentage, "map")
      )
    }

    println("MEAN models")
    val MEANdatasetFiles = new File(pcaPath, "mean").listFiles(f => f.getName.endsWith(".h5") && f.getName.startsWith("pca_full_")).sorted
    MEANdatasetFiles.par.foreach { f =>
      val cleanModelName = f.getName.replace(".h5", "")
      val percentage = cleanModelName.split("_").last.toInt
      val model = StatisticalModelIO.readStatisticalLineMeshModel2D(f).get
      println(s"Model: ${f.getName} with ${percentage}%")

      logger += jsonModelComparisonFormatClass(
        "mean",
        cleanModelName,
        f.getPath.toString,
        computeCompactness(model),
        computeSpecificity(model, gtDataset),
        getMEANMAPModelGeneralization(gtDatasetFiles, percentage, "mean")
      )
    }

    println("Sample models")
    val SAMPLEdatasetFiles = new File(pcaPath, "sample").listFiles(f => f.getName.endsWith(".h5") && f.getName.startsWith("pca_full_")).sorted
    SAMPLEdatasetFiles.par.foreach { f =>
      val cleanModelName = f.getName.replace(".h5", "")
      val percentage = cleanModelName.split("_").last.toInt
      val model = StatisticalModelIO.readStatisticalLineMeshModel2D(f).get
      println(s"Model: ${f.getName} with ${percentage}%")

      logger += jsonModelComparisonFormatClass(
        "sample",
        cleanModelName,
        f.getPath.toString,
        computeCompactness(model),
        computeSpecificity(model, gtDataset),
        getSampleModelGeneralization(gtDatasetFiles, percentage)
      )
    }
    logger
  }

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
      val tmp = LineModelMetrics.specificity(model.truncate(i), trainingData, 1000)
      //      BigDecimal(tmp).setScale(4, BigDecimal.RoundingMode.HALF_UP).toDouble
      tmp
    }
  }

  def getGTModelGeneralization(gtDatasetFiles: Seq[File]): IndexedSeq[Double] = {
    println("Full model")
    val fullRes: Array[IndexedSeq[Double]] = gtDatasetFiles.map { f =>
      val name = f.getName.replace(".vtk", "")
      val modelFile = new File(pcaPath, s"gt/pca_keepOut_${name}.h5")
      val fullModel = StatisticalModelIO.readStatisticalLineMeshModel2D(modelFile).get
      val keepOutMesh = MeshIO.readLineMesh2D(f).get
      val res: IndexedSeq[Double] = computeGeneralization(fullModel, keepOutMesh)
      res
    }.toArray
    reduceResults(fullRes)
  }

  def getMEANMAPModelGeneralization(gtDatasetFiles: Seq[File], percentage: Int, specifier: String): IndexedSeq[Double] = {
    println(s"Percentage cut off: ${percentage}")
    val fullMAPRes: Array[IndexedSeq[Double]] = gtDatasetFiles.map { f =>
      val name = f.getName.replace(".vtk", "")
      val modelFile = new File(pcaPath, s"${specifier}/pca_keepOut_${name}_${percentage}.h5")
      val fullModel = StatisticalModelIO.readStatisticalLineMeshModel2D(modelFile).get
      val keepOutMesh = MeshIO.readLineMesh2D(f).get
      val res: IndexedSeq[Double] = computeGeneralization(fullModel, keepOutMesh)
      res
    }.toArray
    reduceResults(fullMAPRes)
  }

  def computeGeneralization(model: PointDistributionModel[_2D, LineMesh], mesh: LineMesh[_2D]): IndexedSeq[Double] = {
    val dc: LineMeshDataCollection[_2D] = DataCollection.fromLineMeshSequence[_2D](model.reference, Seq(mesh))
    val res = (1 to model.rank).map { i =>
      val tmp = LineModelMetrics.generalization(model.truncate(i), dc).get
      //      BigDecimal(tmp).setScale(4, BigDecimal.RoundingMode.HALF_UP).toDouble
      tmp
    }
    res
  }

  def reduceResults(results: Array[IndexedSeq[Double]]): IndexedSeq[Double] = {
    val res = results.reduce((a, b) => (a zip b).map { case (x, y) => x + y })
    res.map(f => f / results.length)
  }

  def getSampleModelGeneralization(gtDatasetFiles: Seq[File], percentage: Int): IndexedSeq[Double] = {
    println(s"Percentage cut off: ${percentage}")
    val fullSAMPLERes: Array[IndexedSeq[Double]] = gtDatasetFiles.map { f =>
      val name = f.getName.replace(".vtk", "")
      val modelFile = new File(pcaPath, s"sample/pca_keepOut_${name}_${percentage}.h5")
      val fullModel = StatisticalModelIO.readStatisticalLineMeshModel2D(modelFile).get
      val keepOutMesh = MeshIO.readLineMesh2D(f).get
      val res: IndexedSeq[Double] = computeGeneralization(fullModel, keepOutMesh)
      res
    }.toArray
    reduceResults(fullSAMPLERes)
  }

  def writeLog(logger: ListBuffer[jsonModelComparisonFormatClass], outFile: File): Unit = {
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
}
