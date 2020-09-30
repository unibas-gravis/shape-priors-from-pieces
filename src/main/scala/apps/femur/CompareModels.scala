package apps.femur

import java.io._

import scalismo.geometry._3D
import scalismo.io.{MeshIO, StatisticalModelIO}
import scalismo.mesh.TriangleMesh
import scalismo.statisticalmodel.dataset.DataCollection.TriangleMeshDataCollection
import scalismo.statisticalmodel.dataset.{DataCollection, ModelMetrics}
import scalismo.statisticalmodel.{PointDistributionModel, StatisticalMeshModel}
import scalismo.utils.Random.implicits._
import spray.json.DefaultJsonProtocol._
import spray.json.{RootJsonFormat, _}

import scala.collection.mutable.ListBuffer

case class jsonModelComparisonFormatClass(datatype: String, model: String, path: String, compactness: Seq[Double], specificity: Seq[Double], generalization: Seq[Double])

object JsonModelComparisonFormat {
  implicit val myJsonModelComparisonLogger: RootJsonFormat[jsonModelComparisonFormatClass] = jsonFormat6(jsonModelComparisonFormatClass.apply)
}

object CompareModels {

  import JsonModelComparisonFormat._

  val modelIndexThreshold: Int = 10

  val basePath = Paths.generalPath
  val pcaPath = new File(basePath, "shapemi/pca")
  val gtPath = new File(pcaPath, "gt")
  val mapPath = new File(pcaPath, "map")
  val meanPath = new File(pcaPath, "mean")
  val samplePath = new File(pcaPath, "sample")

  val resultPath = new File(basePath, "shapemi/out")
  resultPath.mkdir()
  val resultOutJsonLogFile = new File(resultPath, "experiments.json")

  def computeCompactness(model: StatisticalMeshModel): IndexedSeq[Double] = {
    println("Computing compactness")
    (1 to model.rank).map { i =>
      model.gp.variance.data.take(i).sum
    }
  }

  def computeSpecificity(model: StatisticalMeshModel, trainingData: Seq[TriangleMesh[_3D]]): IndexedSeq[Double] = {
    println("Computing specificity")
    (1 to model.rank).map { i =>
      val pdm = PointDistributionModel(model.truncate(i).gp)
      val tmp = ModelMetrics.specificity(pdm, trainingData, 1000)
      tmp
    }
  }

  def computeGeneralization(model: StatisticalMeshModel, meshes: Seq[TriangleMesh[_3D]]): IndexedSeq[Double] = {
    println("Computing regularization")
    val dc: TriangleMeshDataCollection[_3D] = DataCollection.fromTriangleMesh3DSequence(model.referenceMesh, meshes)
    val res = (1 to model.rank).map { i =>
      val pdm = PointDistributionModel(model.truncate(i).gp)
      val tmp = ModelMetrics.generalization(pdm, dc).get
      tmp
    }
    res
  }

  def computeModelMetrics(): ListBuffer[jsonModelComparisonFormatClass] = {
    val logger: ListBuffer[jsonModelComparisonFormatClass] = new ListBuffer[jsonModelComparisonFormatClass]

    val meshesGtPath = new File(basePath, "registered/meshes").listFiles(f => f.getName.endsWith(".stl") && f.getName.replace(".stl", "").toInt < modelIndexThreshold)
    val meshesValidationPath = new File(basePath, "registered/meshes").listFiles(f => f.getName.endsWith(".stl") && f.getName.replace(".stl", "").toInt >= modelIndexThreshold)

    val gtMeshes = meshesGtPath.map(f => MeshIO.readMesh(f).get)
    val validationMeshes = meshesGtPath.map(f => MeshIO.readMesh(f).get)

    val gtModelFile = new File(gtPath, "complete_0.h5")
    val mapModelFile = new File(mapPath, "map_0.h5")
    val meanModelFile = new File(meanPath, "mean_0.h5")
    val gtModel = StatisticalModelIO.readStatisticalMeshModel(gtModelFile).get
    val mapModel = StatisticalModelIO.readStatisticalMeshModel(mapModelFile).get
    val meanModel = StatisticalModelIO.readStatisticalMeshModel(meanModelFile).get
    val samplesModelsPath = samplePath.listFiles(_.getName.endsWith(".h5"))

    println("GT model")
    logger += jsonModelComparisonFormatClass(
      "gt",
      gtModelFile.getName.replace(".h5", ""),
      gtModelFile.getPath,
      computeCompactness(gtModel),
      computeSpecificity(gtModel, gtMeshes),
      computeGeneralization(gtModel, validationMeshes)
    )

    println("MAP model")
    logger += jsonModelComparisonFormatClass(
      "map",
      mapModelFile.getName.replace(".h5", ""),
      mapModelFile.getPath,
      computeCompactness(mapModel),
      computeSpecificity(mapModel, gtMeshes),
      computeGeneralization(mapModel, validationMeshes)
    )

    println("MEAN model")
    logger += jsonModelComparisonFormatClass(
      "mean",
      meanModelFile.getName.replace(".h5", ""),
      meanModelFile.getPath,
      computeCompactness(meanModel),
      computeSpecificity(meanModel, gtMeshes),
      computeGeneralization(meanModel, validationMeshes)
    )

    println("Sample model")
    samplesModelsPath.sorted.foreach { sampleModelFile =>
      println(s" - ${sampleModelFile}")
      val sampleModel = StatisticalModelIO.readStatisticalMeshModel(sampleModelFile).get
      logger += jsonModelComparisonFormatClass(
        "sample",
        sampleModelFile.getName.replace(".h5", ""),
        sampleModelFile.getPath,
        computeCompactness(sampleModel),
        computeSpecificity(sampleModel, gtMeshes),
        computeGeneralization(sampleModel, validationMeshes)
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
    println("Compare PCAs...")
    val log = computeModelMetrics()
    writeLog(log, resultOutJsonLogFile)
  }
}
