package mima013

import java.io.File
import play.api.libs.json.Json
import sbt._
import sbt.Keys._
import sbt.librarymanagement.{UpdateConfiguration, UpdateConfigurationOps}
import sbt.librarymanagement.ivy.IvyDependencyResolution
import scala.util.matching.Regex

object Mima013Plugin extends AutoPlugin {

  override def requires: Plugins = sbt.plugins.JvmPlugin
  override def trigger = allRequirements

  case object DummyValue

  object autoImport {
    val mimaPreviousArtifacts =
      settingKey[Set[ModuleID]]("Previous released artifacts used to test binary compatibility.")
    val mimaFiltersDirectory =
      settingKey[File]("Directory containing issue filters.")
    val mimaReportBinaryIssues =
      taskKey[DummyValue.type]("Logs all binary incompatibilities to the sbt console/logs.")
    val mimaCheckDirection =
      taskKey[MimaCheckDirection](
        s"Compatibility checking direction; default is ${MimaCheckDirection.Backward}"
      )
    val mimaFailOnProblem =
      settingKey[Boolean]("if true, fail the build on binary incompatibility detection.")
    val mimaFailOnNoPrevious =
      settingKey[Boolean]("if true, fail the build if no previous artifacts are set.")
    val mimaBackwardFilterRegex =
      settingKey[Regex]("")
    val mimaForwardFilterRegex =
      settingKey[Regex]("")
    val mimaParam =
      taskKey[Param](s"parameter for ${mimaReportBinaryIssues.key.label}")
  }

  import autoImport._

  override def extraProjects: Seq[Project] = Seq(extraProject)
  lazy val extraProject: Project =
    Project("mima013", file("mima013"))
      .settings(
        // disable publish tasks
        publishArtifact := false,
        publish := {},
        publishLocal := {},
        skip in publish := true,
        // disable sbt-pgp publish tasks as well
        // https://github.com/sbt/sbt-pgp/blob/v1.1.1/pgp-plugin/src/main/scala/com/typesafe/sbt/pgp/PgpKeys.scala#L41-L42
        TaskKey[Unit]("publishSigned") := {},
        TaskKey[Unit]("publishLocalSigned") := {},
        fork in run := true,
        // for sbt-mima-plugin
        resolvers += Resolver.sbtPluginRepo("releases"),
        // suppress scala binary version warnings
        evictionWarningOptions in evicted := EvictionWarningOptions.empty
          .withGuessCompatible(_ => true),
        evictionWarningOptions in update := EvictionWarningOptions.empty
          .withGuessCompatible(_ => true),
        ivyScala ~= (_ map (_ copy (overrideScalaVersion = false))),
        // use scala 2.12. ignore scalaVersion setting
        autoScalaLibrary := false,
        crossPaths := false,
        // Don't use %%
        libraryDependencies += "com.github.xuwei-k" % "mima013-core_2.12" % mima013.Mima013BuildInfo.version
      )
      .disablePlugins(Mima013Plugin)

