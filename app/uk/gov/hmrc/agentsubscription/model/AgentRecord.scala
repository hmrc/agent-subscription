/*
 * Copyright 2023 HM Revenue & Customs
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

case class AgentRecord(
  arn: Arn,
  isAnASAgent: Boolean,
  agencyName: String,
  agencyAddress: Address,
  agencyEmail: String,
  businessPostcode: String,
  phoneNumber: Option[String]
)

object AgentRecord {
  implicit val agentRecordReads: Reads[AgentRecord] =
    ((__ \ "agentReferenceNumber").read[Arn](verifying[Arn](arn => Arn.isValid(arn.value))) and
      (__ \ "isAnASAgent").read[Boolean] and
      (__ \ "agencyDetails" \ "agencyName").read[String] and
      (__ \ "agencyDetails" \ "agencyAddress" \ "addressLine1").read[String] and
      (__ \ "agencyDetails" \ "agencyAddress" \ "addressLine2").readNullable[String] and
      (__ \ "agencyDetails" \ "agencyAddress" \ "addressLine3").readNullable[String] and
      (__ \ "agencyDetails" \ "agencyAddress" \ "addressLine4").readNullable[String] and
      (__ \ "agencyDetails" \ "agencyAddress" \ "postalCode").read[String] and
      (__ \ "agencyDetails" \ "agencyAddress" \ "countryCode").read[String] and
      (__ \ "agencyDetails" \ "agencyEmail").read[String] and
      (__ \ "addressDetails" \ "postalCode").read[String] and
      (__ \ "contactDetails" \ "phoneNumber").readNullable[String].or(Reads.pure(None: Option[String])))(
      (
        arn,
        isAnASAgent,
        agencyName,
        addressLine1,
        addressLine2,
        addressLine3,
        addressLine4,
        agencyPostcode,
        countryCode,
        agencyEmail,
        businessPostcode,
        phoneNumber
      ) =>
        AgentRecord(
          arn,
          isAnASAgent,
          agencyName,
          Address(addressLine1, addressLine2, addressLine3, addressLine4, agencyPostcode, countryCode),
          agencyEmail,
          businessPostcode,
          phoneNumber
        )
    )

}
