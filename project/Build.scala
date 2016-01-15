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

import com.typesafe.sbt.SbtGit
import com.typesafe.sbt.SbtGit.GitKeys
import com.typesafe.sbt.git.JGit
import de.heikoseeberger.sbtheader.HeaderPlugin.autoImport.{ createHeaders, headers }
import de.heikoseeberger.sbtheader.license.Apache2_0
import sbt.Keys._
import sbt._
import sbtrelease.ReleasePlugin.autoImport._
import sbtrelease.ReleaseStateTransformations._
import sbtrelease.{ Version, versionFormatError }

import scala.xml.transform.{ RewriteRule, RuleTransformer }
import scala.xml.{ Node ⇒ XNode, NodeSeq ⇒ XNodeSeq }


object Build extends AutoPlugin {
  override def trigger = allRequirements
  override def requires = plugins.JvmPlugin

  object autoImport {
    lazy val latestVersionTag = settingKey[Option[String]]("The latest tag describing a version number.")
    lazy val latestVersion = settingKey[String]("The latest version or the current one, if there is no previous version.")

    lazy val maintainer = settingKey[String]("The Maintainer that appears in the license header")
    lazy val githubProject = settingKey[Github]("Github user/repo for this project")
    lazy val githubDevs = settingKey[Seq[Developer]]("Developers of this project")
    lazy val projectName = settingKey[String]("umbrella project name")

    lazy val dontRelease = List(
              publish := (),
         publishLocal := (),
      publishArtifact := false
    )

    def parentSettings(additional: Def.Setting[_]*) = Seq(
      name := projectName.value
    ) ++ additional ++ dontRelease
  }
  import autoImport._

  override lazy val projectSettings =
    pluginSettings ++ orgaSettings ++ compilerSettings ++
    docsSettings ++ releaseSettings ++ miscSettings

  override lazy val buildSettings =
    SbtGit.versionWithGit


  lazy val orgaSettings = Seq(
           projectName := "akka",
                  name := s"${projectName.value}-${thisProject.value.id}",
          organization := "de.knutwalker",
           description := "Json support for Akka Streams/Http via Jawn",
            maintainer := "Paul Horn",
             startYear := Some(2015),
         githubProject := Github("knutwalker", "akka-stream-json"),
            githubDevs := Developer(githubProject.value.org, maintainer.value) :: Nil,
  organizationHomepage := Some(githubProject.value.organization),
              homepage := Some(githubProject.value.repository)
  )

  lazy val compilerSettings = Seq(
    scalaVersion := "2.11.7",
    crossScalaVersions := scalaVersion.value :: "2.10.6" :: Nil,
    scalacOptions in Compile := Seq(
      "-deprecation",
      "-encoding", "UTF-8",
      "-feature",
      "-language:_",
      "-target:jvm-1.7",
      "-unchecked",
      "-Xfatal-warnings",
      "-Xfuture",
      "-Xlint:_",
      "-Yclosure-elim",
      "-Yconst-opt",
      "-Ydead-code",
      "-Yno-adapted-args",
      "-Ywarn-adapted-args",
      "-Ywarn-dead-code",
      "-Ywarn-inaccessible",
      "-Ywarn-infer-any",
      "-Ywarn-nullary-override",
      "-Ywarn-nullary-unit",
      "-Ywarn-numeric-widen",
      "-Ywarn-value-discard"
    ),
    scalacOptions in Test              ++= Seq("-Xcheckinit", "-Yrangepos"),
    scalacOptions in (Compile, console) ~= (_ filterNot (x => x == "-Xfatal-warnings" || x.startsWith("-Ywarn"))),
       scalacOptions in (Test, console) ~= (_ filterNot (x => x == "-Xfatal-warnings" || x.startsWith("-Ywarn")))
  )

  lazy val docsSettings = Seq(
     autoAPIMappings := true,
    latestVersionTag := GitKeys.gitReader.value.withGit(g ⇒ findLatestVersion(g.asInstanceOf[JGit])),
       latestVersion := latestVersionTag.value.getOrElse(version.value),
            pomExtra := pomExtra.value ++
              <properties>
                <info.apiURL>http://{githubProject.value.org}.github.io/{githubProject.value.repo}/api/</info.apiURL>
              </properties>
  )

  lazy val releaseSettings = Seq(
                  scmInfo := Some(githubProject.value.scmInfo),
                 licenses := List("Apache 2" -> url("https://www.apache.org/licenses/LICENSE-2.0.html")),
               developers := githubDevs.value.map(_.sbtDev).toList,
           pomPostProcess := { (node) => rewriteTransformer.transform(node).head },
  publishArtifact in Test := false,
        releaseTagComment := s"Release version ${version.value}",
     releaseCommitMessage := s"Set version to ${version.value}",
       releaseVersionBump := Version.Bump.Bugfix,
           releaseProcess := List[ReleaseStep](
             checkSnapshotDependencies,
             inquireVersions,
             runClean,
             runTest,
             setReleaseVersion,
             commitReleaseVersion,
             tagRelease,
             publishSignedArtifacts,
             releaseToCentral,
             setNextVersion,
             commitNextVersion,
             pushChanges
           )
  )

