/*
 * Copyright 2026 HM Revenue & Customs
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

package uk.gov.hmrc.agentsubscription.connectors

import play.api.libs.json.Format
import play.api.libs.json.JsPath
import play.api.libs.json.Json
import play.api.libs.json.OFormat
import play.api.libs.json.Reads
import uk.gov.hmrc.agentmtdidentifiers.model.Arn
import play.api.libs.functional.syntax._

case class Individual(
  firstName: String,
  lastName: String
)

object Individual {
  implicit val formats: Format[Individual] = Json.format[Individual]
}

case class HipRegistrationBusinessAddress(
  addressLine1: String,
  addressLine2: Option[String],
  addressLine3: Option[String] = None,
  addressLine4: Option[String] = None,
  postalCode: Option[String],
  countryCode: String
)

object HipRegistrationBusinessAddress {
  implicit val format: OFormat[HipRegistrationBusinessAddress] = Json.format
}

case class HipRegistrationResponse(
  isAnASAgent: Boolean,
  organisationName: Option[String],
  individual: Option[Individual],
  agentReferenceNumber: Option[Arn],
  address: DesBusinessAddress,
  emailAddress: Option[String],
  primaryPhoneNumber: Option[String],
  safeId: Option[String]
)

object HipRegistrationResponse {

  private val innerJson = (JsPath \ "success")

  implicit val reads: Reads[HipRegistrationResponse] =
    (
      (innerJson \ "isAnASAgent").read[Boolean] and
        (innerJson \ "organisation" \ "organisationName").readNullable[String] and
        (innerJson \ "individual").readNullable[Individual] and
        (innerJson \ "agentReferenceNumber").readNullable[Arn] and
        (innerJson \ "address").read[DesBusinessAddress] and
        (innerJson \ "emailAddress").readNullable[String] and
        (innerJson \ "primaryPhoneNumber").readNullable[String] and
        (innerJson \ "safeId").readNullable[String]
    )(HipRegistrationResponse.apply _)

}
