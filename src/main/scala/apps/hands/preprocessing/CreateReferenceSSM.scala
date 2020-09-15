package apps.hands.preprocessing

import java.io.File

import apps.hands.VisualizeData
import apps.scalismoExtension.LineMeshConverter
import apps.util.myPaths
import scalismo.geometry.{Landmark, _2D, _3D}
import scalismo.io.{LandmarkIO, MeshIO}
import scalismo.mesh.{LineMesh, LineMesh2D}
import scalismo.statisticalmodel.PointDistributionModel
import scalismo.statisticalmodel.dataset.DataCollection
import scalismo.statisticalmodel.dataset.DataCollection.LineMeshDataCollection
import scalismo.transformations.Scaling
import scalismo.ui.api.ScalismoUI

object CreateReferenceSSM {

  val registeredPath = new File(myPaths.datapath, "registered/mesh")
  val pcaPath = new File(myPaths.datapath, "registered")

  def main(args: Array[String]) {
    println("starting app... Full")
    scalismo.initialize()

    val landmarks3D = LandmarkIO.readLandmarksJson[_3D](new File(myPaths.datapath, "aligned/reference-hand.json")).get
    val landmarks2D = LineMeshConverter.landmark3Dto2D(landmarks3D)
    val referenceMesh = MeshIO.readLineMesh2D(new File(myPaths.datapath, "aligned/reference-hand.vtk")).get
    val meshes: IndexedSeq[LineMesh[_2D]] = registeredPath.listFiles(_.getName.endsWith(".vtk")).map(f => MeshIO.readLineMesh2D(f).get)
    val ssm = computePCAmodel(referenceMesh, meshes)._1

    //    StatisticalLineModelIO.writeStatisticalLineMeshModel(ssm, new File(pcaPath, s"pca.h5"))


    val mean = MeshIO.readLineMesh2D(new File(myPaths.datapath, "registered/mesh/hand-0.vtk")).get //ssm.mean
    val meanLM3D = LandmarkIO.readLandmarksJson[_3D](new File(myPaths.datapath, s"registered/landmarks/hand-0.json")).get
    val meanLM2D = LineMeshConverter.landmark3Dto2D(meanLM3D)
    val outname = "test-0"

    val subFolder = "registered"

    val meanbigger = mean.transform(Scaling(1.05))
    val meanbiggest = mean.transform(Scaling(1.15))
    val meansmaller = mean.transform(Scaling(0.95))
    val meansmallest = mean.transform(Scaling(0.85))

    val ids = meanLM2D.map { lm => (lm, mean.pointSet.findClosestPoint(lm.point).id) }

    val lmsmean: Seq[Landmark[_3D]] = LineMeshConverter.landmark2Dto3D(ids.map { case (lm, id) => lm.copy(point = mean.pointSet.point(id)) })
    val lmsbigger: Seq[Landmark[_3D]] = LineMeshConverter.landmark2Dto3D(ids.map { case (lm, id) => lm.copy(point = meanbigger.pointSet.point(id)) })
    val lmsbiggest: Seq[Landmark[_3D]] = LineMeshConverter.landmark2Dto3D(ids.map { case (lm, id) => lm.copy(point = meanbiggest.pointSet.point(id)) })
    val lmssmaller: Seq[Landmark[_3D]] = LineMeshConverter.landmark2Dto3D(ids.map { case (lm, id) => lm.copy(point = meansmaller.pointSet.point(id)) })
    val lmssmallest: Seq[Landmark[_3D]] = LineMeshConverter.landmark2Dto3D(ids.map { case (lm, id) => lm.copy(point = meansmallest.pointSet.point(id)) })

    //    MeshIO.writeLineMesh[_2D](mean, new File(myPaths.datapath, s"${subFolder}/mesh/${outname}.vtk"))
    //    MeshIO.writeLineMesh[_2D](meanbigger, new File(myPaths.datapath, s"${subFolder}/mesh/${outname}-bigger.vtk"))
    //    MeshIO.writeLineMesh[_2D](meanbiggest, new File(myPaths.datapath, s"${subFolder}/mesh/${outname}-biggest.vtk"))
    //    MeshIO.writeLineMesh[_2D](meansmaller, new File(myPaths.datapath, s"${subFolder}/mesh/${outname}-smaller.vtk"))
    //    MeshIO.writeLineMesh[_2D](meansmallest, new File(myPaths.datapath, s"${subFolder}/mesh/${outname}-smallest.vtk"))
    //
    //    LandmarkIO.writeLandmarksJson[_3D](lmsmean, new File(myPaths.datapath, s"${subFolder}/landmarks/${outname}.json"))
    //    LandmarkIO.writeLandmarksJson[_3D](lmsbigger, new File(myPaths.datapath, s"${subFolder}/landmarks/${outname}-bigger.json"))
    //    LandmarkIO.writeLandmarksJson[_3D](lmsbiggest, new File(myPaths.datapath, s"${subFolder}/landmarks/${outname}-biggest.json"))
    //    LandmarkIO.writeLandmarksJson[_3D](lmssmaller, new File(myPaths.datapath, s"${subFolder}/landmarks/${outname}-smaller.json"))
    //    LandmarkIO.writeLandmarksJson[_3D](lmssmallest, new File(myPaths.datapath, s"${subFolder}/landmarks/${outname}-smallest.json"))


    val ui = ScalismoUI()
    val meanGroup = ui.createGroup("mean")
    ui.show(meanGroup, LineMeshConverter.lineMesh2Dto3D(mean), "mean")
    ui.show(meanGroup, LineMeshConverter.lineMesh2Dto3D(meanbigger), "meanbigger")
    ui.show(meanGroup, LineMeshConverter.lineMesh2Dto3D(meanbiggest), "meanbiggest")
    ui.show(meanGroup, LineMeshConverter.lineMesh2Dto3D(meansmaller), "meansmaller")
    ui.show(meanGroup, LineMeshConverter.lineMesh2Dto3D(meansmallest), "meansmallest")
    val pcGroup = ui.createGroup("PCs")
    //    ui.show(meanGroup, lmsbigger, "landmarks")
    //    ui.show(meanGroup, lmssmaller, "landmarks")
    //    ui.show(meanGroup, lmsmean, "landmarks")

    VisualizeData.visualizePCsamples(ssm, ui, pcGroup, maxNumberOfComponents = 2)
  }

  def computePCAmodel(ref: LineMesh2D, meshes: IndexedSeq[LineMesh[_2D]]): (PointDistributionModel[_2D, LineMesh], LineMeshDataCollection[_2D]) = {
    println(s"Computing PCA model from ${meshes.length} meshes")
    val dc: LineMeshDataCollection[_2D] = DataCollection.fromLineMeshSequence[_2D](ref, meshes)
    val gpadc: LineMeshDataCollection[_2D] = dc
    (PointDistributionModel.createUsingPCA(gpadc), gpadc)
  }
}
