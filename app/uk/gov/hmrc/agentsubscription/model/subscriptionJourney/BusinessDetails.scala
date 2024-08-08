package uk.gov.hmrc.agentsubscription.model.subscriptionJourney

import play.api.libs.json.{Format, JsResult, JsValue, Json}
import uk.gov.hmrc.agentsubscription.model.DateOfBirth
import uk.gov.hmrc.agentsubscription.repository.EncryptionUtils.{maybeDecrypt, maybeDecryptOpt}
import uk.gov.hmrc.crypto.{Decrypter, Encrypter}
import uk.gov.hmrc.crypto.json.JsonEncryption.stringEncrypter


/** Information about the agent's business. They must always provide a business type, UTR and postcode. But other data
 * points are only required for some business types and if certain conditions are NOT met e.g. if they provide a NINO,
 * they must provide date of birth if they are registered for vat, they must provide vat details The record is created
 * once we have the minimum business details
 */
case class BusinessDetails(
                            businessType: BusinessType,
                            utr: String, // CT or SA
                            postcode: String,
                            registration: Option[Registration] = None,
                            nino: Option[String] = None,
                            companyRegistrationNumber: Option[CompanyRegistrationNumber] = None,
                            dateOfBirth: Option[DateOfBirth] = None, // if NINO required
                            registeredForVat: Option[Boolean] = None,
                            vatDetails: Option[VatDetails] = None,
                            encrypted: Option[Boolean] = None
                          ) // if registered for VAT

object BusinessDetails {

  def format(implicit crypto: Encrypter with Decrypter): Format[BusinessDetails] = {

    def reads(json: JsValue): JsResult[BusinessDetails] =
      for {
        isEncrypted <- (json \ "encrypted").validateOpt[Boolean]
        result = BusinessDetails(
          businessType = (json \ "businessType").as[BusinessType],
          utr = maybeDecrypt("utr", isEncrypted, json),
          postcode = maybeDecrypt("postcode", isEncrypted, json),
          registration = (json \ "registration").asOpt[Registration](Registration.format(crypto)),
          nino = maybeDecryptOpt("nino", isEncrypted, json),
          (json \ "companyRegistrationNumber").asOpt[CompanyRegistrationNumber],
          (json \ "dateOfBirth").asOpt[DateOfBirth],
          (json \ "registeredForVat").asOpt[Boolean],
          (json \ "vatDetails").asOpt[VatDetails]
        )
      } yield result

    def writes(businessDetails: BusinessDetails): JsValue =
      Json.obj(
        "businessType" -> businessDetails.businessType,
        "utr" -> stringEncrypter.writes(businessDetails.utr),
        "postcode" -> stringEncrypter.writes(businessDetails.postcode),
        "registration" -> businessDetails.registration.map(Registration.format.writes),
        "nino" -> businessDetails.nino.map(stringEncrypter.writes),
        "companyRegistrationNumber" -> businessDetails.companyRegistrationNumber,
        "dateOfBirth"  -> businessDetails.dateOfBirth,
        "registeredForVat" -> businessDetails.registeredForVat,
        "vatDetails" -> businessDetails.vatDetails,
        "encrypted" -> true
      )

    Format(reads, businessDetails => writes(businessDetails))
  }
}

