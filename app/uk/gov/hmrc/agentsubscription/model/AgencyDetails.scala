/*
 * Copyright 2019 HM Revenue & Customs
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

case class BusinessDetails(tradingName: String, businessAddress: OverseasBusinessAddress)
case class BusinessContactDetails(businessTelephone: String, businessEmail: String)

case class AgencyDetails(
  agencyName: String,
  agencyEmail: String,
  telephoneNumber: String,
  agencyAddress: OverseasAgencyAddress)

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

object OverseasAgencyAddress {
  implicit val writes = Json.writes[OverseasAgencyAddress]
  implicit val reads: Reads[OverseasAgencyAddress] = (
    (__ \ "addressLine1").read[String](overseasAddressValidation) and
    (__ \ "addressLine2").read[String](overseasAddressValidation) and
    (__ \ "addressLine3").readNullable[String](overseasAddressValidation) and
    (__ \ "addressLine4").readNullable[String](overseasAddressValidation) and
    (__ \ "countryCode").read[String](overseasCountryCodeValidation))(OverseasAgencyAddress.apply _)
}

object OverseasBusinessAddress {
  implicit val writes = Json.writes[OverseasBusinessAddress]
  implicit val reads: Reads[OverseasBusinessAddress] = (
    (__ \ "addressLine1").read[String](overseasAddressValidation) and
    (__ \ "addressLine2").read[String](overseasAddressValidation) and
    (__ \ "addressLine3").readNullable[String](overseasAddressValidation) and
    (__ \ "addressLine4").readNullable[String](overseasAddressValidation) and
    (__ \ "countryCode").read[String](overseasCountryCodeValidation))(OverseasBusinessAddress.apply _)
}

object BusinessDetails {
  implicit val writes = Json.writes[BusinessDetails]

  implicit val reads: Reads[BusinessDetails] = (
    (__ \ "tradingName").read[String](overseasNameValidation) and
    (__ \ "businessAddress").read[OverseasBusinessAddress])(BusinessDetails.apply _)
}

object BusinessContactDetails {
  implicit val writes = Json.writes[BusinessContactDetails]

  implicit val reads: Reads[BusinessContactDetails] = (
    (__ \ "businessTelephone").read[String](overseasTelephoneNumberValidation) and
    (__ \ "businessEmail").read[String](overseasEmailValidation))(BusinessContactDetails.apply _)
}

object AgencyDetails {
  implicit val writes = Json.writes[AgencyDetails]

  implicit val reads: Reads[AgencyDetails] = (
    (__ \ "agencyName").read[String](overseasNameValidation) and
    (__ \ "agencyEmail").read[String](overseasEmailValidation) and
    (__ \ "telephoneNumber").read[String](overseasTelephoneNumberValidation) and
    (__ \ "agencyAddress").read[OverseasAgencyAddress])(AgencyDetails.apply _)
}
