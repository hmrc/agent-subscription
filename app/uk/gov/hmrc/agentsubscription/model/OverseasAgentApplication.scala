/*
 * Copyright 2020 HM Revenue & Customs
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

sealed trait OverseasAddress {
  def addressLine1: String
  def addressLine2: String
  def addressLine3: Option[String]
  def addressLine4: Option[String]
  def countryCode: String
}

final case class UkAddressForOverseas(
  addressLine1: String,
  addressLine2: String,
  addressLine3: Option[String],
  addressLine4: Option[String],
  postalCode: String,
  countryCode: String) extends OverseasAddress

final case class OverseasBusinessAddress(
  addressLine1: String,
  addressLine2: String,
  addressLine3: Option[String],
  addressLine4: Option[String],
  countryCode: String) extends OverseasAddress

final case class OverseasAgencyAddress(
  addressLine1: String,
  addressLine2: String,
  addressLine3: Option[String],
  addressLine4: Option[String],
  countryCode: String)

case class TradingDetails(tradingName: String, tradingAddress: OverseasBusinessAddress)

case class TradingDetailsForUkAgentOverseas(tradingName: String, tradingAddress: OverseasAddress)

case class OverseasContactDetails(businessTelephone: String, businessEmail: String)

case class OverseasAgencyDetails(
  agencyName: String,
  agencyEmail: String,
  agencyAddress: OverseasAgencyAddress)

case class OverseasAgencyDetailsForMaybeUkAgent(
  agencyName: String,
  agencyEmail: String,
  agencyAddress: OverseasAddress)

object OverseasAddress {

  def maybeUkAddress(address: OverseasAddress): OverseasAddress = address.countryCode match {
    case "GB" => UkAddressForOverseas(
      addressLine1 = address.addressLine1,
      addressLine2 = address.addressLine2,
      addressLine3 = address.addressLine3,
      addressLine4 = None,
      postalCode = address.addressLine4.getOrElse(throw new RuntimeException("line 4 of the address form should be defined for GB countryCode")),
      countryCode = "GB")
    case _ => OverseasBusinessAddress(
      addressLine1 = address.addressLine1,
      addressLine2 = address.addressLine2,
      addressLine3 = address.addressLine3,
      addressLine4 = address.addressLine4,
      countryCode = address.countryCode)
  }

  implicit val overseasAddressWrites: Writes[OverseasAddress] = new Writes[OverseasAddress] {
    def writes(a: OverseasAddress): JsValue = a match {
      case uk: UkAddressForOverseas => Json.toJson(uk)(Json.writes[UkAddressForOverseas])
      case os: OverseasBusinessAddress => Json.toJson(os)(Json.writes[OverseasBusinessAddress])
    }
  }

  implicit val overseasAddressReads: Reads[OverseasAddress] = (
    (__ \ "addressLine1").read[String](overseasAddressValidation) and
    (__ \ "addressLine2").read[String](overseasAddressValidation) and
    (__ \ "addressLine3").readNullable[String](overseasAddressValidation) and
    (__ \ "addressLine4").readNullable[String](overseasAddressValidation) and
    (__ \ "postalCode").read[String](ukAddressForOverseasPostalCodeValidation) and
    (__ \ "countryCode").read[String](ukAddressForOverseasCountryCodeValidation))(UkAddressForOverseas.apply _)
    .map(x => x: OverseasAddress) orElse (

      (__ \ "addressLine1").lazyRead[String](overseasAddressValidation) and
      (__ \ "addressLine2").lazyRead[String](overseasAddressValidation) and
      (__ \ "addressLine3").lazyReadNullable[String](overseasAddressValidation) and
      (__ \ "addressLine4").lazyReadNullable[String](overseasAddressValidation) and
      (__ \ "countryCode").lazyRead[String](overseasCountryCodeValidation))(OverseasBusinessAddress.apply _).map(x => x: OverseasAddress)
}

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

  def fromOverseasAgencyAddress(osAddress: OverseasAgencyAddress): OverseasBusinessAddress = {
    OverseasBusinessAddress(
      addressLine1 = osAddress.addressLine1,
      addressLine2 = osAddress.addressLine2,
      addressLine3 = osAddress.addressLine3,
      addressLine4 = osAddress.addressLine4,
      countryCode = osAddress.countryCode)
  }

  implicit val writes: OWrites[OverseasBusinessAddress] = Json.writes[OverseasBusinessAddress]
  implicit val reads: Reads[OverseasBusinessAddress] = (
    (__ \ "addressLine1").read[String](overseasAddressValidation) and
    (__ \ "addressLine2").read[String](overseasAddressValidation) and
    (__ \ "addressLine3").readNullable[String](overseasAddressValidation) and
    (__ \ "addressLine4").readNullable[String](overseasAddressValidation) and
    (__ \ "countryCode").read[String](overseasCountryCodeValidation))(OverseasBusinessAddress.apply _)
}

object UkAddressForOverseas {

  implicit val writes: OWrites[UkAddressForOverseas] = Json.writes[UkAddressForOverseas]
  implicit val reads: Reads[UkAddressForOverseas] = (
    (__ \ "addressLine1").read[String](overseasAddressValidation) and
    (__ \ "addressLine2").read[String](overseasAddressValidation) and
    (__ \ "addressLine3").readNullable[String](overseasAddressValidation) and
    (__ \ "addressLine4").readNullable[String](overseasAddressValidation) and
    (__ \ "postalCode").read[String](ukAddressForOverseasPostalCodeValidation) and
    (__ \ "countryCode").read[String](ukAddressForOverseasCountryCodeValidation))(UkAddressForOverseas.apply _)
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

object OverseasAgencyDetailsForMaybeUkAgent {
  implicit val writes: OWrites[OverseasAgencyDetailsForMaybeUkAgent] = Json.writes[OverseasAgencyDetailsForMaybeUkAgent]

  implicit val reads: Reads[OverseasAgencyDetailsForMaybeUkAgent] = (
    (__ \ "agencyName").read[String](overseasNameValidation) and
    (__ \ "agencyEmail").read[String](overseasEmailValidation) and
    (__ \ "agencyAddress").read[OverseasBusinessAddress])(OverseasAgencyDetailsForMaybeUkAgent.apply _)
}
