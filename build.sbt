name := "DownloadImages"
version := "1.0"
scalaVersion := "2.13.0"

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor" % "2.5.25"
)

// sbt 1.3.0 issues
// see https://github.com/sbt/sbt/issues/5075
fork in run := true

// also, there was this error when running from the IDE
// the suggested fix worked
// https://stackoverflow.com/questions/57830084/scala-library-imported-twice
    