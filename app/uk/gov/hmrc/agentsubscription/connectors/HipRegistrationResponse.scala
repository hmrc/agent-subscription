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

import play.api.libs.json.JsError
import play.api.libs.json.Format
import play.api.libs.json.Json
import play.api.libs.json.OFormat
import play.api.libs.json.JsSuccess
import play.api.libs.json.JsValue
import uk.gov.hmrc.agentmtdidentifiers.model.Arn

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
  object RegistrationResponse {
    def fromJson(json: JsValue): HipRegistrationResponse = {
      val address =
        (json \ "address").validate[DesBusinessAddress] match {
          case JsSuccess(value, _) => value
          case JsError(_) => throw InvalidBusinessAddressException
        }

      val isAnASAgent =
        (json \ "isAnASAgent").validate[Boolean] match {
          case JsSuccess(value, _) => value
          case JsError(_) => throw InvalidIsAnASAgentException
        }

      HipRegistrationResponse(
        isAnASAgent,
        (json \ "organisation" \ "organisationName").asOpt[String],
        (json \ "individual").asOpt[Individual],
        (json \ "agentReferenceNumber").asOpt[Arn],
        address,
        (json \ "agencyDetails" \ "agencyEmail")
          .asOpt[String]
          .orElse((json \ "contactDetails" \ "emailAddress").asOpt[String]),
        (json \ "contactDetails" \ "primaryPhoneNumber").asOpt[String],
        (json \ "safeId").asOpt[String]
      )
    }
  }
}
