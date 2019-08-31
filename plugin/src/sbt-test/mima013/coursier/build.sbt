val Scala212 = "2.12.8"

lazy val commonSettings = Def.settings(
  organization := "com.github.xuwei-k",
  scalaVersion := Scala212,
  crossScalaVersions := Seq(Scala212, "2.13.0", "2.11.12")
)

lazy val v1 = project
  .settings(
    commonSettings,
    name := "example1",
    version := "0.1.0-SNAPSHOT"
  )

lazy val v2 = project
  .settings(
    commonSettings,
    version := "0.2.0-SNAPSHOT",
    mimaPreviousArtifacts := Set(
      organization.value %% (name in v1).value % (version in v1).value
    )
  )

commonSettings
