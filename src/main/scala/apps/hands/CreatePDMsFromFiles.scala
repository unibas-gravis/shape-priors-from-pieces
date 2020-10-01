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

import java.io.File

import scalismo.geometry._2D
import scalismo.io.{MeshIO, StatisticalModelIO}
import scalismo.mesh.{LineMesh, LineMesh2D}
import scalismo.statisticalmodel.PointDistributionModel
import scalismo.statisticalmodel.dataset.DataCollection
import scalismo.statisticalmodel.dataset.DataCollection.LineMeshDataCollection

object CreatePDMsFromFiles {
  val fingers = Seq("thumb", "index", "long", "ring", "small")
  val filesStartWith = "hand-"
  val percentageCut = 15

  val experimentPath = new File(Paths.handPath, s"shapemi/allFingers15pMissing")
  val sampleMeshPath = new File(experimentPath, s"samples/")
  val meanMeshPath = new File(experimentPath, s"mean/")
  val mapMeshPath = new File(experimentPath, s"map/")

  val pcaPath = new File(experimentPath, s"pca")
  val pcaGTPath = new File(pcaPath, "gt")
  val pcaMAPPath = new File(pcaPath, "map")
  val pcaMEANPath = new File(pcaPath, "mean")
  val pcaSamplePath = new File(pcaPath, "sample")
  val outPaths = Seq(pcaPath, pcaGTPath, pcaMAPPath, pcaMEANPath, pcaSamplePath)
  outPaths.foreach { p =>
    if (!p.exists()) {
      println(s"Creating path: ${p}")
      p.mkdir()
    }
  }

  def computePCAmodel(ref: LineMesh2D, meshes: IndexedSeq[LineMesh[_2D]]): PointDistributionModel[_2D, LineMesh] = {
    println(s"Computing PCA model from ${meshes.length} meshes")
    val dc: LineMeshDataCollection[_2D] = DataCollection.fromLineMeshSequence(ref, meshes)
    val gpadc: LineMeshDataCollection[_2D] = dc
    PointDistributionModel.createUsingPCA(gpadc)
  }

  def main(args: Array[String]) {
    scalismo.initialize()
    println("Creating PDMs from dumped files. Leave-one-out models created for generalization computation.")

    val targetNames = Seq("FULLPCA") ++ mapMeshPath.listFiles(_.getName.endsWith(".vtk")).map(_.getName.split("_").head).toSeq

    val gpModelFile = new File(Paths.handPath, "hand2D_gp_s25_s50_s120_per.h5")
    val gpModel = StatisticalModelIO.readStatisticalLineMeshModel2D(gpModelFile).get

    println("Targets:")
    targetNames.foreach(f => println(s" - ${f}"))

    targetNames.foreach { keepOutName =>
      val keepOutList: Seq[String] = Seq(keepOutName)
      println(s"Keepout: ${keepOutName}")

      val meshFullFiles = new File(Paths.handPath, "registered/mesh").listFiles(f =>
        f.getName.startsWith(filesStartWith) &&
          f.getName.endsWith(".vtk") &&
          targetNames.contains(f.getName.replace(".vtk", "")) &&
          !keepOutList.contains(f.getName.replace(".vtk", ""))
      ).sorted
      val meshesFull = meshFullFiles.map(f => MeshIO.readLineMesh2D(f).get)

      val meshPartialMAPFiles = mapMeshPath.listFiles(f =>
        f.getName.startsWith(filesStartWith) && f.getName.endsWith("_map.vtk") &&
          !keepOutList.contains(f.getName.split("_").head)
      ).sorted
      val meshesMAPCompletion = meshPartialMAPFiles.map(f => MeshIO.readLineMesh2D(f).get)

      val meshPartialMEANFiles = meanMeshPath.listFiles(f =>
        f.getName.startsWith(filesStartWith) && f.getName.endsWith("_mean.vtk") &&
          !keepOutList.contains(f.getName.split("_").head)
      ).sorted
      val meshesMEANCompletion = meshPartialMEANFiles.map(f => MeshIO.readLineMesh2D(f).get)

      val meshSampleFiles = sampleMeshPath.listFiles(f =>
        f.getName.startsWith(filesStartWith) && f.getName.endsWith(".vtk") &&
          !keepOutList.contains(f.getName.split("_").head)
      ).sorted
      val meshesSampleCompletion = meshSampleFiles.map(f => MeshIO.readLineMesh2D(f).get)

      println(s"Number of full meshes: ${meshesFull.length}")
      println(s"Number of MAP meshes: ${meshesMAPCompletion.length}")
      println(s"Number of MEAN meshes: ${meshesMEANCompletion.length}")
      println(s"Number of sample meshes: ${meshesSampleCompletion.length}")

      val pcaModelFull = computePCAmodel(gpModel.reference, meshesFull)
      val pcaModelMAP = computePCAmodel(gpModel.reference, meshesMAPCompletion)
      val pcaModelMEAN = computePCAmodel(gpModel.reference, meshesMEANCompletion)
      val pcaModelPartialInitial = computePCAmodel(gpModel.reference, meshesSampleCompletion)
      val pcaModelPartial = pcaModelPartialInitial.truncate(pcaModelFull.rank * 3)

      println("Writing models")
      println(s"Full model: ${pcaModelFull.rank}")
      println(s"MAP model: ${pcaModelMAP.rank}")
      println(s"MEAN model: ${pcaModelMEAN.rank}")
      println(s"Sample model: ${pcaModelPartial.rank}")

      if (keepOutName == "FULLPCA") {
        StatisticalModelIO.writeStatisticalLineMeshModel2D(pcaModelFull, new File(pcaGTPath, s"pca_full.h5"))
        StatisticalModelIO.writeStatisticalLineMeshModel2D(pcaModelMAP, new File(pcaMAPPath, s"pca_full_${percentageCut}.h5"))
        StatisticalModelIO.writeStatisticalLineMeshModel2D(pcaModelMEAN, new File(pcaMEANPath, s"pca_full_${percentageCut}.h5"))
        StatisticalModelIO.writeStatisticalLineMeshModel2D(pcaModelPartial, new File(pcaSamplePath, s"pca_full_${percentageCut}.h5"))
      }
      else {
        StatisticalModelIO.writeStatisticalLineMeshModel2D(pcaModelFull, new File(pcaGTPath, s"pca_keepOut_${keepOutName}.h5"))
        StatisticalModelIO.writeStatisticalLineMeshModel2D(pcaModelMAP, new File(pcaMAPPath, s"pca_keepOut_${keepOutName}_${percentageCut}.h5"))
        StatisticalModelIO.writeStatisticalLineMeshModel2D(pcaModelMEAN, new File(pcaMEANPath, s"pca_keepOut_${keepOutName}_${percentageCut}.h5"))
        StatisticalModelIO.writeStatisticalLineMeshModel2D(pcaModelPartial, new File(pcaSamplePath, s"pca_keepOut_${keepOutName}_${percentageCut}.h5"))
      }
    }
  }
}
