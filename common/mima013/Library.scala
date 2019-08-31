package mima013

import play.api.libs.json.{Json, OFormat}

case class Library(
  groupId: String,
  artifactId: String,
  version: String,
  jarPath: String
)

object Library {
  implicit val format: OFormat[Library] = Json.format[Library]
}
