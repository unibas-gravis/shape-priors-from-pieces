package apps.hands.preprocessing

import java.io.File

import apps.util.myPaths
import scalismo.geometry._2D
import scalismo.io.{MeshIO, StatisticalModelIO}
import scalismo.mesh.{LineMesh, LineMesh2D}
import scalismo.statisticalmodel.PointDistributionModel
import scalismo.statisticalmodel.dataset.DataCollection
import scalismo.statisticalmodel.dataset.DataCollection.LineMeshDataCollection

object CreateSSM {
  val fingers = Seq("thumb", "index", "long", "ring", "small")

  //  val experimentName = "shapemi_scale_06_25"
  val experimentName = "shapemi_gauss_per_06_25"
  //  val experimentName = "shapemi_skel_ablation_07_03"

  val experimentPath = new File(myPaths.datapath, s"experiments/${experimentName}")

  val pcaPath = new File(experimentPath, s"pca_random-finger")
  //  val pcaPath = new File(experimentPath, s"pca_all-thumb")

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

  def main(args: Array[String]) {
    println("starting app... Full")
    scalismo.initialize()

    val modelFile = new File(myPaths.datapath, "hand2D_gp_s25_s50_s120_per.h5") // Just need the reference from the model!!!

    val modelGPLineMesh = StatisticalModelIO.readStatisticalLineMeshModel2D(modelFile).get


    println("Create SSMs from files on disk!")
    scala.io.StdIn.readLine(s"File location: ${experimentPath}, PCA output path: ${pcaPath}, continue?")

    // read a mesh from file
    Seq(5, 10, 15, 20, 30, 40, 50, 60, 70, 80, 90).par.foreach { p =>
      println(s"PART cut of: ${p} %")
      val targetNameFullSeq = Seq("FULLPCA", "hand-0", "hand-1", "hand-2", "hand-3", "hand-5", "hand-6", "hand-7", "hand-8", "hand-10", "hand-11", "hand-12", "hand-16")
      // ALL THUMB
      //      val targetNameFullSeqWithFinger = Seq("FULLPCA", s"hand-0_${fingers(0)}", s"hand-1_${fingers(0)}", s"hand-2_${fingers(0)}", s"hand-3_${fingers(0)}", s"hand-5_${fingers(0)}",
      //        s"hand-6_${fingers(0)}", s"hand-7_${fingers(0)}", s"hand-8_${fingers(0)}", s"hand-10_${fingers(0)}", s"hand-11_${fingers(0)}", s"hand-12_${fingers(0)}", s"hand-16_${fingers(0)}")
      val targetNameFullSeqWithFinger = Seq("FULLPCA", s"hand-0_${fingers(1)}", s"hand-1_${fingers(4)}", s"hand-2_${fingers(2)}", s"hand-3_${fingers(3)}", s"hand-5_${fingers(4)}",
        s"hand-6_${fingers(0)}", s"hand-7_${fingers(1)}", s"hand-8_${fingers(2)}", s"hand-10_${fingers(3)}", s"hand-11_${fingers(0)}", s"hand-12_${fingers(2)}", s"hand-16_${fingers(3)}")
      val testIndex = 0
      //      val targetNameFullSeq = Seq("FULLPCA", s"test-${testIndex}-normal", s"test-${testIndex}-smaller", s"test-${testIndex}-smallest", s"test-${testIndex}-bigger", s"test-${testIndex}-biggest")

      println("Targets:")
      targetNameFullSeq.foreach(println(_))

      //      val filesStartWith = "test-"
      val filesStartWith = "hand-"


      targetNameFullSeq.foreach { thisIsTheTarget =>
        val keepOutList: Seq[String] = Seq(thisIsTheTarget)
        val keepOutName = keepOutList.head
        println(s"Keepout: ${keepOutName}")

        val meshFullFiles = new File(myPaths.datapath, "registered/mesh").listFiles(f =>
          f.getName.startsWith(filesStartWith) &&
            f.getName.endsWith(".vtk") &&
            targetNameFullSeq.contains(f.getName.replace(".vtk", "")) &&
            !keepOutList.contains(f.getName.replace(".vtk", ""))
        ).sorted
        val meshesFull = meshFullFiles.map(f => MeshIO.readLineMesh2D(f).get)

        val meshPartialMAPFiles = new File(experimentPath, s"map/${p}").listFiles(f =>
          f.getName.startsWith(filesStartWith) && f.getName.endsWith("_map.vtk") &&
            //            targetNameFullSeq.contains(f.getName.split("_").head) &&
            targetNameFullSeqWithFinger.contains(f.getName.split("_")(0) + "_" + f.getName.split("_")(1)) &&
            !keepOutList.contains(f.getName.split("_").head)
        ).sorted
        val meshesMAPPartial = meshPartialMAPFiles.map(f => MeshIO.readLineMesh2D(f).get)

        val meshPartialMEANFiles = new File(experimentPath, s"mean/${p}").listFiles(f =>
          f.getName.startsWith(filesStartWith) && f.getName.endsWith("_mean.vtk") &&
            //            targetNameFullSeq.contains(f.getName.split("_").head) &&
            targetNameFullSeqWithFinger.contains(f.getName.split("_")(0) + "_" + f.getName.split("_")(1)) &&
            !keepOutList.contains(f.getName.split("_").head)
        ).sorted
        val meshesMEANPartial = meshPartialMEANFiles.map(f => MeshIO.readLineMesh2D(f).get)

        val meshPartialFiles = new File(experimentPath, s"samples/${p}").listFiles(f =>
          f.getName.startsWith(filesStartWith) && f.getName.endsWith(".vtk") &&
            //            targetNameFullSeq.contains(f.getName.split("_").head) &&
            targetNameFullSeqWithFinger.contains(f.getName.split("_")(0) + "_" + f.getName.split("_")(1)) &&
            !keepOutList.contains(f.getName.split("_").head)
        ).sorted
        val meshesPartial = meshPartialFiles.map(f => MeshIO.readLineMesh2D(f).get)

        println(s"Number of full meshes: ${meshesFull.length}")
        println(s"Number of MAP meshes: ${meshesMAPPartial.length}")
        println(s"Number of MEAN meshes: ${meshesMEANPartial.length}")
        println(s"Number of sample meshes: ${meshesPartial.length}")

        meshFullFiles.foreach(f => println(f.getName))

        val (pcaModelFull, _) = computePCAmodel(modelGPLineMesh.reference, meshesFull)

        val (pcaModelMAP, _) = computePCAmodel(modelGPLineMesh.reference, meshesMAPPartial)

        val (pcaModelMEAN, _) = computePCAmodel(modelGPLineMesh.reference, meshesMEANPartial)

        val (pcaModelPartialInitial, _) = computePCAmodel(modelGPLineMesh.reference, meshesPartial)
        val pcaModelPartial = pcaModelPartialInitial.truncate(pcaModelFull.rank * 3)

        println("Writing models")
        println(s"Full model: ${pcaModelFull.rank}")
        println(s"MAP model: ${pcaModelMAP.rank}")
        println(s"MEAN model: ${pcaModelMEAN.rank}")
        println(s"Sample model: ${pcaModelPartial.rank}")

        if (keepOutName == "FULLPCA") {
          StatisticalModelIO.writeStatisticalLineMeshModel2D(pcaModelFull, new File(gtPath, s"pca_full.h5"))
          StatisticalModelIO.writeStatisticalLineMeshModel2D(pcaModelMAP, new File(mapPath, s"pca_full_${p}.h5"))
          StatisticalModelIO.writeStatisticalLineMeshModel2D(pcaModelMEAN, new File(meanPath, s"pca_full_${p}.h5"))
          StatisticalModelIO.writeStatisticalLineMeshModel2D(pcaModelPartial, new File(samplePath, s"pca_full_${p}.h5"))
        }
        else {
          StatisticalModelIO.writeStatisticalLineMeshModel2D(pcaModelFull, new File(gtPath, s"pca_keepOut_${keepOutName}.h5"))
          StatisticalModelIO.writeStatisticalLineMeshModel2D(pcaModelMAP, new File(mapPath, s"pca_keepOut_${keepOutName}_${p}.h5"))
          StatisticalModelIO.writeStatisticalLineMeshModel2D(pcaModelMEAN, new File(meanPath, s"pca_keepOut_${keepOutName}_${p}.h5"))
          StatisticalModelIO.writeStatisticalLineMeshModel2D(pcaModelPartial, new File(samplePath, s"pca_keepOut_${keepOutName}_${p}.h5"))
        }
        //    val ui = ScalismoUI()
        //    val fullGroup = ui.createGroup("full")
        //    val modelGroup = ui.createGroup("PCAmodel")
        //    val modelGroupPartial = ui.createGroup("PCApartial")
        //
        //    visualizePCsamples(pcaModelFull, ui, modelGroup)
        //    visualizePCsamples(pcaModelPartial, ui, modelGroupPartial)
        //
        //    dc.dataItems.zipWithIndex.foreach { case (m, i) =>
        //      ui.show(fullGroup, LineMeshConverter.lineMesh2Dto3D(modelGPLineMesh.referenceMesh.transform(m.transformation)), s"${i}").opacity = 0f
        //    }
      }
    }
    println("All done!")
  }

  def computePCAmodel(ref: LineMesh2D, meshes: IndexedSeq[LineMesh[_2D]]): (PointDistributionModel[_2D, LineMesh], LineMeshDataCollection[_2D]) = {
    println(s"Computing PCA model from ${meshes.length} meshes")
    println(s"${ref.pointSet.numberOfPoints}, ${meshes.head.pointSet.numberOfPoints}")
    val dc: LineMeshDataCollection[_2D] = DataCollection.fromLineMeshSequence(ref, meshes)
    val gpadc: LineMeshDataCollection[_2D] = dc
    (PointDistributionModel.createUsingPCA(gpadc), gpadc)
  }
}
