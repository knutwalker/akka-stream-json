lazy val `stream-json` = project settings (
  libraryDependencies ++= Seq(
    "com.typesafe.akka" %% "akka-stream" % "2.4.2" % "provided",
    "org.spire-math"    %% "jawn-parser" % "0.8.4"))

lazy val `http-json` = project dependsOn `stream-json` settings (
  libraryDependencies += "com.typesafe.akka" %% "akka-http-experimental" % "2.4.2" % "provided")

lazy val tests = project dependsOn (`stream-json`, `http-json`, `stream-circe`, `http-circe`) settings (
  dontRelease,
  libraryDependencies ++= List(
      "com.typesafe.akka" %% "akka-http-experimental" % "2.4.2" % "test",
      "org.specs2"        %% "specs2-core"            % "3.7.2" % "test",
      "io.circe"          %% "circe-generic"          % "0.3.0" % "test"))
lazy val parent = project in file(".") dependsOn (`http-json`, `http-circe`) aggregate (`stream-json`, `http-json`, `stream-circe`, `http-circe`, tests) settings parentSettings(dontRelease)

// circe support
lazy val `stream-circe` = project in file("support")/"stream-circe" dependsOn `stream-json` settings (
  libraryDependencies ++= Seq(
    "com.typesafe.akka" %% "akka-stream" % "2.4.2" % "provided",
    "io.circe"          %% "circe-jawn"  % "0.3.0"))

lazy val `http-circe` = project in file("support")/"http-circe" dependsOn (`stream-circe`, `http-json`) settings (
  libraryDependencies += "com.typesafe.akka" %% "akka-http-experimental" % "2.4.2" % "provided")




addCommandAlias("travis", ";clean;coverage;testOnly -- timefactor 3;coverageReport;coverageAggregate")
