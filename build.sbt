import sbtrelease._
import sbtrelease.ReleaseStateTransformations._
import scala.collection.JavaConverters._
import java.lang.management.ManagementFactory

val javaVmArgs: List[String] = ManagementFactory.getRuntimeMXBean.getInputArguments.asScala.toList

val tagName = Def.setting {
  s"v${if (releaseUseGlobalVersion.value) (version in ThisBuild).value else version.value}"
}

val tagOrHash = Def.setting {
  if (isSnapshot.value) sys.process.Process("git rev-parse HEAD").lineStream_!.head
  else tagName.value
}

lazy val commonSettings = Def.settings(
  Global / onChangedBuildSource := ReloadOnSourceChanges,
  unmanagedResources in Compile += (baseDirectory in LocalRootProject).value / "LICENSE.txt",
  organization := "com.github.xuwei-k",
  publishTo := sonatypePublishTo.value,
  description := "lightbend/mima plugin for sbt 0.13",
  homepage := Some(url("https://github.com/xuwei-k/mima013")),
  licenses := Seq("MIT License" -> url("https://www.opensource.org/licenses/mit-license")),
  commands += Command.command("updateReadme")(UpdateReadme.updateReadmeTask),
  pomExtra := (
    <developers>
      <developer>
        <id>xuwei-k</id>
        <name>Kenji Yoshida</name>
        <url>https://github.com/xuwei-k</url>
      </developer>
    </developers>
    <scm>
      <url>git@github.com:xuwei-k/mima013.git</url>
      <connection>scm:git:git@github.com:xuwei-k/mima013.git</connection>
      <tag>{tagOrHash.value}</tag>
    </scm>
  ),
  releaseProcess := Seq[ReleaseStep](
    checkSnapshotDependencies,
    inquireVersions,
    runClean,
    releaseStepCommandAndRemaining("scripted"),
    setReleaseVersion,
    commitReleaseVersion,
    UpdateReadme.updateReadmeProcess,
    tagRelease,
    releaseStepCommandAndRemaining("publishSigned"),
    setNextVersion,
    commitNextVersion,
    releaseStepCommand("sonatypeReleaseAll"),
    pushChanges,
  ),
  scalacOptions ++= Seq(
    "-deprecation",
    "-encoding",
    "UTF-8",
    "-feature",
    "-language:existentials",
    "-language:higherKinds",
    "-language:implicitConversions",
    "-unchecked",
    "-Ywarn-dead-code",
    "-Ywarn-numeric-widen",
    "-Ywarn-value-discard",
  ),
  scalacOptions ++= {
    CrossVersion.partialVersion(scalaVersion.value) match {
      case Some((2, v)) if v >= 12 =>
        Seq(
          "-Xlint:infer-any",
          "-Xlint:missing-interpolator",
          "-Xlint:nullary-override",
          "-Xlint:nullary-unit",
          "-Xlint:private-shadow",
          "-Xlint:stars-align",
          "-Xlint:type-parameter-shadow",
        )
      case _ =>
        Nil
    }
  },
  scalacOptions ++= {
    CrossVersion.partialVersion(scalaVersion.value) match {
      case Some((2, v)) if v <= 12 =>
        Seq(
          "-Yno-adapted-args",
          "-Xfuture",
        )
      case _ =>
        Nil
    }
  },
  libraryDependencies ++= Seq(
    "com.typesafe.play" %% "play-json" % "2.6.13",
  )
)

lazy val sharedSource = Def.settings(
  unmanagedSourceDirectories in Compile += file("common")
)

commonSettings

skip in publish := true
publishArtifact := false
publish := {}
publishLocal := {}
PgpKeys.publishSigned := {}
PgpKeys.publishLocalSigned := {}

val sbt013 = "0.13.18"

lazy val plugin = project
  .in(file("plugin"))
  .settings(
    commonSettings,
    sharedSource,
    addSbtPlugin("com.dwijnand" % "sbt-compat" % "1.2.6"),
    name := "mima013-plugin",
    scriptedBufferLog := false,
    scriptedLaunchOpts ++= javaVmArgs.filter(
      a => Seq("-Xmx", "-Xms", "-XX", "-Dsbt.log.noformat").exists(a.startsWith)
    ),
    scriptedLaunchOpts ++= javaVmArgs.filter(
      a => Seq("scala.ext.dirs").exists(a.contains)
    ),
    scriptedLaunchOpts += ("-Dplugin.version=" + version.value),
    sbtVersion in pluginCrossBuild := sbt013,
    buildInfoPackage := "mima013",
    buildInfoObject := "Mima013BuildInfo",
    buildInfoKeys := Seq(
      version
    ),
    crossSbtVersions := Seq(sbt013),
    scripted := scripted.dependsOn(publishLocal in core).evaluated,
    scalaVersion := "2.10.7",
  )
  .enablePlugins(SbtPlugin, BuildInfoPlugin)

val unusedWarnings = Seq("-Ywarn-unused:imports")

lazy val core = project
  .in(file("core"))
  .settings(
    commonSettings,
    sharedSource,
    scalacOptions ++= unusedWarnings,
    Seq(Compile, Test).flatMap(c => scalacOptions in (c, console) --= unusedWarnings),
    name := "mima013-core",
    scalaVersion := "2.12.8",
    resolvers += Resolver.sbtPluginRepo("releases"), // for sbt-mima-plugin
    addSbtPlugin("com.typesafe" % "sbt-mima-plugin" % "0.6.0"),
    libraryDependencies ++= Seq(
      "org.scala-sbt" % "sbt" % "1.2.8",
    )
  )
