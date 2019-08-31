package mima013

import java.io.File
import play.api.libs.json.{Json, OFormat}
import LogLevelInstance._

case class Options(
  currentPath: String,
  filterDir: String,
  direction: MimaCheckDirection,
  classpath: List[String],
  failOnProblem: Boolean,
  logLevel: sbt.Level.Value,
  backwardFilterRegex: String,
  forwardFilterRegex: String
) {
  def filterDirectory: File = new File(filterDir)
}

object Options {
  implicit val format: OFormat[Options] = Json.format[Options]
}
