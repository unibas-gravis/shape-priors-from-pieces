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

package apps.hands

import apps.util.Visualization2DHelper
import scalismo.io.StatisticalModelIO
import scalismo.ui.api.ScalismoUI
import scalismo.utils.Random.implicits.randomGenerator

object VisualizeHandModel {

  def main(args: Array[String]) {
    scalismo.initialize()

    val modelFile = Paths.handPath.listFiles(_.getName.endsWith(".h5")).head

    val model = StatisticalModelIO.readStatisticalLineMeshModel2D(modelFile).get

    val ui = ScalismoUI("Visualize hand model")
    val modelGroup = ui.createGroup("model")
    Visualization2DHelper.visualizePCsamples(ui, model, modelGroup)

    val randomGroup = ui.createGroup("random")
    (0 until 10).foreach{i =>
      val showing = Visualization2DHelper.show2DLineMesh(ui, randomGroup, model.sample(), s"${i}")
      showing.opacity = 0f
    }
  }
}
