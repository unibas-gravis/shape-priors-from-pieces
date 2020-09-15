package apps.hands.preprocessing

import java.io.File

import apps.scalismoExtension.LineMeshConverter
import apps.util.myPaths
import scalismo.geometry._
import scalismo.io.{LandmarkIO, MeshIO}
import scalismo.transformations.{Rotation, Translation, TranslationAfterRotation}
import scalismo.ui.api.ScalismoUI

object AlignHandReference {

  def main(args: Array[String]): Unit = {
    scalismo.initialize()

    val referenceMesh = MeshIO.readLineMesh2D(new File(myPaths.datapath, "aligned/reference-hand_old.vtk")).get
    val landmarks = LandmarkIO.readLandmarksJson[_3D](new File(myPaths.datapath, "aligned/reference-hand_old.json")).get

    val center = referenceMesh.pointSet.points.map(_.toVector).reduce(_ + _) * 1.0 / referenceMesh.pointSet.numberOfPoints.toDouble


    val pt = TranslationAfterRotation[_2D](
      Translation(-center - EuclideanVector2D(0, -80)),
      Rotation(0, Point2D(0, 0))
    )
    val refTrans = referenceMesh.transform(pt)
    val landmarks2D = LineMeshConverter.landmark3Dto2D(landmarks)
    val landmark2DTrans = landmarks2D.map(lm => lm.copy(point = pt(lm.point)))
    val landmarksTrans = LineMeshConverter.landmark2Dto3D(landmark2DTrans)

    //    MeshIO.writeLineMesh[_2D](refTrans, new File(myPaths.datapath, "aligned/reference-hand.vtk"))
    //    LandmarkIO.writeLandmarksJson[_3D](landmarksTrans, new File(myPaths.datapath, "aligned/reference-hand.json"))

    val ui = ScalismoUI()
    val someGroup = ui.createGroup("some")
    //    ui.show(someGroup, LineMeshConverter.lineMesh2Dto3D(referenceMesh), "ref")
    ui.show(someGroup, LineMeshConverter.lineMesh2Dto3D(refTrans), "trans")
    ui.show(someGroup, landmarksTrans, "landmarks")

    val alignedGroup = ui.createGroup("aligned")
    val alignedMeshes = new File(myPaths.datapath, "aligned/mesh")
    alignedMeshes.listFiles(_.getName.endsWith(".vtk")).foreach { f =>
      val name = f.getName.replace(".vtk", ".json")
      val msh = MeshIO.readLineMesh2D(f).get
      val mshTrans = msh.transform(pt)
      val lms = LandmarkIO.readLandmarksJson[_3D](new File(myPaths.datapath, s"aligned/landmarks/${name}")).get
      val lms2D = LineMeshConverter.landmark3Dto2D(lms)
      val lms2DTrans = lms2D.map(lm => lm.copy(point = pt(lm.point)))
      val lmTrans = LineMeshConverter.landmark2Dto3D(lms2DTrans)

      //      MeshIO.writeLineMesh[_2D](mshTrans, new File(myPaths.datapath, s"registered/mesh/${f.getName}"))
      //      LandmarkIO.writeLandmarksJson[_3D](lmTrans, new File(myPaths.datapath, s"registered/landmarks/${name}"))

      ui.show(alignedGroup, LineMeshConverter.lineMesh2Dto3D(msh), f.getName)
    }
  }
}
