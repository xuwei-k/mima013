package mima013

import java.io.File
import com.typesafe.tools.mima.core.ProblemFilter
import com.typesafe.tools.mima.core.util.log.Logging
import com.typesafe.tools.mima.plugin.SbtMima
import play.api.libs.json.Json
import sbt.internal.util.{AttributeMap, Attributed}
import sbt.librarymanagement.ModuleID
import sbt.util.{Level, Logger}
import scala.io.Source

object Mima013 {

  private[this] def logger(level: Level.Value): Logging = new Logging {
    override def debugLog(str: String): Unit = {
      if (level == Level.Debug) {
        println(str)
      }
    }
    override def error(str: String): Unit =
      Console.err.println(str)
    override def info(str: String): Unit = {
      level match {
        case Level.Debug | Level.Info =>
          println(str)
        case Level.Warn | Level.Error =>
      }
    }
    override def warn(str: String): Unit = {
      level match {
        case Level.Debug | Level.Info | Level.Warn =>
          Console.err.println(str)
        case Level.Error =>
      }
    }
  }

  def main(args: Array[String]): Unit = {
    args match {
      case Array(fileName) =>
        val str = Source.fromFile(new File(fileName)).getLines().mkString("\n")

        val param = Json.parse(str).validate[Param].asEither match {
          case Left(err) =>
            throw new IllegalArgumentException(err.toString)
          case Right(x) =>
            val log = sbtLogger(x.options.logLevel)
            log.debug(Json.prettyPrint(x.toJson))
            x
        }

        param.previousVersions.foreach { lib =>
          runMima(
            previous = lib,
            options = param.options
          )
        }
      case other =>
        throw new IllegalArgumentException(
          s"invalid args ${other.length} ${other.mkString(", ")}. please specify fileName"
        )
    }
  }

  def sbtLogger(l: Level.Value): Logger = new Logger {
    override def trace(t: => Throwable): Unit = {
      t.printStackTrace()
    }
    override def success(message: => String): Unit =
      println(message)
    override def log(level: Level.Value, message: => String): Unit = {
      level match {
        case Level.Debug =>
          if (l == Level.Debug) {
            println(message)
          }
        case Level.Info =>
          l match {
            case Level.Debug | Level.Info =>
              println(message)
            case Level.Warn | Level.Error =>
          }
        case Level.Warn =>
          l match {
            case Level.Debug | Level.Info | Level.Warn =>
              println(message)
            case Level.Error =>
          }
        case Level.Error =>
          Console.err.println(message)
      }
    }
  }

  def runMima(previous: Library, options: Options): Unit = {
    val log = logger(options.logLevel)
    val (backProblems, forwardProblems) = SbtMima.runMima(
      prev = new File(previous.jarPath),
      curr = new File(options.currentPath),
      cp = options.classpath.map(new File(_)).map(Attributed(_)(AttributeMap.empty)),
      dir = options.direction.value,
      log = log
    )

    def filter(regex: String): Map[String, Seq[ProblemFilter]] = {
      if (options.filterDirectory.isDirectory) {
        val x = SbtMima.loadMimaIgnoredProblems(
          options.filterDirectory,
          regex.r,
          sbtLogger(options.logLevel)
        )
        log.debugLog(x.toString)
        x
      } else {
        if (options.filterDirectory.exists()) {
          log.error(s"${options.filterDir} is not directory!?")
        }
        Map.empty
      }
    }

    SbtMima.reportModuleErrors(
      module = ModuleID(
        organization = previous.groupId,
        name = previous.artifactId,
        revision = previous.version
      ),
      backward = backProblems,
      forward = forwardProblems,
      failOnProblem = options.failOnProblem,
      filters = Nil,
      backwardFilters = filter(options.backwardFilterRegex),
      forwardFilters = filter(options.forwardFilterRegex),
      log = logger(options.logLevel),
      projectName = previous.artifactId
    )
  }
}
