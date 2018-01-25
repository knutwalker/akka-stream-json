lazy val versions = new {
  val circe      = "0.9.0"
  val akkaHttp   = "10.0.11"
  val akka       = "2.5.9"
  val jawn       = "0.11.0"
  val specs2     = "3.8.6"
}

lazy val `stream-json` = project settings (
  libraryDependencies ++= Seq(
    "com.typesafe.akka" %% "akka-stream" % versions.akka % "provided",
    "org.spire-math"    %% "jawn-parser" % versions.jawn))

lazy val `http-json` = project dependsOn `stream-json` settings (
  libraryDependencies += "com.typesafe.akka" %% "akka-http" % versions.akkaHttp % "provided")

lazy val tests = project dependsOn (`stream-json`, `http-json`, `stream-circe`, `http-circe`) settings (
  dontRelease,
  libraryDependencies ++= List(
      "com.typesafe.akka" %% "akka-http" % versions.akkaHttp % "test",
      "org.specs2"        %% "specs2-core"            % versions.specs2 % "test",
      "io.circe"          %% "circe-generic"          % versions.circe % "test"))
lazy val parent = project in file(".") dependsOn (`http-json`, `http-circe`) aggregate (`stream-json`, `http-json`, `stream-circe`, `http-circe`, tests) settings parentSettings(dontRelease)

// circe support
lazy val `stream-circe` = project in file("support")/"stream-circe" dependsOn `stream-json` settings (
  libraryDependencies ++= Seq(
    "com.typesafe.akka" %% "akka-stream" % versions.akka % "provided",
    "io.circe"          %% "circe-jawn"  % versions.circe))

lazy val `http-circe` = project in file("support")/"http-circe" dependsOn (`stream-circe`, `http-json`) settings (
  libraryDependencies += "com.typesafe.akka" %% "akka-http" % versions.akkaHttp % "provided")

addCommandAlias("travis", ";clean;coverage;testOnly -- timefactor 3;coverageReport;coverageAggregate")
