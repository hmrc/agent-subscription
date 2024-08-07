package uk.gov.hmrc.agentsubscription.model

import play.api.libs.json.{Format, JsResult, JsValue, Json}
import uk.gov.hmrc.agentsubscription.repository.EncryptionUtils._
import uk.gov.hmrc.crypto.json.JsonEncryption.stringEncrypter
import uk.gov.hmrc.crypto.{Decrypter, Encrypter}

case class BusinessAddress(
                            addressLine1: String,
                            addressLine2: Option[String],
                            addressLine3: Option[String] = None,
                            addressLine4: Option[String] = None,
                            postalCode: Option[String],
                            countryCode: String,
                            encrypted: Option[Boolean] = None
                          )
object BusinessAddress {
  def format(implicit crypto: Encrypter with Decrypter): Format[BusinessAddress] = {

    def reads(json: JsValue): JsResult[BusinessAddress] =
      for {
        isEncrypted <- (json \ "encrypted").validateOpt[Boolean]
        businessAddress = isEncrypted match {
          case Some(true) =>
            BusinessAddress(
              decrypt("addressLine1", json),
              decryptOpt("addressLine2", json),
              decryptOpt("addressLine3", json),
              decryptOpt("addressLine4", json),
              decryptOpt("postalCode", json),
              decrypt("countryCode", json),
              Some(true)
            )
          case _ =>
            BusinessAddress(
              (json \ "addressLine1").as[String],
              (json \ "addressLine2").asOpt[String],
              (json \ "addressLine3").asOpt[String],
              (json \ "addressLine4").asOpt[String],
              (json \ "postalCode").asOpt[String],
              (json \ "countryCode").as[String],
              Some(false)
            )
        }
      } yield businessAddress

    def writes(businessAddress: BusinessAddress): JsValue =
      Json.obj(
        "addressLine1" -> stringEncrypter.writes(businessAddress.addressLine1),
        "addressLine2" -> businessAddress.addressLine2.map(stringEncrypter.writes),
        "addressLine3" -> businessAddress.addressLine2.map(stringEncrypter.writes),
        "addressLine4" -> businessAddress.addressLine2.map(stringEncrypter.writes),
        "postalCode"   -> businessAddress.addressLine2.map(stringEncrypter.writes),
        "countryCode"  -> stringEncrypter.writes(businessAddress.countryCode),
        "encrypted"    -> businessAddress.encrypted
      )

    Format(reads(_), businessAddress => writes(businessAddress))
  }

}
