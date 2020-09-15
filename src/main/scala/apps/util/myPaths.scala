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

package apps.util

import java.io.File

object myPaths {
  val datapath: File = new File("/export/skulls/projects/lousy-hands/data/hands")
//  val experimentPath: File = new File(datapath, "experiments/shapemi_scale_06_25")
  val experimentPath: File = new File(datapath, "experiments/shapemi_gauss_per_06_25") //
//  val experimentPath: File = new File(datapath, "experiments/shapemi_skel_ablation_07_03") //

  //  val experimentPath: File = new File(datapath, "experiments/test")
  val experimentLogPath: File = new File(experimentPath, "log")
}