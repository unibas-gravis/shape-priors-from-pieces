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

package apps.hands.preprocessing

import java.io.File

import apps.hands.Paths
import apps.scalismoExtension.FormatConverter
import apps.util.VisualizationHelper
import breeze.linalg.DenseMatrix
import scalismo.common.interpolation.NearestNeighborInterpolator
import scalismo.common.{Domain, EuclideanSpace, Field}
import scalismo.geometry._
import scalismo.io.{LandmarkIO, MeshIO, StatisticalModelIO}
import scalismo.kernels.{GaussianKernel, MatrixValuedPDKernel}
import scalismo.mesh.LineMesh
import scalismo.statisticalmodel.{GaussianProcess, LowRankGaussianProcess, PointDistributionModel}
import scalismo.ui.api.ScalismoUI
import scalismo.utils.Random

object Create2DGPModel {

  /**
   * @return the index of the closest bone, the multiple of bone vector or None if not within bone frames
   */
  def getClosestBone(p: Point[_2D], bones: IndexedSeq[(Point[_2D], EuclideanVector[_2D])], minDistance: Double = 0.0, maxDistance: Double = 1.0, maxAssignment: Double = 32.0): Option[(Int, Double)] = {
    val bone = bones.zipWithIndex.map { case (bone, i) => {
      val normal = bone._2.normalize
      val proj = (bone._1 - p) - normal.*((bone._1 - p).dot(normal))
      val clp = p + proj
      val distance = if (math.abs(normal.x) > math.abs(normal.y)) (clp.x - bone._1.x) / bone._2.x else (clp.y - bone._1.y) / bone._2.y
      (i, distance, proj.norm, distance > minDistance && distance < maxDistance)
    }
    }.filter(_._4)

    if (bone.isEmpty) None else {
      val selected = bone.minBy(_._3)
      if (selected._3 < maxAssignment) Option((selected._1, selected._2)) else None
    }
  }

  /**
   * creates a kernel that simulates expert knowledge on difficult shapes where the correct variability might be unknown.
   * It is therefore an approximation of the anatomical truth for finger movement.
   */
  def createPerpendicularModelKernel(referenceMesh: LineMesh[_2D], lmsAll: Seq[Landmark[_2D]]): MatrixValuedPDKernel[_2D] = {
    val thumb = ("finger.ulnar.middle", "finger.thumb.tip", "finger.thumb.valley")
    val index = ("finger.thumb.valley", "finger.index.tip", "finger.index.valley")
    val middle = ("finger.index.valley", "finger.long.tip", "finger.long.valley")
    val ring = ("finger.long.valley", "finger.ring.tip", "finger.ring.valley")
    val small = ("finger.ring.valley", "finger.small.tip", "finger.small.valley")
    val bones = IndexedSeq(thumb, index, middle, ring, small).flatMap(bone => {
      val a1 = lmsAll.find(_.id == bone._1).get.point
      val b = lmsAll.find(_.id == bone._2).get.point
      val a2 = lmsAll.find(_.id == bone._3).get.point
      val m1 = b - a1
      val m2 = b - a2
      Seq((a1, m1), (a2, m2))
    })

    val sigma2 = 1.0
    val amp = 50.0
    val minD = -0.01
    val maxD = 1.05
    val skeletonKernel = bones.grouped(2).zipWithIndex.map { case (finger, i) => {
      val dir = finger.head._2 + finger.last._2
      val alpha = math.acos(dir.y / dir.norm)
      //TODO check in variance visualization the direction of variance for the thumb and pinky.
      val rot = DenseMatrix(
        (math.cos(alpha), if (dir.x > 0.0) math.sin(alpha) else -math.sin(alpha)),
        (if (dir.x > 0.0) -math.sin(alpha) else math.sin(alpha), math.cos(alpha))
      )
      val dimcov = amp * rot * DenseMatrix((1.0, 0.0), (0.0, 0.1)) * rot.t
      new MatrixValuedPDKernel[_2D] {
        override def outputDim: Int = 2

        override def domain: Domain[_2D] = EuclideanSpace[_2D]

        override def k(x: Point[_2D], y: Point[_2D]): DenseMatrix[Double] = {
          val cx = getClosestBone(x, bones, minD, maxD)
          val cy = getClosestBone(y, bones, minD, maxD)
          val fx = if (cx.isDefined) cx.get._1 / 2 else -1
          val fy = if (cy.isDefined) cy.get._1 / 2 else -1
          if (cx.isDefined && cy.isDefined && (fx == i || fy == i)) {
            val dx = (cx.get._2 - minD) / (maxD - minD)
            val dy = (cy.get._2 - minD) / (maxD - minD)
            //increases correlation for points on the same finger. the thumb has a smaller influence on other fingers
            val fingerPenalty = math.pow(0.5, math.abs(fx - fy) + (if (fx == 0 ^ fy == 0) 4.0 else 1.0))
            //final covariance calculation with increased variability for points on the thumb
            fingerPenalty * dx * dy * dimcov * math.exp(-math.pow(dx - dy, 2.0) / sigma2) * (if (fx == 0 && fy == 0) 5.0 else 1.0)
          } else DenseMatrix((0.0, 0.0), (0.0, 0.0))
        }
      }
    }
    }
    skeletonKernel.reduce(_.+(_))
  }

