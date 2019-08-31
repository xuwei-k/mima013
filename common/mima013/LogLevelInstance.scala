package mima013

import sbt._
import play.api.libs.json.{Format, Reads, Writes}

object LogLevelInstance {
  implicit val logLevelFormat: Format[Level.Value] =
    Format(
      Reads.enumNameReads(Level),
      Writes.enumNameWrites[Level.type]
    )
}
