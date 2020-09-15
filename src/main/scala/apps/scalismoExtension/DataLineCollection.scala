///*
// * Copyright 2015 University of Basel, Graphics and Vision Research Group
// *
// * Licensed under the Apache License, Version 2.0 (the "License");
// * you may not use this file except in compliance with the License.
// * You may obtain a copy of the License at
// *
// *     http://www.apache.org/licenses/LICENSE-2.0
// *
// * Unless required by applicable law or agreed to in writing, software
// * distributed under the License is distributed on an "AS IS" BASIS,
// * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// * See the License for the specific language governing permissions and
// * limitations under the License.
// */
//package scalismo.statisticalmodel.dataset
//
//import java.io.File
//
//import api.other.LineMeshMetrics2D
//import scalismo.geometry._
//import scalismo.io.MeshIO
//import scalismo.mesh._
//import scalismo.registration.LandmarkRegistration
//import scalismo.transformations.Transformation
//import scalismo.utils.Random
//
//import scala.annotation.tailrec
//import scala.util.{Failure, Success, Try}
//
//private[dataset] case class CrossvalidationFold(trainingData: DataLineCollection, testingData: DataLineCollection)
//
/////**
////  * A registered item in a dataset.
////  *
////  *  @param info A human-readable description of the processing the data item went through. Current implemented methods on data collections,
////  *  such as [[DataLineCollection.gpa]] will increment this description
////  *  @param transformation Transformation to apply to obtain the data item from the reference of the reference item of the dataset.
////  *  This would typically be the transformation resulting from registering a reference mesh to the mesh represented by this data item.
////  */
////case class DataItem[D](info: String, transformation: Transformation[D])
//
///**
//  * Data-structure for handling a dataset of registered 3D meshes. All pre-implemented operations such as building a
//  * PCA model or performing a Generalized Procrustes Analysis require a DataLineCollection as input
//  *
//  * @param reference The reference mesh of the dataset. This is the mesh that was registered to all other items of the dataset.
//  * @param dataItems Sequence of data items containing the required transformations to apply to the reference mesh in order to obtain
//  * other elements of the dataset.
//  */
//case class DataLineCollection(reference: LineMesh[_2D], dataItems: Seq[DataItem[_2D]])(implicit random: Random) {
//
//  val size: Int = dataItems.size
//
//  private[dataset] def createCrossValidationFolds(nFolds: Int): Seq[CrossvalidationFold] = {
//
//    val shuffledDataItems = random.scalaRandom.shuffle(dataItems)
//    val foldSize = shuffledDataItems.size / nFolds
//    val dataGroups = shuffledDataItems.grouped(foldSize).toSeq
//
//    val folds = for (currFold <- 0 until nFolds) yield {
//      val testingDataItems = dataGroups(currFold)
//      val testingCollection = DataLineCollection(reference, testingDataItems)
//      val trainingDataItems = (dataGroups.slice(0, currFold).flatten ++: dataGroups.slice(currFold + 1, dataGroups.size).flatten)
//      val trainingCollection = DataLineCollection(reference, trainingDataItems)
//
//      CrossvalidationFold(trainingCollection, testingCollection)
//    }
//    folds
//  }
//
//  private[dataset] def createLeaveOneOutFolds = createCrossValidationFolds(dataItems.size)
//
//  /**
//    * Returns a new DataLineCollection where the given function was applied to all data items
//    */
//  def mapItems(f: DataItem[_2D] => DataItem[_2D]): DataLineCollection = {
//    new DataLineCollection(reference, dataItems.map(f))
//  }
//
//  /**
//    * Returns the mean surface computed by transforming the reference with all the transformations in the datalinecollection
//    */
//  def meanSurface: LineMesh[_2D] = {
//    val t = reference.transform(meanTransformation)
//    t
//  }
//
//  /**
//    * Returns the mean transformation from all the transformation in the datalinecollection
//    */
//  val meanTransformation: Transformation[_2D] = {
//
//    Transformation {
//
//      (pt: Point[_2D]) =>
//      {
//        var meanPoint = EuclideanVector2D(0, 0)
//        var i = 0
//        while (i < dataItems.size) {
//          meanPoint += dataItems(i).transformation(pt).toVector
//          i += 1
//        }
//        (meanPoint / dataItems.size).toPoint
//      }
//    }
//  }
//}
//
///**
//  * Implements utility functions on [[DataLineCollection]] instances
//  */
//object DataLineCollection {
//
//  private def meshLineToTransformation(refMesh: LineMesh[_2D], targetMesh: LineMesh[_2D]): Try[Transformation[_2D]] = {
//    if (refMesh.pointSet.numberOfPoints != targetMesh.pointSet.numberOfPoints)
//      Failure(
//        new Throwable(
//          s"reference and target mesh do not have the same number of points (${refMesh.pointSet.numberOfPoints} != ${targetMesh.pointSet.numberOfPoints}"
//        )
//      )
//    else {
//      val t = new Transformation[_2D] {
//        override val domain = refMesh.boundingBox
//        override val f = (x: Point[_2D]) => {
//          val ptId = refMesh.pointSet.findClosestPoint(x).id
//          targetMesh.pointSet.point(ptId)
//        }
//      }
//      Success(t)
//    }
//  }
//
//  /**
//    * Builds a [[DataLineCollection]] instance from a reference mesh and a sequence of meshes in correspondence.
//    * Returns a data collection containing the valid elements as well as the list of errors for invalid items.
//    */
//  def fromMeshSequence(referenceMesh: LineMesh[_2D], registeredMeshes: Seq[LineMesh[_2D]])(implicit rng: Random): (Option[DataLineCollection], Seq[Throwable]) = {
//    val (transformations, errors) = DataUtils.partitionSuccAndFailedTries(registeredMeshes.map(meshLineToTransformation(referenceMesh, _)))
//    val dc = DataLineCollection(referenceMesh, transformations.map(DataItem("from mesh", _)))
//    if (dc.size > 0) (Some(dc), errors) else (None, errors)
//  }
//
//  /**
//    * Builds a [[DataLineCollection]] instance from a reference mesh and a directory containing meshes in correspondence with the reference.
//    * Only vtk and stl meshes are currently supported.
//    *
//    * @return a data collection containing the valid elements as well as the list of errors for invalid items.
//    */
//  def fromMeshDirectory(referenceMesh: LineMesh[_2D], meshDirectory: File)(implicit rng: Random): (Option[DataLineCollection], Seq[Throwable]) = {
//    val meshFileNames = meshDirectory.listFiles().toSeq.filter(fn => fn.getAbsolutePath.endsWith(".vtk") || fn.getAbsolutePath.endsWith(".stl"))
//    val (meshes, ioErrors) = DataUtils.partitionSuccAndFailedTries(for (meshFn <- meshFileNames) yield {
//      MeshIO.readLineMesh2D(meshFn).map(m => LineMesh2D(m.pointSet, referenceMesh.topology))
//    })
//    val (dc, meshErrors) = fromMeshSequence(referenceMesh, meshes)
//    (dc, ioErrors ++ meshErrors)
//  }
//
//  /**
//    * Performs a Generalized Procrustes Analysis on the data collection.
//    * This is done by repeatedly computing the mean of all meshes in the dataset and
//    * aligning all items rigidly to the mean.
//    *
//    * The reference mesh is unchanged, only the transformations in the collection are adapted
//    */
//  def gpa(dc: DataLineCollection, maxIteration: Int = 3, haltDistance: Double = 1e-5)(implicit rng: Random): DataLineCollection = {
//    gpaComputation(dc, dc.meanSurface, maxIteration, haltDistance)
//  }
//
//  @tailrec
//  private def gpaComputation(dc: DataLineCollection, meanShape: LineMesh[_2D], maxIteration: Int, haltDistance: Double)(implicit rng: Random): DataLineCollection = {
//
//    if (maxIteration == 0) return dc
//
//    val referencePoints = dc.reference.pointSet.points.toIndexedSeq
//    val numberOfPoints = referencePoints.size
//    val referenceCenterOfMass = referencePoints.foldLeft(Point2D(0, 0))((acc, pt) => acc + (pt.toVector / numberOfPoints))
//
//    val meanShapePoints = meanShape.pointSet.points.toIndexedSeq
//
//    // align all shape to it and create a transformation from the mean to the aligned shape
//    val dataItemsWithAlignedTransform = dc.dataItems.par.map { dataItem =>
//      val surface = dc.reference.transform(dataItem.transformation)
//      val transform = LandmarkRegistration.rigid2DLandmarkRegistration(surface.pointSet.points.toIndexedSeq.zip(meanShapePoints), referenceCenterOfMass)
//
//      DataItem("gpa -> " + dataItem.info, Transformation(transform.compose(dataItem.transformation)))
//    }
//
//    val newdc = DataLineCollection(dc.reference, dataItemsWithAlignedTransform.toIndexedSeq)
//    val newMean = newdc.meanSurface
//
//    if (LineMeshMetrics2D.procrustesDistance(meanShape, newMean) < haltDistance) {
//      newdc
//    } else {
//      gpaComputation(newdc, newMean, maxIteration - 1, haltDistance)
//    }
//  }
//}