  override def projectSettings: Seq[Def.Setting[_]] = Def.settings(
    mimaPreviousArtifacts := Set.empty,
    // https://github.com/lightbend/mima/blob/0.6.0/sbtplugin/src/main/scala/com/typesafe/tools/mima/plugin/MimaPlugin.scala#L60
    mimaFiltersDirectory := (sourceDirectory in Compile).value / "mima-filters",
    // https://github.com/lightbend/mima/blob/0.6.0/sbtplugin/src/main/scala/com/typesafe/tools/mima/plugin/MimaPlugin.scala#L20
    mimaCheckDirection := MimaCheckDirection.Backward,
    // https://github.com/lightbend/mima/blob/0.6.0/sbtplugin/src/main/scala/com/typesafe/tools/mima/plugin/MimaPlugin.scala#L57
    fullClasspath in mimaReportBinaryIssues := (fullClasspath in Compile).value,
    // default is "true" in mima 0.6.0. but set "false" for mima 0.3.0 compatibility
    mimaFailOnNoPrevious := false,
    // https://github.com/lightbend/mima/blob/0.6.0/sbtplugin/src/main/scala/com/typesafe/tools/mima/plugin/MimaPlugin.scala#L18
    mimaFailOnProblem := true,
    // https://github.com/lightbend/mima/blob/0.6.0/sbtplugin/src/main/scala/com/typesafe/tools/mima/plugin/MimaPlugin.scala#L58-L59
    mimaBackwardFilterRegex := "\\.(?:backward[s]?|both)\\.excludes".r,
    mimaForwardFilterRegex := "\\.(?:forward[s]?|both)\\.excludes".r,
    logLevel in mimaReportBinaryIssues := Level.Info,
    mimaParam := {
      val options = Options(
        currentPath = (classDirectory in Compile).value.getCanonicalPath,
        filterDir = mimaFiltersDirectory.value.getCanonicalPath,
        direction = mimaCheckDirection.value,
        classpath =
          (fullClasspath in mimaReportBinaryIssues).value.map(_.data.getCanonicalPath).toList,
        failOnProblem = mimaFailOnProblem.value,
        logLevel = (logLevel in mimaReportBinaryIssues).value,
        backwardFilterRegex = mimaBackwardFilterRegex.value.toString,
        forwardFilterRegex = mimaForwardFilterRegex.value.toString
      )

      val previous = mimaPreviousArtifacts.value.toList.map {
        artifact =>
          val name = artifact.name + {
            artifact.crossVersion match {
              case CrossVersion.Disabled =>
                ""
              case _: CrossVersion.Binary =>
                "_" + scalaBinaryVersion.value
              case _: CrossVersion.Full =>
                "_" + scalaVersion.value
            }
          }
          Library(
            groupId = artifact.organization,
            artifactId = name,
            version = artifact.revision,
            jarPath = getPreviousArtifact(
              m = artifact.copy(name = name),
              ivy = ivySbt.value,
              s = streams.value
            ).getCanonicalPath
          )
      }

      Param(
        previousVersions = previous,
        options = options
      )
    },
    mimaReportBinaryIssues := {
      Def.taskDyn {
        val param = mimaParam.value
        if (param.previousVersions.isEmpty) {
          val message = mimaPreviousArtifacts.key.label + " not set"
          if (mimaFailOnNoPrevious.value) {
            streams.value.log.error(message)
            sys.error(message)
          } else {
            streams.value.log.info(message)
            Def.task(DummyValue)
          }
        } else {
          streams.value.log.debug(Json.prettyPrint(param.toJson))
          IO.withTemporaryFile("param", ".json") { tempArgFile =>
            Def.taskDyn {
              IO.write(tempArgFile, param.toJson.toString)
              streams.value.log.debug(tempArgFile.getCanonicalPath)
              (runMain in (extraProject, Compile))
                .toTask(" mima013.Mima013 " + tempArgFile.getCanonicalPath)
                .map(_ => DummyValue)
            }
          }
        }
      }.value
    }
  )

  // Derived from
  // https://github.com/lightbend/mima/blob/0.6.0/sbtplugin/src/main/scala/com/typesafe/tools/mima/plugin/SbtMima.scala#L85-L104
  // execute in sbt-plugin. not mima013-core. because serialize `IvySbt` is so difficult
  def getPreviousArtifact(m: ModuleID, ivy: IvySbt, s: TaskStreams): File = {
    val depRes = IvyDependencyResolution(ivy.configuration)
    val module = depRes.wrapDependencyInModule(m)
    val reportEither = depRes.update(
      module,
      UpdateConfiguration() withLogging UpdateLogging.DownloadOnly,
      UnresolvedWarningConfiguration(),
      s.log
    )
    val report = reportEither.fold(x => throw x.resolveException, x => x)
    val optFile = (for {
      config <- report.configurations
      module <- config.modules
      (artifact, file) <- module.artifacts
      if artifact.name == m.name
      if artifact.classifier.isEmpty
    } yield file).headOption
    optFile.getOrElse(sys.error(s"Could not resolve previous ABI: $m"))
  }

}
