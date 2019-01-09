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
import play.api.libs.json.Reads.email
import play.api.libs.json.{ Json, Reads, Writes, _ }

case class BusinessDetails(tradingName: String, businessAddress: OverseasAddress)
case class BusinessContactDetails(businessTelephone: String, businessEmail: String)

case class AgencyDetails(
  agencyName: String,
  agencyEmail: String,
  telephoneNumber: String,
  agencyAddress: OverseasAddress)

case class OverseasAddress(
  addressLine1: String,
  addressLine2: String,
  addressLine3: Option[String],
  addressLine4: Option[String],
  countryCode: String)

object BusinessDetails {
  implicit val format = Json.format[BusinessDetails]
}

object BusinessContactDetails {
  implicit val format = Json.format[BusinessContactDetails]
}

object AgencyDetails {
  implicit val writes = Json.writes[AgencyDetails]

  implicit val reads: Reads[AgencyDetails] = (
    (__ \ "agencyName").read[String](nameValidation) and
    (__ \ "agencyEmail").read[String](email) and
    (__ \ "telephoneNumber").read[String](telephoneNumberValidation) and
    (__ \ "agencyAddress").read[OverseasAddress])(AgencyDetails.apply _)
}

object OverseasAddress {
  implicit val writes: Writes[OverseasAddress] = Json.writes[OverseasAddress]
  implicit val reads: Reads[OverseasAddress] = (
    (__ \ "addressLine1").read[String](addressValidation) and
    (__ \ "addressLine2").read[String](addressValidation) and
    (__ \ "addressLine3").readNullable[String](addressValidation) and
    (__ \ "addressLine4").readNullable[String](addressValidation) and
    (__ \ "countryCode").read[String](overseasCountryCodeValidation))(OverseasAddress.apply _)
}

