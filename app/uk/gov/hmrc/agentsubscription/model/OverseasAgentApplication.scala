/*
 * Copyright 2022 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.gov.hmrc.agentsubscription.model
import play.api.libs.functional.syntax._
import play.api.libs.json.{ Json, Reads, _ }

case class OverseasBusinessAddress(
  addressLine1: String,
  addressLine2: String,
  addressLine3: Option[String],
  addressLine4: Option[String],
  countryCode: String)

case class OverseasAgencyAddress(
  addressLine1: String,
  addressLine2: String,
  addressLine3: Option[String],
  addressLine4: Option[String],
  countryCode: String)

case class TradingDetails(tradingName: String, tradingAddress: OverseasBusinessAddress)

case class OverseasContactDetails(businessTelephone: String, businessEmail: String)

case class OverseasAgencyDetails(
  agencyName: String,
  agencyEmail: String,
  agencyAddress: OverseasAgencyAddress)

object OverseasAgencyAddress {
  implicit val writes: OWrites[OverseasAgencyAddress] = Json.writes[OverseasAgencyAddress]
  implicit val reads: Reads[OverseasAgencyAddress] = (
    (__ \ "addressLine1").read[String](overseasAddressValidation) and
    (__ \ "addressLine2").read[String](overseasAddressValidation) and
    (__ \ "addressLine3").readNullable[String](overseasAddressValidation) and
    (__ \ "addressLine4").readNullable[String](overseasAddressValidation) and
    (__ \ "countryCode").read[String](overseasCountryCodeValidation))(OverseasAgencyAddress.apply _)
}

object OverseasBusinessAddress {

  implicit val writes: OWrites[OverseasBusinessAddress] = Json.writes[OverseasBusinessAddress]
  implicit val reads: Reads[OverseasBusinessAddress] = (
    (__ \ "addressLine1").read[String](overseasAddressValidation) and
    (__ \ "addressLine2").read[String](overseasAddressValidation) and
    (__ \ "addressLine3").readNullable[String](overseasAddressValidation) and
    (__ \ "addressLine4").readNullable[String](overseasAddressValidation) and
    (__ \ "countryCode").read[String](overseasCountryCodeValidation))(OverseasBusinessAddress.apply _)
}

object TradingDetails {
  implicit val writes: OWrites[TradingDetails] = Json.writes[TradingDetails]

  implicit val reads: Reads[TradingDetails] = (
    (__ \ "tradingName").read[String](overseasNameValidation) and
    (__ \ "tradingAddress").read[OverseasBusinessAddress])(TradingDetails.apply _)
}

object OverseasContactDetails {
  implicit val writes: OWrites[OverseasContactDetails] = Json.writes[OverseasContactDetails]

  implicit val reads: Reads[OverseasContactDetails] = (
    (__ \ "businessTelephone").read[String](overseasTelephoneNumberValidation) and
    (__ \ "businessEmail").read[String](overseasEmailValidation))(OverseasContactDetails.apply _)
}

object OverseasAgencyDetails {

  implicit val writes: OWrites[OverseasAgencyDetails] = Json.writes[OverseasAgencyDetails]

  implicit val reads: Reads[OverseasAgencyDetails] = (
    (__ \ "agencyName").read[String](overseasNameValidation) and
    (__ \ "agencyEmail").read[String](overseasEmailValidation) and
    (__ \ "agencyAddress").read[OverseasAgencyAddress])(OverseasAgencyDetails.apply _)
}
