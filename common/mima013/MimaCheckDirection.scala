package mima013

import play.api.libs.json._
import play.api.libs.functional.syntax._

sealed abstract class MimaCheckDirection(val value: String) extends Product with Serializable

object MimaCheckDirection {
  case object Backward extends MimaCheckDirection("backward")
  case object Forward extends MimaCheckDirection("forward")
  case object Both extends MimaCheckDirection("both")

  private[this] val values = Set(Backward, Forward, Both)

  implicit val format: Format[MimaCheckDirection] =
    Format(
      Reads { json =>
        implicitly[Reads[String]].reads(json).flatMap { value =>
          values.find(_.value == value) match {
            case Some(x) =>
              JsSuccess(x)
            case None =>
              JsError(s"expect ${values.mkString(",")} but got $value")
          }
        }
      },
      implicitly[Writes[String]].contramap(_.value)
    )
}
