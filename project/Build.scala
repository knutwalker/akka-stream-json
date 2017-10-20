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

import bintray.BintrayKeys.{ bintrayPackage, bintray => bt }
import de.knutwalker.sbt._
import de.knutwalker.sbt.KSbtKeys._

import com.typesafe.sbt.SbtGit.git
import sbt.Keys._
import sbt._


object Build extends AutoPlugin {
  override def trigger = allRequirements
  override def requires = KSbtPlugin

  val currentScalaVersion = "2.12.3"

  override lazy val projectSettings = Seq(
           git.baseVersion := "3.5.0",
               projectName := "akka", // see https://github.com/knutwalker/akka-stream-json/pull/4#issuecomment-244199557 for why it's akka and not akka-stream-json
              organization := "de.knutwalker",
               description := "Json support for Akka Streams/Http via Jawn",
                maintainer := "Paul Horn",
                 startYear := Some(2015),
             githubProject := Github("knutwalker", "akka-stream-json"),
            bintrayPackage := "akka-stream-json",
               javaVersion := JavaVersion.Java18,
        crossScalaVersions := Seq("2.11.11", currentScalaVersion),
              scalaVersion := currentScalaVersion,
                 publishTo := { if (git.gitCurrentTags.value.isEmpty) (publishTo in bt).value else publishTo.value },
  scalacOptions in Compile := {
    val old = (scalacOptions in Compile).value
    scalaBinaryVersion.value match {
      case "2.11" => old :+ "-Xexperimental"
      case "2.12" => old.filterNot(_ == "-opt:l:project") ++ Seq("-opt:l:inline", "-Xexperimental")
    }
  })
}
