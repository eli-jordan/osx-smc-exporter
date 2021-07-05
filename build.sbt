ThisBuild / scalaVersion := "2.13.5"
ThisBuild / version := "0.1.0-SNAPSHOT"
ThisBuild / organization := "io.smc.exporter"

val circeVersion = "0.14.1"
val prometheusVersion = "0.11.0"
lazy val root = (project in file("."))
  .settings(
    name := "osx-smc-exporter",
    libraryDependencies ++= Seq(
      "io.prometheus" % "simpleclient" % prometheusVersion,
      "io.prometheus" % "simpleclient_httpserver" % prometheusVersion,

      "io.circe" %% "circe-core" % circeVersion,
      "io.circe" %% "circe-generic" % circeVersion,
      "io.circe" %% "circe-parser" % circeVersion
    ),
    Compile / mainClass := Some("io.smc.exporter.SmcExporter"),
    nativeImageOptions ++= Seq(
      "--no-fallback",
      s"-H:ResourceConfigurationFiles=${(Compile / resourceDirectory).value / "native-image-config" / "resources-config.json"}"
    )
  )
  .enablePlugins(NativeImagePlugin)
