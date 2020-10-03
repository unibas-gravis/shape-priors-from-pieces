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

package apps.femur

import java.io.File

import scalismo.geometry._3D
import scalismo.io.{MeshIO, StatisticalModelIO}
import scalismo.mesh.{TriangleMesh, TriangleMesh3D}
import scalismo.statisticalmodel.StatisticalMeshModel
import scalismo.statisticalmodel.dataset.DataCollection
import scalismo.statisticalmodel.dataset.DataCollection.TriangleMeshDataCollection
import scalismo.utils.Random


object CreatePDMsFromFiles {
  implicit val random: Random = Random(1024)

  val modelIndexThreshold: Int = 10

  def computePCAmodel(ref: TriangleMesh3D, meshes: IndexedSeq[TriangleMesh[_3D]]): StatisticalMeshModel = {
    println(s"Computing PCA model from ${meshes.length} meshes")
    println(s"${ref.pointSet.numberOfPoints}, ${meshes.head.pointSet.numberOfPoints}")
    val dc: TriangleMeshDataCollection[_3D] = DataCollection.fromTriangleMesh3DSequence(ref, meshes)
    val gpadc: TriangleMeshDataCollection[_3D] = dc
    StatisticalMeshModel.createUsingPCA(gpadc).get
  }

  def main(args: Array[String]): Unit = {
    scalismo.initialize()
    val (gpModel, _) = LoadData.model()
    val ref = gpModel.referenceMesh

    val basePath = Paths.generalPath
    val meshesGtPath = new File(basePath, "registered/meshes").listFiles(f => f.getName.endsWith(".vtk") && f.getName.replace(".vtk", "").toInt < modelIndexThreshold)
    val meshesMapPath = new File(basePath, "shapemi/map").listFiles(_.getName.endsWith(".vtk"))
    val meshesMeanPath = new File(basePath, "shapemi/mean").listFiles(_.getName.endsWith(".vtk"))
    val meshesSamplePath = new File(basePath, "shapemi/samples").listFiles(_.getName.endsWith(".vtk"))

    println(s"Number of GT: ${meshesGtPath.length}")
    println(s"Number of MAP: ${meshesMapPath.length}")
    println(s"Number of MEAN: ${meshesMeanPath.length}")
    println(s"Number of SAMPLEs: ${meshesSamplePath.length}")

    val pcaPath = new File(basePath, "shapemi/pca")
    val gtPath = new File(pcaPath, "gt")
    val mapPath = new File(pcaPath, "map")
    val meanPath = new File(pcaPath, "mean")
    val samplePath = new File(pcaPath, "sample")
    val outPaths = Seq(pcaPath, gtPath, mapPath, meanPath, samplePath)
    outPaths.foreach { p =>
      if (!p.exists()) {
        println(s"Creating path: ${p}")
        p.mkdir()
      }
    }

    val gtMeshes = meshesGtPath.map(f => MeshIO.readMesh(f).get)
    val gtPCA = computePCAmodel(ref, gtMeshes)
    StatisticalModelIO.writeStatisticalMeshModel(gtPCA, new File(gtPath, "complete_0.h5"))

    val mapMeshes = meshesMapPath.map(f => MeshIO.readMesh(f).get)
    val mapPCA = computePCAmodel(ref, mapMeshes)
    StatisticalModelIO.writeStatisticalMeshModel(mapPCA, new File(mapPath, "map_0.h5"))

    val meanMeshes = meshesMeanPath.map(f => MeshIO.readMesh(f).get)
    val meanPCA = computePCAmodel(ref, meanMeshes)
    StatisticalModelIO.writeStatisticalMeshModel(meanPCA, new File(meanPath, "mean_0.h5"))

    Seq(1, 2, 5, 10, 50, 100).par.foreach { p =>
      val sampleMeshes = meshesSamplePath.filter(f => f.getName.split("_")(2).toInt < p).map(f => MeshIO.readMesh(f).get)
      val samplePCA = computePCAmodel(ref, sampleMeshes).truncate(math.min(sampleMeshes.length, gtPCA.rank * 3))
      StatisticalModelIO.writeStatisticalMeshModel(samplePCA, new File(samplePath, s"sample_${p}.h5"))
    }
  }
}
