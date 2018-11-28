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

import play.api.libs.functional.syntax._
import play.api.libs.json.Reads._
import play.api.libs.json._
import uk.gov.hmrc.agentmtdidentifiers.model.{ Utr, Arn }

object Address {
  implicit val writes: Writes[Address] = Json.writes[Address]
  implicit val reads: Reads[Address] = (
    (__ \ "addressLine1").read[String](addressValidation) and
    (__ \ "addressLine2").readNullable[String](addressValidation) and
    (__ \ "addressLine3").readNullable[String](addressValidation) and
    (__ \ "addressLine4").readNullable[String](addressValidation) and
    (__ \ "postcode").read[String](postcodeValidation) and
    (__ \ "countryCode").read[String])(Address.apply _)
}

object Agency {
  implicit val writes: Writes[Agency] = Json.writes[Agency]
  implicit val reads: Reads[Agency] = (
    (__ \ "name").read[String](nameValidation) and
    (__ \ "address").read[Address] and
    (__ \ "telephone").readNullable[String](telephoneNumberValidation) and
    (__ \ "email").read[String](email))(Agency.apply _)
}

object KnownFacts {
  implicit val writes = Json.writes[KnownFacts]
  implicit val reads: Reads[KnownFacts] = (__ \ "postcode").read(postcodeValidation).map(KnownFacts.apply)
}

object SubscriptionRequest {
  implicit val writes: Writes[SubscriptionRequest] = Json.format[SubscriptionRequest]
  implicit val reads: Reads[SubscriptionRequest] = (
    (__ \ "utr").read[Utr](verifying[Utr](utr => Utr.isValid(utr.value))) and
    (__ \ "knownFacts").read[KnownFacts] and
    (__ \ "agency").read[Agency] and
    (__ \ "amlsDetails").readNullable[AmlsDetails])(SubscriptionRequest.apply _)
}

case class Address(
  addressLine1: String,
  addressLine2: Option[String],
  addressLine3: Option[String],
  addressLine4: Option[String],
  postcode: String,
  countryCode: String)

case class Agency(
  name: String,
  address: Address,
  telephone: Option[String],
  email: String)

case class KnownFacts(postcode: String)

case class SubscriptionRequest(
  utr: Utr,
  knownFacts: KnownFacts,
  agency: Agency,
  amlsDetails: Option[AmlsDetails] = None)

case class SubscriptionResponse(arn: Arn)
object SubscriptionResponse {
  implicit val format = Json.format[SubscriptionResponse]
}

case class UpdateSubscriptionRequest(utr: Utr, knownFacts: KnownFacts)

object UpdateSubscriptionRequest {
  implicit val writes: Writes[UpdateSubscriptionRequest] = Json.writes[UpdateSubscriptionRequest]
  implicit val reads: Reads[UpdateSubscriptionRequest] = (
    (__ \ "utr").read[Utr](verifying[Utr](utr => Utr.isValid(utr.value))) and
    (__ \ "knownFacts").read[KnownFacts])(UpdateSubscriptionRequest.apply _)
}