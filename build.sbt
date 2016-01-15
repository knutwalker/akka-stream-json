import sbt._, Keys._ //, _root_.Build.autoImport._ // screw you, IntelliJ

lazy val `stream-json` = project settings (
  libraryDependencies ++= Seq(
    "com.typesafe.akka" %% "akka-stream-experimental" % "2.0.2" % "provided",
    "org.spire-math"    %% "jawn-parser"              % "0.8.3"))

lazy val `http-json` = project dependsOn `stream-json` settings (
  libraryDependencies += "com.typesafe.akka" %% "akka-http-experimental" % "2.0.2" % "provided")


lazy val `stream-circe` = project in file("support")/"stream-circe" dependsOn `stream-json` settings (
  libraryDependencies ++= Seq(
    "com.typesafe.akka" %% "akka-stream-experimental" % "2.0.2" % "provided",
    "io.circe"          %% "circe-jawn"               % "0.2.1"))

lazy val `http-circe` = project in file("support")/"http-circe" dependsOn (`stream-circe`, `http-json`) settings (
  libraryDependencies += "com.typesafe.akka" %% "akka-http-experimental" % "2.0.2" % "provided")


lazy val all = project dependsOn (allDeps: _*) settings dontRelease
lazy val tests = project dependsOn all settings (
  dontRelease,
  libraryDependencies ++= List(
    "com.typesafe.akka" %% "akka-http-experimental" % "2.0.2" % "test",
    "org.specs2"        %% "specs2-core"            % "3.7"   % "test",
    "io.circe"          %% "circe-generic"          % "0.2.1" % "test"))
lazy val parent = project in file(".") dependsOn all aggregate (allProducts: _*) settings parentSettings()
lazy val allProjects = Seq(`stream-json`, `http-json`, `stream-circe`, `http-circe`)


addCommandAlias("travis", ";clean;coverage;testOnly -- timefactor 3;coverageReport;coverageAggregate;docs/makeSite")
lazy val allModules = allProjects.map(_.project)
lazy val allDeps = allModules.map(x ⇒ ClasspathDependency(x, None))
lazy val allProducts = allModules :+ tests.project