  val smoothCov2D: MatrixValuedPDKernel[_2D] = new MatrixValuedPDKernel[_2D]() {
    val scaleL = 100.0
    val scaleM = 30.0
    val scaleS = 10.0
    val sigmaL = 120
    val sigmaM = 50
    val sigmaS = 25
    private val baseMatrixL = DenseMatrix((scaleL, 0.0), (0.0, scaleL))
    private val baseMatrixM = DenseMatrix((scaleM, 0.0), (0.0, scaleM))
    private val baseMatrixS = DenseMatrix((scaleS, 0.0), (0.0, scaleS))

    private val largeKernels = GaussianKernel[_2D](sigmaL)
    private val midKernels = GaussianKernel[_2D](sigmaM)
    private val smallKernels = GaussianKernel[_2D](sigmaS)

    override protected def k(x: Point[_2D], y: Point[_2D]): DenseMatrix[Double] = {
      (baseMatrixL * largeKernels(x, y)) +
        (baseMatrixM * midKernels(x, y)) +
        (baseMatrixS * smallKernels(x, y))
    }

    override def outputDim = 2

    override def domain: Domain[_2D] = EuclideanSpace[_2D]
  }

  def main(args: Array[String]): Unit = {
    scalismo.initialize()

    implicit val rng: Random = scalismo.utils.Random(42)

    val zeroMean: AnyRef with Field[_2D, EuclideanVector[_2D]] = Field(EuclideanSpace[_2D], _ => EuclideanVector.zeros[_2D])

    val referenceMesh = MeshIO.readLineMesh2D(new File(Paths.handPath, "reference-hand.vtk")).get

    val lmsFile = LandmarkIO.readLandmarksJson[_2D](new File(Paths.handPath, "reference-hand.json")).get
    val smalltip = lmsFile.find(_.id == "finger.small.tip").get
    val radialend = lmsFile.find(_.id == "finger.radial.middle").get
    val mean = (smalltip.point + radialend.point.toVector).map(_ / 2.0)
    val newlm = referenceMesh.pointSet.findClosestPoint(Point2D(mean.x, mean.y))
    //this adds a landmark to the outer side of the little finger
    val lmsAll = lmsFile ++ Seq(new Landmark[_2D]("finger.small.valley", Point2D(newlm.point.x, newlm.point.y)))

    val perpendicularGP = createPerpendicularModelKernel(referenceMesh, lmsAll)

    val gp = GaussianProcess(zeroMean, smoothCov2D + perpendicularGP)

    val lowRankGP = LowRankGaussianProcess.approximateGPCholesky(
      referenceMesh,
      gp,
      relativeTolerance = 0.01,
      interpolator = NearestNeighborInterpolator()
    )


    val ssm = PointDistributionModel(referenceMesh, lowRankGP)

    StatisticalModelIO.writeStatisticalLineMeshModel2D(ssm, new File(Paths.handPath, "hand2D_gp_s25_s50_s120_per.h5"))

    println(s"Model created with ${ssm.gp.rank} components")

    val ui = ScalismoUI()

    val sampleGroup = ui.createGroup("samples")

    ui.show(sampleGroup, FormatConverter.lineMesh2Dto3D(ssm.reference), "reference")
    (1 to 10).foreach(i => {
      val sample = ssm.sample()
      ui.show(sampleGroup, FormatConverter.lineMesh2Dto3D(sample), s"sample$i").opacity = 0
    })

    val pcGroup = ui.createGroup("PCs")

    VisualizationHelper.visualizePCsamples(ui, ssm, pcGroup)
  }
}