  lazy val miscSettings = Seq(
        shellPrompt := { state => configurePrompt(state) },
        logBuffered := false,
    cleanKeepFiles ++= List("resolution-cache", "streams").map(target.value / _),
      updateOptions ~= (_.withCachedResolution(cachedResoluton = true))
  )

  lazy val pluginSettings =
    inConfig(Compile)(headerSettings) ++
    inConfig(Test)(headerSettings)

  lazy val headerSettings = Seq(
    compile := compile.dependsOn(createHeaders).value,
    headers <<= (startYear, maintainer) apply headerConfig
  )

  private def headerConfig(year: Option[Int], maintainer: String) = {
    val thisYear = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
    val years = List(year.getOrElse(thisYear), thisYear).distinct.mkString(" – ")
    Map("java"  -> Apache2_0(years, maintainer),
        "scala" -> Apache2_0(years, maintainer),
        "conf"  -> Apache2_0(years, maintainer, "#"))
  }

  private def configurePrompt(st: State) = {
    import scala.Console._
    val name = Project.extract(st).currentRef.project
    val color = GREEN
    (if (name == "parent") "" else s"[$color$name$RESET] ") + "> "
  }

  private val rewriteRule = new RewriteRule {
    override def transform(n: XNode): XNodeSeq =
      if (n.label == "dependency" && (n \ "scope").text == "provided" && (n \ "groupId").text == "org.scoverage")
        XNodeSeq.Empty
      else n
  }

  private val rewriteTransformer = new RuleTransformer(rewriteRule)

  private def findLatestVersion(git: JGit): Option[String] = {
    val tags = git.tags.collect {
      case tag if tag.getName startsWith "refs/tags/" ⇒
        tag.getName drop 10 replaceFirst ("^v", "")
    }
    val sortedTags = tags.flatMap(Version(_)).sorted.map(_.string)
    sortedTags.lastOption
  }

  private lazy val inquireVersions = ReleaseStep { st: State ⇒
    val extracted = Project.extract(st)
    val lastVer = extracted.get(latestVersionTag).getOrElse("0.0.0")
    val bump = extracted.get(releaseVersionBump)
    val suggestedVersion = Version(lastVer).map(_.withoutQualifier.bump(bump).string).getOrElse(versionFormatError)
    val releaseVersion = readVersion(suggestedVersion, "Release version [%s] : ")
    st.put(ReleaseKeys.versions, (releaseVersion, releaseVersion))
  }

  private lazy val setReleaseVersion = ReleaseStep { st: State =>
    val vs = st.get(ReleaseKeys.versions).getOrElse(sys.error("No versions are set! Was this release part executed before inquireVersions?"))
    val selected = vs._1
    st.log.info(s"Setting version to '$selected'.")
    reapply(Seq(version in ThisBuild := selected), st)
  }

  private def readVersion(ver: String, prompt: String): String = {
    SimpleReader.readLine(prompt format ver) match {
      case Some("") => ver
      case Some(input) => Version(input).map(_.string).getOrElse(versionFormatError)
      case None => sys.error("No version provided!")
    }
  }

  private implicit val versionOrdering = new Ordering[Version] {
    def compare(x: Version, y: Version): Int =
      x.major compare y.major match {
        case 0 ⇒ x.minor.getOrElse(0) compare y.minor.getOrElse(0) match {
          case 0 ⇒ x.bugfix.getOrElse(0) compare y.bugfix.getOrElse(0) match {
            case 0 ⇒ (x.qualifier, y.qualifier) match {
              case (None, None) ⇒ 0
              case (Some(_), Some(_)) ⇒ 0
              case (None, _) ⇒ 1
              case (_, None) ⇒ -1
            }
            case a ⇒ a
          }
          case a ⇒ a
        }
        case a ⇒ a
      }
  }

  private lazy val publishSignedArtifacts = ReleaseStep(
    action = Command.process("publishSigned", _),
    enableCrossBuild = true
  )

  private lazy val releaseToCentral = ReleaseStep(
    action = Command.process("sonatypeReleaseAll", _),
    enableCrossBuild = true
  )

  case class Developer(id: String, name: String, identity: Developer.Identity = Developer.Github) {

    def url: URL =
      sbt.url(identity.url(id))

    def sbtDev: sbt.Developer =
      sbt.Developer(id, name, "", url)
  }

  object Developer {
    def apply(id: String): Developer =
      new Developer(id, id)


    sealed trait Identity { def url(id: String): String }

    case object Github extends Identity {
      def url(id: String) = s"https://github.com/$id/"
    }

    case object Twitter extends Identity {
      def url(id: String) = s"https://twitter.com/$id/"
    }
  }

  case class Github(org: String, repo: String) {

    def repository: URL =
      url(s"https://github.com/$org/$repo/")

    def organization: URL =
      url(s"https://github.com/$org/")

    def scmInfo: ScmInfo = ScmInfo(
      repository,
      s"scm:git:https://github.com/$org/$repo.git",
      Some(s"scm:git:ssh://git@github.com:$org/$repo.git")
    )
  }
}
