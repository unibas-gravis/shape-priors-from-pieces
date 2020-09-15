package apps.hands.preprocessing

import java.io.File

import apps.scalismoExtension.LineMeshConverter
import apps.util.{AlignmentTransforms, myPaths}
import scalismo.geometry._
import scalismo.io.{LandmarkIO, MeshIO}
import scalismo.registration.{RigidTransformation, RotationTransform, TranslationTransform}
import scalismo.ui.api.ScalismoUI

object AlignMeshes {

  def main(args: Array[String]): Unit = {
    scalismo.initialize()

    val referenceMesh = MeshIO.readLineMesh2D(new File(myPaths.datapath, "aligned/reference-hand.vtk")).get
    val landmarks3D = LandmarkIO.readLandmarksJson[_3D](new File(myPaths.datapath, "aligned/reference-hand.json")).get
    val landmarks2D = LineMeshConverter.landmark3Dto2D(landmarks3D)

    val ui = ScalismoUI()
    val otherGroup = ui.createGroup("other")
    val initialGroup = ui.createGroup("initial")
    val alignedMeshes = new File(myPaths.datapath, "aligned/mesh")


    val ids = landmarks2D.map{lm => (lm, referenceMesh.pointSet.findClosestPoint(lm.point).id)}
//    val lmsmean: Seq[Landmark[_3D]] = LineMeshConverter.landmark2Dto3D(ids.map{case (lm, id) => lm.copy(point = mean.pointSet.point(id))})
//    LandmarkIO.writeLandmarksJson[_3D](lmsmean, new File(myPaths.datapath, s"aligned/landmarks/${outname}.json"))
    val alignedGroup = ui.createGroup("aligned")

    ui.show(otherGroup, LineMeshConverter.lineMesh2Dto3D(referenceMesh), "ref")
    ui.show(otherGroup, landmarks3D, "landmarks")
    alignedMeshes.listFiles(_.getName.endsWith(".vtk")).foreach { f =>
      val name = f.getName.replace(".vtk", ".json")
      val msh = MeshIO.readLineMesh2D(f).get
      val lms3D = LandmarkIO.readLandmarksJson[_3D](new File(myPaths.datapath, s"aligned/landmarks/${name}")).get
      val lms2D = LineMeshConverter.landmark3Dto2D(lms3D)
      val lmTrans = AlignmentTransforms.computeTransform2D(lms2D, landmarks2D, Point2D(0,0))
      val mshTrans = msh.transform(lmTrans)
      val lms2Dtrans = lms2D.map(lm => lm.copy(point = lmTrans(lm.point)))
      val lms3Dtrans = LineMeshConverter.landmark2Dto3D(lms2Dtrans)

      ui.show(initialGroup, LineMeshConverter.lineMesh2Dto3D(msh), f.getName).opacity = 0
//      ui.show(alignedGroup, LineMeshConverter.lineMesh2Dto3D(mshTrans), f.getName).opacity = 0
//      ui.show(alignedGroup, lms3Dtrans, "landmarks").foreach(_.opacity = 0)

      //      ui.show(initialGroup, lms3D, "landmarks").foreach(f => f.opacity = 0)
//      MeshIO.writeLineMesh[_2D](mshTrans, f)
//      LandmarkIO.writeLandmarksJson[_3D](lms3Dtrans, new File(myPaths.datapath, s"aligned/landmarks/${name}"))
    }
  }
}
