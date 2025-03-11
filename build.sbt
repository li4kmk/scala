import Dependencies._

ThisBuild / scalaVersion     := "2.13.12"
ThisBuild / version          := "0.1.0-SNAPSHOT"
ThisBuild / organization     := "com.example"
ThisBuild / organizationName := "example"

lazy val root = (project in file("."))
  .settings(
    name := "sberSbt",
    libraryDependencies ++= Seq(
      "org.apache.poi" % "poi" % "5.2.3",                // Зависимость для работы с .xls файлами
      "org.apache.poi" % "poi-ooxml" % "5.2.3",          // Зависимость для работы с .xlsx файлами
      "org.json4s" %% "json4s-native" % "4.0.3",         // Зависимость для json4s (native)
      "org.json4s" %% "json4s-jackson" % "4.0.3",        // Зависимость для json4s (jackson)
      munit % Test
    )
  )

// See https://www.scala-sbt.org/1.x/docs/Using-Sonatype.html for instructions on how to publish to Sonatype.
