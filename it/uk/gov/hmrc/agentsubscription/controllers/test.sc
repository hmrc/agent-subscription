import play.api.data.validation.ValidationError
import play.api.libs.functional.syntax._
import play.api.libs.json.Reads._
import play.api.libs.json._
import uk.gov.hmrc.agentsubscription.model._

val telephoneNumberValidation = {
  filterNot[String](ValidationError("error.whitespace.or.empty"))(_.replaceAll("\\s", "").isEmpty) andKeep
    filter[String](ValidationError("error.telephone.invalid"))(_.matches(telephoneRegex))
}


case class Agency(
                   name: String,
                   telephone: Option[String],
                   email: String)

object Agency {
  implicit val writes: Writes[Agency] = Json.writes[Agency]
  implicit val reads: Reads[Agency] = (
    (__ \ "name").read[String] and
      (__ \ "telephone").readNullable[String](telephoneNumberValidation) and
      (__ \ "email").read[String](email))(Agency.apply _)
}


val json1 =
  """
    |{
    |    "name": "My Agency",
    |    "email": "agency@example.com"
    |  }
  """.stripMargin


Json.parse(json1).validate[Agency]