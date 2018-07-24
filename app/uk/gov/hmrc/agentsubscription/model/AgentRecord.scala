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

import play.api.libs.json.Reads.verifying
import play.api.libs.json._
import play.api.libs.functional.syntax._
import uk.gov.hmrc.agentmtdidentifiers.model.Arn

case class AgentAddress(
  addressLine1: String,
  addressLine2: Option[String],
  addressLine3: Option[String],
  addressLine4: Option[String],
  postalCode: String,
  countryCode: String)

object AgentAddress {
  implicit val format = Json.format[AgentAddress]
}

case class AgentRecord(
  arn: Arn,
  isAnASAgent: Boolean,
  agencyName: String,
  address: AgentAddress,
  email: String,
  knownfactPostcode: String,
  phoneNUmber: Option[String])

object AgentRecord {
  implicit val agentRecordReads: Reads[AgentRecord] = (
    (__ \ "agentReferenceNumber").read[Arn](verifying[Arn](arn => Arn.isValid(arn.value))) and
    (__ \ "isAnASAgent").read[Boolean] and
    (__ \ "agencyDetails" \ "agencyName").read[String] and
    (__ \ "agencyDetails" \ "agencyAddress").read[AgentAddress] and
    (__ \ "agencyDetails" \ "agencyEmail").read[String] and
    (__ \ "addressDetails" \ "postalCode").read[String] and
    (__ \ "contactDetails" \ "phoneNumber").readNullable[String])(AgentRecord.apply _)

}