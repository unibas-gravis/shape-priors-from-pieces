package apps.scalismoExtension

import java.io.File

import breeze.linalg.DenseMatrix
import scalismo.common.{Domain, RealSpace, UnstructuredPointsDomain, VectorField}
import scalismo.geometry.{EuclideanVector, Point, Point3D, _3D}
import scalismo.io.StatisticalModelIO
import scalismo.kernels.{DiagonalKernel, GaussianKernel, MatrixValuedPDKernel}
import scalismo.mesh.{TriangleList, TriangleMesh3D}
import scalismo.numerics.RandomMeshSampler3D
import scalismo.statisticalmodel.{GaussianProcess, LowRankGaussianProcess, StatisticalMeshModel}
import scalismo.utils.Random

object ModelSaveLoadTest extends App {
  implicit val Random: Random = scalismo.utils.Random(1024)

  println("starting app...")
  scalismo.initialize()

  val zeroMean = VectorField(RealSpace[_3D], (_: Point[_3D]) => EuclideanVector.zeros[_3D])

  val cov: MatrixValuedPDKernel[_3D] = new MatrixValuedPDKernel[_3D]() {
    private val kernel = DiagonalKernel(GaussianKernel[_3D](1), 3)

    override protected def k(x: Point[_3D], y: Point[_3D]): DenseMatrix[Double] = {
      kernel(x, y)
    }

    override def outputDim = 3

    override def domain: Domain[_3D] = RealSpace[_3D]
  }

  val gp = GaussianProcess[_3D, EuclideanVector[_3D]](zeroMean, cov)

  val pointDomain = UnstructuredPointsDomain[_3D](IndexedSeq(Point3D(0, 0, 0), Point3D(1, 0, 0), Point3D(0, 1, 0)))
  val mesh = TriangleMesh3D(pointDomain, TriangleList(IndexedSeq()))

  val sampler = RandomMeshSampler3D(mesh, 10, 1024)
  val lowRankGP = LowRankGaussianProcess.approximateGPNystrom(gp, sampler, numBasisFunctions = 100)

  val ssm = StatisticalMeshModel(mesh, lowRankGP)

  val modelFile = new File("/tmp/dummy.h5")
  StatisticalModelIO.writeStatisticalMeshModel(ssm, modelFile)
  println("File written")
  StatisticalModelIO.readStatisticalMeshModel(modelFile).get
  println("File read")
}
