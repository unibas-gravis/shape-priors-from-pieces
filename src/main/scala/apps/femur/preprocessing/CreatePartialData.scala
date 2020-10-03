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

package apps.femur.preprocessing

import java.io.File

import apps.femur.{LoadData, Paths}
import scalismo.common.PointId
import scalismo.geometry._3D
import scalismo.io.MeshIO
import scalismo.mesh.TriangleMesh

object CreatePartialData {

  def cutTriangleMesh(mesh: TriangleMesh[_3D], pid: PointId, ratio: Double): TriangleMesh[_3D] = {
    val idsToCut = mesh.pointSet.findNClosestPoints(mesh.pointSet.point(pid), (mesh.pointSet.numberOfPoints * ratio).toInt).map(_.id)
    val mask = mesh.operations.maskPoints(!idsToCut.contains(_))
    mask.transformedMesh
  }

  def main(args: Array[String]): Unit = {
    scalismo.initialize()

    println(s"Creating partial data!")

    //lms 0-2 and 3-6 on different ends of the bone
    val (model, modelLms) = LoadData.model()

    //TODO use correct filesystem
    val alignedFiles = new File(Paths.generalPath, "aligned/meshes/")
    val registeredFiles = new File(Paths.generalPath, "registered/meshes/")
    val outputPartial = new File(Paths.generalPath, "partialMeshes/")
    outputPartial.mkdir()

    //add some more location to cut by combining landmarks
    val lmsToUse = (modelLms.map(_.point) ++ Seq((0, 3, 0.2), (2, 4, 0.4), (1, 5, 0.6), (1, 4, 0.8)).map(t =>
      modelLms(t._1).point + ((modelLms(t._2).point - modelLms(t._1).point) * t._3)
    )).map(model.referenceMesh.pointSet.findClosestPoint).map(_.id)

    val ratios = Seq(0.2, 0.1, 0.1, 0.19, 0.23, 0.22, 0.05, 0.05, 0.03, 0.03)

    (0 until 10).foreach { i =>
      println(s"Processing index: ${i}")

      val targetMesh = MeshIO.readMesh(new File(alignedFiles, s"${i}.stl")).get
      val registeredTarget = MeshIO.readMesh(new File(registeredFiles, s"${i}.vtk")).get

      val partial = if (i == 8 || i == 9) { //special cases where we remove small amounts of points at multiple locations
        val lms = (6 to 9).map(lmsToUse)
        lms.foldLeft(targetMesh) { case (mesh, lm) => {
          val lmLoc = mesh.pointSet.findClosestPoint(registeredTarget.pointSet.point(lm)).id
          cutTriangleMesh(mesh, lmLoc, ratios(i))
        }
        }
      } else {
        val lm = lmsToUse(i)
        val lmLoc = targetMesh.pointSet.findClosestPoint(registeredTarget.pointSet.point(lm)).id
        cutTriangleMesh(targetMesh, lmLoc, ratios(i))
      }
      MeshIO.writeMesh(partial, new File(outputPartial, s"${i}.stl"))
    }
  }
}
