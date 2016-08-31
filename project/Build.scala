/*
 * Copyright 2016 Paul Horn
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import bintray.BintrayKeys.{ bintrayPackage, bintray ⇒ bt }
import de.knutwalker.sbt._
import de.knutwalker.sbt.KSbtKeys._

import com.typesafe.sbt.SbtGit.git
import com.typesafe.sbt.pgp.PgpKeys
import sbt.Keys._
import sbt._


object Build extends AutoPlugin {
  override def trigger = allRequirements
  override def requires = KSbtPlugin

  override lazy val projectSettings = Seq(
           git.baseVersion := "3.0.0",
               projectName := "akka-stream-json",
              organization := "de.knutwalker",
               description := "Json support for Akka Streams/Http via Jawn",
                maintainer := "Paul Horn",
                 startYear := Some(2015),
             githubProject := Github("knutwalker", "akka-stream-json"),
            bintrayPackage := "akka-stream-json",
               javaVersion := JavaVersion.Java18,
              scalaVersion := "2.11.8",
  scalacOptions in Compile += "-Xexperimental",
                 publishTo := { if (git.gitCurrentTags.value.isEmpty) (publishTo in bt).value else publishTo.value }
  )
}
