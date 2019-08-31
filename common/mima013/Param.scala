package mima013

import play.api.libs.json.{JsValue, Json, OFormat}

case class Param(
  previousVersions: List[Library],
  options: Options
) {
  def toJson: JsValue = Json.toJson(this)
}

object Param {
  implicit val format: OFormat[Param] = Json.format[Param]
}
