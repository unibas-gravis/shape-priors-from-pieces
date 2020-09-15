//package apps.hands
//
//import java.awt.Color
//import java.io.File
//
//import api.sampling.loggers.{JSONAcceptRejectLogger, jsonLogFormat}
//import api.sampling.ModelFittingParameters
//import apps.hands.preprocessing.Create2dGPmodel
//import apps.scalismoExtension.LineMeshConverter
//import apps.util.{LogHelper2D, myPaths}
//import scalismo.common.PointId
//import scalismo.geometry._
//import scalismo.io.{MeshIO, StatisticalLineModelIO}
//import scalismo.mesh._
//import scalismo.ui.api.{ScalismoUI, ScalismoUIHeadless}
//import scalismo.utils.Random.implicits._
//
//object Visualize2Dposterior {
//
//  def main(args: Array[String]) {
//    println("starting app...")
//    scalismo.initialize()
//
//    val experimentName = "overlap_bootstrap"
//
//    val logPath = new File(myPaths.datapath, s"log_${experimentName}/")
//    val registeredPath = new File(myPaths.datapath, s"registered_${experimentName}/")
//
//
//    Seq(20).foreach { p =>
////      Seq(5, 10, 15, 20, 30, 40, 50, 60, 70, 80).foreach { p =>
//
//      Thread.sleep(p * 100)
//
////      val modelFile = new File(myPaths.datapath, "gpModel.h5")
//      val modelFile = new File(myPaths.datapath, s"pca_overlap/sample_augmented/pca_full_${p}.h5")
//
//
//      val modelLineMesh =
//        StatisticalLineModelIO.readStatisticalLineMeshModel(modelFile).get
//
//      val referenceLineMesh2D = modelLineMesh.referenceMesh
//      val referenceLineMesh3D =
//        LineMeshConverter.lineMesh2Dto3D(referenceLineMesh2D)
//
////      val targetNameSeq = Seq(s"hand-0_thumb_${p}", s"hand-1_index_${p}")
//
//      val targetNameSeq = Seq(s"hand-0_thumb_${p}", s"hand-1_index_${p}", s"hand-2_long_${p}", s"hand-3_ring_${p}", s"hand-5_small_${p}",
//        s"hand-6_thumb_${p}", s"hand-7_index_${p}", s"hand-8_long_${p}", s"hand-10_ring_${p}", s"hand-11_small_${p}",
//        s"hand-12_thumb_${p}", s"hand-16_index_${p}")
//
////      val finger = "thumb"
////      val targetNameSeq = Seq(
////        s"hand-0_${finger}_${p}",
////        s"hand-1_${finger}_${p}",
////        s"hand-2_${finger}_${p}",
////        s"hand-3_${finger}_${p}",
////        s"hand-5_${finger}_${p}",
////        s"hand-6_${finger}_${p}",
////        s"hand-7_${finger}_${p}",
////        s"hand-8_${finger}_${p}",
////        s"hand-10_${finger}_${p}",
////        s"hand-11_${finger}_${p}",
////        s"hand-12_${finger}_${p}",
////        s"hand-16_${finger}_${p}"
////      )
//
//      targetNameSeq.zipWithIndex.par.foreach {
//        case (_, index) =>
//          val targetName = targetNameSeq(index)
//          val targetHand = targetName.split("_").head
//
////        val dataPath = new File(myPaths.datapath, "aligned/mesh")
////        val dataFiles = dataPath.listFiles(f => f.getName.endsWith(".vtk") && f.getName.startsWith("hand")).sorted
//
//          println(s"Index ${index} - TargetName: ${targetName}")
//
//          val targetInitFile =
//            new File(myPaths.datapath, s"aligned/mesh/${targetHand}.vtk")
//          val targetFile =
//            new File(myPaths.datapath, s"partial/mesh/${targetName}.vtk")
//
//          val targetLinemesh2DInit = MeshIO.readLineMesh2D(targetInitFile).get
//          val targetLinemesh3DGT =
//            LineMeshConverter.lineMesh2Dto3D(targetLinemesh2DInit)
//
//          val targetLinemesh2D = MeshIO.readLineMesh2D(targetFile).get
//          val targetLineMesh3D =
//            LineMeshConverter.lineMesh2Dto3D(targetLinemesh2D)
//
////        val ui: ScalismoUI = ScalismoUI()
//          val ui = ScalismoUIHeadless()
//
//          val modelGroup = ui.createGroup("model")
//          ui.show(modelGroup, referenceLineMesh3D, "ref").opacity = 0f
//
//          val targetGroup = ui.createGroup("target")
//          val showTarget =
//            ui.show(targetGroup, targetLineMesh3D, targetFile.getName)
//          ui.show(targetGroup, targetLinemesh3DGT, "GT").color = Color.GREEN
//          showTarget.color = Color.BLACK
//
//          val jsonFileName = s"${targetName}_partial.json"
//
//          val logObj = new JSONAcceptRejectLogger[ModelFittingParameters](
//            new File(logPath, jsonFileName)
//          )
//          val logInit: IndexedSeq[jsonLogFormat] = logObj.loadLog()
//
//          val firstAccept = logInit.filter(f => f.status).head.index
//          val burnInPhase = math.max(firstAccept + 1, 100)
//          println(
//            s"Log length: ${logInit.length}, first accept: ${firstAccept}"
//          )
//
//          val logSamples = LogHelper2D.samplesFromLog(
//            logInit,
//            takeEveryN = 50,
//            total = 10000,
//            burnInPhase
//          )
//          println(
//            s"Number of samples from log: ${logSamples.length}/${logInit.length - burnInPhase}"
//          )
//          val logShapes =
//            LogHelper2D.logSamples2shapes(modelLineMesh, logSamples.map(_._1))
//          val rndShapes = scala.util.Random.shuffle(logShapes)
//
//          val bestGroup = ui.createGroup("best")
//          val sampleGroup = ui.createGroup("samples-LOG")
//
//          val best = modelLineMesh.instance(
//            logObj.getBestFittingParsFromJSON.shapeParameters.parameters
//          )
//
//          val maxNumberOfSamples = math.min(100, rndShapes.length)
//          println(s"Taking ${maxNumberOfSamples} from log")
//          MeshIO.writeLineMesh[_2D](
//            best,
//            new File(registeredPath, s"mesh_partial_${p}/${targetName}_MAP.vtk")
//          )
//          (0 until maxNumberOfSamples).foreach { i =>
//            val m = rndShapes(i)
//            MeshIO.writeLineMesh[_2D](
//              m,
//              new File(
//                registeredPath,
//                s"mesh_partial_${p}/${targetName}_sample-${i}.vtk"
//              )
//            )
//          }
//
//
//          (0 until math.min(50, rndShapes.length)).foreach { i =>
//            val m = rndShapes(i)
//            ui.show(
//                sampleGroup,
//                LineMeshConverter.lineMesh2Dto3D(m),
//                s"rnd-${i.toString}"
//              )
//              .color = Color.ORANGE
//          }
//
//          ui.show(bestGroup, LineMeshConverter.lineMesh2Dto3D(best), "best")
//            .color = Color.BLUE
//
//          def computeClosestPoints(
//            best: LineMesh2D,
//            target: LineMesh2D
//          ): IndexedSeq[(PointId, Point[_2D])] = {
//            val closestModel = best.pointSet.points.toIndexedSeq.map { p =>
//              val targetClosestPoint = target.pointSet.findClosestPoint(p)
//              best.pointSet.findClosestPoint(targetClosestPoint.point)
//            }
//            closestModel.map(
//              p => (p.id, target.pointSet.findClosestPoint(p.point).point)
//            )
//          }
//
//          val cp = computeClosestPoints(best, targetLinemesh2D)
//          val posModel = modelLineMesh.posterior(cp, 1.0)
//
//          println("All done!")
//      }
//    }
//  }
//}
