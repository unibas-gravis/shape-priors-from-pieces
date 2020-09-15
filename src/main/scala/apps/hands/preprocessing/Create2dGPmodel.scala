package apps.hands.preprocessing

import java.io.File

import apps.scalismoExtension.LineMeshConverter
import apps.util.myPaths
import breeze.linalg.DenseMatrix
import scalismo.common.{Domain, EuclideanSpace, Field, PointId}
import scalismo.geometry._
import scalismo.io.MeshIO
import scalismo.kernels.{GaussianKernel, MatrixValuedPDKernel}
import scalismo.mesh.{LineMesh, LineMesh2D}
import scalismo.numerics.Sampler
import scalismo.statisticalmodel.{GaussianProcess, LowRankGaussianProcess, StatisticalLineMeshModel}
import scalismo.ui.api.ScalismoUI
import scalismo.utils.Random

object Create2dGPmodel {

  implicit val random: Random = Random(1024)

  def main(args: Array[String]): Unit = {
    scalismo.initialize()

    val modelOutputFile = new File(myPaths.datapath, "gpModel_new.h5")
    val refOutputtFile = new File(myPaths.datapath, "reference.stl")

    val referenceMesh = MeshIO.readLineMesh2D(new File(myPaths.datapath, "aligned/reference-hand.vtk")).get
    val ref3D = LineMeshConverter.lineMesh2Dto3D(referenceMesh)

    val mm = createModel(referenceMesh, 500, 100)

    val ui = ScalismoUI()

    val sample2D = mm.sample()

    val modelGroup = ui.createGroup("Model")
    val otherGroup = ui.createGroup("other")
    ui.show(modelGroup, ref3D, "ref")
    ui.show(otherGroup, LineMeshConverter.lineMesh2Dto3D(sample2D), "sample2D")

  }

  def createModel(ref: LineMesh2D, numberOfSamplePoints: Int, numBasisFunctions: Int = 100): StatisticalLineMeshModel = {
    println("Num of points in ref: " + ref.pointSet.numberOfPoints)

    val zeroMean2D: Field[_2D, EuclideanVector[_2D]] = Field(EuclideanSpace[_2D], (_: Point[_2D]) => EuclideanVector.zeros[_2D])
    val zeroMean3D: Field[_3D, EuclideanVector[_3D]] = Field(EuclideanSpace[_3D], (_: Point[_3D]) => EuclideanVector.zeros[_3D])

    val scaleL = 50.0
    val scaleM = 20.0
    val scaleS = 3.0
    val sigmaL = 200
    val sigmaM = 100
    val sigmaS = 40
    //    val sigmaT = 5

    val cov2D: MatrixValuedPDKernel[_2D] = new MatrixValuedPDKernel[_2D]() {
      private val baseMatrixL = DenseMatrix((scaleL, 0.0), (0.0, scaleL))
      private val baseMatrixM = DenseMatrix((scaleM, 0.0), (0.0, scaleM))
      private val baseMatrixS = DenseMatrix((scaleS, 0.0), (0.0, scaleS))

      private val largeKernels = GaussianKernel[_2D](sigmaL)
      private val midKernels = GaussianKernel[_2D](sigmaM)
      private val smallKernels = GaussianKernel[_2D](sigmaS)
      //      private val tinyKernels = GaussianKernel[_2D](sigmaT)

      override protected def k(x: Point[_2D], y: Point[_2D]): DenseMatrix[Double] = {
        (baseMatrixL * largeKernels(x, y)) + (baseMatrixM * midKernels(x, y)) + (baseMatrixS * smallKernels(x, y)) // + (baseMatrixS * tinyKernels(x, y))
      }

      override def outputDim = 2

      override def domain: Domain[_2D] = EuclideanSpace[_2D]
    }

    val gp2D: GaussianProcess[_2D, EuclideanVector[_2D]] = GaussianProcess[_2D, EuclideanVector[_2D]](zeroMean2D, cov2D)

    val sampleIds: IndexedSeq[PointId] = scala.util.Random.shuffle(ref.pointSet.pointIds.toIndexedSeq).take(numberOfSamplePoints)

    val sampler2D = DummySamplerWithId2D(ref, sampleIds, 0)
    val lowRankGP2D: LowRankGaussianProcess[_2D, EuclideanVector[_2D]] = LowRankGaussianProcess.approximateGPNystrom(gp2D, sampler2D, numBasisFunctions = numBasisFunctions)

    println(s"Number of components: ${lowRankGP2D.klBasis.length}")

    val mm = StatisticalLineMeshModel(ref, lowRankGP2D)
    mm
  }

  case class DummySampler2D(mesh: LineMesh[_2D], numberOfPoints: Int) extends Sampler[_2D] {
    val pIds: IndexedSeq[PointId] = scala.util.Random.shuffle(mesh.pointSet.pointIds.toIndexedSeq).take(numberOfPoints)
    val p = 1.0

    def volumeOfSampleRegion: Double = 1.0

    def sample(): IndexedSeq[(Point[_2D], Double)] = {
      val points = pIds.map(id => mesh.pointSet.point(id)).toIndexedSeq
      points.map(point => (point, p))
    }
  }

  case class DummySamplerWithId2D(mesh: LineMesh[_2D], pIds: IndexedSeq[PointId], numberOfPoints: Int) extends Sampler[_2D] {
    val p = 1.0

    def volumeOfSampleRegion: Double = 1.0

    def sample(): IndexedSeq[(Point[_2D], Double)] = {
      val points = pIds.map(id => mesh.pointSet.point(id)).toIndexedSeq
      points.map(point => (point, p))
    }
  }

  case class DummySamplerWithId3D(mesh: LineMesh[_2D], pIds: IndexedSeq[PointId], numberOfPoints: Int) extends Sampler[_3D] {
    val p = 1.0

    def volumeOfSampleRegion: Double = 1.0

    def sample(): IndexedSeq[(Point[_3D], Double)] = {
      val points = pIds.map(id => mesh.pointSet.point(id)).toIndexedSeq
      points.map(point => (Point3D(point.x, point.y, 0.0), p))
    }
  }

}
