/*
 * Copyright 2018 HM Revenue & Customs
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
import java.util.UUID

import play.api.libs.functional.syntax._
import play.api.libs.json.{ Json, Reads, Writes, _ }

case class OverseasSubscriptionRequest(agencyName: String, agencyEmail: String, telephoneNumber: String, agencyAddress: OverseasAddress) {
  def toRegistrationRequest: OverseasRegistrationRequest = OverseasRegistrationRequest(
    regime = "AGSV",
    acknowledgementReference = UUID.randomUUID.toString.replaceAll("-", ""),
    isAnAgent = false,
    isAGroup = false,
    Organisation(agencyName),
    agencyAddress)
}

case class OverseasAddress(
  addressLine1: String,
  addressLine2: String,
  addressLine3: Option[String],
  addressLine4: Option[String],
  countryCode: String)

object OverseasSubscriptionRequest {
  implicit val OSubRequestFormat = Json.format[OverseasSubscriptionRequest]
}

object OverseasAddress {
  implicit val writes: Writes[OverseasAddress] = Json.writes[OverseasAddress]
  implicit val reads: Reads[OverseasAddress] = (
    (__ \ "addressLine1").read[String] and
    (__ \ "addressLine2").read[String] and
    (__ \ "addressLine3").readNullable[String] and
    (__ \ "addressLine4").readNullable[String] and
    (__ \ "countryCode").read[String](overseasCountryCodeValidation))(OverseasAddress.apply _)
}
