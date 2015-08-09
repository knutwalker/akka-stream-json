                name := "akka-http-jawn-argonaut"
        organization := "de.knutwalker"
           startYear := Some(2015)
          maintainer := "Paul Horn"
       githubProject := Github("knutwalker", "akka-http-jawn-argonaut")
         description := "Argonaut Support with Jawn parser for Akka HTTP"
        scalaVersion := "2.11.7"
  crossScalaVersions := scalaVersion.value :: "2.10.5" :: Nil
libraryDependencies ++= List(
    "com.typesafe.akka" %% "akka-http-experimental" % "1.0"   % "provided",
    "org.spire-math"    %% "jawn-argonaut"          % "0.8.3" % "compile" ,
    "org.specs2"        %% "specs2-core"            % "3.6.4" % "test"    )

addCommandAlias("travis", ";clean;coverage;test;coverageReport;coverageAggregate")
